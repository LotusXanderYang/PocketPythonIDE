package com.example.pocketpythonide;

import java.io.IOException;

/** Shared API v1 workspace input budget, checked before and during copying. */
public final class PackInputLimits {
    public static final int MAX_FILES = 2000;
    public static final long MAX_FILE_BYTES = 1024L * 1024;
    public static final long MAX_TOTAL_BYTES = 8L * 1024 * 1024;

    private PackInputLimits() { }

    public static long checkedTotal(String path, long fileBytes, long previousTotal)
            throws IOException {
        if (fileBytes > MAX_FILE_BYTES)
            throw new IOException("语言包输入文件超过 1 MiB：" + path);
        if (fileBytes > MAX_TOTAL_BYTES - previousTotal)
            throw new IOException("语言包输入总量超过 8 MiB，达到：" + path);
        return previousTotal + fileBytes;
    }
}
