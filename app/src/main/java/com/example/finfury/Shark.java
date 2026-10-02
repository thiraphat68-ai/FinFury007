package com.example.finfury;

public class Shark extends Hero {
    public Shark() {
        super("Shark", "Circuits");
    }

    @Override
    public boolean usesUltimateButton() {
        return true;
    }

    @Override
    public void useSkill1(BattleContext ctx) {
        // พุ่งกัด 350 px ดาเมจ 3
        SkillEffects.dash(ctx, 350f, 3);
    }

    @Override
    public void useSkill2(BattleContext ctx) {
        // ยิงคลื่นไฟฟ้า ระยะ 1100 px ดาเมจ 2
        SkillEffects.projectile(ctx, android.R.drawable.ic_menu_send, 500, 1100f, 2);
    }

    @Override
    public void executeUltimateSkill(BattleContext ctx) {
        // พุ่งไล่กัดศัตรูทั้งหมด ตัวละ 6 ดาเมจ
        SkillEffects.thunderChain(ctx, 6);
    }
}