package org.pocketide.python;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Base64;

import com.example.pocketpythonide.NativePythonBridge;

import org.json.JSONArray;
import org.json.JSONObject;
import org.pocketide.pack.v1.ILanguagePack;
import org.pocketide.pack.v1.ITaskCallback;
import org.pocketide.pack.v1.PackContract;
import org.pocketide.pack.debug.v1.DebugContract;
import org.pocketide.pack.debug.v1.IDebugCallback;
import org.pocketide.pack.debug.v1.IDebugPack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** API v1 CPython service; all interpreter calls stay on one process-lifetime thread. */
public final class PythonPackService extends Service {
    private static final ExecutorService PYTHON = Executors.newSingleThreadExecutor();
    private static boolean cleanedOrphans;
    private static final AtomicBoolean DEBUG_BUSY = new AtomicBoolean();
    private static volatile DebugTask activeDebug;
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    private final ILanguagePack.Stub binder = new ILanguagePack.Stub() {
        @Override public int getProtocolVersion() {
            checkCaller();
            return PackContract.PROTOCOL_VERSION;
        }

        @Override public void submit(Bundle request, ParcelFileDescriptor input,
                ParcelFileDescriptor output, ITaskCallback callback) {
            checkCaller();
            if (request == null || input == null || output == null || callback == null)
                throw new IllegalArgumentException("Missing Python task field");
            if (DEBUG_BUSY.get()) {
                close(input); close(output);
                throw new IllegalStateException("A Python debug session is active");
            }
            String id = request.getString(PackContract.TASK_ID, "");
            if (!id.matches("[0-9a-f-]{36}")
                    || request.getInt(PackContract.VERSION) != PackContract.PROTOCOL_VERSION) {
                close(input); close(output);
                throw new IllegalArgumentException("Invalid Python task ID or protocol");
            }
            Task task = new Task(id, input, output, callback);
            if (tasks.putIfAbsent(id, task) != null) {
                close(input); close(output);
                throw new IllegalArgumentException("Duplicate Python task ID");
            }
            task.future = PYTHON.submit(() -> process(request, task));
        }

        @Override public void cancel(String taskId) {
            checkCaller();
            Task task = tasks.get(taskId);
            if (task == null) return;
            task.cancelled = true;
            task.reply(PackContract.CANCELLED, "CANCELLED", "Python 任务已取消",
                    -1, 0, 0);
            // The host's interrupted caller already returns PACK_CANCELLED. End this
            // process before the next validation call can bind to a doomed worker.
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    };

    private final IDebugPack.Stub debugBinder = new IDebugPack.Stub() {
        @Override public int getProtocolVersion() {
            checkCaller();
            return DebugContract.PROTOCOL_VERSION;
        }

        @Override public void start(Bundle request, ParcelFileDescriptor input,
                ParcelFileDescriptor channel, IDebugCallback callback) {
            checkCaller();
            if (request == null || input == null || channel == null || callback == null)
                throw new IllegalArgumentException("Missing Python debug session field");
            String id = request.getString(DebugContract.SESSION_ID, "");
            String entry = request.getString(DebugContract.ENTRY, "");
            if (!id.matches("[0-9a-f-]{36}")
                    || request.getInt(DebugContract.VERSION) != DebugContract.PROTOCOL_VERSION
                    || entry.isEmpty() || entry.startsWith("/") || entry.contains("..")
                    || entry.contains("\\") || !entry.endsWith(".py")) {
                close(input); close(channel);
                throw new IllegalArgumentException("Invalid Python debug request");
            }
            if (!DEBUG_BUSY.compareAndSet(false, true)) {
                close(input); close(channel);
                throw new IllegalStateException("A Python debug session is active");
            }
            if (!tasks.isEmpty()) {
                DEBUG_BUSY.set(false);
                close(input); close(channel);
                throw new IllegalStateException("A Python task is active");
            }
            DebugTask task = new DebugTask(id, entry, input, channel, callback);
            activeDebug = task;
            PYTHON.submit(() -> processDebug(task));
        }

        @Override public void stop(String sessionId) {
            checkCaller();
            DebugTask task = activeDebug;
            if (task != null && task.id.equals(sessionId))
                android.os.Process.killProcess(android.os.Process.myPid());
        }
    };

    @Override public IBinder onBind(Intent intent) {
        return intent != null && DebugContract.ACTION.equals(intent.getAction())
                ? debugBinder : binder;
    }

    private void processDebug(DebugTask task) {
        File folder = new File(new File(getFilesDir(), "tasks"), task.id);
        File ready = new File(folder, "debug.ready");
        String status = "error";
        String message = "Python 调试未完成";
        try {
            cleanOrphans(task.id);
            File workspace = new File(folder, "workspace");
            if (!workspace.mkdirs()) throw new IOException("Cannot create Python debug workspace");
            extract(task.input, workspace);
            File entry = new File(workspace, task.entry).getCanonicalFile();
            if (!entry.getPath().startsWith(workspace.getCanonicalPath() + File.separator)
                    || !entry.isFile()) throw new IOException("Invalid debug entry");
            Thread watcher = new Thread(() -> {
                for (int attempt = 0; attempt < 1200 && !task.done; attempt++) {
                    if (ready.isFile()) {
                        Bundle info = new Bundle();
                        info.putString(DebugContract.SESSION_ID, task.id);
                        info.putInt(DebugContract.VERSION, DebugContract.PROTOCOL_VERSION);
                        try { task.callback.ready(info); } catch (RemoteException ignored) { }
                        return;
                    }
                    try { Thread.sleep(100); } catch (InterruptedException ignored) { return; }
                }
            }, "python-debug-ready");
            watcher.start();
            JSONObject command = new JSONObject().put("workspace", workspace.getAbsolutePath())
                    .put("entry", task.entry);
            JSONObject answer = NativePythonBridge.debug(this, command,
                    task.channel.getFd(), ready);
            status = answer.optString("status", "error");
            message = answer.optString("message");
        } catch (Throwable failure) {
            message = failure.getMessage() == null
                    ? failure.toString() : failure.getMessage();
        } finally {
            task.done = true;
            close(task.input); close(task.channel);
            try { deleteTask(folder); } catch (IOException ignored) { }
            activeDebug = null;
            DEBUG_BUSY.set(false);
            // Only report completion once the next debug session can start.
            task.finish(status, message);
        }
    }

    private void checkCaller() {
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.myUid()) return;
        String[] names = getPackageManager().getPackagesForUid(uid);
        if (names != null) for (String name : names) {
            if ("com.example.pocketandroidide".equals(name)
                    && getPackageManager().checkSignatures(name, getPackageName())
                    == PackageManager.SIGNATURE_MATCH) return;
        }
        throw new SecurityException("Only the same-signature Pocket Android IDE may call Python");
    }

