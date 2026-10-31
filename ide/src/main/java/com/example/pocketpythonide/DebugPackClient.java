package com.example.pocketpythonide;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import org.pocketide.pack.debug.v1.DebugContract;
import org.pocketide.pack.debug.v1.IDebugCallback;
import org.pocketide.pack.debug.v1.IDebugPack;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** One optional DAP session; v1 run/analyze IPC remains unchanged. */
final class DebugPackClient implements Closeable {
    private final Context context;
    private final PackRegistry.Descriptor pack;
    private final String id = UUID.randomUUID().toString();
    private final File input;
    private final CountDownLatch connected = new CountDownLatch(1);
    private final CountDownLatch ready = new CountDownLatch(1);
    private final CountDownLatch finished = new CountDownLatch(1);
    private final AtomicReference<IDebugPack> remote = new AtomicReference<>();
    private ParcelFileDescriptor channel;
    private volatile String error;
    private volatile boolean bound;
    private DapTransport dap;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            remote.set(IDebugPack.Stub.asInterface(binder));
            connected.countDown();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            error = "Python 调试进程已退出";
            connected.countDown(); ready.countDown(); finished.countDown();
        }
        @Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
        @Override public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
    };

    DebugPackClient(Context context, PackRegistry.Descriptor pack, File workspace, File entry)
            throws Exception {
        this.context = context.getApplicationContext();
        this.pack = pack;
        if (context.getPackageManager().checkSignatures(context.getPackageName(),
                pack.packId()) != PackageManager.SIGNATURE_MATCH)
            throw new IOException("调试语言包与 IDE 签名不一致");
        input = File.createTempFile("debug-input-", ".zip", context.getCacheDir());
        try {
            PackClient.zipWorkspace(input, workspace);
            start(PackClient.relative(workspace, entry));
        } catch (Exception failed) {
            close();
            throw failed;
        }
    }

    private void start(String relativeEntry) throws Exception {
        Intent intent = new Intent(DebugContract.ACTION).setComponent(pack.component);
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!bound && !pack.manifest.optString("warmupActivity").isEmpty()) {
            Intent warmup = new Intent().setComponent(new ComponentName(pack.packId(),
                    pack.manifest.getString("warmupActivity")));
            warmup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(warmup);
            for (int attempt = 0; attempt < 10 && !bound; attempt++) {
                Thread.sleep(300);
                bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            }
        }
        if (!bound || !connected.await(10, TimeUnit.SECONDS) || remote.get() == null)
            throw new IOException("无法连接 Python DAP 服务");
        if (remote.get().getProtocolVersion() != DebugContract.PROTOCOL_VERSION)
            throw new IOException("Python DAP 协议版本不匹配");
        Bundle request = new Bundle();
        request.putString(DebugContract.SESSION_ID, id);
        request.putInt(DebugContract.VERSION, DebugContract.PROTOCOL_VERSION);
        request.putString(DebugContract.ENTRY, relativeEntry);
        IDebugCallback callback = new IDebugCallback.Stub() {
            @Override public void ready(Bundle info) {
                if (!id.equals(info.getString(DebugContract.SESSION_ID))) return;
                if (info.getInt(DebugContract.VERSION) != DebugContract.PROTOCOL_VERSION) {
                    error = "DAP 就绪响应版本不匹配";
                }
                ready.countDown();
            }
            @Override public void finished(Bundle result) {
                if (!id.equals(result.getString(DebugContract.SESSION_ID))) return;
                if (!"ok".equals(result.getString(DebugContract.STATUS)))
                    error = result.getString(DebugContract.MESSAGE, "调试失败");
                finished.countDown();
                ready.countDown();
            }
        };
        ParcelFileDescriptor[] pair = ParcelFileDescriptor.createSocketPair();
        channel = pair[0];
        try (ParcelFileDescriptor source = ParcelFileDescriptor.open(input,
                ParcelFileDescriptor.MODE_READ_ONLY); ParcelFileDescriptor peer = pair[1]) {
            remote.get().start(request, source, peer, callback);
        }
        if (!ready.await(120, TimeUnit.SECONDS) || error != null)
            throw new IOException(error == null ? "Python DAP 启动超时" : error);
        dap = new DapTransport(channel);
    }

    DapTransport dap() { return dap; }
    String id() { return id; }
    String error() { return error; }
    boolean isFinished() { return finished.getCount() == 0; }
    boolean awaitFinished(long seconds) throws InterruptedException {
        return finished.await(seconds, TimeUnit.SECONDS);
    }

    @Override public void close() throws IOException { close(false); }

    void close(boolean naturallyTerminated) throws IOException {
        if (dap != null && !naturallyTerminated) {
            try { dap.request("disconnect", new org.json.JSONObject()
                    .put("terminateDebuggee", true), 2); } catch (Exception ignored) { }
        }
        IDebugPack service = remote.get();
        boolean completed = isFinished();
        // A terminated DAP event precedes the pack's cleanup callback. Keep the
        // session active until that callback; otherwise the next launch races
        // DEBUG_BUSY and a short timeout can kill a normally exiting process.
        if (!completed) try { completed = awaitFinished(naturallyTerminated ? 15 : 2); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        if (service != null && !completed) {
            try { service.stop(id); } catch (RemoteException ignored) { }
        }
        if (dap != null) { dap.close(); dap = null; }
        if (bound) { context.unbindService(connection); bound = false; }
        if (channel != null) { channel.close(); channel = null; }
        Files.deleteIfExists(input.toPath());
    }
}
