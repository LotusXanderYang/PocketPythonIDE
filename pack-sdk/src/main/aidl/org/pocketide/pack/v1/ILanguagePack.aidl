package org.pocketide.pack.v1;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.pocketide.pack.v1.ITaskCallback;

interface ILanguagePack {
    int getProtocolVersion();
    void submit(in Bundle request, in ParcelFileDescriptor input,
            in ParcelFileDescriptor output, ITaskCallback callback);
    void cancel(String taskId);
}
