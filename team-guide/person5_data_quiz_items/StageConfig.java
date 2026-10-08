// =====================================================================================
// [คนที่ 5 - ข้อมูล ควิซ ไอเทม และสมดุลเกม]  ไฟล์: StageConfig.java  (59 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/StageConfig.java
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   "ตารางความยากของแต่ละด่าน" (5 ด่าน) เป็นข้อมูลล้วน ๆ (immutable: ทุกฟิลด์ final) เรียกด้วย StageConfig.forStage(เลขด่าน)
//   คนที่เรียกใช้: BattleActivity (อ่านด่านอะไรมีศัตรูกี่ตัว ฯลฯ) , SeaEnemy.applyStageModifiers (คูณสเตตัสศัตรู) , BossMinion (ใช้ค่าของด่าน 3)
//
// [ตารางค่าในโค้ด (ฟิลด์ -> ความหมาย)]
//   boss              ด่านบอส : มีแค่ Kraken Boss (ด่าน 5)
//   includeSquid      มีหมึกในด่านไหม
//   enemyCount        จำนวนศัตรู (ข้อมูลอ้างอิง)
//   speedMul / hpMul  คูณความเร็ว / เลือด ศัตรู
//   attackIntervalMs  เวลาพักระหว่างการโจมตีของศัตรูแต่ละตัว (ยิ่งน้อยยิ่งโจมตีถี่ = ยากขึ้น)
//   dashSpeedMul      คูณความเร็วพุ่ง
//   dualAttack        โจมตีพร้อมกันได้ 2 ตัว
//   ultDebuffEveryHits ถูกตีครบทุก N ครั้ง พลัง Ultimate ที่ได้ลดลง (0 = ปิด) , ultGainFactor ตัวคูณพลังที่ได้ระหว่างติดดีบัฟ
//   damageMul         คูณดาเมจศัตรู +20% ต่อด่าน (คำนวณใน constructor: 1 + 0.2 x (ด่าน - 1))
//
// [จะปรับความยากยังไง]  แก้ตัวเลขในบรรทัด case ของด่านนั้น ใน forStage (เรียงพารามิเตอร์ตาม constructor)
//    forStage(2): new StageConfig(2, boss, squid, count, speedMul, hpMul, interval, dashMul, dual, debuffEvery, gain)
// [จะเพิ่มด่านที่ 6] เพิ่ม case 6 ใน forStage + แก้ MAX_STAGES (GameProgress และ BattleActivity) + ปุ่มด่านใน activity_main.xml + พื้นหลัง bg_stage_6
// =====================================================================================
package com.example.finfury;

// [คอมเมนต์คลาส - โดยเจ้าของไฟล์เดิม] "ค่าที่ด่านไม่ได้ระบุใหม่ ใช้ต่อจากด่านก่อนหน้า" หมายถึงความยากสะสม
/**
 * ค่าความยากของแต่ละด่าน (ศัตรูกี่ตัว / เร็วแค่ไหน / เลือด / คูลดาวน์ / กติกาพิเศษ)
 * ค่าที่ด่านไม่ได้ระบุใหม่ ใช้ต่อจากด่านก่อนหน้า (ความยากสะสม)
 */
// [ฟิลด์ทั้งหมด public final] อ่านได้จากไฟล์อื่น แก้ไม่ได้หลังสร้าง (ปลอดภัย)
public final class StageConfig {
    public final int stage;
    public final boolean boss;           // ด่านบอส: มีแค่ Kraken Boss ตัวเดียว
    public final boolean includeSquid;   // false = ไม่มีหมึก (ด่าน 4)
    public final int enemyCount;
    public final float speedMul;         // คูณความเร็วเคลื่อนที่
    public final float hpMul;            // คูณเลือด
    public final long attackIntervalMs;  // เวลาพักระหว่างการโจมตีของศัตรูแต่ละตัว (รวมช่วงเตือน)
    public final float dashSpeedMul;     // คูณความเร็วพุ่ง
    public final boolean dualAttack;     // true = โจมตีพร้อมกันได้ 2 ตัว (Dual Attacker)
    public final int ultDebuffEveryHits; // ถูกตีครบทุก N ครั้ง ศัตรูลดพลัง Ultimate ที่ได้ (0 = ปิด)
    public final float ultGainFactor;    // พลังที่ได้ระหว่างติดดีบัฟ (0.7 = ลด 30%)
    public final float damageMul;        // คูณดาเมจศัตรู: +20% ต่อด่านที่ผ่าน (ด่าน 1 = x1.0, ด่าน 5 = x1.8)

