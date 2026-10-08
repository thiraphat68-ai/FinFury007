// =====================================================================================
// [คนที่ 3 - View หน้าจอและการเปลี่ยนหน้า]  ไฟล์: MainActivity.java  (367 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/MainActivity.java
// หน้าตา (layout) ที่ไฟล์นี้ควบคุม: app/src/main/res/layout/activity_main.xml
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   หน้าแรกของแอป (เป็น Activity ที่เปิดก่อน ตั้งใน AndroidManifest.xml) รวม "3 หน้า" ไว้ใน Activity เดียว
//   โดยสลับการแสดง/ซ่อน (setVisibility) ทีละหน้า ไม่ได้เปิดหน้าใหม่:
//      หน้า 1  layoutMainMenu     เมนูหลัก: ปุ่ม "เริ่มเกม" และ "ตั้งค่า"
//      หน้า 2  layoutLevelSelect  เลือกด่าน (น้ำวน 5 ด่าน + ดาวที่ได้ + กุญแจถ้ายังล็อก)
//      หน้า 3  layoutHeroSelect   เลือกฮีโร่ (การ์ด 5 ใบ + รายละเอียดสกิล + ปุ่ม FIGHT!)
//   กด FIGHT! แล้วเปิด BattleActivity พร้อมส่ง HERO_ID และ STAGE_ID ทาง Intent
//
// [ลำดับเปลี่ยนหน้า]
//   เมนูหลัก --(เริ่มเกม)--> เลือกด่าน --(กดด่านที่ปลดล็อก)--> เลือกฮีโร่ --(FIGHT!)--> BattleActivity
//   ย้อนกลับ: เลือกฮีโร่ -> เลือกด่าน -> เมนูหลัก -> ออกแอป (ทั้งปุ่มบนจอและปุ่ม Back ของเครื่อง)
//   กลับจากการสู้ (ชนะ/แพ้ กด "เลือกด่าน") BattleActivity ส่ง SHOW_LEVEL_SELECT=true เพื่อข้ามเมนูหลัก
//
// [จะ "เพิ่มปุ่ม/หน้าใหม่" ในเมนู ยังไง]
//   1) เพิ่มปุ่มใน activity_main.xml ใน layoutMainMenu (ให้ id ใหม่ เช่น btnQuizMode)
//   2) ในไฟล์นี้ findViewById แล้ว setOnClickListener เหมือนปุ่ม btnSettings (ท้าย onCreate)
//   3) ถ้าเป็นหน้าใหม่แบบแยก Activity: สร้างคลาสใหม่ + ลงทะเบียนใน AndroidManifest.xml
//
// [จะ "เพิ่มด่าน/ฮีโร่" ต้องแก้ตรงไหนบ้าง]
//   ด่านใหม่: เพิ่มปุ่มด่านใน activity_main.xml + เพิ่ม id ในอาร์เรย์ stageButtonIds / buttonIds / starViews + StageConfig.java + MAX_STAGES
//   ฮีโร่ใหม่: เพิ่มการ์ดใน activity_main.xml + เพิ่มใน heroCards + เพิ่ม case ใน selectHero + HeroFactory
// =====================================================================================
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

// [ส่วนหัวคลาส] extends BaseActivity = ได้โหมดเต็มจอ (ซ่อนแถบระบบ) จาก BaseActivity.java
public class MainActivity extends BaseActivity {

    // [ตัวแปรสถานะการเลือก]
    //   selectedHeroId = ฮีโร่ที่เลือก (0 = ยังไม่เลือก , 1..5) | selectedStageId = ด่านที่เลือก (ส่งต่อให้ BattleActivity)
    //   heroCards = อาร์เรย์การ์ดฮีโร่ 5 ใบ ไว้เปลี่ยนสีไฮไลต์
    // ตัวแปรเก็บตัวละครที่ถูกเลือก (0 = ยังไม่ได้เลือก, 1-5 คือตัวละคร)
    private int selectedHeroId = 0;
    private int selectedStageId = 1; // ด่านที่เลือก ส่งไป BattleActivity เพื่อบันทึกการปลดล็อกด่านถัดไปให้ถูกด่าน
    private LinearLayout[] heroCards;

