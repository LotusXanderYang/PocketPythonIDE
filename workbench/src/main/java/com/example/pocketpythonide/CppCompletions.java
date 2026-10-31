package com.example.pocketpythonide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immediate editor hints; the pack remains the source of project-wide completions. */
final class CppCompletions {
    private static final String C_WORDS = "auto break case char const continue default do double else enum extern float for goto if inline int long register restrict return short signed sizeof static struct switch typedef union unsigned void volatile while _Bool _Static_assert";
    private static final String CPP_WORDS = "alignas alignof bool break case catch char class const constexpr continue decltype default delete do double else enum explicit false float for friend if inline int long namespace new noexcept nullptr operator private protected public return short signed sizeof static struct switch template this throw true try typename using virtual void volatile while";
    private static final String C_COMMON = "printf fprintf scanf puts fgets malloc free memcpy strlen strcmp NULL size_t FILE stdin stdout stderr";
    private static final String CPP_COMMON = "std cout cin cerr endl string vector array map unordered_map unique_ptr make_unique move begin end size push_back emplace_back";
    private static final Pattern DECLARATION = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(\\(|(?=;))");

    private CppCompletions() { }

    static PythonCompletions.Result suggest(String source, int cursor, boolean cpp) {
        if (cursor < 0 || cursor > source.length()) return empty(cursor);
        int start = cursor;
        while (start > 0 && identifierPart(source.charAt(start - 1))) start--;
        if (start == cursor || !identifierStart(source.charAt(start))
                || cursor < source.length() && identifierPart(source.charAt(cursor)))
            return empty(cursor);
        String prefix = source.substring(start, cursor);
        LinkedHashMap<String, PythonCompletions.Item> words = new LinkedHashMap<>();
        add(words, cpp ? CPP_WORDS : C_WORDS, "关键字", false);
        add(words, cpp ? CPP_COMMON : C_COMMON, "常用符号", false);
        String scanned = source.length() > 100_000
                ? source.substring(source.length() - 100_000) : source;
        Matcher declarations = DECLARATION.matcher(scanned);
        while (declarations.find()) {
            String name = declarations.group(1);
            if (name.startsWith(prefix) && !name.equals(prefix))
                words.put(name, new PythonCompletions.Item(name, "当前文件", false));
        }
        ArrayList<PythonCompletions.Item> matches = new ArrayList<>();
        for (PythonCompletions.Item item : words.values())
            if (item.label.startsWith(prefix) && !item.label.equals(prefix)) matches.add(item);
        matches.sort(Comparator.comparingInt((PythonCompletions.Item item) ->
                item.kind.equals("当前文件") ? 0 : item.kind.equals("常用符号") ? 1 : 2)
                .thenComparing(item -> item.label));
        if (matches.size() > 8) matches.subList(8, matches.size()).clear();
        return new PythonCompletions.Result(start, cursor, matches);
    }

    private static void add(Map<String, PythonCompletions.Item> result, String values,
            String kind, boolean callable) {
        for (String value : values.split(" "))
            result.putIfAbsent(value, new PythonCompletions.Item(value, kind, callable));
    }

    private static boolean identifierStart(char c) {
        return c == '_' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    private static boolean identifierPart(char c) {
        return identifierStart(c) || c >= '0' && c <= '9';
    }

    private static PythonCompletions.Result empty(int cursor) {
        return new PythonCompletions.Result(cursor, cursor, new ArrayList<>());
    }
}
