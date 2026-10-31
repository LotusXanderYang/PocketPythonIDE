package com.example.pocketpythonide;

import java.util.Objects;

/** Keeps the launch file stable while the debugger navigates through stack frames. */
final class DebugLaunchTarget {
    private String entry, workspace;
    private String shownFile, shownWorkspace;

    void fileShown(String path, String key, boolean debuggerNavigation) {
        boolean changed = !Objects.equals(path, shownFile)
                || !Objects.equals(key, shownWorkspace);
        if (changed && !debuggerNavigation) {
            entry = null;
            workspace = null;
        }
        shownFile = path;
        shownWorkspace = key;
    }

    void launched(String path, String key) {
        entry = path;
        workspace = key;
    }

    String entryFor(String path, String key) {
        return entry != null && key.equals(workspace) ? entry : path;
    }
}
