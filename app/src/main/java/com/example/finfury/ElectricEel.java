package com.example.finfury;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

/**
 * Electric Eel (วิชาฟิสิกส์):
 *  - Skill 1 Magnetic Repulsion        : คลื่นแม่เหล็กรอบตัวรัศมี 220 px ดาเมจ 2 ผลักถอย 250 px
 *                                         ชนขอบจอ/ศัตรูตัวอื่นระหว่างถูกผลัก = ดาเมจเพิ่ม 2
 *  - Skill 2 Particle Accelerator Shot : กระสุนไฟฟ้าที่วิ่งเร่งขึ้นเรื่อยๆ (พลังงานจลน์) ยิ่งไกลยิ่งแรง 1 -> 4 ดาเมจที่ 1100 px
 *  - Ultimate Railgun                  : ชาร์จ 1 วินาที (ขยับไม่ได้) แล้วยิงลำแสงใหญ่ยาวสุดจอ ทะลุทุกตัว
 *                                         ดาเมจ 8 + สตัน 2 วินาที ยิงครั้งเดียว เล็งพลาดคือเสียเปล่า
 */
public class ElectricEel extends Hero {
    // ---- สเตตัสพื้นฐาน (สมดุล) ----
    @Override public int getMaxHp() { return 85; }
    @Override public float getBaseSpeedMultiplier() { return 1.1f; }
    @Override public int getStackNeeded() { return 9; }
    @Override public long getSkill1CooldownMs() { return 2000; }
    @Override public long getSkill2CooldownMs() { return 5000; }


    // ---------- Skill 1: Magnetic Repulsion ----------
    private static final float REPEL_RADIUS = 220f;
    private static final int REPEL_DAMAGE = 2;
    private static final float KNOCKBACK_DISTANCE = 250f;
    private static final long KNOCKBACK_MS = 320;
    private static final int COLLISION_DAMAGE = 2;

