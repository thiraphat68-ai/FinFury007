package com.example.finfury;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

/**
 * การโจมตีระยะไกลของศัตรู
 * - แมงกะพรุน: ยิงลำพลังเป็นเส้นตรง
 * - หมึกยักษ์: พ่นหมึกเป็นรูปกรวยที่ขยายกว้างออกไปข้างหน้า
 * ทั้งสองแบบทำงานอิสระหลังปล่อย (ศัตรูกลับไปวนรอบได้ทันที)
 */
public final class RangedAttacks {

    private RangedAttacks() {}

    private static final float BOLT_HIT_RADIUS = 45f;   // ระยะจากกึ่งกลางลำพลังถึงกึ่งกลางผู้เล่นที่นับว่าโดน
    private static final float BOLT_LENGTH = 70f;
    private static final float BOLT_THICKNESS = 26f;

    public static final float LINE_BAND_WIDTH = 60f;    // ความกว้างแถบเตือนของลำพลัง
    public static final float INK_RANGE = 440f;         // ระยะไกลสุดของกรวยหมึก
    public static final float INK_HALF_ANGLE_DEG = 32f; // ครึ่งมุมของกรวย (กว้างรวม 64°)

    private static float[] playerCenter(BattleContext ctx) {
        View p = ctx.getPlayerContainer();
        if (p == null) return null;
        return new float[]{p.getX() + p.getWidth() / 2f, p.getY() + p.getHeight() / 2f};
    }

