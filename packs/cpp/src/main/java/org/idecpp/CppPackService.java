package org.idecpp;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import org.pocketide.pack.v1.ILanguagePack;
import org.pocketide.pack.v1.ITaskCallback;
import org.pocketide.pack.v1.PackContract;
import org.pocketide.pack.debug.v1.DebugContract;
import org.pocketide.pack.debug.v1.IDebugCallback;
import org.pocketide.pack.debug.v1.IDebugPack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Clang-backed API v1 service in its own Android process. */
public final class CppPackService extends Service {
    private static final Pattern LOCATION = Pattern.compile("(?m)^([^\\n:]+):(\\d+):(\\d+):\\s*(?:fatal )?error:");
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private volatile CppDebugSession debugSession;

    private final IDebugPack.Stub debugBinder = new IDebugPack.Stub() {
        @Override public int getProtocolVersion() {
            checkCaller();
            return DebugContract.PROTOCOL_VERSION;
        }

        @Override public void start(Bundle request, ParcelFileDescriptor input,
                ParcelFileDescriptor channel, IDebugCallback callback) {
            checkCaller();
            if (request == null || input == null || channel == null || callback == null)
                throw new IllegalArgumentException("Missing C/C++ debug field");
            String id = request.getString(DebugContract.SESSION_ID, "");
            String entry = request.getString(DebugContract.ENTRY, "");
            if (!id.matches("[0-9a-f-]{36}")
                    || request.getInt(DebugContract.VERSION) != DebugContract.PROTOCOL_VERSION
                    || !(entry.endsWith(".c") || entry.endsWith(".cpp")
                            || entry.endsWith(".cc") || entry.endsWith(".cxx"))) {
                close(input); close(channel);
                throw new IllegalArgumentException("Invalid C/C++ debug request");
            }
            synchronized (CppPackService.this) {
                if (debugSession != null || !tasks.isEmpty()) {
                    close(input); close(channel);
                    throw new IllegalStateException("C/C++ debug session already active");
                }
                CppDebugSession session = new CppDebugSession(CppPackService.this,
                        id, entry, input, channel, callback);
                debugSession = session;
                new Thread(() -> {
                    try { session.run(); }
                    finally { synchronized (CppPackService.this) {
                        if (debugSession == session) debugSession = null;
                    } }
                }, "cpp-debug-session").start();
            }
        }

        @Override public void stop(String sessionId) {
            checkCaller();
            CppDebugSession session = debugSession;
            if (session != null && session.id.equals(sessionId)) session.stop();
        }
    };

    private final ILanguagePack.Stub binder = new ILanguagePack.Stub() {
        @Override public int getProtocolVersion() {
            checkCaller();
            return PackContract.PROTOCOL_VERSION;
        }
        @Override public void submit(Bundle request, ParcelFileDescriptor input,
                ParcelFileDescriptor output, ITaskCallback callback) {
            checkCaller();
            if (request == null || input == null || output == null || callback == null)
                throw new IllegalArgumentException("Missing task field");
            if (debugSession != null) {
                close(input); close(output);
                throw new IllegalStateException("C/C++ debug session is active");
            }
            String id = request.getString(PackContract.TASK_ID, "");
            if (!id.matches("[0-9a-f-]{36}")
                    || request.getInt(PackContract.VERSION) != PackContract.PROTOCOL_VERSION) {
                close(input); close(output);
                throw new IllegalArgumentException("Invalid task or protocol version");
            }
            Task task = new Task(id, input, output, callback);
            if (tasks.putIfAbsent(id, task) != null) {
                close(input); close(output);
                throw new IllegalArgumentException("Duplicate task ID");
            }
            task.future = worker.submit(() -> process(request, task));
        }
        @Override public void cancel(String taskId) {
            checkCaller();
            Task task = tasks.get(taskId);
            if (task == null) return;
            task.cancelled = true;
            Process active = task.process;
            if (active != null) active.destroyForcibly();
            Future<?> future = task.future;
            if (future != null) future.cancel(true);
            task.reply(PackContract.CANCELLED, "CANCELLED", "任务已取消", -1, 0, 0);
            close(task.input); close(task.output);
            tasks.remove(taskId, task);
        }
    };

    private void checkCaller() {
        int caller = Binder.getCallingUid();
        if (caller == android.os.Process.myUid()) return;
        String[] packages = getPackageManager().getPackagesForUid(caller);
        if (packages != null) for (String name : packages)
            if ("com.example.pocketandroidide".equals(name)) return;
        throw new SecurityException("Only Pocket Android IDE may call the compiler service");
    }

