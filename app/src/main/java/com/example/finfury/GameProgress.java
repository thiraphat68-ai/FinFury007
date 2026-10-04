package com.example.finfury;

import android.content.Context;
import android.content.SharedPreferences;

/** ความคืบหน้าของผู้เล่นที่เก็บใน SharedPreferences "GamePrefs": ดาวที่ดีที่สุดของแต่ละด่าน */
public final class GameProgress {

    private static final String PREFS = "GamePrefs";

    private GameProgress() {}

    private static String starsKey(int stageId) {
        return "stars_stage_" + stageId;
    }

    /** ดาวที่ดีที่สุดของด่านนี้ (0 = ยังไม่เคยชนะ) */
    public static int getStars(Context context, int stageId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getInt(starsKey(stageId), 0);
    }

    /** บันทึกเฉพาะเมื่อดีกว่าสถิติเดิม */
    public static void saveBestStars(Context context, int stageId, int stars) {
        if (stars <= getStars(context, stageId)) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(starsKey(stageId), stars).apply();
    }

    public static final int MAX_STAGES = 5;

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

    /** ล้างความคืบหน้า: ดาทุกด่านและการปลดล็อก (กลับไปเหลือด่าน 1) ไม่แตะค่าตั้งเสียง/สอนเล่น */
    public static void reset(Context context) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int stage = 1; stage <= MAX_STAGES; stage++) editor.remove(starsKey(stage));
        editor.remove("unlocked_stage");
        editor.apply();
    }

    /** เช่น 2 -> "★★☆" ; ถ้ายังไม่เคยชนะ (0) คืนข้อความว่าง */
    public static String starsText(int stars) {
        if (stars <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 3; i++) sb.append(i <= stars ? "★" : "☆");
        return sb.toString();
    }
}
