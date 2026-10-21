package com.example.pocketpythonide;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.Layout;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/** Caret-anchored completion list that leaves the editor and IME focused. */
final class EditorCompletionPopup {
    private static final int BACKGROUND = Color.rgb(43, 45, 50);
    private static final int BORDER = Color.rgb(77, 80, 87);
    private static final int HIGHLIGHT = Color.rgb(61, 81, 105);
    private static final int TEXT = Color.rgb(223, 226, 230);
    private static final int MUTED = Color.rgb(162, 167, 177);
    private final Activity activity;
    private final EditorEditText editor;
    private PopupWindow popup;
    private LinearLayout panel;
    private PythonCompletions.Result result;
    private int selected;
    private boolean inserting;

    EditorCompletionPopup(Activity activity, EditorEditText editor) {
        this.activity = activity;
        this.editor = editor;
    }

    boolean isInserting() { return inserting; }

    void dismiss() {
        if (popup != null) popup.dismiss();
        result = null;
    }

    void update() {
        if (!editor.hasFocus() || editor.getSelectionStart() != editor.getSelectionEnd()) {
            dismiss();
            return;
        }
        result = PythonCompletions.suggest(editor.getText().toString(), editor.getSelectionStart());
        if (result.items.isEmpty()) { dismiss(); return; }
        selected = 0;
        if (popup == null) {
            panel = new LinearLayout(activity);
            panel.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable background = new GradientDrawable();
            background.setColor(BACKGROUND);
            background.setCornerRadius(dp(7));
            background.setStroke(dp(1), BORDER);
            panel.setBackground(background);
            popup = new PopupWindow(panel, dp(318), -2, false);
            popup.setBackgroundDrawable(background);
            popup.setElevation(dp(9));
            popup.setClippingEnabled(true);
            popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        }
        render();
        position();
    }

    boolean onKey(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE && event.isCtrlPressed()) {
            update();
            return true;
        }
        if (popup == null || !popup.isShowing() || result == null || result.items.isEmpty()
                || event.isCtrlPressed() || event.isAltPressed() || event.isMetaPressed()) return false;
        if (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK) {
            dismiss(); return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            selected = (selected + (keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)
                    + result.items.size()) % result.items.size();
            render();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            dismiss();
            return false;
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            accept(selected);
            return true;
        }
        return false;
    }

    private void accept(int index) {
        if (result == null || index < 0 || index >= result.items.size()) return;
        Editable text = editor.getText();
        if (result.end > text.length() || editor.getSelectionStart() != result.end) {
            dismiss(); return;
        }
        PythonCompletions.Item item = result.items.get(index);
        int start = result.start;
        int end = result.end;
        String insertion = item.insert;
        int caretBack = item.caretBack;
        if (caretBack == 1 && end < text.length() && text.charAt(end) == '(') {
            insertion = item.label;
            caretBack = 0;
        }
        int caret = start + insertion.length() - caretBack;
        inserting = true;
        dismiss();
        text.replace(start, end, insertion);
        editor.setSelection(Math.min(caret, text.length()));
        inserting = false;
        editor.requestFocus();
    }

    private void render() {
        panel.removeAllViews();
        for (int i = 0; i < result.items.size(); i++) {
            final int index = i;
            PythonCompletions.Item item = result.items.get(i);
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(11), 0, dp(11), 0);
            row.setBackgroundColor(i == selected ? HIGHLIGHT : Color.TRANSPARENT);
            TextView name = new TextView(activity);
            name.setText(item.label);
            name.setTextColor(TEXT);
            name.setTextSize(14);
            name.setTypeface(Typeface.MONOSPACE);
            name.setSingleLine(true);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(33), 1));
            name.setGravity(Gravity.CENTER_VERTICAL);
            TextView kind = new TextView(activity);
            kind.setText(item.kind);
            kind.setTextColor(MUTED);
            kind.setTextSize(11);
            kind.setSingleLine(true);
            row.addView(kind);
            row.setOnClickListener(view -> accept(index));
            panel.addView(row, new LinearLayout.LayoutParams(-1, dp(33)));
        }
        TextView hint = new TextView(activity);
        hint.setText("↑↓ 选择    Enter 插入    Tab 缩进");
        hint.setTextColor(MUTED);
        hint.setTextSize(10);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setPadding(dp(11), 0, 0, 0);
        panel.addView(hint, new LinearLayout.LayoutParams(-1, dp(25)));
    }

    private void position() {
        Layout layout = editor.getLayout();
        if (layout == null) return;
        int caret = editor.getSelectionStart();
        int line = layout.getLineForOffset(caret);
        int[] origin = new int[2];
        editor.getLocationOnScreen(origin);
        Rect frame = new Rect();
        editor.getWindowVisibleDisplayFrame(frame);
        int width = Math.min(dp(318), Math.max(dp(200), frame.width() - dp(16)));
        int height = result.items.size() * dp(33) + dp(25);
        popup.setWidth(width);
        int x = origin[0] + editor.getTotalPaddingLeft()
                + Math.round(layout.getPrimaryHorizontal(caret)) - editor.getScrollX();
        x = Math.max(frame.left + dp(4), Math.min(x, frame.right - width - dp(4)));
        int y = origin[1] + editor.getTotalPaddingTop()
                + layout.getLineBottom(line) - editor.getScrollY() + dp(2);
        if (y + height > frame.bottom - dp(6))
            y = origin[1] + editor.getTotalPaddingTop()
                    + layout.getLineTop(line) - editor.getScrollY() - height - dp(2);
        y = Math.max(frame.top + dp(4), y);
        if (popup.isShowing()) popup.update(x, y, width, height);
        else popup.showAtLocation(editor, Gravity.TOP | Gravity.LEFT, x, y);
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
