package com.example.finfury;

public class Shark extends Hero {
    public Shark() {
        super("Shark", "Circuits");
    }

    @Override
    public void useSkill1(BattleContext ctx) {
        // พุ่งกัด 300 px ดาเมจ 2
        SkillEffects.dash(ctx, 300f, 2);
    }

    @Override
    public void useSkill2(BattleContext ctx) {
        // ยิงกระสุน ระยะ 1100 px ดาเมจ 1
        SkillEffects.projectile(ctx, android.R.drawable.ic_menu_compass, 550, 1100f, 1);
    }

    @Override
    public void executeUltimateSkill(BattleContext ctx) {
        // พุ่งไล่ศัตรูทั้งหมด ตัวละ 5 ดาเมจ
        SkillEffects.thunderChain(ctx, 5);
    }
}