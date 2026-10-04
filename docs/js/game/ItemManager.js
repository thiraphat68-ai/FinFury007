// Port of ItemManager.java: items on the map. Enemies drop one with 25% chance, one more spawns by itself every 12-15 s
// (max 2 random + 5 total on the map). Swim into an item to pick it up; it floats 8 s (blinks the last 2 s).
// All timing uses the battle's game time, so everything freezes while paused or during a quiz.
import { FONT, dp } from '../config.js';
import { playSfx } from '../core/audio.js';

// ---- balance values (same as Java) ----
const DROP_CHANCE = 0.25;
const LIFETIME_MS = 8000;
const BLINK_MS = 2000;
const SPAWN_MIN_MS = 12000;
const SPAWN_MAX_MS = 15000;
const MAX_RANDOM_ON_MAP = 2;
const MAX_TOTAL_ON_MAP = 5;
const PICKUP_RADIUS_DP = 36;
const HEAL_PERCENT = 0.15;
const ENERGY_AMOUNT = 2;
const SHIELD_MS = 10000;
const SPEED_MS = 6000;
export const SPEED_FACTOR = 1.3;
const COOLDOWN_MS = 8000;
export const COOLDOWN_FACTOR = 0.5;
const DAMAGE_MS = 8000;
export const DAMAGE_BONUS = 1;
const FREEZE_MS = 4000;
const FREEZE_FACTOR = 0.5;
const POPUP_MS = 1500;

const sec = (ms) => String(ms / 1000);

/** Item types: icon, ring colour, drop weight, Thai name and one-line description built from the constants above. */
export const ITEM_TYPES = {
  HEART: { key: 'HEART', icon: '❤️', color: '#E53935', popup: '#EF5350', weight: 25, nameTh: 'หัวใจ',
    descTh: `ฟื้นเลือด ${Math.round(HEAL_PERCENT * 100)}%` },
  ENERGY: { key: 'ENERGY', icon: '⚡', color: '#FBC02D', popup: '#FDD835', weight: 25, nameTh: 'พลังงาน',
    descTh: `เพิ่มสแตก +${ENERGY_AMOUNT}` },
  SHIELD: { key: 'SHIELD', icon: '🛡️', color: '#1E88E5', popup: '#64B5F6', weight: 15, nameTh: 'โล่',
    descTh: `กันดาเมจ 1 ครั้ง (นาน ${sec(SHIELD_MS)} วินาที)` },
  SPEED: { key: 'SPEED', icon: '💨', color: '#26C6DA', popup: '#4DD0E1', weight: 12, nameTh: 'ว่ายเร็ว',
    descTh: `ความเร็ว x${SPEED_FACTOR.toFixed(1)} นาน ${sec(SPEED_MS)} วินาที` },
  COOLDOWN: { key: 'COOLDOWN', icon: '⏱️', color: '#8E24AA', popup: '#BA68C8', weight: 12, nameTh: 'ลดคูลดาวน์',
    descTh: `สกิลฟื้นเร็วขึ้น ${(1 / COOLDOWN_FACTOR).toFixed(0)} เท่า นาน ${sec(COOLDOWN_MS)} วินาที` },
  DAMAGE: { key: 'DAMAGE', icon: '🔥', color: '#FB8C00', popup: '#FFA726', weight: 6, nameTh: 'ดาเมจเพิ่ม',
    descTh: `+${DAMAGE_BONUS} ดาเมจต่อฮิต นาน ${sec(DAMAGE_MS)} วินาที` },
  FREEZE: { key: 'FREEZE', icon: '🧊', color: '#4FC3F7', popup: '#81D4FA', weight: 5, nameTh: 'แช่แข็ง',
    descTh: `ศัตรูช้าลง ${Math.round((1 - FREEZE_FACTOR) * 100)}% นาน ${sec(FREEZE_MS)} วินาที` },
};
export const ITEM_LIST = Object.values(ITEM_TYPES);
export const itemLabel = (t) => `${t.icon} ${t.nameTh}: ${t.descTh}`;     // e.g. "🛡️ โล่: กันดาเมจ 1 ครั้ง (นาน 10 วินาที)"