    // ---------- Skill 2: Particle Accelerator Shot ----------
    private static final float SHOT_RANGE = 1100f;
    private static final long SHOT_MS = 800;              // เวลาบินจนสุดระยะ (ระยะ = SHOT_RANGE * t^2 จึงเร่งขึ้นเรื่อยๆ)
    private static final int SHOT_MIN_DAMAGE = 1;
    private static final int SHOT_MAX_DAMAGE = 4;

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
        return "ปล่อยคลื่นแม่เหล็กรอบตัว โดนผลักถอยดาเมจ 2 หน่วย ชนขอบจอ/ศัตรูอื่นโดนอีก 2 ดาเมจ"; }

    @Override public String getSkill2Name() { return "Particle Accelerator Shot"; }
    @Override public String getSkill2Icon() { return "⚛️"; }
    @Override public String getSkill2Description() { return "กระสุนที่เร่งความเร็วตลอดทาง ยิ่งไกลยิ่งแรง 1 ถึง 4 ดาเมจ"; }

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

        FieldView field = new FieldView(ctx.getContext(), cx, cy);
        field.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(field);
        ValueAnimator fx = ValueAnimator.ofFloat(0f, 1f);
        fx.setDuration(380);
        fx.setInterpolator(new DecelerateInterpolator());
        fx.addUpdateListener(a -> field.setProgress((float) a.getAnimatedValue()));
        fx.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeFromParent(field);
            }
        });
        fx.start();

        for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
            SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);
            if (!enemy.isAlive || enemy.containerView == null) continue;
            View ev = enemy.containerView;
            float ex = ev.getX() + ev.getWidth() / 2f;
            float ey = ev.getY() + ev.getHeight() / 2f;
            float dist = (float) Math.hypot(ex - cx, ey - cy);
            if (dist > REPEL_RADIUS + Math.max(ev.getWidth(), ev.getHeight()) / 2f) continue;

            enemy.takeDamage(REPEL_DAMAGE, false);
            ctx.onHitEnemySuccess();
            if (!enemy.isAlive) continue;

            // ผลักออกจากตัวปลา (ถ้าซ้อนทับกันพอดีให้ผลักขึ้นบน)
            float dx = dist > 0.01f ? (ex - cx) / dist : 0f;
            float dy = dist > 0.01f ? (ey - cy) / dist : -1f;
            knockBack(ctx, enemy, dx, dy);
        }
    }

    /** ผลักศัตรู 250 px ตามทิศ (dx,dy) ถ้าชนขอบจอหรือศัตรูตัวอื่นระหว่างทาง = โดนอีก 2 ดาเมจแล้วหยุด */
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
        final float dirX = (float) Math.cos(rad);
        final float dirY = (float) Math.sin(rad);
        final float startX = player.getX() + player.getWidth() / 2f + dirX * 50f;
        final float startY = player.getY() + player.getHeight() / 2f + dirY * 50f;

        final OrbView orb = new OrbView(ctx.getContext());
        final int box = 120;
        orb.setLayoutParams(new FrameLayout.LayoutParams(box, box));
        orb.setX(startX - box / 2f);
        orb.setY(startY - box / 2f);
        area.addView(orb);

        final boolean[] landed = {false};
        final long[] trailTimer = {0};   // เสกเงาไม่ถี่กว่า 40 ms
        final float[] prevDist = {0f};

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(SHOT_MS);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(animation -> {
            if (landed[0]) return;
            float t = animation.getAnimatedFraction();
            float dist = SHOT_RANGE * t * t;       // เร่งความเร็วตลอดทาง
            float charge = dist / SHOT_RANGE;      // 0..1 ยิ่งไกลยิ่งสว่าง/ใหญ่/แรง
            orb.setCharge(charge);

            // วิ่งหลายสิบ px ต่อเฟรม จึงไล่เช็กเป็นช่วงย่อยตลอดเส้นทางของเฟรมนี้ ไม่งั้นทะลุศัตรูไป
            int sub = 4;
            for (int s = 1; s <= sub && !landed[0]; s++) {
                float d = prevDist[0] + (dist - prevDist[0]) * s / sub;
                float px = startX + dirX * d;
                float py = startY + dirY * d;
                float orbR = 14f + 20f * (d / SHOT_RANGE);
                for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
                    SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);
                    if (!enemy.isAlive || enemy.containerView == null) continue;
                    View ev = enemy.containerView;
                    float ex = ev.getX() + ev.getWidth() / 2f;
                    float ey = ev.getY() + ev.getHeight() / 2f;
                    if (Math.hypot(ex - px, ey - py) <= orbR + Math.max(ev.getWidth(), ev.getHeight()) / 2f) {
                        landed[0] = true;
                        animation.cancel();
                        int damage = SHOT_MIN_DAMAGE
                                + Math.round((SHOT_MAX_DAMAGE - SHOT_MIN_DAMAGE) * (d / SHOT_RANGE));
                        removeFromParent(orb);
                        spawnImpact(area, px, py);
                        enemy.takeDamage(damage);
                        ctx.onHitEnemySuccess();
                        break;
                    }
                }
            }
            if (landed[0]) return;

            prevDist[0] = dist;
            orb.setX(startX + dirX * dist - box / 2f);
            orb.setY(startY + dirY * dist - box / 2f);
            // ท้ายกระสุนทิ้งเงาสว่าง ยิ่งเร็วยิ่งยาว
            if (GhostPool.due(trailTimer)) {
                spawnTrail(area, startX + dirX * dist, startY + dirY * dist, 10f + 26f * charge);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeFromParent(orb);
            }
        });
        anim.start();
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

    /** เงาสว่างจางๆ ทิ้งไว้ตามเส้นทางกระสุน */
    private static void spawnTrail(FrameLayout area, float cx, float cy, float size) {
        // วงกลมจากกองที่ใช้ซ้ำ (ไม่สร้าง View ใหม่ทุกเฟรม)
        GhostPool.ghosts(area).spark(cx, cy, (int) size, TRAIL_COLOR, 260);
    }

    private static final int TRAIL_COLOR = 0x6600E5FF;

    private static Paint stroke(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        p.setStrokeWidth(width);
        return p;
    }

    /** คลื่นแม่เหล็ก: วงขยายออกถึงรัศมี 220 px + เส้นแรงแม่เหล็กโค้งพุ่งออกรอบตัว */
    private static class FieldView extends View {
        private final Paint ring = stroke(Color.parseColor("#FF80D8FF"), 8f);
        private final Paint lines = stroke(Color.parseColor("#FFB3E5FC"), 5f);
        private final Path fieldPath = new Path();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float cx, cy;
        private float progress;

        FieldView(Context c, float cx, float cy) {
            super(c);
            this.cx = cx;
            this.cy = cy;
            fill.setColor(Color.parseColor("#2200B0FF"));
        }

        void setProgress(float p) {
            progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float r = REPEL_RADIUS * progress;
            int alpha = (int) (255 * (1f - progress * progress));
            fill.setAlpha((int) (60 * (1f - progress)));
            canvas.drawCircle(cx, cy, r, fill);
            ring.setAlpha(alpha);
            canvas.drawCircle(cx, cy, r, ring);

            // เส้นแรงโค้งออกจากตัว 8 เส้น บิดเป็นเกลียวตามระยะ
            lines.setAlpha(alpha);
            Path p = fieldPath;   // ใช้ Path เดิมซ้ำ (reset ในลูป)
            for (int i = 0; i < 8; i++) {
                double a0 = Math.PI * 2 * i / 8;
                p.reset();
                for (int k = 0; k <= 10; k++) {
                    float t = k / 10f;
                    double a = a0 + t * 0.7;
                    float rr = 20f + (r - 20f) * t;
                    float x = cx + (float) Math.cos(a) * rr;
                    float y = cy + (float) Math.sin(a) * rr;
                    if (k == 0) p.moveTo(x, y);
                    else p.lineTo(x, y);
                }
                canvas.drawPath(p, lines);
            }
        }
    }

    /** ลูกกระสุนอนุภาค: ยิ่งไกล (charge ใกล้ 1) ยิ่งใหญ่ ขาวสว่างขึ้น และมีวงแหวนพลังงานล้อมรอบ */
    // สีของลูกพลังงาน (ไล่สีจากกลางออกขอบ)
    private static final int ORB_GLOW_IN = 0xCC00E5FF;
    private static final int ORB_GLOW_OUT = 0x0000E5FF;
    private static final int CHARGE_ORB_IN = 0xEE80D8FF;
    private static final int CHARGE_ORB_OUT = 0x0000B0FF;

    private static class OrbView extends View {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint halo = stroke(Color.parseColor("#AA80D8FF"), 4f);
        private float charge = 0f;

        OrbView(Context c) {
            super(c);
            core.setColor(Color.WHITE);
            glow.setShader(new RadialGradient(0f, 0f, 1f, ORB_GLOW_IN, ORB_GLOW_OUT, Shader.TileMode.CLAMP));
        }

        void setCharge(float charge) {
            this.charge = charge;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = 14f + 20f * charge;
            // วงเรืองแสง: ไล่สีรัศมี 1 สร้างครั้งเดียว แล้วขยายด้วย canvas.scale (ไม่สร้าง RadialGradient ใหม่ทุกเฟรม)
            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(r * 2.4f, r * 2.4f);
            canvas.drawCircle(0f, 0f, 1f, glow);
            canvas.restore();
            canvas.drawCircle(cx, cy, r * 0.7f, core);
            halo.setAlpha((int) (80 + 175 * charge));
            canvas.drawCircle(cx, cy, r * 1.3f, halo);
        }
    }

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
