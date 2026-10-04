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
    Context getContext();

    FrameLayout getGameArea();

    View getPlayerContainer();

    /** ทิศที่ผู้เล่นหันอยู่ (องศา) ได้จากจอยสติ๊กล่าสุด */
    float getPlayerAngle();

    /** ล็อก/ปลดล็อกการเคลื่อนที่ปกติของผู้เล่นระหว่างใช้สกิล */
    void setSkillLock(boolean locked);

    /** ความเร็วว่ายน้ำตอนนี้เทียบความเร็วสูงสุดปกติ: 0 = หยุดนิ่ง, 1 = เต็มสปีด */
    float getPlayerSpeedRatio();

    /** คูณความเร็วว่ายน้ำ (1 = ปกติ) ใช้กับสกิลที่เพิ่มความเร็ว */
    void setSpeedMultiplier(float multiplier);

    /** คูณเวลาคูลดาวน์ปุ่ม Skill 1/2 (1 = ปกติ, 0.5 = สั้นลงครึ่งหนึ่ง) */
    void setCooldownMultiplier(float multiplier);

    /** โชว์หลอดเวลาที่เหลือของ Ultimate แบบมีเวลา (นับถอยหลังเองและซ่อนเมื่อหมด หยุดนับตอนเกมหยุด) */
    void showUltimateDuration(long durationMs);

    /** ซ่อนหลอดเวลา Ultimate ก่อนหมดเวลา (เช่น Ultimate ถูกยกเลิก) */
    void hideUltimateDuration();

    /** แสดงโบนัสดาเมจเหนือหัวผู้เล่น เช่น "+3" (Blood Frenzy) */
    void setPlayerBonusDamage(int bonus);

    void clearPlayerBonusDamage();

    /** แสดงหลอดชาร์จเหนือหัวผู้เล่น progress 0..1 (Railgun) */
    void setPlayerChargeProgress(float progress);

    void hidePlayerChargeBar();

    boolean isGameRunning();

    boolean isGamePaused();

    List<SeaEnemy> getEnemies();

    void damagePlayer(int damage);

    /** ผลักผู้เล่นไปตามทิศ (dirX, dirY เป็นเวกเตอร์หน่วย) ด้วยแรงกระแทกความเร็ว speed px/s ที่ค่อยๆ ลดลงเอง */
    void knockbackPlayer(float dirX, float dirY, float speed);

    /** สตันผู้เล่น: ขยับไม่ได้ durationMs มิลลิวินาที (สกิลยังกดได้ ต่อซ้ำไม่ซ้อนทับ) */
    void stunPlayer(long durationMs);

    /** ทำให้ผู้เล่นเคลื่อนที่ช้าลง: factor 0.5 = เหลือครึ่งหนึ่ง นาน durationMs (ต่อซ้ำ = รีเซ็ตเวลา ไม่ซ้อนทับ) */
    void slowPlayer(float factor, long durationMs);

    /** เรียกเมื่อสกิลโดนศัตรู เพื่อสะสมสแตก */
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