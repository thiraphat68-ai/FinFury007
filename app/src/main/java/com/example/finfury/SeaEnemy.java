package com.example.finfury;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

public class SeaEnemy {
    private final BattleContext ctx;

    public final String name;
    public final String emoji;
    public int hp = 10;
    public int maxHp = 10;
    public boolean isAlive = true;
    public View containerView;

    private TextView imgAvatar;
    private ProgressBar barHp;
    private TextView txtHp;
    private final float speed = 1.1f;
    private long lastAttackTime = 0;
    private float animTime = (float) (Math.random() * 10);

    public SeaEnemy(BattleContext ctx, String name, String emoji, float posX, float posY) {
        this.ctx = ctx;
        this.name = name;
        this.emoji = emoji;
        createView(posX, posY);
    }

    private void createView(float posX, float posY) {
        LinearLayout layout = new LinearLayout(ctx.getContext());
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);

        TextView txtName = new TextView(ctx.getContext());
        txtName.setText(name);
        txtName.setTextColor(Color.WHITE);
        txtName.setTextSize(11f);
        txtName.setGravity(Gravity.CENTER);

        barHp = new ProgressBar(ctx.getContext(), null, android.R.attr.progressBarStyleHorizontal);
        barHp.setMax(maxHp);
        barHp.setProgress(hp);
        barHp.setProgressTintList(ColorStateList.valueOf(Color.RED));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(90, 14);
        barParams.bottomMargin = 2;
        barHp.setLayoutParams(barParams);

        txtHp = new TextView(ctx.getContext());
        txtHp.setText(String.format(Locale.US, "%d/%d", hp, maxHp));
        txtHp.setTextColor(Color.YELLOW);
        txtHp.setTextSize(9f);
        txtHp.setGravity(Gravity.CENTER);

        imgAvatar = new TextView(ctx.getContext());
        imgAvatar.setText(emoji);
        imgAvatar.setTextSize(36f);
        imgAvatar.setGravity(Gravity.CENTER);

        layout.addView(txtName);
        layout.addView(barHp);
        layout.addView(txtHp);
        layout.addView(imgAvatar);

        layout.setX(posX);
        layout.setY(posY);

        containerView = layout;
        if (ctx.getGameArea() != null) {
            ctx.getGameArea().addView(containerView);
        }
    }

    public void updateAI(float targetX, float targetY) {
        if (!isAlive || containerView == null || !ctx.isGameRunning() || ctx.isGamePaused()) return;

        float currentX = containerView.getX();
        float currentY = containerView.getY();

        float dx = targetX - currentX;
        float dy = targetY - currentY;
        float distance = (float) Math.hypot(dx, dy);

        float nextX = currentX;
        float nextY = currentY;

        if (distance > 45f) {
            float dirX = dx / distance;
            float dirY = dy / distance;
            nextX += (dirX * speed);
            nextY += (dirY * speed);
        } else {
            long now = System.currentTimeMillis();
            if (now - lastAttackTime > 800) {
                lastAttackTime = now;
                ctx.damagePlayer(5);
            }
        }

        for (SeaEnemy other : ctx.getEnemies()) {
            if (other != this && other.isAlive && other.containerView != null) {
                float ox = other.containerView.getX();
                float oy = other.containerView.getY();
                float distToOther = (float) Math.hypot(nextX - ox, nextY - oy);

                if (distToOther > 0 && distToOther < 80f) {
                    float pushX = (nextX - ox) / distToOther;
                    float pushY = (nextY - oy) / distToOther;
                    nextX += pushX * 1.5f;
                    nextY += pushY * 1.5f;
                }
            }
        }

        containerView.setX(nextX);
        containerView.setY(nextY);

        animTime += 0.08f;
        float facing = (dx < 0) ? 1f : -1f;
        float wave = (float) Math.sin(animTime * 2.5f);
        float tilt = (float) Math.sin(animTime * 1.5f) * 7f;

        containerView.setRotation(tilt);
        containerView.setScaleX(facing * (1.0f + wave * 0.06f));
        containerView.setScaleY(1.0f - wave * 0.06f);
    }

    public void takeDamage(int damage) {
        if (!isAlive || !ctx.isGameRunning() || containerView == null) return;

        hp = Math.max(0, hp - damage);
        if (barHp != null) barHp.setProgress(hp);
        if (txtHp != null) txtHp.setText(String.format(Locale.US, "%d/%d", hp, maxHp));

        containerView.animate().alpha(0.3f).setDuration(80)
                .withEndAction(() -> {
                    if (containerView != null) containerView.animate().alpha(1.0f).setDuration(80).start();
                }).start();

        if (hp <= 0) {
            isAlive = false;
            containerView.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(250)
                    .withEndAction(() -> {
                        if (containerView != null) containerView.setVisibility(View.GONE);
                    }).start();

            ctx.onEnemyDefeated();
        }
    }
}