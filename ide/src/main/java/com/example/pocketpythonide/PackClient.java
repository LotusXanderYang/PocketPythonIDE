package com.example.pocketpythonide;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.pocketide.pack.v1.ILanguagePack;
import org.pocketide.pack.v1.ITaskCallback;
import org.pocketide.pack.v1.PackContract;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** One bounded AIDL task. All inputs are read-only descriptors; packs get no SAF grant. */
final class PackClient {
    private PackClient() {}

    static JSONObject invoke(Context context, PackRegistry.Descriptor pack, String operation,
            File workdir, File entry, long timeoutSeconds) throws Exception {
        return invoke(context, pack, operation, workdir, entry, timeoutSeconds, null);
    }

    static JSONObject invoke(Context context, PackRegistry.Descriptor pack, String operation,
            File workdir, File entry, long timeoutSeconds, CountDownLatch submitted)
            throws Exception {
        return invoke(context, pack, operation, workdir, entry, timeoutSeconds, submitted, null);
    }

    static JSONObject complete(Context context, PackRegistry.Descriptor pack,
            File workdir, File entry, int cursor) throws Exception {
        return invoke(context, pack, "complete", workdir, entry, 30, null, cursor);
    }

    private static JSONObject invoke(Context context, PackRegistry.Descriptor pack,
            String operation, File workdir, File entry, long timeoutSeconds,
            CountDownLatch submitted, Integer cursor) throws Exception {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            throw new IllegalStateException("Pack IPC must run off the UI thread");
        String taskId = UUID.randomUUID().toString();
        File input = File.createTempFile("pack-input-", ".zip", context.getCacheDir());
        File output = File.createTempFile("pack-output-", ".txt", context.getCacheDir());
        String relativeEntry = entry == null ? "" : relative(workdir, entry);
        try {
            zipWorkspace(input, workdir);
            return call(context, pack, operation, taskId, relativeEntry,
                    input, output, workdir, timeoutSeconds, submitted, cursor);
        } finally {
            Files.deleteIfExists(input.toPath());
            Files.deleteIfExists(output.toPath());
        }
    }

