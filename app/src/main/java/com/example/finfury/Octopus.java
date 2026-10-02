package com.example.finfury;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Octopus (วิชาการเขียนโปรแกรม):
 *  - Skill 1 Tentacle Loop : ไม่พุ่ง หวดหนวดรอบตัว 3 รอบ (for loop) รอบละ 1 ดาเมจ ศัตรูทุกตัวในรัศมี 200 px
 *  - Skill 2 Bug Ink       : ยิงก้อนหมึกตรงๆ ดาเมจ 2 ตกที่ไหนทิ้งแอ่งหมึก 3 วินาที ศัตรูในแอ่งช้าลง 50%
 *  - Ultimate Infinite Loop: หนวดทั้ง 8 คว้าศัตรูทุกตัวมากองรวมกันด้านหน้า ตรึงไว้ 4 วินาที
 *                            บีบ 1 ดาเมจทุก 0.5 วินาที (รวม 8) กองอยู่ในระยะ Tentacle Loop พอดี
 */
public class Octopus extends Hero {

    // ---------- Skill 1: Tentacle Loop ----------
    private static final float LOOP_RADIUS = 200f;
    private static final int LOOP_SPINS = 3;
    private static final long SPIN_MS = 300;
    private static final int LOOP_DAMAGE = 1;

    // ---------- Skill 2: Bug Ink ----------
    private static final float INK_DISTANCE = 900f;
    private static final long INK_DURATION_MS = 500;
    private static final int INK_SIZE = 56;
    private static final int INK_DAMAGE = 2;
    private static final float PUDDLE_RADIUS = 110f;
    private static final long PUDDLE_MS = 3000;
    private static final float PUDDLE_SLOW = 0.5f;

    // ---------- Ultimate: Infinite Loop ----------
    private static final float GATHER_DISTANCE = 150f;    // กองศัตรูอยู่หน้าปลา (ในรัศมี Tentacle Loop 200 px)
    private static final long PULL_MS = 600;
    private static final long HOLD_MS = 4000;
    private static final long SQUEEZE_INTERVAL_MS = 500;  // 4000 / 500 = 8 ครั้ง
    private static final int SQUEEZE_DAMAGE = 1;
    private static final int TENTACLES = 8;

    // ระหว่างตรึงศัตรูไม่สะสมสแตก ไม่งั้น quiz เด้งขึ้นมาขัดกลางจังหวะ
    private boolean holding = false;
    private ValueAnimator holdLoop;
    private GrabView grabView;
    private final List<SeaEnemy> held = new ArrayList<>();

    public Octopus() {
        super("Octopus", "Programming");
    }

    @Override
    public boolean usesUltimateButton() {
        return true;
    }

    @Override public String getSkill1Name() { return "Tentacle Loop"; }
    @Override public String getSkill1Icon() { return "🐙"; }
    @Override public String getSkill1Description() { return "หวดหนวดรอบตัว 3 รอบ รอบละ 1 ดาเมจ ศัตรูในรัศมี 200 px"; }

    @Override public String getSkill2Name() { return "Bug Ink"; }
    @Override public String getSkill2Icon() { return "🖋️"; }
    @Override public String getSkill2Description() { return "ยิงหมึก 2 ดาเมจ ทิ้งแอ่งหมึก 3 วินาที ศัตรูในแอ่งช้าลง 50%"; }

    @Override public String getUltimateName() { return "Infinite Loop"; }
    @Override public String getUltimateIcon() { return "♾️"; }
    @Override public String getUltimateDescription() { return "หนวดดึงศัตรูทั้งหมดมารวมกัน ตรึง 4 วินาที บีบ 8 ครั้ง ครั้งละ 1 ดาเมจ"; }

