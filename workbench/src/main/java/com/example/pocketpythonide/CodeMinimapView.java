package com.example.pocketpythonide;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

/** Compact overview of the current script, aligned with the editor viewport. */
public class CodeMinimapView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String[] lines = new String[0];
    private EditText editor;

    public CodeMinimapView(Context context) { super(context); }

    public void setEditor(EditText editor) { this.editor = editor; }

    public void setSource(String source) {
        lines = source.split("\n", -1);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float row = getResources().getDisplayMetrics().density * 2.5f;
        int count = Math.min(lines.length, (int) (getHeight() / row));
        for (int i = 0; i < count; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            int color = line.startsWith("#") ? Color.rgb(89, 139, 77)
                    : line.startsWith("def ") || line.startsWith("class ")
                    ? Color.rgb(153, 106, 152) : Color.rgb(130, 130, 130);
            paint.setColor(color);
            paint.setStrokeWidth(Math.max(1f, row * 0.43f));
            int leading = 0;
            while (leading < lines[i].length() && Character.isWhitespace(lines[i].charAt(leading))) leading++;
            float indent = Math.min(14, leading);
            float x1 = 3 + indent * getResources().getDisplayMetrics().density;
            float x2 = Math.min(getWidth() - 3, x1 + line.length() * row * 0.8f);
            canvas.drawLine(x1, i * row + row, x2, i * row + row, paint);
        }
        if (editor != null) {
            Layout layout = editor.getLayout();
            if (layout != null && layout.getHeight() > 0) {
                float scale = (float) getHeight() / Math.max(getHeight(), layout.getHeight());
                float top = editor.getScrollY() * scale;
                float bottom = Math.min(getHeight(), top + editor.getHeight() * scale);
                paint.setColor(Color.argb(48, 170, 170, 170));
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRect(0, top, getWidth(), bottom, paint);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (editor == null) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN
                || event.getAction() == MotionEvent.ACTION_MOVE) {
            Layout layout = editor.getLayout();
            if (layout != null && getHeight() > 0) {
                int max = Math.max(0, layout.getHeight() - editor.getHeight());
                int y = Math.max(0, Math.min(max, (int) (event.getY() / getHeight() * max)));
                editor.scrollTo(editor.getScrollX(), y);
                invalidate();
            }
            return true;
        }
        return event.getAction() == MotionEvent.ACTION_UP || super.onTouchEvent(event);
    }
}
