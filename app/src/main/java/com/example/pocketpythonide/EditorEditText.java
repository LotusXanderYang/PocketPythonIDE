package com.example.pocketpythonide;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.view.KeyEvent;
import android.widget.EditText;

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
        return super.onKeyDown(keyCode, event);
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
