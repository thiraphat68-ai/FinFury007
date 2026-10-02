package com.example.finfury;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends BaseActivity {

    // ตัวแปรเก็บตัวละครที่ถูกเลือก (0 = ยังไม่ได้เลือก, 1-5 คือตัวละคร)
    private int selectedHeroId = 0;
    private int selectedStageId = 1; // ด่านที่เลือก ส่งไป BattleActivity เพื่อบันทึกการปลดล็อกด่านถัดไปให้ถูกด่าน
    private LinearLayout[] heroCards;

    // ตัวแปรสำหรับแสดงรายละเอียดตัวละครด้านล่าง
    private ImageView imgSelectedHero;
    private TextView txtSelectedName;
    private TextView txtSelectedSubject;
    private TextView txtSelectedSkills;

    // วิดีโอพื้นหลังและหน้าจอ เก็บเป็นฟิลด์ เพื่อกู้วิดีโอกลับมาตอนกลับจากหน้าอื่น (เช่น ตั้งค่า)
    private VideoView videoBackground;
    private VideoView videoBackgroundLevel;
    private View layoutMainMenu;
    private View layoutLevelSelect;
    private boolean leftScreen = false;
    private TextView txtSelectedDesc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SoundManager.init(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ==========================================
        // 🎬 1. จัดการวิดีโอพื้นหลัง หน้าที่ 1 (เมนูหลัก)
        // ==========================================
        videoBackground = findViewById(R.id.videoBackground);
        String videoPath = "android.resource://" + getPackageName() + "/" + R.raw.bg_video;
        videoBackground.setVideoURI(Uri.parse(videoPath));
        videoBackground.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0f, 0f); // ปิดเสียงวิดีโอ ไม่ให้ตีกับเพลง
        });
        videoBackground.start();

        // ==========================================
        // 🎬 2. จัดการวิดีโอพื้นหลัง หน้าที่ 2 (เลือกด่าน)
        // ==========================================
        videoBackgroundLevel = findViewById(R.id.videoBackgroundLevel);
        String videoPathLevel = "android.resource://" + getPackageName() + "/" + R.raw.bg_level_video;
        videoBackgroundLevel.setVideoURI(Uri.parse(videoPathLevel));
        videoBackgroundLevel.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0f, 0f); // ปิดเสียงวิดีโอ ไม่ให้ตีกับเพลง
        });

        // ==========================================
        // 🔄 ส่วนการสลับหน้าจอและการกดปุ่มหน้าแรก
        // ==========================================
        layoutMainMenu = findViewById(R.id.layoutMainMenu);
        layoutLevelSelect = findViewById(R.id.layoutLevelSelect);
        View layoutHeroSelect = findViewById(R.id.layoutHeroSelect);

        // กดปุ่ม Start Game (จากหน้า 1 ไปหน้า 2)
        Button btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> {
            SoundManager.play(SoundManager.Sfx.BUTTON);
            layoutMainMenu.setVisibility(View.GONE);
            videoBackground.pause();

            layoutLevelSelect.setVisibility(View.VISIBLE);
            videoBackgroundLevel.start();
        });

        // ==========================================
        // 🔙 ปุ่มย้อนกลับ (จากหน้า 3 กลับไปหน้า 2)
        // ==========================================
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                SoundManager.play(SoundManager.Sfx.BUTTON);
                layoutHeroSelect.setVisibility(View.GONE); // ซ่อนหน้าเลือกตัวละคร
                layoutLevelSelect.setVisibility(View.VISIBLE); // แสดงหน้าเลือกด่านอีกครั้ง
                videoBackgroundLevel.start(); // เล่นวิดีโอด่านต่อ
            });
        }

        // ==========================================
        // ⚔️ 4. ระบบเลือกตัวละคร (Hero Highlight & Details)
        // ==========================================
        Button btnFight = findViewById(R.id.btnFight);

        // ผูกตัวแปรการ์ดตัวละครทั้ง 5
        LinearLayout cardHero1 = findViewById(R.id.cardHero1);
        LinearLayout cardHero2 = findViewById(R.id.cardHero2);
        LinearLayout cardHero3 = findViewById(R.id.cardHero3);
        LinearLayout cardHero4 = findViewById(R.id.cardHero4);
        LinearLayout cardHero5 = findViewById(R.id.cardHero5);

        heroCards = new LinearLayout[]{cardHero1, cardHero2, cardHero3, cardHero4, cardHero5};

        // ผูกตัวแปรแสดงรายละเอียดฮีโร่ที่เลือกด้านล่าง
        imgSelectedHero = findViewById(R.id.imgSelectedHero);
        txtSelectedName = findViewById(R.id.txtSelectedName);
        txtSelectedSubject = findViewById(R.id.txtSelectedSubject);
        txtSelectedSkills = findViewById(R.id.txtSelectedSkills);
        txtSelectedDesc = findViewById(R.id.txtSelectedDesc);

        // ตั้งค่าให้เลือกตัวละครแรก (Swordfish) เป็นค่าเริ่มต้น
        selectHero(1, btnFight);

        // ตั้งค่า Event การคลิกให้การ์ดแต่ละตัว
        for (int i = 0; i < heroCards.length; i++) {
            final int heroIndex = i + 1; // 1 = Swordfish, 2 = Pufferfish, ...
            heroCards[i].setOnClickListener(v -> {
                SoundManager.play(SoundManager.Sfx.BUTTON);
                selectHero(heroIndex, btnFight);
            });
        }

        // ==========================================
        // 🌀 3. จัดการปุ่มน้ำวน (หน้าเลือกด่าน)
        // ==========================================
        // ปลดล็อกทุกด่านตั้งแต่เริ่ม: ปุ่มด่าน 1-5 ใช้ได้หมด และทำงานเหมือนกัน (ต่างกันแค่หมายเลขด่าน)
        int[] stageButtonIds = {R.id.btnStage1, R.id.btnStage2, R.id.btnStage3, R.id.btnStage4, R.id.btnStage5};
        for (int i = 0; i < stageButtonIds.length; i++) {
            final int stageId = i + 1;
            ImageButton btnStage = findViewById(stageButtonIds[i]);
            btnStage.setEnabled(true);
            // ด่าน 3-4 ในเลย์เอาต์ใช้ไอคอนกุญแจ จึงเปลี่ยนเป็นไอคอนน้ำวนปกติในโค้ด (ด่าน 5 คงรูปบอส)
            if (stageId == 3 || stageId == 4) btnStage.setImageResource(R.drawable.ic_whirlpool_blue);
            btnStage.setOnClickListener(v -> {
                SoundManager.play(SoundManager.Sfx.BUTTON);
                selectedStageId = stageId;
                layoutLevelSelect.setVisibility(View.GONE);
                videoBackgroundLevel.pause();
                layoutHeroSelect.setVisibility(View.VISIBLE);
                selectHero(1, btnFight);
            });
        }

        // ==========================================
        // 🥊 5. ปุ่ม FIGHT! (เมื่อพร้อมลุย)
        // ==========================================
        btnFight.setOnClickListener(v -> {
            SoundManager.play(SoundManager.Sfx.BUTTON);
            if (selectedHeroId > 0) {
                // เปิดหน้า BattleActivity พร้อมส่งหมายเลขปลาไปด้วย
                Intent intent = new Intent(MainActivity.this, BattleActivity.class);
                intent.putExtra("HERO_ID", selectedHeroId);
                intent.putExtra("STAGE_ID", selectedStageId);
                startActivity(intent);
            }
        });

        // ปุ่มตั้งค่าในเมนูหลัก
        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            SoundManager.play(SoundManager.Sfx.BUTTON);
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        // กลับมาจากด่าน (ชนะ/แพ้) -> ข้ามเมนูหลัก ไปหน้าเลือกด่านเลย
        if (getIntent().getBooleanExtra("SHOW_LEVEL_SELECT", false)) {
            layoutMainMenu.setVisibility(View.GONE);
            videoBackground.pause();
            layoutLevelSelect.setVisibility(View.VISIBLE);
            videoBackgroundLevel.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        leftScreen = true;
        SoundManager.pauseMusic();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SoundManager.playMusic(this, "bgm_menu");
        updateStageStars();

        // กลับมาจากหน้าอื่น (เช่น ตั้งค่า): พื้นผิววิดีโอของ VideoView ถูกทำลายตอนออกจากหน้านี้
        // ถ้าไม่โหลดใหม่ พื้นหลังจะเป็นสีดำ จึงตั้งวิดีโอใหม่แล้วเล่นของหน้าที่แสดงอยู่
        if (leftScreen) {
            leftScreen = false;
            if (layoutMainMenu.getVisibility() == View.VISIBLE) {
                reloadVideo(videoBackground, R.raw.bg_video);
            } else if (layoutLevelSelect.getVisibility() == View.VISIBLE) {
                reloadVideo(videoBackgroundLevel, R.raw.bg_level_video);
            }
        }
    }

    private void reloadVideo(VideoView video, int rawRes) {
        video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + rawRes));
        video.start();
    }

    /** ดาวที่ดีที่สุดของแต่ละด่านใต้ปุ่มด่านในหน้าเลือกด่าน */
    private void updateStageStars() {
        int[] starViews = {R.id.txtStars1, R.id.txtStars2, R.id.txtStars3, R.id.txtStars4, R.id.txtStars5};
        for (int i = 0; i < starViews.length; i++) {
            TextView tv = findViewById(starViews[i]);
            if (tv != null) tv.setText(GameProgress.starsText(GameProgress.getStars(this, i + 1)));
        }
    }

    // ฟังก์ชันจัดการการเปลี่ยนสีไฮไลท์, แสดงรายละเอียดตัวละคร และเปิดปุ่ม FIGHT
    private void selectHero(int heroIndex, Button btnFight) {
        selectedHeroId = heroIndex;

        // 1. คืนค่าการ์ดทั้งหมดให้เป็นสีพื้นหลังปกติ (#1A252C)
        for (LinearLayout card : heroCards) {
            if (card != null) {
                card.setBackgroundColor(Color.parseColor("#1A252C"));
            }
        }

        // 2. เปลี่ยนสีการ์ดที่ถูกเลือกให้สว่างไฮไลท์ขึ้นมา (#00ADB5)
        if (heroCards[heroIndex - 1] != null) {
            heroCards[heroIndex - 1].setBackgroundColor(Color.parseColor("#00ADB5"));
        }

        // 3. อัปเดตข้อมูลภาพใหญ่ ข้อความวิชา และคำอธิบายด้านล่างให้ถูกต้องตามหมายเลขฮีโร่
        switch (heroIndex) {
            case 1: // Swordfish
                if (imgSelectedHero != null) imgSelectedHero.setImageResource(R.drawable.hero_1);
                if (txtSelectedName != null) txtSelectedName.setText("Swordfish");
                if (txtSelectedSubject != null) txtSelectedSubject.setText("วงจรไฟฟ้า (Electrical Circuits)");
                if (txtSelectedDesc != null) txtSelectedDesc.setText("ใช้พลังงานไฟฟ้าในการโจมตี ฝากประจุไว้บนศัตรูแล้วจุดระเบิด และเชื่อมศัตรูทั้งหมดเป็นวงจรเดียวกัน");
                break;
            case 2: // Pufferfish
                if (imgSelectedHero != null) imgSelectedHero.setImageResource(R.drawable.hero_2);
                if (txtSelectedName != null) txtSelectedName.setText("Pufferfish");
                if (txtSelectedSubject != null) txtSelectedSubject.setText("เคมี (Chemistry)");
                if (txtSelectedDesc != null) txtSelectedDesc.setText("พองตัวและปล่อยสารเคมีสะสมพิษเพื่อสร้างเกราะสะท้อนการโจมตี");
                break;
            case 3: // Shark
                if (imgSelectedHero != null) imgSelectedHero.setImageResource(R.drawable.hero_3);
                if (txtSelectedName != null) txtSelectedName.setText("Shark");
                if (txtSelectedSubject != null) txtSelectedSubject.setText("แคลคูลัส (Calculus)");
                if (txtSelectedDesc != null) txtSelectedDesc.setText("ใช้ความแม่นยำในการคำนวณเวกเตอร์เพื่อพุ่งโจมตีศัตรูอย่างรวดเร็ว");
                break;
            case 4: // Octopus
                if (imgSelectedHero != null) imgSelectedHero.setImageResource(R.drawable.hero_4);
                if (txtSelectedName != null) txtSelectedName.setText("Octopus");
                if (txtSelectedSubject != null) txtSelectedSubject.setText("การเขียนโปรแกรม (Programming)");
                if (txtSelectedDesc != null) txtSelectedDesc.setText("ใช้หนวดสั่งการเขียนลูปและอัลกอริทึมในการพ่นหมึกบดบังศัตรู");
                break;
            case 5: // Electric Eel
                if (imgSelectedHero != null) imgSelectedHero.setImageResource(R.drawable.hero_5);
                if (txtSelectedName != null) txtSelectedName.setText("Electric Eel");
                if (txtSelectedSubject != null) txtSelectedSubject.setText("ฟิสิกส์ (Physics)");
                if (txtSelectedDesc != null) txtSelectedDesc.setText("ปล่อยกระแสไฟฟ้าแรงสูงตามกฎการนำไฟฟ้าของฟิสิกส์เพื่อช็อตศัตรู");
                break;
        }

        // 3.1 คำอธิบายสกิล 1 / สกิล 2 / Ultimate ดึงจากคลาสฮีโร่ตัวนั้นโดยตรง จึงตรงกับสกิลจริงเสมอ
        if (txtSelectedSkills != null) {
            Hero hero = HeroFactory.createHero(heroIndex);
            txtSelectedSkills.setText(
                    "1) " + hero.getSkill1Icon() + " " + hero.getSkill1Name() + ": " + hero.getSkill1Description()
                    + "\n2) " + hero.getSkill2Icon() + " " + hero.getSkill2Name() + ": " + hero.getSkill2Description()
                    + "\nULT) " + hero.getUltimateIcon() + " " + hero.getUltimateName() + ": " + hero.getUltimateDescription());
        }

        // 4. ปลดล็อคปุ่ม FIGHT! และคืนความเข้มปุ่มเป็น 100%
        btnFight.setEnabled(true);
        btnFight.setAlpha(1.0f);
    }
}