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
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Electric Eel (วิชาฟิสิกส์):
 *  - Skill 1 Magnetic Repulsion        : คลื่นแม่เหล็ก 1 ลูกพุ่งออกเป็นรูปพัดไปทางที่ปลาหัน ระยะ 650 px
 *                                         ศัตรูที่คลื่นผ่าน ดาเมจ 2 ผลักไปทางเดียวกัน 450 px (ชนขอบจอ/ศัตรูอื่น = ดาเมจเพิ่ม 2)
 *  - Skill 2 Laser Beam                : เลเซอร์ตรงๆ ระยะจำกัด 650 px ทะลุทุกตัวในแนว ดาเมจ 3 คูลดาวน์สั้น
 *  - Ultimate Railgun                  : ชาร์จ 1 วินาที (ขยับไม่ได้) แล้วยิงลำแสงใหญ่ยาวสุดจอ ทะลุทุกตัว
 *                                         ดาเมจ 8 + สตัน 2 วินาที ยิงครั้งเดียว เล็งพลาดคือเสียเปล่า
 */
public class ElectricEel extends Hero {
    // ---- สเตตัสพื้นฐาน (สมดุล) ----
    @Override public int getMaxHp() { return 85; }
    @Override public float getBaseSpeedMultiplier() { return 1.1f; }
    @Override public int getStackNeeded() { return 20; }
    @Override public long getSkill1CooldownMs() { return 2000; }
    @Override public long getSkill2CooldownMs() { return 2500; }


    // ---------- Skill 1: Magnetic Repulsion ----------
    private static final float WAVE_RANGE = 650f;          // คลื่นวิ่งไกลสุดจากตัวปลา
    private static final long WAVE_MS = 550;
    private static final float WAVE_SPAN_DEG = 120f;       // มุมของคลื่นรูปพัด (กว้างด้านละ 60°)
    private static final int REPEL_DAMAGE = 2;
    private static final float KNOCKBACK_DISTANCE = 450f;
    private static final long KNOCKBACK_MS = 450;
    private static final int COLLISION_DAMAGE = 2;

    // ---------- Skill 2: Laser Beam ----------
    private static final float LASER_RANGE = 650f;
    private static final float LASER_WIDTH = 36f;
    private static final int LASER_DAMAGE = 3;

    // ---------- Ultimate: Railgun ----------
    private static final long CHARGE_MS = 1000;
    private static final float BEAM_WIDTH = 110f;
    private static final int BEAM_DAMAGE = 8;
    private static final long BEAM_STUN_MS = 2000;

    public ElectricEel() {
        super("Electric Eel", "Physics");
    }

    @Override
    public boolean usesUltimateButton() {
        return true;
    }

    @Override public String getSkill1Name() { return "Magnetic Repulsion"; }
    @Override public String getSkill1Icon() { return "🧲"; }
    @Override public String getSkill1Description() {
        return "ปล่อยคลื่นแม่เหล็ก 1 ลูกพุ่งออกไปทางที่ปลาหัน ศัตรูที่คลื่นผ่านโดน 2 ดาเมจ ถูกผลักไกล 450 px ชนขอบจอ/ศัตรูอื่นโดนอีก 2 ดาเมจ"; }

    @Override public String getSkill2Name() { return "Laser Beam"; }
    @Override public String getSkill2Icon() { return "🔆"; }
    @Override public String getSkill2Description() { return "ยิงเลเซอร์ตรงไปข้างหน้า ระยะจำกัด ทะลุทุกตัวในแนว 3 ดาเมจ คูลดาวน์สั้น"; }

    @Override public String getUltimateName() { return "Railgun"; }
    @Override public String getUltimateIcon() { return "🚀"; }
    @Override public String getUltimateDescription() { return "ชาร์จ 1 วินาที ยิงลำแสงทะลุทั้งจอ 8 ดาเมจ + สตัน 2 วินาที ยิงได้ครั้งเดียว"; }