    // [View ที่โชว์รายละเอียดฮีโร่ที่เลือก] รูปใหญ่ ชื่อ วิชา คำอธิบาย และชื่อ+คำอธิบายสกิลทั้ง 3 (ผูกใน onCreate)
    // ตัวแปรสำหรับแสดงรายละเอียดตัวละครด้านล่าง
    private ImageView imgSelectedHero;
    private TextView txtSelectedName;
    private TextView txtSelectedSubject;
    // ชื่อ/คำอธิบายสกิลของฮีโร่ที่เลือก (แยกเป็น view ต่อสกิล)
    private TextView txtSkill1Name, txtSkill1Desc, txtSkill2Name, txtSkill2Desc, txtUltName, txtUltDesc;

    // [ตัวแปรวิดีโอและหน้า] videoBackground = วิดีโอเมนูหลัก | videoBackgroundLevel = วิดีโอหน้าเลือกด่าน
    //   leftScreen = เคยออกจากหน้านี้ไหม (ไว้โหลดวิดีโอใหม่ตอนกลับมา เพราะ VideoView จะดำถ้าไม่โหลดใหม่)
    // วิดีโอพื้นหลังและหน้าจอ เก็บเป็นฟิลด์ เพื่อกู้วิดีโอกลับมาตอนกลับจากหน้าอื่น (เช่น ตั้งค่า)
    private VideoView videoBackground;
    private VideoView videoBackgroundLevel;
    private View layoutMainMenu;
    private View layoutLevelSelect;
    private boolean leftScreen = false;
    private TextView txtSelectedDesc;

    // [onCreate] จุดเริ่มต้นหน้าแรก ทำตามลำดับ:
    //   ตั้ง layout -> จัดขอบจอ (insets) -> ตั้งวิดีโอ 2 ตัว -> ผูกปุ่มสลับหน้า -> ผูกปุ่มย้อนกลับ -> ผูกการ์ดฮีโร่ -> ผูกปุ่มด่าน
    //   -> ผูกปุ่ม FIGHT -> ผูกปุ่มตั้งค่า -> ถ้ามาจากฉากต่อสู้ ข้ามไปหน้าเลือกด่าน
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SoundManager.init(this);
        // [วาดเต็มจอ] EdgeToEdge ให้เนื้อหาวาดทับพื้นที่แถบระบบ; ด้านล่างจึงต้องใส่ padding กันของบัง
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // [กันแถบระบบบัง] เมื่อระบบบอกขนาดแถบสถานะ/นำทาง (insets) ให้ใส่ padding ที่กล่องหลัก (id main) เท่ากัน
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ==========================================
        // 🎬 1. จัดการวิดีโอพื้นหลัง หน้าที่ 1 (เมนูหลัก)
        // ==========================================
        // [วิดีโอพื้นหลังหน้าเมนู] โหลดไฟล์ res/raw/bg_video.mp4 ผ่านที่อยู่แบบ android.resource://...
        //   setLooping(true) = เล่นวนซ้ำ , setVolume(0,0) = ปิดเสียงวิดีโอไม่ให้ตีกับเพลงเกม
        //   [เปลี่ยนวิดีโอ] วางไฟล์ใหม่ใน res/raw/ แล้วแก้ R.raw.bg_video เป็นชื่อไฟล์ใหม่ (ห้ามมีตัวพิมพ์ใหญ่หรือเว้นวรรคในชื่อไฟล์)
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
        // [วิดีโอพื้นหลังหน้าเลือกด่าน] เหมือนด้านบน ใช้ bg_laval_video.mp4 (ยังไม่ start จนกว่าจะเข้าหน้านั้น)
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
        // [ผูก 3 หน้า] สามกล่องหลักใน activity_main.xml (ซ้อนกัน แสดงทีละกล่อง)
        layoutMainMenu = findViewById(R.id.layoutMainMenu);
        layoutLevelSelect = findViewById(R.id.layoutLevelSelect);
        View layoutHeroSelect = findViewById(R.id.layoutHeroSelect);

