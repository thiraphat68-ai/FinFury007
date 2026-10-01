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

    boolean isGameRunning();

    boolean isGamePaused();

    List<SeaEnemy> getEnemies();

    void damagePlayer(int damage);

    /** เรียกเมื่อสกิลโดนศัตรู เพื่อสะสมสแตก */
    void onHitEnemySuccess();

    /** เรียกเมื่อศัตรูตาย เพื่อเช็กเงื่อนไขชนะ */
    void onEnemyDefeated();

    /** เรียกเมื่อ Ultimate จบ เพื่อรีเซ็ตสแตก */
    void onUltimateFinished();
}