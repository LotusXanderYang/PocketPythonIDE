package org.pocketide.test.probe;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;

import org.pocketide.pack.v1.ILanguagePack;
import org.pocketide.pack.v1.PackContract;

/** Separate UID used only to prove the sample service rejects unauthorized callers. */
public final class ProbeActivity extends Activity {
    private static final String TAG = "PocketPackProbe";
    private ServiceConnection connection;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView result = new TextView(this);
        result.setText("检查语言包权限…");
        setContentView(result);
        Intent intent = new Intent(PackContract.ACTION).setComponent(new ComponentName(
                "org.pocketide.sample.echo", "org.pocketide.sample.echo.EchoPackService"));
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                new Thread(() -> {
                    String verdict;
                    try {
                        ILanguagePack.Stub.asInterface(binder).getProtocolVersion();
                        verdict = "PERMISSION_BYPASS";
                    } catch (SecurityException denied) {
                        verdict = "PERMISSION_DENIED_OK";
                    } catch (Exception failure) {
                        verdict = "PROBE_ERROR " + failure;
                    }
                    Log.i(TAG, verdict);
                    String display = verdict;
                    runOnUiThread(() -> result.setText(display));
                }, "permission-probe").start();
            }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        if (!bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            result.setText("PROBE_BIND_FAILED");
            connection = null;
        }
    }

    @Override protected void onDestroy() {
        if (connection != null) unbindService(connection);
        super.onDestroy();
    }
}