    private void process(Bundle request, Task task) {
        File folder = new File(new File(getFilesDir(), "tasks"), task.id);
        try {
            cleanOrphans(task.id);
            if ("testCrash".equals(request.getString(PackContract.OPERATION))) {
                android.os.Process.killProcess(android.os.Process.myPid());
                return;
            }
            if ("testVersionMismatch".equals(request.getString(PackContract.OPERATION))) {
                task.replyVersionMismatch();
                return;
            }
            File workspace = new File(folder, "workspace");
            if (!workspace.mkdirs()) throw new IOException("Cannot create Python task workspace");
            extract(task.input, workspace);
            if (task.cancelled) return;
            String operation = request.getString(PackContract.OPERATION, "");
            String entry = request.getString(PackContract.ENTRY, "");
            if (!"initialize".equals(operation)) {
                if (entry.isEmpty() || entry.startsWith("/") || entry.contains("..")
                        || entry.contains("\\") || !entry.endsWith(".py")
                        || !new File(workspace, entry).isFile())
                    throw new IOException("Invalid Python entry: " + entry);
            }
            Map<String, String> before = "run".equals(operation)
                    ? hashes(workspace) : new HashMap<>();
            JSONObject command = new JSONObject()
                    .put("operation", operation)
                    .put("workspace", workspace.getAbsolutePath())
                    .put("entry", entry);
            if (request.containsKey("cursor")) command.put("cursor", request.getInt("cursor"));
            JSONObject result = NativePythonBridge.execute(this, command);
            JSONArray changes = "run".equals(operation)
                    ? changes(workspace, before) : new JSONArray();
            JSONObject envelope = new JSONObject()
                    .put("stdout", result.optString("stdout"))
                    .put("stderr", result.optString("stderr"))
                    .put("changes", changes);
            byte[] bytes = envelope.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 3_700_000) throw new IOException("Python output exceeds 3.7 MB");
            try (OutputStream sink = new ParcelFileDescriptor.AutoCloseOutputStream(task.output)) {
                sink.write(bytes);
                sink.flush();
            }
            task.reply(result.optString("status", PackContract.ERROR),
                    result.optString("errorCode"), result.optString("message"),
                    result.optInt("exitCode", -1), result.optInt("line"),
                    result.optInt("column"));
        } catch (Throwable failure) {
            if (!task.cancelled) task.reply(PackContract.ERROR, "PY_RUNTIME_ERROR",
                    failure.getMessage() == null ? failure.toString() : failure.getMessage(),
                    -1, 0, 0);
        } finally {
            tasks.remove(task.id, task);
            close(task.input); close(task.output);
            try { deleteTask(folder); } catch (IOException ignored) { }
        }
    }

    private static Map<String, String> hashes(File workspace) throws Exception {
        Map<String, String> result = new HashMap<>();
        gather(workspace, workspace, result);
        return result;
    }

    private static JSONArray changes(File workspace, Map<String, String> before)
            throws Exception {
        Map<String, String> after = hashes(workspace);
        JSONArray result = new JSONArray();
        for (String path : before.keySet())
            if (!after.containsKey(path))
                throw new IOException("Python 删除了项目文件；API v1 暂不回写删除：" + path);
        long total = 0;
        java.util.List<String> paths = new java.util.ArrayList<>(after.keySet());
        java.util.Collections.sort(paths);
        for (String path : paths) {
            if (after.get(path).equals(before.get(path))) continue;
            if (result.length() >= 100)
                throw new IOException("Python 修改文件超过 100 个");
            byte[] content = Files.readAllBytes(new File(workspace, path).toPath());
            total += content.length;
            if (content.length > 1024 * 1024 || total > 2L * 1024 * 1024)
                throw new IOException("Python 文件变更超过 2 MiB 回传上限");
            result.put(new JSONObject()
                    .put("path", path)
                    .put("baseSha256", before.getOrDefault(path, ""))
                    .put("dataBase64", Base64.encodeToString(content, Base64.NO_WRAP)));
        }
        return result;
    }

    private static void gather(File root, File path, Map<String, String> hashes)
            throws Exception {
        File[] children = path.listFiles();
        if (children == null) throw new IOException("Cannot list Python workspace");
        for (File child : children) {
            if (Files.isSymbolicLink(child.toPath()))
                throw new IOException("Python workspace contains a symlink");
            String name = child.getName();
            if (name.equals(".git") || name.equals(".venv") || name.equals("__pycache__")
                    || name.equals("build")) continue;
            if (child.isDirectory()) {
                gather(root, child, hashes);
                continue;
            }
            String relative = root.toPath().relativize(child.toPath()).toString()
                    .replace(File.separatorChar, '/');
            if (child.length() > 1024 * 1024)
                throw new IOException("Python 输出文件超过 1 MiB：" + relative);
            String now = hash(child);
            hashes.put(relative, now);
        }
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] block = new byte[8192];
            int n;
            while ((n = input.read(block)) != -1) digest.update(block, 0, n);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte item : digest.digest())
            result.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
        return result.toString();
    }

    private static void extract(ParcelFileDescriptor descriptor, File root) throws IOException {
        int files = 0;
        long total = 0;
        Set<String> seen = new HashSet<>();
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             ZipInputStream zip = new ZipInputStream(stream)) {
            ZipEntry member;
            byte[] block = new byte[8192];
            while ((member = zip.getNextEntry()) != null) {
                String name = member.getName();
                if (name.isEmpty() || name.startsWith("/") || name.contains("..")
                        || name.contains("\\") || !seen.add(name))
                    throw new IOException("Unsafe Python project ZIP path");
                File output = new File(root, name).getCanonicalFile();
                if (!output.getPath().startsWith(root.getCanonicalPath() + File.separator))
                    throw new IOException("Python project ZIP escaped task directory");
                if (member.isDirectory()) {
                    if (!output.mkdirs() && !output.isDirectory())
                        throw new IOException("Cannot create Python project directory");
                } else {
                    if (++files > 2000) throw new IOException("Python project has too many files");
                    File parent = output.getParentFile();
                    if (!parent.mkdirs() && !parent.isDirectory())
                        throw new IOException("Cannot create Python project directory");
                    try (FileOutputStream file = new FileOutputStream(output)) {
                        int n;
                        long size = 0;
                        while ((n = zip.read(block)) != -1) {
                            size += n;
                            total += n;
                            if (size > 1024 * 1024 || total > 8L * 1024 * 1024)
                                throw new IOException("Python project exceeds API v1 input limits");
                            file.write(block, 0, n);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private void deleteTask(File folder) throws IOException {
        if (!folder.exists()) return;
        File base = new File(getFilesDir(), "tasks").getCanonicalFile();
        if (!base.equals(folder.getCanonicalFile().getParentFile()))
            throw new IOException("Refusing to remove a non-task directory");
        deleteRecursively(folder);
    }

    private void cleanOrphans(String currentId) throws IOException {
        if (cleanedOrphans) return;
        File root = new File(getFilesDir(), "tasks");
        File[] children = root.listFiles();
        if (children != null) for (File child : children) {
            if (child.getName().matches("[0-9a-f-]{36}")
                    && !child.getName().equals(currentId)) deleteTask(child);
        }
        cleanedOrphans = true;
    }

    private static void deleteRecursively(File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath())) throw new IOException("Task contains a symlink");
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot list Python task directory");
            for (File child : children) deleteRecursively(child);
        }
        if (!file.delete()) throw new IOException("Cannot remove Python task file");
    }

    private static void close(ParcelFileDescriptor fd) {
        try { fd.close(); } catch (IOException ignored) { }
    }

    private static final class DebugTask {
        final String id;
        final String entry;
        final ParcelFileDescriptor input;
        final ParcelFileDescriptor channel;
        final IDebugCallback callback;
        final AtomicBoolean finished = new AtomicBoolean();
        volatile boolean done;

        DebugTask(String id, String entry, ParcelFileDescriptor input,
                ParcelFileDescriptor channel,
                IDebugCallback callback) {
            this.id = id;
            this.entry = entry;
            this.input = input;
            this.channel = channel;
            this.callback = callback;
        }

        void finish(String status, String message) {
            if (!finished.compareAndSet(false, true)) return;
            Bundle result = new Bundle();
            result.putString(DebugContract.SESSION_ID, id);
            result.putInt(DebugContract.VERSION, DebugContract.PROTOCOL_VERSION);
            result.putString(DebugContract.STATUS, status);
            result.putString(DebugContract.MESSAGE, message);
            try { callback.finished(result); } catch (RemoteException ignored) { }
        }
    }

    private static final class Task {
        final String id;
        final ParcelFileDescriptor input;
        final ParcelFileDescriptor output;
        final ITaskCallback callback;
        final AtomicBoolean replied = new AtomicBoolean();
        volatile Future<?> future;
        volatile boolean cancelled;

        Task(String id, ParcelFileDescriptor input, ParcelFileDescriptor output,
                ITaskCallback callback) {
            this.id = id;
            this.input = input;
            this.output = output;
            this.callback = callback;
        }

        void reply(String status, String code, String message, int exit, int line, int column) {
            if (!replied.compareAndSet(false, true)) return;
            Bundle value = new Bundle();
            value.putString(PackContract.TASK_ID, id);
            value.putInt(PackContract.VERSION, PackContract.PROTOCOL_VERSION);
            value.putString(PackContract.STATUS, status);
            value.putString(PackContract.ERROR_CODE, code);
            value.putString(PackContract.MESSAGE, message);
            value.putInt(PackContract.EXIT_CODE, exit);
            value.putInt(PackContract.LINE, line);
            value.putInt(PackContract.COLUMN, column);
            value.putInt(PackContract.END_LINE, line);
            value.putInt(PackContract.END_COLUMN, column);
            value.putString(PackContract.RESULT_PATH, "");
            try { callback.finished(value); } catch (RemoteException ignored) { }
        }

        void replyVersionMismatch() {
            if (!replied.compareAndSet(false, true)) return;
            Bundle value = new Bundle();
            value.putString(PackContract.TASK_ID, id);
            value.putInt(PackContract.VERSION, 2);
            value.putString(PackContract.STATUS, PackContract.OK);
            try { callback.finished(value); } catch (RemoteException ignored) { }
        }
    }
}
