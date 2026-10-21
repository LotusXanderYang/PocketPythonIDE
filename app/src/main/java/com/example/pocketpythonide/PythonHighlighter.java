package com.example.pocketpythonide;

import android.graphics.Color;
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
    private static final int PURPLE = Color.rgb(197, 134, 192);
    private static final int GREEN = Color.rgb(206, 145, 120);
    private static final int LIME = Color.rgb(181, 206, 168);
    private static final int COMMENT = Color.rgb(106, 153, 85);

    static void apply(Editable text) {
        for (ForegroundColorSpan span : text.getSpans(0, text.length(), ForegroundColorSpan.class))
            text.removeSpan(span);
        if (text.length() > 50000) return;
        color(text, KEYWORDS, PURPLE);
        color(text, NUMBERS, LIME);
        color(text, STRINGS, GREEN);
        color(text, COMMENTS, COMMENT);
    }

    private static void color(Editable text, Pattern pattern, int color) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find())
            text.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
