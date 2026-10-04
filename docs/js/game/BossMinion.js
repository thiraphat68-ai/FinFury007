// Port of BossMinion.java: Kraken Boss minions (stage-3 stats). NOT spawned by default in the web port
// (see BOSS_MINIONS in config.js): the web boss stage has the boss as its only enemy.
// If enabled: damage x0.25, HP x1.2, hitting the player slows it x0.7 for 2 s, and each minion that dies costs the boss 10 HP.
import SeaEnemy from './SeaEnemy.js';
import { stageConfig } from '../data/stages.js';

const DAMAGE_SCALE = 0.25;
const HP_SCALE = 1.2;
const SLOW_FACTOR = 0.7;
const SLOW_MS = 2000;
const BOSS_HP_LOSS = 10;

export default class BossMinion extends SeaEnemy {
  constructor(ctx, name, emoji, posX, posY) {
    super(ctx, name, emoji, posX, posY, stageConfig(3));
    this.isMinion = true;
  }

  damageScale() { return DAMAGE_SCALE; }
  hpScale() { return HP_SCALE; }

  onPlayerHit() { this.ctx.slowPlayer(SLOW_FACTOR, SLOW_MS); }

  onDefeated() {
    const boss = this.ctx.getEnemies().find((e) => e.isBoss && e.isAlive);
    if (boss) boss.takeFixedDamage(BOSS_HP_LOSS);
  }
}
