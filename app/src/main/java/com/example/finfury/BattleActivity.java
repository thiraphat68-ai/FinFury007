package com.example.finfury;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings({"unchecked", "rawtypes"})
public class BattleActivity extends AppCompatActivity {

    private FrameLayout gameArea;
    private View playerContainer;
    private ImageView imgPlayer;
    private ImageView imgProfile;
    private View joystickKnob;

    private ProgressBar barPlayerHp;
    private ProgressBar stackProgressBar;
    private TextView txtStackGauge;

    private float joystickCenterX, joystickCenterY;
    private float moveX = 0f, moveY = 0f;
    private float swimTime = 0f;

    private float velX, velY, faceScale = -1f, tilt;
    private long lastFrameNs = 0;
    private boolean skillLock = false;
    private static final float MAX_SPEED = 700f;
    private boolean isFacingRight = true;

    private Hero playerHero;
    private int currentHeroId = 1;
    private int currentStageId = 1;
    private int playerHp = 100;
    private final int maxPlayerHp = 100;
    private int currentStack = 0;
    private static final int MAX_STACK = 10;

    private float playerAngle = 0f;

    private boolean isGameRunning = true;
    private boolean isGamePaused = false;

    private final List enemyList = new ArrayList();

    // --- Quiz System ---
    private static class QuizQuestion {
        String question;
        String[] options;
        int correctIndex;

        QuizQuestion(String question, String[] options, int correctIndex) {
            this.question = question;
            this.options = options;
            this.correctIndex = correctIndex;
        }
    }

    private final List quizBank = new ArrayList();
    private int lastQuestionIndex = -1;
    private CountDownTimer quizTimer;
    private AlertDialog quizDialog;

    private void initQuizBank() {
        quizBank.clear();
        quizBank.add(new QuizQuestion("ดิฟเฟอเรนเชียลพื้นฐาน: อนุพันธ์ของ x (d/dx x) มีค่าเท่ากับข้อใด?", new String[]{"0", "1", "x", "2x"}, 1));
        quizBank.add(new QuizQuestion("อนุพันธ์ของค่าคงที่ c (d/dx c) มีค่าเท่ากับข้อใด?", new String[]{"0", "1", "c", "x"}, 0));
        quizBank.add(new QuizQuestion("อนุพันธ์ของ x² (d/dx x²) มีค่าเท่ากับข้อใด?", new String[]{"x", "2x", "x²", "2"}, 1));
        quizBank.add(new QuizQuestion("อนุพันธ์ของ x³ (d/dx x³) มีค่าเท่ากับข้อใด?", new String[]{"3x", "3x²", "x²", "3"}, 1));
        quizBank.add(new QuizQuestion("อนุพันธ์ของ sin(x) (d/dx sin(x)) คือข้อใด?", new String[]{"cos(x)", "-cos(x)", "tan(x)", "-sin(x)"}, 0));
        quizBank.add(new QuizQuestion("อนุพันธ์ของ cos(x) (d/dx cos(x)) คือข้อใด?", new String[]{"sin(x)", "-sin(x)", "-cos(x)", "sec(x)"}, 1));
        quizBank.add(new QuizQuestion("อนุพันธ์ของ e^x (d/dx e^x) คือข้อใด?", new String[]{"e^x", "x e^(x-1)", "1", "ln(x)"}, 0));
        quizBank.add(new QuizQuestion("อนุพันธ์ของ ln(x) (d/dx ln(x)) คือข้อใด?", new String[]{"1/x", "e^x", "1", "x"}, 0));
    }

    private class SeaEnemy {
        String name;
        String emoji;
        int hp = 10;
        int maxHp = 10;
        boolean isAlive = true;
        View containerView;
        TextView imgAvatar;
        ProgressBar barHp;
        TextView txtHp;
        float speed = 1.1f;
        long lastAttackTime = 0;
        float animTime = (float) (Math.random() * 10);

        SeaEnemy(String name, String emoji, float posX, float posY) {
            this.name = name;
            this.emoji = emoji;
            createView(posX, posY);
        }

