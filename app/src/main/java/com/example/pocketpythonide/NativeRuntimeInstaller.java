package com.example.pocketpythonide;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class NativeRuntimeInstaller {
    private NativeRuntimeInstaller() {}

    static File install(Context context) throws Exception {
        JSONObject manifest;
        try (InputStream input = context.getAssets().open("runtime.json")) {
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) content.write(buffer, 0, count);
            manifest = new JSONObject(content.toString(StandardCharsets.UTF_8.name()));
        }
        String expected = manifest.getString("asset_sha256");
        if (!"com.example.pocketpythonide".equals(manifest.getString("app_id"))
                || !"3.14.6".equals(manifest.getString("python"))
                || !"2.14.1".equals(manifest.getString("torch"))
                || !expected.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid embedded runtime manifest");
        }
        File root = context.getFilesDir();
        File target = new File(root, "usr");
        File backup = new File(root, "usr-backup");
        File stage = new File(root, "runtime-stage");
        if (!target.exists() && backup.exists()) {
            if (!backup.renameTo(target)) throw new IOException("Cannot restore runtime backup");
        }
        File marker = new File(target, ".pocketpy-sha256");
        if (marker.isFile() && expected.equals(new String(
                Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim())) {
            return target;
        }
        deleteTree(stage.toPath());
        if (!stage.mkdirs()) throw new IOException("Cannot create runtime staging directory");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = context.getAssets().open("runtime.zip")) {
                byte[] buffer = new byte[1 << 16];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            if (!expected.equals(hex(digest.digest()))) {
                throw new IOException("Embedded runtime SHA-256 mismatch");
            }
            try (ZipInputStream archive = new ZipInputStream(
                    context.getAssets().open("runtime.zip"))) {
                ZipEntry entry;
                byte[] buffer = new byte[1 << 16];
                while ((entry = archive.getNextEntry()) != null) {
                    if (!entry.getName().startsWith("usr/"))
                        throw new IOException("Unexpected runtime ZIP path");
                    File output = new File(stage, entry.getName());
                    String stagePath = stage.getCanonicalPath() + File.separator;
                    if (!output.getCanonicalPath().startsWith(stagePath))
                        throw new IOException("Runtime ZIP path escapes staging directory");
                    if (entry.isDirectory()) {
                        if (!output.mkdirs() && !output.isDirectory())
                            throw new IOException("Cannot create " + output);
                    } else {
                        File parent = output.getParentFile();
                        if (!parent.mkdirs() && !parent.isDirectory())
                            throw new IOException("Cannot create " + parent);
                        try (OutputStream file = Files.newOutputStream(output.toPath())) {
                            int count;
                            while ((count = archive.read(buffer)) != -1)
                                file.write(buffer, 0, count);
                        }
                    }
                    archive.closeEntry();
                }
            }
            File stagedUsr = new File(stage, "usr");
            if (!new File(stagedUsr, "lib/libpython3.14.so").isFile()
                    || !new File(stagedUsr, "lib/python3.14/site-packages/torch/__init__.py").isFile()) {
                throw new IOException("Embedded runtime is incomplete");
            }
            Files.write(new File(stagedUsr, ".pocketpy-sha256").toPath(),
                    expected.getBytes(StandardCharsets.UTF_8));
            deleteTree(backup.toPath());
            if (target.exists() && !target.renameTo(backup))
                throw new IOException("Cannot back up previous runtime");
            if (!stagedUsr.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target);
                throw new IOException("Cannot activate embedded runtime");
            }
            deleteTree(backup.toPath());
            return target;
        } finally {
            deleteTree(stage.toPath());
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException error)
                    throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
