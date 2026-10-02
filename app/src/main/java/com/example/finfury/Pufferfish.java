package com.example.finfury;

public class Pufferfish extends Hero {
    public Pufferfish() {
        super("Pufferfish", "Chemistry");
    }

    @Override
    public boolean usesUltimateButton() {
        return true;
    }

    @Override
    public void useSkill1(BattleContext ctx) {
        // พุ่งพองตัว 300 px ดาเมจ 2
        SkillEffects.dash(ctx, 300f, 2);
    }

    @Override
    public void useSkill2(BattleContext ctx) {
        // ยิงกระสุนพิษ ระยะ 1100 px ดาเมจ 2
        SkillEffects.projectile(ctx, android.R.drawable.ic_menu_send, 500, 1100f, 2);
    }

    @Override
    public void executeUltimateSkill(BattleContext ctx) {
        // พุ่งระเบิดพิษไล่ศัตรูทั้งหมด ตัวละ 5 ดาเมจ
        SkillEffects.thunderChain(ctx, 5);
    }
}