// =====================================================================================
// [คนที่ 5 - ข้อมูล ควิซ ไอเทม และสมดุลเกม]  ไฟล์: GameProgress.java  (60 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/GameProgress.java
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   ตัวจัดการ "การเซฟ" ความคืบหน้าของผู้เล่น ใช้ SharedPreferences ชื่อ "GamePrefs" (ไฟล์เล็ก ๆ ในเครื่อง เก็บเป็นคู่ คีย์-ค่า)
//   เก็บ: ดาวที่ดีที่สุดของแต่ละด่าน (คีย์ stars_stage_1 ถึง stars_stage_5)
//   ผู้ใช้: BattleActivity (บันทึกดาวตอนชนะ) , MainActivity (แสดงดาว/กุญแจ) , SettingsActivity (รีเซ็ต)
//   final class + constructor private + เมธอด static = เรียกตรง ๆ ไม่ต้องสร้าง object
//
// [ปลดล็อกด่านทำงานยังไง]
//   ด่านถัดไปเปิดเมื่อด่านก่อนหน้า "มีดาว > 0" (getUnlockedStage วนเช็กทุกด่าน) ผู้เล่นเริ่มที่ด่าน 1 เสมอ
//   [ข้อสังเกต] ในโค้ดไม่มีที่ไหนเขียนคีย์ "unlocked_stage" (คอมเมนต์ของเมธอดบอกว่า BattleActivity บันทึก แต่ไม่มี)
//               การปลดล็อกจึงมาจากดาวอย่างเดียว ซึ่งทำงานถูกต้อง
//
// [จะเพิ่มข้อมูลที่เซฟยังไง] เช่นเก็บคะแนนสูงสุด: เพิ่มเมธอด get/save ตามแบบ getStars/saveBestStars ใช้คีย์ใหม่ เช่น "best_score"
// [จะแก้จำนวนด่าน] แก้ MAX_STAGES (และที่อื่น: StageConfig, BattleActivity, activity_main.xml)
// =====================================================================================
package com.example.finfury;

import android.content.Context;
import android.content.SharedPreferences;

// [คอมเมนต์คลาส - โดยเจ้าของไฟล์เดิม]
/** ความคืบหน้าของผู้เล่นที่เก็บใน SharedPreferences "GamePrefs": ดาวที่ดีที่สุดของแต่ละด่าน */
public final class GameProgress {

    // [PREFS] ชื่อไฟล์เซฟ "GamePrefs" (ไฟล์เดียวกับที่ SoundManager เก็บค่าเปิด/ปิดเสียง และ tutorial_seen)
    private static final String PREFS = "GamePrefs";

    private GameProgress() {}

    // [starsKey] สร้างชื่อคีย์ต่อด่าน เช่น ด่าน 3 -> "stars_stage_3"
    private static String starsKey(int stageId) {
        return "stars_stage_" + stageId;
    }

    // [getStars] อ่านดาวที่ดีที่สุดของด่านนั้น (0 = ยังไม่เคยชนะ)
    /** ดาวที่ดีที่สุดของด่านนี้ (0 = ยังไม่เคยชนะ) */
    public static int getStars(Context context, int stageId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getInt(starsKey(stageId), 0);
    }

    // [saveBestStars] บันทึกเฉพาะเมื่อดาวใหม่มากกว่าสถิติเดิม (เล่นซ้ำแล้วได้ดาวน้อยกว่า ไม่ทับ) apply() = บันทึกแบบ async
    /** บันทึกเฉพาะเมื่อดีกว่าสถิติเดิม */
    public static void saveBestStars(Context context, int stageId, int stars) {
        if (stars <= getStars(context, stageId)) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(starsKey(stageId), stars).apply();
    }

    // [MAX_STAGES] จำนวนด่านทั้งหมด (5)
    public static final int MAX_STAGES = 5;

    // [getUnlockedStage] คืนเลขด่านสูงสุดที่เล่นได้ (อย่างน้อย 1 ไม่เกิน MAX_STAGES) = ค่ามากสุดของ "unlocked_stage" ที่เก็บไว้ (ถ้ามี) กับ "ด่านถัดจากด่านที่เคยได้ดาว"
    /**
     * ด่านสูงสุดที่เล่นได้ (เริ่มที่ 1) = ค่า "unlocked_stage" ที่ BattleActivity บันทึกตอนชนะ
     * หรือด่านถัดจากด่านที่เคยได้ดาวสูงสุด (กันกรณีค่าหายแต่ดาวยังอยู่) ไม่เกิน MAX_STAGES
     */
    public static int getUnlockedStage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int unlocked = prefs.getInt("unlocked_stage", 1);
        for (int stage = 1; stage <= MAX_STAGES; stage++) {
            if (getStars(context, stage) > 0) unlocked = Math.max(unlocked, stage + 1);
        }
        return Math.max(1, Math.min(MAX_STAGES, unlocked));
    }

    // [reset] ลบดาวทุกด่านและค่าปลดล็อก (กลับไปเหลือด่าน 1) ไม่แตะค่าตั้งเสียงและสอนเล่น (เรียกจากปุ่มรีเซ็ตในหน้าตั้งค่า)
    /** ล้างความคืบหน้า: ดาทุกด่านและการปลดล็อก (กลับไปเหลือด่าน 1) ไม่แตะค่าตั้งเสียง/สอนเล่น */
    public static void reset(Context context) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int stage = 1; stage <= MAX_STAGES; stage++) editor.remove(starsKey(stage));
        editor.remove("unlocked_stage");
        editor.apply();
    }

    // [starsText] แปลงจำนวนดาวเป็นข้อความ เช่น 2 -> "★★☆" ; 0 -> ข้อความว่าง (ใช้แสดงใต้ปุ่มด่านและหน้าชนะ)
    /** เช่น 2 -> "★★☆" ; ถ้ายังไม่เคยชนะ (0) คืนข้อความว่าง */
    public static String starsText(int stars) {
        if (stars <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 3; i++) sb.append(i <= stars ? "★" : "☆");
        return sb.toString();
    }
}
