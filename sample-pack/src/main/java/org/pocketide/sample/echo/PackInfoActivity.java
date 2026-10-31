package org.pocketide.sample.echo;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Lets users open the installed sample pack on devices that restrict cold service starts. */
public final class PackInfoActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView message = new TextView(this);
        message.setText("Pocket Echo 语言包已安装。返回 Pocket Android IDE，在语言能力中验证并启用。");
        message.setTextSize(18);
        message.setPadding(32, 48, 32, 32);
        setContentView(message);
    }
}