    private static JSONObject call(Context context, PackRegistry.Descriptor pack, String operation,
            String taskId, String entry, File input, File output, File workdir,
            long timeoutSeconds,
            CountDownLatch submitted, Integer cursor)
            throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<ILanguagePack> remote = new AtomicReference<>();
        AtomicReference<Bundle> result = new AtomicReference<>();
        AtomicReference<String> disconnected = new AtomicReference<>();
        ITaskCallback callback = new ITaskCallback.Stub() {
            @Override public void finished(Bundle value) {
                result.set(value);
                finished.countDown();
            }
        };
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                remote.set(ILanguagePack.Stub.asInterface(binder));
                connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                disconnected.set("PACK_CRASH: " + name.flattenToShortString());
                connected.countDown(); finished.countDown();
            }
            @Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
            @Override public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
        };
        boolean bound = false;
        try {
            Intent intent = new Intent(PackContract.ACTION).setComponent(pack.component);
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
                throw new IOException("PACK_BIND_FAILED: " + pack.packId() + " / " + taskId);
            int protocol;
            try { protocol = remote.get().getProtocolVersion(); }
            catch (RemoteException died) {
                throw new IOException("PACK_CRASH: " + pack.packId() + " / " + taskId,
                        died);
            }
            if (protocol != PackContract.PROTOCOL_VERSION)
                throw new IOException("PROTOCOL_MISMATCH: " + pack.packId() + " / " + taskId);
            Bundle request = new Bundle();
            request.putString(PackContract.TASK_ID, taskId);
            request.putInt(PackContract.VERSION, PackContract.PROTOCOL_VERSION);
            request.putString(PackContract.OPERATION, operation);
            request.putString(PackContract.WORKSPACE, "task:" + taskId);
            request.putString(PackContract.ENTRY, entry);
            if (cursor != null) request.putInt("cursor", cursor);
            try (ParcelFileDescriptor source = ParcelFileDescriptor.open(input,
                         ParcelFileDescriptor.MODE_READ_ONLY);
                 ParcelFileDescriptor sink = ParcelFileDescriptor.open(output,
                         ParcelFileDescriptor.MODE_WRITE_ONLY
                         | ParcelFileDescriptor.MODE_TRUNCATE)) {
                try { remote.get().submit(request, source, sink, callback); }
                catch (RemoteException died) {
                    throw new IOException("PACK_CRASH: " + pack.packId() + " / " + taskId, died);
                }
                if (submitted != null) submitted.countDown();
            }
            if (!finished.await(timeoutSeconds, TimeUnit.SECONDS)) {
                remote.get().cancel(taskId);
                throw new IOException("PACK_TIMEOUT: " + pack.packId() + " / " + taskId);
            }
            if (disconnected.get() != null)
                throw new IOException(disconnected.get() + " / " + taskId);
            Bundle value = result.get();
            if (value == null || !taskId.equals(value.getString(PackContract.TASK_ID)))
                throw new IOException("PACK_BAD_RESULT: " + pack.packId() + " / " + taskId);
            if (value.getInt(PackContract.VERSION) != PackContract.PROTOCOL_VERSION)
                throw new IOException("PROTOCOL_MISMATCH: " + pack.packId() + " / " + taskId);
            if (output.length() > 4L * 1024 * 1024)
                throw new IOException("PACK_OUTPUT_LIMIT: " + pack.packId() + " / " + taskId);
            JSONObject json = new JSONObject();
            String status = value.getString(PackContract.STATUS, PackContract.ERROR);
            json.put("ok", PackContract.OK.equals(status));
            json.put("status", status);
            String payload = new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
            if ("changes-v1".equals(pack.manifest.optString("workspaceOutput"))
                    && payload.isEmpty() && !PackContract.OK.equals(status)) {
                json.put("stdout", "");
                json.put("stderr", "");
            } else if ("changes-v1".equals(pack.manifest.optString("workspaceOutput"))) {
                if (context.getPackageManager().checkSignatures(context.getPackageName(),
                        pack.packId()) != android.content.pm.PackageManager.SIGNATURE_MATCH)
                    throw new IOException("PACK_OUTPUT_UNTRUSTED: signature mismatch");
                JSONObject envelope = new JSONObject(payload);
                JSONArray changes = envelope.getJSONArray("changes");
                if ("run".equals(operation) && !PackContract.CANCELLED.equals(status))
                    applyChanges(workdir, changes);
                else if (changes.length() != 0)
                    throw new IOException("PACK_BAD_RESULT: non-run task changed workspace");
                json.put("stdout", envelope.getString("stdout"));
                json.put("stderr", envelope.optString("stderr"));
            } else {
                json.put("stdout", payload);
                json.put("stderr", "");
            }
            String display = value.getString(PackContract.MESSAGE, "");
            if (!PackContract.OK.equals(status)) display += " [" + pack.packId()
                    + " / " + taskId + " / " + value.getString(PackContract.ERROR_CODE, "")
                    + " · " + value.getInt(PackContract.LINE) + ":"
                    + value.getInt(PackContract.COLUMN) + "]";
            json.put("error", display);
            json.put("errorCode", value.getString(PackContract.ERROR_CODE, ""));
            json.put("exitCode", value.getInt(PackContract.EXIT_CODE, -1));
            json.put("line", value.getInt(PackContract.LINE));
            json.put("column", value.getInt(PackContract.COLUMN));
            json.put("packId", pack.packId());
            json.put("taskId", taskId);
            return json;
        } catch (InterruptedException cancelled) {
            ILanguagePack service = remote.get();
            if (service != null) {
                try { service.cancel(taskId); } catch (RemoteException ignored) { }
            }
            Thread.currentThread().interrupt();
            throw new IOException("PACK_CANCELLED: " + pack.packId() + " / " + taskId, cancelled);
        } finally {
            if (bound) context.unbindService(connection);
        }
    }

    private static final class Change {
        final File target;
        final byte[] content;
        Change(File target, byte[] content) {
            this.target = target;
            this.content = content;
        }
    }

    /** Validate the whole write set before touching the host's private workspace copy. */
    private static void applyChanges(File workspace, JSONArray changes) throws Exception {
        if (changes.length() == 0) return;
        if (workspace == null || changes.length() > 100)
            throw new IOException("PACK_OUTPUT_LIMIT: invalid workspace changes");
        String base = workspace.getCanonicalPath() + File.separator;
        Set<String> paths = new HashSet<>();
        List<Change> pending = new ArrayList<>();
        long total = 0;
        for (int index = 0; index < changes.length(); index++) {
            JSONObject change = changes.getJSONObject(index);
            String path = change.getString("path");
            if (path.isEmpty() || path.startsWith("/") || path.contains("\\")
                    || path.contains("//") || !paths.add(path))
                throw new IOException("PACK_BAD_RESULT: unsafe change path");
            for (String part : path.split("/"))
                if (part.isEmpty() || part.equals(".") || part.equals("..")
                        || part.equals(".git") || part.equals(".venv")
                        || part.equals("__pycache__") || part.equals("build"))
                    throw new IOException("PACK_BAD_RESULT: unsafe change path");
            File target = new File(workspace, path).getCanonicalFile();
            if (!target.getPath().startsWith(base) || target.isDirectory())
                throw new IOException("PACK_BAD_RESULT: change escaped workspace");
            byte[] content = Base64.decode(change.getString("dataBase64"), Base64.DEFAULT);
            total += content.length;
            if (content.length > 1024 * 1024 || total > 2L * 1024 * 1024)
                throw new IOException("PACK_OUTPUT_LIMIT: changed files exceed 2 MiB");
            String before = change.getString("baseSha256");
            if (target.exists()) {
                if (target.length() > 1024 * 1024 || !before.equals(hash(target)))
                    throw new IOException("PACK_OUTPUT_CONFLICT: " + path);
            } else if (!before.isEmpty()) {
                throw new IOException("PACK_OUTPUT_CONFLICT: " + path);
            }
            pending.add(new Change(target, content));
        }
        for (Change change : pending) {
            File parent = change.target.getParentFile();
            if (!parent.mkdirs() && !parent.isDirectory())
                throw new IOException("PACK_OUTPUT_FAILED: cannot create output directory");
            File staged = File.createTempFile(".pack-change-", ".tmp", parent);
            try {
                Files.write(staged.toPath(), change.content);
                Files.move(staged.toPath(), change.target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(staged.toPath());
            }
        }
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] block = new byte[8192];
            int n;
            while ((n = stream.read(block)) != -1) digest.update(block, 0, n);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest.digest())
            out.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }

    static String relative(File root, File entry) throws IOException {
        String parent = root.getCanonicalPath();
        String child = entry.getCanonicalPath();
        if (!child.startsWith(parent + File.separator)) throw new IOException("Entry outside workspace");
        return child.substring(parent.length() + 1).replace(File.separatorChar, '/');
    }

    static void zipWorkspace(File output, File root) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            if (root == null) return;
            String base = root.getCanonicalPath();
            List<File> queue = new ArrayList<>();
            queue.add(root);
            long total = 0;
            int count = 0;
            for (int index = 0; index < queue.size(); index++) {
                File current = queue.get(index);
                File[] children = current.listFiles();
                if (children == null) throw new IOException("Cannot list workspace");
                for (File file : children) {
                    if (!file.getCanonicalPath().startsWith(base + File.separator))
                        throw new IOException("Workspace symlink escaped root");
                    if (file.isDirectory()) { queue.add(file); continue; }
                    String name = relative(root, file);
                    if (!file.isFile() || ++count > PackInputLimits.MAX_FILES)
                        throw new IOException("语言包输入文件超过 2000 个，达到：" + name);
                    total = PackInputLimits.checkedTotal(name, file.length(), total);
                    zip.putNextEntry(new ZipEntry(name));
                    try (FileInputStream source = new FileInputStream(file)) {
                        byte[] block = new byte[8192];
                        int read;
                        while ((read = source.read(block)) != -1) zip.write(block, 0, read);
                    }
                    zip.closeEntry();
                }
            }
        }
    }
}
