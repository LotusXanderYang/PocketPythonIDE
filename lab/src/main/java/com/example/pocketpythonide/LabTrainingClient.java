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
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Foreground CPU task with file-backed progress and a bounded wait. */
final class LabTrainingClient {
    interface Progress { void update(String text); }

    private LabTrainingClient() { }

    static JSONObject execute(Context context, File project, String runId, Progress progress)
            throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Lab training must run off the UI thread");
        File tasks = new File(context.getFilesDir(), "runtime-requests");
        File task = new File(tasks, UUID.randomUUID().toString());
        if (!task.mkdirs()) throw new IOException("Cannot create Lab request directory");
        File requestFile = new File(task, "request.json");
        File outputFile = new File(task, "result.json");
        File log = new File(new File(project, "logs"), runId + ".log");
        JSONObject request = new JSONObject().put("mode", "train")
                .put("project", project.getCanonicalPath()).put("runId", runId);
        Files.write(requestFile.toPath(), request.toString().getBytes(StandardCharsets.UTF_8));
        CountDownLatch bound = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Messenger> remote = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();
        Messenger reply = new Messenger(new Handler(Looper.getMainLooper(), message -> {
            if (message.what != LabRuntimeService.FINISHED) return false;
            failure.set(message.getData().getString("error"));
            finished.countDown();
            return true;
        }));
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                remote.set(new Messenger(binder));
                bound.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                failure.set("Python 训练进程已退出；日志和已完成的检查点仍保留");
                bound.countDown();
                finished.countDown();
            }
            @Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
            @Override public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
        };
        Intent service = new Intent(context, LabRuntimeService.class)
                .setAction(LabRuntimeService.TRAIN_ACTION);
        boolean attached = false;
        boolean started = false;
        boolean completed = false;
        try {
            context.startForegroundService(service);
            started = true;
            attached = context.bindService(service, connection, Context.BIND_AUTO_CREATE);
            if (!attached || !bound.await(15, TimeUnit.SECONDS) || remote.get() == null)
                throw new IOException("无法连接 Lab 训练进程");
            Message submit = Message.obtain(null, LabRuntimeService.EXECUTE);
            Bundle paths = new Bundle();
            paths.putString("request", requestFile.getAbsolutePath());
            paths.putString("output", outputFile.getAbsolutePath());
            submit.setData(paths);
            submit.replyTo = reply;
            remote.get().send(submit);
            long deadline = SystemClock.elapsedRealtime() + 30L * 60 * 1000;
            long previousSize = -1;
            while (!finished.await(400, TimeUnit.MILLISECONDS)) {
                if (SystemClock.elapsedRealtime() > deadline) {
                    requestStop(remote.get());
                    throw new IOException("训练超过 30 分钟上限，已停止独立 Python 进程");
                }
                if (log.isFile() && log.length() != previousSize) {
                    previousSize = log.length();
                    if (previousSize <= 1100000)
                        progress.update(new String(Files.readAllBytes(log.toPath()),
                                StandardCharsets.UTF_8));
                }
            }
            if (log.isFile() && log.length() <= 1100000)
                progress.update(new String(Files.readAllBytes(log.toPath()),
                        StandardCharsets.UTF_8));
            if (failure.get() != null) throw new IOException(failure.get());
            if (!outputFile.isFile()) throw new IOException("训练任务未写出结果");
            JSONObject report = new JSONObject(new String(Files.readAllBytes(outputFile.toPath()),
                    StandardCharsets.UTF_8));
            report.put("runId", runId);
            completed = true;
            return report;
        } catch (InterruptedException cancelled) {
            requestStop(remote.get());
            Thread.currentThread().interrupt();
            throw new IOException("训练已停止；日志与已完成的检查点保留", cancelled);
        } finally {
            if (completed) {
                try { Messenger connectionToRelease = remote.get();
                    if (connectionToRelease != null)
                        connectionToRelease.send(Message.obtain(null, LabRuntimeService.RELEASE));
                } catch (Exception ignored) { }
            }
            if (attached) context.unbindService(connection);
            if (started) context.stopService(service);
            Files.deleteIfExists(requestFile.toPath());
            Files.deleteIfExists(outputFile.toPath());
            if (!task.delete()) task.deleteOnExit();
        }
    }

    private static void requestStop(Messenger remote) {
        if (remote == null) return;
        try { remote.send(Message.obtain(null, LabRuntimeService.CANCEL)); }
        catch (Exception ignored) { /* Process may already have exited. */ }
    }

    static void stopDetached(Context context) throws Exception {
        CountDownLatch bound = new CountDownLatch(1);
        AtomicReference<Messenger> remote = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                remote.set(new Messenger(binder));
                bound.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) { bound.countDown(); }
        };
        boolean attached = context.bindService(new Intent(context, LabRuntimeService.class),
                connection, 0);
        if (!attached) return;
        try {
            if (bound.await(3, TimeUnit.SECONDS)) requestStop(remote.get());
        } finally { context.unbindService(connection); }
    }
}
