package com.example.languagefluencyriser;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Lays children out left to right and wraps to a new line when the next child doesn't fit,
 * so chip rows stay fully visible on any screen width or font size.
 */
public class FlowLayout extends ViewGroup {
    private final int horizontalGap;
    private final int verticalGap;

    public FlowLayout(Context context, int horizontalGapPx, int verticalGapPx) {
        super(context);
        this.horizontalGap = horizontalGapPx;
        this.verticalGap = verticalGapPx;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int maxWidth = widthMode == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE
                : MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();

        int lineWidth = 0, lineHeight = 0, widest = 0, totalHeight = 0;
        boolean firstLine = true;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            int w = Math.min(child.getMeasuredWidth(), maxWidth);
            int h = child.getMeasuredHeight();
            if (lineWidth > 0 && lineWidth + horizontalGap + w > maxWidth) {
                totalHeight += lineHeight + (firstLine ? 0 : verticalGap);
                firstLine = false;
                widest = Math.max(widest, lineWidth);
                lineWidth = 0;
                lineHeight = 0;
            }
            lineWidth += (lineWidth > 0 ? horizontalGap : 0) + w;
            lineHeight = Math.max(lineHeight, h);
        }
        if (lineWidth > 0) {
            totalHeight += lineHeight + (firstLine ? 0 : verticalGap);
            widest = Math.max(widest, lineWidth);
        }

        int width = resolveSize(widest + getPaddingLeft() + getPaddingRight(), widthMeasureSpec);
        int height = resolveSize(totalHeight + getPaddingTop() + getPaddingBottom(), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int maxRight = right - left - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int w = Math.min(child.getMeasuredWidth(), maxRight - getPaddingLeft());
            int h = child.getMeasuredHeight();
            if (x > getPaddingLeft() && x + w > maxRight) {
                x = getPaddingLeft();
                y += lineHeight + verticalGap;
                lineHeight = 0;
            }
            child.layout(x, y, x + w, y + h);
            x += w + horizontalGap;
            lineHeight = Math.max(lineHeight, h);
        }
    }
}
