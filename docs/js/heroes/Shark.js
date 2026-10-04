// Port of Shark.java (Calculus): Derivative Bite (damage by speed) + Accumulating Wave (1, 2, 3 ... damage).
// Ultimate "Blood Frenzy": 6 s of speed x1.5 + cooldown x0.5; every bite that hits adds +1 damage to the next one (a miss resets).
import Hero from './Hero.js';
import * as fx from '../game/effects.js';
import { rectsOverlap, decelerate } from '../game/geometry.js';

// ---- Skill 1: Derivative Bite ----
const BITE_DISTANCE = 350;
const BITE_DURATION_MS = 150;
const BITE_MIN_DAMAGE = 2;     // standing still
const BITE_MAX_DAMAGE = 4;     // full speed
// ---- Skill 2: Accumulating Wave ----
const WAVE_DURATION_MS = 650;
const WAVE_DISTANCE = 1200;
const WAVE_RADIUS = 70;
const WAVE_THICKNESS = 36;
const WAVE_SPAN = 150;
// ---- Ultimate: Blood Frenzy ----
const FRENZY_MS = 6000;
const FRENZY_SPEED = 1.5;
const FRENZY_COOLDOWN = 0.5;

function blendColor(c0, c1, t) {
  t = Math.max(0, Math.min(1, t));
  const ch = (shift) => Math.round(((c0 >> shift) & 0xFF) + (((c1 >> shift) & 0xFF) - ((c0 >> shift) & 0xFF)) * t);
  return (ch(16) << 16) | (ch(8) << 8) | ch(0);
}

export default class Shark extends Hero {
  constructor() {
    super(3, 'Shark', 'Calculus');
    this.frenzyActive = false;
    this.biteStack = 0;
    this.frenzyG = null;
    this.frenzyToken = 0;
  }

  getMaxHp() { return 100; }
  getBaseSpeedMultiplier() { return 1.0; }
  getStackNeeded() { return 16; }
  getSkill1CooldownMs() { return 2000; }
  getSkill2CooldownMs() { return 4000; }
  usesUltimateButton() { return true; }
  getImageWidthDp() { return 105; }          // long body
  spriteFacesRight() { return true; }

  getRoleLabel() { return '(Assassin)'; }
  getSubjectLabel() { return 'แคลคูลัส (Calculus)'; }
  getProfileDescription() { return 'ใช้ความแม่นยำในการคำนวณเวกเตอร์เพื่อพุ่งโจมตีศัตรูอย่างรวดเร็ว'; }

  getSkill1Name() { return 'Derivative Bite'; }
  getSkill1Icon() { return '🦈'; }
  getSkill1Description() { return 'พุ่งกัด ดาเมจ 2-4 ตามความเร็วที่ว่ายอยู่ตอนกด ยิ่งเร็วยิ่งแรง'; }
  getSkill2Name() { return 'Accumulating Wave'; }
  getSkill2Icon() { return '🌊'; }
  getSkill2Description() { return 'คลื่นทะลุทุกตัว ตัวแรก 1 ดาเมจ ตัวถัดไปบวกเพิ่มทีละ 1 (1, 2, 3...)'; }
  getUltimateName() { return 'Blood Frenzy'; }
  getUltimateIcon() { return '🩸'; }
  getUltimateDescription() { return '6 วินาที ว่ายเร็วขึ้น คูลดาวน์สั้นลง กัดโดนติดกันแรงขึ้นทีละ 1 กัดพลาดรีเซ็ต'; }

  // =========================================================
  // Skill 1: Derivative Bite — damage depends on the speed at the moment of the press
  // =========================================================
  useSkill1(ctx) {
    const scene = ctx.gs;
    // read the speed BEFORE locking movement (otherwise it is forced to 0)
    const speedRatio = ctx.getPlayerSpeedRatio();
    const baseDamage = BITE_MIN_DAMAGE + Math.round(speedRatio * (BITE_MAX_DAMAGE - BITE_MIN_DAMAGE));
    const damage = baseDamage + (this.frenzyActive ? this.biteStack : 0);

    ctx.setSkillLock(true);
    const rad = ctx.getPlayerAngle() * Math.PI / 180;
    const start = ctx.playerPos();
    const end = ctx.clampPlayerPos(start.x + Math.cos(rad) * BITE_DISTANCE, start.y + Math.sin(rad) * BITE_DISTANCE);

    // shadow colour follows the bite strength: blue (slow) -> red (fast)
    const ghostColor = blendColor(0x00E5FF, 0xFF1744, speedRatio);
    const hitEnemies = new Set();
    let elapsed = 0;
    let ghostT = 0;

    ctx.addTask((dtMs) => {
      elapsed += dtMs;
      const t = Math.min(1, elapsed / BITE_DURATION_MS);
      const p = decelerate(t);
      ctx.setPlayerPos(start.x + (end.x - start.x) * p, start.y + (end.y - start.y) * p);

      const r = ctx.playerRect();
      ghostT += dtMs;
      if (ghostT >= 40) { ghostT = 0; fx.spark(scene, r.x + r.w / 2, r.y + r.h / 2, r.h * 0.7, (0x66 << 24) | ghostColor, 300); }

      for (const e of ctx.getEnemies()) {
        if (e.isAlive && !hitEnemies.has(e) && rectsOverlap(r, e.rect())) {
          hitEnemies.add(e);
          e.takeDamage(damage);
          if (!this.frenzyActive) ctx.onHitEnemySuccess();
        }
      }
      if (t >= 1) {
        ctx.setSkillLock(false);
        // Frenzy: a bite that hits adds +1 to the next bite, a miss resets it
        if (this.frenzyActive) {
          this.biteStack = hitEnemies.size === 0 ? 0 : this.biteStack + 1;
          ctx.setPlayerBonusDamage(this.biteStack);
        }
        return true;
      }
      return false;
    });
  }

