package com.example.languagefluencyriser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The app logo (speech bubble with three rising bars on an indigo circle), drawn in code so the
 * bars can animate. Matches res/drawable/ic_launcher_*.xml, which use the same 108x108 grid;
 * the circle is the 72-unit area a launcher shows, so grid units 18..90 fill this view.
 */
public class LogoMarkView extends View {
    private static final float GRID_MIN = 18f, GRID_SPAN = 72f;
    private static final float BAR_BOTTOM = 62f, BAR_HALF = 3f;
    private static final float[] BAR_X = {43f, 54f, 65f};
    private static final float[] BAR_TOP = {53f, 47f, 40f};
    private static final int[] BAR_COLOR = {Color.rgb(165, 180, 252), Color.rgb(99, 102, 241), Color.rgb(251, 191, 36)};

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bubble = new Path();
    private final RectF rect = new RectF();
    private final float[] barProgress = {1f, 1f, 1f};
    private float scale = 1f;

    public LogoMarkView(Context context) {
        super(context);
        bubblePaint.setColor(Color.WHITE);
        shadowPaint.setColor(Color.argb(0x33, 0x20, 0x1A, 0x6B));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** 0 = bar shrunk to a dot, 1 = full height. Values above 1 overshoot slightly. */
    public void setBarProgress(int index, float progress) {
        barProgress[index] = progress;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float size = Math.min(w, h);
        scale = size / GRID_SPAN;
        circlePaint.setShader(new LinearGradient(0, 0, size, size,
                new int[]{Color.rgb(129, 140, 248), Color.rgb(99, 102, 241), Color.rgb(67, 56, 202)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        glowPaint.setShader(new RadialGradient(g(30), g(26), 60 * scale,
                Color.argb(0x33, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
    }

    /** Converts a 108-grid coordinate to view pixels. */
    private float g(float grid) {
        return (grid - GRID_MIN) * scale;
    }

    private void buildBubble(float dy) {
        bubble.reset();
        bubble.moveTo(g(42), g(32 + dy));
        bubble.lineTo(g(66), g(32 + dy));
        rect.set(g(54), g(32 + dy), g(78), g(56 + dy));
        bubble.arcTo(rect, 270, 90);
        bubble.lineTo(g(78), g(58 + dy));
        rect.set(g(54), g(46 + dy), g(78), g(70 + dy));
        bubble.arcTo(rect, 0, 90);
        bubble.lineTo(g(50), g(70 + dy));
        bubble.lineTo(g(40), g(78 + dy));
        bubble.lineTo(g(40), g(70 + dy));
        bubble.lineTo(g(42), g(70 + dy));
        rect.set(g(30), g(46 + dy), g(54), g(70 + dy));
        bubble.arcTo(rect, 90, 90);
        bubble.lineTo(g(30), g(44 + dy));
        rect.set(g(30), g(32 + dy), g(54), g(56 + dy));
        bubble.arcTo(rect, 180, 90);
        bubble.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = GRID_SPAN * scale / 2f;
        canvas.drawCircle(r, r, r, circlePaint);
        canvas.drawCircle(r, r, r, glowPaint);

        buildBubble(2.5f);
        canvas.drawPath(bubble, shadowPaint);
        buildBubble(0f);
        canvas.drawPath(bubble, bubblePaint);

        for (int i = 0; i < 3; i++) {
            float fullTop = BAR_TOP[i];
            float dotTop = BAR_BOTTOM - 2 * BAR_HALF;
            float top = dotTop - (dotTop - fullTop) * barProgress[i];
            barPaint.setColor(BAR_COLOR[i]);
            rect.set(g(BAR_X[i] - BAR_HALF), g(top), g(BAR_X[i] + BAR_HALF), g(BAR_BOTTOM));
            canvas.drawRoundRect(rect, BAR_HALF * scale, BAR_HALF * scale, barPaint);
        }
    }
}