    synchronized void debugFinished(CppDebugSession session) {
        if (debugSession == session) debugSession = null;
    }

    @Override public IBinder onBind(Intent intent) {
        return intent != null && DebugContract.ACTION.equals(intent.getAction())
                ? debugBinder : binder;
    }

    private void process(Bundle request, Task task) {
        File directory = new File(getCacheDir(), "cpp-task-" + task.id);
        try (InputStream source = new ParcelFileDescriptor.AutoCloseInputStream(task.input);
             OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(task.output)) {
            String operation = request.getString(PackContract.OPERATION, "");
            if ("testCrash".equals(operation)) {
                android.os.Process.killProcess(android.os.Process.myPid());
                return;
            }
            if ("testVersionMismatch".equals(operation)) {
                task.replyVersionMismatch();
                return;
            }
            Toolchain tools = new Toolchain(this);
            if (!Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a")) {
                task.reply(PackContract.ERROR, "ARCH_MISMATCH",
                        "此包只支持 arm64-v8a，当前设备架构不兼容", -1, 0, 0);
                return;
            }
            if ("initialize".equals(operation)) {
                tools.ensure();
                task.reply(PackContract.OK, "", "Clang 21.1.8 ARM64 已就绪", 0, 0, 0);
                return;
            }
            if (!("analyze".equals(operation) || "complete".equals(operation)
                    || "diagnostics".equals(operation) || "build".equals(operation)
                    || "run".equals(operation))) {
                task.reply(PackContract.UNSUPPORTED, "UNSUPPORTED", operation, -1, 0, 0);
                return;
            }
            tools.ensure();
            CppProject project = CppProject.read(source, directory,
                    request.getString(PackContract.ENTRY, ""));
            if ("complete".equals(operation)) {
                output.write(CppCompletion.suggest(project, request.getInt("cursor", -1))
                        .getBytes(StandardCharsets.UTF_8));
                task.reply(PackContract.OK, "", "补全范围：关键字、常用标准库名与项目声明", 0, 0, 0);
                return;
            }
            boolean syntaxOnly = "analyze".equals(operation) || "diagnostics".equals(operation);
            File artifact = new File(directory, "libexercise.so");
            List<String> command = compileCommand(tools, project, artifact, syntaxOnly);
            long deadline = System.nanoTime() + 55_000_000_000L;
            CppProcess.Result compile = CppProcess.run(command, directory, tools,
                    null, output, task, deadline);
            if (compile.outputLimit) {
                task.reply(PackContract.ERROR, "OUTPUT_LIMIT", "编译输出超过 3.5 MB", -1, 0, 0);
                return;
            }
            if (compile.timedOut) {
                task.reply(PackContract.ERROR, "TIMEOUT", "编译超过 55 秒", -1, 0, 0);
                return;
            }
            if (compile.exit != 0) {
                Matcher location = LOCATION.matcher(compile.diagnostic);
                int line = location.find() ? Integer.parseInt(location.group(2)) : 0;
                int column = line == 0 ? 0 : Integer.parseInt(location.group(3));
                String code;
                if (compile.diagnostic.contains("file not found")
                        || compile.diagnostic.contains("No such file or directory"))
                    code = "HEADER_MISSING";
                else if (compile.diagnostic.contains("incompatible with aarch64")
                        || compile.diagnostic.contains("incompatible target")
                        || compile.diagnostic.contains("file in wrong format"))
                    code = "ARCH_MISMATCH";
                else code = "COMPILE_ERROR";
                task.reply(PackContract.ERROR, code,
                        code.equals("HEADER_MISSING") ? "找不到头文件，请检查 includeDirs"
                                : code.equals("ARCH_MISMATCH") ? "对象文件架构与 ARM64 Android 不兼容"
                                : "编译失败，见输出",
                        compile.exit, line, column);
                return;
            }
            if (syntaxOnly) {
                output.write("语法检查通过\n".getBytes(StandardCharsets.UTF_8));
                task.reply(PackContract.OK, "", "语法检查通过", 0, 0, 0);
                return;
            }
            if ("build".equals(operation)) {
                output.write("构建成功（临时产物已在语言包私有任务目录核验）\n".getBytes(StandardCharsets.UTF_8));
                task.reply(PackContract.OK, "", "构建成功", 0, 0, 0);
                return;
            }
            List<String> run = new ArrayList<>();
            run.add(tools.runner().getAbsolutePath());
            run.add(artifact.getAbsolutePath());
            run.addAll(project.arguments);
            CppProcess.Result executed = CppProcess.run(run, directory, tools,
                    project.stdin, output, task, deadline);
            if (executed.outputLimit) {
                task.reply(PackContract.ERROR, "OUTPUT_LIMIT", "程序输出超过 3.5 MB", -1, 0, 0);
            } else if (executed.timedOut) {
                task.reply(PackContract.ERROR, "TIMEOUT", "程序运行超过 55 秒", -1, 0, 0);
            } else if (executed.exit != 0) {
                task.reply(PackContract.ERROR, "RUNTIME_EXIT",
                        "程序以非零状态退出：" + executed.exit, executed.exit, 0, 0);
            } else {
                task.reply(PackContract.OK, "", "运行完成", 0, 0, 0);
            }
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            task.reply(PackContract.CANCELLED, "CANCELLED", "任务已取消", -1, 0, 0);
        } catch (IOException platformFailure) {
            String message = platformFailure.getMessage() == null
                    ? platformFailure.toString() : platformFailure.getMessage();
            String code = message.contains("Permission denied") || message.contains("EACCES")
                    ? "DEVICE_RESTRICTION" : message.contains("No such file")
                    ? "TOOLCHAIN_MISSING" : "PACK_IO_ERROR";
            task.reply(PackContract.ERROR, code,
                    code.equals("DEVICE_RESTRICTION")
                            ? "设备拒绝执行打包的编译器或运行器：" + message : message,
                    -1, 0, 0);
        } catch (Exception failure) {
            task.reply(PackContract.ERROR, "PACK_ERROR",
                    failure.getMessage() == null ? failure.toString() : failure.getMessage(), -1, 0, 0);
        } finally {
            tasks.remove(task.id, task);
            close(task.input); close(task.output);
            deletePrivateTask(directory);
        }
    }

