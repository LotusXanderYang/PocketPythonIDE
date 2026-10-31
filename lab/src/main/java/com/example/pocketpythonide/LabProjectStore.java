package com.example.pocketpythonide;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Private Lab projects; SAF access is limited to explicit import/export actions. */
final class LabProjectStore {
    private final Context context;
    private final File root;

    LabProjectStore(Context context) throws IOException {
        this.context = context.getApplicationContext();
        root = new File(context.getFilesDir(), "projects");
        if (!root.mkdirs() && !root.isDirectory())
            throw new IOException("无法创建 Lab 项目目录");
    }

    File open(String name) throws IOException {
        if (!name.matches("[A-Za-z0-9_-]{1,40}"))
            throw new IOException("项目名只可使用英文字母、数字、_ 和 -，最多 40 字符");
        File folder = new File(root, name).getCanonicalFile();
        if (!root.getCanonicalFile().equals(folder.getParentFile()) || !folder.isDirectory())
            throw new IOException("项目不存在：" + name);
        return folder;
    }

    String[] names() {
        String[] values = root.list((parent, name) ->
                name.matches("[A-Za-z0-9_-]{1,40}") && new File(parent, name).isDirectory());
        if (values == null) return new String[0];
        Arrays.sort(values);
        return values;
    }

    File create(String name, String sample) throws Exception {
        if (!name.matches("[A-Za-z0-9_-]{1,40}"))
            throw new IOException("项目名只可使用英文字母、数字、_ 和 -，最多 40 字符");
        File project = new File(root, name).getCanonicalFile();
        if (!root.getCanonicalFile().equals(project.getParentFile()))
            throw new IOException("项目路径无效");
        if (!project.mkdir()) throw new IOException("同名项目已存在：" + name);
        for (String subdir : new String[] {"data", "logs", "metrics", "checkpoints", "runs"})
            if (!new File(project, subdir).mkdir())
                throw new IOException("无法创建项目目录：" + subdir);
        if ("matrix".equals(sample) || "tiny".equals(sample)) {
            copyAsset("examples/" + sample + "_main.py", new File(project, "main.py"));
            copyAsset("examples/" + sample + "_params.json", new File(project, "params.json"));
        } else {
            Files.write(new File(project, "main.py").toPath(),
                    "print('Lab CPU project ready')\n".getBytes(StandardCharsets.UTF_8));
            Files.write(new File(project, "params.json").toPath(), "{}\n".getBytes(
                    StandardCharsets.UTF_8));
        }
        return project;
    }

    String uniqueName(String stem) {
        if (!new File(root, stem).exists()) return stem;
        for (int number = 1; number < 1000; number++) {
            String candidate = stem + "_" + number;
            if (!new File(root, candidate).exists()) return candidate;
        }
        return stem + "_" + System.currentTimeMillis();
    }

    String read(File project, String name) throws IOException {
        File file = editable(project, name);
        if (file.length() > 2L * 1024 * 1024)
            throw new IOException("编辑文件超过 2 MiB");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    void save(File project, String name, String source) throws IOException {
        File target = editable(project, name);
        byte[] content = source.getBytes(StandardCharsets.UTF_8);
        if (content.length > 2L * 1024 * 1024)
            throw new IOException("编辑内容超过 2 MiB");
        File temporary = new File(project, "." + name + ".saving");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(content);
                output.getFD().sync();
            }
            Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }

    private static File editable(File project, String name) throws IOException {
        if (!"main.py".equals(name) && !"params.json".equals(name))
            throw new IOException("只能编辑 main.py 或 params.json");
        File file = new File(project, name).getCanonicalFile();
        if (!project.getCanonicalFile().equals(file.getParentFile()) || !file.isFile())
            throw new IOException("文件不存在：" + name);
        return file;
    }

