package com.example.pocketpythonide;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A SAF document tree. The document provider remains the source of truth. */
final class WorkspaceFiles {
    static final class Entry {
        final String path;
        final Uri uri;
        final boolean directory;
        final long size;

        Entry(String path, Uri uri, boolean directory, long size) {
            this.path = path;
            this.uri = uri;
            this.directory = directory;
            this.size = size;
        }

        String name() {
            int slash = path.lastIndexOf('/');
            return path.substring(slash + 1);
        }
    }

    static final class Snapshot {
        final Map<String, byte[]> hashes = new HashMap<>();
        final Map<String, Uri> uris = new HashMap<>();
    }

    static final class SyncResult {
        int written;
        int conflicts;
    }

    private static final int MAX_ENTRIES = 2000;
    private static final long MAX_FILE_BYTES = 256L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;
    private static final Set<String> SKIP_DIRS = new HashSet<>(Arrays.asList(
            ".git", ".gradle", ".venv", "venv", "node_modules", "__pycache__", "build"));
    private final ContentResolver resolver;
    private final Uri tree;
    private final Uri root;
    private final String name;

    WorkspaceFiles(Context context, Uri tree) throws IOException {
        this.resolver = context.getContentResolver();
        this.tree = tree;
        this.root = DocumentsContract.buildDocumentUriUsingTree(tree,
                DocumentsContract.getTreeDocumentId(tree));
        this.name = queryName(root);
    }

    String name() { return name; }
    Uri treeUri() { return tree; }

    List<Entry> listTree() throws IOException {
        ArrayList<Entry> result = new ArrayList<>();
        collect(root, "", result, 0);
        return result;
    }

    private void collect(Uri parent, String prefix, List<Entry> out, int depth) throws IOException {
        if (depth > 24) throw new IOException("文件夹嵌套超过 24 层");
        List<Entry> children = children(parent, prefix);
        children.sort(Comparator.comparing((Entry e) -> !e.directory)
                .thenComparing(e -> e.name().toLowerCase(java.util.Locale.ROOT)));
        for (Entry child : children) {
            if (out.size() >= MAX_ENTRIES) throw new IOException("工作区超过 2000 个项目，请选择更小的文件夹");
            out.add(child);
            if (child.directory && !SKIP_DIRS.contains(child.name()))
                collect(child.uri, child.path, out, depth + 1);
        }
    }

