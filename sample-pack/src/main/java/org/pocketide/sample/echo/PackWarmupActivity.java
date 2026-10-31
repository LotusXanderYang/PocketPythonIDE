package org.pocketide.sample.echo;

import android.app.Activity;
import android.os.Bundle;

/** Starts this package's process on devices that block cross-package cold service starts. */
public final class PackWarmupActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        finish();
    }
}
