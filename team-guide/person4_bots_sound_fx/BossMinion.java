// =====================================================================================
// [คนที่ 4 - บอท เสียง และเอฟเฟกต์]  ไฟล์: BossMinion.java  (43 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/BossMinion.java
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   ลูกน้องของ Kraken Boss ในด่าน 5 (extends SeaEnemy) ใช้ศัตรู 5 ชนิดเดิม (ปู แมงกะพรุน เต่า หมึก ดาวทะเล) แต่ใช้ค่าความยากของ "ด่าน 3"
//   เปลี่ยนจากศัตรูปกติ 4 อย่าง ด้วยการ override เมธอด hook ที่ SeaEnemy เตรียมไว้ (ตัวอย่างที่ดีของ OOP):
//     damageScale   ดาเมจเหลือ 1/4      hpScale  เลือดเพิ่ม 1.2 เท่า
//     onPlayerHit   โดนแล้วผู้เล่นช้าลง (0.7 เท่า นาน 2 วินาที) แทนเอฟเฟกต์พิเศษของเต่า/ดาวทะเล
//     onDefeated    ตายแล้วเรียก KrakenBoss.takeFixedDamage ลดเลือดบอส 10
//   สร้างโดย BattleActivity.spawnBossMinions (5 ตัว ฝั่งซ้ายของจอ) และถูกเก็บทิ้งตอนบอสเข้าเฟส 2 (removeSilently)
//
// [จะแก้ยังไง] ลูกน้องอ่อน/แข็งเกินไป: แก้ DAMAGE_SCALE / HP_SCALE | ลูกน้องกระทบบอสมาก/น้อย: แก้ BOSS_HP_LOSS
// =====================================================================================
package com.example.finfury;

// [คอมเมนต์คลาส - โดยเจ้าของไฟล์เดิม]
/**
 * ลูกน้อง Kraken Boss ด่าน 5: ศัตรูชุดเดียวกับด่าน 3 (ใช้ค่าของด่าน 3)
 * - ดาเมจเหลือ 1/4, เลือดเพิ่ม 1.2 เท่า
 * - โจมตีโดนผู้เล่น = ความเร็วเหลือ 0.7 เท่า (แทนเอฟเฟกต์เต่า/ดาวทะเลปกติ)
 * - ตายแล้วบอสเสียเลือด 10
 */
public class BossMinion extends SeaEnemy {

    // [ค่าคงที่] DAMAGE_SCALE ตัวคูณดาเมจ | HP_SCALE ตัวคูณเลือด | SLOW_FACTOR/SLOW_MS ความช้าที่ผู้เล่นโดนเมื่อถูกลูกน้องตี | BOSS_HP_LOSS เลือดบอสที่หายเมื่อลูกน้องตาย 1 ตัว
    private static final float DAMAGE_SCALE = 0.25f;
    private static final float HP_SCALE = 1.2f;
    private static final float SLOW_FACTOR = 0.7f;   // ความเร็วเหลือ 0.7 เท่า
    private static final long SLOW_MS = 2000;
    private static final int BOSS_HP_LOSS = 10;

    // [constructor] ส่ง StageConfig.forStage(3) ให้ SeaEnemy เสมอ (ใช้ค่าความยากด่าน 3 ไม่ว่าอยู่ด่านไหน)
    public BossMinion(BattleContext ctx, String name, String emoji, float posX, float posY) {
        super(ctx, name, emoji, posX, posY, StageConfig.forStage(3));
    }

    // [damageScale / hpScale] ตัวคูณที่ SeaEnemy.applyStageModifiers เรียกใช้ตอนคำนวณสเตตัส
    @Override
    protected float damageScale() { return DAMAGE_SCALE; }

    @Override
    protected float hpScale() { return HP_SCALE; }

    // [onPlayerHit] แทนที่ผลเดิมของ SeaEnemy ทั้งหมด (ไม่ต้องเช็กชื่อศัตรู) : ผู้เล่นช้าลงทุกครั้งที่โดน
    @Override
    protected void onPlayerHit(float dirX, float dirY) {
        ctx.slowPlayer(SLOW_FACTOR, SLOW_MS);
    }

    // [onDefeated] ตอนลูกน้องตาย: หาบอสที่ยังมีชีวิตในรายชื่อศัตรู แล้วลดเลือดบอสแบบคงที่ (ไม่คิดโบนัส) เพียงตัวเดียว
    @Override
    protected void onDefeated() {
        java.util.List<SeaEnemy> all = ctx.getEnemies();
        for (int i = 0; i < all.size(); i++) {
            SeaEnemy e = all.get(i);
            if (e instanceof KrakenBoss && e.isAlive) {
                ((KrakenBoss) e).takeFixedDamage(BOSS_HP_LOSS);
                break;
            }
        }
    }
}
