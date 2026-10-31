package com.example.pocketpythonide;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Uninstall revokes approval so a later reinstall requires explicit validation. */
public final class PackRemovalReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) || intent.getData() == null)
            return;
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
        String removed = intent.getData().getSchemeSpecificPart();
        String enabled = context.getSharedPreferences("language_packs", 0)
                .getString("packId", "");
        if (enabled.equals(removed)) new PackRegistry(context).disable();
    }
}
