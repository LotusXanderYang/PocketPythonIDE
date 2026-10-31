package org.pocketide.python;

import android.app.Activity;
import android.os.Bundle;

/** Starts this APK's process on devices that block a cold cross-package service bind. */
public final class PythonWarmupActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        finish();
    }
}
