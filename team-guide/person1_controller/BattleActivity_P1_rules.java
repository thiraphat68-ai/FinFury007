// *** ไฟล์ตัวอย่างเฉพาะส่วนของ คนที่ 1 (Controller: กติกา / game loop / ชนะ-แพ้) ***
// ตัดส่วนของคนอื่นออกเพื่อให้อ่านง่าย ไฟล์นี้ใช้ build ไม่ได้ ให้ดูโค้ดเต็มที่ shared_BattleActivity/BattleActivity.java
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

    // ------------------ ส่วนที่ 1 ของคนที่ 1 (ในไฟล์เต็ม) ------------------
    // --- ตัวอ้างอิง View หลักในฉาก (ผูกใน onCreate ด้วย findViewById ตาม id ใน activity_battle.xml) ---
    // [แก้/เพิ่ม] ถ้าเพิ่ม View ใหม่ใน activity_battle.xml ให้ประกาศฟิลด์ที่นี่ แล้ว findViewById ใน onCreate
    private FrameLayout gameArea;        // พื้นที่เล่น (ศัตรู ปลา เอฟเฟกต์ ถูก addView ลงในนี้)
    private View playerContainer;        // กล่องที่หุ้มตัวปลาผู้เล่น (เลื่อนด้วย translationX/Y)
    private ImageView imgPlayer;         // รูปปลาของผู้เล่น (หมุน/กลับด้านตอนว่าย)
    private ImageView imgProfile;        // รูปโปรไฟล์ฮีโร่มุมจอ (HUD)
    private View joystickKnob;           // ปุ่มกลมของจอยสติ๊ก (ขยับตามนิ้ว)

    private ProgressBar barPlayerHp;       // หลอดเลือด
    private ProgressBar stackProgressBar;  // หลอดสแตกพลัง Ultimate
    private TextView txtStackGauge;        // ข้อความ "ชื่อฮีโร่ | Stack: 3/10"

    // --- ตัวแปรจอยสติ๊ก: moveX/moveY คือทิศที่ผู้เล่นอยากไป ค่า -1..1 (0 = นิ่ง) ---
    private float joystickCenterX, joystickCenterY;
    private float moveX = 0f, moveY = 0f;
    private float swimTime = 0f;           // ตัวนับเวลาไว้คำนวณท่าว่าย (โยกตัว)

    // --- ตัวแปรการเคลื่อนที่ของผู้เล่น ---
    // velX/velY = ความเร็วจริงตอนนี้ (px/วินาที) , faceScale = ทิศหัน (-1/1) ค่อย ๆ เปลี่ยนเพื่อให้กลับตัวนุ่ม , tilt = องศาเอียง
    private float velX, velY, faceScale = -1f, tilt;
    private long lastFrameNs = 0;          // เวลา (นาโนวินาที) ของเฟรมก่อนหน้า ใช้คำนวณ dt
    private boolean skillLock = false;     // true = ล็อกการเดินปกติระหว่างสกิล (ดู setSkillLock)
    private float slowFactor = 1f;               // สโลว์จากบอส (คูณกับ speedMultiplier)
    private float slowRemainingMs = 0f;
    private float stunRemainingMs = 0f;          // สตันจากศัตรู: เคลื่อนที่ไม่ได้
    private float stunImmuneMs = 0f;             // หายสตันแล้วกันสตันซ้ำชั่วครู่ ไม่งั้นโดนสตันต่อเนื่องจนขยับไม่ได้เลย
    // [แก้ได้] เวลากันสตันซ้ำหลังหายสตัน (มิลลิวินาที) เพิ่มค่า = ผู้เล่นปลอดภัยจากสตันนานขึ้น
    private static final float STUN_IMMUNE_AFTER_MS = 1500f;
    // [แก้ได้] เวลาอมตะหลังโดนตี (มิลลิวินาที) เพิ่มค่า = เกมง่ายขึ้น ใช้กันกระสุนรัวหลายนัดในเฟรมเดียว
    private static final long HIT_INVULN_MS = 350;
    private long lastDamageMs = 0;         // เวลาที่โดนตีครั้งล่าสุด (ไว้เช็กช่วงอมตะ)
    private float speedMultiplier = 1f;     // สกิลที่เพิ่มความเร็ว (เช่น Blood Frenzy)
    private float cooldownMultiplier = 1f;   // สกิลที่ลดคูลดาวน์
    // [แก้ได้] ความเร็วว่ายสูงสุดพื้นฐาน px/วินาที ของทุกฮีโร่ (ฮีโร่แต่ละตัวคูณเพิ่มด้วย getBaseSpeedMultiplier)
    static final float MAX_SPEED = 700f;
    private boolean isFacingRight = true;  // ตอนนี้หันขวาอยู่ไหม (ใช้กลับด้านรูป)

    // --- ข้อมูลผู้เล่นและด่านปัจจุบัน ---
    private Hero playerHero;               // ฮีโร่ที่เลือก (สร้างจาก HeroFactory) เรียกสกิลผ่านตัวนี้
    private int currentHeroId = 1;         // 1..5
    private int currentStageId = 1;        // 1..5
    private StageConfig stageConfig = StageConfig.forStage(1);   // ความยากของด่านนี้ (ดู StageConfig.java ของคนที่ 5)
    private int playerHp = 100;
    private int maxPlayerHp = 100;   // ตั้งตามฮีโร่ตอน onCreate
    private int currentStack = 0;          // สแตกพลังที่สะสมได้ตอนนี้
    private int maxStack = 10;       // ตั้งตามฮีโร่ตอน onCreate
    private float heroBaseSpeed = 1f;
    private ItemManager itemManager;       // ระบบไอเทมที่ดรอป (คนที่ 5) สร้างหลังจอวัดขนาดเสร็จ
    private TextView txtBuffs;             // ข้อความแสดงบัฟไอเทมที่ติดอยู่ (HUD)
    // [แก้ได้] จำนวนด่านทั้งหมด ถ้าเพิ่มด่านใหม่ต้องแก้ที่นี่ + StageConfig.java + ปุ่มด่านใน activity_main.xml
    private static final int MAX_STAGES = 5;

    // ดีบัฟลดพลัง Ultimate (ด่าน 3-4): ถูกตีครบทุก 2 ครั้ง ได้พลังต่อฮิต 70% นาน 8 วินาที
    // [แก้ได้] ระยะเวลาดีบัฟ ULT (มิลลิวินาที) ตัวเลขในคอมเมนต์เดิมเก่า ค่าจริงในโค้ดคือ 5000 = 5 วินาที
    private static final float ULT_DEBUFF_DURATION_MS = 5000f;
    // ด่าน 3+: ถูกตีทุก 3 ครั้ง พลังสะสม ULT ลด 20% ของหลอด (2 จาก 10)
    // [แก้ได้] 3 ค่านี้คุมกลไก "โดนตีแล้วสแตกลด": เริ่มตั้งแต่ด่านไหน / ทุกกี่ครั้ง / ลดกี่ % ของหลอด
    private static final int ENERGY_DRAIN_FROM_STAGE = 3;
    private static final int ENERGY_DRAIN_EVERY_HITS = 3;
    private static final int ENERGY_DRAIN_PERCENT = 20;
    private int hitsTaken = 0;                 // จำนวนครั้งที่ผู้เล่นโดนตีในด่านนี้
    private float ultDebuffRemainingMs = 0f;   // เวลาที่เหลือของดีบัฟ ULT (>0 = กำลังติดดีบัฟ)
    private float stackFraction = 0f;          // เศษสแตกสะสม (กรณีติดดีบัฟได้ 0.7 ต่อฮิต)

    private float playerAngle = 0f;            // มุมที่ผู้เล่นหัน (องศา) จากจอยสติ๊ก สกิลใช้ยิงไปทิศนี้

    private boolean isGameRunning = true;      // false = ด่านจบแล้ว (ชนะ/แพ้) ทุกระบบควรหยุด
    private boolean isGamePaused = false;      // true = หยุดชั่วคราว (เมนูหยุด / quiz / สอนเล่น)

    private final List<SeaEnemy> enemyList = new ArrayList<>();   // ศัตรูทั้งหมดในด่าน (คนที่ 4 เขียนคลาส SeaEnemy)

    // --- Quiz System (แยกไปอยู่ใน QuizManager.java) ---
    private QuizManager quizManager;       // ตัวจัดการโจทย์ (คนที่ 5) ผูก Listener ใน setupQuiz()

    // --- Overlay ต่างๆ (อยู่ใน activity_battle.xml) ---
    private View overlayPause;             // หน้าเมนูหยุดเกม
    private View overlayResult;            // หน้าชนะ/แพ้
    private String skill1Label = "Skill 1";   // ข้อความบนปุ่มสกิล (ตั้งใหม่จากชื่อสกิลของฮีโร่ใน onCreate)
    private String skill2Label = "Skill 2";

    // --- Ultimate Button (ใช้กับฮีโร่ที่ usesUltimateButton() = true) ---
    private Button btnUltimate;
    private boolean ultimateReady = false;   // ตอบ quiz ถูกแล้ว รอผู้เล่นกด
    private boolean ultimateRunning = false; // กำลังปล่อย Ultimate อยู่
    // ------------------ ส่วนที่ 2 ของคนที่ 1 (ในไฟล์เต็ม) ------------------
    @Override
    public Context getContext() { return this; }

    @Override
    public FrameLayout getGameArea() { return gameArea; }

    @Override
    public View getPlayerContainer() { return playerContainer; }

    @Override
    public float getPlayerAngle() { return playerAngle; }

    // [ทำอะไร] เปิด/ปิดล็อก: ตอน skillLock=true ผู้เล่นจะเดินเองไม่ได้ (ความเร็วเป้าหมาย = 0 และไม่ขยับตำแหน่ง)
    //          ใช้โดยสกิลพุ่ง/ชาร์จที่ขยับผู้เล่นเอง ดู updateFish
    @Override
    public void setSkillLock(boolean locked) { skillLock = locked; }

    // [ทำอะไร] คืนอัตราเร็วปัจจุบัน 0..1 = ความเร็วจริง / MAX_SPEED (ใช้ให้สกิลรู้ว่าผู้เล่นกำลังว่ายเร็วแค่ไหน)
    @Override
    public float getPlayerSpeedRatio() {
        return Math.min(1f, (float) Math.hypot(velX, velY) / MAX_SPEED);
    }

    // [ทำอะไร] ตั้งตัวคูณความเร็ว (1 = ปกติ) สกิลบัฟความเร็วเรียก แล้วต้องคืนเป็น 1f เมื่อสกิลจบ
    @Override
    public void setSpeedMultiplier(float multiplier) { speedMultiplier = multiplier; }

    // [ทำอะไร] ผลักผู้เล่นด้วยการ "บวกความเร็ว" เข้าไปตรง ๆ แล้วปล่อยให้ updateFish ดึงกลับเอง
    // [แก้ยังไง] อยากให้ผลักไกลขึ้น: เพิ่มค่า speed ตอนเรียกจากฝั่งศัตรู/สกิล (ไม่ใช่แก้ตรงนี้)
    @Override
    public void knockbackPlayer(float dirX, float dirY, float speed) {
        velX += dirX * speed;   // updateFish ดึงความเร็วกลับเข้าหาเป้าหมายเอง แรงจึงค่อยๆ หมด (ระยะประมาณ 250 px)
        velY += dirY * speed;
    }

    // [ทำอะไร] สตันผู้เล่น ถ้ากำลังอยู่ในช่วงกันสตัน (stunImmuneMs > 0) จะไม่ติดสตัน
    //          ถ้าติดอยู่แล้วเอาค่าที่ยาวกว่า (ไม่ต่อทบกัน)
    @Override
    public void stunPlayer(long durationMs) {
        if (stunImmuneMs > 0f) return;
        stunRemainingMs = Math.max(stunRemainingMs, durationMs);
    }

    // [ทำอะไร] ทำให้ช้าลง factor เท่า นาน durationMs เรียกซ้ำ = รีเซ็ตเวลา (ไม่ซ้อนทับ) ตัวนับเวลาถอยหลังอยู่ใน updateFish
    @Override
    public void slowPlayer(float factor, long durationMs) {
        slowFactor = factor;
        slowRemainingMs = durationMs;
    }

    // [ทำอะไร] ตั้งตัวคูณคูลดาวน์สกิล (0.5 = เร็วขึ้นเท่าตัว) ใช้ใน startCooldownUI
    @Override
    public void setCooldownMultiplier(float multiplier) { cooldownMultiplier = multiplier; }
    // ------------------ ส่วนที่ 3 ของคนที่ 1 (ในไฟล์เต็ม) ------------------
    // [ทำอะไร] ให้ฮีโร่/ศัตรูถามสถานะเกม: ถ้าเกมจบ/หยุด ควรเลิกทำงานในเฟรมนั้น
    @Override
    public boolean isGameRunning() { return isGameRunning; }

    @Override
    public boolean isGamePaused() { return isGamePaused; }

    // [ทำอะไร] ส่งลิสต์ศัตรูให้สกิลวนหาเป้าหมาย (ส่งตัวจริง ไม่ใช่สำเนา ระวังอย่า add/remove จากฝั่งสกิล)
    @Override
    public List<SeaEnemy> getEnemies() { return enemyList; }

    // [ทำอะไร] ศัตรูตาย 1 ตัว -> อัปเดตข้อความ "ศัตรูเหลือกี่ตัว" แล้วเช็กว่าชนะหรือยัง
    @Override
    public void onEnemyDefeated() {
        updateStageInfo();
        checkWinCondition();
    }

    // [ทำอะไร] บอสเสียหนึ่งชีวิต (ยังไม่ตาย) -> ถ้าเป็นด่านบอส เก็บลูกน้องทิ้งหมด (เฟส 2) และให้บอสดรอปหัวใจ
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

    // [ทำอะไร] ส่งต่อให้ ItemManager (คนที่ 5) สุ่มดรอปไอเทมที่จุด (cx, cy) เฉพาะตอนเกมยังเล่นอยู่
    @Override
    public void dropItemAt(float cx, float cy, boolean forceHeart) {
        if (itemManager != null && isGameRunning) itemManager.onEnemyDefeated(cx, cy, forceHeart);
    }

    // ---------- ItemManager.Host ----------
    // [ส่วนนี้คืออะไร] เมธอดที่ ItemManager (ระบบไอเทม ของคนที่ 5) เรียกกลับมา เพื่อใช้ผลไอเทมกับผู้เล่น
    @Override public int getPlayerHp() { return playerHp; }
    @Override public int getPlayerMaxHp() { return maxPlayerHp; }

    // [ทำอะไร] ฟื้นเลือด ไม่เกินเลือดสูงสุด แล้วอัปเดตหลอด+ตัวเลข  (ไอเทมหัวใจเรียก)
    @Override
    public void healPlayer(int amount) {
        playerHp = Math.min(maxPlayerHp, playerHp + amount);
        if (barPlayerHp != null) barPlayerHp.setProgress(playerHp);
        updateHpUI();
    }

    // [ทำอะไร] เพิ่มสแตก (ไอเทมพลังงานเรียก) ถ้าเต็มแล้วเรียก quizManager.show() ทันที
    @Override
    public void addStack(int amount) {
        if (!isGameRunning || currentStack >= maxStack) return;
        currentStack = Math.min(maxStack, currentStack + amount);
        updateStackUI();
        if (currentStack >= maxStack) quizManager.show();
    }

    // [ทำอะไร] แช่แข็ง/ทำให้ศัตรูทุกตัวที่ยังมีชีวิตช้าลง factor เท่า นาน durationMs
    @Override
    public void freezeEnemies(float factor, long durationMs) {
        for (int i = 0; i < enemyList.size(); i++) {
            SeaEnemy e = enemyList.get(i);
            if (e.isAlive) e.applySlow(factor, durationMs);
        }
    }

    // [ทำอะไร] อัปเดตระบบไอเทมทุกเฟรม + ส่งโบนัสดาเมจไปให้ SeaEnemy + อัปเดตข้อความบัฟเมื่อเปลี่ยนจริง
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

    // [ทำอะไร] Ultimate ปล่อยจบแล้ว -> ปลดสถานะ "กำลังปล่อย", รีเซ็ตสแตกเป็น 0, ล็อกปุ่ม ULT กลับ
    //          ฮีโร่ต้องเรียก ctx.onUltimateFinished() ตอนจบสกิล Ultimate ทุกครั้ง ไม่งั้นสแตกค้างและปุ่มสกิลใช้ไม่ได้
    @Override
    public void onUltimateFinished() {
        ultimateRunning = false;
        currentStack = 0;
        updateStackUI();
        updateUltimateButton();
    }
    // ------------------ ส่วนที่ 4 ของคนที่ 1 (ในไฟล์เต็ม) ------------------
    // [ทำอะไร] จุดเริ่มต้นของฉากต่อสู้ ทำตามลำดับ:
    //   1) โหลดเสียง + layout   2) อ่าน HERO_ID/STAGE_ID จาก Intent   3) สร้างฮีโร่ ตั้ง HP/สแตก/ความเร็ว
    //   4) ตั้ง quiz + พื้นหลัง  5) ผูก View   6) ผูกจอยสติ๊ก   7) ผูกปุ่มสกิล/ULT
    //   8) รอจอวัดขนาดเสร็จ (gameArea.post) แล้วค่อยสร้างไอเทม วางศัตรู เริ่ม game loop และโชว์สอนเล่น
    // [แก้ยังไง]
    //   - เปลี่ยนค่าเริ่มต้น HP/สแตก/ความเร็ว ให้แก้ที่ฮีโร่แต่ละตัว (Swordfish.java ฯลฯ ของคนที่ 2) ไม่ใช่ที่นี่
    //   - ลำดับสำคัญ: setupQuiz() ต้องก่อน quizManager.setGate(...) เพราะ quizManager ถูกสร้างใน setupQuiz
    // [เพิ่มยังไง] อยากเพิ่มปุ่มใหม่ (เช่น ปุ่มสกิล 3): เพิ่มปุ่มใน activity_battle.xml -> findViewById ที่นี่
    //             -> setOnClickListener ให้เหมือนบล็อก btnSkill1 -> เพิ่มเมธอด useSkill3 ใน Hero.java
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SoundManager.init(this);
        setContentView(R.layout.activity_battle);

        // รับค่าจากหน้าเลือกด่าน/เลือกฮีโร่ (MainActivity ใส่ไว้ใน Intent.putExtra) ถ้าไม่มีใช้ค่าเริ่มต้น 1
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

        // ---- จอยสติ๊ก ----
        // [ทำอะไร] แปลงตำแหน่งนิ้วบนวงกลมจอยสติ๊กเป็น moveX/moveY (-1..1) และมุม playerAngle
        //          ปล่อยนิ้ว (UP/CANCEL) = รีเซ็ตเสมอ แม้เกมหยุด ไม่งั้นปลาวิ่งค้าง
        // [แก้ยังไง] อยากให้จอยสติ๊กไวขึ้น/ช้าลง: แก้ที่ MAX_SPEED หรือสูตร clamped/radius ด้านล่าง
        //           อยากย้ายตำแหน่งจอยสติ๊ก: แก้ joystickBase ใน activity_battle.xml
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

        // ---- ปุ่มสกิล ----
        // ปุ่มสกิล: ดูแลแค่คูลดาวน์/แอนิเมชันปุ่ม แล้วส่งต่อให้ฮีโร่เป็นคนทำสกิล
        // [ลำดับเมื่อกดปุ่ม] เช็กว่าใช้ได้ไหม -> เล่นเสียง -> เริ่มนับคูลดาวน์ -> แอนิเมชันปุ่ม -> เรียก playerHero.useSkillN(this)
        // [แก้ยังไง] เปลี่ยนเวลาคูลดาวน์: แก้ getSkill1CooldownMs()/getSkill2CooldownMs() ในไฟล์ฮีโร่ (คนที่ 2)
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

        // ---- ปุ่ม Ultimate ----
        // ปุ่ม Ultimate: โชว์เฉพาะฮีโร่ที่ใช้ปุ่มแยก
        // [เงื่อนไขกดได้] ultimateReady = true (ตอบ quiz ถูกแล้ว) และไม่ได้กำลังปล่อย/หยุดเกม
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

        // ต้อง post: ขนาดของ gameArea ยังเป็น 0 ใน onCreate ต้องรอให้ระบบวัดขนาดก่อนจึงวางศัตรูตามสัดส่วนจอได้
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

    // [ทำอะไร] คืนจอยสติ๊กกลางและหยุดเดิน เรียกเมื่อปล่อยนิ้ว หรือเมื่อมีหน้าต่างอื่นขึ้นมาบัง (quiz/เมนูหยุด)
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

    // ============================ (D) Game loop ============================
    // [ทำอะไร] Choreographer เรียก doFrame "ทุกเฟรมจอ" (~60 ครั้ง/วินาที) เราเป็นหัวใจของเกม
    //   dt = เวลาตั้งแต่เฟรมก่อน (วินาที) จำกัดไม่เกิน 0.05 กันกระโดดไกลตอนเครื่องกระตุก
    //   ถ้าไม่ได้หยุดเกม: ขยับผู้เล่น -> อัปเดตไอเทม -> ย้าย overlay เหนือหัว -> นับเวลา ULT -> นับดีบัฟ -> สั่งศัตรูทุกตัวคิด (updateAI)
    //   แล้วขอเฟรมถัดไปด้วย postFrameCallback(this) (ถ้าไม่เรียก เกมจะหยุดวน)
    // [เพิ่มยังไง] อยากให้มีระบบใหม่ที่ต้องอัปเดตทุกเฟรม (เช่น เวลาเกม): เพิ่มเมธอด updateXxx(dt) แล้วเรียกในบล็อก !isGamePaused ด้านล่าง
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

    // [ทำอะไร] ขอให้ระบบเรียก frameCallback เฟรมแรก (หลังจากนั้นมันขอเฟรมต่อไปเองเรื่อย ๆ)
    private void startGameLoop() {
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    // [ทำอะไร] ขยับและแอนิเมชันปลาของผู้เล่นทุกเฟรม:
    //   1) นับถอยหลังสโลว์/สตัน  2) คำนวณความเร็วเป้าหมาย = จอยสติ๊ก x MAX_SPEED x (ความเร็วฮีโร่ x บัฟ x สโลว์ x ไอเทม)
    //   3) ค่อย ๆ ดึงความเร็วจริงเข้าหาเป้าหมายแบบนุ่ม (exp) 4) ขยับตำแหน่งและบีบไม่ให้ออกนอกจอ
    //   5) หันซ้าย/ขวา 6) โยกตัว/เอียง/บีบตัวให้ดูเหมือนว่ายน้ำ
    // [แก้ยังไง]
    //   - ว่ายเร็ว/ช้าขึ้น: แก้ MAX_SPEED (ทั้งเกม) หรือ getBaseSpeedMultiplier() ของฮีโร่ (รายตัว)
    //   - ตอบสนองนุ่ม/หนืด: ค่า 8f ใน exp(-8f * dt) มากขึ้น = ตอบสนองไวขึ้น
    //   - ท่าว่าย: แก้ค่า 20f (องศาเอียง) , 2f/8f (โยกตัว) , 0.04f (บีบตัว)
    //   - เพิ่มฮีโร่ใหม่ที่ภาพต้นฉบับหันขวา: เพิ่ม ID ในเงื่อนไข isSpriteDefaultFacingRight
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

    // ============================ (E) วางศัตรู / คูลดาวน์ / สแตก / รับดาเมจ ============================
    /** ลูกน้อง 5 ตัวแบบด่าน 3 อยู่ฝั่งซ้ายของบอส (เฟส 1 เท่านั้น เฟส 2 ถูกเก็บออกหมด) */
    // [ทำอะไร] สร้าง BossMinion 5 ตัว ตำแหน่งเป็นสัดส่วนของจอ (xs, ys = 0..1 ของความกว้าง/สูง)
    // [แก้ยังไง] ย้ายตำแหน่ง: แก้ array xs/ys  | เปลี่ยนชนิด/อีโมจิ: แก้ names/emojis (ต้องเท่ากับจำนวนตัว)
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

    // [ทำอะไร] ล้างลิสต์ศัตรูแล้ววางศัตรูใหม่ตาม stageConfig:
    //   ด่านบอส = KrakenBoss + ลูกน้อง 5 ตัว  | ด่านปกติ = Crab, Jellyfish, Turtle, (Kraken ถ้า includeSquid), Starfish
    // [แก้ยังไง]
    //   - ตำแหน่งศัตรู: แก้ตัวคูณ width * 0.70f ฯลฯ  (0..1 ของความกว้าง/สูงจอ)
    //   - ความแข็งแรงศัตรูของแต่ละด่าน: แก้ที่ StageConfig.java (คนที่ 5) ไม่ใช่ที่นี่
    // [เพิ่มยังไง] เพิ่มศัตรูอีก 1 ตัว: เพิ่มบรรทัด enemyList.add(new SeaEnemy(this, "ชื่อ", "อีโมจิ", x, y, stageConfig));
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

    // [ทำอะไร] นับถอยหลังคูลดาวน์บนปุ่ม: ปิดปุ่ม แสดงเลขวินาที (อัปเดตทุก 100 ms) แล้วคืนข้อความ+เปิดปุ่มเมื่อครบ
    //   เวลา = baseMs x ตัวคูณจากสกิล x ตัวคูณจากไอเทม
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

    // [ทำอะไร] สกิลโดนศัตรู 1 ครั้ง -> สะสมสแตก +1 (ถ้าติดดีบัฟจะได้น้อยลงเป็นเศษ stackFraction)
    //          สแตกเต็ม -> quizManager.show() ขึ้นโจทย์
    // [แก้ยังไง] อยากให้สแตกเต็มเร็วขึ้น: ลด getStackNeeded() ของฮีโร่ (คนที่ 2) หรือเพิ่มค่าที่บวกต่อฮิต
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

    // [ทำอะไร] ยังมีศัตรูเหลือรอดไหม (true ถ้ามีอย่างน้อย 1 ตัว)
    private boolean anyEnemyAlive() {
        for (int i = 0; i < enemyList.size(); i++) {
            if (enemyList.get(i).isAlive) return true;
        }
        return false;
    }

    // [ทำอะไร] ผู้เล่นโดนดาเมจ (ศัตรูเรียกผ่าน ctx.damagePlayer) ตามลำดับ:
    //   1) เกมจบแล้ว = ไม่ทำอะไร  2) ช่วงอมตะ 350 ms หลังโดนตี = ไม่รับ  3) โล่ไอเทมกันได้ = ไม่เสียเลือด
    //   4) ลด HP + อัปเดตหลอด  5) นับครั้งที่โดน -> ติดดีบัฟ ULT ตาม stageConfig
    //   6) ด่าน 3+ โดนครบ 3 ครั้ง สแตกลด 20%  7) เล่นเสียงโดน/แพ้ กะพริบตัวผู้เล่น  8) HP = 0 -> แพ้ แสดงหน้าผลลัพธ์
    // [แก้ยังไง]
    //   - เกมง่ายขึ้น/ยากขึ้น: แก้ HIT_INVULN_MS (อมตะ), ENERGY_DRAIN_*, หรือค่าดาเมจของศัตรู (SeaEnemy.java ของคนที่ 4)
    //   - เปลี่ยนสิ่งที่เกิดตอนแพ้: แก้บล็อก if (playerHp <= 0) ท้ายเมธอด
    // [เพิ่มยังไง] อยากเพิ่ม "ฟื้นคืนชีพ" ตอนตาย ให้เพิ่มในบล็อก playerHp <= 0 ก่อนเรียก showResultOverlay
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
    // ------------------ ส่วนที่ 5 ของคนที่ 1 (ในไฟล์เต็ม) ------------------
    // [ทำอะไร] คิดจำนวนดาวจากเลือดที่เหลือตอนชนะ (เก็บสถิติสูงสุดผ่าน GameProgress.saveBestStars ของคนที่ 5)
    // [แก้ยังไง] เปลี่ยนเกณฑ์ดาว: แก้ 0.70f และ 0.35f  (เช่น 3 ดาวต้องเลือด >= 90% ให้เปลี่ยนเป็น 0.90f)
    private int starsForRemainingHp() {
        float ratio = playerHp / (float) maxPlayerHp;
        if (ratio >= 0.70f) return 3;
        if (ratio >= 0.35f) return 2;
        return 1;
    }
    // ------------------ ส่วนที่ 6 ของคนที่ 1 (ในไฟล์เต็ม) ------------------
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
    // [ทำอะไร] เป็น "ประตู" (Gate) ที่ QuizManager ถามก่อนขึ้นโจทย์ทุกครั้ง (ผูกใน onCreate ด้วย quizManager.setGate)
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
    // [เพิ่มยังไง] ถ้าเพิ่มระบบใหม่ที่ต้องหยุดตอนด่านจบ (เช่น ตัวนับเวลา) ให้ใส่การปิดที่นี่
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

    // [uiHandler / pendingAutoUltimate] ไว้หน่วงเวลา 600 ms ก่อนปล่อย Ultimate อัตโนมัติหลังตอบถูก (ดู setupQuiz)
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingAutoUltimate;

    // [ทำอะไร] เริ่มด่านใหม่ด้วย recreate() = สร้าง Activity ใหม่ทั้งก้อน ตัวแปรทุกตัวกลับเป็นค่าเริ่มต้น
    private void restartStage() {
        recreate();
    }
    // ------------------ ส่วนที่ 7 ของคนที่ 1 (ในไฟล์เต็ม) ------------------
    // [ทำอะไร] เช็กว่าชนะหรือยัง: ศัตรูตายหมดทุกตัว (ด่านบอสดูเฉพาะบอส ลูกน้องไม่ต้องฆ่าให้หมด)
    //   ชนะ -> เล่นเสียง หยุดเกม -> คิดดาว -> บันทึกสถิติ GameProgress.saveBestStars (คนที่ 5) -> แสดงหน้าชนะ
    //   การปลดล็อกด่านถัดไปมาจาก "ดาว" ที่บันทึกนี้ (ดู GameProgress.getUnlockedStage)
    // [แก้ยังไง] เปลี่ยนเงื่อนไขชนะ: แก้ตัวแปร allDead / เงื่อนไข continue ของด่านบอส
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

            // ดาวตาม HP ที่เหลือ เก็บเฉพาะสถิติที่ดีที่สุดของด่านนี้
            int stars = starsForRemainingHp();
            GameProgress.saveBestStars(this, currentStageId, stars);
            showResultOverlay(true, stars);
        }
    }

    // [ทำอะไร] สร้าง QuizManager (คนที่ 5) ด้วยโจทย์ตามวิชาของฮีโร่ และผูก Listener 4 จังหวะ:
    //   onQuizShown : เล่นเสียง หยุดเกม รีเซ็ตจอยสติ๊ก
    //   onCorrect   : ตอบถูก -> ฮีโร่ที่มีปุ่ม ULT = ปลดล็อกปุ่มให้กดเอง | ฮีโร่อื่น = ปล่อย Ultimate อัตโนมัติหลัง 600 ms
    //   onWrong / onTimeout : ตอบผิด/หมดเวลา -> สแตกกลับเป็น 0 เล่นต่อ
    // [แก้ยังไง]
    //   - เปลี่ยนรางวัลตอบถูก: แก้ใน onCorrect  | เปลี่ยนบทลงโทษตอบผิด: แก้ resetStackAfterQuiz()
    //   - เปลี่ยนเวลาตอบ/โจทย์: แก้ที่ QuizManager.java / QuestionBank.java (คนที่ 5)
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

    // [ทำอะไร] บทลงโทษตอบผิด/หมดเวลา: เล่นต่อ และสแตกกลับเป็น 0 (ต้องสะสมใหม่)
    private void resetStackAfterQuiz() {
        isGamePaused = false;
        currentStack = 0;
        updateStackUI();
    }
    // ------------------ ส่วนที่ 8 ของคนที่ 1 (ในไฟล์เต็ม) ------------------
    // ---- วงจรชีวิต Activity ----
    // onResume : กลับมาหน้านี้ -> เล่นเพลงฉากต่อสู้ (ชื่อ "bgm_battle")
    @Override
    protected void onResume() {
        super.onResume();
        SoundManager.playMusic(this, "bgm_battle");
    }

    // onPause : ออกจากหน้านี้ (กด Home/สลับแอป) -> หยุดเพลง
    @Override
    protected void onPause() {
        super.onPause();
        SoundManager.pauseMusic();
    }

    // onDestroy : ปิดหน้านี้ -> เก็บกวาดทุกอย่าง ห้ามลืมเมื่อเพิ่มระบบที่ใช้ทรัพยากร (timer/handler/callback)
    //   หยุด game loop, รีเซ็ตคิวโจมตีศัตรู, ล้างโบนัสดาเมจ, ปล่อย quiz และกองเอฟเฟกต์ที่ใช้ซ้ำ (HitEffects/GhostPool ของคนที่ 4)
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
