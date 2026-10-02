package com.example.finfury;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class Pufferfish extends Hero {

    // ---------- Ultimate: พองตัวดูดศัตรูทุกตัวที่อยู่ในระยะเข้าปาก ----------
    private static final float SUCK_RANGE = 380f;          // px รัศมีวงเล็ง
    private static final long AIM_MIN_MS = 2000;           // เวลาเล็งขั้นต่ำก่อนดูด
    private static final long AIM_MAX_MS = 6000;           // ไม่มีศัตรูเข้าวงเลยถึงเวลานี้ = สกิลหมดอายุ
    private static final float INFLATE_SCALE = 1.9f;
    private static final long SUCK_MS = 450;               // เวลาที่ศัตรูถูกดูดเข้าปาก
    private static final int CHEW_TICKS = 6;               // จำนวนครั้งที่ทำดาเมจขณะอยู่ในปาก
    private static final long CHEW_INTERVAL_MS = 500;
    private static final int CHEW_DAMAGE = 2;              // ต่อศัตรูหนึ่งตัวต่อหนึ่งครั้ง
    private static final float SPIT_DISTANCE = 240f;

    private final Handler handler = new Handler(Looper.getMainLooper());

    public Pufferfish() {
        super("Pufferfish", "Chemistry");
    }

    @Override
    public boolean usesUltimateButton() {
        return true;
    }

    @Override public String getSkill1Name() { return "Inflate"; }
    @Override public String getSkill1Icon() { return "🐡"; }
    @Override public String getSkill1Description() { return "พองตัว 2.2 เท่า นาน 1 วินาที ศัตรูที่ชนโดน 2 ดาเมจ"; }

    @Override public String getSkill2Name() { return "Venom Spray"; }
    @Override public String getSkill2Icon() { return "☠️"; }
    @Override public String getSkill2Description() { return "พ่นพิษสีม่วงเป็นกรวยจากปาก ระยะ 600 px ดาเมจ 2"; }

    @Override public String getUltimateName() { return "Toxic Gulp"; }
    @Override public String getUltimateIcon() { return "🌀"; }
    @Override public String getUltimateDescription() { return "พองตัวดูดศัตรูในวงเข้าปาก บีบ 6 ครั้ง ครั้งละ 2 ดาเมจ แล้วคายออก"; }

    @Override
    public void useSkill1(BattleContext ctx) {
        // พองตัวขยาย 2.2 เท่า ค้าง 1 วินาที ศัตรูที่โดนตัวที่พองได้ดาเมจ 2
        SkillEffects.inflate(ctx, 2.2f, 1000, 2);
    }

    @Override
    public void useSkill2(BattleContext ctx) {
        // พ่นพิษออกจากปาก ระยะ 600 px กรวย 50° นาน 0.6 วินาที ดาเมจ 2
        SkillEffects.venomSpray(ctx, 600f, 50f, 600, 2);
    }

    // =========================================================
    // Ultimate: โชว์วงระยะ ผู้เล่นขยับตัวเล็งเอง -> พองตัวดูดศัตรูทุกตัวที่อยู่ในวงเข้าปาก
    // ทำดาเมจต่อเนื่อง แล้วคายออกมา
    // =========================================================
    @Override
    public void executeUltimateSkill(BattleContext ctx) {
        FrameLayout area = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (area == null || player == null) {
            ctx.onUltimateFinished();
            return;
        }

        final AimView aim = new AimView(ctx.getContext(), SUCK_RANGE);
        aim.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(aim);

        // ช่วงเล็ง: ไม่ล็อกการเคลื่อนที่ ผู้เล่นขยับปลาให้ศัตรูเข้าวงได้เอง
        ValueAnimator aimLoop = ValueAnimator.ofFloat(0f, 1f);
        aimLoop.setDuration(AIM_MAX_MS);
        aimLoop.setInterpolator(new LinearInterpolator());
        final boolean[] fired = {false};

        aimLoop.addUpdateListener(animation -> {
            if (!ctx.isGameRunning()) {
                animation.cancel();
                return;
            }
            float cx = player.getX() + player.getWidth() / 2f;
            float cy = player.getY() + player.getHeight() / 2f;
            List<SeaEnemy> inRange = findInRange(ctx, cx, cy);

            long elapsed = (long) (animation.getAnimatedFraction() * AIM_MAX_MS);
            aim.update(cx, cy, inRange, Math.min(1f, elapsed / (float) AIM_MIN_MS));

            if (!inRange.isEmpty() && elapsed >= AIM_MIN_MS && !fired[0]) {
                fired[0] = true;
                animation.cancel();
                area.removeView(aim);
                suckIn(ctx, player, inRange);
            }
        });
        aimLoop.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (fired[0]) return;
                // หมดเวลาเล็ง (หรือเกมจบ) โดยไม่มีศัตรูเข้าวง
                area.removeView(aim);
                if (ctx.isGameRunning()) ctx.onUltimateFinished();
            }
        });
        aimLoop.start();
    }

    /** ศัตรูที่ยังมีชีวิตทุกตัวที่อยู่ในวงระยะ */
    private static List<SeaEnemy> findInRange(BattleContext ctx, float cx, float cy) {
        List<SeaEnemy> result = new ArrayList<>();
        for (SeaEnemy e : ctx.getEnemies()) {
            if (!e.isAlive || e.isSwallowed() || e.containerView == null) continue;
            float ex = e.containerView.getX() + e.containerView.getWidth() / 2f;
            float ey = e.containerView.getY() + e.containerView.getHeight() / 2f;
            if (Math.hypot(ex - cx, ey - cy) <= SUCK_RANGE) result.add(e);
        }
        return result;
    }

    private void suckIn(BattleContext ctx, View player, List<SeaEnemy> victims) {
        FrameLayout area = ctx.getGameArea();
        ctx.setSkillLock(true);

        float cx = player.getX() + player.getWidth() / 2f;
        float cy = player.getY() + player.getHeight() / 2f;

        // พองตัว + วงแรงดูดหดเข้าหาปาก
        player.animate().scaleX(INFLATE_SCALE).scaleY(INFLATE_SCALE).setDuration(300)
                .setInterpolator(new DecelerateInterpolator()).start();
        for (int i = 0; i < 3; i++) spawnSuctionRing(area, cx, cy, i * 140L);

        // ศัตรูทุกตัวถูกดูดเข้าไปรวมกันตรงกลางปาก (กระจายเล็กน้อยไม่ให้ซ้อนทับสนิท)
        for (SeaEnemy e : victims) {
            e.setSwallowed(true);
            View ev = e.containerView;
            float mouthX = cx - ev.getWidth() / 2f + (float) (Math.random() * 60 - 30);
            float mouthY = cy - ev.getHeight() / 2f + (float) (Math.random() * 60 - 30);
            ev.animate().x(mouthX).y(mouthY).scaleX(0.35f).scaleY(0.35f).alpha(0.75f)
                    .setDuration(SUCK_MS).setInterpolator(new AccelerateInterpolator()).start();
        }

        handler.postDelayed(() -> chew(ctx, player, victims, 0), SUCK_MS);
    }

    private void chew(BattleContext ctx, View player, List<SeaEnemy> victims, int tick) {
        if (!ctx.isGameRunning()) return;

        boolean anyAlive = false;
        for (SeaEnemy e : victims) {
            if (e.isAlive) {
                anyAlive = true;
                break;
            }
        }
        if (tick >= CHEW_TICKS || !anyAlive) {
            spitOut(ctx, player, victims);
            return;
        }

        // ตัวปลาเต้นตุบตามจังหวะเคี้ยว แล้วทุกตัวในปากโดนดาเมจ
        player.animate().scaleX(INFLATE_SCALE * 1.12f).scaleY(INFLATE_SCALE * 1.12f).setDuration(120)
                .withEndAction(() -> player.animate()
                        .scaleX(INFLATE_SCALE).scaleY(INFLATE_SCALE).setDuration(120).start())
                .start();
        for (SeaEnemy e : victims) {
            if (e.isAlive) e.takeDamage(CHEW_DAMAGE);
        }

        handler.postDelayed(() -> chew(ctx, player, victims, tick + 1), CHEW_INTERVAL_MS);
    }

    private void spitOut(BattleContext ctx, View player, List<SeaEnemy> victims) {
        // ยุบตัวกลับ แล้วคายตัวที่ยังรอดออกไปคนละทิศรอบตัว
        player.animate().scaleX(1f).scaleY(1f).setDuration(350)
                .withEndAction(() -> {
                    ctx.setSkillLock(false);
                    ctx.onUltimateFinished();
                }).start();

        int alive = 0;
        for (SeaEnemy e : victims) {
            if (e.isAlive) alive++;
        }

        int i = 0;
        for (SeaEnemy e : victims) {
            e.setSwallowed(false);
            View ev = e.containerView;
            if (!e.isAlive || ev == null) continue;

            double ang = Math.PI * 2 * i / Math.max(alive, 1);
            i++;
            float outX = ev.getX() + (float) Math.cos(ang) * SPIT_DISTANCE;
            float outY = ev.getY() + (float) Math.sin(ang) * SPIT_DISTANCE;
            View area = (View) ev.getParent();
            if (area != null && area.getWidth() > 0) {
                outX = Math.max(0f, Math.min(area.getWidth() - ev.getWidth(), outX));
                outY = Math.max(0f, Math.min(area.getHeight() - ev.getHeight(), outY));
            }
            ev.animate().x(outX).y(outY).scaleX(1f).scaleY(1f).alpha(1f).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    /** วงระยะดูด: วงเส้นประหมุนรอบตัวปลา + กากบาทแดงล็อกบนศัตรูทุกตัวที่อยู่ในวง */
    private static class AimView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lock = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float range;
        private float cx, cy, charge, phase;
        private List<SeaEnemy> targets = new ArrayList<>();

        AimView(Context context, float range) {
            super(context);
            this.range = range;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(Color.parseColor("#229C27B0"));
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(6f);
            ring.setColor(Color.parseColor("#CCCE93D8"));
            lock.setStyle(Paint.Style.STROKE);
            lock.setStrokeWidth(7f);
            lock.setStrokeCap(Paint.Cap.ROUND);
            lock.setColor(Color.parseColor("#FF5252"));
        }

        void update(float cx, float cy, List<SeaEnemy> targets, float charge) {
            this.cx = cx;
            this.cy = cy;
            this.targets = targets;
            this.charge = charge;
            phase += 6f;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            canvas.drawCircle(cx, cy, range, fill);

            // เส้นประวงหมุนช้าๆ ให้รู้ว่าเป็นวงเล็ง
            ring.setPathEffect(new DashPathEffect(new float[]{36f, 22f}, phase));
            canvas.drawCircle(cx, cy, range, ring);

            // วงใน ค่อยๆ ขยายเต็มวงตามเวลาที่ชาร์จ
            canvas.drawCircle(cx, cy, range * charge, ring);

            for (SeaEnemy t : targets) {
                if (t.containerView == null) continue;
                View ev = t.containerView;
                float tx = ev.getX() + ev.getWidth() / 2f;
                float ty = ev.getY() + ev.getHeight() / 2f;
                float r = Math.max(ev.getWidth(), ev.getHeight()) * 0.6f;

                canvas.drawCircle(tx, ty, r, lock);
                float g = r * 0.45f;
                canvas.drawLine(tx - r - g, ty, tx - r + g, ty, lock);
                canvas.drawLine(tx + r - g, ty, tx + r + g, ty, lock);
                canvas.drawLine(tx, ty - r - g, tx, ty - r + g, lock);
                canvas.drawLine(tx, ty + r - g, tx, ty + r + g, lock);
            }
        }
    }

    /** วงสีม่วงใหญ่ๆ หดเข้าหาปาก ให้ดูเหมือนแรงดูด (เหลื่อมเวลากันหลายวง) */
    private static void spawnSuctionRing(FrameLayout area, float cx, float cy, long delayMs) {
        View ring = new View(area.getContext());
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.TRANSPARENT);
        bg.setStroke(8, Color.parseColor("#CCAB47BC"));
        ring.setBackground(bg);
        int d = 700;
        ring.setLayoutParams(new FrameLayout.LayoutParams(d, d));
        ring.setX(cx - d / 2f);
        ring.setY(cy - d / 2f);
        ring.setAlpha(0f);
        area.addView(ring);
        ring.animate().alpha(1f).setDuration(1).setStartDelay(delayMs).withEndAction(() ->
                ring.animate().scaleX(0.1f).scaleY(0.1f).alpha(0f).setDuration(SUCK_MS)
                        .setStartDelay(0).setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> area.removeView(ring)).start()).start();
    }
}
