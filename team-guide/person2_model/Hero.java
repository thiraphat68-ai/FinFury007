// =====================================================================================
// [คนที่ 2 - Model ฮีโร่และสกิล]  ไฟล์: Hero.java  (60 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/Hero.java
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   "คลาสแม่" (abstract class) ของฮีโร่ทุกตัว กำหนดว่าฮีโร่ต้องมีอะไรบ้าง:
//     - สเตตัส: เลือด, ความเร็ว, จำนวนสแตกที่ต้องสะสม, คูลดาวน์สกิล
//     - สกิล 3 อย่าง: useSkill1, useSkill2, executeUltimateSkill  (บังคับให้ลูกทุกตัวเขียนเอง = abstract)
//     - ข้อความ/ไอคอน/คำอธิบายสกิล (ใช้แสดงบนปุ่มและหน้าเลือกฮีโร่)
//   ฮีโร่ 5 ตัว (Swordfish, Pufferfish, Shark, Octopus, ElectricEel) extends คลาสนี้
//
// [หลัก OOP ที่ใช้]
//   Inheritance (สืบทอด)  : ฮีโร่ทุกตัว extends Hero
//   Polymorphism          : BattleActivity เรียก playerHero.useSkill1(this) โดยไม่ต้องรู้ว่าเป็นตัวไหน
//   Encapsulation         : name/subject เป็น protected อ่านผ่าน getter
//
// [จะ "เพิ่มฮีโร่ตัวใหม่" ยังไง]  (เช็กลิสต์)
//   1) สร้างไฟล์ใหม่ เช่น Dolphin.java  public class Dolphin extends Hero
//   2) เขียน constructor เรียก super("ชื่อ", "วิชา")  (วิชาต้องตรงกับคำใน QuestionBank.forSubject)
//   3) override useSkill1 / useSkill2 / executeUltimateSkill (ต้องเรียก ctx.onUltimateFinished() ตอน Ultimate จบ)
//   4) override ค่าสเตตัสที่อยากให้ต่างจากค่าเริ่มต้น (getMaxHp ฯลฯ) และชื่อ/ไอคอน/คำอธิบายสกิล
//   5) เพิ่ม case ใน HeroFactory.java
//   6) เพิ่มรูป hero_N ใน res/drawable-nodpi/ , เพิ่มการ์ดฮีโร่ใน activity_main.xml (คนที่ 3)
//      และเพิ่ม case ใน BattleActivity.setupHeroAndSkills (คนที่ 3)
//
// [จะ "แก้" ยังไง]
//   - แก้ค่าเริ่มต้นของ "ทุกฮีโร่" แก้ที่ไฟล์นี้ | แก้เฉพาะฮีโร่หนึ่งตัว ให้ override ในไฟล์ของฮีโร่ตัวนั้น
// =====================================================================================
package com.example.finfury;

public abstract class Hero {
    // ชื่อฮีโร่ที่แสดงบนหน้าจอ และ "วิชา" ที่ใช้เลือกชุดโจทย์ quiz
    protected String name;
    protected String subject;

    // constructor: ลูกทุกตัวต้องส่งชื่อและวิชามาที่นี่ (ผ่าน super(...))
    public Hero(String name, String subject) {
        this.name = name;
        this.subject = subject;
    }

    // getter: ให้ไฟล์อื่นอ่านชื่อ/วิชาได้ แต่แก้ค่าตรง ๆ ไม่ได้
    public String getName() { return name; }
    public String getSubject() { return subject; }

    // ---------------------------------------------------------
    // สเตตัสพื้นฐาน (ฮีโร่แต่ละตัว override ให้ต่างกัน)
    // ---------------------------------------------------------
    /** เลือดสูงสุด */
    // [แก้ยังไง] อยากให้ฮีโร่ตัวไหนเลือดเยอะขึ้น: override ในไฟล์ฮีโร่นั้น  @Override public int getMaxHp() { return 150; }
    public int getMaxHp() { return 100; }

    /** ตัวคูณความเร็วเดินพื้นฐาน (1.0 = ปกติ) */
    // [ค่ายิ่งมาก = ว่ายเร็วขึ้น] ถูกคูณกับ MAX_SPEED (700) ใน BattleActivity.updateFish
    public float getBaseSpeedMultiplier() { return 1f; }

    /** จำนวนฮิตที่ต้องสะสมจนสแตกเต็มและขึ้นควิซ */
    // [ค่ายิ่งน้อย = ได้ Ultimate เร็วขึ้น]
    public int getStackNeeded() { return 10; }

    /** คูลดาวน์ Skill 1 / Skill 2 (มิลลิวินาที) ก่อนคูณตัวลดคูลดาวน์ของสกิล */
    // [หน่วยมิลลิวินาที] 500 = 0.5 วินาที  แก้เพื่อปรับความถี่ที่กดสกิลได้
    public long getSkill1CooldownMs() { return 500; }
    public long getSkill2CooldownMs() { return 500; }

    /** สกิล 1 (ปุ่ม Skill 1) */
    // [abstract] ไม่มีโค้ดในนี้ ลูกทุกตัวต้องเขียนเอง ไม่งั้น compile ไม่ผ่าน
    //            ctx คือ "ช่องทางคุยกับฉากต่อสู้" (ดู BattleContext.java) ใช้เรียก getEnemies(), onHitEnemySuccess() ฯลฯ
    public abstract void useSkill1(BattleContext ctx);

    /** สกิล 2 (ปุ่ม Skill 2) */
    public abstract void useSkill2(BattleContext ctx);

    /** Ultimate (ทำงานหลังตอบ quiz ถูก) ต้องเรียก ctx.onUltimateFinished() เมื่อจบ */
    // [ระวัง] ถ้าลืมเรียก ctx.onUltimateFinished() สแตกจะไม่รีเซ็ตและปุ่มสกิลจะกดไม่ได้ค้าง
    public abstract void executeUltimateSkill(BattleContext ctx);

    /**
     * true = ตอบ quiz ถูกแล้วแค่ปลดล็อกปุ่ม ULT ให้ผู้เล่นกดเอง
     * false = ปล่อย Ultimate อัตโนมัติทันทีหลังตอบถูก (แบบเดิม)
     */
    // [แก้ยังไง] อยากให้ฮีโร่ตัวไหนมีปุ่ม ULT แยก: override ให้ return true (ดู BattleActivity.setupQuiz/ปุ่ม btnUltimate)
    public boolean usesUltimateButton() { return false; }

    // ---------------------------------------------------------
    // ชื่อ / ไอคอน / คำอธิบายสกิล (ใช้บนปุ่มในการต่อสู้ และหน้าเลือกฮีโร่)
    // ไอคอนเป็นอีโมจิ ไม่ต้องมีไฟล์ภาพ ฮีโร่แต่ละตัว override ให้ตรงกับสกิลของตัวเอง
    // ---------------------------------------------------------
    // ใครอ่านค่าเหล่านี้: ปุ่มสกิลใน BattleActivity.onCreate และ MainActivity.selectHero (หน้าเลือกฮีโร่)
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