    private List<Entry> children(Uri parent, String prefix) throws IOException {
        ArrayList<Entry> result = new ArrayList<>();
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE};
        try {
            Uri listing = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                    DocumentsContract.getDocumentId(parent));
            try (Cursor cursor = resolver.query(listing, columns, null, null, null)) {
                if (cursor == null) throw new IOException("文档提供者无法列出文件夹");
                while (cursor.moveToNext()) {
                    String id = cursor.getString(0);
                    String childName = cursor.getString(1);
                    String mime = cursor.getString(2);
                    if (childName == null || childName.isEmpty() || childName.contains("/")
                            || childName.equals(".") || childName.equals("..")) continue;
                    Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    long size = cursor.isNull(3) ? -1 : cursor.getLong(3);
                    result.add(new Entry(prefix.isEmpty() ? childName : prefix + "/" + childName,
                            uri, DocumentsContract.Document.MIME_TYPE_DIR.equals(mime), size));
                }
            }
        } catch (RuntimeException e) {
            throw providerError("列出文件夹", e);
        }
        return result;
    }

    Entry find(String path) throws IOException {
        if (path == null || path.isEmpty()) return new Entry("", root, true, -1);
        Uri parent = root;
        String prefix = "";
        Entry match = null;
        for (String part : path.split("/")) {
            match = null;
            for (Entry entry : children(parent, prefix)) {
                if (entry.name().equals(part)) { match = entry; break; }
            }
            if (match == null) return null;
            parent = match.uri;
            prefix = match.path;
        }
        return match;
    }

    Entry create(String parentPath, String filename, boolean directory) throws IOException {
        if (!validSegment(filename)) throw new IOException("名称不能包含路径分隔符或特殊路径");
        Entry parent = find(parentPath);
        if (parent == null || !parent.directory) throw new IOException("目标文件夹不存在");
        String path = parentPath.isEmpty() ? filename : parentPath + "/" + filename;
        if (find(path) != null) throw new IOException("同名文件或文件夹已存在");
        String mime = directory ? DocumentsContract.Document.MIME_TYPE_DIR
                : mimeType(filename);
        try {
            Uri uri = DocumentsContract.createDocument(resolver, parent.uri, mime, filename);
            if (uri == null) throw new IOException("文档提供者未创建文件");
            String actualName = queryName(uri);
            String actualPath = parentPath.isEmpty() ? actualName : parentPath + "/" + actualName;
            return new Entry(actualPath, uri, directory, 0);
        } catch (RuntimeException e) { throw providerError("创建文件", e); }
    }

    byte[] read(Entry entry, int limit) throws IOException {
        if (entry.directory) throw new IOException("不能把文件夹作为文本打开");
        if (entry.size > limit) throw new IOException("文件超过编辑器的 2 MB 限制");
        try (InputStream in = resolver.openInputStream(entry.uri)) {
            if (in == null) throw new IOException("无法打开文件");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > limit) throw new IOException("文件超过编辑器的 2 MB 限制");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (RuntimeException e) { throw providerError("读取文件", e); }
    }

    void write(Entry entry, byte[] bytes) throws IOException {
        if (entry.directory) throw new IOException("不能写入文件夹");
        try (OutputStream out = resolver.openOutputStream(entry.uri, "wt")) {
            if (out == null) throw new IOException("无法写入文件");
            out.write(bytes);
        } catch (RuntimeException e) { throw providerError("写入文件", e); }
    }

    Snapshot copyToMirror(File mirror) throws IOException {
        clearMirror(mirror);
        if (!mirror.mkdirs() && !mirror.isDirectory()) throw new IOException("无法创建运行目录");
        Snapshot snapshot = new Snapshot();
        long total = 0;
        for (Entry entry : listTree()) {
            if (skipped(entry.path)) continue;
            File target = safeChild(mirror, entry.path);
            if (entry.directory) {
                if (!target.mkdirs() && !target.isDirectory()) throw new IOException("无法创建 " + entry.path);
                continue;
            }
            if (entry.size > MAX_FILE_BYTES) throw new IOException(entry.path + " 超过 256 MB 运行限制");
            File parent = target.getParentFile();
            if (!parent.mkdirs() && !parent.isDirectory()) throw new IOException("无法创建 " + parent);
            MessageDigest digest = sha256();
            try (InputStream in = resolver.openInputStream(entry.uri);
                    OutputStream out = new FileOutputStream(target)) {
                if (in == null) throw new IOException("无法读取 " + entry.path);
                byte[] buffer = new byte[64 * 1024];
                int n;
                long fileBytes = 0;
                while ((n = in.read(buffer)) != -1) {
                    fileBytes += n;
                    total += n;
                    if (fileBytes > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES)
                        throw new IOException("工作区运行副本超过大小限制（单文件 256 MB，总计 512 MB）");
                    digest.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                }
            }
            snapshot.hashes.put(entry.path, digest.digest());
            snapshot.uris.put(entry.path, entry.uri);
        }
        return snapshot;
    }

    SyncResult copyChangesBack(File mirror, Snapshot snapshot) throws IOException {
        SyncResult result = new SyncResult();
        ArrayList<File> files = new ArrayList<>();
        gatherFiles(mirror, files);
        long total = 0;
        for (File file : files) {
            String path = mirror.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
            if (skipped(path)) continue;
            total += file.length();
            if (file.length() > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES)
                throw new IOException("运行输出超过大小限制，未完整写回");
            byte[] now = hashFile(file);
            byte[] before = snapshot.hashes.get(path);
            if (Arrays.equals(now, before)) continue;
            Entry destination;
            if (before == null) {
                destination = createParentsAndFile(path);
            } else {
                Uri uri = snapshot.uris.get(path);
                destination = new Entry(path, uri, false, -1);
                if (!Arrays.equals(hashUri(uri), before)) {
                    result.conflicts++;
                    continue;
                }
            }
            try (InputStream in = new FileInputStream(file);
                    OutputStream out = resolver.openOutputStream(destination.uri, "wt")) {
                if (out == null) throw new IOException("无法写回 " + path);
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            result.written++;
        }
        return result;
    }

    private Entry createParentsAndFile(String path) throws IOException {
        int slash = path.lastIndexOf('/');
        String parent = slash < 0 ? "" : path.substring(0, slash);
        if (!parent.isEmpty()) {
            String built = "";
            for (String part : parent.split("/")) {
                String next = built.isEmpty() ? part : built + "/" + part;
                Entry entry = find(next);
                if (entry == null) create(built, part, true);
                else if (!entry.directory) throw new IOException(next + " 不是文件夹");
                built = next;
            }
        }
        Entry existing = find(path);
        if (existing != null) throw new IOException(path + " 已被外部创建，未覆盖");
        return create(parent, path.substring(slash + 1), false);
    }

    private byte[] hashUri(Uri uri) throws IOException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("无法核对文件变更");
            MessageDigest digest = sha256();
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
            return digest.digest();
        }
    }

    private static byte[] hashFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = sha256();
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
            return digest.digest();
        }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    private static void gatherFiles(File dir, List<File> out) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) throw new IOException("无法读取运行目录");
        for (File child : children) {
            if (Files.isSymbolicLink(child.toPath())) continue;
            if (child.isDirectory()) {
                if (!SKIP_DIRS.contains(child.getName())) gatherFiles(child, out);
            }
            else if (child.isFile()) {
                if (out.size() >= MAX_ENTRIES) throw new IOException("运行目录文件过多，未写回");
                out.add(child);
            }
        }
    }

    private static void clearMirror(File dir) throws IOException {
        if (!dir.exists()) return;
        if (Files.isSymbolicLink(dir.toPath())) throw new IOException("运行目录是符号链接");
        File[] children = dir.listFiles();
        if (children == null) throw new IOException("无法清理运行目录");
        for (File child : children) {
            if (child.isDirectory() && !Files.isSymbolicLink(child.toPath())) clearMirror(child);
            if (!child.delete()) throw new IOException("无法清理 " + child.getName());
        }
    }

    static void removeMirror(File dir) throws IOException {
        clearMirror(dir);
        Files.deleteIfExists(dir.toPath());
    }

    private static File safeChild(File root, String path) throws IOException {
        File target = new File(root, path);
        if (!target.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator))
            throw new IOException("无效的工作区路径");
        return target;
    }

    private static boolean skipped(String path) {
        for (String part : path.split("/")) if (SKIP_DIRS.contains(part)) return true;
        return false;
    }

    private static boolean validSegment(String name) {
        return name != null && !name.isEmpty() && !name.equals(".") && !name.equals("..")
                && !name.contains("/") && !name.contains("\\") && name.length() <= 100;
    }

    private static String mimeType(String name) {
        // Some Android/Harmony document providers reject nonstandard Python MIME types.
        if (name.endsWith(".py") || name.endsWith(".pyi")) return "text/plain";
        String guessed = java.net.URLConnection.guessContentTypeFromName(name);
        return guessed == null ? "application/octet-stream" : guessed;
    }

    private static IOException providerError(String operation, RuntimeException cause) {
        String detail = cause.getMessage();
        String type = cause.getClass().getSimpleName();
        return new IOException("文档提供者无法" + operation + "（" + type + "）"
                + (detail == null || detail.isEmpty() ? "" : "：" + detail), cause);
    }

    private String queryName(Uri uri) throws IOException {
        try (Cursor cursor = resolver.query(uri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IOException("无法访问所选文件夹");
            String displayName = cursor.getString(0);
            if (displayName == null || displayName.isEmpty()) throw new IOException("文档提供者未返回文件名称");
            return displayName;
        } catch (RuntimeException e) { throw providerError("读取文件名称", e); }
    }
}
