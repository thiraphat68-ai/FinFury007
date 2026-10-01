package com.example.finfury;

public abstract class Hero {
    protected String name;
    protected String subject;

    public Hero(String name, String subject) {
        this.name = name;
        this.subject = subject;
    }

    public String getName() { return name; }
    public String getSubject() { return subject; }

    /** สกิล 1 (ปุ่ม Skill 1) */
    public abstract void useSkill1(BattleContext ctx);

    /** สกิล 2 (ปุ่ม Skill 2) */
    public abstract void useSkill2(BattleContext ctx);

    /** Ultimate (ทำงานหลังตอบ quiz ถูก) ต้องเรียก ctx.onUltimateFinished() เมื่อจบ */
    public abstract void executeUltimateSkill(BattleContext ctx);

    /**
     * true = ตอบ quiz ถูกแล้วแค่ปลดล็อกปุ่ม ULT ให้ผู้เล่นกดเอง
     * false = ปล่อย Ultimate อัตโนมัติทันทีหลังตอบถูก (แบบเดิม)
     */
    public boolean usesUltimateButton() { return false; }
}