export default class ItemManager {
  /**
   * @param scene   BattleScene (also the host: getPlayerHp, getPlayerMaxHp, healPlayer, addStack, freezeEnemies)
   * @param randomSpawnEnabled  periodic random spawns
   */
  constructor(scene, randomSpawnEnabled = true) {
    this.scene = scene;
    this.randomSpawnEnabled = randomSpawnEnabled;
    this.items = [];
    this.nextSpawnMs = SPAWN_MIN_MS + Math.random() * (SPAWN_MAX_MS - SPAWN_MIN_MS);
    this.timeMs = 0;
    this.shieldMs = 0; this.speedMs = 0; this.cooldownMs = 0; this.damageMs = 0;
    this.lastBuffKey = -1;
    this.totalW = scene.W;
    this.totalH = scene.H;
  }

  // ---- buff state read by the battle ----
  speedFactor() { return this.speedMs > 0 ? SPEED_FACTOR : 1; }
  cooldownFactor() { return this.cooldownMs > 0 ? COOLDOWN_FACTOR : 1; }
  damageBonus() { return this.damageMs > 0 ? DAMAGE_BONUS : 0; }

  /** Shield: returns true if it absorbed a hit (the shield is used up). */
  absorbHit() {
    if (this.shieldMs <= 0) return false;
    this.shieldMs = 0;
    const p = this.scene.playerCenter();
    this.popup(p.x, this.scene.py, '🛡️ กันได้!', '#64B5F6');
    return true;
  }

  // ---- drops / spawns ----
  onEnemyDefeated(cx, cy, forceHeart) {
    if (forceHeart) this.spawn(ITEM_TYPES.HEART, cx, cy, false);
    else if (Math.random() < DROP_CHANCE) this.spawn(this.randomType(), cx, cy, false);
  }

  randomType() {
    let total = 0;
    for (const t of ITEM_LIST) total += t.weight;
    let roll = Math.floor(Math.random() * total);
    for (const t of ITEM_LIST) {
      roll -= t.weight;
      if (roll < 0) return t;
    }
    return ITEM_TYPES.HEART;
  }

  spawn(type, cx, cy, fromRandom) {
    const s = this.scene;
    if (this.items.length >= MAX_TOTAL_ON_MAP) return;
    const size = dp(46);
    const c = s.add.container(0, 0).setDepth(9);
    const ring = s.add.circle(0, 0, size / 2, 0x000000, 0.59).setStrokeStyle(dp(3), Phaser.Display.Color.HexStringToColor(type.color).color);
    const icon = s.add.text(0, 0, type.icon, { fontFamily: FONT, fontSize: `${Math.round(dp(24))}px` }).setOrigin(0.5);
    c.add([ring, icon]);
    const x = Math.max(size / 2, Math.min(this.totalW - size / 2, cx));
    const y = Math.max(size / 2, Math.min(this.totalH - size / 2, cy));
    c.setPosition(x, y).setScale(0);
    s.tweens.add({ targets: c, scale: 1, duration: 200 });
    this.items.push({ type, view: c, baseY: y, fromRandom, ageMs: 0, bobPhase: Math.random() * 6.28 });
  }

  randomOnMap() {
    let n = 0;
    for (let i = 0; i < this.items.length; i++) if (this.items[i].fromRandom) n++;
    return n;
  }

