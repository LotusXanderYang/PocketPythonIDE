package com.example.pocketpythonide;

public final class DebugLaunchTargetSmokeTest {
    private static void expect(String actual, String wanted) {
        if (!wanted.equals(actual)) throw new AssertionError(actual + " != " + wanted);
    }

    public static void main(String[] args) {
        DebugLaunchTarget target = new DebugLaunchTarget();
        target.fileShown("debug_main.py", "workspace-a", false);
        target.launched("debug_main.py", "workspace-a");
        target.fileShown("debug_helper.py", "workspace-a", true);
        expect(target.entryFor("debug_helper.py", "workspace-a"), "debug_main.py");
        // Continuing to program exit does not navigate back from the helper.
        target.launched(target.entryFor("debug_helper.py", "workspace-a"), "workspace-a");
        target.fileShown("debug_helper.py", "workspace-a", true);
        expect(target.entryFor("debug_helper.py", "workspace-a"), "debug_main.py");
        target.fileShown("debug_helper.py", "workspace-b", false);
        expect(target.entryFor("debug_helper.py", "workspace-b"), "debug_helper.py");
        target.fileShown("other.py", "workspace-b", false);
        expect(target.entryFor("other.py", "workspace-b"), "other.py");
        System.out.println("Debug launch target survives debugger navigation and resets on user navigation: OK");
    }
}
