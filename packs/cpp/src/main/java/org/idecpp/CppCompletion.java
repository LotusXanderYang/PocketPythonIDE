package org.idecpp;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded v1 text completions: keywords, common library names, project symbols. */
final class CppCompletion {
    private static final String C_WORDS = "auto break case char const continue default do double else enum extern float for goto if inline int long register restrict return short signed sizeof static struct switch typedef union unsigned void volatile while _Bool _Static_assert printf fprintf scanf puts fgets malloc free memcpy strlen strcmp NULL size_t FILE stdin stdout stderr";
    private static final String CPP_WORDS = "alignas alignof bool break case catch char class const constexpr continue decltype default delete do double else enum explicit false float for friend if inline int long namespace new noexcept nullptr operator private protected public return short signed sizeof static struct switch template this throw true try typename using virtual void volatile while std cout cin cerr endl string vector array map unordered_map unique_ptr make_unique move begin end size push_back emplace_back";
    private static final Pattern NAME = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\(|(?=;))");
    private static final Pattern PREFIX = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)$");

    private CppCompletion() { }

    static String suggest(CppProject project, int cursor) throws Exception {
        String source = new String(Files.readAllBytes(project.entry.toPath()), StandardCharsets.UTF_8);
        String before = cursor >= 0 && cursor <= source.length()
                ? source.substring(0, cursor) : source;
        Matcher prefixMatch = PREFIX.matcher(before);
        String prefix = prefixMatch.find() ? prefixMatch.group(1) : "";
        Set<String> words = new LinkedHashSet<>();
        for (String value : (project.cpp ? CPP_WORDS : C_WORDS).split(" ")) words.add(value);
        for (File file : project.sources) {
            String code = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Matcher names = NAME.matcher(code.substring(0, Math.min(code.length(), 100_000)));
            while (names.find()) words.add(names.group(1));
        }
        List<String> matches = new ArrayList<>();
        for (String word : words) {
            if (word.startsWith(prefix) && !word.equals(prefix)) matches.add(word);
            if (matches.size() >= 32) break;
        }
        return String.join("\n", matches) + (matches.isEmpty() ? "" : "\n");
    }
}
