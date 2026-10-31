package org.pocketide.sample.echo;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import org.pocketide.pack.v1.ILanguagePack;
import org.pocketide.pack.v1.ITaskCallback;
import org.pocketide.pack.v1.PackContract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** A deliberately small, offline .echo interpreter for API v1 integration tests. */
public final class EchoPackService extends Service {
    private static final int MAX_SOURCE = 1024 * 1024;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    private final ILanguagePack.Stub binder = new ILanguagePack.Stub() {
        @Override public int getProtocolVersion() {
            checkHost();
            return PackContract.PROTOCOL_VERSION;
        }

        @Override public void submit(Bundle request, ParcelFileDescriptor input,
                ParcelFileDescriptor output, ITaskCallback callback) {
            checkHost();
            if (request == null || input == null || output == null || callback == null)
                throw new IllegalArgumentException("Missing task fields or descriptors");
            String id = request.getString(PackContract.TASK_ID, "");
            if (!id.matches("[a-f0-9-]{36}")
                    || request.getInt(PackContract.VERSION) != PackContract.PROTOCOL_VERSION) {
                close(input); close(output);
                throw new IllegalArgumentException("Invalid task ID or protocol version");
            }
            Task task = new Task(id, callback, input, output);
            if (tasks.putIfAbsent(id, task) != null) {
                close(input); close(output);
                throw new IllegalArgumentException("Duplicate task ID");
            }
            task.future = worker.submit(() -> process(request, input, output, task));
        }

        @Override public void cancel(String taskId) {
            checkHost();
            Task task = tasks.get(taskId);
            if (task != null) {
                task.reply(PackContract.CANCELLED, "CANCELLED", "任务已取消", -1, 0, 0);
                Future<?> future = task.future;
                if (future != null) future.cancel(true);
                close(task.input); close(task.output);
                tasks.remove(taskId, task);
            }
        }
    };

