package org.pocketide.pack.debug.v1;

import android.os.Bundle;

oneway interface IDebugCallback {
    void ready(in Bundle info);
    void finished(in Bundle result);
}
