package com.example.pocketpythonide;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

import java.util.ArrayList;

/** Reports caret movement from touch and hardware keyboards to the status bar. */
public class EditorEditText extends EditText {
    private Runnable selectionListener;
    private CompletionKeyListener completionKeyListener;
    private final Paint linePaint = new Paint();

    public EditorEditText(Context context) {
        super(context);
        linePaint.setColor(Color.rgb(40, 40, 40));
    }

    public void setSelectionListener(Runnable listener) { selectionListener = listener; }

    interface CompletionKeyListener { boolean onKey(int keyCode, KeyEvent event); }

    public void setCompletionKeyListener(CompletionKeyListener listener) {
        completionKeyListener = listener;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (completionKeyListener != null && completionKeyListener.onKey(keyCode, event)) return true;
        if (!event.isCtrlPressed() && !event.isAltPressed() && !event.isMetaPressed()) {
            if (keyCode == KeyEvent.KEYCODE_TAB) {
                indentSelection(event.isShiftPressed());
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                insertNewline();
                return true;
            }
            int unicode = event.getUnicodeChar();
            if (unicode > 0 && unicode <= Character.MAX_VALUE
                    && typeDelimiter((char) unicode)) return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection connection = super.onCreateInputConnection(outAttrs);
        if (connection == null) return null;
        return new InputConnectionWrapper(connection, false) {
            @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                if (text != null && text.length() == 1) {
                    char typed = text.charAt(0);
                    if (typed == '\n') { insertNewline(); return true; }
                    if (typed == '\t') { indentSelection(false); return true; }
                    if (typeDelimiter(typed)) return true;
                }
                return super.commitText(text, newCursorPosition);
            }
        };
    }

    private void insertNewline() {
        Editable text = getText();
        int start = Math.min(getSelectionStart(), getSelectionEnd());
        int end = Math.max(getSelectionStart(), getSelectionEnd());
        String insertion = "\n" + PythonEditorRules.indentationAfterNewline(text, start);
        text.replace(start, end, insertion);
        setSelection(start + insertion.length());
    }

    private boolean typeDelimiter(char typed) {
        Editable text = getText();
        int start = Math.min(getSelectionStart(), getSelectionEnd());
        int end = Math.max(getSelectionStart(), getSelectionEnd());
        if (start == end && PythonEditorRules.isClosing(typed)
                && start < text.length() && text.charAt(start) == typed) {
            setSelection(start + 1);
            return true;
        }
        char close = PythonEditorRules.matchingClose(typed);
        if (close == 0) return false;
        if (!PythonEditorRules.canAutoPair(text, start, typed)) return false;
        if (start != end) {
            String selected = text.subSequence(start, end).toString();
            text.replace(start, end, typed + selected + close);
            setSelection(start + 1, start + selected.length() + 1);
        } else {
            text.insert(start, new String(new char[]{typed, close}));
            setSelection(start + 1);
        }
        return true;
    }

    private void indentSelection(boolean outdent) {
        Editable text = getText();
        int start = Math.min(getSelectionStart(), getSelectionEnd());
        int end = Math.max(getSelectionStart(), getSelectionEnd());
        if (start == end && !outdent) {
            text.insert(start, "    ");
            setSelection(start + 4);
            return;
        }
        int firstLine = lineStart(text, start);
        if (start == end) {
            int removed = removeIndent(text, firstLine);
            setSelection(Math.max(firstLine, start - removed));
            return;
        }
        int last = end > start && text.charAt(end - 1) == '\n' ? end - 1 : end;
        ArrayList<Integer> starts = new ArrayList<>();
        starts.add(firstLine);
        for (int i = firstLine; i < last; i++)
            if (text.charAt(i) == '\n' && i + 1 <= last) starts.add(i + 1);
        int delta = 0;
        for (int i = starts.size() - 1; i >= 0; i--) {
            int position = starts.get(i);
            if (outdent) delta -= removeIndent(text, position);
            else { text.insert(position, "    "); delta += 4; }
        }
        setSelection(firstLine, Math.max(firstLine, end + delta));
    }

    private static int lineStart(CharSequence text, int position) {
        while (position > 0 && text.charAt(position - 1) != '\n') position--;
        return position;
    }

    private static int removeIndent(Editable text, int position) {
        if (position < text.length() && text.charAt(position) == '\t') {
            text.delete(position, position + 1);
            return 1;
        }
        int end = position;
        while (end < text.length() && end - position < 4 && text.charAt(end) == ' ') end++;
        text.delete(position, end);
        return end - position;
    }

    @Override protected void onSelectionChanged(int start, int end) {
        super.onSelectionChanged(start, end);
        if (selectionListener != null) selectionListener.run();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (layout != null) {
            int line = layout.getLineForOffset(Math.max(0, getSelectionStart()));
            int top = getTotalPaddingTop() + layout.getLineTop(line) - getScrollY();
            int bottom = getTotalPaddingTop() + layout.getLineBottom(line) - getScrollY();
            canvas.drawRect(0, top, getWidth(), bottom, linePaint);
        }
        super.onDraw(canvas);
    }
}
