// *** ไฟล์ตัวอย่างเฉพาะส่วนของ คนที่ 3 (View: HUD / overlay / หน้าผลลัพธ์) ***
// ตัดส่วนของคนอื่นออกเพื่อให้อ่านง่าย ไฟล์นี้ใช้ build ไม่ได้ ให้ดูโค้ดเต็มที่ _full_annotated/BattleActivity.java
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/BattleActivity.java

// =====================================================================================
// ไฟล์: BattleActivity.java  (ต้นฉบับ 1,135 บรรทัด)  *** ไฟล์นี้ "ใช้ร่วมกัน" 2 คน ***
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/BattleActivity.java
//
// สำเนานี้เพิ่มคอมเมนต์อธิบาย โค้ดเหมือนไฟล์จริงทุกตัวอักษร (ตรวจด้วย tools/verify_same_code.py)
// ทุกส่วนมีป้ายบอกเจ้าของ:
//     //@@P1  = ส่วนของ "คนที่ 1 Controller" (กติกา / game loop / จอยสติ๊ก / ปุ่มสกิล / ชนะ-แพ้)
//     //@@P3  = ส่วนของ "คนที่ 3 View"       (HUD / หลอดเลือด / overlay / หน้าชนะ-แพ้ / เปลี่ยนหน้า)
//     //@@END = จบส่วนนั้น
// ไฟล์แยกรายคนอยู่ที่  person1_controller/BattleActivity_P1_rules.java
//                       person3_view/BattleActivity_P3_hud.java   (สร้างจากไฟล์นี้อัตโนมัติ)
//
// [ไฟล์นี้คืออะไร]
//   หน้าจอ "ฉากต่อสู้" ทั้งหมด: รับ HERO_ID/STAGE_ID จากหน้าเมนู -> สร้างฮีโร่ + ศัตรู -> วนเกมทุกเฟรม
//   -> ผู้เล่นขยับ/ใช้สกิล -> สะสมสแตก -> สแตกเต็มขึ้นโจทย์ quiz -> ตอบถูกปล่อย Ultimate -> ชนะ/แพ้
//   เป็น "ตัวกลาง" เชื่อม ฮีโร่(คน2) ศัตรู(คน4) ข้อมูลด่าน/เซฟ/quiz/ไอเทม(คน5) และหน้าจอ(คน3)
//
// [แผนที่ไฟล์แบบเร็ว]  (ค้นหาด้วย Ctrl+F ชื่อหัวข้อในวงเล็บ)
//   (A) ฟิลด์ตัวแปรทั้งหมด                                  [P1 + ฟิลด์ HUD ของ P3]
//   (B) เมธอด BattleContext ที่ฮีโร่/ศัตรูเรียก             [P1 เป็นหลัก, HUD เหนือหัว/หลอด Ult เป็น P3]
//   (C) onCreate: ประกอบฉากทั้งหมด                          [P1]
//   (D) game loop: frameCallback / updateFish                [P1]
//   (E) วางศัตรู / คูลดาวน์ / สแตก / รับดาเมจ                [P1]
//   (F) HUD: พื้นหลัง HP ข้อความด่าน                         [P3]
//   (G) ดาว / สอนเล่น / เมนูหยุด / ผลลัพธ์ / เปลี่ยนหน้า      [P3 (ดาวและเช็กชนะเป็น P1)]
//   (H) quiz ↔ Ultimate (setupQuiz)                          [P1]
//   (I) ปุ่ม ULT / รูปฮีโร่ / วงจรชีวิต Activity             [P3 / P1]
//
// [วิธีแก้/เพิ่มที่พบบ่อย ดูหัวข้อ "แก้ยังไง / เพิ่มยังไง" ใต้แต่ละเมธอด]
// =====================================================================================
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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// [คลาสนี้] extends BaseActivity  = ได้ฟีเจอร์ซ่อนแถบระบบ (เต็มจอ) จาก BaseActivity.java (คนที่ 3)
//           implements BattleContext = สัญญาให้ฮีโร่/ศัตรูเรียกฉากนี้ได้ (ดู BattleContext.java ของคนที่ 1)
//           implements ItemManager.Host = สัญญาให้ระบบไอเทม (คนที่ 5) เรียกกลับมาฟื้นเลือด/เพิ่มสแตก/แช่แข็งศัตรู
public class BattleActivity extends BaseActivity implements BattleContext, ItemManager.Host {

