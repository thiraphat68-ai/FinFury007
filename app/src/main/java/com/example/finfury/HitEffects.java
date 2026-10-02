package com.example.finfury;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * เอฟเฟกต์ตอนโจมตีโดนศัตรู: วงคลื่นกระแทก + ตัวเลขดาเมจลอยขึ้น + จอสั่น
 */
public final class HitEffects {

    private HitEffects() {}

    public static void impact(BattleContext ctx, View target, int damage) {
        FrameLayout area = ctx.getGameArea();
        if (area == null || target == null) return;

        float cx = target.getX() + target.getWidth() / 2f;
        float cy = target.getY() + target.getHeight() / 2f;

        boolean big = damage >= 3;
        burst(ctx.getContext(), area, cx, cy, big, Color.parseColor("#FF9800"), Color.parseColor("#FFEB3B"));
        damagePopup(ctx.getContext(), area, cx, cy, damage, big, Color.parseColor("#FFEB3B"));
        shake(area, big ? 10f : 5f);
    }

    /** ศัตรูพุ่งโดนผู้เล่น: เอฟเฟกต์สีแดง + จอวาบแดง + จอสั่นแรง */
    public static void playerHit(BattleContext ctx, View player, int damage) {
        FrameLayout area = ctx.getGameArea();
        if (area == null || player == null) return;

        float cx = player.getX() + player.getWidth() / 2f;
        float cy = player.getY() + player.getHeight() / 2f;
        int red = Color.parseColor("#FF1744");

        burst(ctx.getContext(), area, cx, cy, false, red, Color.parseColor("#FFCDD2"));
        damagePopup(ctx.getContext(), area, cx, cy, damage, false, Color.parseColor("#FF5252"));
        shake(area, 9f);

        View flash = new View(ctx.getContext());
        flash.setBackgroundColor(Color.parseColor("#26FF0000"));
        flash.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(flash);
        flash.animate().alpha(0f).setDuration(200).withEndAction(() -> area.removeView(flash)).start();
    }

