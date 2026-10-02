package com.example.finfury;

import android.content.Intent;
import android.os.Bundle;
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

        setupStageButton(btnStage1, 1, R.drawable.ic_whirlpool_green);
        setupStageButton(btnStage2, 2, R.drawable.ic_whirlpool_blue);
        setupStageButton(btnStage3, 3, R.drawable.ic_whirlpool_blue);
        setupStageButton(btnStage4, 4, R.drawable.ic_whirlpool_blue);
        setupStageButton(btnStage5, 5, R.drawable.ic_whirlpool_blue);
    }

    private void setupStageButton(ImageButton button, int stageNumber, int drawableRes) {
        if (button == null) return;

        button.setEnabled(true);
        button.setAlpha(1.0f);
        button.setImageResource(drawableRes);
        button.setOnClickListener(v -> startBattleStage(stageNumber));
    }

    private void startBattleStage(int stageId) {
        SoundManager.play(SoundManager.Sfx.BUTTON);
        Intent intent = new Intent(SelectStageActivity.this, BattleActivity.class);
        intent.putExtra("STAGE_ID", stageId);
        intent.putExtra("HERO_ID", currentHeroId);
        startActivity(intent);
    }
}