    // ============================ (A) ฟิลด์ตัวแปร ============================

    // ------------------ ส่วนที่ 1 ของคนที่ 3 (ในไฟล์เต็ม) ------------------
    // [ส่วนนี้คืออะไร] HUD เล็ก ๆ ที่สกิลสั่งให้โชว์: หลอดนับถอยหลังของ Ultimate, ตัวเลข "+3" เหนือหัว, หลอดชาร์จเหนือหัว
    // [View ที่ใช้] id อยู่ใน activity_battle.xml: layoutUltTimer, barUltTime, txtUltTimer, txtBonusDamage, barPlayerCharge
    private View layoutUltTimer;
    private ProgressBar barUltTime;
    private TextView txtUltTimer;
    private float ultTimerRemainingMs = 0f;
    private long ultTimerTotalMs = 1;
    private TextView txtBonusDamage;
    private ProgressBar barPlayerCharge;

    // [ทำอะไร] เริ่มนับถอยหลังหลอดเวลา Ultimate  (สกิล Ultimate ที่มีเวลาเรียก ctx.showUltimateDuration(ms))
    // [แก้ยังไง] อยากเปลี่ยนสีหลอด/ตำแหน่ง ให้แก้ใน activity_battle.xml (layoutUltTimer) ไม่ใช่ที่นี่
    @Override
    public void showUltimateDuration(long durationMs) {
        ultTimerTotalMs = Math.max(1, durationMs);
        ultTimerRemainingMs = durationMs;
        lastUltTenths = -1;
        lastUltBarProgress = -1;
        updateUltimateTimerUI();
        layoutUltTimer.setVisibility(View.VISIBLE);
    }

    // [ทำอะไร] ซ่อนหลอดเวลา + ล้างเวลาที่เหลือ (เรียกตอน Ultimate จบ/ถูกยกเลิก/ด่านจบ)
    @Override
    public void hideUltimateDuration() {
        ultTimerRemainingMs = 0f;
        layoutUltTimer.setVisibility(View.GONE);
    }

    // [ทำอะไร] ลดเวลาที่เหลือตามเวลาจริงของเฟรม (dtSec วินาที) เรียกจาก game loop ทุกเฟรม (เฉพาะตอนไม่หยุดเกม)
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
    // [เหตุผล] ประหยัดแรงเครื่อง: ไม่สร้างสตริงใหม่ทุกเฟรม (60 ครั้ง/วิ) ใช้ StringBuilder เดิมซ้ำ
    private int lastUltTenths = -1;
    private int lastUltBarProgress = -1;
    private final StringBuilder ultTimerText = new StringBuilder(32);

    // [ทำอะไร] วาดหลอดเวลา (0..1000) และข้อความ "ชื่อ Ultimate 3.4s"
    // [แก้ยังไง] เปลี่ยนรูปแบบข้อความ: แก้ตรง append(...) ด้านล่าง  เช่น เปลี่ยน 's' เป็น " วิ"
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

    // [ทำอะไร] แสดงข้อความโบนัสดาเมจ เช่น "+3" เหนือหัวผู้เล่น (ฮีโร่ Swordfish สกิล Blood Frenzy เรียก)
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

    // [ทำอะไร] แสดงหลอดชาร์จเหนือหัว progress 0..1 (ถูกบีบให้อยู่ในช่วงด้วย max/min) แล้วคูณ 1000 ให้ตรงกับ max ของหลอด
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
    // [ทำอะไร] คำนวณตำแหน่งกึ่งกลางเหนือหัวผู้เล่น แล้ววางตัวเลข/หลอดตรงนั้น เรียกทุกเฟรมจาก game loop
    // [แก้ยังไง] อยากให้ลอยสูง/ต่ำขึ้น: เปลี่ยนค่า 4f (ตัวเลข) และ 8f (หลอด) ที่ใช้ลบจาก top
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
    // ------------------ ส่วนที่ 2 ของคนที่ 3 (ในไฟล์เต็ม) ------------------
    // [ทำอะไร] ตั้งภาพพื้นหลังตามเลขด่าน โดยค้นหาไฟล์ drawable ชื่อ bg_stage_<เลขด่าน> (ไม่ต้องแก้โค้ด)
    // [เพิ่มยังไง] เพิ่มพื้นหลังด่านใหม่: ใส่ไฟล์ภาพชื่อ bg_stage_6.png ใน app/src/main/res/drawable/
    //             ถ้าไม่มีไฟล์ จะใช้ภาพสำรอง bg_level_video
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

