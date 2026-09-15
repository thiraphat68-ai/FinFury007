package com.example.finfury;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class BattleActivity extends AppCompatActivity {

    private LinearLayout playerContainer;
    private View joystickKnob;
    private ImageButton btnAttack;
    private TextView txtStackGauge;

    private float joystickCenterX, joystickCenterY;
    private float moveX = 0f, moveY = 0f;
    private final float moveSpeed = 12f;
    private float swimTime = 0f;

    // ⚔️ ตัวแปรระบบ Stack-to-Quiz & Hero Model
    private Hero playerHero;
    private int currentHeroId = 1;
    private int currentStack = 0;
    private static final int MAX_STACK = 10;
    private long lastAttackTime = 0;
    private static final long ATTACK_COOLDOWN_MS = 500;

    private boolean isFacingRight = true;
    private final Handler gameLoopHandler = new Handler(Looper.getMainLooper());
    private boolean isGameRunning = true;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);

        currentHeroId = getIntent().getIntExtra("HERO_ID", 1);
        playerHero = HeroFactory.createHero(currentHeroId);

        playerContainer = findViewById(R.id.playerContainer);
        ImageView imgPlayer = findViewById(R.id.imgPlayer);
        TextView txtPlayerName = findViewById(R.id.txtPlayerName);

        txtStackGauge = txtPlayerName;

        View joystickBase = findViewById(R.id.joystickBase);
        joystickKnob = findViewById(R.id.joystickKnob);

        btnAttack = findViewById(R.id.btnAttack);
        ImageButton btnSkill1 = findViewById(R.id.btnSkill1);
        ImageButton btnSkill2 = findViewById(R.id.btnSkill2);
        ImageButton btnSkill3 = findViewById(R.id.btnSkill3);

        setupHeroAndSkills(currentHeroId, imgPlayer, txtPlayerName);
        updateStackUI();

        joystickBase.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    joystickCenterX = joystickBase.getWidth() / 2f;
                    joystickCenterY = joystickBase.getHeight() / 2f;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float touchX = event.getX() - joystickCenterX;
                    float touchY = event.getY() - joystickCenterY;
                    float dist = (float) Math.sqrt(touchX * touchX + touchY * touchY);
                    if (dist > 0) {
                        float limit = Math.min(dist, 120f);
                        moveX = (touchX / dist);
                        moveY = (touchY / dist);
                        joystickKnob.setTranslationX(moveX * limit);
                        joystickKnob.setTranslationY(moveY * limit);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    moveX = 0f;
                    moveY = 0f;
                    joystickKnob.setTranslationX(0f);
                    joystickKnob.setTranslationY(0f);
                    return true;
            }
            return false;
        });

        btnAttack.setOnClickListener(this::handleNormalAttack);
        btnSkill1.setOnClickListener(v -> playSkillAnimation(1, v));
        btnSkill2.setOnClickListener(v -> playSkillAnimation(2, v));

        btnSkill3.setOnClickListener(v -> {
            if (currentStack >= MAX_STACK) {
                showQuizDialog();
            } else {
                Toast.makeText(this, "Stack ยังไม่เต็ม!", Toast.LENGTH_SHORT).show();
            }
        });

        startGameLoop();
    }

    private void handleNormalAttack(View button) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAttackTime >= ATTACK_COOLDOWN_MS) {
            lastAttackTime = currentTime;
            playSkillAnimation(0, button);

            if (currentStack < MAX_STACK) {
                currentStack++;
                updateStackUI();

                if (currentStack >= MAX_STACK) {
                    showQuizDialog();
                }
            }
        }
    }

    @SuppressWarnings("unused")
    public void onHitByBoss(int stackPenalty) {
        currentStack = Math.max(0, currentStack - stackPenalty);
        updateStackUI();
    }

    private void showQuizDialog() {
        String title;
        String question;
        String[] options;
        final int targetCorrectIndex; // กำหนดให้เป็น final เพื่อใช้งานใน Lambda ได้สมบูรณ์

        switch (currentHeroId) {
            case 1:
                title = "Calculus Quiz";
                question = "อนุพันธ์ของ x² (d/dx x²) เท่ากับข้อใด?";
                options = new String[]{"x", "2x", "x²", "2"};
                targetCorrectIndex = 1;
                break;
            case 2:
                title = "Chemistry Quiz";
                question = "สูตรเคมีของน้ำคือข้อใด?";
                options = new String[]{"CO2", "NaCl", "H2O", "O2"};
                targetCorrectIndex = 2;
                break;
            case 3:
                title = "Circuits Quiz";
                question = "กฎของโอห์ม (Ohm's Law) คือสูตรใด?";
                options = new String[]{"V = IR", "P = IV", "F = ma", "E = mc²"};
                targetCorrectIndex = 0;
                break;
            case 4:
                title = "Programming Quiz";
                question = "การวนลูปที่ไม่ทราบจำนวนรอบแน่นอนควรใช้คำสั่งใด?";
                options = new String[]{"for", "while", "if-else", "switch"};
                targetCorrectIndex = 1;
                break;
            case 5:
            default:
                title = "Physics Quiz";
                question = "หน่วยของแรง (Force) ในระบบ SI คืออะไร?";
                options = new String[]{"Joule", "Watt", "Newton", "Pascal"};
                targetCorrectIndex = 2;
                break;
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(question)
                .setCancelable(false)
                .setItems(options, (dialog, which) -> {
                    if (which == targetCorrectIndex) {
                        Toast.makeText(this, "ถูกต้อง! ปลดล็อก ULTIMATE!", Toast.LENGTH_SHORT).show();
                        executeUltimateSkill();
                    } else {
                        Toast.makeText(this, "ผิด! Stack ถูกรีเซ็ต", Toast.LENGTH_SHORT).show();
                        currentStack = 0;
                        updateStackUI();
                    }
                })
                .show();
    }

    private void executeUltimateSkill() {
        float direction = playerContainer.getScaleX() > 0 ? 1f : -1f;

        playerContainer.animate().scaleX(direction * 2.0f).scaleY(2.0f).setDuration(300)
                .withEndAction(() -> playerContainer.animate().translationXBy(direction * 500f).setDuration(400)
                        .withEndAction(() -> {
                            playerContainer.animate().scaleX(direction).scaleY(1f).translationXBy(direction * -500f).setDuration(300).start();
                            currentStack = 0;
                            updateStackUI();
                        }).start()).start();
    }

    @SuppressLint("SetTextI18n")
    private void updateStackUI() {
        if (txtStackGauge != null && playerHero != null) {
            txtStackGauge.setText(playerHero.getName() + " | Stack: " + currentStack + "/" + MAX_STACK);
        }
    }

    private void setupHeroAndSkills(int heroId, ImageView imgPlayer, TextView txtPlayerName) {
        if (playerHero != null) {
            txtPlayerName.setText(playerHero.getName());
        }

        if (heroId == 1) {
            imgPlayer.setImageResource(R.drawable.hero_1);
            btnAttack.setImageResource(android.R.drawable.ic_menu_edit);
        } else if (heroId == 2) {
            imgPlayer.setImageResource(R.drawable.hero_2);
            btnAttack.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        } else {
            imgPlayer.setImageResource(R.drawable.hero_3);
        }
    }

    private void playSkillAnimation(int skillNumber, View button) {
        button.animate().scaleX(0.8f).scaleY(0.8f).setDuration(80).withEndAction(() ->
                button.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        ).start();

        float direction = playerContainer.getScaleX() > 0 ? 1f : -1f;

        if (skillNumber == 0) {
            playerContainer.animate().translationXBy(direction * 80f).setDuration(100)
                    .withEndAction(() -> playerContainer.animate().translationXBy(direction * -80f).setDuration(150).start()).start();
        } else if (skillNumber == 1) {
            playerContainer.animate().translationXBy(direction * 300f).scaleY(0.5f).setDuration(150)
                    .withEndAction(() -> playerContainer.animate().scaleY(1f).setDuration(100).start()).start();
        }
    }

    private void startGameLoop() {
        Runnable gameRunnable = new Runnable() {
            @Override
            public void run() {
                if (isGameRunning) {
                    if (moveX > 0) isFacingRight = true;
                    else if (moveX < 0) isFacingRight = false;

                    if (moveX != 0 || moveY != 0) {
                        float newX = playerContainer.getTranslationX() + (moveX * moveSpeed);
                        float newY = playerContainer.getTranslationY() + (moveY * moveSpeed);
                        playerContainer.setTranslationX(newX);
                        playerContainer.setTranslationY(newY);

                        swimTime += 0.35f;
                        playerContainer.setRotation((float) Math.sin(swimTime) * 12f);
                        float stretchX = 1f + (float) Math.sin(swimTime * 2) * 0.08f;
                        float stretchY = 1f - (float) Math.sin(swimTime * 2) * 0.08f;

                        playerContainer.setScaleX(isFacingRight ? stretchX : -stretchX);
                        playerContainer.setScaleY(stretchY);
                    } else {
                        swimTime += 0.1f;
                        playerContainer.setRotation((float) Math.sin(swimTime) * 3f);
                        float breathStretch = 1f + (float) Math.sin(swimTime) * 0.03f;
                        playerContainer.setScaleX(isFacingRight ? breathStretch : -breathStretch);
                        playerContainer.setScaleY(breathStretch);
                    }

                    gameLoopHandler.postDelayed(this, 16);
                }
            }
        };
        gameLoopHandler.post(gameRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isGameRunning = false;
    }
}