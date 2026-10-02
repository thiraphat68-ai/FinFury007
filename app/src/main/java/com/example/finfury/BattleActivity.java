package com.example.finfury;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BattleActivity extends AppCompatActivity implements BattleContext {

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
    static final float MAX_SPEED = 700f;
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

    private final List<SeaEnemy> enemyList = new ArrayList<>();

    // --- Quiz System (แยกไปอยู่ใน QuizManager.java) ---
    private QuizManager quizManager;

    // --- Ultimate Button (ใช้กับฮีโร่ที่ usesUltimateButton() = true) ---
    private Button btnUltimate;
    private boolean ultimateReady = false;   // ตอบ quiz ถูกแล้ว รอผู้เล่นกด
    private boolean ultimateRunning = false; // กำลังปล่อย Ultimate อยู่

    // =========================================================
    // BattleContext: สิ่งที่สกิลของฮีโร่เรียกใช้ได้
    // =========================================================
    @Override
    public Context getContext() { return this; }

    @Override
    public FrameLayout getGameArea() { return gameArea; }

    @Override
    public View getPlayerContainer() { return playerContainer; }

    @Override
    public float getPlayerAngle() { return playerAngle; }

    @Override
    public void setSkillLock(boolean locked) { skillLock = locked; }

    @Override
    public boolean isGameRunning() { return isGameRunning; }

    @Override
    public boolean isGamePaused() { return isGamePaused; }

    @Override
    public List<SeaEnemy> getEnemies() { return enemyList; }

    @Override
    public void onEnemyDefeated() { checkWinCondition(); }

    @Override
    public void onUltimateFinished() {
        ultimateRunning = false;
        currentStack = 0;
        updateStackUI();
        updateUltimateButton();
    }

    // =========================================================

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);

        currentHeroId = getIntent().getIntExtra("HERO_ID", 1);
        currentStageId = getIntent().getIntExtra("STAGE_ID", 1);
        playerHero = HeroFactory.createHero(currentHeroId);
        setupQuiz();

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
                // ปล่อยนิ้วต้องรีเซ็ตเสมอ แม้เกมหยุดอยู่ (เช่น quiz ขึ้น) ไม่งั้นปลาวิ่งค้างหลังกลับมาเล่น
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    resetJoystick();
                    return true;
                }
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
                }
                return false;
            });
        }

        // ปุ่มสกิล: ดูแลแค่คูลดาวน์/แอนิเมชันปุ่ม แล้วส่งต่อให้ฮีโร่เป็นคนทำสกิล
        Button btnSkill1 = findViewById(R.id.btnSkill1);
        Button btnSkill2 = findViewById(R.id.btnSkill2);

        if (btnSkill1 != null) {
            btnSkill1.setOnClickListener(v -> {
                if (!canUseSkill()) return;
                startCooldownUI(btnSkill1, "Skill 1");
                animateButton(btnSkill1);
                playerHero.useSkill1(this);
            });
        }
        if (btnSkill2 != null) {
            btnSkill2.setOnClickListener(v -> {
                if (!canUseSkill()) return;
                startCooldownUI(btnSkill2, "Skill 2");
                animateButton(btnSkill2);
                playerHero.useSkill2(this);
            });
        }

        // ปุ่ม Ultimate: โชว์เฉพาะฮีโร่ที่ใช้ปุ่มแยก
        btnUltimate = findViewById(R.id.btnUltimate);
        if (btnUltimate != null) {
            btnUltimate.setVisibility(playerHero.usesUltimateButton() ? View.VISIBLE : View.GONE);
            btnUltimate.setOnClickListener(v -> {
                if (!ultimateReady || ultimateRunning || isGamePaused || !isGameRunning) return;
                ultimateReady = false;
                ultimateRunning = true;
                updateUltimateButton();
                animateButton(btnUltimate);
                playerHero.executeUltimateSkill(this);
            });
        }

        setupHeroAndSkills(currentHeroId);
        updateStackUI();
        updateUltimateButton();

        if (gameArea != null) {
            gameArea.post(() -> {
                spawn5SeaEnemies();
                startGameLoop();
            });
        }
    }

    private void resetJoystick() {
        moveX = 0f;
        moveY = 0f;
        if (joystickKnob != null) {
            joystickKnob.setTranslationX(0f);
            joystickKnob.setTranslationY(0f);
        }
    }

    /** สกิลใช้ได้เฉพาะตอนเกมเดินอยู่ ไม่หยุด/ตาย และไม่ได้กำลังปล่อย Ultimate (กันล็อกการเคลื่อนที่หลุด) */
    private boolean canUseSkill() {
        return isGameRunning && !isGamePaused && !ultimateRunning && gameArea != null && playerContainer != null;
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
                    // getX()/getY() รวม translation ไว้แล้ว ห้ามบวก getTranslationX/Y ซ้ำ
                    // (ของเดิมบวกซ้ำ ศัตรูเลยวิ่งไปตีจุดว่างๆ แล้ว HP เราลดทั้งที่ไม่มีใครอยู่ใกล้)
                    float pX = playerContainer.getX();
                    float pY = playerContainer.getY();
                    for (SeaEnemy enemy : enemyList) {
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
        SeaEnemy.resetAttackQueue();
        if (gameArea == null) return;

        float width = gameArea.getWidth() > 0 ? gameArea.getWidth() : 1000f;
        float height = gameArea.getHeight() > 0 ? gameArea.getHeight() : 500f;

        enemyList.add(new SeaEnemy(this, "ปูซ่า", "🦀", width * 0.70f, height * 0.15f));
        enemyList.add(new SeaEnemy(this, "แมงกะพรุน", "🪼", width * 0.85f, height * 0.35f));
        enemyList.add(new SeaEnemy(this, "เต่าทะเล", "🐢", width * 0.95f, height * 0.55f));
        enemyList.add(new SeaEnemy(this, "หมึกยักษ์", "🦑", width * 0.90f, height * 0.75f));
        enemyList.add(new SeaEnemy(this, "ดาวทะเล", "⭐️", width * 0.65f, height * 0.85f));
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

    @Override
    public void onHitEnemySuccess() {
        if (currentStack < MAX_STACK) {
            currentStack++;
            updateStackUI();
            if (currentStack >= MAX_STACK) quizManager.show();
        }
    }

    @Override
    public void damagePlayer(int damage) {
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
        for (SeaEnemy enemy : enemyList) {
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

    private void setupQuiz() {
        quizManager = new QuizManager(this, "Calculus", QuizManager.calculusQuestions(),
                new QuizManager.Listener() {
                    @Override
                    public void onQuizShown() {
                        isGamePaused = true;
                        resetJoystick();
                    }

                    @Override
                    public void onCorrect() {
                        if (playerHero.usesUltimateButton()) {
                            // ตอบถูก -> แค่ปลดล็อกปุ่ม ULT ให้ผู้เล่นกดเองตอนไหนก็ได้
                            isGamePaused = false;
                            ultimateReady = true;
                            updateUltimateButton();
                            return;
                        }
                        // ฮีโร่อื่น: ตอบถูก -> ปล่อย Ultimate อัตโนมัติ (แบบเดิม)
                        ultimateRunning = true;
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            isGamePaused = false;
                            if (!isGameRunning || isFinishing()) return;
                            playerHero.executeUltimateSkill(BattleActivity.this);
                        }, 600);
                    }

                    @Override
                    public void onWrong() {
                        resetStackAfterQuiz();
                    }

                    @Override
                    public void onTimeout() {
                        resetStackAfterQuiz();
                    }
                });
    }

    private void resetStackAfterQuiz() {
        isGamePaused = false;
        currentStack = 0;
        updateStackUI();
    }

    @SuppressLint("SetTextI18n")
    private void updateStackUI() {
        if (stackProgressBar != null) stackProgressBar.setProgress(currentStack);
        if (txtStackGauge != null && playerHero != null) {
            txtStackGauge.setText(playerHero.getName() + " | Stack: " + currentStack + "/" + MAX_STACK);
        }
    }

    /** ล็อก = จาง + กดไม่ได้, พร้อม = สว่าง + เด้งเตือน */
    @SuppressLint("SetTextI18n")
    private void updateUltimateButton() {
        if (btnUltimate == null) return;
        btnUltimate.animate().cancel();
        btnUltimate.setScaleX(1f);
        btnUltimate.setScaleY(1f);

        if (ultimateReady) {
            btnUltimate.setEnabled(true);
            btnUltimate.setAlpha(1f);
            btnUltimate.setText("ULT!");
            btnUltimate.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150)
                    .withEndAction(() -> btnUltimate.animate().scaleX(1f).scaleY(1f).setDuration(150).start())
                    .start();
        } else {
            btnUltimate.setEnabled(false);
            btnUltimate.setAlpha(0.4f);
            btnUltimate.setText("🔒 ULT");
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
        TextView txtPlayerName = findViewById(R.id.txtPlayerName);
        if (txtPlayerName != null && playerHero != null) txtPlayerName.setText(playerHero.getName());
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
        SeaEnemy.resetAttackQueue();

        Choreographer.getInstance().removeFrameCallback(frameCallback);

        if (quizManager != null) quizManager.destroy();
    }
}