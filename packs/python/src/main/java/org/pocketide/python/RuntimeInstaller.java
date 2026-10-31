package org.pocketide.python;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs the verified CPython subset only inside this language pack's UID. */
public final class RuntimeInstaller {
    private static final Object LOCK = new Object();

    private RuntimeInstaller() { }

    public static File ensure(Context context) throws Exception {
        JSONObject manifest;
        try (InputStream input = context.getAssets().open("runtime.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] block = new byte[8192];
            int n;
            while ((n = input.read(block)) != -1) bytes.write(block, 0, n);
            manifest = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        }
        String expected = manifest.getString("asset_sha256");
        if (!context.getPackageName().equals(manifest.getString("app_id"))
                || !"3.14.6".equals(manifest.getString("python"))
                || !expected.matches("[0-9a-f]{64}"))
            throw new IOException("Invalid Python base runtime manifest");
        File home = context.getFilesDir();
        File target = new File(home, "usr");
        File stage = new File(home, "python-runtime.next");
        File previous = new File(home, "python-runtime.previous");
        synchronized (LOCK) {
            if (!target.exists() && previous.exists() && !previous.renameTo(target))
                throw new IOException("Cannot restore previous Python runtime");
            File marker = new File(target, ".complete");
            if (marker.isFile() && expected.equals(new String(
                    Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim()))
                return target;
            byte[] block = new byte[65536];
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = context.getAssets().open("runtime.zip")) {
                int n;
                while ((n = input.read(block)) != -1) digest.update(block, 0, n);
            }
            if (!expected.equals(hex(digest.digest())))
                throw new IOException("Python base runtime SHA-256 mismatch");
            removeManaged(home, stage);
            if (!stage.mkdirs()) throw new IOException("Cannot create Python runtime staging dir");
            try {
                int entries = 0;
                long total = 0;
                try (ZipInputStream zip = new ZipInputStream(context.getAssets().open("runtime.zip"))) {
                    ZipEntry member;
                    while ((member = zip.getNextEntry()) != null) {
                        if (++entries > 1000 || !member.getName().startsWith("usr/")
                                || member.getName().contains("..")
                                || member.getName().contains("\\"))
                            throw new IOException("Unsafe Python runtime member");
                        File output = new File(stage, member.getName()).getCanonicalFile();
                        if (!output.getPath().startsWith(stage.getCanonicalPath() + File.separator))
                            throw new IOException("Python runtime ZIP escapes staging");
                        if (member.isDirectory()) {
                            if (!output.mkdirs() && !output.isDirectory())
                                throw new IOException("Cannot create Python runtime folder");
                        } else {
                            File parent = output.getParentFile();
                            if (!parent.mkdirs() && !parent.isDirectory())
                                throw new IOException("Cannot create Python runtime folder");
                            try (FileOutputStream file = new FileOutputStream(output)) {
                                int n;
                                long size = 0;
                                while ((n = zip.read(block)) != -1) {
                                    size += n;
                                    total += n;
                                    if (size > 12L * 1024 * 1024 || total > 80L * 1024 * 1024)
                                        throw new IOException("Python runtime exceeds size budget");
                                    file.write(block, 0, n);
                                }
                            }
                        }
                        zip.closeEntry();
                    }
                }
                File prepared = new File(stage, "usr");
                if (!new File(prepared, "lib/libpython3.14.so").isFile()
                        || !new File(prepared,
                                "lib/python3.14/site-packages/python_pack_bridge.py").isFile())
                    throw new IOException("Python base runtime is incomplete");
                try (FileOutputStream done = new FileOutputStream(new File(prepared, ".complete"))) {
                    done.write(expected.getBytes(StandardCharsets.UTF_8));
                    done.getFD().sync();
                }
                removeManaged(home, previous);
                boolean old = target.exists();
                if (old && !target.renameTo(previous))
                    throw new IOException("Cannot preserve old Python runtime");
                if (!prepared.renameTo(target)) {
                    if (old) previous.renameTo(target);
                    throw new IOException("Cannot activate Python runtime");
                }
                removeManaged(home, previous);
                return target;
            } finally {
                removeManaged(home, stage);
            }
        }
    }

    private static void removeManaged(File home, File target) throws IOException {
        if (!target.exists()) return;
        File canonical = target.getCanonicalFile();
        if (!home.getCanonicalFile().equals(canonical.getParentFile())
                || !(canonical.getName().equals("python-runtime.next")
                || canonical.getName().equals("python-runtime.previous")))
            throw new IOException("Refusing to remove a non-runtime directory");
        deleteTree(canonical);
    }

    private static void deleteTree(File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath()))
            throw new IOException("Python runtime directory contains a symlink");
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot list Python runtime directory");
            for (File child : children) deleteTree(child);
        }
        if (!file.delete()) throw new IOException("Cannot delete Python runtime staging file");
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }
}
