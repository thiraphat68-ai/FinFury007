package com.example.finfury;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * ชิ้นส่วนสกิลที่ใช้ซ้ำได้ ฮีโร่แต่ละตัวเรียกใช้พร้อมกำหนดค่าของตัวเอง
 * หรือจะเขียนสกิลใหม่ทั้งหมดในไฟล์ฮีโร่โดยไม่ใช้คลาสนี้ก็ได้
 */
public final class SkillEffects {

    private SkillEffects() {}

    private static final int PROJECTILE_SIZE = 80;

    // สีเงาตามเส้นทาง (ภาพเงาจางๆ มาจาก GhostPool ที่ใช้ซ้ำ ไม่สร้าง View ใหม่ทุกเฟรม)
    private static final int GHOST_DASH = 0x3300E5FF;
    private static final int GHOST_PROJECTILE = 0x44FFEB3B;

    // ---------------------------------------------------------
    // พุ่งไปตามทิศของจอยสติ๊ก ชนศัตรูตัวไหนก็ทำดาเมจ (ตัวละ 1 ครั้ง)
    // ---------------------------------------------------------
    public static void dash(BattleContext ctx, float dashDist, int damage) {
        FrameLayout gameArea = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (gameArea == null || player == null) return;

        ctx.setSkillLock(true);

        float rad = (float) Math.toRadians(ctx.getPlayerAngle());

        // ทำงานกับ translation ตรงๆ (getX() รวม translation อยู่แล้ว ห้ามบวกซ้ำ)
        final float startTx = player.getTranslationX();
        final float startTy = player.getTranslationY();

        float endTx = startTx + (float) Math.cos(rad) * dashDist;
        float endTy = startTy + (float) Math.sin(rad) * dashDist;

        if (gameArea.getWidth() > 0 && gameArea.getHeight() > 0) {
            endTx = Math.max(-player.getLeft(), Math.min(gameArea.getWidth() - player.getRight(), endTx));
            endTy = Math.max(-player.getTop(), Math.min(gameArea.getHeight() - player.getBottom(), endTy));
        }

        final float finalEndTx = endTx;
        final float finalEndTy = endTy;

        ValueAnimator dashAnimator = ValueAnimator.ofFloat(0f, 1f);
        dashAnimator.setDuration(150);
        dashAnimator.setInterpolator(new DecelerateInterpolator());

        final List<SeaEnemy> hitEnemies = new ArrayList<>();
        final GhostPool ghostPool = GhostPool.ghosts(gameArea);
        final long[] lastGhost = {0};

        dashAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();

            player.setTranslationX(startTx + (finalEndTx - startTx) * progress);
            player.setTranslationY(startTy + (finalEndTy - startTy) * progress);

            // เสกเงาไม่ถี่กว่า 40 ms
            if (GhostPool.due(lastGhost)) {
                ghostPool.ghost(player.getX(), player.getY(),
                        player.getWidth(), player.getHeight(), GHOST_DASH);
            }

            for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
                SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);
                if (enemy.isAlive && enemy.containerView != null
                        && !hitEnemies.contains(enemy)
                        && isColliding(player, enemy.containerView)) {
                    hitEnemies.add(enemy);
                    enemy.takeDamage(damage);
                    ctx.onHitEnemySuccess();
                }
            }
        });

        dashAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ctx.setSkillLock(false);
            }
        });

        dashAnimator.start();
    }

    // ---------------------------------------------------------
    // พองตัวขึ้น ศัตรูที่ชนตัวที่พองจะโดนดาเมจ (ตัวละ 1 ครั้ง) ค้างไว้ครู่หนึ่งแล้วยุบกลับ
    // ---------------------------------------------------------
    public static void inflate(BattleContext ctx, float scale, long holdMs, int damage) {
        FrameLayout gameArea = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (gameArea == null || player == null) return;

        ctx.setSkillLock(true);

        final List<SeaEnemy> hitEnemies = new ArrayList<>();

        ValueAnimator puff = ValueAnimator.ofFloat(1f, scale);
        puff.setDuration(250);
        puff.setInterpolator(new DecelerateInterpolator());
        puff.addUpdateListener(animation -> {
            float s = (float) animation.getAnimatedValue();
            player.setScaleX(s);
            player.setScaleY(s);

            // scale ไม่เปลี่ยน getX()/getWidth() จึงเช็กชนจากกรอบที่ขยายแล้วเอง
            float cx = player.getX() + player.getWidth() / 2f;
            float cy = player.getY() + player.getHeight() / 2f;
            float halfW = player.getWidth() * s / 2f;
            float halfH = player.getHeight() * s / 2f;

            for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
                SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);
                if (enemy.isAlive && enemy.containerView != null
                        && !hitEnemies.contains(enemy)) {
                    View ev = enemy.containerView;
                    float ex = ev.getX() + ev.getWidth() / 2f;
                    float ey = ev.getY() + ev.getHeight() / 2f;
                    if (Math.abs(ex - cx) < halfW + ev.getWidth() / 2f
                            && Math.abs(ey - cy) < halfH + ev.getHeight() / 2f) {
                        hitEnemies.add(enemy);
                        enemy.takeDamage(damage);
                        ctx.onHitEnemySuccess();
                    }
                }
            }
        });
        puff.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                new Handler(Looper.getMainLooper()).postDelayed(() ->
                        player.animate().scaleX(1f).scaleY(1f).setDuration(300)
                                .withEndAction(() -> ctx.setSkillLock(false)).start(),
                        holdMs);
            }
        });
        puff.start();
    }

    // ---------------------------------------------------------
    // พ่นพิษออกจากปากเป็นลำฟุ้งไปตามทิศของจอยสติ๊ก ศัตรูที่อยู่ในรูปกรวยโดนดาเมจ (ตัวละ 1 ครั้ง)
    // ---------------------------------------------------------
    public static void venomSpray(BattleContext ctx, float range, float coneDeg,
                                  long durationMs, int damage) {
        FrameLayout gameArea = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (gameArea == null || player == null) return;

        final float angle = ctx.getPlayerAngle();
        final float rad = (float) Math.toRadians(angle);
        final float dirX = (float) Math.cos(rad);
        final float dirY = (float) Math.sin(rad);
        final float halfCone = coneDeg / 2f;

        // ปากอยู่ที่ขอบตัวปลาด้านที่หัน (ตัวที่พองอยู่ก็ใช้ขนาดตามจริง)
        final float mouthReach = player.getWidth() * player.getScaleX() / 2f;
        final float originX = player.getX() + player.getWidth() / 2f + dirX * mouthReach;
        final float originY = player.getY() + player.getHeight() / 2f + dirY * mouthReach;

        // ม่วงล้วน หลายเฉดเพื่อให้ละอองดูมีมิติ
        final int[] colors = {0xAA9C27B0, 0xAA7B1FA2, 0xAAAB47BC, 0xAA6A1B9A};
        final java.util.Random rnd = new java.util.Random();
        final List<SeaEnemy> hitEnemies = new ArrayList<>();
        final GhostPool puffPool = GhostPool.puffs(gameArea);

        ValueAnimator spray = ValueAnimator.ofFloat(0f, 1f);
        spray.setDuration(durationMs);
        spray.setInterpolator(new LinearInterpolator());
        spray.addUpdateListener(animation -> {
            // พ่นละอองหลายเม็ดต่อเฟรม แต่ละเม็ดกระจายในมุมกรวยแล้วลอยออกไปจางหาย
            for (int i = 0; i < 3; i++) {
                float a = (float) Math.toRadians(angle + (rnd.nextFloat() - 0.5f) * coneDeg);
                float dist = range * (0.5f + rnd.nextFloat() * 0.5f);
                int size = 30 + rnd.nextInt(40);

                // ละอองมาจากกองที่ใช้ซ้ำ: เริ่มที่ 0.4 เท่า ขยายเป็น 2 เท่า ลอยออกไปแล้วจาง (เหมือนเดิม)
                puffPool.spawn(originX, originY, size, size, colors[rnd.nextInt(colors.length)],
                        0.4f, 2f, (float) Math.cos(a) * dist, (float) Math.sin(a) * dist,
                        450 + rnd.nextInt(200));
            }

            // ลำพิษยาวขึ้นตามเวลา เช็กศัตรูในกรวยเท่าความยาวปัจจุบัน
            float reach = range * Math.min(1f, animation.getAnimatedFraction() * 2.5f);
            for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
                SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);
                if (!enemy.isAlive || enemy.containerView == null || hitEnemies.contains(enemy)) {
                    continue;
                }
                View ev = enemy.containerView;
                float dx = ev.getX() + ev.getWidth() / 2f - originX;
                float dy = ev.getY() + ev.getHeight() / 2f - originY;
                float dist = (float) Math.hypot(dx, dy);
                if (dist > reach + ev.getWidth() / 2f) continue;

                float diff = (float) Math.toDegrees(Math.atan2(dy, dx)) - angle;
                while (diff > 180f) diff -= 360f;
                while (diff < -180f) diff += 360f;
                // ศัตรูตัวใหญ่ ขยายมุมที่ยอมรับตามระยะ (ใกล้ๆ ก็ไม่หลุดกรวย)
                float slack = (float) Math.toDegrees(Math.atan2(ev.getWidth() / 2f, Math.max(dist, 1f)));
                if (Math.abs(diff) <= halfCone + slack) {
                    hitEnemies.add(enemy);
                    enemy.takeDamage(damage);
                    ctx.onHitEnemySuccess();
                }
            }
        });
        spray.start();
    }

    // ---------------------------------------------------------
    // ยิงกระสุนไปตามทิศของจอยสติ๊ก โดนศัตรูตัวแรกแล้วหายไป
    // ---------------------------------------------------------
    public static void projectile(BattleContext ctx, int iconResId, long duration,
                                  float distance, int damage) {
        FrameLayout gameArea = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (gameArea == null || player == null) return;

        ImageView projectile = new ImageView(ctx.getContext());
        projectile.setImageResource(iconResId);
        projectile.setLayoutParams(new FrameLayout.LayoutParams(PROJECTILE_SIZE, PROJECTILE_SIZE));

        float playerX = player.getX() + (player.getWidth() / 2f);
        float playerY = player.getY() + (player.getHeight() / 2f);

        float rad = (float) Math.toRadians(ctx.getPlayerAngle());
        final float startX = playerX + (float) Math.cos(rad) * 40f - PROJECTILE_SIZE / 2f;
        final float startY = playerY + (float) Math.sin(rad) * 40f - PROJECTILE_SIZE / 2f;

        projectile.setX(startX);
        projectile.setY(startY);
        gameArea.addView(projectile);

        final float targetX = startX + (float) Math.cos(rad) * distance;
        final float targetY = startY + (float) Math.sin(rad) * distance;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new LinearInterpolator());

        final boolean[] hasHit = {false};
        final GhostPool ghostPool = GhostPool.ghosts(gameArea);
        final long[] lastGhost = {0};

        animator.addUpdateListener(animation -> {
            if (hasHit[0]) return;

            float fraction = animation.getAnimatedFraction();
            projectile.setX(startX + (targetX - startX) * fraction);
            projectile.setY(startY + (targetY - startY) * fraction);
            projectile.setRotation(projectile.getRotation() + 18f);
            if (GhostPool.due(lastGhost)) {
                ghostPool.ghost(projectile.getX() + PROJECTILE_SIZE * 0.2f,
                        projectile.getY() + PROJECTILE_SIZE * 0.2f,
                        PROJECTILE_SIZE * 0.6f, PROJECTILE_SIZE * 0.6f, GHOST_PROJECTILE);
            }

            for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
                SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);
                if (enemy.isAlive && enemy.containerView != null
                        && isColliding(projectile, enemy.containerView)) {
                    hasHit[0] = true;
                    animator.cancel();
                    gameArea.removeView(projectile);

                    enemy.takeDamage(damage);
                    ctx.onHitEnemySuccess();
                    break;
                }
            }
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!hasHit[0]) {
                    gameArea.removeView(projectile);
                }
            }
        });

        animator.start();
    }

    // ---------------------------------------------------------
    // Ultimate: พุ่งไล่ศัตรูที่ยังมีชีวิตทีละตัว พร้อมเอฟเฟกต์ฟ้าผ่า
    // ---------------------------------------------------------
    public static void thunderChain(BattleContext ctx, int damage) {
        View player = ctx.getPlayerContainer();
        FrameLayout gameArea = ctx.getGameArea();
        if (player == null || gameArea == null) return;

        ctx.setSkillLock(true);

        View flashView = new View(ctx.getContext());
        flashView.setBackgroundColor(Color.parseColor("#40FFFF00"));
        flashView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        gameArea.addView(flashView);
        flashView.animate().alpha(0f).setDuration(600)
                .withEndAction(() -> gameArea.removeView(flashView)).start();

        final List<SeaEnemy> targets = new ArrayList<>();
        for (int eIdx = 0; eIdx < ctx.getEnemies().size(); eIdx++) {
            SeaEnemy e = ctx.getEnemies().get(eIdx);
            if (e.isAlive) targets.add(e);
        }

        if (targets.isEmpty()) {
            ctx.setSkillLock(false);
            ctx.onUltimateFinished();
            return;
        }

        final float originalX = player.getTranslationX();
        final float originalY = player.getTranslationY();

        player.animate().scaleX(1.3f).scaleY(1.3f).setDuration(300)
                .withEndAction(() -> chainDash(ctx, targets, 0, originalX, originalY, damage))
                .start();
    }

    private static void chainDash(BattleContext ctx, List<SeaEnemy> targets, int index,
                                  float startTransX, float startTransY, int damage) {
        View player = ctx.getPlayerContainer();
        FrameLayout gameArea = ctx.getGameArea();
        if (player == null) return;

        if (index >= targets.size()) {
            player.animate().translationX(startTransX).translationY(startTransY)
                    .scaleX(1f).scaleY(1f).setDuration(350).withEndAction(() -> {
                        ctx.setSkillLock(false);
                        ctx.onUltimateFinished();
                    }).start();
            return;
        }

        SeaEnemy target = targets.get(index);

        if (!target.isAlive || target.containerView == null) {
            chainDash(ctx, targets, index + 1, startTransX, startTransY, damage);
            return;
        }

        float targetX = target.containerView.getX();
        float targetY = target.containerView.getY();

        LightningEffectView lightning = new LightningEffectView(ctx.getContext(),
                player.getX() + player.getWidth() / 2f,
                player.getY() + player.getHeight() / 2f,
                targetX + target.containerView.getWidth() / 2f,
                targetY + target.containerView.getHeight() / 2f);
        if (gameArea != null) gameArea.addView(lightning);

        player.animate()
                .translationX(targetX - player.getLeft())
                .translationY(targetY - player.getTop())
                .setDuration(350)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    target.takeDamage(damage);

                    if (target.containerView != null) {
                        target.containerView.animate().translationYBy(-20f).setDuration(120)
                                .withEndAction(() -> {
                                    if (target.containerView != null) {
                                        target.containerView.animate().translationYBy(20f).setDuration(120).start();
                                    }
                                }).start();
                    }

                    if (gameArea != null) gameArea.removeView(lightning);

                    new Handler(Looper.getMainLooper()).postDelayed(
                            () -> chainDash(ctx, targets, index + 1, startTransX, startTransY, damage),
                            100);
                }).start();
    }

    // ---------------------------------------------------------
    // ตัวช่วยเช็กการชน
    // ---------------------------------------------------------
    private static boolean isColliding(View v1, View v2) {
        if (v1 == null || v2 == null) return false;

        float x1 = v1.getX();
        float y1 = v1.getY();
        float w1 = v1.getWidth() > 0 ? v1.getWidth() : 80f;
        float h1 = v1.getHeight() > 0 ? v1.getHeight() : 80f;

        float x2 = v2.getX();
        float y2 = v2.getY();
        float w2 = v2.getWidth() > 0 ? v2.getWidth() : 80f;
        float h2 = v2.getHeight() > 0 ? v2.getHeight() : 80f;

        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    private static class LightningEffectView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        LightningEffectView(Context context, float sx, float sy, float ex, float ey) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(14f);
            paint.setColor(Color.parseColor("#FFF59D"));
            paint.setShadowLayer(28f, 0f, 0f, Color.parseColor("#00E5FF"));

            path.moveTo(sx, sy);
            int steps = 5;
            float dx = (ex - sx) / steps;
            float dy = (ey - sy) / steps;

            for (int i = 1; i < steps; i++) {
                float px = sx + (dx * i) + (float) (Math.random() * 110 - 55);
                float py = sy + (dy * i) + (float) (Math.random() * 110 - 55);
                path.lineTo(px, py);
            }
            path.lineTo(ex, ey);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawPath(path, paint);
        }
    }
}