        private void createView(float posX, float posY) {
            LinearLayout layout = new LinearLayout(BattleActivity.this);
            layout.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            ));
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER);

            TextView txtName = new TextView(BattleActivity.this);
            txtName.setText(name);
            txtName.setTextColor(Color.WHITE);
            txtName.setTextSize(11f);
            txtName.setGravity(Gravity.CENTER);

            barHp = new ProgressBar(BattleActivity.this, null, android.R.attr.progressBarStyleHorizontal);
            barHp.setMax(maxHp);
            barHp.setProgress(hp);
            barHp.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.RED));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(90, 14);
            barParams.bottomMargin = 2;
            barHp.setLayoutParams(barParams);

            txtHp = new TextView(BattleActivity.this);
            txtHp.setText(String.format(Locale.US, "%d/%d", hp, maxHp));
            txtHp.setTextColor(Color.YELLOW);
            txtHp.setTextSize(9f);
            txtHp.setGravity(Gravity.CENTER);

            imgAvatar = new TextView(BattleActivity.this);
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
            if (gameArea != null) {
                gameArea.addView(containerView);
            }
        }

        void updateAI(float targetX, float targetY) {
            if (!isAlive || containerView == null || !isGameRunning || isGamePaused) return;

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
                    damagePlayer(5);
                }
            }

            for (Object item : enemyList) {
                SeaEnemy other = (SeaEnemy) item;
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

        void takeDamage(int damage) {
            if (!isAlive || !isGameRunning || containerView == null) return;

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

                checkWinCondition();
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);

        currentHeroId = getIntent().getIntExtra("HERO_ID", 1);
        currentStageId = getIntent().getIntExtra("STAGE_ID", 1);
        playerHero = HeroFactory.createHero(currentHeroId);

        gameArea = findViewById(R.id.gameArea);
        playerContainer = findViewById(R.id.playerContainer);
        imgPlayer = findViewById(R.id.imgPlayer);
        imgProfile = findViewById(R.id.imgProfile);
        barPlayerHp = findViewById(R.id.barHp);
        stackProgressBar = findViewById(R.id.barStack);
        txtStackGauge = findViewById(R.id.txtStackCount);

        if (stackProgressBar != null) stackProgressBar.setMax(MAX_STACK);
        if (barPlayerHp != null) {
            barPlayerHp.setMax(maxPlayerHp);
            barPlayerHp.setProgress(playerHp);
        }

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        View joystickBase = findViewById(R.id.joystickBase);
        joystickKnob = findViewById(R.id.joystickKnob);

        if (joystickBase != null) {
            joystickBase.setOnTouchListener((v, event) -> {
                if (!isGameRunning || isGamePaused) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        joystickCenterX = joystickBase.getWidth() / 2f;
                        joystickCenterY = joystickBase.getHeight() / 2f;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float radius = joystickBase.getWidth() / 2f;
                        float dx = event.getX() - radius;
                        float dy = event.getY() - joystickBase.getHeight() / 2f;
                        float dist = (float) Math.hypot(dx, dy);
                        float clamped = Math.min(dist, radius);
                        if (dist > 0) {
                            if (joystickKnob != null) {
                                joystickKnob.setTranslationX(dx / dist * clamped);
                                joystickKnob.setTranslationY(dy / dist * clamped);
                            }
                            moveX = dx / dist * (clamped / radius);
                            moveY = dy / dist * (clamped / radius);
                            playerAngle = (float) Math.toDegrees(Math.atan2(moveY, moveX));
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        moveX = 0f;
                        moveY = 0f;
                        if (joystickKnob != null) {
                            joystickKnob.setTranslationX(0f);
                            joystickKnob.setTranslationY(0f);
                        }
                        return true;
                }
                return false;
            });
        }

        Button btnSkill1 = findViewById(R.id.btnSkill1);
        Button btnSkill2 = findViewById(R.id.btnSkill2);

        if (btnSkill1 != null) btnSkill1.setOnClickListener(v -> useSkill1BiteDash((Button) v));
        if (btnSkill2 != null) btnSkill2.setOnClickListener(v -> useSkill2Needle((Button) v));

        setupHeroAndSkills(currentHeroId);
        updateStackUI();

        if (gameArea != null) {
            gameArea.post(() -> {
                spawn5SeaEnemies();
                startGameLoop();
            });
        }
    }

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNs) {
            if (!isGameRunning) return;
            if (lastFrameNs == 0) lastFrameNs = frameTimeNs;
            float dt = Math.min((frameTimeNs - lastFrameNs) / 1_000_000_000f, 0.05f);
            lastFrameNs = frameTimeNs;

            if (!isGamePaused) {
                updateFish(dt);

                if (playerContainer != null) {
                    float pX = playerContainer.getX() + playerContainer.getTranslationX();
                    float pY = playerContainer.getY() + playerContainer.getTranslationY();
                    for (Object item : enemyList) {
                        SeaEnemy enemy = (SeaEnemy) item;
                        enemy.updateAI(pX, pY);
                    }
                }
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private void startGameLoop() {
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void updateFish(float dt) {
        if (playerContainer == null || imgPlayer == null) return;

        float k = 1f - (float) Math.exp(-8f * dt);
        float targetVx = skillLock ? 0f : moveX * MAX_SPEED;
        float targetVy = skillLock ? 0f : moveY * MAX_SPEED;
        velX += (targetVx - velX) * k;
        velY += (targetVy - velY) * k;

        if (!skillLock) {
            View parent = (View) playerContainer.getParent();
            if (parent != null && parent.getWidth() > 0 && parent.getHeight() > 0) {
                float minX = -playerContainer.getLeft();
                float maxX = parent.getWidth() - playerContainer.getRight();
                float minY = -playerContainer.getTop();
                float maxY = parent.getHeight() - playerContainer.getBottom();
                playerContainer.setTranslationX(Math.max(minX, Math.min(maxX, playerContainer.getTranslationX() + velX * dt)));
                playerContainer.setTranslationY(Math.max(minY, Math.min(maxY, playerContainer.getTranslationY() + velY * dt)));
            }
        }

        if (moveX > 0.15f) isFacingRight = true;
        else if (moveX < -0.15f) isFacingRight = false;

        // 🟢 กำหนด ID ของตัวละครที่รูปภาพต้นฉบับหันหน้าไปทาง "ขวา" อยู่แล้ว
        // (เช่น Electric Eel ID=3, Octopus ID=4, Shark ID=5)
        boolean isSpriteDefaultFacingRight = (currentHeroId == 3 || currentHeroId == 4 || currentHeroId == 5);

        float targetScaleX;
        if (isSpriteDefaultFacingRight) {
            targetScaleX = isFacingRight ? 1f : -1f;
        } else {
            targetScaleX = isFacingRight ? -1f : 1f;
        }

        faceScale += (targetScaleX - faceScale) * Math.min(1f, 15f * dt);

        float speed = Math.min(1f, (float) Math.hypot(velX, velY) / MAX_SPEED);
        swimTime += (2f + 8f * speed) * dt;

        float targetTilt = (velY / MAX_SPEED) * 20f * (isFacingRight ? 1f : -1f);
        tilt += (targetTilt - tilt) * Math.min(1f, 10f * dt);

        float wiggle = (float) Math.sin(swimTime * 2f) * (2f + 8f * speed);
        float squash = (float) Math.sin(swimTime * 4f) * 0.04f * (0.3f + speed);

        imgPlayer.setRotation(tilt + wiggle);
        imgPlayer.setScaleX(faceScale * (1f + squash));
        imgPlayer.setScaleY(1f - squash);
        imgPlayer.setTranslationY((float) Math.sin(swimTime) * 4f * (1f - speed));
    }

    private void spawn5SeaEnemies() {
        enemyList.clear();
        if (gameArea == null) return;

        float width = gameArea.getWidth() > 0 ? gameArea.getWidth() : 1000f;
        float height = gameArea.getHeight() > 0 ? gameArea.getHeight() : 500f;

        enemyList.add(new SeaEnemy("ปูซ่า", "🦀", width * 0.70f, height * 0.15f));
        enemyList.add(new SeaEnemy("แมงกะพรุน", "🪼", width * 0.85f, height * 0.35f));
        enemyList.add(new SeaEnemy("เต่าทะเล", "🐢", width * 0.75f, height * 0.55f));
        enemyList.add(new SeaEnemy("หมึกยักษ์", "🦑", width * 0.90f, height * 0.75f));
        enemyList.add(new SeaEnemy("ดาวทะเล", "⭐️", width * 0.65f, height * 0.85f));
    }

    private void startCooldownUI(Button btn, String originalText) {
        if (btn == null) return;
        btn.setEnabled(false);
        new CountDownTimer(500, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                btn.setText(String.format(Locale.US, "%.1f", millisUntilFinished / 1000.0f));
            }
            @Override
            public void onFinish() {
                btn.setText(originalText);
                btn.setEnabled(true);
            }
        }.start();
    }

    private void useSkill1BiteDash(Button button) {
        if (isGamePaused || gameArea == null || playerContainer == null) return;
        startCooldownUI(button, "Skill 1");
        animateButton(button);

        skillLock = true;

        float rad = (float) Math.toRadians(playerAngle);
        float dashDist = 300f;

        float startX = playerContainer.getX() + playerContainer.getTranslationX();
        float startY = playerContainer.getY() + playerContainer.getTranslationY();

        float targetX = startX + (float) Math.cos(rad) * dashDist;
        float targetY = startY + (float) Math.sin(rad) * dashDist;

        if (gameArea.getWidth() > 0 && gameArea.getHeight() > 0) {
            targetX = Math.max(0, Math.min(gameArea.getWidth() - playerContainer.getWidth(), targetX));
            targetY = Math.max(0, Math.min(gameArea.getHeight() - playerContainer.getHeight(), targetY));
        }

        ValueAnimator dashAnimator = ValueAnimator.ofFloat(0f, 1f);
        dashAnimator.setDuration(150);
        dashAnimator.setInterpolator(new DecelerateInterpolator());

        List hitEnemies = new ArrayList();
        final float finalTargetX = targetX;
        final float finalTargetY = targetY;

        dashAnimator.addUpdateListener(animation -> {
            if (playerContainer == null) return;
            float progress = (float) animation.getAnimatedValue();

            playerContainer.setTranslationX((finalTargetX - startX) * progress + (startX - playerContainer.getX()));
            playerContainer.setTranslationY((finalTargetY - startY) * progress + (startY - playerContainer.getY()));

            for (Object item : enemyList) {
                SeaEnemy enemy = (SeaEnemy) item;
                if (enemy.isAlive && enemy.containerView != null && !hitEnemies.contains(enemy) && isColliding(playerContainer, enemy.containerView)) {
                    hitEnemies.add(enemy);
                    enemy.takeDamage(2);
                    onHitEnemySuccess();
                }
            }
        });

        dashAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                skillLock = false;
            }
        });

        dashAnimator.start();
    }

    private void useSkill2Needle(Button button) {
        if (isGamePaused) return;
        startCooldownUI(button, "Skill 2");
        animateButton(button);
        spawnProjectile(android.R.drawable.ic_menu_compass, 550, 1100f);
    }

    private void spawnProjectile(int iconResId, long duration, float distance) {
        if (gameArea == null || playerContainer == null) return;

        ImageView projectile = new ImageView(this);
        projectile.setImageResource(iconResId);
        projectile.setLayoutParams(new FrameLayout.LayoutParams(55, 55));

        float playerX = playerContainer.getX() + playerContainer.getTranslationX() + (playerContainer.getWidth() / 2f);
        float playerY = playerContainer.getY() + playerContainer.getTranslationY() + (playerContainer.getHeight() / 2f);

        float rad = (float) Math.toRadians(playerAngle);
        float startX = playerX + (float) Math.cos(rad) * 40f - 27.5f;
        float startY = playerY + (float) Math.sin(rad) * 40f - 27.5f;

        projectile.setX(startX);
        projectile.setY(startY);
        gameArea.addView(projectile);

        float targetX = startX + (float) Math.cos(rad) * distance;
        float targetY = startY + (float) Math.sin(rad) * distance;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new LinearInterpolator());

        final boolean[] hasHit = {false};

        animator.addUpdateListener(animation -> {
            if (hasHit[0]) return;

            float fraction = animation.getAnimatedFraction();
            float currentX = startX + (targetX - startX) * fraction;
            float currentY = startY + (targetY - startY) * fraction;

            projectile.setX(currentX);
            projectile.setY(currentY);
            projectile.setRotation(projectile.getRotation() + 18f);

            for (Object item : enemyList) {
                SeaEnemy enemy = (SeaEnemy) item;
                if (enemy.isAlive && enemy.containerView != null && isColliding(projectile, enemy.containerView)) {
                    hasHit[0] = true;
                    animator.cancel();
                    gameArea.removeView(projectile);

                    enemy.takeDamage(1);
                    onHitEnemySuccess();
                    break;
                }
            }
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!hasHit[0] && gameArea != null) {
                    gameArea.removeView(projectile);
                }
            }
        });

        animator.start();
    }

    private boolean isColliding(View v1, View v2) {
        if (v1 == null || v2 == null) return false;
        Rect r1 = new Rect();
        v1.getGlobalVisibleRect(r1);
        Rect r2 = new Rect();
        v2.getGlobalVisibleRect(r2);
        return Rect.intersects(r1, r2);
    }

    private void onHitEnemySuccess() {
        if (currentStack < MAX_STACK) {
            currentStack++;
            updateStackUI();
            if (currentStack >= MAX_STACK) showQuizDialog();
        }
    }

    private void damagePlayer(int damage) {
        if (!isGameRunning) return;

        playerHp = Math.max(0, playerHp - damage);
        if (barPlayerHp != null) barPlayerHp.setProgress(playerHp);

        if (playerContainer != null) {
            playerContainer.setAlpha(0.5f);
            playerContainer.postDelayed(() -> {
                if (playerContainer != null) playerContainer.setAlpha(1.0f);
            }, 100);
        }

        if (playerHp <= 0) {
            isGameRunning = false;
            new AlertDialog.Builder(this)
                    .setTitle("Game Over")
                    .setMessage("คุณตายแล้วเริ่มต้นใหม่")
                    .setCancelable(false)
                    .setPositiveButton("OK", (dialog, which) -> recreate())
                    .show();
        }
    }

    private void checkWinCondition() {
        boolean allDead = true;
        for (Object item : enemyList) {
            SeaEnemy enemy = (SeaEnemy) item;
            if (enemy.isAlive) {
                allDead = false;
                break;
            }
        }

        if (allDead && isGameRunning) {
            isGameRunning = false;

            SharedPreferences prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE);
            int currentUnlocked = prefs.getInt("unlocked_stage", 1);

            if (currentStageId >= currentUnlocked) {
                prefs.edit().putInt("unlocked_stage", currentStageId + 1).apply();
            }

            new AlertDialog.Builder(this)
                    .setTitle("Victory!")
                    .setMessage("ชนะไปด่านถัดไป")
                    .setCancelable(false)
                    .setPositiveButton("OK", (dialog, which) -> {
                        Intent intent = new Intent(BattleActivity.this, SelectStageActivity.class);
                        intent.putExtra("HERO_ID", currentHeroId);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    })
                    .show();
        }
    }

    private void showQuizDialog() {
        if (quizBank.isEmpty()) initQuizBank();

        isGamePaused = true;

        int randomIndex;
        do {
            randomIndex = (int) (Math.random() * quizBank.size());
        } while (quizBank.size() > 1 && randomIndex == lastQuestionIndex);
        lastQuestionIndex = randomIndex;

        QuizQuestion q = (QuizQuestion) quizBank.get(randomIndex);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(String.format(Locale.US, "Calculus Quiz (เวลา 05:00)\n%s", q.question));
        builder.setCancelable(false);
        builder.setItems(q.options, (dialog, which) -> {
            if (quizTimer != null) quizTimer.cancel();

            if (which == q.correctIndex) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    isGamePaused = false;
                    executeUltimateThunderBreathing();
                }, 600);
            } else {
                isGamePaused = false;
                currentStack = 0;
                updateStackUI();
            }
        });

        quizDialog = builder.create();
        quizDialog.show();

        if (quizTimer != null) quizTimer.cancel();
        quizTimer = new CountDownTimer(300000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = (millisUntilFinished / 1000) / 60;
                long seconds = (millisUntilFinished / 1000) % 60;
                if (quizDialog != null && quizDialog.isShowing()) {
                    quizDialog.setTitle(String.format(Locale.US, "Calculus Quiz (%02d:%02d)\n%s", minutes, seconds, q.question));
                }
            }

            @Override
            public void onFinish() {
                if (quizDialog != null && quizDialog.isShowing()) {
                    quizDialog.dismiss();
                }
                isGamePaused = false;
                currentStack = 0;
                updateStackUI();
            }
        }.start();
    }

    private void executeUltimateThunderBreathing() {
        if (playerContainer == null || gameArea == null) return;

        skillLock = true;

        View flashView = new View(this);
        flashView.setBackgroundColor(Color.parseColor("#66FFFF00"));
        flashView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        gameArea.addView(flashView);
        flashView.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            if (gameArea != null) gameArea.removeView(flashView);
        }).start();

        List targets = new ArrayList();
        for (Object item : enemyList) {
            SeaEnemy e = (SeaEnemy) item;
            if (e.isAlive) targets.add(e);
        }

        if (targets.isEmpty()) {
            skillLock = false;
            currentStack = 0;
            updateStackUI();
            return;
        }

        float originalX = playerContainer.getTranslationX();
        float originalY = playerContainer.getTranslationY();

        playerContainer.animate().scaleX(1.3f).scaleY(1.3f).setDuration(300).withEndAction(() ->
                dashToNextTarget(targets, 0, originalX, originalY)
        ).start();
    }

    private void dashToNextTarget(List targets, int index, float startTransX, float startTransY) {
        if (playerContainer == null) return;

        if (index >= targets.size()) {
            playerContainer.animate().translationX(startTransX).translationY(startTransY)
                    .scaleX(1f).scaleY(1f).setDuration(350).withEndAction(() -> {
                        skillLock = false;
                        currentStack = 0;
                        updateStackUI();
                    }).start();
            return;
        }

        SeaEnemy target = (SeaEnemy) targets.get(index);

        if (!target.isAlive || target.containerView == null) {
            dashToNextTarget(targets, index + 1, startTransX, startTransY);
            return;
        }

        float targetX = target.containerView.getX();
        float targetY = target.containerView.getY();

        LightningEffectView lightning = new LightningEffectView(this,
                playerContainer.getX() + playerContainer.getTranslationX() + playerContainer.getWidth() / 2f,
                playerContainer.getY() + playerContainer.getTranslationY() + playerContainer.getHeight() / 2f,
                targetX + target.containerView.getWidth() / 2f,
                targetY + target.containerView.getHeight() / 2f);
        if (gameArea != null) gameArea.addView(lightning);

        playerContainer.animate()
                .translationX(targetX - playerContainer.getX())
                .translationY(targetY - playerContainer.getY())
                .setDuration(350)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    target.takeDamage(5);

                    if (target.containerView != null) {
                        target.containerView.animate().translationYBy(-20f).setDuration(120)
                                .withEndAction(() -> {
                                    if (target.containerView != null) target.containerView.animate().translationYBy(20f).setDuration(120).start();
                                }).start();
                    }

                    if (gameArea != null) gameArea.removeView(lightning);

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        dashToNextTarget(targets, index + 1, startTransX, startTransY);
                    }, 100);
                }).start();
    }

    @SuppressLint("SetTextI18n")
    private void updateStackUI() {
        if (stackProgressBar != null) stackProgressBar.setProgress(currentStack);
        if (txtStackGauge != null && playerHero != null) {
            txtStackGauge.setText(playerHero.getName() + " | Stack: " + currentStack + "/" + MAX_STACK);
        }
    }

    private void setupHeroAndSkills(int heroId) {
        int heroDrawableId = R.drawable.hero_1;
        switch (heroId) {
            case 2: heroDrawableId = R.drawable.hero_2; break;
            case 3: heroDrawableId = R.drawable.hero_3; break;
            case 4: heroDrawableId = R.drawable.hero_4; break;
            case 5: heroDrawableId = R.drawable.hero_5; break;
        }
        if (imgPlayer != null) imgPlayer.setImageResource(heroDrawableId);
        if (imgProfile != null) imgProfile.setImageResource(heroDrawableId);
    }

    private void animateButton(View button) {
        if (button == null) return;
        button.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction(() ->
                button.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        ).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isGameRunning = false;

        Choreographer.getInstance().removeFrameCallback(frameCallback);

        if (quizTimer != null) {
            quizTimer.cancel();
        }
        if (quizDialog != null && quizDialog.isShowing()) {
            quizDialog.dismiss();
        }
    }

    private static class LightningEffectView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public LightningEffectView(Context context, float sx, float sy, float ex, float ey) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(12f);
            paint.setColor(Color.parseColor("#FFF59D"));
            paint.setShadowLayer(25f, 0f, 0f, Color.parseColor("#00E5FF"));

            path.moveTo(sx, sy);
            int steps = 4;
            float dx = (ex - sx) / steps;
            float dy = (ey - sy) / steps;

            for (int i = 1; i < steps; i++) {
                float px = sx + (dx * i) + (float) (Math.random() * 80 - 40);
                float py = sy + (dy * i) + (float) (Math.random() * 80 - 40);
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