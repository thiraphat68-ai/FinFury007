package com.example.finfury;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
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
    // ชื่อ/คำอธิบายสกิลของฮีโร่ที่เลือก (แยกเป็น view ต่อสกิล)
    private TextView txtSkill1Name, txtSkill1Desc, txtSkill2Name, txtSkill2Desc, txtUltName, txtUltDesc;

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
        String videoPathLevel = "android.resource://" + getPackageName() + "/" + R.raw.bg_laval_video;
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

        // กันกล้องหน้า/มุมโค้งบังหน้าเลือกฮีโร่: padding ซ้าย-ขวาอย่างน้อย 24dp และไม่น้อยกว่า cutout ของแต่ละฝั่ง
        final int minHeroPad = Math.round(24 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(layoutHeroSelect, (v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            v.setPadding(Math.max(minHeroPad, cutout.left), v.getPaddingTop(),
                    Math.max(minHeroPad, cutout.right), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(layoutHeroSelect);

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
            btnBack.setOnClickListener(v -> backFromHeroSelect());
        }

        // ปุ่มย้อนกลับหน้าเลือกด่าน (กลับเมนูหลัก) กันกล้องหน้า/มุมโค้งเหมือนหน้าเลือกฮีโร่
        ImageButton btnBackLevel = findViewById(R.id.btnBackLevel);
        if (btnBackLevel != null) {
            btnBackLevel.setOnClickListener(v -> backFromLevelSelect());
            ViewCompat.setOnApplyWindowInsetsListener(btnBackLevel, (v, insets) -> {
                Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                lp.setMarginStart(Math.max(minHeroPad, cutout.left));
                v.setLayoutParams(lp);
                return insets;
            });
            ViewCompat.requestApplyInsets(btnBackLevel);
        }

        // ปุ่ม/ท่าทางย้อนกลับของระบบ: ใช้ตัวช่วยตัวเดียวกับปุ่มบนหน้าจอ
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutHeroSelect.getVisibility() == View.VISIBLE) {
                    backFromHeroSelect();
                } else if (layoutLevelSelect.getVisibility() == View.VISIBLE) {
                    backFromLevelSelect();
                } else {
                    setEnabled(false);   // หน้าเมนูหลัก: ให้ระบบปิดแอปตามปกติ
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

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
        txtSkill1Name = findViewById(R.id.txtSkill1Name);
        txtSkill1Desc = findViewById(R.id.txtSkill1Desc);
        txtSkill2Name = findViewById(R.id.txtSkill2Name);
        txtSkill2Desc = findViewById(R.id.txtSkill2Desc);
        txtUltName = findViewById(R.id.txtUltName);
        txtUltDesc = findViewById(R.id.txtUltDesc);
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
        // ปลดล็อกทีละด่าน: เริ่มที่ด่าน 1 ผ่านด่านไหนแล้วด่านถัดไปถึงจะเปิด (ไอคอนอัปเดตใน updateStageLocks)
        int[] stageButtonIds = {R.id.btnStage1, R.id.btnStage2, R.id.btnStage3, R.id.btnStage4, R.id.btnStage5};
        for (int i = 0; i < stageButtonIds.length; i++) {
            final int stageId = i + 1;
            ImageButton btnStage = findViewById(stageButtonIds[i]);
            btnStage.setOnClickListener(v -> {
                if (stageId > GameProgress.getUnlockedStage(this)) return;   // ด่านล็อก: ไม่ทำอะไร
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

    /** หน้าเลือกฮีโร่ -> หน้าเลือกด่าน */
    private void backFromHeroSelect() {
        SoundManager.play(SoundManager.Sfx.BUTTON);
        findViewById(R.id.layoutHeroSelect).setVisibility(View.GONE); // ซ่อนหน้าเลือกตัวละคร
        layoutLevelSelect.setVisibility(View.VISIBLE); // แสดงหน้าเลือกด่านอีกครั้ง
        videoBackgroundLevel.start(); // เล่นวิดีโอด่านต่อ
    }

    /** หน้าเลือกด่าน -> เมนูหลัก */
    private void backFromLevelSelect() {
        SoundManager.play(SoundManager.Sfx.BUTTON);
        layoutLevelSelect.setVisibility(View.GONE);
        videoBackgroundLevel.pause();
        layoutMainMenu.setVisibility(View.VISIBLE);
        videoBackground.start();
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
        updateStageLocks();

        // กลับมาจากหน้าอื่น (เช่น ตั้งค่า): พื้นผิววิดีโอของ VideoView ถูกทำลายตอนออกจากหน้านี้
        // ถ้าไม่โหลดใหม่ พื้นหลังจะเป็นสีดำ จึงตั้งวิดีโอใหม่แล้วเล่นของหน้าที่แสดงอยู่
        if (leftScreen) {
            leftScreen = false;
            if (layoutMainMenu.getVisibility() == View.VISIBLE) {
                reloadVideo(videoBackground, R.raw.bg_video);
            } else if (layoutLevelSelect.getVisibility() == View.VISIBLE) {
                reloadVideo(videoBackgroundLevel, R.raw.bg_laval_video);
            }
        }
    }

    private void reloadVideo(VideoView video, int rawRes) {
        video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + rawRes));
        video.start();
    }

    /** ด่านที่ยังไม่ปลดล็อกใช้ไอคอนกุญแจและจางลง ด่านที่เล่นได้ใช้ไอคอนปกติ (ด่าน 5 = บอส) */
    private void updateStageLocks() {
        int[] buttonIds = {R.id.btnStage1, R.id.btnStage2, R.id.btnStage3, R.id.btnStage4, R.id.btnStage5};
        int[] openIcons = {R.drawable.ic_whirlpool_green, R.drawable.ic_whirlpool_blue,
                R.drawable.ic_whirlpool_blue, R.drawable.ic_whirlpool_blue, R.drawable.ic_boss};
        int unlocked = GameProgress.getUnlockedStage(this);
        for (int i = 0; i < buttonIds.length; i++) {
            ImageButton btn = findViewById(buttonIds[i]);
            if (btn == null) continue;
            boolean open = (i + 1) <= unlocked;
            btn.setImageResource(open ? openIcons[i] : R.drawable.ic_whirlpool_lock);
            btn.setAlpha(open ? 1f : 0.6f);
        }
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

        // 3.1 ชื่อและคำอธิบายสกิล 1 / สกิล 2 / Ultimate ดึงจากคลาสฮีโร่ตัวนั้นโดยตรง จึงตรงกับสกิลจริงเสมอ
        // (ป้าย Skill 1 / Skill 2 / Ultimate Skill เป็นข้อความตายตัวใน layout)
        if (txtSkill1Name != null) {
            Hero hero = HeroFactory.createHero(heroIndex);
            txtSkill1Name.setText(hero.getSkill1Name());
            txtSkill1Desc.setText(hero.getSkill1Description());
            txtSkill2Name.setText(hero.getSkill2Name());
            txtSkill2Desc.setText(hero.getSkill2Description());
            txtUltName.setText(hero.getUltimateName());
            txtUltDesc.setText(hero.getUltimateDescription());
        }

        // 4. ปลดล็อคปุ่ม FIGHT! และคืนความเข้มปุ่มเป็น 100%
        btnFight.setEnabled(true);
        btnFight.setAlpha(1.0f);
    }
}