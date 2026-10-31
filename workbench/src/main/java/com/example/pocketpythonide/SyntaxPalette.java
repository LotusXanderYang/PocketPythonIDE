package com.example.pocketpythonide;

import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONObject;

/** Editor token colors. Custom values are scoped to the current light/dark theme. */
final class SyntaxPalette {
    static final String PREF_DARK = "ide_syntax_palette_dark";
    static final String PREF_LIGHT = "ide_syntax_palette_light";
    static final String[] NAMES = {"基础文字", "关键字", "字符串", "数字", "注释", "预处理"};
    static final String[] KEYS = {"plain", "keyword", "string", "number", "comment", "directive"};
    private static final SyntaxPalette LIGHT = new SyntaxPalette(Color.rgb(30, 40, 38),
            Color.rgb(104, 61, 143), Color.rgb(142, 71, 42), Color.rgb(73, 103, 40),
            Color.rgb(82, 104, 81), Color.rgb(53, 94, 143));
    private static final SyntaxPalette DARK = new SyntaxPalette(Color.rgb(232, 235, 228),
            Color.rgb(197, 134, 192), Color.rgb(206, 145, 120), Color.rgb(181, 206, 168),
            Color.rgb(106, 153, 85), Color.rgb(125, 180, 226));
    private static final SyntaxPalette CPP_LIGHT = new SyntaxPalette(Color.rgb(30, 40, 38),
            Color.rgb(106, 60, 139), Color.rgb(146, 72, 42), Color.rgb(75, 105, 42),
            Color.rgb(80, 111, 81), Color.rgb(53, 94, 143));
    private static final SyntaxPalette CPP_DARK = new SyntaxPalette(Color.rgb(232, 235, 228),
            Color.rgb(197, 134, 192), Color.rgb(206, 145, 120), Color.rgb(181, 206, 168),
            Color.rgb(106, 153, 85), Color.rgb(125, 180, 226));

    final int plain, keyword, string, number, comment, directive;

    SyntaxPalette(int plain, int keyword, int string, int number, int comment, int directive) {
        this.plain = plain;
        this.keyword = keyword;
        this.string = string;
        this.number = number;
        this.comment = comment;
        this.directive = directive;
    }

    static SyntaxPalette defaults(boolean light) {
        return light ? LIGHT : DARK;
    }

    static SyntaxPalette cppDefaults(boolean light) {
        return light ? CPP_LIGHT : CPP_DARK;
    }

    static SyntaxPalette load(SharedPreferences prefs, boolean light) {
        String saved = prefs.getString(light ? PREF_LIGHT : PREF_DARK, null);
        SyntaxPalette fallback = defaults(light);
        if (saved == null) return fallback;
        try {
            JSONObject values = new JSONObject(saved);
            return new SyntaxPalette(parse(values.getString(KEYS[0])),
                    parse(values.getString(KEYS[1])), parse(values.getString(KEYS[2])),
                    parse(values.getString(KEYS[3])), parse(values.getString(KEYS[4])),
                    parse(values.getString(KEYS[5])));
        } catch (Exception ignored) { return fallback; }
    }

    String json() {
        try {
            JSONObject values = new JSONObject();
            int[] colors = colors();
            for (int i = 0; i < KEYS.length; i++) values.put(KEYS[i], hex(colors[i]));
            return values.toString();
        } catch (Exception impossible) { throw new AssertionError(impossible); }
    }

    int[] colors() { return new int[]{plain, keyword, string, number, comment, directive}; }

    static String hex(int color) {
        return String.format(java.util.Locale.ROOT, "#%06X", color & 0x00ffffff);
    }

    static int parse(String value) {
        if (value == null || !value.matches("(?i)#[0-9a-f]{6}"))
            throw new IllegalArgumentException("颜色应为 #RRGGBB");
        return Color.parseColor(value);
    }
}