        // [กันกล้องหน้า/มุมโค้ง] หน้าเลือกฮีโร่ใส่ padding ซ้าย-ขวาอย่างน้อย 24dp และไม่น้อยกว่าขนาด cutout (รอยบากกล้อง) ของแต่ละฝั่ง
        //   [แก้ยังไง] ถ้ายังโดนบัง เพิ่มเลข 24
        // กันกล้องหน้า/มุมโค้งบังหน้าเลือกฮีโร่: padding ซ้าย-ขวาอย่างน้อย 24dp และไม่น้อยกว่า cutout ของแต่ละฝั่ง
        final int minHeroPad = Math.round(24 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(layoutHeroSelect, (v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            v.setPadding(Math.max(minHeroPad, cutout.left), v.getPaddingTop(),
                    Math.max(minHeroPad, cutout.right), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(layoutHeroSelect);

        // [ปุ่ม เริ่มเกม] เล่นเสียงปุ่ม -> ซ่อนเมนูหลัก + หยุดวิดีโอเมนู -> แสดงหน้าเลือกด่าน + เริ่มวิดีโอหน้าเลือกด่าน
        //   [เพิ่มปุ่มอื่นแบบเดียวกัน] คัดลอกบล็อกนี้ แล้วเปลี่ยนสิ่งที่เกิดเมื่อกด
        // กดปุ่ม Start Game (จากหน้า 1 ไปหน้า 2)
        Button btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> {
            SoundManager.play(SoundManager.Sfx.BUTTON);
            layoutMainMenu.setVisibility(View.GONE);
            videoBackground.pause();

            layoutLevelSelect.setVisibility(View.VISIBLE);
            videoBackgroundLevel.start();
        });

        // [ปุ่มย้อนกลับในหน้าเลือกฮีโร่] เรียก backFromHeroSelect()
        // ==========================================
        // 🔙 ปุ่มย้อนกลับ (จากหน้า 3 กลับไปหน้า 2)
        // ==========================================
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> backFromHeroSelect());
        }

        // [ปุ่มย้อนกลับในหน้าเลือกด่าน] เรียก backFromLevelSelect() พร้อมดัน margin ซ้ายหนีกล้องหน้าเหมือนด้านบน
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

        // [ปุ่ม Back ของเครื่อง] ดูว่าหน้าไหนแสดงอยู่ แล้วย้อนทีละหน้า; ถ้าอยู่เมนูหลัก ปิดตัว callback นี้แล้วให้ระบบปิดแอปตามปกติ
        //   [เพิ่มหน้าใหม่] ถ้ามีหน้าใหม่ต้องเพิ่ม else if ที่นี่
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

        // [ระบบเลือกฮีโร่] btnFight = ปุ่ม FIGHT!
        // ==========================================
        // ⚔️ 4. ระบบเลือกตัวละคร (Hero Highlight & Details)
        // ==========================================
        Button btnFight = findViewById(R.id.btnFight);

        // [การ์ดฮีโร่ 5 ใบ] cardHero1..5 เก็บลงอาร์เรย์ heroCards ตามลำดับ (ตรงกับเลขฮีโร่ 1..5)
        // ผูกตัวแปรการ์ดตัวละครทั้ง 5
        LinearLayout cardHero1 = findViewById(R.id.cardHero1);
        LinearLayout cardHero2 = findViewById(R.id.cardHero2);
        LinearLayout cardHero3 = findViewById(R.id.cardHero3);
        LinearLayout cardHero4 = findViewById(R.id.cardHero4);
        LinearLayout cardHero5 = findViewById(R.id.cardHero5);

        heroCards = new LinearLayout[]{cardHero1, cardHero2, cardHero3, cardHero4, cardHero5};

        // [ผูก View รายละเอียดฮีโร่ที่เลือก] ตาม id ใน activity_main.xml
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

        // [เลือกฮีโร่เริ่มต้น] selectHero(1) = Swordfish และเปิดปุ่ม FIGHT
        // ตั้งค่าให้เลือกตัวละครแรก (Swordfish) เป็นค่าเริ่มต้น
        selectHero(1, btnFight);

        // [คลิกการ์ดฮีโร่] วนทุกการ์ด เล่นเสียงปุ่มแล้ว selectHero(เลขฮีโร่)
        // ตั้งค่า Event การคลิกให้การ์ดแต่ละตัว
        for (int i = 0; i < heroCards.length; i++) {
            final int heroIndex = i + 1; // 1 = Swordfish, 2 = Pufferfish, ...
            heroCards[i].setOnClickListener(v -> {
                SoundManager.play(SoundManager.Sfx.BUTTON);
                selectHero(heroIndex, btnFight);
            });
        }

        // [ปุ่มด่าน] stageButtonIds = id ปุ่มด่าน 5 ปุ่ม
        //   กดด่านที่ยังล็อก (stageId มากกว่าด่านที่ปลดล็อกแล้ว) = ไม่ทำอะไร
        //   กดด่านที่เปิด = จำเลขด่าน -> ซ่อนหน้าเลือกด่าน -> แสดงหน้าเลือกฮีโร่ (รีเซ็ตเลือกฮีโร่ตัวแรก)
        //   การปลดล็อก: GameProgress.getUnlockedStage (คนที่ 5) นับจากดาวที่เคยได้
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

        // [ปุ่ม FIGHT!] เปิด BattleActivity ด้วย Intent ใส่ HERO_ID และ STAGE_ID (BattleActivity อ่านใน onCreate ชื่อต้องตรงกัน)
        //   [เพิ่มข้อมูลส่งไปฉากต่อสู้] intent.putExtra("ชื่อ", ค่า) แล้วอ่านที่ BattleActivity ด้วย getIntent().getXxxExtra
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

        // [ปุ่มตั้งค่า] เปิด SettingsActivity (หน้าแยก)
        // ปุ่มตั้งค่าในเมนูหลัก
        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            SoundManager.play(SoundManager.Sfx.BUTTON);
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        // [ข้ามเมนูหลัก] ถ้า Intent มี SHOW_LEVEL_SELECT=true ให้แสดงหน้าเลือกด่านทันที (สลับหน้าเหมือนปุ่มเริ่มเกม)
        // กลับมาจากด่าน (ชนะ/แพ้) -> ข้ามเมนูหลัก ไปหน้าเลือกด่านเลย
        if (getIntent().getBooleanExtra("SHOW_LEVEL_SELECT", false)) {
            layoutMainMenu.setVisibility(View.GONE);
            videoBackground.pause();
            layoutLevelSelect.setVisibility(View.VISIBLE);
            videoBackgroundLevel.start();
        }
    }

