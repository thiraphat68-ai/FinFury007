package com.example.finfury;

import android.annotation.SuppressLint;
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
import androidx.activity.OnBackPressedCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BattleActivity extends BaseActivity implements BattleContext, ItemManager.Host {

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
    private float slowFactor = 1f;               // สโลว์จากบอส (คูณกับ speedMultiplier)
    private float slowRemainingMs = 0f;
    private float stunRemainingMs = 0f;          // สตันจากศัตรู: เคลื่อนที่ไม่ได้
    private float stunImmuneMs = 0f;             // หายสตันแล้วกันสตันซ้ำชั่วครู่ ไม่งั้นโดนสตันต่อเนื่องจนขยับไม่ได้เลย
    private static final float STUN_IMMUNE_AFTER_MS = 1500f;
    private static final long HIT_INVULN_MS = 350;
    private long lastDamageMs = 0;
    private float speedMultiplier = 1f;     // สกิลที่เพิ่มความเร็ว (เช่น Blood Frenzy)
    private float cooldownMultiplier = 1f;   // สกิลที่ลดคูลดาวน์
    static final float MAX_SPEED = 700f;
    private boolean isFacingRight = true;

    private Hero playerHero;
    private int currentHeroId = 1;
    private int currentStageId = 1;
    private StageConfig stageConfig = StageConfig.forStage(1);
    private int playerHp = 100;
    private int maxPlayerHp = 100;   // ตั้งตามฮีโร่ตอน onCreate
    private int currentStack = 0;
    private int maxStack = 10;       // ตั้งตามฮีโร่ตอน onCreate
    private float heroBaseSpeed = 1f;
    private ItemManager itemManager;
    private TextView txtBuffs;
    private static final int MAX_STAGES = 5;

    // ดีบัฟลดพลัง Ultimate (ด่าน 3-4): ถูกตีครบทุก 2 ครั้ง ได้พลังต่อฮิต 70% นาน 8 วินาที
    private static final float ULT_DEBUFF_DURATION_MS = 5000f;
    // ด่าน 3+: ถูกตีทุก 3 ครั้ง พลังสะสม ULT ลด 20% ของหลอด (2 จาก 10)
    private static final int ENERGY_DRAIN_FROM_STAGE = 3;
    private static final int ENERGY_DRAIN_EVERY_HITS = 3;
    private static final int ENERGY_DRAIN_PERCENT = 20;
    private int hitsTaken = 0;
    private float ultDebuffRemainingMs = 0f;
    private float stackFraction = 0f;

    private float playerAngle = 0f;

    private boolean isGameRunning = true;
    private boolean isGamePaused = false;

    private final List<SeaEnemy> enemyList = new ArrayList<>();

    // --- Quiz System (แยกไปอยู่ใน QuizManager.java) ---
    private QuizManager quizManager;

    // --- Overlay ต่างๆ (อยู่ใน activity_battle.xml) ---
    private View overlayPause;
    private View overlayResult;
    private String skill1Label = "Skill 1";
    private String skill2Label = "Skill 2";

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
    public float getPlayerSpeedRatio() {
        return Math.min(1f, (float) Math.hypot(velX, velY) / MAX_SPEED);
    }

    @Override
    public void setSpeedMultiplier(float multiplier) { speedMultiplier = multiplier; }

    @Override
    public void knockbackPlayer(float dirX, float dirY, float speed) {
        velX += dirX * speed;   // updateFish ดึงความเร็วกลับเข้าหาเป้าหมายเอง แรงจึงค่อยๆ หมด (ระยะประมาณ 250 px)
        velY += dirY * speed;
    }

    @Override
    public void stunPlayer(long durationMs) {
        if (stunImmuneMs > 0f) return;
        stunRemainingMs = Math.max(stunRemainingMs, durationMs);
    }

    @Override
    public void slowPlayer(float factor, long durationMs) {
        slowFactor = factor;
        slowRemainingMs = durationMs;
    }

    @Override
    public void setCooldownMultiplier(float multiplier) { cooldownMultiplier = multiplier; }

    // --- หลอดเวลา Ultimate / โบนัสดาเมจ / หลอดชาร์จ (HUD ที่สกิลเรียกใช้) ---
    private View layoutUltTimer;
    private ProgressBar barUltTime;
    private TextView txtUltTimer;
    private float ultTimerRemainingMs = 0f;
    private long ultTimerTotalMs = 1;
    private TextView txtBonusDamage;
    private ProgressBar barPlayerCharge;

    @Override
    public void showUltimateDuration(long durationMs) {
        ultTimerTotalMs = Math.max(1, durationMs);
        ultTimerRemainingMs = durationMs;
        lastUltTenths = -1;
        lastUltBarProgress = -1;
        updateUltimateTimerUI();
        layoutUltTimer.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideUltimateDuration() {
        ultTimerRemainingMs = 0f;
        layoutUltTimer.setVisibility(View.GONE);
    }

    private void tickUltimateTimer(float dtSec) {
        if (ultTimerRemainingMs <= 0f) return;
        ultTimerRemainingMs -= dtSec * 1000f;
        if (ultTimerRemainingMs <= 0f) {
            hideUltimateDuration();
        } else {
            updateUltimateTimerUI();
        }
    }

    // ข้อความ/หลอดอัปเดตเฉพาะตอนค่าเปลี่ยนจริง (เศษวินาที 0.1 s หรือหลอดขยับ 1/1000) ไม่ format ทุกเฟรม
    private int lastUltTenths = -1;
    private int lastUltBarProgress = -1;
    private final StringBuilder ultTimerText = new StringBuilder(32);

    private void updateUltimateTimerUI() {
        int progress = (int) (ultTimerRemainingMs * 1000f / ultTimerTotalMs);
        if (progress != lastUltBarProgress) {
            lastUltBarProgress = progress;
            barUltTime.setProgress(progress);
        }
        int tenths = (int) (Math.max(0f, ultTimerRemainingMs) / 100f);
        if (tenths != lastUltTenths) {
            lastUltTenths = tenths;
            ultTimerText.setLength(0);
            ultTimerText.append(playerHero.getUltimateName()).append(' ')
                    .append(tenths / 10).append('.').append(tenths % 10).append('s');
            txtUltTimer.setText(ultTimerText);
        }
    }

    @Override
    public void setPlayerBonusDamage(int bonus) {
        txtBonusDamage.setText("+" + bonus);
        txtBonusDamage.setVisibility(View.VISIBLE);
        updatePlayerOverlays();
    }

    @Override
    public void clearPlayerBonusDamage() {
        txtBonusDamage.setVisibility(View.GONE);
    }

    @Override
    public void setPlayerChargeProgress(float progress) {
        barPlayerCharge.setProgress((int) (Math.max(0f, Math.min(1f, progress)) * 1000));
        barPlayerCharge.setVisibility(View.VISIBLE);
        updatePlayerOverlays();
    }

    @Override
    public void hidePlayerChargeBar() {
        barPlayerCharge.setVisibility(View.GONE);
    }

    /** ย้ายตัวเลขโบนัส/หลอดชาร์จให้อยู่เหนือหัวผู้เล่นเสมอ */
    private void updatePlayerOverlays() {
        if (playerContainer == null) return;
        float cx = playerContainer.getX() + playerContainer.getWidth() / 2f;
        float top = playerContainer.getY();
        if (txtBonusDamage.getVisibility() == View.VISIBLE) {
            txtBonusDamage.setX(cx - txtBonusDamage.getWidth() / 2f);
            txtBonusDamage.setY(top - txtBonusDamage.getHeight() - 4f);
        }
        if (barPlayerCharge.getVisibility() == View.VISIBLE) {
            barPlayerCharge.setX(cx - barPlayerCharge.getWidth() / 2f);
            barPlayerCharge.setY(top - barPlayerCharge.getHeight() - 8f);
        }
    }

    @Override
    public boolean isGameRunning() { return isGameRunning; }

    @Override
    public boolean isGamePaused() { return isGamePaused; }

    @Override
    public List<SeaEnemy> getEnemies() { return enemyList; }

    @Override
    public void onEnemyDefeated() {
        updateStageInfo();
        checkWinCondition();
    }

    @Override
    public void onEnemyLifeLost() {
        // บอสเข้าเฟส 2: ลูกน้องที่เหลือหายไปหมด เหลือแค่บอสกับผู้เล่น
        if (stageConfig.boss) {
            for (int i = 0; i < enemyList.size(); i++) {
                SeaEnemy e = enemyList.get(i);
                if (e instanceof BossMinion) e.removeSilently();
            }
        }
        // บอสเสียชีวิต: ดรอปหัวใจให้ที่ตัวบอส
        for (int i = 0; i < enemyList.size(); i++) {
            SeaEnemy e = enemyList.get(i);
            if (e instanceof KrakenBoss && e.containerView != null) {
                View v = e.containerView;
                dropItemAt(v.getX() + v.getWidth() / 2f, v.getY() + v.getHeight() / 2f, true);
                break;
            }
        }
        updateStageInfo();
    }

    @Override
    public void dropItemAt(float cx, float cy, boolean forceHeart) {
        if (itemManager != null && isGameRunning) itemManager.onEnemyDefeated(cx, cy, forceHeart);
    }

    // ---------- ItemManager.Host ----------
    @Override public int getPlayerHp() { return playerHp; }
    @Override public int getPlayerMaxHp() { return maxPlayerHp; }

    @Override
    public void healPlayer(int amount) {
        playerHp = Math.min(maxPlayerHp, playerHp + amount);
        if (barPlayerHp != null) barPlayerHp.setProgress(playerHp);
        updateHpUI();
    }

    @Override
    public void addStack(int amount) {
        if (!isGameRunning || currentStack >= maxStack) return;
        currentStack = Math.min(maxStack, currentStack + amount);
        updateStackUI();
        if (currentStack >= maxStack) quizManager.show();
    }

    @Override
    public void freezeEnemies(float factor, long durationMs) {
        for (int i = 0; i < enemyList.size(); i++) {
            SeaEnemy e = enemyList.get(i);
            if (e.isAlive) e.applySlow(factor, durationMs);
        }
    }

    private void updateItems(float dt) {
        if (itemManager == null) return;
        itemManager.update(dt);
        SeaEnemy.playerDamageBonus = itemManager.damageBonus();
        if (txtBuffs != null && itemManager.buffTextChanged()) {
            String text = itemManager.buffText();
            txtBuffs.setText(text);
            txtBuffs.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

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
        SoundManager.init(this);
        setContentView(R.layout.activity_battle);

        currentHeroId = getIntent().getIntExtra("HERO_ID", 1);
        currentStageId = getIntent().getIntExtra("STAGE_ID", 1);
        stageConfig = StageConfig.forStage(currentStageId);
        playerHero = HeroFactory.createHero(currentHeroId);
        maxPlayerHp = playerHero.getMaxHp();
        playerHp = maxPlayerHp;
        maxStack = playerHero.getStackNeeded();
        heroBaseSpeed = playerHero.getBaseSpeedMultiplier();
        setupQuiz();
        setupStageBackground(currentStageId);

        gameArea = findViewById(R.id.gameArea);
        playerContainer = findViewById(R.id.playerContainer);
        imgPlayer = findViewById(R.id.imgPlayer);
        imgProfile = findViewById(R.id.imgProfile);
        barPlayerHp = findViewById(R.id.barHp);
        txtBuffs = findViewById(R.id.txtBuffs);
        SeaEnemy.playerDamageBonus = 0;
        stackProgressBar = findViewById(R.id.barStack);
        txtStackGauge = findViewById(R.id.txtStackCount);

        if (stackProgressBar != null) stackProgressBar.setMax(maxStack);
        if (barPlayerHp != null) {
            barPlayerHp.setMax(maxPlayerHp);
            barPlayerHp.setProgress(playerHp);
        }
        updateHpUI();

        // ปุ่มมุมขวาบนเป็นปุ่มหยุดเกม (เปิดเมนู เล่นต่อ / เริ่มใหม่ / ออก) แทนการออกทันที
        ImageButton btnPause = findViewById(R.id.btnBack);
        if (btnPause != null) btnPause.setOnClickListener(v -> openPauseMenu());
        setupOverlays();
        // ประตูกันโจทย์ที่ตัวโจทย์เอง: ทุกทางที่เรียก show() ถูกตรวจว่าด่านยังเล่นอยู่และไม่มี overlay อื่น
        quizManager.setGate(this::canOpenQuiz);

        layoutUltTimer = findViewById(R.id.layoutUltTimer);
        barUltTime = findViewById(R.id.barUltTime);
        txtUltTimer = findViewById(R.id.txtUltTimer);
        txtBonusDamage = findViewById(R.id.txtBonusDamage);
        barPlayerCharge = findViewById(R.id.barPlayerCharge);

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

        // ชื่อ + ไอคอนสกิลตามฮีโร่ที่เลือก (ใช้ซ้ำตอนคืนข้อความหลังคูลดาวน์)
        skill1Label = playerHero.getSkill1Icon() + "\n" + playerHero.getSkill1Name();
        skill2Label = playerHero.getSkill2Icon() + "\n" + playerHero.getSkill2Name();
        if (btnSkill1 != null) btnSkill1.setText(skill1Label);
        if (btnSkill2 != null) btnSkill2.setText(skill2Label);

        if (btnSkill1 != null) {
            btnSkill1.setOnClickListener(v -> {
                if (!canUseSkill()) return;
                SoundManager.play(SoundManager.Sfx.SKILL1);
                startCooldownUI(btnSkill1, skill1Label, playerHero.getSkill1CooldownMs());
                animateButton(btnSkill1);
                playerHero.useSkill1(this);
            });
        }
        if (btnSkill2 != null) {
            btnSkill2.setOnClickListener(v -> {
                if (!canUseSkill()) return;
                SoundManager.play(SoundManager.Sfx.SKILL2);
                startCooldownUI(btnSkill2, skill2Label, playerHero.getSkill2CooldownMs());
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
                SoundManager.play(SoundManager.Sfx.ULTIMATE);
                playerHero.executeUltimateSkill(this);
            });
        }

        setupHeroAndSkills(currentHeroId);
        updateStackUI();
        updateUltimateButton();

        if (gameArea != null) {
            gameArea.post(() -> {
                itemManager = new ItemManager(this, gameArea, playerContainer, this, true);
                spawnStageEnemies();
                updateStageInfo();
                startGameLoop();
                showTutorialIfFirstTime();
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
                updateItems(dt);
                updatePlayerOverlays();
                tickUltimateTimer(dt);
                if (ultDebuffRemainingMs > 0f) {
                    ultDebuffRemainingMs -= dt * 1000f;
                    if (ultDebuffRemainingMs <= 0f) {
                        ultDebuffRemainingMs = 0f;
                        updateStageInfo();
                    }
                }

                if (playerContainer != null) {
                    // getX()/getY() รวม translation ไว้แล้ว ห้ามบวก getTranslationX/Y ซ้ำ
                    // (ของเดิมบวกซ้ำ ศัตรูเลยวิ่งไปตีจุดว่างๆ แล้ว HP เราลดทั้งที่ไม่มีใครอยู่ใกล้)
                    float pX = playerContainer.getX();
                    float pY = playerContainer.getY();
                    for (int enemyIdx = 0; enemyIdx < enemyList.size(); enemyIdx++) {
                        SeaEnemy enemy = enemyList.get(enemyIdx);
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
        if (slowRemainingMs > 0f) {
            slowRemainingMs -= dt * 1000f;
            if (slowRemainingMs <= 0f) { slowRemainingMs = 0f; slowFactor = 1f; }
        }
        boolean stunned = stunRemainingMs > 0f;
        if (stunned) {
            stunRemainingMs = Math.max(0f, stunRemainingMs - dt * 1000f);
            if (stunRemainingMs == 0f) stunImmuneMs = STUN_IMMUNE_AFTER_MS;
        } else if (stunImmuneMs > 0f) {
            stunImmuneMs = Math.max(0f, stunImmuneMs - dt * 1000f);
        }
        float effSpeed = heroBaseSpeed * speedMultiplier * slowFactor * (itemManager != null ? itemManager.speedFactor() : 1f);
        float targetVx = (skillLock || stunned) ? 0f : moveX * MAX_SPEED * effSpeed;
        float targetVy = (skillLock || stunned) ? 0f : moveY * MAX_SPEED * effSpeed;
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

    /** ลูกน้อง 5 ตัวแบบด่าน 3 อยู่ฝั่งซ้ายของบอส (เฟส 1 เท่านั้น เฟส 2 ถูกเก็บออกหมด) */
    private void spawnBossMinions() {
        if (gameArea == null) return;
        SeaEnemy.setDualAttack(true);
        float width = gameArea.getWidth() > 0 ? gameArea.getWidth() : 1000f;
        float height = gameArea.getHeight() > 0 ? gameArea.getHeight() : 500f;
        String[] names = {"Crab", "Jellyfish", "Turtle", "Kraken", "Starfish"};
        String[] emojis = {"🦀", "🪼", "🐢", "🦑", "⭐️"};
        float[] xs = {0.10f, 0.25f, 0.05f, 0.22f, 0.12f};
        float[] ys = {0.15f, 0.35f, 0.55f, 0.75f, 0.85f};
        for (int i = 0; i < names.length; i++) {
            enemyList.add(new BossMinion(this, names[i], emojis[i], width * xs[i], height * ys[i]));
        }
    }

    private void spawnStageEnemies() {
        enemyList.clear();
        SeaEnemy.resetAttackQueue();
        if (gameArea == null) return;
        SeaEnemy.setDualAttack(stageConfig.dualAttack);

        float width = gameArea.getWidth() > 0 ? gameArea.getWidth() : 1000f;
        float height = gameArea.getHeight() > 0 ? gameArea.getHeight() : 500f;

        if (stageConfig.boss) {
            enemyList.add(new KrakenBoss(this, width * 0.6f, height * 0.2f, stageConfig));
            spawnBossMinions();
            return;
        }

        enemyList.add(new SeaEnemy(this, "Crab", "🦀", width * 0.70f, height * 0.15f, stageConfig));
        enemyList.add(new SeaEnemy(this, "Jellyfish", "🪼", width * 0.85f, height * 0.35f, stageConfig));
        enemyList.add(new SeaEnemy(this, "Turtle", "🐢", width * 0.95f, height * 0.55f, stageConfig));
        if (stageConfig.includeSquid) {
            enemyList.add(new SeaEnemy(this, "Kraken", "🦑", width * 0.90f, height * 0.75f, stageConfig));
        }
        enemyList.add(new SeaEnemy(this, "Starfish", "⭐️", width * 0.65f, height * 0.85f, stageConfig));
    }

    private void startCooldownUI(Button btn, String originalText, long baseMs) {
        if (btn == null) return;
        btn.setEnabled(false);
        new CountDownTimer((long) (baseMs * cooldownMultiplier * (itemManager != null ? itemManager.cooldownFactor() : 1f)), 100) {
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
        // ด่านจบแล้ว (ชนะ/แพ้) หรือไม่มีศัตรูเหลือ: ไม่เพิ่มสแตก ไม่เปิดโจทย์
        if (!isGameRunning || !anyEnemyAlive()) return;

        if (currentStack < maxStack) {
            // ติดดีบัฟ: ได้พลังต่อฮิตน้อยลง (เช่น 0.7) เศษสะสมไว้จนครบ 1 ถึงขึ้นสแตก
            stackFraction += ultDebuffRemainingMs > 0f ? stageConfig.ultGainFactor : 1f;
            int gained = (int) stackFraction;
            if (gained <= 0) return;
            stackFraction -= gained;
            currentStack = Math.min(maxStack, currentStack + gained);
            updateStackUI();
            if (currentStack >= maxStack) quizManager.show();
        }
    }

    private boolean anyEnemyAlive() {
        for (int i = 0; i < enemyList.size(); i++) {
            if (enemyList.get(i).isAlive) return true;
        }
        return false;
    }

    @Override
    public void damagePlayer(int damage) {
        if (!isGameRunning) return;
        // ช่วงอมตะสั้นๆ หลังโดน: กระสุนรัว/เส้นคลื่นที่ซ้อนกันในเฟรมเดียวไม่รุมโดนซ้ำจนหลอดเลือดหายวับ
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastDamageMs < HIT_INVULN_MS) return;
        lastDamageMs = nowMs;
        if (itemManager != null && itemManager.absorbHit()) return;   // โล่กันไว้ ไม่เสียเลือด ไม่นับเป็นโดนตี

        playerHp = Math.max(0, playerHp - damage);
        if (barPlayerHp != null) barPlayerHp.setProgress(playerHp);
        updateHpUI();

        // ถูกตีครบทุก N ครั้ง ศัตรูทำให้พลัง Ultimate ที่ได้ลดลงชั่วคราว
        hitsTaken++;
        if (stageConfig.ultDebuffEveryHits > 0 && playerHp > 0
                && hitsTaken % stageConfig.ultDebuffEveryHits == 0) {
            boolean wasActive = ultDebuffRemainingMs > 0f;
            ultDebuffRemainingMs = ULT_DEBUFF_DURATION_MS;
            if (!wasActive) updateStageInfo();
        }

        // ด่าน 3 เป็นต้นไป: ถูกตีครบทุก 3 ครั้ง พลังสะสมสู่ ULT ลด 20% ของหลอด
        // (ไม่แตะตอนโจทย์ขึ้นอยู่ ULT พร้อมกด หรือกำลังปล่อย ULT)
        if (currentStageId >= ENERGY_DRAIN_FROM_STAGE && playerHp > 0
                && hitsTaken % ENERGY_DRAIN_EVERY_HITS == 0
                && !ultimateReady && !ultimateRunning && !quizManager.isShowing()) {
            int drain = Math.round(maxStack * ENERGY_DRAIN_PERCENT / 100f);
            currentStack = Math.max(0, currentStack - drain);
            stackFraction = 0f;
            updateStackUI();
        }
        // HP หมด = เสียงแพ้แทนเสียงโดนตี
        SoundManager.play(playerHp <= 0 ? SoundManager.Sfx.LOSE : SoundManager.Sfx.PLAYER_HURT);

        if (playerContainer != null) {
            playerContainer.setAlpha(0.5f);
            playerContainer.postDelayed(() -> {
                if (playerContainer != null) playerContainer.setAlpha(1.0f);
            }, 100);
        }

        if (playerHp <= 0) {
            isGameRunning = false;
            showResultOverlay(false, 0);
        }
    }

    // =========================================================
    // HUD: ภาพพื้นหลังด่าน / ตัวเลข HP / ด่านและศัตรูที่เหลือ
    // =========================================================
    private void setupStageBackground(int stageId) {
        ImageView imgBattleBackground = findViewById(R.id.imgBattleBackground);
        if (imgBattleBackground == null) return;

        // ค้นหา Drawable ID ตามชื่อไฟล์ เช่น bg_stage_1, bg_stage_2 หรือใช้ภาพ default หากยังไม่มี
        int bgResId = getResources().getIdentifier("bg_stage_" + stageId, "drawable", getPackageName());
        if (bgResId != 0) {
            imgBattleBackground.setImageResource(bgResId);
        } else {
            imgBattleBackground.setImageResource(R.drawable.bg_level_video);
        }
    }

    private void updateHpUI() {
        TextView txtHpValue = findViewById(R.id.txtHpValue);
        if (txtHpValue != null) {
            txtHpValue.setText(String.format(Locale.US, "%d/%d", playerHp, maxPlayerHp));
        }
    }

    private void updateStageInfo() {
        TextView txtStageInfo = findViewById(R.id.txtStageInfo);
        if (txtStageInfo == null) return;
        int alive = 0;
        int bossLives = 0;
        for (int eIdx = 0; eIdx < enemyList.size(); eIdx++) {
            SeaEnemy e = enemyList.get(eIdx);
            if (e.isAlive) {
                alive++;
                if (e instanceof KrakenBoss) bossLives = e.livesLeft();
            }
        }
        String info = bossLives > 0
                ? String.format(Locale.US, "ด่าน %d  |  บอสเหลือ %d ชีวิต", currentStageId, bossLives)
                : String.format(Locale.US, "ด่าน %d  |  ศัตรูเหลือ %d ตัว", currentStageId, alive);
        if (ultDebuffRemainingMs > 0f) {
            info += String.format(Locale.US, "  |  ⚠ ULT -%d%%", Math.round((1f - stageConfig.ultGainFactor) * 100f));
        }
        txtStageInfo.setText(info);
    }

    // =========================================================
    // ดาวต่อด่าน: 3 ดาว = HP เหลือ >= 70%, 2 ดาว = >= 35%, ไม่งั้น 1 ดาว  (เก็บสถิติดีที่สุดต่อด่าน)
    // =========================================================
    private int starsForRemainingHp() {
        float ratio = playerHp / (float) maxPlayerHp;
        if (ratio >= 0.70f) return 3;
        if (ratio >= 0.35f) return 2;
        return 1;
    }

    // =========================================================
    // Overlay: สอนเล่น (ครั้งแรกที่เข้าด่าน 1)
    // =========================================================
    private void showTutorialIfFirstTime() {
        SharedPreferences prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE);
        if (currentStageId != 1 || prefs.getBoolean("tutorial_seen", false)) return;

        View overlay = findViewById(R.id.overlayTutorial);
        isGamePaused = true;
        resetJoystick();
        overlay.setVisibility(View.VISIBLE);
        findViewById(R.id.btnTutorialOk).setOnClickListener(v -> {
            prefs.edit().putBoolean("tutorial_seen", true).apply();
            overlay.setVisibility(View.GONE);
            isGamePaused = false;
        });
    }

    // =========================================================
    // Overlay: เมนูหยุดเกม / หน้าจอชนะ-แพ้
    // =========================================================
    private void setupOverlays() {
        overlayPause = findViewById(R.id.overlayPause);
        overlayResult = findViewById(R.id.overlayResult);

        findViewById(R.id.btnPauseResume).setOnClickListener(v -> closePauseMenu());
        findViewById(R.id.btnPauseRestart).setOnClickListener(v -> restartStage());
        findViewById(R.id.btnPauseExit).setOnClickListener(v -> returnToLevelSelect());

        // ปุ่ม Back ของเครื่อง: เปิดเมนูหยุดเกม / ถ้าเมนูเปิดอยู่ให้เล่นต่อ (ระหว่างขึ้นโจทย์หรือหน้าจอผลลัพธ์ไม่ทำอะไร)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (overlayPause.getVisibility() == View.VISIBLE) closePauseMenu();
                else openPauseMenu();
            }
        });
    }

    /** มี overlay อื่นเปิดอยู่ (โจทย์ / ผลลัพธ์ / เมนูหยุด) จึงไม่ควรเปิดเมนูหยุดซ้อน */
    private boolean isOverlayOpen() {
        return (quizManager != null && quizManager.isShowing())
                || overlayPause.getVisibility() == View.VISIBLE
                || overlayResult.getVisibility() == View.VISIBLE
                || findViewById(R.id.overlayTutorial).getVisibility() == View.VISIBLE;
    }

    private void openPauseMenu() {
        if (!isGameRunning || isOverlayOpen()) return;
        isGamePaused = true;
        resetJoystick();
        overlayPause.setVisibility(View.VISIBLE);
    }

    private void closePauseMenu() {
        overlayPause.setVisibility(View.GONE);
        isGamePaused = false;
        openPendingQuiz();
    }

    /**
     * โจทย์ที่ถูกกันไว้เพราะเมนูหยุด/หน้าจออื่นเปิดอยู่ตอนสแตกเต็ม ให้เปิดต่อเมื่อปิดเมนูแล้ว
     * (สแตกเต็มค้างอยู่ และยังไม่ได้ตอบ/ยังไม่ปลดล็อก ULT) ไม่งั้นสแตกเต็มแล้วโจทย์จะไม่ขึ้นอีกเลย
     */
    private void openPendingQuiz() {
        if (currentStack >= maxStack && !ultimateReady && !ultimateRunning && !quizManager.isShowing()) {
            quizManager.show();
        }
    }

    /** ตอนนี้เปิดโจทย์ได้ไหม: ด่านต้องยังเล่นอยู่ มีศัตรูเหลือ และไม่มี overlay อื่น (เมนูหยุด/ผลลัพธ์/สอนเล่น) เปิดอยู่ */
    private boolean canOpenQuiz() {
        return isGameRunning && anyEnemyAlive()
                && overlayPause.getVisibility() != View.VISIBLE
                && overlayResult.getVisibility() != View.VISIBLE
                && findViewById(R.id.overlayTutorial).getVisibility() != View.VISIBLE;
    }

    /**
     * ด่านจบ (ชนะ/แพ้): ปิดทุกอย่างที่อาจโผล่ตามมา เหลือแค่หน้าจอผลลัพธ์
     * - ปิดโจทย์ทันที (ยกเลิกนับเวลา ไม่เรียก onCorrect/onWrong/onTimeout)
     * - ปิดเมนูหยุดและหน้าสอนเล่น
     * - ยกเลิกการปล่อย Ultimate อัตโนมัติที่รอเวลาอยู่ และซ่อนหลอดเวลา Ultimate
     */
    private void cancelEverythingForStageEnd() {
        if (quizManager != null) quizManager.dismiss();
        overlayPause.setVisibility(View.GONE);
        findViewById(R.id.overlayTutorial).setVisibility(View.GONE);
        if (pendingAutoUltimate != null) {
            uiHandler.removeCallbacks(pendingAutoUltimate);
            pendingAutoUltimate = null;
        }
        hideUltimateDuration();
        if (itemManager != null) itemManager.clear();
        SeaEnemy.playerDamageBonus = 0;
        if (txtBuffs != null) txtBuffs.setVisibility(View.GONE);
    }

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingAutoUltimate;

    private void restartStage() {
        recreate();
    }

    private void showResultOverlay(boolean win, int stars) {
        cancelEverythingForStageEnd();   // หน้าจอชนะ/แพ้มีความสำคัญสูงสุด ที่เหลือต้องปิดหมด
        resetJoystick();
        TextView title = findViewById(R.id.txtResultTitle);
        TextView sub = findViewById(R.id.txtResultSub);
        TextView txtStars = findViewById(R.id.txtResultStars);
        if (win) {
            txtStars.setText(GameProgress.starsText(stars));
            txtStars.setVisibility(View.VISIBLE);
        } else {
            txtStars.setVisibility(View.GONE);
        }
        Button b1 = findViewById(R.id.btnResult1);
        Button b2 = findViewById(R.id.btnResult2);
        Button b3 = findViewById(R.id.btnResult3);

        if (win) {
            title.setText("🎉 ชนะแล้ว!");
            title.setTextColor(0xFF2ECC71);
            sub.setText("ผ่านด่าน " + currentStageId);
            if (currentStageId < MAX_STAGES) {
                setResultButton(b1, "ด่านถัดไป ▶", this::goToNextStage);
                setResultButton(b2, "เล่นอีกครั้ง", this::restartStage);
                setResultButton(b3, "เลือกด่าน", this::returnToLevelSelect);
            } else {
                // ด่านสุดท้าย ไม่มีด่านถัดไป
                setResultButton(b1, "เล่นอีกครั้ง", this::restartStage);
                setResultButton(b2, "เลือกด่าน", this::returnToLevelSelect);
                b3.setVisibility(View.GONE);
            }
        } else {
            title.setText("💀 แพ้แล้ว");
            title.setTextColor(0xFFE74C3C);
            sub.setText("HP หมด ลองสู้ใหม่อีกครั้ง");
            setResultButton(b1, "เล่นอีกครั้ง", this::restartStage);
            setResultButton(b2, "เลือกด่าน", this::returnToLevelSelect);
            b3.setVisibility(View.GONE);
        }
        overlayResult.setVisibility(View.VISIBLE);
    }

    private static void setResultButton(Button b, String text, Runnable action) {
        b.setVisibility(View.VISIBLE);
        b.setText(text);
        b.setOnClickListener(v -> action.run());
    }

    private void goToNextStage() {
        Intent intent = new Intent(this, BattleActivity.class);
        intent.putExtra("HERO_ID", currentHeroId);
        intent.putExtra("STAGE_ID", currentStageId + 1);
        startActivity(intent);
        finish();
    }

    private void returnToLevelSelect() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("SHOW_LEVEL_SELECT", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void checkWinCondition() {
        boolean allDead = true;
        for (int enemyIdx = 0; enemyIdx < enemyList.size(); enemyIdx++) {
            SeaEnemy enemy = enemyList.get(enemyIdx);
            if (stageConfig.boss && !(enemy instanceof KrakenBoss)) continue;   // ด่านบอส: ชนะเมื่อบอสตาย ลูกน้องไม่ต้องกำจัดให้หมด
            if (enemy.isAlive) {
                allDead = false;
                break;
            }
        }

        if (allDead && isGameRunning) {
            SoundManager.play(SoundManager.Sfx.WIN);
            isGameRunning = false;

            SharedPreferences prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE);
            int currentUnlocked = prefs.getInt("unlocked_stage", 1);

            if (currentStageId >= currentUnlocked) {
                prefs.edit().putInt("unlocked_stage", currentStageId + 1).apply();
            }

            // ดาวตาม HP ที่เหลือ เก็บเฉพาะสถิติที่ดีที่สุดของด่านนี้
            int stars = starsForRemainingHp();
            GameProgress.saveBestStars(this, currentStageId, stars);
            showResultOverlay(true, stars);
        }
    }

    private void setupQuiz() {
        // ชุดโจทย์ตามวิชาของฮีโร่ที่เลือก (QuestionBank เป็นแหล่งคำถามเดียว)
        String subject = playerHero.getSubject();
        quizManager = new QuizManager(this, findViewById(R.id.overlayQuiz), subject,
                QuestionBank.forSubject(subject),
                new QuizManager.Listener() {
                    @Override
                    public void onQuizShown() {
                        SoundManager.play(SoundManager.Sfx.QUIZ_SHOW);
                        isGamePaused = true;
                        resetJoystick();
                    }

                    @Override
                    public void onCorrect() {
                        SoundManager.play(SoundManager.Sfx.QUIZ_CORRECT);
                        if (playerHero.usesUltimateButton()) {
                            // ตอบถูก -> แค่ปลดล็อกปุ่ม ULT ให้ผู้เล่นกดเองตอนไหนก็ได้
                            isGamePaused = false;
                            ultimateReady = true;
                            updateUltimateButton();
                            SoundManager.play(SoundManager.Sfx.ULT_READY);
                            return;
                        }
                        // ฮีโร่อื่น: ตอบถูก -> ปล่อย Ultimate อัตโนมัติ (แบบเดิม)
                        SoundManager.play(SoundManager.Sfx.ULTIMATE);
                        ultimateRunning = true;
                        pendingAutoUltimate = () -> {
                            pendingAutoUltimate = null;
                            isGamePaused = false;
                            if (!isGameRunning || isFinishing()) return;
                            playerHero.executeUltimateSkill(BattleActivity.this);
                        };
                        uiHandler.postDelayed(pendingAutoUltimate, 600);
                    }

                    @Override
                    public void onWrong() {
                        SoundManager.play(SoundManager.Sfx.QUIZ_WRONG);
                        resetStackAfterQuiz();
                    }

                    @Override
                    public void onTimeout() {
                        SoundManager.play(SoundManager.Sfx.QUIZ_WRONG);
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
            txtStackGauge.setText(playerHero.getName() + " | Stack: " + currentStack + "/" + maxStack);
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
    protected void onResume() {
        super.onResume();
        SoundManager.playMusic(this, "bgm_battle");
    }

    @Override
    protected void onPause() {
        super.onPause();
        SoundManager.pauseMusic();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isGameRunning = false;
        SeaEnemy.resetAttackQueue();
        SeaEnemy.playerDamageBonus = 0;

        Choreographer.getInstance().removeFrameCallback(frameCallback);

        if (quizManager != null) quizManager.destroy();

        // ทิ้งกอง View เอฟเฟกต์ที่ใช้ซ้ำ (ผูกกับพื้นที่เกมของ Activity นี้)
        HitEffects.release();
        GhostPool.release();
    }
}