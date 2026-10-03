package com.example.finfury;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull; // 🟢 Import สำหรับแก้ Warning line 38

public class SelectStageActivity extends BaseActivity {

    private ImageButton btnStage1;
    private ImageButton btnStage2;
    private ImageButton btnStage3;
    private ImageButton btnStage4;
    private ImageButton btnStage5;

    private int currentHeroId = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_stage);

        currentHeroId = getIntent().getIntExtra("HERO_ID", 1);

        // ดึง View ตาม ID ใน XML
        btnStage1 = findViewById(R.id.btnStage1);
        btnStage2 = findViewById(R.id.btnStage2);
        btnStage3 = findViewById(R.id.btnStage3);
        btnStage4 = findViewById(R.id.btnStage4);
        btnStage5 = findViewById(R.id.btnStage5);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) { // 🟢 ใส่ @NonNull แก้ Warning line 38
        super.onNewIntent(intent);
        setIntent(intent);
        currentHeroId = intent.getIntExtra("HERO_ID", currentHeroId); // 🟢 ตัด if ออก แก้ Warning line 41
    }

    @Override
    protected void onResume() {
        super.onResume();
        SoundManager.playMusic(this, "bgm_menu");
        updateStageUnlocks();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SoundManager.pauseMusic();
    }

    private void updateStageUnlocks() {
        // ทุกด่านเลือกได้ตั้งแต่เริ่ม ไม่ดูค่า "unlocked_stage" (BattleActivity ยังบันทึกค่านี้ไว้ตามเดิม)

        // ดาวที่ดีที่สุดของแต่ละด่านใต้ปุ่มด่าน
        int[] starViews = {R.id.txtStars1, R.id.txtStars2, R.id.txtStars3, R.id.txtStars4, R.id.txtStars5};
        for (int i = 0; i < starViews.length; i++) {
            TextView tv = findViewById(starViews[i]);
            if (tv != null) tv.setText(GameProgress.starsText(GameProgress.getStars(this, i + 1)));
        }

        setupStageButton(btnStage1, 1, R.drawable.ic_whirlpool_green, R.id.txtStars1);
        setupStageButton(btnStage2, 2, R.drawable.ic_whirlpool_blue, R.id.txtStars2);
        setupStageButton(btnStage3, 3, R.drawable.ic_whirlpool_blue, R.id.txtStars3);
        setupStageButton(btnStage4, 4, R.drawable.ic_whirlpool_blue, R.id.txtStars4);
        setupStageButton(btnStage5, 5, R.drawable.ic_whirlpool_blue, R.id.txtStars5);
    }

    private void setupStageButton(ImageButton button, int stageNumber, int drawableRes, int starsViewId) {
        if (button == null) return;

        button.setEnabled(true);
        button.setAlpha(1.0f);
        button.setImageResource(drawableRes);
        button.setOnClickListener(v -> startBattleStage(stageNumber));
        makeDraggable(button, findViewById(starsViewId), stageNumber);
    }

    // ---------------------------------------------------------
    // ลากไอคอนด่านไปวางที่ไหนก็ได้ (แตะเฉยๆ ยังเข้าด่านเหมือนเดิม) ตำแหน่งที่วางจำไว้ข้ามการเปิดแอป
    // ---------------------------------------------------------
    private static final String PREFS_STAGE_POS = "StageIconPositions";

    @SuppressLint("ClickableViewAccessibility")
    private void makeDraggable(View icon, View starsLabel, int stageNumber) {
        SharedPreferences prefs = getSharedPreferences(PREFS_STAGE_POS, MODE_PRIVATE);
        float density = getResources().getDisplayMetrics().density;
        // เก็บเป็น dp เพื่อให้ตรงกันแม้ความละเอียดหน้าจอเปลี่ยน
        applyOffset(icon, starsLabel,
                prefs.getFloat("x" + stageNumber, 0f) * density,
                prefs.getFloat("y" + stageNumber, 0f) * density);

        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        final float[] down = new float[4];   // rawX, rawY, translationX, translationY ตอนนิ้วแตะ
        final boolean[] dragging = {false};

        icon.setOnTouchListener((v, e) -> {
            View parent = (View) v.getParent();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                    down[2] = v.getTranslationX();
                    down[3] = v.getTranslationY();
                    dragging[0] = false;
                    return false;   // ให้ปุ่มจัดการการกดตามปกติต่อ (ถ้าไม่ลากจะเป็นการคลิก)
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - down[0], dy = e.getRawY() - down[1];
                    if (!dragging[0] && Math.hypot(dx, dy) > slop) {
                        dragging[0] = true;
                        v.setPressed(false);
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (!dragging[0]) return false;
                    // จำกัดไม่ให้ลากออกนอกจอ (v.getLeft/Top คือตำแหน่งตั้งต้นของ layout)
                    float tx = down[2] + dx, ty = down[3] + dy;
                    if (parent != null) {
                        tx = Math.max(-v.getLeft(), Math.min(parent.getWidth() - v.getRight(), tx));
                        ty = Math.max(-v.getTop(), Math.min(parent.getHeight() - v.getBottom(), ty));
                    }
                    applyOffset(v, starsLabel, tx, ty);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        dragging[0] = false;
                        v.setPressed(false);
                        prefs.edit()
                                .putFloat("x" + stageNumber, v.getTranslationX() / density)
                                .putFloat("y" + stageNumber, v.getTranslationY() / density)
                                .apply();
                        return true;   // กลืนเหตุการณ์ ไม่ให้นับเป็นคลิกเข้าด่าน
                    }
                    return false;
                default:
                    return false;
            }
        });
    }

    /** ย้ายไอคอนพร้อมป้ายดาวใต้ไอคอน (ป้ายผูกกับไอคอนด้วย constraint แต่ translation ไม่ส่งต่อให้อัตโนมัติ) */
    private static void applyOffset(View icon, View starsLabel, float tx, float ty) {
        icon.setTranslationX(tx);
        icon.setTranslationY(ty);
        if (starsLabel != null) {
            starsLabel.setTranslationX(tx);
            starsLabel.setTranslationY(ty);
        }
    }

    private void startBattleStage(int stageId) {
        SoundManager.play(SoundManager.Sfx.BUTTON);
        Intent intent = new Intent(SelectStageActivity.this, BattleActivity.class);
        intent.putExtra("STAGE_ID", stageId);
        intent.putExtra("HERO_ID", currentHeroId);
        startActivity(intent);
    }
}