  // =========================================================
  // Skill 2: Accumulating Wave — pierces everything; n-th enemy hit takes n damage
  // =========================================================
  useSkill2(ctx) {
    const scene = ctx.gs;
    const angleDeg = ctx.getPlayerAngle();
    const rad = angleDeg * Math.PI / 180;
    const dirX = Math.cos(rad), dirY = Math.sin(rad);

    const pr = ctx.playerRect();
    const startCx = pr.x + pr.w / 2 + dirX * 50;
    const startCy = pr.y + pr.h / 2 + dirY * 50;
    const endCx = startCx + dirX * WAVE_DISTANCE;
    const endCy = startCy + dirY * WAVE_DISTANCE;

    // crescent wave front (glowing blue/white), rotated to the firing direction
    const g = scene.add.graphics().setDepth(14);
    g.lineStyle(14, 0x4FC3F7, 0.6);
    g.beginPath();
    g.arc(-WAVE_SPAN * 0.2, 0, WAVE_SPAN / 2, -Math.PI * 0.42, Math.PI * 0.42, false);
    g.strokePath();
    g.lineStyle(5, 0xFFFFFF, 1);
    g.beginPath();
    g.arc(-WAVE_SPAN * 0.2, 0, WAVE_SPAN / 2, -Math.PI * 0.42, Math.PI * 0.42, false);
    g.strokePath();
    g.setRotation(rad);
    g.setPosition(startCx, startCy);

    const hit = new Set();
    let elapsed = 0;

    ctx.addTask((dtMs) => {
      elapsed += dtMs;
      const f = Math.min(1, elapsed / WAVE_DURATION_MS);
      const cx = startCx + (endCx - startCx) * f;
      const cy = startCy + (endCy - startCy) * f;
      g.setPosition(cx, cy);
      g.setAlpha(1 - 0.5 * f);

      for (const e of ctx.getEnemies()) {
        if (!e.isAlive || hit.has(e)) continue;
        const reach = WAVE_RADIUS + Math.max(e.w, e.h) / 2;
        if (Math.hypot(e.centerX() - cx, e.centerY() - cy) <= reach) {
          hit.add(e);
          e.takeDamage(hit.size);     // n-th enemy hit = n damage (the wave keeps going)
          if (!this.frenzyActive) ctx.onHitEnemySuccess();
        }
      }
      if (f >= 1) { g.destroy(); return true; }
      return false;
    });
  }

  // =========================================================
  // Ultimate: Blood Frenzy — 6 s: speed x1.5, cooldowns x0.5, bite damage grows by 1 per consecutive hit
  // =========================================================
  executeUltimateSkill(ctx) {
    const scene = ctx.gs;
    this.endFrenzy(ctx);                   // used again while active = restart

    this.frenzyActive = true;
    this.biteStack = 0;
    ctx.setSpeedMultiplier(FRENZY_SPEED);
    ctx.setCooldownMultiplier(FRENZY_COOLDOWN);

    // red tint over the whole arena while it lasts
    this.frenzyG = scene.add.rectangle(ctx.W / 2, ctx.H / 2, ctx.W, ctx.H, 0xB71C1C, 0.12).setDepth(4);

    // the skill buttons are usable at once: the player must keep biting during the frenzy
    ctx.onUltimateFinished();
    ctx.showUltimateDuration(FRENZY_MS);
    ctx.setPlayerBonusDamage(0);

    const token = ++this.frenzyToken;
    let remaining = FRENZY_MS;
    ctx.addTask((dtMs) => {
      if (token !== this.frenzyToken || !this.frenzyActive) return true;
      if (!ctx.isGameRunning()) { this.endFrenzy(ctx); return true; }
      remaining -= dtMs;
      if (remaining <= 0) { this.endFrenzy(ctx); return true; }
      if (this.frenzyG) this.frenzyG.setAlpha(remaining < 1500 && Math.floor(remaining / 150) % 2 === 0 ? 0.04 : 0.12);
      return false;
    });
  }

  endFrenzy(ctx) {
    const wasActive = this.frenzyActive;
    this.frenzyActive = false;
    this.biteStack = 0;
    this.frenzyToken++;
    if (wasActive) {
      ctx.setSpeedMultiplier(1);
      ctx.setCooldownMultiplier(1);
      ctx.clearPlayerBonusDamage();
      ctx.hideUltimateDuration();
    }
    if (this.frenzyG) {
      const g = this.frenzyG;
      this.frenzyG = null;
      g.scene.tweens.add({ targets: g, alpha: 0, duration: 250, onComplete: () => g.destroy() });
    }
  }

  clearAll() {
    // stage over: the scene already hides the timer / bonus text; just stop the effect
    this.frenzyActive = false;
    this.frenzyToken++;
    if (this.frenzyG) { this.frenzyG.destroy(); this.frenzyG = null; }
  }
}
