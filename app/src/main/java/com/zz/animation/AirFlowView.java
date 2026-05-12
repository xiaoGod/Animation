package com.zz.animation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

public class AirFlowView extends View {
    private static final float LOGICAL_WIDTH = 1920f;
    private static final float LOGICAL_HEIGHT = 1200f;

    private static final Beam[] BEAMS = new Beam[] {
            new Beam(905f, 718f, 862f, 860f, 810f, 1056f, 762f, 1300f, 214f, -0.06f, 0.24f, 0.7f),
            new Beam(1048f, 718f, 1095f, 860f, 1156f, 1056f, 1215f, 1300f, 214f, 0.06f, 0.24f, 0.85f),
    };

    private final BodySettings body = new BodySettings();
    private final FlowSettings flow = new FlowSettings();
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final PointF tempEnd = new PointF();
    private final PointF tempC1 = new PointF();
    private final PointF tempC2 = new PointF();
    private final PointF tempStart = new PointF();
    private final PointF localC1 = new PointF();
    private final PointF localC2 = new PointF();
    private final PointF localEnd = new PointF();
    private final PointF flowStartP = new PointF();
    private final PointF flowEndP = new PointF();
    private final PointF flowLocalStart = new PointF();
    private final PointF flowLocalEnd = new PointF();
    private final PointF flowC1 = new PointF();
    private final PointF flowC2 = new PointF();
    private final CubicSegment trimmedSegment = new CubicSegment();
    private final int[] bodyColors = new int[5];
    private final int[] flowColors = new int[5];
    private final float[] bodyStops = new float[] {0f, 0.13f, 0.45f, 0.72f, 1f};
    private final float[] flowStops = new float[] {0f, 0.24f, 0.52f, 0.78f, 1f};

    private Bitmap bodyBitmap;
    private Canvas bodyCanvas;
    private boolean cacheDirty = true;

    private int level = 4;
    private float opacity = 0.72f;
    private float spread = 1f;
    private float speed = 1f;
    private float coolness = 0.82f;
    private float flowAlpha = 0.85f;
    private float flowWidth = 1f;
    private float flowLength = 1f;
    private int flowCount = 5;
    private float flowSpeed = 1f;
    private boolean blackBackground = true;
    private boolean bodyVisible = true;
    private boolean flowVisible = true;

    public AirFlowView(Context context) {
        super(context);
        bodyPaint.setStyle(Paint.Style.STROKE);
        bodyPaint.setStrokeJoin(Paint.Join.ROUND);
        bodyPaint.setStrokeCap(Paint.Cap.BUTT);
        bodyPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        flowPaint.setStyle(Paint.Style.STROKE);
        flowPaint.setStrokeCap(Paint.Cap.ROUND);
        flowPaint.setStrokeJoin(Paint.Join.ROUND);
        flowPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
    }

    public void setLevel(int level) {
        int next = clamp(level, 1, 7);
        if (this.level == next) return;
        this.level = next;
        markBodyDirty();
    }

    public void setOpacity(float opacity) {
        float next = clamp(opacity, 0.2f, 1f);
        if (this.opacity == next) return;
        this.opacity = next;
        markBodyDirty();
    }

    public void setSpread(float spread) {
        float next = clamp(spread, 0.55f, 1.5f);
        if (this.spread == next) return;
        this.spread = next;
        markBodyDirty();
    }

    public void setSpeed(float speed) {
        this.speed = clamp(speed, 0.35f, 1.8f);
        postInvalidateOnAnimation();
    }

    public void setCoolness(float coolness) {
        float next = clamp(coolness, 0.35f, 1.25f);
        if (this.coolness == next) return;
        this.coolness = next;
        markBodyDirty();
    }

    public void setBlackBackground(boolean blackBackground) {
        this.blackBackground = blackBackground;
        invalidate();
    }

    public void setBodyVisible(boolean bodyVisible) {
        if (this.bodyVisible == bodyVisible) return;
        this.bodyVisible = bodyVisible;
        markBodyDirty();
    }

    public void setFlowVisible(boolean flowVisible) {
        this.flowVisible = flowVisible;
        postInvalidateOnAnimation();
    }

    public void setFlowAlpha(float flowAlpha) {
        this.flowAlpha = clamp(flowAlpha, 0.3f, 1.6f);
        postInvalidateOnAnimation();
    }

    public void setFlowWidth(float flowWidth) {
        this.flowWidth = clamp(flowWidth, 0.5f, 1.8f);
        postInvalidateOnAnimation();
    }