    // [ทำอะไร] เขียนตัวเลขเลือด "70/100" ข้างหลอดเลือด (txtHpValue) เรียกทุกครั้งที่เลือดเปลี่ยน
    // [แก้ยังไง] เปลี่ยนรูปแบบข้อความ: แก้ "%d/%d" เช่น "HP: %d/%d"
    private void updateHpUI() {
        TextView txtHpValue = findViewById(R.id.txtHpValue);
        if (txtHpValue != null) {
            txtHpValue.setText(String.format(Locale.US, "%d/%d", playerHp, maxPlayerHp));
        }
    }

    // [ทำอะไร] เขียนข้อความหัวด่าน: "ด่าน N | ศัตรูเหลือ X ตัว" หรือ "ด่าน N | บอสเหลือ X ชีวิต"
    //          ต่อท้าย "⚠ ULT -30%" เมื่อกำลังติดดีบัฟลดพลัง Ultimate
    // [แก้ยังไง] เปลี่ยนข้อความ: แก้สตริงภาษาไทยใน String.format ด้านล่าง
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
    // ------------------ ส่วนที่ 3 ของคนที่ 3 (ในไฟล์เต็ม) ------------------
    // [ทำอะไร] โชว์หน้าสอนเล่น "ครั้งแรกที่เข้าด่าน 1 เท่านั้น" (จำด้วย SharedPreferences คีย์ tutorial_seen)
    //          ระหว่างโชว์เกมหยุด (isGamePaused = true) กดตกลงแล้วเกมเดินต่อ
    // [แก้ยังไง] อยากให้โชว์ทุกครั้ง: ลบเงื่อนไข prefs.getBoolean(...) | อยากโชว์ทุกด่าน: ลบเงื่อนไข currentStageId != 1
    //           ข้อความสอนเล่นแก้ใน activity_battle.xml (overlayTutorial)
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

    /** คู่มือไอเทมในเมนูหยุดเกม: สร้างแถวจาก ItemManager.Type เพื่อให้ตรงกับ enum เสมอ */
    // [ทำอะไร] วนทุกชนิดไอเทมใน ItemManager.Type สร้าง TextView 1 แถวต่อ 1 ชนิดใส่เมนูหยุด
    // [เพิ่มยังไง] เพิ่มไอเทมใหม่: เพิ่มค่าใน enum Type ของ ItemManager.java (คนที่ 5) คู่มือจะขึ้นเองอัตโนมัติ
    private void buildItemGuide() {
        LinearLayout guide = findViewById(R.id.layoutItemGuide);
        if (guide == null) return;
        guide.removeAllViews();
        int pad = Math.round(4 * getResources().getDisplayMetrics().density);
        for (ItemManager.Type type : ItemManager.Type.values()) {
            TextView row = new TextView(this);
            row.setText(type.label());
            row.setTextColor(Color.WHITE);
            row.setTextSize(13f);
            row.setPadding(0, pad, 0, pad);
            guide.addView(row);
        }
    }

