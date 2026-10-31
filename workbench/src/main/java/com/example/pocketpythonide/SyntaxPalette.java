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
    static final String[] PRESET_NAMES = {"松绿 · 默认", "海蓝", "暖琥珀", "高对比"};
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

    static SyntaxPalette preset(int index, boolean light) {
        switch (index) {
            case 1:
                return light ? new SyntaxPalette(0xff1e2933, 0xff385fa3, 0xff8b4f65,
                        0xff326b74, 0xff526c72, 0xff47609b)
                        : new SyntaxPalette(0xffe6eef5, 0xffa6c8ff, 0xffe3adbc,
                                0xff9bd6d8, 0xffa5bbc0, 0xffb8b5f4);
            case 2:
                return light ? new SyntaxPalette(0xff302820, 0xff854b16, 0xff9a3f46,
                        0xff536b2e, 0xff6d6759, 0xff74509a)
                        : new SyntaxPalette(0xfff4eee2, 0xffffc47d, 0xfff3a0a2,
                                0xffb9d898, 0xffb7b0a2, 0xffcab7f0);
            case 3:
                return light ? new SyntaxPalette(0xff111111, 0xff243f9b, 0xff8a2736,
                        0xff235c28, 0xff505050, 0xff5a358e)
                        : new SyntaxPalette(0xffffffff, 0xffffff7a, 0xffffa7b6,
                                0xffa9f5b4, 0xffcccccc, 0xffa9d8ff);
            default: return defaults(light);
        }
    }

    static int[] colorChoices(boolean light) {
        return light ? new int[]{0xff1e2933, 0xff385fa3, 0xff8b4f65, 0xff326b74,
                0xff526c72, 0xff47609b, 0xff854b16, 0xff9a3f46, 0xff536b2e,
                0xff74509a, 0xff111111, 0xff243f9b, 0xff8a2736, 0xff235c28,
                0xff505050, 0xff5a358e, 0xff00748c, 0xff935f13}
                : new int[]{0xffe6eef5, 0xffa6c8ff, 0xffe3adbc, 0xff9bd6d8,
                        0xffa5bbc0, 0xffb8b5f4, 0xffffc47d, 0xfff3a0a2,
                        0xffb9d898, 0xffcab7f0, 0xffffffff, 0xffffff7a,
                        0xffffa7b6, 0xffa9f5b4, 0xffcccccc, 0xffa9d8ff,
                        0xff93e5f2, 0xffffd79d};
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