    // =========================================================
    // Skill 1: Magnetic Repulsion
    // =========================================================
    @Override
    public void useSkill1(BattleContext ctx) {
        FrameLayout area = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (area == null || player == null) return;

        final float cx = player.getX() + player.getWidth() / 2f;
        final float cy = player.getY() + player.getHeight() / 2f;

        // คลื่นลูกเดียว พุ่งไปทิศที่ปลาหันอยู่ (ทิศจอยล่าสุด) ศัตรูที่คลื่นผ่านถูกผลักไปทิศเดียวกัน
        final float angleDeg = ctx.getPlayerAngle();
        final float pushRad = (float) Math.toRadians(angleDeg);
        final float pushX = (float) Math.cos(pushRad);
        final float pushY = (float) Math.sin(pushRad);
        final float minCos = (float) Math.cos(Math.toRadians(WAVE_SPAN_DEG / 2f));

        final WaveView wave = new WaveView(ctx.getContext(), cx, cy, angleDeg);
        wave.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(wave);

        final Set<SeaEnemy> alreadyHit = new HashSet<>();   // คลื่นลูกเดียว โดนตัวเดิมได้ครั้งเดียว
        ValueAnimator fx = ValueAnimator.ofFloat(0f, 1f);
        fx.setDuration(WAVE_MS);
        fx.setInterpolator(new LinearInterpolator());
        fx.addUpdateListener(a -> {
            if (!ctx.isGameRunning()) {
                a.cancel();
                return;
            }
            float p = (float) a.getAnimatedValue();
            wave.setProgress(p);
            float r = WAVE_RANGE * p;

            for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
                SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);
                if (!enemy.isAlive || enemy.containerView == null || alreadyHit.contains(enemy)) continue;
                View ev = enemy.containerView;
                float dx = ev.getX() + ev.getWidth() / 2f - cx;
                float dy = ev.getY() + ev.getHeight() / 2f - cy;
                float dist = (float) Math.hypot(dx, dy);
                // อยู่ในพัดของคลื่น (ซ้อนทับตัวปลาพอดีนับว่าโดน) และหน้าคลื่นเดินทางมาถึงตัวแล้ว
                if (dist > 0.01f && (dx * pushX + dy * pushY) / dist < minCos) continue;
                if (r + Math.max(ev.getWidth(), ev.getHeight()) / 2f < dist) continue;

                alreadyHit.add(enemy);
                enemy.takeDamage(REPEL_DAMAGE, false);
                ctx.onHitEnemySuccess();
                if (enemy.isAlive) knockBack(ctx, enemy, pushX, pushY);
            }
        });
        fx.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeFromParent(wave);
            }
        });
        fx.start();
    }

    /** ผลักศัตรู 450 px ตามทิศ (dx,dy) ถ้าชนขอบจอหรือศัตรูตัวอื่นระหว่างทาง = โดนอีก 2 ดาเมจแล้วหยุด */
    private void knockBack(BattleContext ctx, SeaEnemy enemy, float dx, float dy) {
        final View ev = enemy.containerView;
        final View area = (View) ev.getParent();
        if (area == null) return;

        final float startX = ev.getX();
        final float startY = ev.getY();
        final float maxX = Math.max(0f, area.getWidth() - ev.getWidth());
        final float maxY = Math.max(0f, area.getHeight() - ev.getHeight());
        final boolean[] collided = {false};

        // ระหว่างถูกผลัก AI หยุดทำงาน ไม่งั้นเดินสวนทางกับแอนิเมชันนี้
        enemy.stun(KNOCKBACK_MS + 60);

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(KNOCKBACK_MS);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(animation -> {
            if (!enemy.isAlive || !ctx.isGameRunning()) {
                animation.cancel();
                return;
            }
            float p = (float) animation.getAnimatedValue();
            float wantX = startX + dx * KNOCKBACK_DISTANCE * p;
            float wantY = startY + dy * KNOCKBACK_DISTANCE * p;
            float nx = Math.max(0f, Math.min(maxX, wantX));
            float ny = Math.max(0f, Math.min(maxY, wantY));
            ev.setX(nx);
            ev.setY(ny);

            boolean hitEdge = nx != wantX || ny != wantY;
            SeaEnemy hitOther = hitEdge ? null : findBlocker(ctx, enemy, dx, dy);

            if ((hitEdge || hitOther != null) && !collided[0]) {
                collided[0] = true;
                animation.cancel();
                float ix = nx + ev.getWidth() / 2f;
                float iy = ny + ev.getHeight() / 2f;
                FrameLayout fl = ctx.getGameArea();
                if (fl != null) spawnImpact(fl, ix, iy);
                enemy.takeDamage(COLLISION_DAMAGE, false);
            }
        });
        anim.start();
    }

    /** ศัตรูตัวอื่นที่อยู่ "ข้างหน้า" ตามทิศที่ถูกผลักและใกล้พอจะชน (ตัวที่อยู่ด้านหลังไม่นับ) */
    private static SeaEnemy findBlocker(BattleContext ctx, SeaEnemy self, float dx, float dy) {
        View a = self.containerView;
        float ax = a.getX() + a.getWidth() / 2f;
        float ay = a.getY() + a.getHeight() / 2f;
        for (int oIdx = 0; oIdx < ctx.getEnemies().size(); oIdx++) {
            SeaEnemy o = ctx.getEnemies().get(oIdx);
            if (o == self || !o.isAlive || o.containerView == null) continue;
            View b = o.containerView;
            float ox = b.getX() + b.getWidth() / 2f - ax;
            float oy = b.getY() + b.getHeight() / 2f - ay;
            float reach = (Math.max(a.getWidth(), a.getHeight()) + Math.max(b.getWidth(), b.getHeight())) * 0.4f;
            if (ox * dx + oy * dy > 0f && Math.hypot(ox, oy) < reach) return o;
        }
        return null;
    }

    // =========================================================
    // Skill 2: Particle Accelerator Shot ยิ่งไกลยิ่งเร็ว ยิ่งแรง (ระยะ = R * t^2)
    // =========================================================
    @Override
    public void useSkill2(BattleContext ctx) {
        FrameLayout area = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (area == null || player == null) return;

        float rad = (float) Math.toRadians(ctx.getPlayerAngle());
        float dirX = (float) Math.cos(rad);
        float dirY = (float) Math.sin(rad);
        float sx = player.getX() + player.getWidth() / 2f + dirX * 30f;
        float sy = player.getY() + player.getHeight() / 2f + dirY * 30f;
        float ex = sx + dirX * LASER_RANGE;
        float ey = sy + dirY * LASER_RANGE;

        // เลเซอร์วาบแล้วจางหาย
        final BeamView beam = new BeamView(ctx.getContext(), sx, sy, ex, ey, LASER_WIDTH);
        beam.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(beam);
        beam.animate().alpha(0f).setStartDelay(60).setDuration(240)
                .withEndAction(() -> removeFromParent(beam)).start();

        // ทะลุทุกตัวที่อยู่ในแนวและในระยะ
        for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
            SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);
            if (!enemy.isAlive || enemy.containerView == null) continue;
            View ev = enemy.containerView;
            float px = ev.getX() + ev.getWidth() / 2f;
            float py = ev.getY() + ev.getHeight() / 2f;
            float reach = LASER_WIDTH / 2f + Math.max(ev.getWidth(), ev.getHeight()) * 0.4f;
            if (distanceToSegment(px, py, sx, sy, ex, ey) <= reach) {
                spawnImpact(area, px, py);
                enemy.takeDamage(LASER_DAMAGE);
                ctx.onHitEnemySuccess();
            }
        }
    }

    // =========================================================
    // Ultimate: Railgun
    // =========================================================
    @Override
    public void executeUltimateSkill(BattleContext ctx) {
        FrameLayout area = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (area == null || player == null) {
            ctx.onUltimateFinished();
            return;
        }

        ctx.setSkillLock(true);   // ชาร์จอยู่ ขยับไม่ได้ (หมุนเล็งด้วยจอยสติ๊กได้)

        final ChargeView charge = new ChargeView(ctx.getContext(), Math.max(area.getWidth(), area.getHeight()) * 1.5f);
        charge.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(charge);

        final float[] aim = {ctx.getPlayerAngle()};

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(CHARGE_MS);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(animation -> {
            if (!ctx.isGameRunning()) {
                animation.cancel();
                return;
            }
            aim[0] = ctx.getPlayerAngle();
            float cx = player.getX() + player.getWidth() / 2f;
            float cy = player.getY() + player.getHeight() / 2f;
            charge.update(cx, cy, aim[0], animation.getAnimatedFraction());
            ctx.setPlayerChargeProgress(animation.getAnimatedFraction());
        });
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                removeFromParent(charge);
                ctx.hidePlayerChargeBar();
                if (cancelled) return;
                fireRailgun(ctx, player, aim[0]);
            }
        });
        anim.start();
    }

    private void fireRailgun(BattleContext ctx, View player, float angleDeg) {
        FrameLayout area = ctx.getGameArea();
        if (area == null) return;

        float rad = (float) Math.toRadians(angleDeg);
        float dirX = (float) Math.cos(rad);
        float dirY = (float) Math.sin(rad);
        float sx = player.getX() + player.getWidth() / 2f;
        float sy = player.getY() + player.getHeight() / 2f;
        // ยาวเกินความกว้างจอ/ความสูงจอ รวมกันเสมอ = ครอบคลุม "ยาวสุดจอ" ทุกทิศ
        float len = (float) Math.hypot(Math.max(area.getWidth(), 1), Math.max(area.getHeight(), 1));
        float ex = sx + dirX * len;
        float ey = sy + dirY * len;

        BeamView beam = new BeamView(ctx.getContext(), sx, sy, ex, ey, BEAM_WIDTH);
        beam.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(beam);
        beam.animate().alpha(0f).setStartDelay(150).setDuration(450)
                .withEndAction(() -> removeFromParent(beam)).start();

        View flash = new View(ctx.getContext());
        flash.setBackgroundColor(Color.parseColor("#55FFFFFF"));
        flash.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(flash);
        flash.animate().alpha(0f).setDuration(220).withEndAction(() -> removeFromParent(flash)).start();

        // ทะลุทุกตัวที่อยู่ในแนวลำแสง
        for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
            SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);
            if (!enemy.isAlive || enemy.containerView == null) continue;
            View ev = enemy.containerView;
            float px = ev.getX() + ev.getWidth() / 2f;
            float py = ev.getY() + ev.getHeight() / 2f;
            float reach = BEAM_WIDTH / 2f + Math.max(ev.getWidth(), ev.getHeight()) * 0.3f;
            if (distanceToSegment(px, py, sx, sy, ex, ey) <= reach) {
                enemy.takeDamage(BEAM_DAMAGE);
                if (enemy.isAlive) {
                    enemy.stun(BEAM_STUN_MS);
                }
            }
        }

        // ยิงครั้งเดียวจบ ไม่ว่าจะโดนหรือไม่
        ctx.setSkillLock(false);
        ctx.onUltimateFinished();
    }

    // =========================================================
    // ตัวช่วย / เอฟเฟกต์
    // =========================================================
    private static float distanceToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax, aby = by - ay;
        float len2 = abx * abx + aby * aby;
        float t = len2 < 0.0001f ? 0f : Math.max(0f, Math.min(1f, ((px - ax) * abx + (py - ay) * aby) / len2));
        return (float) Math.hypot(px - (ax + abx * t), py - (ay + aby * t));
    }

    private static void removeFromParent(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    /** วงกระแทกสั้นๆ ตอนชนขอบจอ/ชนศัตรู/กระสุนโดน */
    private static void spawnImpact(FrameLayout area, float cx, float cy) {
        View ring = new View(area.getContext());
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(Color.TRANSPARENT);
        bg.setStroke(7, Color.parseColor("#FFEB3B"));
        ring.setBackground(bg);
        int d = 150;
        ring.setLayoutParams(new FrameLayout.LayoutParams(d, d));
        ring.setX(cx - d / 2f);
        ring.setY(cy - d / 2f);
        ring.setScaleX(0.2f);
        ring.setScaleY(0.2f);
        area.addView(ring);
        ring.animate().scaleX(1.2f).scaleY(1.2f).alpha(0f).setDuration(280)
                .withEndAction(() -> removeFromParent(ring)).start();
    }

    private static Paint stroke(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        p.setStrokeWidth(width);
        return p;
    }

    /** คลื่นแม่เหล็กรูปพัด: ขอบคลื่นเป็นส่วนโค้งขยายออกไปทางทิศที่ปลาหัน ถึงระยะ WAVE_RANGE แล้วจาง */
    private static class WaveView extends View {
        private final Paint outer = stroke(Color.parseColor("#AA80D8FF"), 26f);
        private final Paint mid = stroke(Color.parseColor("#FF40C4FF"), 12f);
        private final Paint core = stroke(Color.WHITE, 5f);
        private final RectF box = new RectF();
        private final float cx, cy, startAngle;
        private float progress;

        WaveView(Context c, float cx, float cy, float angleDeg) {
            super(c);
            this.cx = cx;
            this.cy = cy;
            this.startAngle = angleDeg - WAVE_SPAN_DEG / 2f;
            outer.setStrokeCap(Paint.Cap.BUTT);
            mid.setStrokeCap(Paint.Cap.BUTT);
            core.setStrokeCap(Paint.Cap.BUTT);
        }

        void setProgress(float p) {
            progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float r = Math.max(1f, WAVE_RANGE * progress);
            int alpha = (int) (255 * (1f - progress * progress));
            box.set(cx - r, cy - r, cx + r, cy + r);
            outer.setAlpha(alpha * 2 / 3);
            mid.setAlpha(alpha);
            core.setAlpha(alpha);
            canvas.drawArc(box, startAngle, WAVE_SPAN_DEG, false, outer);
            canvas.drawArc(box, startAngle, WAVE_SPAN_DEG, false, mid);
            canvas.drawArc(box, startAngle, WAVE_SPAN_DEG, false, core);
        }
    }

    // สีของลูกพลังงาน (ไล่สีจากกลางออกขอบ)
    private static final int CHARGE_ORB_IN = 0xEE80D8FF;
    private static final int CHARGE_ORB_OUT = 0x0000B0FF;

    /** ตอนชาร์จ Railgun: ลูกพลังงานหน้าตัวปลาโตขึ้น + เส้นเล็งตามทิศ (เล็งด้วยจอยสติ๊กได้ก่อนยิง) */
    private static class ChargeView extends View {
        private final Paint aimLine = stroke(Color.parseColor("#AAFF5252"), 4f);
        private final Paint orb = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float length;
        private float cx, cy, angle, frac;

        ChargeView(Context c, float length) {
            super(c);
            this.length = length;
            aimLine.setPathEffect(new DashPathEffect(new float[]{28f, 18f}, 0f));
            core.setColor(Color.WHITE);
            orb.setShader(new RadialGradient(0f, 0f, 1f, CHARGE_ORB_IN, CHARGE_ORB_OUT, Shader.TileMode.CLAMP));
        }

        void update(float cx, float cy, float angle, float frac) {
            this.cx = cx;
            this.cy = cy;
            this.angle = angle;
            this.frac = frac;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            double rad = Math.toRadians(angle);
            float dx = (float) Math.cos(rad), dy = (float) Math.sin(rad);

            // เส้นเล็งแสดงแนวที่ลำแสงจะยิงออกไป
            canvas.drawLine(cx, cy, cx + dx * length, cy + dy * length, aimLine);

            float ox = cx + dx * 60f, oy = cy + dy * 60f;
            float r = 12f + 46f * frac;
            canvas.save();
            canvas.translate(ox, oy);
            canvas.scale(r * 2f, r * 2f);
            canvas.drawCircle(0f, 0f, 1f, orb);
            canvas.restore();
            canvas.drawCircle(ox, oy, r * 0.6f, core);
        }
    }

    /** ลำแสง Railgun: แกนขาว + เรืองฟ้า-เหลือง กว้าง BEAM_WIDTH */
    private static class BeamView extends View {
        private final Paint outer = stroke(Color.parseColor("#6600B0FF"), 10f);
        private final Paint mid = stroke(Color.parseColor("#CC80D8FF"), 10f);
        private final Paint core = stroke(Color.WHITE, 10f);
        private final float x0, y0, x1, y1;

        BeamView(Context c, float x0, float y0, float x1, float y1, float width) {
            super(c);
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            outer.setStrokeWidth(width);
            mid.setStrokeWidth(width * 0.6f);
            core.setStrokeWidth(width * 0.28f);
            outer.setStrokeCap(Paint.Cap.BUTT);
            mid.setStrokeCap(Paint.Cap.BUTT);
            core.setStrokeCap(Paint.Cap.BUTT);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            canvas.drawLine(x0, y0, x1, y1, outer);
            canvas.drawLine(x0, y0, x1, y1, mid);
            canvas.drawLine(x0, y0, x1, y1, core);
        }
    }
}
