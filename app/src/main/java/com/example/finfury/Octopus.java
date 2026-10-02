package com.example.finfury;

public class Octopus extends Hero {
    public Octopus() {
        super("Octopus", "Programming");
    }

    @Override
    public boolean usesUltimateButton() {
        return true;
    }

    @Override
    public void useSkill1(BattleContext ctx) {
        // พุ่งพ่นหมึก 320 px ดาเมจ 3
        SkillEffects.dash(ctx, 320f, 3);
    }

    @Override
    public void useSkill2(BattleContext ctx) {
        // ยิงหมึกพ่นใส่ศัตรู ระยะ 1100 px ดาเมจ 2
        SkillEffects.projectile(ctx, android.R.drawable.ic_menu_send, 500, 1100f, 2);
    }

    @Override
    public void executeUltimateSkill(BattleContext ctx) {
        // พุ่งระเบิดหมึกไล่ศัตรูทั้งหมด ตัวละ 6 ดาเมจ
        SkillEffects.thunderChain(ctx, 6);
    }
}