    // [backFromHeroSelect] ซ่อนหน้าเลือกฮีโร่ แสดงหน้าเลือกด่าน เล่นวิดีโอด่านต่อ
    /** หน้าเลือกฮีโร่ -> หน้าเลือกด่าน */
    private void backFromHeroSelect() {
        SoundManager.play(SoundManager.Sfx.BUTTON);
        findViewById(R.id.layoutHeroSelect).setVisibility(View.GONE); // ซ่อนหน้าเลือกตัวละคร
        layoutLevelSelect.setVisibility(View.VISIBLE); // แสดงหน้าเลือกด่านอีกครั้ง
        videoBackgroundLevel.start(); // เล่นวิดีโอด่านต่อ
    }

    // [backFromLevelSelect] ซ่อนหน้าเลือกด่านพร้อมหยุดวิดีโอ แสดงเมนูหลักและเล่นวิดีโอเมนูต่อ
    /** หน้าเลือกด่าน -> เมนูหลัก */
    private void backFromLevelSelect() {
        SoundManager.play(SoundManager.Sfx.BUTTON);
        layoutLevelSelect.setVisibility(View.GONE);
        videoBackgroundLevel.pause();
        layoutMainMenu.setVisibility(View.VISIBLE);
        videoBackground.start();
    }

    // [onPause] ออกจากหน้านี้: จดว่าเคยออก (leftScreen) และหยุดเพลง
    @Override
    protected void onPause() {
        super.onPause();
        leftScreen = true;
        SoundManager.pauseMusic();
    }

