package org.idecpp;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileLock;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs only the pinned, read-only compiler resource tree in this pack's UID. */
final class Toolchain {
    private static final Object INSTALL_LOCK = new Object();
    private final Context context;
    private final File prefix;

    Toolchain(Context context) {
        this.context = context.getApplicationContext();
        this.prefix = new File(context.getFilesDir(), "usr");
    }

    File prefix() { return prefix; }
    File compiler() { return new File(context.getApplicationInfo().nativeLibraryDir, "libclang_exec.so"); }
    File linker() { return new File(context.getApplicationInfo().nativeLibraryDir, "liblld_exec.so"); }
    File linkerWrapper() { return new File(context.getApplicationInfo().nativeLibraryDir, "liblld_wrapper.so"); }
    File runner() { return new File(context.getApplicationInfo().nativeLibraryDir, "libprojectrunner.so"); }
    File debugger() { return new File(context.getApplicationInfo().nativeLibraryDir, "liblldb_dap.so"); }
    File debugServer() { return new File(context.getApplicationInfo().nativeLibraryDir, "liblldb_server.so"); }

    void ensure() throws Exception {
        JSONObject manifest;
        try (InputStream in = context.getAssets().open("toolchain.json")) {
            manifest = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
        }
        String expected = manifest.getString("asset_sha256");
        synchronized (INSTALL_LOCK) {
            File lockPath = new File(context.getFilesDir(), "toolchain.lock");
            try (RandomAccessFile lockFile = new RandomAccessFile(lockPath, "rw");
                 FileLock ignored = lockFile.getChannel().lock()) {
                if (installed(expected)) return;
                verifyAsset(expected);
                File staging = new File(context.getFilesDir(), "usr.next");
                File previous = new File(context.getFilesDir(), "usr.previous");
                if (!prefix.exists() && previous.exists()
                        && !previous.renameTo(prefix))
                    throw new IOException("Cannot restore previous toolchain");
                removeManaged(staging);
                if (!staging.mkdirs()) throw new IOException("Cannot create staging toolchain");
                extract(staging);
                File stagedMarker = new File(staging, ".complete");
                try (FileOutputStream out = new FileOutputStream(stagedMarker)) {
                    out.write(expected.getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
                removeManaged(previous);
                boolean hadPrevious = prefix.exists();
                if (hadPrevious && !prefix.renameTo(previous))
                    throw new IOException("Cannot preserve previous toolchain");
                if (!staging.renameTo(prefix)) {
                    if (hadPrevious) previous.renameTo(prefix);
                    throw new IOException("Cannot activate staged toolchain");
                }
                removeManaged(previous);
            }
        }
    }

    private boolean installed(String expected) throws IOException {
        File marker = new File(prefix, ".complete");
        return marker.isFile() && expected.equals(new String(
                java.nio.file.Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim());
    }

    private void verifyAsset(String expected) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream raw = context.getAssets().open("toolchain.zip")) {
            byte[] chunk = new byte[32768];
            int count;
            while ((count = raw.read(chunk)) != -1) digest.update(chunk, 0, count);
        }
        if (!expected.equals(hex(digest.digest())))
            throw new IOException("Toolchain asset SHA-256 mismatch");
    }

    private void extract(File staging) throws IOException {
        try (InputStream raw = context.getAssets().open("toolchain.zip");
             ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            byte[] buffer = new byte[32768];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("..") || name.contains("\\"))
                    throw new IOException("Unsafe toolchain entry: " + name);
                File destination = new File(staging, name);
                if (entry.isDirectory()) {
                    if (!destination.mkdirs() && !destination.isDirectory())
                        throw new IOException("Cannot create " + name);
                    continue;
                }
                File parent = destination.getParentFile();
                if (!parent.mkdirs() && !parent.isDirectory())
                    throw new IOException("Cannot create " + parent);
                try (FileOutputStream out = new FileOutputStream(destination)) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) out.write(buffer, 0, count);
                }
                destination.setReadable(true, true);
                destination.setWritable(false, false);
            }
        }
    }

    private void removeManaged(File path) throws IOException {
        if (!path.exists()) return;
        File home = context.getFilesDir().getCanonicalFile();
        File canonical = path.getCanonicalFile();
        if (!home.equals(canonical.getParentFile())
                || !(canonical.getName().equals("usr.next")
                || canonical.getName().equals("usr.previous")))
            throw new IOException("Refusing to remove a non-toolchain directory");
        deleteTree(path);
    }

    private static void deleteTree(File path) throws IOException {
        if (java.nio.file.Files.isSymbolicLink(path.toPath()))
            throw new IOException("Unexpected symlink in toolchain directory");
        if (path.isDirectory()) {
            File[] children = path.listFiles();
            if (children == null) throw new IOException("Cannot list toolchain directory");
            for (File child : children) deleteTree(child);
        }
        path.setWritable(true, true);
        if (!path.delete()) throw new IOException("Cannot remove old toolchain file: " + path);
    }

    static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte b : value) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
}
