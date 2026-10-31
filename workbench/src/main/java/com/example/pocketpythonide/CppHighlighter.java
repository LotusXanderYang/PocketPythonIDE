package com.example.pocketpythonide;

import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Lightweight C/C++ lexical colors for course files; compiler owns diagnostics. */
final class CppHighlighter {
    private static final Set<String> WORDS = new HashSet<>(Arrays.asList((
            "alignas alignof auto bool break case catch char class const constexpr continue "
            + "default delete do double else enum explicit extern false float for friend goto "
            + "if inline int long namespace new noexcept nullptr operator private protected "
            + "public register restrict return short signed sizeof static struct switch "
            + "template this throw true try typedef typename union unsigned using virtual void "
            + "volatile while").split(" ")));

    private CppHighlighter() { }

    static void apply(Editable text, boolean light) {
        apply(text, SyntaxPalette.cppDefaults(light));
    }

    static void apply(Editable text, SyntaxPalette palette) {
        for (ForegroundColorSpan span : text.getSpans(0, text.length(), ForegroundColorSpan.class))
            text.removeSpan(span);
        if (text.length() > 50_000) return;
        int keyword = palette.keyword;
        int string = palette.string;
        int comment = palette.comment;
        int number = palette.number;
        int directive = palette.directive;
        int i = 0;
        boolean lineStart = true;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\n') { i++; lineStart = true; continue; }
            if (lineStart && Character.isWhitespace(c)) { i++; continue; }
            if (lineStart && c == '#') {
                int start = i;
                while (i < text.length() && text.charAt(i) != '\n') i++;
                color(text, start, i, directive);
                lineStart = false;
                continue;
            }
            lineStart = false;
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                int start = i;
                while (i < text.length() && text.charAt(i) != '\n') i++;
                color(text, start, i, comment);
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i + 1 < text.length()
                        && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++;
                i = Math.min(text.length(), i + 2);
                color(text, start, i, comment);
                continue;
            }
            if (c == '"' || c == '\'') {
                int start = i++;
                while (i < text.length()) {
                    char next = text.charAt(i++);
                    if (next == '\\' && i < text.length()) { i++; continue; }
                    if (next == c) break;
                }
                color(text, start, i, string);
                continue;
            }
            if (Character.isDigit(c)) {
                int start = i++;
                while (i < text.length()
                        && (Character.isLetterOrDigit(text.charAt(i))
                        || text.charAt(i) == '.' || text.charAt(i) == '_')) i++;
                color(text, start, i, number);
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < text.length()
                        && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) i++;
                if (WORDS.contains(text.subSequence(start, i).toString()))
                    color(text, start, i, keyword);
                continue;
            }
            i++;
        }
    }

    private static void color(Editable text, int start, int end, int color) {
        if (end > start) text.setSpan(new ForegroundColorSpan(color), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