  // ---- per frame (dtSec, only while the game runs) ----
  update(dtSec) {
    const s = this.scene;
    const dtMs = dtSec * 1000;
    this.timeMs += dtMs;
    this.shieldMs = Math.max(0, this.shieldMs - dtMs);
    this.speedMs = Math.max(0, this.speedMs - dtMs);
    this.cooldownMs = Math.max(0, this.cooldownMs - dtMs);
    this.damageMs = Math.max(0, this.damageMs - dtMs);

    if (this.randomSpawnEnabled) {
      this.nextSpawnMs -= dtMs;
      if (this.nextSpawnMs <= 0) {
        this.nextSpawnMs = SPAWN_MIN_MS + Math.random() * (SPAWN_MAX_MS - SPAWN_MIN_MS);
        if (this.randomOnMap() < MAX_RANDOM_ON_MAP) {
          const margin = dp(60);
          this.spawn(this.randomType(), margin + Math.random() * Math.max(1, this.totalW - margin * 2),
            margin + Math.random() * Math.max(1, this.totalH - margin * 2), true);
        }
      }
    }

    const pc = s.playerCenter();
    const pickup = dp(PICKUP_RADIUS_DP) + Math.min(s.pw, s.ph) / 2;
    for (let i = this.items.length - 1; i >= 0; i--) {
      const it = this.items[i];
      it.ageMs += dtMs;
      if (it.ageMs >= LIFETIME_MS) { this.remove(i); continue; }
      // bob up and down, blink in the last 2 s
      it.view.y = it.baseY + Math.sin(this.timeMs / 350 + it.bobPhase) * dp(4);
      const left = LIFETIME_MS - it.ageMs;
      it.view.setAlpha(left < BLINK_MS && Math.floor(left / 150) % 2 === 0 ? 0.3 : 1);
      if (Math.hypot(pc.x - it.view.x, pc.y - it.view.y) <= pickup && this.canPickup(it.type)) {
        this.apply(it.type);
        this.remove(i);
      }
    }
  }

  canPickup(type) {
    // a heart is left on the map while the HP is full
    return type !== ITEM_TYPES.HEART || this.scene.hp < this.scene.maxHp;
  }

  apply(type) {
    const s = this.scene;
    playSfx(s, 'sfx_ult_ready');
    switch (type.key) {
      case 'HEART': s.healPlayer(Math.max(1, Math.round(s.maxHp * HEAL_PERCENT))); break;
      case 'ENERGY': s.addStack(ENERGY_AMOUNT); break;
      case 'SHIELD': this.shieldMs = SHIELD_MS; break;
      case 'SPEED': this.speedMs = SPEED_MS; break;
      case 'COOLDOWN': this.cooldownMs = COOLDOWN_MS; break;
      case 'DAMAGE': this.damageMs = DAMAGE_MS; break;
      case 'FREEZE': s.freezeEnemies(FREEZE_FACTOR, FREEZE_MS); break;
      default: break;
    }
    const pc = s.playerCenter();
    this.popup(pc.x, s.py - dp(8), itemLabel(type), type.popup);
  }

  /** Buff status for the HUD, e.g. "🛡️ 8s  💨 4s" (empty = no buff). */
  buffText() {
    const parts = [];
    const add = (icon, ms) => { if (ms > 0) parts.push(`${icon} ${this.secs(ms)}s`); };
    add('🛡️', this.shieldMs); add('💨', this.speedMs); add('⏱️', this.cooldownMs); add('🔥', this.damageMs);
    return parts.join('  ');
  }

  /** True when the HUD text should be refreshed (counts whole seconds, so not every frame). */
  buffTextChanged() {
    const key = this.secs(this.shieldMs) | (this.secs(this.speedMs) << 6) | (this.secs(this.cooldownMs) << 12) | (this.secs(this.damageMs) << 18);
    if (key === this.lastBuffKey) return false;
    this.lastBuffKey = key;
    return true;
  }

  secs(ms) { return ms <= 0 ? 0 : Math.min(63, Math.ceil(ms / 1000)); }

  /** Floating message (icon + name + description) that stays ~1.5 s and never leaves the play area. */
  popup(cx, y, text, color) {
    const s = this.scene;
    const margin = dp(8);
    const t = s.add.text(0, 0, text, {
      fontFamily: FONT, fontSize: `${Math.round(dp(16))}px`, color, stroke: '#000000', strokeThickness: 6,
      align: 'center', wordWrap: { width: this.totalW - margin * 2 },
    }).setOrigin(0, 0).setDepth(30);
    const w = t.width;
    t.setPosition(Math.max(margin, Math.min(this.totalW - w - margin, cx - w / 2)), Math.max(margin, y - dp(24)));
    s.tweens.add({ targets: t, y: t.y - dp(50), alpha: 0, duration: POPUP_MS, onComplete: () => t.destroy() });
  }

  remove(index) {
    const it = this.items.splice(index, 1)[0];
    this.scene.tweens.killTweensOf(it.view);
    it.view.destroy();
  }

  clear() {
    for (let i = this.items.length - 1; i >= 0; i--) this.remove(i);
    this.shieldMs = this.speedMs = this.cooldownMs = this.damageMs = 0;
  }
}