    // =========================================================
    // Overlay: เมนูหยุดเกม / หน้าจอชนะ-แพ้
    // =========================================================
    // [ทำอะไร] ผูกปุ่มในเมนูหยุด (เล่นต่อ / เริ่มใหม่ / ออก) และปุ่ม Back ของเครื่อง
    //   Back: เมนูเปิดอยู่ = ปิดเมนู , เมนูปิดอยู่ = เปิดเมนู (เปิดไม่ได้ถ้ามี quiz/ผลลัพธ์ขึ้นอยู่ ดู openPauseMenu)
    // [เพิ่มยังไง] เพิ่มปุ่มในเมนูหยุด: เพิ่มปุ่มใน activity_battle.xml (overlayPause) แล้วผูก setOnClickListener ที่นี่
    private void setupOverlays() {
        overlayPause = findViewById(R.id.overlayPause);
        overlayResult = findViewById(R.id.overlayResult);

        findViewById(R.id.btnPauseResume).setOnClickListener(v -> closePauseMenu());
        findViewById(R.id.btnPauseRestart).setOnClickListener(v -> restartStage());
        findViewById(R.id.btnPauseExit).setOnClickListener(v -> returnToLevelSelect());
        buildItemGuide();

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
    // [เพิ่มยังไง] ถ้าสร้าง overlay ใหม่ ให้เพิ่มเงื่อนไข || ที่นี่ (และใน canOpenQuiz ด้านล่าง) กัน overlay ซ้อนกัน
    private boolean isOverlayOpen() {
        return (quizManager != null && quizManager.isShowing())
                || overlayPause.getVisibility() == View.VISIBLE
                || overlayResult.getVisibility() == View.VISIBLE
                || findViewById(R.id.overlayTutorial).getVisibility() == View.VISIBLE;
    }

    // [ทำอะไร] เปิดเมนูหยุด: หยุดเกม รีเซ็ตจอยสติ๊ก แล้วแสดง overlay (ไม่เปิดถ้าเกมจบหรือมี overlay อื่นอยู่)
    private void openPauseMenu() {
        if (!isGameRunning || isOverlayOpen()) return;
        isGamePaused = true;
        resetJoystick();
        overlayPause.setVisibility(View.VISIBLE);
    }

    // [ทำอะไร] ปิดเมนูหยุด เล่นต่อ และเปิดโจทย์ที่ค้างอยู่ (ถ้าสแตกเต็มระหว่างหยุด)
    private void closePauseMenu() {
        overlayPause.setVisibility(View.GONE);
        isGamePaused = false;
        openPendingQuiz();
    }
    // ------------------ ส่วนที่ 4 ของคนที่ 3 (ในไฟล์เต็ม) ------------------
    // [ทำอะไร] แสดงหน้าชนะ/แพ้ (overlayResult):
    //   ชนะ = หัวข้อเขียว + ดาว + ปุ่ม "ด่านถัดไป/เล่นอีกครั้ง/เลือกด่าน" (ด่านสุดท้ายไม่มีปุ่มด่านถัดไป)
    //   แพ้ = หัวข้อแดง + ปุ่ม "เล่นอีกครั้ง/เลือกด่าน"
    //   เริ่มด้วย cancelEverythingForStageEnd() ปิดทุกอย่างที่ค้างอยู่
    // [แก้ยังไง] เปลี่ยนข้อความ/สี: แก้สตริงและค่าสี 0xFF2ECC71 (เขียว) , 0xFFE74C3C (แดง)
    // [เพิ่มยังไง] เพิ่มปุ่มที่ 4: เพิ่มใน activity_battle.xml แล้วใช้ setResultButton ให้เหมือนปุ่มอื่น
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

    // [ทำอะไร] ตัวช่วยตั้งปุ่ม: แสดงปุ่ม ใส่ข้อความ และผูกคำสั่งที่จะทำเมื่อกด
    private static void setResultButton(Button b, String text, Runnable action) {
        b.setVisibility(View.VISIBLE);
        b.setText(text);
        b.setOnClickListener(v -> action.run());
    }

    // [ทำอะไร] ไปด่านถัดไป: เปิด BattleActivity ใหม่ด้วย STAGE_ID + 1 (ฮีโร่เดิม) แล้วปิดหน้านี้
    private void goToNextStage() {
        Intent intent = new Intent(this, BattleActivity.class);
        intent.putExtra("HERO_ID", currentHeroId);
        intent.putExtra("STAGE_ID", currentStageId + 1);
        startActivity(intent);
        finish();
    }

    // [ทำอะไร] กลับหน้าเลือกด่านใน MainActivity (ส่ง SHOW_LEVEL_SELECT=true ให้ MainActivity เปิดหน้าเลือกด่านเลย)
    private void returnToLevelSelect() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("SHOW_LEVEL_SELECT", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
    // ------------------ ส่วนที่ 5 ของคนที่ 3 (ในไฟล์เต็ม) ------------------
    // [ทำอะไร] อัปเดตหลอดสแตก (ความคืบหน้า) และข้อความ "ชื่อฮีโร่ | Stack: 3/10"
    @SuppressLint("SetTextI18n")
    private void updateStackUI() {
        if (stackProgressBar != null) stackProgressBar.setProgress(currentStack);
        if (txtStackGauge != null && playerHero != null) {
            txtStackGauge.setText(playerHero.getName() + " | Stack: " + currentStack + "/" + maxStack);
        }
    }

    /** ล็อก = จาง + กดไม่ได้, พร้อม = สว่าง + เด้งเตือน */
    // [ทำอะไร] เปลี่ยนหน้าตาปุ่ม ULT ตามสถานะ ultimateReady: พร้อม = ข้อความ "ULT!" สว่าง + เด้งขยาย 1.15 เท่า
    //          ยังไม่พร้อม = "🔒 ULT" จางและกดไม่ได้
    // [แก้ยังไง] เปลี่ยนข้อความ/ความจาง: แก้สตริงและค่า 0.4f
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

    // [ทำอะไร] ใส่รูปฮีโร่ (hero_1..hero_5) ให้ตัวผู้เล่นและโปรไฟล์ ตั้งความกว้างกล่องรูปรายตัว (สูง = กว้าง x 0.545)
    //          และเขียนชื่อฮีโร่
    // [เพิ่มยังไง] เพิ่มฮีโร่ตัวที่ 6: ใส่รูป hero_6 ใน res/drawable-nodpi/ แล้วเพิ่ม case 6 ในทั้ง 2 switch ด้านล่าง
    //             (และเพิ่มใน HeroFactory.java กับการ์ดฮีโร่ใน activity_main.xml)
    // [แก้ยังไง] ปลาตัวเล็ก/ใหญ่เกินไป: แก้ค่า widthDp ของ case นั้น
    private void setupHeroAndSkills(int heroId) {
        int heroDrawableId = R.drawable.hero_1;
        switch (heroId) {
            case 2: heroDrawableId = R.drawable.hero_2; break;
            case 3: heroDrawableId = R.drawable.hero_3; break;
            case 4: heroDrawableId = R.drawable.hero_4; break;
            case 5: heroDrawableId = R.drawable.hero_5; break;
        }
        if (imgPlayer != null) {
            imgPlayer.setImageResource(heroDrawableId);
            // ภาพฮีโร่เป็นแคนวาสกว้าง 677x369 (hero_5 1698x926) ที่มีขอบโปร่งใสเยอะ เดิมยัดในกล่อง 70dp จัตุรัส
            // ตัวปลาจริงเหลือแค่ ~25-60dp เล็กกว่าอีโมจิศัตรู: ตั้งความกว้างกล่องตามสัดส่วนตัวปลาในภาพแต่ละตัว
            float widthDp = 120f;
            switch (heroId) {
                case 2: widthDp = 200f; break;   // ปักเป้า: ตัวปลาในภาพกว้างแค่ 38% ของแคนวาส
                case 3: widthDp = 105f; break;   // ฉลาม: ตัวยาว
                case 4: widthDp = 145f; break;   // หมึก
                case 5: widthDp = 95f; break;    // ปลาไหล: ภาพเต็มแคนวาสอยู่แล้ว
            }
            float density = getResources().getDisplayMetrics().density;
            ViewGroup.LayoutParams lp = imgPlayer.getLayoutParams();
            lp.width = Math.round(widthDp * density);
            lp.height = Math.round(widthDp * 0.545f * density);   // อัตราส่วนภาพ 369/677 = 926/1698
            imgPlayer.setLayoutParams(lp);
        }
        TextView txtPlayerName = findViewById(R.id.txtPlayerName);
        if (txtPlayerName != null && playerHero != null) txtPlayerName.setText(playerHero.getName());
        if (imgProfile != null) imgProfile.setImageResource(heroDrawableId);
    }

    // [ทำอะไร] แอนิเมชันกดปุ่ม: ย่อเหลือ 85% 80 ms แล้วคืนขนาด
    private void animateButton(View button) {
        if (button == null) return;
        button.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction(() ->
                button.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        ).start();
    }
}
