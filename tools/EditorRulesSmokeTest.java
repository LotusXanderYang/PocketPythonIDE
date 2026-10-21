package com.example.pocketpythonide;

/** Run with javac/java to check the editor's pure indentation rules without an emulator. */
public final class EditorRulesSmokeTest {
    private static void check(String code, String expected) {
        String actual = PythonEditorRules.indentationAfterNewline(code, code.length());
        if (!expected.equals(actual)) throw new AssertionError(
                "Expected " + expected.length() + " spaces after: " + code + ", got " + actual.length());
    }

    public static void main(String[] args) {
        check("if ready:", "    ");
        check("    if ready:", "        ");
        check("    print('ready')", "    ");
        check("if ready:  # explain", "    ");
        check("# note:", "");
        check("print(':')", "");
        check("if ready: 'text'", "");
        check("mapping = {'key':", "");
        check("def f(x: int) -> None:", "    ");
        if (PythonEditorRules.matchingClose('(') != ')'
                || PythonEditorRules.matchingClose('[') != ']'
                || PythonEditorRules.matchingClose('{') != '}'
                || PythonEditorRules.matchingClose('\'') != '\''
                || PythonEditorRules.matchingClose('"') != '"')
            throw new AssertionError("Delimiter pairing failed");
        if (!PythonEditorRules.canAutoPair("print", 5, '(')
                || PythonEditorRules.canAutoPair("print('hi", 9, '(')
                || PythonEditorRules.canAutoPair("# comment", 9, '"')
                || PythonEditorRules.canAutoPair("x = \\", 5, '"'))
            throw new AssertionError("Context-aware pairing failed");
        System.out.println("Editor rules OK");
    }
}
