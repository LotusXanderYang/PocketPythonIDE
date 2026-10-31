package com.example.pocketpythonide;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;

import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** File-backed Messenger request avoids Binder's source-size limit. Call off the UI thread. */
final class LabRuntimeClient {
    private LabRuntimeClient() {}

    static String execute(Context context, String mode, String source, String filename,
            String workdir) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Runtime request must not block the UI thread");
        File root = new File(context.getFilesDir(), "runtime-requests");
        File task = new File(root, UUID.randomUUID().toString());
        if (!task.mkdirs()) throw new IOException("Cannot create runtime request directory");
        File input = new File(task, "request.json");
        File output = new File(task, "result.json");
        JSONObject payload = new JSONObject().put("mode", mode);
        if ("run".equals(mode)) payload.put("source", source).put("filename", filename)
                .put("workdir", workdir);
        Files.write(input.toPath(), payload.toString().getBytes(StandardCharsets.UTF_8));
        CountDownLatch bound = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Messenger> server = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();
        Messenger reply = new Messenger(new Handler(Looper.getMainLooper(), message -> {
            if (message.what == LabRuntimeService.FINISHED) {
                failure.set(message.getData().getString("error"));
                finished.countDown();
                return true;
            }
            return false;
        }));
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                server.set(new Messenger(binder));
                bound.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                failure.set("Python 运行进程已退出");
                bound.countDown();
                finished.countDown();
            }
            @Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
            @Override public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
        };
        boolean attached = false;
        try {
            attached = context.bindService(new Intent(context, LabRuntimeService.class),
                    connection, Context.BIND_AUTO_CREATE);
            if (!attached || !bound.await(15, TimeUnit.SECONDS) || server.get() == null)
                throw new IOException("无法连接 Python 运行进程");
            Message request = Message.obtain(null, LabRuntimeService.EXECUTE);
            Bundle paths = new Bundle();
            paths.putString("request", input.getAbsolutePath());
            paths.putString("output", output.getAbsolutePath());
            request.setData(paths);
            request.replyTo = reply;
            server.get().send(request);
            finished.await();
            if (failure.get() != null) throw new IOException(failure.get());
            return new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
        } catch (InterruptedException cancelled) {
            Messenger remote = server.get();
            if (remote != null) {
                try { remote.send(Message.obtain(null, LabRuntimeService.CANCEL)); }
                catch (Exception ignored) { /* Process may already have stopped. */ }
            }
            Thread.currentThread().interrupt();
            throw new IOException("运行已取消，独立 Python 进程已请求停止", cancelled);
        } finally {
            if (attached) context.unbindService(connection);
            Files.deleteIfExists(input.toPath());
            Files.deleteIfExists(output.toPath());
            if (!task.delete()) task.deleteOnExit();
        }
    }
}