    // =========================================================
    // Skill 1: Tentacle Loop  for (int i = 0; i < 3; i++) { หวดรอบตัว }
    // =========================================================
    @Override
    public void useSkill1(BattleContext ctx) {
        FrameLayout area = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (area == null || player == null) return;

        final SpinView spin = new SpinView(ctx.getContext());
        spin.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(spin);

        final Set<SeaEnemy> everHit = new HashSet<>();
        final int[] applied = {0};

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(SPIN_MS * LOOP_SPINS);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(animation -> {
            float revolutions = animation.getAnimatedFraction() * LOOP_SPINS;
            float cx = player.getX() + player.getWidth() / 2f;
            float cy = player.getY() + player.getHeight() / 2f;
            spin.update(cx, cy, revolutions);

            // หนวดหวดโดนกลางรอบ (ครบครึ่งรอบแล้วคิดดาเมจรอบนั้น)
            while (applied[0] < LOOP_SPINS && revolutions >= applied[0] + 0.5f) {
                applied[0]++;
                for (SeaEnemy enemy : ctx.getEnemies()) {
                    if (!enemy.isAlive || enemy.containerView == null) continue;
                    View ev = enemy.containerView;
                    float ex = ev.getX() + ev.getWidth() / 2f;
                    float ey = ev.getY() + ev.getHeight() / 2f;
                    float reach = LOOP_RADIUS + Math.max(ev.getWidth(), ev.getHeight()) / 2f;
                    if (Math.hypot(ex - cx, ey - cy) <= reach) {
                        // ไม่ผลักถอย ไม่งั้นศัตรูหลุดวงตั้งแต่รอบแรก รอบที่ 2-3 ไม่โดน
                        enemy.takeDamage(LOOP_DAMAGE, false);
                        if (everHit.add(enemy) && !holding) ctx.onHitEnemySuccess();
                    }
                }
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeFromParent(spin);
            }
        });
        anim.start();
    }

    // =========================================================
    // Skill 2: Bug Ink ก้อนหมึกตกแล้วกลายเป็นแอ่ง "บั๊ก" ที่ทำให้ศัตรูช้า
    // =========================================================
    @Override
    public void useSkill2(BattleContext ctx) {
        FrameLayout area = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (area == null || player == null) return;

        float rad = (float) Math.toRadians(ctx.getPlayerAngle());
        final float dirX = (float) Math.cos(rad);
        final float dirY = (float) Math.sin(rad);

        final float startCx = player.getX() + player.getWidth() / 2f + dirX * 50f;
        final float startCy = player.getY() + player.getHeight() / 2f + dirY * 50f;
        final float endCx = startCx + dirX * INK_DISTANCE;
        final float endCy = startCy + dirY * INK_DISTANCE;

        final View blob = new View(ctx.getContext());
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#7E57C2"), Color.parseColor("#311B92")});
        bg.setShape(GradientDrawable.OVAL);
        blob.setBackground(bg);
        blob.setLayoutParams(new FrameLayout.LayoutParams(INK_SIZE, INK_SIZE));
        blob.setX(startCx - INK_SIZE / 2f);
        blob.setY(startCy - INK_SIZE / 2f);
        area.addView(blob);

        final boolean[] landed = {false};

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(INK_DURATION_MS);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(animation -> {
            if (landed[0]) return;
            float f = animation.getAnimatedFraction();
            float cx = startCx + (endCx - startCx) * f;
            float cy = startCy + (endCy - startCy) * f;
            blob.setX(cx - INK_SIZE / 2f);
            blob.setY(cy - INK_SIZE / 2f);
            // ก้อนหมึกยืด/หดเล็กน้อยระหว่างบิน
            float squash = 1f + 0.18f * (float) Math.sin(f * Math.PI * 6);
            blob.setScaleX(squash);
            blob.setScaleY(2f - squash);

            for (SeaEnemy enemy : ctx.getEnemies()) {
                if (!enemy.isAlive || enemy.containerView == null) continue;
                View ev = enemy.containerView;
                float ex = ev.getX() + ev.getWidth() / 2f;
                float ey = ev.getY() + ev.getHeight() / 2f;
                if (Math.hypot(ex - cx, ey - cy) <= INK_SIZE / 2f + Math.max(ev.getWidth(), ev.getHeight()) / 2f) {
                    landed[0] = true;
                    animation.cancel();
                    enemy.takeDamage(INK_DAMAGE);
                    if (!holding) ctx.onHitEnemySuccess();
                    spawnPuddle(ctx, cx, cy);
                    return;
                }
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeFromParent(blob);
                // ไม่โดนใคร: ตกลงพื้นที่ปลายทาง ก็ยังทิ้งแอ่งไว้
                if (!landed[0]) {
                    landed[0] = true;
                    spawnPuddle(ctx, endCx, endCy);
                }
            }
        });
        anim.start();
    }

