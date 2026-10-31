package org.pocketide.pack.v1;

/** Stable strings shared by host and independently installed executable packs. */
public final class PackContract {
    private PackContract() {}

    public static final int PROTOCOL_VERSION = 1;
    public static final String ACTION = "org.pocketide.pack.v1.LANGUAGE_PACK";
    public static final String MANIFEST_META_DATA = "org.pocketide.pack.v1.MANIFEST";
    public static final String TASK_ID = "taskId";
    public static final String VERSION = "protocolVersion";
    public static final String OPERATION = "operation";
    public static final String WORKSPACE = "workspace";
    public static final String ENTRY = "entry";
    public static final String STATUS = "status";
    public static final String ERROR_CODE = "errorCode";
    public static final String MESSAGE = "message";
    public static final String EXIT_CODE = "exitCode";
    public static final String LINE = "line";
    public static final String COLUMN = "column";
    public static final String END_LINE = "endLine";
    public static final String END_COLUMN = "endColumn";
    public static final String RESULT_PATH = "resultPath";

    public static final String OK = "ok";
    public static final String ERROR = "error";
    public static final String CANCELLED = "cancelled";
    public static final String UNSUPPORTED = "unsupported";
}
