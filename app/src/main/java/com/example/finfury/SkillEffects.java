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

    // ภาพเงาจางๆ ทิ้งไว้ด้านหลังตัวที่พุ่ง/กระสุน เพื่อให้เห็นเส้นทางชัด
    private static void spawnGhost(FrameLayout area, float x, float y, int w, int h, int color) {
        View ghost = new View(area.getContext());
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(color);
        ghost.setBackground(bg);
        ghost.setLayoutParams(new FrameLayout.LayoutParams(w, h));
        ghost.setX(x);
        ghost.setY(y);
        area.addView(ghost);
        ghost.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(250)
                .withEndAction(() -> area.removeView(ghost)).start();
    }

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

        dashAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();

            player.setTranslationX(startTx + (finalEndTx - startTx) * progress);
            player.setTranslationY(startTy + (finalEndTy - startTy) * progress);

            spawnGhost(gameArea, player.getX(), player.getY(),
                    player.getWidth(), player.getHeight(), Color.parseColor("#3300E5FF"));

            for (SeaEnemy enemy : ctx.getEnemies()) {
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

        animator.addUpdateListener(animation -> {
            if (hasHit[0]) return;

            float fraction = animation.getAnimatedFraction();
            projectile.setX(startX + (targetX - startX) * fraction);
            projectile.setY(startY + (targetY - startY) * fraction);
            projectile.setRotation(projectile.getRotation() + 18f);
            spawnGhost(gameArea, projectile.getX() + PROJECTILE_SIZE * 0.2f,
                    projectile.getY() + PROJECTILE_SIZE * 0.2f,
                    (int) (PROJECTILE_SIZE * 0.6f), (int) (PROJECTILE_SIZE * 0.6f),
                    Color.parseColor("#44FFEB3B"));

            for (SeaEnemy enemy : ctx.getEnemies()) {
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
        for (SeaEnemy e : ctx.getEnemies()) {
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