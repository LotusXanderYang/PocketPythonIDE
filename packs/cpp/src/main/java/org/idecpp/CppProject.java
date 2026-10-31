package org.idecpp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Validated single-target course project, copied from the host's ZIP snapshot. */
final class CppProject {
    final File root;
    final File entry;
    final List<File> sources;
    final List<File> includes;
    final String standard;
    final boolean cpp;
    final byte[] stdin;
    final List<String> arguments;

    private CppProject(File root, File entry, List<File> sources, List<File> includes,
            String standard, boolean cpp, byte[] stdin, List<String> arguments) {
        this.root = root;
        this.entry = entry;
        this.sources = sources;
        this.includes = includes;
        this.standard = standard;
        this.cpp = cpp;
        this.stdin = stdin;
        this.arguments = arguments;
    }

    static CppProject read(InputStream input, File root, String entryPath) throws Exception {
        if (!root.mkdirs()) throw new IOException("Cannot create private task directory");
        int count = 0;
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry member;
            byte[] buffer = new byte[8192];
            while ((member = zip.getNextEntry()) != null) {
                if (++count > 2000) throw new IOException("Project contains too many files");
                File destination = inside(root, member.getName());
                if (member.isDirectory()) {
                    if (!destination.mkdirs() && !destination.isDirectory())
                        throw new IOException("Cannot create project folder");
                    continue;
                }
                File parent = destination.getParentFile();
                if (!parent.mkdirs() && !parent.isDirectory())
                    throw new IOException("Cannot create project folder");
                try (FileOutputStream output = new FileOutputStream(destination)) {
                    int size;
                    long fileSize = 0;
                    while ((size = zip.read(buffer)) != -1) {
                        fileSize += size;
                        total += size;
                        if (fileSize > 1024 * 1024 || total > 8L * 1024 * 1024)
                            throw new IOException("Project exceeds API v1 size limits");
                        output.write(buffer, 0, size);
                    }
                }
            }
        }
        File entry = inside(root, entryPath);
        if (!entry.isFile()) throw new IOException("Entry absent from workspace snapshot: " + entryPath);
        boolean cpp = isCpp(entryPath);
        if (!cpp && !isC(entryPath)) throw new IOException("Only C and C++ source entries can run");
        File configuration = new File(entry.getParentFile(), ".pocket-cpp.json");
        if (!configuration.isFile()) configuration = new File(root, ".pocket-cpp.json");
        JSONObject config = configuration.isFile()
                ? new JSONObject(new String(Files.readAllBytes(configuration.toPath()), StandardCharsets.UTF_8))
                : new JSONObject();
        String standard = config.optString("standard", cpp ? "c++17" : "c11");
        if (!(cpp ? standard.equals("c++17") || standard.equals("c++20")
                : standard.equals("c11") || standard.equals("c17")))
            throw new IOException("Unsupported -std for this project: " + standard);
        List<File> sources = new ArrayList<>();
        JSONArray declared = config.optJSONArray("sources");
        if (declared == null) sources.add(entry);
        else {
            if (declared.length() < 1 || declared.length() > 64)
                throw new IOException("sources must contain 1–64 files");
            for (int i = 0; i < declared.length(); i++) {
                String path = declared.getString(i);
                if (cpp != isCpp(path) || (!cpp && !isC(path)))
                    throw new IOException("Mixed C/C++ source lists are not supported: " + path);
                File source = inside(root, path);
                if (!source.isFile()) throw new IOException("Source missing: " + path);
                if (sources.contains(source)) throw new IOException("Duplicate source: " + path);
                sources.add(source);
            }
            if (!sources.contains(entry)) throw new IOException("Current file is not in sources");
        }
        List<File> includes = new ArrayList<>();
        includes.add(root);
        JSONArray declaredIncludes = config.optJSONArray("includeDirs");
        if (declaredIncludes != null) {
            if (declaredIncludes.length() > 16) throw new IOException("Too many includeDirs");
            for (int i = 0; i < declaredIncludes.length(); i++) {
                File folder = inside(root, declaredIncludes.getString(i));
                if (!folder.isDirectory()) throw new IOException("Include directory missing: " + folder);
                includes.add(folder);
            }
        }
        String inputText = config.optString("stdin", "");
        byte[] stdin = inputText.getBytes(StandardCharsets.UTF_8);
        if (stdin.length > 64 * 1024) throw new IOException("stdin exceeds 64 KiB");
        List<String> arguments = new ArrayList<>();
        JSONArray declaredArguments = config.optJSONArray("args");
        if (declaredArguments != null) {
            if (declaredArguments.length() > 16) throw new IOException("Too many args");
            for (int i = 0; i < declaredArguments.length(); i++) {
                String value = declaredArguments.getString(i);
                if (value.length() > 1024 || value.indexOf('\0') >= 0)
                    throw new IOException("Invalid program argument");
                arguments.add(value);
            }
        }
        return new CppProject(root, entry, sources, includes, standard, cpp, stdin, arguments);
    }

    static File inside(File root, String path) throws IOException {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.contains("\\")
                || path.equals(".") || path.contains("//"))
            throw new IOException("Unsafe project path: " + path);
        for (String component : path.split("/"))
            if (component.equals(".") || component.equals("..") || component.isEmpty())
                throw new IOException("Unsafe project path: " + path);
        File destination = new File(root, path).getCanonicalFile();
        if (!destination.getPath().startsWith(root.getCanonicalPath() + File.separator))
            throw new IOException("Project path escapes task directory");
        return destination;
    }

    private static boolean isC(String name) { return name.endsWith(".c"); }
    private static boolean isCpp(String name) {
        return name.endsWith(".cpp") || name.endsWith(".cc") || name.endsWith(".cxx");
    }
}