    // [constructor private] สร้างได้เฉพาะภายในคลาสนี้ (ผ่าน forStage) ค่า damageMul ไม่รับจากภายนอก คำนวณตามเลขด่าน
    private StageConfig(int stage, boolean boss, boolean includeSquid, int enemyCount, float speedMul,
                        float hpMul, long attackIntervalMs, float dashSpeedMul, boolean dualAttack,
                        int ultDebuffEveryHits, float ultGainFactor) {
        this.stage = stage;
        this.boss = boss;
        this.includeSquid = includeSquid;
        this.enemyCount = enemyCount;
        this.speedMul = speedMul;
        this.hpMul = hpMul;
        this.attackIntervalMs = attackIntervalMs;
        this.dashSpeedMul = dashSpeedMul;
        this.dualAttack = dualAttack;
        this.ultDebuffEveryHits = ultDebuffEveryHits;
        this.ultGainFactor = ultGainFactor;
        this.damageMul = 1f + 0.20f * (stage - 1);
    }

    // [ความถี่การโจมตี] ด่าน 1 = 3000 ms แต่ละด่านถี่ขึ้น 1.4 เท่าแบบทบต่อ (ด่าน 2 = 2143 , 3 = 1531 , 4 = 1094) แต่ไม่ต่ำกว่า 1300 ms
    //   [แก้ยังไง] BASE_ATTACK_INTERVAL_MS (เริ่มต้น) , ATTACK_SPEED_PER_STAGE (อัตราเร่ง) , MIN_ATTACK_INTERVAL_MS (ขั้นต่ำกันโหดเกิน)
    /** ผ่านทุกด่าน ศัตรูโจมตีถี่ขึ้น 1.4 เท่า (ทบต่อด่าน): ด่าน 1 = 3000 ms, ด่าน 2 ≈ 2143, ด่าน 3 ≈ 1531, ด่าน 4 ≈ 1094 */
    private static final long BASE_ATTACK_INTERVAL_MS = 3000;
    private static final float ATTACK_SPEED_PER_STAGE = 1.4f;
    private static final long MIN_ATTACK_INTERVAL_MS = 1300;

    // [intervalForStage] สูตร = max(ขั้นต่ำ, ปัดเศษ(ฐาน / ตัวเร่ง ยกกำลัง (ด่าน - 1)))
    private static long intervalForStage(int stage) {
        // มีขั้นต่ำ: ด่าน 4 ถ้าเร็วถึง ~1100 ms ศัตรูจะโจมตีแทบไม่เว้นช่วงให้หายใจ
        return Math.max(MIN_ATTACK_INTERVAL_MS,
                Math.round(BASE_ATTACK_INTERVAL_MS / Math.pow(ATTACK_SPEED_PER_STAGE, stage - 1)));
    }

    // [forStage] เมธอดเดียวที่ภายนอกเรียก : switch ตามเลขด่าน คืน StageConfig ที่ตั้งค่าไว้ (ด่านที่ไม่รู้จัก = ด่าน 1)
    //   ด่าน 1 : เร็ว 1.10 เลือด x1.15 ไม่มีดีบัฟ
    //   ด่าน 2 : เร็ว 1.20 เลือด x1.40 ถูกตีทุก 3 ครั้ง พลัง ULT เหลือ 85% (ที่ BattleActivity)
    //   ด่าน 3 : เร็ว 1.30 เลือด x1.65 โจมตีพร้อมกัน 2 ตัว ถูกตีทุก 2 ครั้ง ULT เหลือ 65%
    //   ด่าน 4 : เร็ว 1.40 เลือด x1.95 โจมตีพร้อมกัน 2 ตัว ถูกตีทุก 2 ครั้ง ULT เหลือ 60%
    //   ด่าน 5 : บอส (มีแค่บอสตัวเดียว ค่าอื่นเป็นค่ากลาง 1.0 เพราะบอสใช้ค่าของตัวเองใน KrakenBoss)
    //   [หมายเหตุ] ค่า "includeSquid" ของทุกด่านเป็น true ในโค้ดปัจจุบัน (คอมเมนต์ที่ฟิลด์บอกว่าด่าน 4 ไม่มีหมึก แต่ค่าจริงคือมี)
    public static StageConfig forStage(int stage) {
        switch (stage) {
            case 2:  return new StageConfig(2, false, true, 5, 1.20f, 1.40f, intervalForStage(2), 1.10f, false, 3, 0.85f);
            case 3:  return new StageConfig(3, false, true, 5, 1.30f, 1.65f, intervalForStage(3), 1.20f, true, 2, 0.65f);
            case 4:  return new StageConfig(4, false, true, 5, 1.40f, 1.95f, intervalForStage(4), 1.30f, true, 2, 0.6f);
            case 5:  return new StageConfig(5, true, true, 1, 1.0f, 1.0f, 3000, 1.0f, false, 0, 1f);
            case 1:
            default: return new StageConfig(1, false, true, 5, 1.10f, 1.15f, intervalForStage(1), 1.05f, false, 0, 1f);
        }
    }
}