    private void checkHost() {
        String[] names = getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (names != null) for (String name : names)
            if ("com.example.pocketandroidide".equals(name)) return;
        throw new SecurityException("Only Pocket Android IDE may call this sample service");
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    private void process(Bundle request, ParcelFileDescriptor input,
            ParcelFileDescriptor output, Task task) {
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(input);
             OutputStream sink = new ParcelFileDescriptor.AutoCloseOutputStream(output)) {
            String operation = request.getString(PackContract.OPERATION, "");
            String entry = request.getString(PackContract.ENTRY, "");
            if ("testCrash".equals(operation)) {
                android.os.Process.killProcess(android.os.Process.myPid());
                return;
            }
            if ("testVersionMismatch".equals(operation)) {
                task.replyVersionMismatch();
                return;
            }
            if ("initialize".equals(operation)) {
                task.reply(PackContract.OK, "", "Pocket Echo ready", 0, 0, 0);
                return;
            }
            if (!("run".equals(operation) || "analyze".equals(operation)
                    || "complete".equals(operation) || "diagnostics".equals(operation))) {
                task.reply(PackContract.UNSUPPORTED, "UNSUPPORTED", operation, -1, 0, 0);
                return;
            }
            String source = readEntry(stream, entry);
            String[] lines = source.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                String line = lines[i].trim();
                if (line.startsWith("ERROR")) {
                    task.reply(PackContract.ERROR, "SOURCE_ERROR", "ERROR 指令", 2,
                            i + 1, Math.max(1, lines[i].indexOf("ERROR") + 1));
                    return;
                }
            }
            if ("complete".equals(operation)) {
                sink.write("ECHO\nWAIT\nREPEAT\n".getBytes(StandardCharsets.UTF_8));
            } else if ("run".equals(operation)) {
                for (String line : lines) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (line.startsWith("ECHO ")) {
                        sink.write((line.substring(5) + "\n").getBytes(StandardCharsets.UTF_8));
                    } else if (line.startsWith("WAIT ")) {
                        long millis = Long.parseLong(line.substring(5));
                        if (millis < 0 || millis > 30000) throw new IOException("WAIT out of range");
                        Thread.sleep(millis);
                    } else if (line.startsWith("REPEAT ")) {
                        int count = Integer.parseInt(line.substring(7));
                        if (count < 0 || count > 200000) throw new IOException("REPEAT out of range");
                        byte[] bytes = "echo output\n".getBytes(StandardCharsets.UTF_8);
                        for (int n = 0; n < count; n++) {
                            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                            sink.write(bytes);
                        }
                    } else if (!line.isEmpty() && !line.startsWith("#")) {
                        throw new IOException("Unknown instruction: " + line);
                    }
                }
            }
            sink.flush();
            task.reply(PackContract.OK, "", "完成", 0, 0, 0);
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            task.reply(PackContract.CANCELLED, "CANCELLED", "任务已取消", -1, 0, 0);
        } catch (Exception failure) {
            task.reply(PackContract.ERROR, "PACK_ERROR", failure.getMessage(), 1, 0, 0);
        } finally {
            tasks.remove(task.id, task);
            close(input); close(output);
        }
    }

    private static String readEntry(InputStream input, String expected) throws IOException {
        if (expected.isEmpty() || expected.startsWith("/") || expected.contains("..")
                || expected.contains("\\")) throw new IOException("Invalid entry path");
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            int count = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 2000) throw new IOException("Too many files");
                if (!expected.equals(entry.getName())) continue;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] block = new byte[8192];
                int read;
                while ((read = zip.read(block)) != -1) {
                    if (bytes.size() + read > MAX_SOURCE) throw new IOException("Source exceeds 1 MiB");
                    bytes.write(block, 0, read);
                }
                return bytes.toString(StandardCharsets.UTF_8.name());
            }
        }
        throw new IOException("Entry not present in workspace snapshot");
    }

    private static void close(ParcelFileDescriptor fd) {
        try { fd.close(); } catch (IOException ignored) { }
    }

    @Override public void onDestroy() {
        for (Task task : tasks.values()) {
            task.reply(PackContract.CANCELLED, "CANCELLED", "服务已关闭", -1, 0, 0);
            close(task.input); close(task.output);
        }
        tasks.clear();
        worker.shutdownNow();
        super.onDestroy();
    }

    private final class Task {
        final String id;
        final ITaskCallback callback;
        final ParcelFileDescriptor input;
        final ParcelFileDescriptor output;
        final AtomicBoolean replied = new AtomicBoolean();
        volatile Future<?> future;
        Task(String id, ITaskCallback callback, ParcelFileDescriptor input,
                ParcelFileDescriptor output) {
            this.id = id; this.callback = callback; this.input = input; this.output = output;
        }
        void reply(String status, String code, String message, int exit, int line, int column) {
            if (!replied.compareAndSet(false, true)) return;
            Bundle result = new Bundle();
            result.putString(PackContract.TASK_ID, id);
            result.putInt(PackContract.VERSION, PackContract.PROTOCOL_VERSION);
            result.putString(PackContract.STATUS, status);
            result.putString(PackContract.ERROR_CODE, code);
            result.putString(PackContract.MESSAGE, message);
            result.putInt(PackContract.EXIT_CODE, exit);
            result.putInt(PackContract.LINE, line);
            result.putInt(PackContract.COLUMN, column);
            result.putInt(PackContract.END_LINE, line);
            result.putInt(PackContract.END_COLUMN, column);
            result.putString(PackContract.RESULT_PATH, "");
            try { callback.finished(result); } catch (RemoteException ignored) { }
        }
        void replyVersionMismatch() {
            if (!replied.compareAndSet(false, true)) return;
            Bundle result = new Bundle();
            result.putString(PackContract.TASK_ID, id);
            result.putInt(PackContract.VERSION, 2);
            result.putString(PackContract.STATUS, PackContract.OK);
            try { callback.finished(result); } catch (RemoteException ignored) { }
        }
    }
}