    /** วาดเงาจางๆ ต่อเนื่องตามเส้นทางพุ่ง (ทุก ~45px) เพื่อให้เห็นแนวพุ่งแม้ความเร็วสูงมาก */
    public static void trail(FrameLayout area, float x0, float y0, float x1, float y1,
                             int w, int h, int color) {
        float dist = (float) Math.hypot(x1 - x0, y1 - y0);
        int n = Math.min(6, Math.max(1, (int) (dist / 80f)));
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            View ghost = new View(area.getContext());
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(color);
            ghost.setBackground(bg);
            ghost.setLayoutParams(new FrameLayout.LayoutParams(w, h));
            ghost.setX(x0 + (x1 - x0) * t);
            ghost.setY(y0 + (y1 - y0) * t);
            area.addView(ghost);
            ghost.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(250)
                    .withEndAction(() -> area.removeView(ghost)).start();
        }
    }

    /**
     * โชว์เส้นทางพุ่งของศัตรูก่อนโจมตี: แถบแดงโปร่งใสกว้างเท่าระยะโดนจริง + เส้นประกลาง + หัวลูกศร
     * ค่อยๆ ชัดขึ้นตลอดช่วงเตือน คืน View ให้ผู้เรียกลบเองเมื่อพุ่งหรือถูกขัดจังหวะ
     */
    public static View showAttackPath(FrameLayout area, float x0, float y0, float x1, float y1,
                                      float bandWidth, long durationMs) {
        PathView view = new PathView(area.getContext(), x0, y0, x1, y1, bandWidth);
        view.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        view.setAlpha(0.25f);
        area.addView(view);
        view.animate().alpha(1f).setDuration(durationMs).start();
        return view;
    }

    private static class PathView extends View {
        private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float x0, y0, x1, y1;

        PathView(Context c, float x0, float y0, float x1, float y1, float bandWidth) {
            super(c);
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            band.setStyle(Paint.Style.STROKE);
            band.setStrokeWidth(bandWidth);
            band.setStrokeCap(Paint.Cap.BUTT);
            band.setColor(Color.parseColor("#33FF1744"));
            dash.setStyle(Paint.Style.STROKE);
            dash.setStrokeWidth(6f);
            dash.setColor(Color.parseColor("#CCFF5252"));
            dash.setPathEffect(new android.graphics.DashPathEffect(new float[]{26f, 18f}, 0f));
            arrow.setStyle(Paint.Style.FILL);
            arrow.setColor(Color.parseColor("#CCFF5252"));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            canvas.drawLine(x0, y0, x1, y1, band);
            canvas.drawLine(x0, y0, x1, y1, dash);

            float len = (float) Math.hypot(x1 - x0, y1 - y0);
            if (len < 1f) return;
            float ux = (x1 - x0) / len, uy = (y1 - y0) / len;
            float size = 34f;
            android.graphics.Path head = new android.graphics.Path();
            head.moveTo(x1 + ux * size, y1 + uy * size);
            head.lineTo(x1 - uy * size * 0.8f, y1 + ux * size * 0.8f);
            head.lineTo(x1 + uy * size * 0.8f, y1 - ux * size * 0.8f);
            head.close();
            canvas.drawPath(head, arrow);
        }
    }

    private static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    // ---------------------------------------------------------
    // วงแหวนกระแทก + เส้นประกายพุ่งออกรอบตัว
    // ---------------------------------------------------------
    private static void burst(Context c, FrameLayout area, float cx, float cy, boolean big, int glow, int spikeColor) {
        final float maxRadius = dp(c, big ? 70 : 55);
        BurstView view = new BurstView(c, cx, cy, maxRadius, glow, spikeColor);
        view.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(view);

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(300);
        anim.addUpdateListener(a -> view.setProgress((float) a.getAnimatedValue()));
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                area.removeView(view);
            }
        });
        anim.start();
    }

    // ---------------------------------------------------------
    // ตัวเลขดาเมจลอยขึ้นแล้วจางหาย
    // ---------------------------------------------------------
    private static void damagePopup(Context c, FrameLayout area, float cx, float cy, int damage, boolean big, int color) {
        TextView tv = new TextView(c);
        tv.setText("-" + damage);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, big ? 26 : 20);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(color);
        tv.setShadowLayer(8f, 0f, 0f, Color.BLACK);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        tv.setScaleX(0.7f);
        tv.setScaleY(0.7f);
        area.addView(tv);

        tv.post(() -> {
            tv.setX(cx - tv.getWidth() / 2f);
            tv.setY(cy - tv.getHeight() - dp(c, 20));
            tv.animate()
                    .scaleX(1.15f).scaleY(1.15f)
                    .translationYBy(-dp(c, 36))
                    .alpha(0f)
                    .setDuration(550)
                    .withEndAction(() -> area.removeView(tv))
                    .start();
        });
    }

    // ---------------------------------------------------------
    // จอสั่นสั้นๆ แอมพลิจูดลดลงเรื่อยๆ
    // ---------------------------------------------------------
    private static void shake(View area, float amplitudePx) {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(160);
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float decay = 1f - t;
            area.setTranslationX((float) Math.sin(t * Math.PI * 8) * amplitudePx * decay);
            area.setTranslationY((float) Math.cos(t * Math.PI * 6) * amplitudePx * 0.5f * decay);
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                area.setTranslationX(0f);
                area.setTranslationY(0f);
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                area.setTranslationX(0f);
                area.setTranslationY(0f);
            }
        });
        anim.start();
    }

    private static class BurstView extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint spike = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float cx, cy, maxRadius;
        private float progress;

        BurstView(Context c, float cx, float cy, float maxRadius, int glow, int spikeColor) {
            super(c);
            this.cx = cx;
            this.cy = cy;
            this.maxRadius = maxRadius;
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(Color.WHITE);
            ring.setShadowLayer(14f, 0f, 0f, glow);
            spike.setStyle(Paint.Style.STROKE);
            spike.setStrokeCap(Paint.Cap.ROUND);
            spike.setColor(spikeColor);
            core.setColor(Color.WHITE);
            setLayerType(LAYER_TYPE_SOFTWARE, null); // ให้ shadow ทำงาน
        }

        void setProgress(float p) {
            progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float ease = 1f - (1f - progress) * (1f - progress);
            int alpha = (int) (255 * (1f - progress));

            // แกนแสงสว่างวาบตอนเริ่ม
            core.setAlpha((int) (255 * Math.max(0f, 1f - progress * 2.5f)));
            canvas.drawCircle(cx, cy, maxRadius * 0.3f * (1f - progress), core);

            ring.setAlpha(alpha);
            ring.setStrokeWidth(9f * (1f - progress) + 1f);
            canvas.drawCircle(cx, cy, maxRadius * ease, ring);

            spike.setAlpha(alpha);
            spike.setStrokeWidth(5f * (1f - progress) + 1f);
            for (int i = 0; i < 8; i++) {
                double ang = Math.PI * 2 * i / 8 + 0.3;
                float r0 = maxRadius * ease * 0.55f;
                float r1 = maxRadius * ease * 1.05f;
                canvas.drawLine(cx + (float) Math.cos(ang) * r0, cy + (float) Math.sin(ang) * r0,
                        cx + (float) Math.cos(ang) * r1, cy + (float) Math.sin(ang) * r1, spike);
            }
        }
    }
}