    /** แอ่งหมึก: ศัตรูที่อยู่ในแอ่งถูกสโลว์ 50% ทุกเฟรมที่อยู่ข้างใน */
    private void spawnPuddle(BattleContext ctx, float cx, float cy) {
        FrameLayout area = ctx.getGameArea();
        if (area == null) return;

        final PuddleView puddle = new PuddleView(ctx.getContext(), PUDDLE_RADIUS);
        int d = (int) (PUDDLE_RADIUS * 2);
        puddle.setLayoutParams(new FrameLayout.LayoutParams(d, d));
        puddle.setX(cx - PUDDLE_RADIUS);
        puddle.setY(cy - PUDDLE_RADIUS);
        puddle.setScaleX(0.3f);
        puddle.setScaleY(0.3f);
        area.addView(puddle);
        puddle.animate().scaleX(1f).scaleY(1f).setDuration(180).start();

        ValueAnimator life = ValueAnimator.ofFloat(0f, 1f);
        life.setDuration(PUDDLE_MS);
        life.setInterpolator(new LinearInterpolator());
        life.addUpdateListener(animation -> {
            if (!ctx.isGameRunning()) {
                animation.cancel();
                return;
            }
            float f = animation.getAnimatedFraction();
            // ครึ่งวินาทีสุดท้ายแอ่งค่อยๆ จาง
            puddle.setAlpha(f > 0.85f ? (1f - f) / 0.15f : 1f);

            for (SeaEnemy enemy : ctx.getEnemies()) {
                if (!enemy.isAlive || enemy.containerView == null) continue;
                View ev = enemy.containerView;
                float ex = ev.getX() + ev.getWidth() / 2f;
                float ey = ev.getY() + ev.getHeight() / 2f;
                if (Math.hypot(ex - cx, ey - cy) <= PUDDLE_RADIUS) {
                    enemy.applySlow(PUDDLE_SLOW, 150);
                }
            }
        });
        life.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeFromParent(puddle);
            }
        });
        life.start();
    }

    // =========================================================
    // Ultimate: Infinite Loop คว้าทุกตัวมากองหน้าตัว ตรึง 4 วินาที บีบทุก 0.5 วินาที
    // =========================================================
    @Override
    public void executeUltimateSkill(BattleContext ctx) {
        FrameLayout area = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (area == null || player == null) {
            ctx.onUltimateFinished();
            return;
        }

        releaseAll();   // ใช้ซ้ำระหว่างตรึงอยู่ = เริ่มใหม่

        final List<SeaEnemy> victims = new ArrayList<>();
        for (SeaEnemy e : ctx.getEnemies()) {
            if (e.isAlive && !e.isSwallowed() && e.containerView != null) victims.add(e);
        }
        if (victims.isEmpty()) {
            ctx.onUltimateFinished();
            return;
        }

        // จุดรวมพลอยู่ "ด้านหน้า" ปลา ตามทิศที่หันอยู่ และต้องอยู่ในจอ
        float rad = (float) Math.toRadians(ctx.getPlayerAngle());
        float pcx = player.getX() + player.getWidth() / 2f;
        float pcy = player.getY() + player.getHeight() / 2f;
        float gx = pcx + (float) Math.cos(rad) * GATHER_DISTANCE;
        float gy = pcy + (float) Math.sin(rad) * GATHER_DISTANCE;
        if (area.getWidth() > 0 && area.getHeight() > 0) {
            gx = Math.max(90f, Math.min(area.getWidth() - 90f, gx));
            gy = Math.max(90f, Math.min(area.getHeight() - 90f, gy));
        }

        held.addAll(victims);
        holding = true;

        // ศัตรูกองรวมกันเป็นวงเล็กๆ รอบจุดรวมพล ไม่ซ้อนทับสนิท
        int n = victims.size();
        for (int i = 0; i < n; i++) {
            SeaEnemy e = victims.get(i);
            e.setSwallowed(true);     // หยุด AI: เคลื่อนที่/โจมตีไม่ได้
            View ev = e.containerView;
            double a = Math.PI * 2 * i / n;
            float ring = n == 1 ? 0f : 55f;
            float tx = gx + (float) Math.cos(a) * ring - ev.getWidth() / 2f;
            float ty = gy + (float) Math.sin(a) * ring - ev.getHeight() / 2f;
            ev.animate().x(tx).y(ty).setDuration(PULL_MS)
                    .setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }

        grabView = new GrabView(ctx.getContext(), player, held);
        grabView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(grabView);

        final long[] elapsed = {0};
        final long[] lastNs = {System.nanoTime()};
        final int[] ticks = {0};
        final boolean[] unlocked = {false};

        holdLoop = ValueAnimator.ofFloat(0f, 1f);
        holdLoop.setDuration(60_000);   // เพดานกันค้าง จบจริงด้วยตัวนับเวลาด้านล่าง
        holdLoop.setInterpolator(new LinearInterpolator());
        holdLoop.addUpdateListener(animation -> {
            long now = System.nanoTime();
            long dtMs = (now - lastNs[0]) / 1_000_000L;
            lastNs[0] = now;

            if (!ctx.isGameRunning()) {
                releaseAll();
                return;
            }
            if (ctx.isGamePaused()) return;   // quiz ขึ้นอยู่ เวลาหยุดนับ

            elapsed[0] += dtMs;
            grabView.update(Math.min(1f, elapsed[0] / (float) PULL_MS));

            // ดึงเข้ามาครบแล้ว: ปลดล็อกปุ่มสกิลให้ผู้เล่นใช้ Tentacle Loop ใส่กองศัตรูได้ทันที
            if (!unlocked[0] && elapsed[0] >= PULL_MS) {
                unlocked[0] = true;
                ctx.onUltimateFinished();
            }

            // บีบทุก 0.5 วินาทีหลังดึงเข้ามาครบ รวม 8 ครั้ง
            while (ticks[0] < HOLD_MS / SQUEEZE_INTERVAL_MS
                    && elapsed[0] >= PULL_MS + (ticks[0] + 1) * SQUEEZE_INTERVAL_MS) {
                ticks[0]++;
                for (SeaEnemy e : held) {
                    if (e.isAlive) e.takeDamage(SQUEEZE_DAMAGE, false);
                }
                grabView.pulse();
            }

            boolean anyAlive = false;
            for (SeaEnemy e : held) {
                if (e.isAlive) {
                    anyAlive = true;
                    break;
                }
            }
            if (elapsed[0] >= PULL_MS + HOLD_MS || !anyAlive) {
                if (!unlocked[0]) {
                    unlocked[0] = true;
                    ctx.onUltimateFinished();
                }
                releaseAll();
            }
        });
        holdLoop.start();
    }

    /** ปล่อยศัตรูทุกตัวและเก็บหนวด */
    private void releaseAll() {
        holding = false;
        for (SeaEnemy e : held) e.setSwallowed(false);
        held.clear();
        if (holdLoop != null) {
            ValueAnimator l = holdLoop;
            holdLoop = null;
            l.removeAllUpdateListeners();
            l.cancel();
        }
        if (grabView != null) {
            View v = grabView;
            grabView = null;
            v.animate().alpha(0f).setDuration(250).withEndAction(() -> removeFromParent(v)).start();
        }
    }

    private static void removeFromParent(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    private static Paint stroke(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        p.setStrokeWidth(width);
        return p;
    }

    // =========================================================
    // วิวที่วาดเอง
    // =========================================================

    /** หนวด 8 เส้นหวดเป็นวงรอบตัวปลา หมุนตามจำนวนรอบ ปลายหนวดโค้งตามหลัง */
    private static class SpinView extends View {
        private final Paint glow = stroke(Color.parseColor("#66FF5252"), 26f);
        private final Paint core = stroke(Color.parseColor("#FFD32F2F"), 12f);
        private final Paint ring = stroke(Color.parseColor("#33FF5252"), 4f);
        private float cx, cy, revolutions;

        SpinView(Context c) {
            super(c);
        }

        void update(float cx, float cy, float revolutions) {
            this.cx = cx;
            this.cy = cy;
            this.revolutions = revolutions;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            // วงขอบเขตจริงของสกิล (รัศมีโดนศัตรู) จางๆ ให้ผู้เล่นเห็นระยะ
            canvas.drawCircle(cx, cy, LOOP_RADIUS, ring);

            double base = revolutions * Math.PI * 2;
            Path path = new Path();
            for (int i = 0; i < TENTACLES; i++) {
                double a0 = base + Math.PI * 2 * i / TENTACLES;
                path.reset();
                int steps = 14;
                for (int k = 0; k <= steps; k++) {
                    float t = k / (float) steps;
                    float r = 30f + (LOOP_RADIUS - 30f) * t;
                    // ปลายหนวดโค้งตามหลังแนวหวด
                    double a = a0 - t * 0.9;
                    float x = cx + (float) Math.cos(a) * r;
                    float y = cy + (float) Math.sin(a) * r;
                    if (k == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                canvas.drawPath(path, glow);
                canvas.drawPath(path, core);
            }
        }
    }

    /** แอ่งหมึกซ้อนวงกลมหลายวงให้ดูเป็นหยดไม่เรียบ + 🐞 ตรงกลางบอกว่าเป็นบั๊ก */
    private static class PuddleView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = stroke(Color.parseColor("#AA7E57C2"), 5f);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float radius;

        PuddleView(Context c, float radius) {
            super(c);
            this.radius = radius;
            fill.setColor(Color.parseColor("#B3311B92"));
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(radius * 0.5f);
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float c = radius;
            canvas.drawCircle(c, c, radius * 0.86f, fill);
            canvas.drawCircle(c - radius * 0.4f, c - radius * 0.2f, radius * 0.5f, fill);
            canvas.drawCircle(c + radius * 0.38f, c - radius * 0.3f, radius * 0.42f, fill);
            canvas.drawCircle(c + radius * 0.2f, c + radius * 0.5f, radius * 0.46f, fill);
            canvas.drawCircle(c - radius * 0.35f, c + radius * 0.42f, radius * 0.4f, fill);
            canvas.drawCircle(c, c, radius * 0.9f, edge);
            canvas.drawText("🐞", c, c + radius * 0.17f, text);
        }
    }

    /** หนวด 8 เส้นเลื้อยจากตัวปลาไปรัดศัตรูที่ถูกตรึง (ปลายหนวดตามตัวศัตรูไปเอง) */
    private static class GrabView extends View {
        private final Paint glow = stroke(Color.parseColor("#66FF5252"), 24f);
        private final Paint core = stroke(Color.parseColor("#FFD32F2F"), 12f);
        private final View player;
        private final List<SeaEnemy> victims;
        private float reach = 0f;
        private float pulse = 0f;

        GrabView(Context c, View player, List<SeaEnemy> victims) {
            super(c);
            this.player = player;
            this.victims = victims;
        }

        /** reach 0..1 = หนวดยืดออกไปถึงศัตรูมากแค่ไหน */
        void update(float reach) {
            this.reach = reach;
            invalidate();
        }

        void pulse() {
            pulse = 1f;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            List<SeaEnemy> alive = new ArrayList<>();
            for (SeaEnemy e : victims) {
                if (e.isAlive && e.containerView != null) alive.add(e);
            }
            if (alive.isEmpty()) return;

            float px = player.getX() + player.getWidth() / 2f;
            float py = player.getY() + player.getHeight() / 2f;
            float phase = System.nanoTime() / 150e6f;

            glow.setStrokeWidth(24f + 14f * pulse);
            core.setStrokeWidth(12f + 6f * pulse);

            Path path = new Path();
            for (int i = 0; i < TENTACLES; i++) {
                // หนวด 8 เส้นแบ่งกันไปรัดศัตรูที่เหลือ (ถ้าศัตรูน้อยกว่า 8 หลายเส้นรัดตัวเดียวกัน)
                SeaEnemy e = alive.get(i % alive.size());
                View ev = e.containerView;
                float ex = ev.getX() + ev.getWidth() / 2f;
                float ey = ev.getY() + ev.getHeight() / 2f;

                // หนวดแต่ละเส้นออกจากตัวปลาคนละมุม แล้วเลื้อยไปหาเป้า
                double sa = Math.PI * 2 * i / TENTACLES;
                float sx = px + (float) Math.cos(sa) * 24f;
                float sy = py + (float) Math.sin(sa) * 24f;

                float dx = ex - sx, dy = ey - sy;
                float len = Math.max((float) Math.hypot(dx, dy), 1f);
                float nx = -dy / len, ny = dx / len;

                path.reset();
                int steps = 16;
                for (int k = 0; k <= steps; k++) {
                    float t = k / (float) steps * reach;
                    float wob = (float) Math.sin(phase + i + t * 9f) * 16f * (1f - t * 0.5f)
                            * (float) Math.sin(t * Math.PI);
                    float x = sx + dx * t + nx * wob;
                    float y = sy + dy * t + ny * wob;
                    if (k == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                canvas.drawPath(path, glow);
                canvas.drawPath(path, core);
            }
            pulse = Math.max(0f, pulse - 0.05f);
        }
    }
}
