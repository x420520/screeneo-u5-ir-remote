package com.screeneo.u5irremote;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

final class RemoteIcon extends Drawable {
    enum Shape {
        POWER, INPUT, UP, DOWN, LEFT, RIGHT, BACK, HOME, SETTINGS, FOCUS, ASSIST,
        MINUS, MUTE, PLUS
    }

    private final Shape shape;
    private final int size;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    RemoteIcon(Shape shape, int color, int size) {
        this.shape = shape;
        this.size = size;
        paint.setColor(color);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setBounds(0, 0, size, size);
    }

    @Override
    public void draw(Canvas canvas) {
        int saved = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        paint.setStyle(Paint.Style.STROKE);

        switch (shape) {
            case POWER:
                canvas.drawArc(4, 4, 20, 20, -50, 280, false, paint);
                line(canvas, 12, 3, 12, 11);
                break;
            case INPUT:
                line(canvas, 13, 5, 21, 5, 21, 19, 13, 19);
                line(canvas, 3, 12, 16, 12);
                line(canvas, 12, 8, 16, 12, 12, 16);
                break;
            case UP:
                line(canvas, 6, 15, 12, 9, 18, 15);
                break;
            case DOWN:
                line(canvas, 6, 9, 12, 15, 18, 9);
                break;
            case LEFT:
                line(canvas, 15, 6, 9, 12, 15, 18);
                break;
            case RIGHT:
                line(canvas, 9, 6, 15, 12, 9, 18);
                break;
            case BACK:
                line(canvas, 10, 5, 4, 11, 10, 17);
                Path back = new Path();
                back.moveTo(4, 11);
                back.lineTo(14, 11);
                back.quadTo(20, 11, 20, 17);
                canvas.drawPath(back, paint);
                break;
            case HOME:
                line(canvas, 3, 11, 12, 4, 21, 11);
                line(canvas, 6, 10, 6, 20, 10, 20, 10, 15, 14, 15, 14, 20, 18, 20, 18, 10);
                break;
            case SETTINGS:
                line(canvas, 10, 3, 14, 3, 15, 6, 18, 7, 21, 9, 19, 12, 21, 15, 18, 17,
                        15, 18, 14, 21, 10, 21, 9, 18, 6, 17, 3, 15, 5, 12, 3, 9, 6, 7,
                        9, 6, 10, 3);
                canvas.drawCircle(12, 12, 3, paint);
                break;
            case FOCUS:
                line(canvas, 3, 8, 3, 3, 8, 3);
                line(canvas, 16, 3, 21, 3, 21, 8);
                line(canvas, 21, 16, 21, 21, 16, 21);
                line(canvas, 8, 21, 3, 21, 3, 16);
                canvas.drawCircle(12, 12, 3.5f, paint);
                break;
            case ASSIST:
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(8, 12, 4, paint);
                canvas.drawCircle(16, 7, 2.2f, paint);
                canvas.drawCircle(18.5f, 13, 2.6f, paint);
                canvas.drawCircle(14.5f, 18.5f, 1.9f, paint);
                break;
            case MINUS:
                line(canvas, 5, 12, 19, 12);
                break;
            case MUTE:
                line(canvas, 3, 9, 7, 9, 12, 5, 12, 19, 7, 15, 3, 15, 3, 9);
                line(canvas, 16, 9, 21, 15);
                line(canvas, 21, 9, 16, 15);
                break;
            case PLUS:
                line(canvas, 5, 12, 19, 12);
                line(canvas, 12, 5, 12, 19);
                break;
        }
        canvas.restoreToCount(saved);
    }

    private void line(Canvas canvas, float... coordinates) {
        Path path = new Path();
        path.moveTo(coordinates[0], coordinates[1]);
        for (int index = 2; index < coordinates.length; index += 2) {
            path.lineTo(coordinates[index], coordinates[index + 1]);
        }
        canvas.drawPath(path, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
}
