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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    // ตัวแปรเก็บตัวละครที่ถูกเลือก (0 = ยังไม่ได้เลือก, 1-5 คือตัวละคร)
    private int selectedHeroId = 0;
    private LinearLayout[] heroCards;

    // ตัวแปรสำหรับแสดงรายละเอียดตัวละครด้านล่าง
    private ImageView imgSelectedHero;
    private TextView txtSelectedName;
    private TextView txtSelectedSubject;
    private TextView txtSelectedDesc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        VideoView videoBackground = findViewById(R.id.videoBackground);
        String videoPath = "android.resource://" + getPackageName() + "/" + R.raw.bg_video;
        videoBackground.setVideoURI(Uri.parse(videoPath));
        videoBackground.setOnPreparedListener(mp -> mp.setLooping(true));
        videoBackground.start();

        // ==========================================
        // 🎬 2. จัดการวิดีโอพื้นหลัง หน้าที่ 2 (เลือกด่าน)
        // ==========================================
        VideoView videoBackgroundLevel = findViewById(R.id.videoBackgroundLevel);
        String videoPathLevel = "android.resource://" + getPackageName() + "/" + R.raw.bg_level_video;
        videoBackgroundLevel.setVideoURI(Uri.parse(videoPathLevel));
        videoBackgroundLevel.setOnPreparedListener(mp -> mp.setLooping(true));

        // ==========================================
        // 🔄 ส่วนการสลับหน้าจอและการกดปุ่มหน้าแรก
        // ==========================================
        View layoutMainMenu = findViewById(R.id.layoutMainMenu);
        View layoutLevelSelect = findViewById(R.id.layoutLevelSelect);
        View layoutHeroSelect = findViewById(R.id.layoutHeroSelect);

        // กดปุ่ม Start Game (จากหน้า 1 ไปหน้า 2)
        Button btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> {
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
                layoutHeroSelect.setVisibility(View.GONE); // ซ่อนหน้าเลือกตัวละคร
                layoutLevelSelect.setVisibility(View.VISIBLE); // แสดงหน้าเลือกด่านอีกครั้ง
                videoBackgroundLevel.start(); // เล่นวิดีโอด่านต่อ
            });
        }

        // ==========================================
        // 🌀 3. จัดการปุ่มน้ำวน (หน้าเลือกด่าน)
        // ==========================================
        ImageButton btnStage1 = findViewById(R.id.btnStage1);
        ImageButton btnStage2 = findViewById(R.id.btnStage2);
        ImageButton btnStage3 = findViewById(R.id.btnStage3);
        ImageButton btnStage4 = findViewById(R.id.btnStage4);

        btnStage3.setEnabled(false);
        btnStage4.setEnabled(false);

        btnStage1.setOnClickListener(v -> {
            layoutLevelSelect.setVisibility(View.GONE);
            videoBackgroundLevel.pause();
            layoutHeroSelect.setVisibility(View.VISIBLE);
        });

        btnStage2.setOnClickListener(v -> {
            layoutLevelSelect.setVisibility(View.GONE);
            videoBackgroundLevel.pause();
            layoutHeroSelect.setVisibility(View.VISIBLE);
        });

        // ==========================================
        // ⚔️ 4. ระบบเลือกตัวละคร (Hero Highlight & Details)
        // ==========================================
        Button btnFight = findViewById(R.id.btnFight);

        // ล็อคปุ่ม FIGHT ไว้ก่อน และจางปุ่มลง 50%
        btnFight.setEnabled(false);
        btnFight.setAlpha(0.5f);

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
        txtSelectedDesc = findViewById(R.id.txtSelectedDesc);

        // ตั้งค่า Event การคลิกให้การ์ดแต่ละตัว
        for (int i = 0; i < heroCards.length; i++) {
            final int heroIndex = i + 1; // 1 = Swordfish, 2 = Pufferfish, ...
            heroCards[i].setOnClickListener(v -> selectHero(heroIndex, btnFight));
        }

        // ==========================================
        // 🥊 5. ปุ่ม FIGHT! (เมื่อพร้อมลุย)
        // ==========================================
        btnFight.setOnClickListener(v -> {
            if (selectedHeroId > 0) {
                // เปิดหน้า BattleActivity พร้อมส่งหมายเลขปลาไปด้วย
                Intent intent = new Intent(MainActivity.this, BattleActivity.class);
                intent.putExtra("HERO_ID", selectedHeroId);
                startActivity(intent);
            }
        });
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
                if (txtSelectedSubject != null) txtSelectedSubject.setText("แคลคูลัส (Calculus)");
                if (txtSelectedDesc != null) txtSelectedDesc.setText("ใช้ความแม่นยำในการคำนวณเวกเตอร์เพื่อพุ่งโจมตีศัตรูอย่างรวดเร็ว");
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
                if (txtSelectedSubject != null) txtSelectedSubject.setText("วงจรไฟฟ้า (Electrical Circuits)");
                if (txtSelectedDesc != null) txtSelectedDesc.setText("ใช้พลังงานไฟฟ้าในการโจมตี และควบคุมสนามไฟฟ้าได้");
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

        // 4. ปลดล็อคปุ่ม FIGHT! และคืนความเข้มปุ่มเป็น 100%
        btnFight.setEnabled(true);
        btnFight.setAlpha(1.0f);
    }
}