    // [onResume] กลับมาหน้านี้: เล่นเพลงเมนู "bgm_menu" , อัปเดตดาวและกุญแจ (เพราะอาจเพิ่งชนะด่าน)
    //   ถ้าเคยออกจากหน้านี้ โหลดวิดีโอของหน้าที่แสดงอยู่ใหม่ (แก้ปัญหาพื้นหลังดำ)
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

    // [reloadVideo] ตั้งที่อยู่วิดีโอใหม่แล้วเล่น
    private void reloadVideo(VideoView video, int rawRes) {
        video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + rawRes));
        video.start();
    }

    // [updateStageLocks] ด่านที่ปลดล็อกใช้ไอคอนปกติ (ด่าน 1 เขียว, 2-4 น้ำเงิน, ด่าน 5 ไอคอนบอส) ด่านที่ล็อกใช้ไอคอนกุญแจ ความโปร่งใส 0.6
    //   [เปลี่ยนไอคอน] แก้อาร์เรย์ openIcons (ไฟล์อยู่ res/drawable-nodpi/)
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

    // [updateStageStars] เขียนดาวที่ดีที่สุดของแต่ละด่านใต้ปุ่มด่าน (ข้อความดาวจาก GameProgress.starsText)
    /** ดาวที่ดีที่สุดของแต่ละด่านใต้ปุ่มด่านในหน้าเลือกด่าน */
    private void updateStageStars() {
        int[] starViews = {R.id.txtStars1, R.id.txtStars2, R.id.txtStars3, R.id.txtStars4, R.id.txtStars5};
        for (int i = 0; i < starViews.length; i++) {
            TextView tv = findViewById(starViews[i]);
            if (tv != null) tv.setText(GameProgress.starsText(GameProgress.getStars(this, i + 1)));
        }
    }

    // [selectHero] ฟังก์ชันเมื่อเลือกฮีโร่ ทำ 4 อย่าง:
    //   1) คืนสีการ์ดทุกใบเป็นสีปกติ #1A252C   2) ไฮไลต์การ์ดที่เลือกเป็นสี #00ADB5
    //   3) เปลี่ยนรูปใหญ่/ชื่อ/วิชา/คำอธิบายตามเลขฮีโร่ (switch ด้านล่าง)
    //   3.1) ดึงชื่อและคำอธิบายสกิลจากคลาสฮีโร่ตรง ๆ ผ่าน HeroFactory จึงตรงกับสกิลจริงเสมอ (แก้ที่ไฟล์ฮีโร่ ไม่ต้องแก้ที่นี่)
    //   4) เปิดปุ่ม FIGHT
    //   [แก้ยังไง] เปลี่ยนสีไฮไลต์: แก้ Color.parseColor | เปลี่ยนข้อความแนะนำฮีโร่: แก้ txtSelectedDesc.setText ใน case นั้น
    //   [เพิ่มฮีโร่] เพิ่ม case ใหม่ในสวิตช์ (รูป ชื่อ วิชา คำอธิบาย) และเพิ่มการ์ดใน heroCards
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