    List<String> compileCommand(Toolchain tools, CppProject project,
            File artifact, boolean syntaxOnly) {
        List<String> command = new ArrayList<>();
        command.add(tools.compiler().getAbsolutePath());
        command.add("-target"); command.add("aarch64-linux-android26");
        command.add("--sysroot=" + getFilesDir().getAbsolutePath());
        command.add("-resource-dir");
        command.add(new File(tools.prefix(), "lib/clang/21").getAbsolutePath());
        command.add("-B" + new File(tools.prefix(), "lib").getAbsolutePath());
        command.add("-L" + new File(tools.prefix(), "lib").getAbsolutePath());
        command.add("-isystem");
        command.add(new File(tools.prefix(), "include/c++/v1").getAbsolutePath());
        command.add("-isystem"); command.add(new File(tools.prefix(), "include").getAbsolutePath());
        command.add("-std=" + project.standard);
        command.add("-O0");
        command.add("-Wall");
        for (File include : project.includes) command.add("-I" + include.getAbsolutePath());
        if (syntaxOnly) command.add("-fsyntax-only");
        else {
            command.add("-fuse-ld=" + tools.linkerWrapper().getAbsolutePath());
            command.add("-shared"); command.add("-fPIC");
            command.add("-Wl,-rpath," + new File(tools.prefix(), "lib").getAbsolutePath());
            command.add("-o"); command.add(artifact.getAbsolutePath());
        }
        for (File source : project.sources) command.add(source.getAbsolutePath());
        if (project.cpp && !syntaxOnly) command.add("-lc++_shared");
        return command;
    }

    private static void deletePrivateTask(File path) {
        if (!path.exists()) return;
        File[] children = path.listFiles();
        if (children != null) for (File child : children) deletePrivateTask(child);
        path.delete();
    }

    private static void close(ParcelFileDescriptor fd) {
        try { fd.close(); } catch (IOException ignored) { }
    }

    @Override public void onDestroy() {
        for (Task task : tasks.values()) {
            task.cancelled = true;
            if (task.process != null) task.process.destroyForcibly();
            task.reply(PackContract.CANCELLED, "CANCELLED", "服务已关闭", -1, 0, 0);
            close(task.input); close(task.output);
        }
        tasks.clear();
        worker.shutdownNow();
        super.onDestroy();
    }

    private static final class Task implements CppProcess.Active {
        final String id;
        final ParcelFileDescriptor input;
        final ParcelFileDescriptor output;
        final ITaskCallback callback;
        final AtomicBoolean replied = new AtomicBoolean();
        volatile Process process;
        volatile Future<?> future;
        volatile boolean cancelled;
        Task(String id, ParcelFileDescriptor input, ParcelFileDescriptor output, ITaskCallback callback) {
            this.id = id; this.input = input; this.output = output; this.callback = callback;
        }
        @Override public void set(Process value) { process = value; }
        @Override public boolean cancelled() { return cancelled; }
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
