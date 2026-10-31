package org.idecpp;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.pocketide.pack.debug.v1.DebugContract;
import org.pocketide.pack.debug.v1.IDebugCallback;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** One private C/C++ project snapshot and an LLDB DAP stream in the pack process. */
final class CppDebugSession {
    final String id;
    private final CppPackService service;
    private final String entry;
    private final ParcelFileDescriptor input;
    private final ParcelFileDescriptor channel;
    private final IDebugCallback callback;
    private volatile Process adapter;
    private volatile Process compiler;
    private volatile boolean cancelled;
    private volatile boolean naturallyTerminated;
    private volatile String bridgeFailure;
    private File projectRoot;
    private File artifact;
    private CppProject project;
    private Toolchain tools;

    CppDebugSession(CppPackService service, String id, String entry,
            ParcelFileDescriptor input, ParcelFileDescriptor channel, IDebugCallback callback) {
        this.service = service;
        this.id = id;
        this.entry = entry;
        this.input = input;
        this.channel = channel;
        this.callback = callback;
    }

    void stop() {
        cancelled = true;
        Process current = adapter;
        if (current != null) current.destroyForcibly();
        Process building = compiler;
        if (building != null) building.destroyForcibly();
        try { channel.close(); } catch (IOException ignored) { }
    }

    void run() {
        String status = "error";
        String message = "C/C++ 调试未完成";
        File folder = new File(service.getCacheDir(), "cpp-debug-" + id);
        try {
            tools = new Toolchain(service);
            tools.ensure();
            project = CppProject.read(new ParcelFileDescriptor.AutoCloseInputStream(input),
                    folder, entry);
            projectRoot = folder;
            artifact = new File(folder, "libexercise.so");
            List<String> command = new ArrayList<>(service.compileCommand(tools, project,
                    artifact, false));
            command.add(1, "-g");
            command.add(2, "-fno-omit-frame-pointer");
            ProcessBuilder compiler = new ProcessBuilder(command).directory(folder)
                    .redirectErrorStream(true);
            compiler.environment().put("LD_LIBRARY_PATH", libraryPath());
            compiler.environment().put("TMPDIR", folder.getAbsolutePath());
            if (cancelled) throw new IOException("调试已取消");
            Process build = compiler.start();
            this.compiler = build;
            java.util.concurrent.atomic.AtomicBoolean timedOut =
                    new java.util.concurrent.atomic.AtomicBoolean();
            Thread compileWatchdog = new Thread(() -> {
                try { Thread.sleep(55000); } catch (InterruptedException ignored) { return; }
                if (build.isAlive()) {
                    timedOut.set(true);
                    build.destroyForcibly();
                }
            }, "cpp-debug-compile-timeout");
            compileWatchdog.setDaemon(true);
            compileWatchdog.start();
            try {
                byte[] diagnostics = readBounded(build.getInputStream(), 3_500_000);
                if (!build.waitFor(5, TimeUnit.SECONDS))
                    throw new IOException("C/C++ 调试构建未能结束");
                if (cancelled) throw new IOException("调试已取消");
                if (timedOut.get()) throw new IOException("C/C++ 调试构建超时");
                if (build.exitValue() != 0) throw new IOException("C/C++ 调试构建失败：\n"
                        + new String(diagnostics, StandardCharsets.UTF_8));
            } finally {
                compileWatchdog.interrupt();
                if (build.isAlive()) build.destroyForcibly();
                this.compiler = null;
            }
            if (cancelled) throw new IOException("调试已取消");
            ProcessBuilder launch = new ProcessBuilder(tools.debugger().getAbsolutePath())
                    .directory(folder).redirectError(new File(folder, "lldb-stderr.txt"));
            launch.environment().put("LD_LIBRARY_PATH", libraryPath());
            launch.environment().put("TMPDIR", folder.getAbsolutePath());
            launch.environment().put("LLDB_DEBUGSERVER_PATH", tools.debugServer().getAbsolutePath());
            launch.environment().put("PYTHONHOME", tools.prefix().getAbsolutePath());
            adapter = launch.start();
            Bundle ready = new Bundle();
            ready.putString(DebugContract.SESSION_ID, id);
            ready.putInt(DebugContract.VERSION, DebugContract.PROTOCOL_VERSION);
            callback.ready(ready);
            InputStream fromIde = new FileInputStream(channel.getFileDescriptor());
            OutputStream toIde = new FileOutputStream(channel.getFileDescriptor());
            Thread requests = new Thread(() -> bridge(fromIde, adapter.getOutputStream(), true),
                    "cpp-dap-requests");
            Thread replies = new Thread(() -> bridge(adapter.getInputStream(), toIde, false),
                    "cpp-dap-replies");
            requests.start();
            replies.start();
            if (!adapter.waitFor(3600, TimeUnit.SECONDS))
                throw new IOException("C/C++ 调试会话超时");
            status = cancelled ? "cancelled" : bridgeFailure == null
                    && (naturallyTerminated || adapter.exitValue() == 0)
                    ? "ok" : "error";
            message = status.equals("ok") ? "C/C++ 调试结束"
                    : bridgeFailure != null ? bridgeFailure : "LLDB 退出：" + adapter.exitValue();
        } catch (Throwable failure) {
            if (cancelled) status = "cancelled";
            message = failure.getMessage() == null ? failure.toString() : failure.getMessage();
        } finally {
            Process current = adapter;
            if (current != null) current.destroyForcibly();
            try { channel.close(); } catch (IOException ignored) { }
            try { input.close(); } catch (IOException ignored) { }
            Bundle result = new Bundle();
            result.putString(DebugContract.SESSION_ID, id);
            result.putInt(DebugContract.VERSION, DebugContract.PROTOCOL_VERSION);
            result.putString(DebugContract.STATUS, status);
            result.putString(DebugContract.MESSAGE, message);
            service.debugFinished(this);
            try { callback.finished(result); } catch (RemoteException ignored) { }
            delete(folder);
        }
    }

