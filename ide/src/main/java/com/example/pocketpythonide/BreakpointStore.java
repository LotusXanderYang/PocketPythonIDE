package com.example.pocketpythonide;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Workspace-scoped breakpoints anchored to source text, with conservative remapping. */
final class BreakpointStore {
    static final class Mark {
        String path;
        int line;
        String text;
        String before;
        String after;
        Mark(String path, int line, String text, String before, String after) {
            this.path = path; this.line = line; this.text = text;
            this.before = before; this.after = after;
        }
    }

    private final Context context;
    private final String key;
    private final List<Mark> marks = new ArrayList<>();

    BreakpointStore(Context context, String workspaceKey) {
        this.context = context.getApplicationContext();
        key = "bp:" + workspaceKey;
        try {
            JSONArray saved = new JSONArray(context.getSharedPreferences("debug-breakpoints", 0)
                    .getString(key, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.getJSONObject(i);
                marks.add(new Mark(item.getString("path"), item.getInt("line"),
                        item.optString("text"), item.optString("before"),
                        item.optString("after")));
            }
        } catch (Exception ignored) { marks.clear(); }
    }

    synchronized List<Mark> all() { return new ArrayList<>(marks); }

    synchronized boolean has(String path, int line) {
        for (Mark mark : marks) if (mark.path.equals(path) && mark.line == line) return true;
        return false;
    }

    synchronized void toggle(File root, String path, int line) throws Exception {
        toggle(path, line, String.join("\n", lines(root, path)));
    }

    synchronized void toggle(String path, int line, String source) {
        for (int i = 0; i < marks.size(); i++) {
            Mark mark = marks.get(i);
            if (mark.path.equals(path) && mark.line == line) {
                marks.remove(i); save(); return;
            }
        }
        String[] lines = source.split("\n", -1);
        if (line < 1 || line > lines.length) return;
        if (normalize(lines[line - 1]).isEmpty()) return;
        marks.add(new Mark(path, line, normalize(lines[line - 1]),
                line > 1 ? normalize(lines[line - 2]) : "",
                line < lines.length ? normalize(lines[line]) : ""));
        save();
    }

    /** Return count of unresolved marks; never guess when a source anchor is ambiguous. */
    synchronized int remap(File root) throws Exception {
        List<String> files = new ArrayList<>();
        gather(root, root, files);
        int unresolved = 0;
        for (Mark mark : marks) {
            List<Candidate> choices = new ArrayList<>();
            for (String path : files) {
                String[] source = lines(root, path);
                for (int index = 0; index < source.length; index++) {
                    if (!normalize(source[index]).equals(mark.text)) continue;
                    int score = path.equals(mark.path) ? 100 : 0;
                    if (index > 0 && normalize(source[index - 1]).equals(mark.before)) score += 10;
                    if (index + 1 < source.length && normalize(source[index + 1]).equals(mark.after)) score += 10;
                    if (path.equals(mark.path)) score -= Math.min(20, Math.abs(index + 1 - mark.line));
                    choices.add(new Candidate(path, index + 1, score));
                }
            }
            Collections.sort(choices, (a, b) -> Integer.compare(b.score, a.score));
            if (choices.isEmpty() || (choices.size() > 1
                    && choices.get(0).score == choices.get(1).score)) {
                mark.line = 0; unresolved++; continue;
            }
            mark.path = choices.get(0).path;
            mark.line = choices.get(0).line;
        }
        save();
        return unresolved;
    }

    private synchronized void save() {
        try {
            JSONArray result = new JSONArray();
            for (Mark mark : marks) result.put(new JSONObject()
                    .put("path", mark.path).put("line", mark.line)
                    .put("text", mark.text).put("before", mark.before)
                    .put("after", mark.after));
            context.getSharedPreferences("debug-breakpoints", 0).edit()
                    .putString(key, result.toString()).apply();
        } catch (Exception failed) {
            throw new IllegalStateException("Cannot save breakpoints", failed);
        }
    }

    private static String normalize(String text) { return text.trim(); }

    private static String[] lines(File root, String relative) throws Exception {
        File file = new File(root, relative).getCanonicalFile();
        if (!file.getPath().startsWith(root.getCanonicalPath() + File.separator)
                || !file.isFile()) throw new Exception("Invalid breakpoint source");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                .split("\\n", -1);
    }

    private static void gather(File root, File dir, List<String> output) throws Exception {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (Files.isSymbolicLink(child.toPath())) continue;
            if (child.isDirectory()) gather(root, child, output);
            else if (supportsBreakpoints(child.getName()))
                output.add(root.toPath().relativize(child.toPath()).toString()
                        .replace(File.separatorChar, '/'));
        }
    }

    private static boolean supportsBreakpoints(String name) {
        return name.endsWith(".py") || name.endsWith(".c")
                || name.endsWith(".cpp") || name.endsWith(".cc")
                || name.endsWith(".cxx");
    }

    private static final class Candidate {
        final String path; final int line; final int score;
        Candidate(String path, int line, int score) {
            this.path = path; this.line = line; this.score = score;
        }
    }
}
