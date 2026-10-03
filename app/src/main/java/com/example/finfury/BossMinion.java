package com.example.finfury;

/**
 * ลูกน้อง Kraken Boss ด่าน 5: ศัตรูชุดเดียวกับด่าน 3 (ใช้ค่าของด่าน 3)
 * - ดาเมจเหลือ 1/4, เลือดเพิ่ม 1.2 เท่า
 * - โจมตีโดนผู้เล่น = ความเร็วเหลือ 0.7 เท่า (แทนเอฟเฟกต์เต่า/ดาวทะเลปกติ)
 * - ตายแล้วบอสเสียเลือด 10
 */
public class BossMinion extends SeaEnemy {

    private static final float DAMAGE_SCALE = 0.25f;
    private static final float HP_SCALE = 1.2f;
    private static final float SLOW_FACTOR = 0.7f;   // ความเร็วเหลือ 0.7 เท่า
    private static final long SLOW_MS = 2000;
    private static final int BOSS_HP_LOSS = 10;

    public BossMinion(BattleContext ctx, String name, String emoji, float posX, float posY) {
        super(ctx, name, emoji, posX, posY, StageConfig.forStage(3));
    }

    @Override
    protected float damageScale() { return DAMAGE_SCALE; }

    @Override
    protected float hpScale() { return HP_SCALE; }

    @Override
    protected void onPlayerHit(float dirX, float dirY) {
        ctx.slowPlayer(SLOW_FACTOR, SLOW_MS);
    }

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
