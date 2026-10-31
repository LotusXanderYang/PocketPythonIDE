package com.example.pocketpythonide;

/** Small, side-effect-free editing rules shared by hardware and software keyboards. */
final class PythonEditorRules {
    private PythonEditorRules() { }

    static char matchingClose(char opening) {
        switch (opening) {
            case '(': return ')';
            case '[': return ']';
            case '{': return '}';
            case '\'': return '\'';
            case '"': return '"';
            default: return 0;
        }
    }

    static boolean isClosing(char value) {
        return value == ')' || value == ']' || value == '}'
                || value == '\'' || value == '"';
    }

    static boolean canAutoPair(CharSequence source, int caret, char typed) {
        if (matchingClose(typed) == 0) return false;
        int end = Math.max(0, Math.min(caret, source.length()));
        int lineStart = end;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;
        char quote = 0;
        boolean escaped = false;
        for (int i = lineStart; i < end; i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (c == '#') return false;
            else if (c == '\'' || c == '"') quote = c;
        }
        if (quote != 0) return false;
        if (typed == '\'' || typed == '"') {
            int slashes = 0;
            for (int i = end - 1; i >= lineStart && source.charAt(i) == '\\'; i--) slashes++;
            if ((slashes & 1) != 0) return false;
        }
        return true;
    }

    static String indentationAfterNewline(CharSequence source, int caret) {
        int end = Math.max(0, Math.min(caret, source.length()));
        int lineStart = end;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;
        int prefixEnd = lineStart;
        while (prefixEnd < end && (source.charAt(prefixEnd) == ' '
                || source.charAt(prefixEnd) == '\t')) prefixEnd++;
        String prefix = source.subSequence(lineStart, prefixEnd).toString();

        char quote = 0;
        boolean escaped = false;
        int bracketDepth = 0;
        char lastCode = 0;
        for (int i = lineStart; i < end; i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '#') break;
            if (c == '\'' || c == '"') { quote = c; lastCode = c; continue; }
            if (c == '(' || c == '[' || c == '{') bracketDepth++;
            else if (c == ')' || c == ']' || c == '}') bracketDepth--;
            if (!Character.isWhitespace(c)) lastCode = c;
        }
        return lastCode == ':' && quote == 0 && bracketDepth <= 0
                ? prefix + "    " : prefix;
    }
}
