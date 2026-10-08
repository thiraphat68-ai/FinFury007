// =====================================================================================
// [คนที่ 1 - Controller]  ไฟล์: BattleContext.java  (81 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/BattleContext.java
// สำเนานี้มีคอมเมนต์อธิบายเพิ่ม "โค้ดเหมือนไฟล์จริงทุกตัวอักษร" (ตรวจด้วย tools/verify_same_code.py)
//
// [ไฟล์นี้คืออะไร]
//   BattleContext เป็น "interface" (สัญญา) ที่บอกว่า ฉากต่อสู้ (BattleActivity) ต้องทำอะไรได้บ้าง
//   ฮีโร่ (คนที่ 2) และศัตรู (คนที่ 4) จะเรียกฉากต่อสู้ผ่าน interface นี้เท่านั้น
//   ไม่ได้เรียก BattleActivity ตรง ๆ  ข้อดี: ไฟล์ฮีโร่/ศัตรูไม่ต้องรู้จักหน้าจอ แก้หน้าจอได้โดยไม่กระทบ
//
// [ใครเรียกใคร]
//   BattleActivity  --implements-->  BattleContext
//   Hero / Swordfish / Shark / ...   --ctx.xxx()-->  BattleContext   (สกิลของฮีโร่)
//   SeaEnemy / KrakenBoss / ...      --ctx.xxx()-->  BattleContext   (ศัตรูทำดาเมจผู้เล่น ฯลฯ)
//
// [จะ "เพิ่ม" เมธอดใหม่ให้ฮีโร่/ศัตรูเรียกได้ยังไง]  (ต้องทำ 2 ที่เสมอ ไม่งั้น build ไม่ผ่าน)
//   1) เพิ่มบรรทัดเมธอดใน interface นี้  เช่น  void showShield(boolean on);
//   2) ไปที่ BattleActivity.java แล้ว implement เมธอดเดียวกัน (ใส่ @Override public void showShield(...) { ... })
//
// [จะ "แก้" ยังไง]
//   - เปลี่ยนชื่อ/พารามิเตอร์ของเมธอดที่นี่ -> ต้องแก้ทุกที่ที่เรียก (ค้นหาด้วย Ctrl+Shift+F ชื่อเมธอด)
//     และแก้ที่ BattleActivity ให้ตรงกัน
//   - เปลี่ยน "พฤติกรรม" (เช่น ผู้เล่นโดนแรงกระแทกแรงขึ้น) ให้แก้ในตัว implement ที่ BattleActivity ไม่ใช่ที่นี่
//     เพราะที่นี่เป็นแค่ "ชื่อเมธอด" ไม่มีโค้ดทำงาน
// =====================================================================================
package com.example.finfury;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import java.util.List;

/**
 * สิ่งที่สกิลของฮีโร่ "ขอใช้" จากฉากต่อสู้ได้
 * BattleActivity implement interface นี้ ทำให้ไฟล์ฮีโร่ไม่ต้องรู้จัก Activity ตรงๆ
 */
public interface BattleContext {
    // [Context ของแอป] ใช้สร้าง View ใหม่ในสกิล (เช่น new ImageView(ctx.getContext())) หรือเข้าถึง resources
    Context getContext();

    // [พื้นที่เล่นเกม] FrameLayout ที่ศัตรู/ปลา/เอฟเฟกต์ถูกวาดอยู่ ใช้ addView เพื่อเพิ่มเอฟเฟกต์ของสกิลลงจอ
    FrameLayout getGameArea();

    // [View ของตัวผู้เล่น] ใช้อ่านตำแหน่ง (getX/getY) เพื่อให้สกิลยิงออกจากตัวผู้เล่น
    View getPlayerContainer();

    /** ทิศที่ผู้เล่นหันอยู่ (องศา) ได้จากจอยสติ๊กล่าสุด */
    float getPlayerAngle();

    /** ล็อก/ปลดล็อกการเคลื่อนที่ปกติของผู้เล่นระหว่างใช้สกิล */
    // [ใช้ตอนไหน] สกิลพุ่ง/ชาร์จที่ไม่อยากให้ผู้เล่นเดินเองระหว่างทำสกิล: setSkillLock(true) ... จบแล้ว setSkillLock(false)
    // [ระวัง] ถ้าลืมปลดล็อกผู้เล่นจะขยับไม่ได้ตลอด
    void setSkillLock(boolean locked);

    /** ความเร็วว่ายน้ำตอนนี้เทียบความเร็วสูงสุดปกติ: 0 = หยุดนิ่ง, 1 = เต็มสปีด */
    float getPlayerSpeedRatio();