    String importData(File project, Uri uri) throws Exception {
        String original = "dataset.csv";
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null)
                original = cursor.getString(0);
        }
        String safe = original.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..") || safe.startsWith("."))
            safe = "dataset.csv";
        if (safe.length() > 80) safe = safe.substring(safe.length() - 80);
        File data = new File(project, "data");
        File target = new File(data, safe);
        if (target.exists()) {
            int dot = safe.lastIndexOf('.');
            String stem = dot < 0 ? safe : safe.substring(0, dot);
            String suffix = dot < 0 ? "" : safe.substring(dot);
            for (int index = 2; index <= 999; index++) {
                target = new File(data, stem + "_" + index + suffix);
                if (!target.exists()) break;
            }
            if (target.exists()) throw new IOException("无法生成不冲突的数据文件名");
        }
        File temporary = File.createTempFile(".import-", ".tmp", data);
        try {
            long total = 0;
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                    FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IOException("文件提供者拒绝读取数据");
                byte[] block = new byte[65536];
                int size;
                while ((size = input.read(block)) != -1) {
                    total += size;
                    if (total > 10L * 1024 * 1024)
                        throw new IOException("数据文件超过 10 MiB 导入上限");
                    output.write(block, 0, size);
                }
                output.getFD().sync();
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary.toPath()); }
        return "data/" + target.getName();
    }

    void setDataFileIfEmpty(File project, String path) throws Exception {
        File params = new File(project, "params.json");
        JSONObject settings = new JSONObject(read(project, "params.json"));
        if (!settings.optString("data_file").isEmpty()) return;
        settings.put("data_file", path);
        save(project, "params.json", settings.toString(2) + "\n");
    }

    void saveRun(File project, String runId, JSONObject report) throws IOException {
        if (!runId.matches("[0-9a-f-]{36}")) throw new IOException("无效任务 ID");
        File file = new File(new File(project, "runs"), runId + ".json");
        if (file.exists()) return; // The service's completed result takes precedence.
        File temporary = File.createTempFile(".run-", ".tmp", file.getParentFile());
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write((report.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            if (!file.exists()) Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }

    void exportResults(File project, Uri uri) throws Exception {
        File temporary = File.createTempFile("lab-export-", ".zip", context.getCacheDir());
        try {
            List<File> selected = new ArrayList<>();
            selected.add(new File(project, "main.py"));
            selected.add(new File(project, "params.json"));
            for (String folder : new String[] {"logs", "metrics", "checkpoints", "runs"}) {
                File[] contents = new File(project, folder).listFiles();
                if (contents != null) selected.addAll(Arrays.asList(contents));
            }
            long total = 0;
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(temporary))) {
                for (File file : selected) {
                    if (!file.isFile() || Files.isSymbolicLink(file.toPath())) continue;
                    String name = project.toPath().relativize(file.toPath()).toString()
                            .replace(File.separatorChar, '/');
                    if (file.length() > 50L * 1024 * 1024
                            || (total += file.length()) > 100L * 1024 * 1024)
                        throw new IOException("结果归档超过 100 MiB；请先减少旧检查点");
                    zip.putNextEntry(new ZipEntry(name));
                    try (InputStream input = new FileInputStream(file)) {
                        byte[] block = new byte[65536];
                        int count;
                        while ((count = input.read(block)) != -1) zip.write(block, 0, count);
                    }
                    zip.closeEntry();
                }
            }
            try (InputStream source = new FileInputStream(temporary);
                    OutputStream destination = context.getContentResolver().openOutputStream(uri, "w")) {
                if (destination == null) throw new IOException("文件提供者拒绝导出");
                byte[] block = new byte[65536];
                int count;
                while ((count = source.read(block)) != -1) destination.write(block, 0, count);
                destination.flush();
            }
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }

    private void copyAsset(String name, File target) throws IOException {
        try (InputStream input = context.getAssets().open(name);
                FileOutputStream output = new FileOutputStream(target)) {
            byte[] block = new byte[8192];
            int size;
            while ((size = input.read(block)) != -1) output.write(block, 0, size);
            output.getFD().sync();
        }
    }
}
