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

    /** เช่น 2 -> "★★☆" ; ถ้ายังไม่เคยชนะ (0) คืนข้อความว่าง */
    public static String starsText(int stars) {
        if (stars <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 3; i++) sb.append(i <= stars ? "★" : "☆");
        return sb.toString();
    }
}
