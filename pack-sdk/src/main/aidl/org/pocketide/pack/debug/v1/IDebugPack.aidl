package org.pocketide.pack.debug.v1;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.pocketide.pack.debug.v1.IDebugCallback;

interface IDebugPack {
    int getProtocolVersion();
    void start(in Bundle request, in ParcelFileDescriptor input,
            in ParcelFileDescriptor channel,
            IDebugCallback callback);
    void stop(String sessionId);
}
