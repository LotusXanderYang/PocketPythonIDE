package org.pocketide.pack.v1;

import android.os.Bundle;

oneway interface ITaskCallback {
    void finished(in Bundle result);
}
