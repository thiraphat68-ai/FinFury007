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

    // ---------------------------------------------------------
    // สเตตัสพื้นฐาน (ฮีโร่แต่ละตัว override ให้ต่างกัน)
    // ---------------------------------------------------------
    /** เลือดสูงสุด */
    public int getMaxHp() { return 100; }

    /** ตัวคูณความเร็วเดินพื้นฐาน (1.0 = ปกติ) */
    public float getBaseSpeedMultiplier() { return 1f; }

    /** จำนวนฮิตที่ต้องสะสมจนสแตกเต็มและขึ้นควิซ */
    public int getStackNeeded() { return 10; }

    /** คูลดาวน์ Skill 1 / Skill 2 (มิลลิวินาที) ก่อนคูณตัวลดคูลดาวน์ของสกิล */
    public long getSkill1CooldownMs() { return 500; }
    public long getSkill2CooldownMs() { return 500; }

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

    // ---------------------------------------------------------
    // ชื่อ / ไอคอน / คำอธิบายสกิล (ใช้บนปุ่มในการต่อสู้ และหน้าเลือกฮีโร่)
    // ไอคอนเป็นอีโมจิ ไม่ต้องมีไฟล์ภาพ ฮีโร่แต่ละตัว override ให้ตรงกับสกิลของตัวเอง
    // ---------------------------------------------------------
    public String getSkill1Name() { return "Skill 1"; }
    public String getSkill1Icon() { return "⚔️"; }
    public String getSkill1Description() { return ""; }

    public String getSkill2Name() { return "Skill 2"; }
    public String getSkill2Icon() { return "🌀"; }
    public String getSkill2Description() { return ""; }

    public String getUltimateName() { return "Ultimate"; }
    public String getUltimateIcon() { return "💥"; }
    public String getUltimateDescription() { return ""; }
}