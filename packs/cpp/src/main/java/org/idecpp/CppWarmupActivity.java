package org.idecpp;

import android.app.Activity;
import android.os.Bundle;

/** Some Honor builds need an explicit activity launch before cross-app bind. */
public final class CppWarmupActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        finish();
    }
}
