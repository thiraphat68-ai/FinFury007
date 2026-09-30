package com.example.finfury;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import androidx.annotation.NonNull; // 🟢 Import สำหรับแก้ Warning line 38
import androidx.appcompat.app.AppCompatActivity;

public class SelectStageActivity extends AppCompatActivity {

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
        updateStageUnlocks();
    }

    private void updateStageUnlocks() {
        SharedPreferences prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE);
        int unlockedStage = prefs.getInt("unlocked_stage", 1);

        setupStageButton(btnStage1, 1, unlockedStage, R.drawable.ic_whirlpool_green);
        setupStageButton(btnStage2, 2, unlockedStage, R.drawable.ic_whirlpool_blue);
        setupStageButton(btnStage3, 3, unlockedStage, R.drawable.ic_whirlpool_blue);
        setupStageButton(btnStage4, 4, unlockedStage, R.drawable.ic_whirlpool_blue);
        setupStageButton(btnStage5, 5, unlockedStage, R.drawable.ic_whirlpool_blue);
    }

    private void setupStageButton(ImageButton button, int stageNumber, int unlockedStage, int unlockedDrawableRes) {
        if (button == null) return;

        if (unlockedStage >= stageNumber) {
            button.setEnabled(true);
            button.setAlpha(1.0f);
            button.setImageResource(unlockedDrawableRes);
            button.setOnClickListener(v -> startBattleStage(stageNumber));
        } else {
            button.setEnabled(false);
            button.setAlpha(0.5f);
            button.setImageResource(R.drawable.ic_whirlpool_lock);
            button.setOnClickListener(null);
        }
    }

    private void startBattleStage(int stageId) {
        Intent intent = new Intent(SelectStageActivity.this, BattleActivity.class);
        intent.putExtra("STAGE_ID", stageId);
        intent.putExtra("HERO_ID", currentHeroId);
        startActivity(intent);
    }
}