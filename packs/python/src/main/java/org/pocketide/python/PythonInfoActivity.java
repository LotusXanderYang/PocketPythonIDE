package org.pocketide.python;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** An installed pack has a human-readable entry without launching a runtime task. */
public final class PythonInfoActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView info = new TextView(this);
        info.setText("Pocket Python 基础语言包\nCPython 3.14.6 · ARM64\n"
                + "在 Pocket Android IDE 的语言包入口验证并启用。\n"
                + "仅含标准库，不含 PyTorch 或 NumPy。\n"
                + "文件在 IDE 与语言包各自的私有空间处理。\n"
                + "许可证与兼容范围见随包说明。");
        info.setTextSize(18);
        info.setPadding(32, 48, 32, 32);
        setContentView(info);
    }
}