    private String libraryPath() {
        return tools.prefix() + "/lib:" + service.getApplicationInfo().nativeLibraryDir;
    }

    private void bridge(InputStream input, OutputStream output, boolean request) {
        try {
            while (!cancelled) {
                JSONObject message = readMessage(input);
                if (request) rewriteRequest(message);
                else rewriteReply(message);
                writeMessage(output, message);
                if (!request && "event".equals(message.optString("type"))
                        && "terminated".equals(message.optString("event"))) {
                    naturallyTerminated = true;
                    adapter.destroy();
                    return;
                }
            }
        } catch (Exception failure) {
            if (!cancelled && !naturallyTerminated) {
                bridgeFailure = "DAP 通道失败：" + failure.getMessage();
                Process current = adapter;
                if (current != null) current.destroyForcibly();
            }
        }
    }

    private void rewriteRequest(JSONObject message) throws Exception {
        if (!"request".equals(message.optString("type"))) return;
        JSONObject args = message.optJSONObject("arguments");
        if (args == null) return;
        switch (message.optString("command")) {
            case "initialize":
                args.put("pathFormat", "path");
                args.put("linesStartAt1", true);
                args.put("columnsStartAt1", true);
                break;
            case "launch":
                args.put("program", tools.runner().getAbsolutePath());
                JSONArray argv = new JSONArray().put(artifact.getAbsolutePath());
                for (String arg : project.arguments) argv.put(arg);
                args.put("args", argv);
                args.put("cwd", projectRoot.getAbsolutePath());
                args.put("env", new JSONObject().put("LD_LIBRARY_PATH", libraryPath()));
                args.put("disableASLR", false);
                // LLDB defaults to disabling ASLR. Some Android compatibility
                // environments reject the required personality() call.
                args.put("preRunCommands", new JSONArray()
                        .put("!settings set target.disable-aslr false"));
                break;
            case "setBreakpoints":
                JSONObject source = args.optJSONObject("source");
                if (source != null) source.put("path", inside(source.optString("path"))
                        .getAbsolutePath());
                break;
            default: break;
        }
    }

    private File inside(String path) throws IOException {
        if (path.startsWith(projectRoot.getCanonicalPath() + File.separator)) {
            File file = new File(path).getCanonicalFile();
            if (!file.getPath().startsWith(projectRoot.getCanonicalPath() + File.separator))
                throw new IOException("调试源文件超出工作区");
            return file;
        }
        return CppProject.inside(projectRoot, path);
    }

    private void rewriteReply(JSONObject message) throws Exception {
        rewritePaths(message);
    }

    private void rewritePaths(Object object) throws Exception {
        if (object instanceof JSONObject) {
            JSONObject json = (JSONObject) object;
            JSONObject source = json.optJSONObject("source");
            if (source != null) {
                String path = source.optString("path");
                String root = projectRoot.getCanonicalPath() + File.separator;
                if (path.startsWith(root)) source.put("path", path.substring(root.length())
                        .replace(File.separatorChar, '/'));
            }
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) rewritePaths(json.opt(keys.next()));
        } else if (object instanceof JSONArray) {
            JSONArray list = (JSONArray) object;
            for (int index = 0; index < list.length(); index++) rewritePaths(list.opt(index));
        }
    }

    private static JSONObject readMessage(InputStream input) throws Exception {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) throw new IOException("DAP channel closed");
            header.write(value);
            byte[] bytes = header.toByteArray();
            int size = bytes.length;
            if (size > 4096) throw new IOException("DAP header too large");
            if (size >= 4 && bytes[size - 4] == '\r' && bytes[size - 3] == '\n'
                    && bytes[size - 2] == '\r' && bytes[size - 1] == '\n') break;
        }
        String text = header.toString(StandardCharsets.US_ASCII.name());
        int key = text.indexOf("Content-Length:");
        int end = key < 0 ? -1 : text.indexOf("\r\n", key);
        if (end < 0) throw new IOException("DAP length missing");
        int length = Integer.parseInt(text.substring(key + 15, end).trim());
        if (length < 1 || length > 4 * 1024 * 1024) throw new IOException("DAP frame too large");
        byte[] payload = new byte[length];
        int done = 0;
        while (done < length) {
            int count = input.read(payload, done, length - done);
            if (count < 0) throw new IOException("DAP frame truncated");
            done += count;
        }
        return new JSONObject(new String(payload, StandardCharsets.UTF_8));
    }

    private static void writeMessage(OutputStream output, JSONObject message) throws IOException {
        byte[] bytes = message.toString().getBytes(StandardCharsets.UTF_8);
        output.write(("Content-Length: " + bytes.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        output.write(bytes);
        output.flush();
    }

    private static byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (result.size() + count > limit) throw new IOException("编译输出超过 3.5 MB");
            result.write(buffer, 0, count);
        }
        return result.toByteArray();
    }

    private static void delete(File path) {
        if (path == null || !path.exists()) return;
        File[] children = path.listFiles();
        if (children != null) for (File child : children) delete(child);
        path.delete();
    }
}
