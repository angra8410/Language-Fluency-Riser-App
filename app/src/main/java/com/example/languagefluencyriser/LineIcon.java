package com.example.languagefluencyriser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Simple stroke icons drawn in a 24x24 grid (Lucide-style), so the app needs no image assets.
 * Icons are decorative: the clickable parent carries the accessible label.
 */
public class LineIcon extends View {
    public enum Type {
        HOME, REVIEW, HISTORY, SETTINGS, MIC, STOP, SEND, CLOSE, ARROW_RIGHT, CHEVRON_RIGHT,
        SPEAKER, CHECK, PAUSE, KEYBOARD, FLAME
    }

    private Type type;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public LineIcon(Context context, Type type, int color) {
        super(context);
        this.type = type;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(2f);
        paint.setColor(color);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setType(Type type) {
        if (this.type != type) {
            this.type = type;
            invalidate();
        }
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        float scale = size / 24f;
        canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(scale, scale);
        paint.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (type) {
            case HOME:
                poly(true, 3, 10.5f, 12, 3, 21, 10.5f, 21, 21, 15, 21, 15, 15, 9, 15, 9, 21, 3, 21);
                break;
            case REVIEW:
                poly(true, 12, 3, 2, 8, 12, 13, 22, 8);
                poly(false, 2, 13, 12, 18, 22, 13);
                poly(false, 2, 17.5f, 12, 22, 22, 17.5f);
                break;
            case HISTORY:
                path.addCircle(12, 12, 9, Path.Direction.CW);
                poly(false, 12, 7, 12, 12, 15, 14);
                break;
            case SETTINGS:
                poly(false, 4, 7, 14, 7);
                poly(false, 18, 7, 20, 7);
                poly(false, 4, 17, 8, 17);
                poly(false, 12, 17, 20, 17);
                path.addCircle(16, 7, 2, Path.Direction.CW);
                path.addCircle(10, 17, 2, Path.Direction.CW);
                break;
            case MIC:
                rect.set(9, 3, 15, 14);
                path.addRoundRect(rect, 3, 3, Path.Direction.CW);
                rect.set(5, 4, 19, 18);
                path.addArc(rect, 0, 180);
                poly(false, 12, 18, 12, 21);
                break;
            case STOP:
                paint.setStyle(Paint.Style.FILL);
                rect.set(7, 7, 17, 17);
                path.addRoundRect(rect, 2, 2, Path.Direction.CW);
                break;
            case SEND:
                poly(false, 12, 19, 12, 5);
                poly(false, 5, 12, 12, 5, 19, 12);
                break;
            case CLOSE:
                poly(false, 6, 6, 18, 18);
                poly(false, 18, 6, 6, 18);
                break;
            case ARROW_RIGHT:
                poly(false, 5, 12, 19, 12);
                poly(false, 13, 6, 19, 12, 13, 18);
                break;
            case CHEVRON_RIGHT:
                poly(false, 9, 6, 15, 12, 9, 18);
                break;
            case SPEAKER:
                poly(true, 11, 5, 6, 9, 3, 9, 3, 15, 6, 15, 11, 19);
                rect.set(7, 7, 17, 17);
                path.addArc(rect, -45, 90);
                break;
            case CHECK:
                poly(false, 5, 12, 10, 17, 19, 7);
                break;
            case PAUSE:
                poly(false, 9, 5, 9, 19);
                poly(false, 15, 5, 15, 19);
                break;
            case KEYBOARD:
                rect.set(3, 6, 21, 18);
                path.addRoundRect(rect, 2, 2, Path.Direction.CW);
                poly(false, 7, 14, 17, 14);
                poly(false, 7, 10, 7.01f, 10);
                poly(false, 11, 10, 11.01f, 10);
                poly(false, 15, 10, 15.01f, 10);
                break;
            case FLAME:
                path.moveTo(12, 3);
                path.cubicTo(13, 7, 17, 9, 17, 13);
                rect.set(7, 8, 17, 18);
                path.arcTo(rect, 0, 180, false);
                path.cubicTo(7, 11, 8, 10, 9, 9);
                path.cubicTo(9, 11, 10, 12, 11, 12);
                path.cubicTo(11, 9, 10, 7, 12, 3);
                path.close();
                break;
        }
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    /** Adds a polyline (x0, y0, x1, y1, ...) to the path, optionally closed. */
    private void poly(boolean close, float... xy) {
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) {
            path.lineTo(xy[i], xy[i + 1]);
        }
        if (close) path.close();
    }
}
