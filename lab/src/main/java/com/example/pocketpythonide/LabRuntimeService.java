package com.example.pocketpythonide;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.SystemClock;

import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Native Python lives in :python so an extension crash does not kill the editor. */
public final class LabRuntimeService extends Service {
    static final int EXECUTE = 1;
    static final int CANCEL = 2;
    static final int FINISHED = 3;
    static final int RELEASE = 4;
    static final String TRAIN_ACTION = "com.example.pocketpythonlab.TRAIN";
    private static final String CHANNEL = "lab-training";
    // CPython is initialized on its first caller and the JNI bridge requires every
    // later command to run on that same thread. A bound Service can be recreated
    // while its process stays alive, so the worker must outlive Service instances.
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean foregroundActive;
    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), this::receive));

    @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !TRAIN_ACTION.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Lab CPU 训练", NotificationManager.IMPORTANCE_LOW));
        Intent view = new Intent(this, LabHomeActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 0, view,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notice = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Python · PyTorch Lab")
                .setContentText("CPU 任务运行中；返回 Lab 可查看进度或停止")
                .setContentIntent(open).setOngoing(true).build();
        startForeground(7, notice);
        foregroundActive = true;
        return START_NOT_STICKY;
    }

    private boolean receive(Message message) {
        if (message.what == CANCEL) {
            Process.killProcess(Process.myPid());
            return true;
        }
        if (message.what == RELEASE) {
            Process.killProcess(Process.myPid());
            return true;
        }
        if (message.what != EXECUTE || message.replyTo == null) return false;
        Messenger reply = message.replyTo;
        if (!BUSY.compareAndSet(false, true)) {
            send(reply, "已有 Python 任务在运行");
            return true;
        }
        Bundle request = message.getData();
        String path = request.getString("request");
        String output = request.getString("output");
        long started = SystemClock.elapsedRealtime();
        AtomicInteger peakPss = new AtomicInteger();
        Runnable sampler = new Runnable() {
            @Override public void run() {
                if (!BUSY.get()) return;
                peakPss.accumulateAndGet((int) Math.min(Integer.MAX_VALUE,
                        Debug.getPss()), Math::max);
                main.postDelayed(this, 250);
            }
        };
        main.post(sampler);
        WORKER.execute(() -> {
            String error = null;
            try {
                File root = new File(getFilesDir(), "runtime-requests").getCanonicalFile();
                File input = new File(path).getCanonicalFile();
                File result = new File(output).getCanonicalFile();
                if (!root.equals(input.getParentFile().getParentFile())
                        || !input.getParentFile().equals(result.getParentFile())
                        || !input.getName().equals("request.json")
                        || !result.getName().equals("result.json"))
                    throw new SecurityException("Invalid runtime request paths");
                JSONObject command = new JSONObject(new String(Files.readAllBytes(input.toPath()),
                        StandardCharsets.UTF_8));
                String response;
                if ("probe".equals(command.getString("mode"))) {
                    response = NativePythonBridge.probe(this);
                } else if ("run".equals(command.getString("mode"))) {
                    response = NativePythonBridge.runCode(this, command.getString("source"),
                            command.getString("filename"), command.getString("workdir"));
                } else if ("train".equals(command.getString("mode"))) {
                    File projects = new File(getFilesDir(), "projects").getCanonicalFile();
                    File project = new File(command.getString("project")).getCanonicalFile();
                    String runId = command.getString("runId");
                    if (!projects.equals(project.getParentFile())
                            || !project.getName().matches("[A-Za-z0-9_-]{1,40}")
                            || !runId.matches("[0-9a-f-]{36}")
                            || !new File(project, "main.py").isFile())
                        throw new SecurityException("Invalid Lab training project");
                    response = NativePythonBridge.train(this, project, runId);
                    JSONObject report = new JSONObject(response);
                    report.put("wallMs", SystemClock.elapsedRealtime() - started);
                    report.put("peakPssKb", peakPss.get());
                    response = report.toString();
                    File record = new File(new File(project, "runs"), runId + ".json");
                    Files.write(record.toPath(), (response + "\n").getBytes(StandardCharsets.UTF_8),
                            java.nio.file.StandardOpenOption.CREATE_NEW);
                } else throw new IllegalArgumentException("Unknown runtime mode");
                Files.write(result.toPath(), response.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable failure) { error = failure.toString(); }
            finally {
                BUSY.set(false);
                main.removeCallbacks(sampler);
                if (foregroundActive) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    foregroundActive = false;
                    stopSelf();
                }
            }
            send(reply, error);
        });
        return true;
    }

    private static void send(Messenger reply, String error) {
        Message done = Message.obtain(null, FINISHED);
        Bundle status = new Bundle();
        status.putString("error", error);
        done.setData(status);
        try { reply.send(done); } catch (Exception ignored) { /* Client left. */ }
    }

}
