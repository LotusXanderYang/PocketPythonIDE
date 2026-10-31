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
import android.widget.ScrollView;
import android.widget.TextView;

/** Caret-anchored completion list that leaves the editor and IME focused. */
final class EditorCompletionPopup {
    private static final int BACKGROUND = Color.rgb(43, 45, 50);
    private static final int BORDER = Color.rgb(77, 80, 87);
    private static final int HIGHLIGHT = Color.rgb(61, 81, 105);
    private static final int TEXT = Color.rgb(223, 226, 230);
    private static final int MUTED = Color.rgb(162, 167, 177);
    private static final int FOOTER_HEIGHT = 40;
    private final Activity activity;
    private final EditorEditText editor;
    private final CompletionCatalog catalog;
    private PopupWindow popup;
    private LinearLayout panel;
    private LinearLayout suggestions;
    private ScrollView suggestionScroll;
    private PythonCompletions.Result result;
    private int selected;
    private boolean inserting;

    EditorCompletionPopup(Activity activity, EditorEditText editor, CompletionCatalog catalog) {
        this.activity = activity;
        this.editor = editor;
        this.catalog = catalog;
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
        show(PythonCompletions.suggest(editor.getText().toString(),
                editor.getSelectionStart(), catalog));
    }

    void show(PythonCompletions.Result candidateResult) {
        if (!editor.hasFocus() || editor.getSelectionStart() != candidateResult.end
                || editor.getSelectionEnd() != candidateResult.end) {
            dismiss();
            return;
        }
        result = candidateResult;
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
            suggestionScroll = new ScrollView(activity);
            suggestionScroll.setVerticalScrollBarEnabled(true);
            suggestions = new LinearLayout(activity);
            suggestions.setOrientation(LinearLayout.VERTICAL);
            suggestionScroll.addView(suggestions);
            panel.addView(suggestionScroll, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView hint = new TextView(activity);
            hint.setText("↑↓ 选择 · Enter 插入 · Ctrl+K / 点此关闭");
            hint.setTextColor(MUTED);
            hint.setTextSize(10);
            hint.setGravity(Gravity.CENTER_VERTICAL);
            hint.setPadding(dp(11), 0, 0, 0);
            hint.setContentDescription("关闭代码提示");
            hint.setOnClickListener(view -> dismiss());
            panel.addView(hint, new LinearLayout.LayoutParams(-1, dp(FOOTER_HEIGHT)));
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
        if (popup != null && popup.isShowing()
                && (keyCode == KeyEvent.KEYCODE_K || keyCode == KeyEvent.KEYCODE_PERIOD)
                && event.isCtrlPressed()
                && !event.isAltPressed() && !event.isMetaPressed()) {
            dismiss();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_SPACE && event.isCtrlPressed()) {
            if (popup != null && popup.isShowing()) dismiss();
            else update();
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
        suggestions.removeAllViews();
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
            suggestions.addView(row, new LinearLayout.LayoutParams(-1, dp(33)));
        }
        suggestionScroll.post(() -> {
            int rowTop = selected * dp(33);
            int visibleTop = suggestionScroll.getScrollY();
            int visibleHeight = suggestionScroll.getHeight();
            if (visibleHeight <= 0) return;
            if (rowTop < visibleTop) suggestionScroll.scrollTo(0, rowTop);
            else if (rowTop + dp(33) > visibleTop + visibleHeight)
                suggestionScroll.scrollTo(0, rowTop + dp(33) - visibleHeight);
        });
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
        int desiredRows = Math.min(result.items.size(), 6);
        int desiredHeight = desiredRows * dp(33) + dp(FOOTER_HEIGHT);
        popup.setWidth(width);
        int x = origin[0] + editor.getTotalPaddingLeft()
                + Math.round(layout.getPrimaryHorizontal(caret)) - editor.getScrollX();
        x = Math.max(frame.left + dp(4), Math.min(x, frame.right - width - dp(4)));
        int below = origin[1] + editor.getTotalPaddingTop()
                + layout.getLineBottom(line) - editor.getScrollY() + dp(2);
        int above = origin[1] + editor.getTotalPaddingTop()
                + layout.getLineTop(line) - editor.getScrollY() - dp(2);
        int spaceBelow = Math.max(0, frame.bottom - below - dp(4));
        int spaceAbove = Math.max(0, above - frame.top - dp(4));
        boolean placeAbove = spaceBelow < desiredHeight && spaceAbove > spaceBelow;
        int available = placeAbove ? spaceAbove : spaceBelow;
        int visibleRows = Math.max(1, Math.min(desiredRows,
                Math.max(1, (available - dp(FOOTER_HEIGHT)) / dp(33))));
        int height = Math.min(desiredHeight, visibleRows * dp(33) + dp(FOOTER_HEIGHT));
        height = Math.min(height, Math.max(dp(33 + FOOTER_HEIGHT), frame.height() - dp(8)));
        int y = placeAbove ? above - height : below;
        y = Math.max(frame.top + dp(4), Math.min(y, frame.bottom - height - dp(4)));
        if (popup.isShowing()) popup.update(x, y, width, height);
        else {
            popup.setHeight(height);
            popup.showAtLocation(editor, Gravity.TOP | Gravity.LEFT, x, y);
        }
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