    public void setFlowLength(float flowLength) {
        this.flowLength = clamp(flowLength, 0.6f, 1.8f);
        postInvalidateOnAnimation();
    }

    public void setFlowCount(int flowCount) {
        this.flowCount = clamp(flowCount, 1, 8);
        postInvalidateOnAnimation();
    }

    public void setFlowSpeed(float flowSpeed) {
        this.flowSpeed = clamp(flowSpeed, 0.4f, 2.2f);
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (blackBackground) {
            canvas.drawColor(Color.BLACK);
        }

        ensureBodyCache();
        float scale = Math.min(getWidth() / LOGICAL_WIDTH, getHeight() / LOGICAL_HEIGHT);
        float left = (getWidth() - LOGICAL_WIDTH * scale) * 0.5f;
        float top = (getHeight() - LOGICAL_HEIGHT * scale) * 0.5f;

        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        if (bodyBitmap != null) {
            canvas.drawBitmap(bodyBitmap, 0f, 0f, bitmapPaint);
        }
        if (bodyVisible && flowVisible) {
            float time = SystemClock.uptimeMillis() / 1000f;
            for (Beam beam : BEAMS) {
                drawBodyFlow(canvas, beam, time);
            }
        }
        canvas.restore();

        if (bodyVisible && flowVisible) {
            postInvalidateOnAnimation();
        }
    }

    private void ensureBodyCache() {
        if (bodyBitmap == null || bodyBitmap.isRecycled()) {
            bodyBitmap = Bitmap.createBitmap((int) LOGICAL_WIDTH, (int) LOGICAL_HEIGHT, Bitmap.Config.ARGB_8888);
            bodyCanvas = new Canvas(bodyBitmap);
            cacheDirty = true;
        }
        if (!cacheDirty) return;

        bodyBitmap.eraseColor(Color.TRANSPARENT);
        if (bodyVisible) {
            for (Beam beam : BEAMS) {
                drawBodyLayer(bodyCanvas, beam);
            }
        }
        cacheDirty = false;
    }

    private void drawBodyLayer(Canvas canvas, Beam beam) {
        float fan = (level - 1f) / 6f;
        float lengthScale = 0.72f + fan * 0.28f;
        shiftedPoint(tempEnd, beam.end, beam, lengthScale);
        shiftedPoint(tempC1, beam.c1, beam, 0.91f + fan * 0.09f);
        shiftedPoint(tempC2, beam.c2, beam, 0.8f + fan * 0.2f);

        float baseAlpha = opacity * body.alpha * (0.86f + fan * 0.42f);
        float width = beam.baseWidth * body.width * spread * (0.86f + fan * 0.22f);

        for (int i = 0; i < body.repetitions; i += 1) {
            float offset = i - (body.repetitions - 1f) * 0.5f;
            float normalized = Math.abs(offset) / ((body.repetitions - 1f) * 0.5f);
            float wave = (float) Math.sin(i * 1.17f + beam.drift + body.phase) * body.wave;

            tempStart.set(
                    beam.start.x + offset * body.offsetX * body.startOffsetScale + wave * body.startOffsetScale,
                    beam.start.y + offset * body.offsetY * body.startOffsetScale
            );
            localC1.set(tempC1.x + offset * body.fanX + wave * 0.45f, tempC1.y + offset * body.fanY);
            localC2.set(tempC2.x + offset * body.fanX * 1.35f - wave * 0.3f, tempC2.y + offset * body.fanY * 1.35f);
            localEnd.set(tempEnd.x + offset * body.endX, tempEnd.y + offset * body.endY);

            float coreBoost = 1f - normalized * 0.55f;
            float strokeAlpha = baseAlpha * coreBoost * (0.94f + (float) Math.sin(i + body.phase) * 0.07f);
            float tStart = body.startTrim + normalized * body.edgeStartTrim;
            cubicSegmentFrom(trimmedSegment, tempStart, localC1, localC2, localEnd, tStart);

            drawLayerStroke(
                    canvas,
                    trimmedSegment.start,
                    trimmedSegment.c1,
                    trimmedSegment.c2,
                    trimmedSegment.end,
                    width * widthFalloff(i, body.repetitions),
                    strokeAlpha,
                    0.34f,
                    1f,
                    0.3f
            );
        }
    }