    // ---------------------------------------------------------
    // แมงกะพรุน: ลำพลังเส้นตรง ความเร็ว speed (px/s) บินจนพ้นจอหรือโดนผู้เล่น
    // ---------------------------------------------------------
    public static void fireLine(BattleContext ctx, float x0, float y0, float dirX, float dirY,
                                float speed, int damage) {
        FrameLayout area = ctx.getGameArea();
        if (area == null) return;

        View bolt = new View(ctx.getContext());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(BOLT_THICKNESS / 2f);
        bg.setColor(Color.parseColor("#F48FB1"));
        bg.setStroke(4, Color.WHITE);
        bolt.setBackground(bg);
        bolt.setLayoutParams(new FrameLayout.LayoutParams((int) BOLT_LENGTH, (int) BOLT_THICKNESS));
        bolt.setRotation((float) Math.toDegrees(Math.atan2(dirY, dirX)));
        area.addView(bolt);

        // บินให้พ้นขอบจอ (ใช้เส้นทแยงมุมของพื้นที่เล่นเป็นระยะสูงสุด)
        final float maxDist = (float) Math.hypot(Math.max(area.getWidth(), 1), Math.max(area.getHeight(), 1));
        final long durationMs = (long) (maxDist / speed * 1000f);

        final boolean[] hit = {false};
        final float[] prevDist = {0f};

        ValueAnimator anim = ValueAnimator.ofFloat(0f, maxDist);
        anim.setDuration(durationMs);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            if (!ctx.isGameRunning()) {
                a.cancel();
                return;
            }
            float d = (float) a.getAnimatedValue();
            float bx = x0 + dirX * d;
            float by = y0 + dirY * d;
            bolt.setX(bx - BOLT_LENGTH / 2f);
            bolt.setY(by - BOLT_THICKNESS / 2f);

            float[] pc = playerCenter(ctx);
            if (!hit[0] && pc != null && !ctx.isGamePaused()) {
                // ลำพลังเร็วมาก ก้าวต่อเฟรมยาว จึงเช็กทั้งช่วงที่บินผ่านในเฟรมนี้
                float ax = x0 + dirX * prevDist[0], ay = y0 + dirY * prevDist[0];
                if (distanceToSegment(pc[0], pc[1], ax, ay, bx, by) <= BOLT_HIT_RADIUS) {
                    hit[0] = true;
                    ctx.damagePlayer(damage);
                    HitEffects.playerHit(ctx, ctx.getPlayerContainer(), damage);
                    a.cancel();
                }
            }
            prevDist[0] = d;
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                area.removeView(bolt);
            }
        });
        anim.start();
    }

    // ---------------------------------------------------------
    // หมึกยักษ์: พ่นหมึกเป็นกรวย ขยายทั้งระยะและมุมจากตัวหมึกออกไปทางผู้เล่น
    // ---------------------------------------------------------
    public static void inkCone(BattleContext ctx, float cx, float cy, float dirX, float dirY, int damage) {
        FrameLayout area = ctx.getGameArea();
        if (area == null) return;

        final float dirDeg = (float) Math.toDegrees(Math.atan2(dirY, dirX));
        ConeView cone = new ConeView(ctx.getContext(), cx, cy, dirDeg, false);
        cone.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(cone);

        final boolean[] hit = {false};

        ValueAnimator expand = ValueAnimator.ofFloat(0f, 1f);
        expand.setDuration(380);
        expand.setInterpolator(new DecelerateInterpolator());
        expand.addUpdateListener(a -> {
            if (!ctx.isGameRunning()) {
                a.cancel();
                return;
            }
            float p = (float) a.getAnimatedValue();
            float radius = INK_RANGE * p;
            float half = INK_HALF_ANGLE_DEG * (0.35f + 0.65f * p); // เริ่มแคบแล้วบานออกเป็นกรวย
            cone.setShape(radius, half);

            float[] pc = playerCenter(ctx);
            if (!hit[0] && pc != null && !ctx.isGamePaused() && inCone(pc[0], pc[1], cx, cy, dirDeg, radius, half)) {
                hit[0] = true;
                ctx.damagePlayer(damage);
                HitEffects.playerHit(ctx, ctx.getPlayerContainer(), damage);
            }
        });
        expand.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // ปล่อยให้หมึกค้างครู่หนึ่งแล้วจางหาย
                cone.animate().alpha(0f).setDuration(350).withEndAction(() -> area.removeView(cone)).start();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                area.removeView(cone);
            }
        });
        expand.start();
    }

    /** แถบเตือนรูปกรวย (โชว์ก่อนพ่นหมึก) ค่อยๆ ชัดขึ้นตลอดช่วงเตือน คืน View ให้ผู้เรียกลบเอง */
    public static View showInkTelegraph(FrameLayout area, float cx, float cy, float dirX, float dirY,
                                        long durationMs) {
        ConeView cone = new ConeView(area.getContext(), cx, cy,
                (float) Math.toDegrees(Math.atan2(dirY, dirX)), true);
        cone.setShape(INK_RANGE, INK_HALF_ANGLE_DEG);
        cone.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        cone.setAlpha(0.25f);
        area.addView(cone);
        cone.animate().alpha(1f).setDuration(durationMs).start();
        return cone;
    }

    // ---------------------------------------------------------
    // ตัวช่วย
    // ---------------------------------------------------------
    private static boolean inCone(float px, float py, float cx, float cy, float dirDeg,
                                  float radius, float halfDeg) {
        float dx = px - cx, dy = py - cy;
        if (Math.hypot(dx, dy) > radius) return false;
        float ang = (float) Math.toDegrees(Math.atan2(dy, dx));
        float diff = Math.abs(((ang - dirDeg + 540f) % 360f) - 180f);
        return diff <= halfDeg;
    }

    private static float distanceToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax, aby = by - ay;
        float len2 = abx * abx + aby * aby;
        float t = len2 < 0.0001f ? 0f : Math.max(0f, Math.min(1f, ((px - ax) * abx + (py - ay) * aby) / len2));
        return (float) Math.hypot(px - (ax + abx * t), py - (ay + aby * t));
    }

    /** กรวยหมึก: telegraph = แถบม่วงโปร่งใสมีเส้นประขอบ, ไม่ใช่ telegraph = หมึกดำทึบพร้อมขอบม่วง */
    private static class ConeView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final float cx, cy, dirDeg;
        private final boolean telegraph;
        private float radius, halfDeg;

        ConeView(Context c, float cx, float cy, float dirDeg, boolean telegraph) {
            super(c);
            this.cx = cx;
            this.cy = cy;
            this.dirDeg = dirDeg;
            this.telegraph = telegraph;

            fill.setStyle(Paint.Style.FILL);
            edge.setStyle(Paint.Style.STROKE);
            inner.setStyle(Paint.Style.FILL);
            if (telegraph) {
                fill.setColor(Color.parseColor("#55AA00FF"));
                edge.setColor(Color.parseColor("#CCCE93D8"));
                edge.setStrokeWidth(5f);
                edge.setPathEffect(new DashPathEffect(new float[]{26f, 18f}, 0f));
            } else {
                fill.setColor(Color.parseColor("#CC6A1B9A"));
                edge.setColor(Color.parseColor("#FFE1BEE7"));
                edge.setStrokeWidth(4f);
                inner.setColor(Color.parseColor("#992A0845"));
            }
        }

        void setShape(float radius, float halfDeg) {
            this.radius = radius;
            this.halfDeg = halfDeg;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (radius < 1f) return;
            oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(oval, dirDeg - halfDeg, halfDeg * 2f, true, fill);
            if (!telegraph) {
                float r2 = radius * 0.62f;
                oval.set(cx - r2, cy - r2, cx + r2, cy + r2);
                canvas.drawArc(oval, dirDeg - halfDeg * 0.8f, halfDeg * 1.6f, true, inner);
            }
            oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(oval, dirDeg - halfDeg, halfDeg * 2f, true, edge);
        }
    }
}