    /** คูณความเร็วว่ายน้ำ (1 = ปกติ) ใช้กับสกิลที่เพิ่มความเร็ว */
    // [ตัวอย่าง] setSpeedMultiplier(1.5f) = เร็วขึ้น 50%  / เมื่อสกิลหมดอย่าลืมคืนค่า setSpeedMultiplier(1f)
    void setSpeedMultiplier(float multiplier);

    /** คูณเวลาคูลดาวน์ปุ่ม Skill 1/2 (1 = ปกติ, 0.5 = สั้นลงครึ่งหนึ่ง) */
    void setCooldownMultiplier(float multiplier);

    /** โชว์หลอดเวลาที่เหลือของ Ultimate แบบมีเวลา (นับถอยหลังเองและซ่อนเมื่อหมด หยุดนับตอนเกมหยุด) */
    void showUltimateDuration(long durationMs);

    /** ซ่อนหลอดเวลา Ultimate ก่อนหมดเวลา (เช่น Ultimate ถูกยกเลิก) */
    void hideUltimateDuration();

    /** แสดงโบนัสดาเมจเหนือหัวผู้เล่น เช่น "+3" (Blood Frenzy) */
    void setPlayerBonusDamage(int bonus);

    // [ลบข้อความโบนัสดาเมจเหนือหัวผู้เล่น] คู่กับ setPlayerBonusDamage
    void clearPlayerBonusDamage();

    /** แสดงหลอดชาร์จเหนือหัวผู้เล่น progress 0..1 (Railgun) */
    void setPlayerChargeProgress(float progress);

    // [ซ่อนหลอดชาร์จ] คู่กับ setPlayerChargeProgress
    void hidePlayerChargeBar();

    // [เกมกำลังเล่นอยู่ไหม] false เมื่อชนะ/แพ้แล้ว  ศัตรู/สกิลควรหยุดทำงานเมื่อเป็น false
    boolean isGameRunning();

    // [เกมถูกหยุดชั่วคราวไหม] true ตอนเปิดเมนูหยุด หรือตอนโจทย์ quiz ขึ้น  ควรเช็กก่อนขยับอะไรทุกเฟรม
    boolean isGamePaused();

    // [รายชื่อศัตรูทั้งหมดในด่าน] สกิลใช้วนหาศัตรูที่โดน (เช็กระยะแล้วเรียก enemy.takeDamage)
    List<SeaEnemy> getEnemies();

    // [ทำดาเมจผู้เล่น] ศัตรูเรียกเมื่อโจมตีโดน  ตรรกะอมตะ/โล่/HP หมด อยู่ใน BattleActivity.damagePlayer
    void damagePlayer(int damage);

    /** ผลักผู้เล่นไปตามทิศ (dirX, dirY เป็นเวกเตอร์หน่วย) ด้วยแรงกระแทกความเร็ว speed px/s ที่ค่อยๆ ลดลงเอง */
    void knockbackPlayer(float dirX, float dirY, float speed);

    /** สตันผู้เล่น: ขยับไม่ได้ durationMs มิลลิวินาที (สกิลยังกดได้ ต่อซ้ำไม่ซ้อนทับ) */
    void stunPlayer(long durationMs);

    /** ทำให้ผู้เล่นเคลื่อนที่ช้าลง: factor 0.5 = เหลือครึ่งหนึ่ง นาน durationMs (ต่อซ้ำ = รีเซ็ตเวลา ไม่ซ้อนทับ) */
    void slowPlayer(float factor, long durationMs);

    /** เรียกเมื่อสกิลโดนศัตรู เพื่อสะสมสแตก */
    // [สำคัญ] สกิลทุกตัวต้องเรียกเมธอดนี้เมื่อโดนศัตรู ไม่งั้นสแตกไม่เพิ่ม -> quiz/Ultimate ไม่ขึ้น
    void onHitEnemySuccess();

    /** เรียกเมื่อศัตรูตาย เพื่อเช็กเงื่อนไขชนะ */
    void onEnemyDefeated();

    /** เรียกเมื่อบอสเสียชีวิตหนึ่ง (แต่ยังไม่ตาย) เพื่ออัปเดตข้อความด่าน */
    void onEnemyLifeLost();

    /** ศัตรูตายที่จุดกึ่งกลาง (cx, cy) ให้สุ่มดรอปไอเทม (forceHeart = ดรอปหัวใจแน่นอน) */
    void dropItemAt(float cx, float cy, boolean forceHeart);

    /** เรียกเมื่อ Ultimate จบ เพื่อรีเซ็ตสแตก */
    void onUltimateFinished();
}
