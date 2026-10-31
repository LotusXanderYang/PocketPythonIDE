package org.pocketide.pack.debug.v1;

/** Optional, same-signature debug channel alongside Language Pack API v1. */
public final class DebugContract {
    private DebugContract() { }
    public static final int PROTOCOL_VERSION = 1;
    public static final String ACTION = "org.pocketide.pack.debug.v1.DEBUG_PACK";
    public static final String SESSION_ID = "sessionId";
    public static final String VERSION = "debugProtocolVersion";
    public static final String ENTRY = "entry";
    public static final String STATUS = "status";
    public static final String MESSAGE = "message";
}
