package com.example.pocketpythonide;

import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, non-parsing color pass for the prototype editor. */
final class PythonHighlighter {
    private static final Pattern KEYWORDS = Pattern.compile(
            "\\b(?:and|as|assert|async|await|break|class|continue|def|del|elif|else|except|False|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|True|try|while|with|yield|None)\\b");
    private static final Pattern NUMBERS = Pattern.compile("\\b(?:0[xX][0-9a-fA-F]+|\\d+(?:\\.\\d+)?)\\b");
    private static final Pattern STRINGS = Pattern.compile("(?:\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')");
    private static final Pattern COMMENTS = Pattern.compile("#[^\\n]*");

    static void apply(Editable text) {
        apply(text, false);
    }

    static void apply(Editable text, boolean light) {
        apply(text, SyntaxPalette.defaults(light));
    }

    static void apply(Editable text, SyntaxPalette palette) {
        for (ForegroundColorSpan span : text.getSpans(0, text.length(), ForegroundColorSpan.class))
            text.removeSpan(span);
        if (text.length() > 50000) return;
        color(text, KEYWORDS, palette.keyword);
        color(text, NUMBERS, palette.number);
        color(text, STRINGS, palette.string);
        color(text, COMMENTS, palette.comment);
    }

    private static void color(Editable text, Pattern pattern, int color) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find())
            text.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