    private void drawLayerStroke(
            Canvas canvas,
            PointF start,
            PointF c1,
            PointF c2,
            PointF end,
            float width,
            float alpha,
            float headAlpha,
            float midAlpha,
            float tailAlpha
    ) {
        bodyColors[0] = mixColor(coolness, alpha * headAlpha);
        bodyColors[1] = mixColor(coolness, alpha);
        bodyColors[2] = mixColor(coolness, alpha * midAlpha);
        bodyColors[3] = mixColor(coolness, alpha * tailAlpha);
        bodyColors[4] = mixColor(coolness, 0f);
        bodyPaint.setShader(new LinearGradient(start.x, start.y, end.x, end.y, bodyColors, bodyStops, Shader.TileMode.CLAMP));
        bodyPaint.setStrokeWidth(Math.max(1f, width));

        path.reset();
        path.moveTo(start.x, start.y);
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y);
        canvas.drawPath(path, bodyPaint);
        bodyPaint.setShader(null);
    }

    private void drawBodyFlow(Canvas canvas, Beam beam, float time) {
        float fan = (level - 1f) / 6f;
        float lengthScale = 0.72f + fan * 0.28f;
        shiftedPoint(tempEnd, beam.end, beam, lengthScale);
        shiftedPoint(tempC1, beam.c1, beam, 0.91f + fan * 0.09f);
        shiftedPoint(tempC2, beam.c2, beam, 0.8f + fan * 0.2f);

        float renderSpeed = speed * (0.75f + fan * 0.55f);
        float bandLength = flow.bandLength * flowLength;
        float speedScale = flow.speedScale * flowSpeed;

        for (int i = 0; i < flowCount; i += 1) {
            float offset = i - (flowCount - 1f) * 0.5f;
            float lineFade = 1f - Math.abs(offset) / (flowCount + 1f);

            for (int band = 0; band < flow.bandsPerLine; band += 1) {
                float phase = positiveModulo(
                        time * renderSpeed * speedScale
                                + i * flow.linePhase
                                + band * flow.bandPhase
                                + beam.drift * 0.11f,
                        1f
                );
                float bandStart = Math.max(0.04f, phase - bandLength * 0.45f);
                float bandEnd = Math.min(0.94f, phase + bandLength * 0.55f);
                if (bandEnd <= bandStart) continue;

                cubicPointInto(flowStartP, beam.start, tempC1, tempC2, tempEnd, bandStart);
                cubicPointInto(flowEndP, beam.start, tempC1, tempC2, tempEnd, bandEnd);

                flowLocalStart.set(
                        flowStartP.x + offset * flow.offsetX * spread,
                        flowStartP.y + offset * flow.offsetY * spread
                );
                flowLocalEnd.set(
                        flowEndP.x + offset * flow.endX * spread,
                        flowEndP.y + offset * flow.endY * spread
                );

                float alpha = opacity * flow.alpha * flowAlpha * lineFade * (0.78f + fan * 0.32f);
                flowColors[0] = Color.argb(0, 255, 255, 255);
                flowColors[1] = mixColor(coolness, alpha * 0.38f);
                flowColors[2] = mixColor(coolness, alpha);
                flowColors[3] = mixColor(coolness, alpha * 0.34f);
                flowColors[4] = Color.argb(0, 210, 242, 255);
                flowPaint.setShader(new LinearGradient(
                        flowLocalStart.x,
                        flowLocalStart.y,
                        flowLocalEnd.x,
                        flowLocalEnd.y,
                        flowColors,
                        flowStops,
                        Shader.TileMode.CLAMP
                ));
                flowPaint.setStrokeWidth(beam.baseWidth * flow.width * flowWidth * spread * (0.76f + fan * 0.22f));

                flowC1.set(
                        flowLocalStart.x + (flowLocalEnd.x - flowLocalStart.x) * 0.3f,
                        flowLocalStart.y + (flowLocalEnd.y - flowLocalStart.y) * 0.2f
                );
                flowC2.set(
                        flowLocalStart.x + (flowLocalEnd.x - flowLocalStart.x) * 0.7f,
                        flowLocalStart.y + (flowLocalEnd.y - flowLocalStart.y) * 0.82f
                );

                path.reset();
                path.moveTo(flowLocalStart.x, flowLocalStart.y);
                path.cubicTo(flowC1.x, flowC1.y, flowC2.x, flowC2.y, flowLocalEnd.x, flowLocalEnd.y);
                canvas.drawPath(path, flowPaint);
                flowPaint.setShader(null);
            }
        }
    }

    private void shiftedPoint(PointF out, PointF point, Beam beam, float lengthScale) {
        float trim = 1f - lengthScale;
        out.set(
                point.x - beam.lengthAxisX * trim * 760f,
                point.y - beam.lengthAxisY * trim * 760f
        );
    }

    private void cubicPointInto(PointF out, PointF start, PointF c1, PointF c2, PointF end, float t) {
        float mt = 1f - t;
        float a = mt * mt * mt;
        float b = 3f * mt * mt * t;
        float c = 3f * mt * t * t;
        float d = t * t * t;
        out.set(
                a * start.x + b * c1.x + c * c2.x + d * end.x,
                a * start.y + b * c1.y + c * c2.y + d * end.y
        );
    }

    private void cubicSegmentFrom(CubicSegment out, PointF start, PointF c1, PointF c2, PointF end, float t) {
        lerpInto(out.p01, start, c1, t);
        lerpInto(out.p12, c1, c2, t);
        lerpInto(out.p23, c2, end, t);
        lerpInto(out.p012, out.p01, out.p12, t);
        lerpInto(out.p123, out.p12, out.p23, t);
        lerpInto(out.p0123, out.p012, out.p123, t);
        out.start.set(out.p0123);
        out.c1.set(out.p123);
        out.c2.set(out.p23);
        out.end.set(end);
    }

    private void lerpInto(PointF out, PointF start, PointF end, float amount) {
        out.set(
                start.x + (end.x - start.x) * amount,
                start.y + (end.y - start.y) * amount
        );
    }

    private int mixColor(float coolness, float alpha) {
        int blue = Math.round(172f + coolness * 42f);
        int green = Math.round(222f + coolness * 28f);
        return Color.argb(
                Math.round(clamp(alpha, 0f, 1f) * 255f),
                Math.min(255, blue),
                Math.min(255, green),
                255
        );
    }

    private float widthFalloff(int i, int total) {
        return 0.58f - Math.abs(i - (total - 1f) * 0.5f) * 0.04f;
    }

    private void markBodyDirty() {
        cacheDirty = true;
        invalidate();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float positiveModulo(float value, float divisor) {
        float result = value % divisor;
        return result < 0f ? result + divisor : result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (bodyBitmap != null) {
            bodyBitmap.recycle();
            bodyBitmap = null;
            bodyCanvas = null;
        }
    }

    private static final class BodySettings {
        final float alpha = 0.255f;
        final float width = 0.34f;
        final float phase = 2.4f;
        final int repetitions = 8;
        final float wave = 5f;
        final float offsetX = 7f;
        final float offsetY = 2f;
        final float fanX = 10f;
        final float fanY = 5.4f;
        final float endX = 28f;
        final float endY = 11f;
        final float startTrim = 0.045f;
        final float edgeStartTrim = 0.018f;
        final float startOffsetScale = 0.35f;
    }

    private static final class FlowSettings {
        final float alpha = 0.085f;
        final float width = 0.17f;
        final int bandsPerLine = 2;
        final float bandLength = 0.28f;
        final float speedScale = 0.34f;
        final float linePhase = 0.13f;
        final float bandPhase = 0.46f;
        final float offsetX = 7f;
        final float offsetY = 2f;
        final float endX = 24f;
        final float endY = 9f;
    }

    private static final class Beam {
        final PointF start;
        final PointF c1;
        final PointF c2;
        final PointF end;
        final float baseWidth;
        final float lengthAxisX;
        final float lengthAxisY;
        final float drift;

        Beam(
                float startX,
                float startY,
                float c1X,
                float c1Y,
                float c2X,
                float c2Y,
                float endX,
                float endY,
                float baseWidth,
                float lengthAxisX,
                float lengthAxisY,
                float drift
        ) {
            this.start = new PointF(startX, startY);
            this.c1 = new PointF(c1X, c1Y);
            this.c2 = new PointF(c2X, c2Y);
            this.end = new PointF(endX, endY);
            this.baseWidth = baseWidth;
            this.lengthAxisX = lengthAxisX;
            this.lengthAxisY = lengthAxisY;
            this.drift = drift;
        }
    }

    private static final class CubicSegment {
        final PointF start = new PointF();
        final PointF c1 = new PointF();
        final PointF c2 = new PointF();
        final PointF end = new PointF();
        final PointF p01 = new PointF();
        final PointF p12 = new PointF();
        final PointF p23 = new PointF();
        final PointF p012 = new PointF();
        final PointF p123 = new PointF();
        final PointF p0123 = new PointF();
    }
}
