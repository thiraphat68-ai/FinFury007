// Port of Octopus.java (Programming): Tentacle Loop (3 spins, no dash) + Bug Ink (blob that leaves a slowing puddle).
// Ultimate "Infinite Loop": all 8 tentacles drag every enemy into one pile in front of the fish and squeeze it.
import Hero from './Hero.js';
import * as fx from '../game/effects.js';
import { animateEnemy, logicalPos } from '../game/motion.js';
import { accelDecel } from '../game/geometry.js';

// ---- Skill 1: Tentacle Loop ----
const LOOP_RADIUS = 200;
const LOOP_SPINS = 3;
const SPIN_MS = 300;
const LOOP_DAMAGE = 1;
const TENTACLES = 8;
// ---- Skill 2: Bug Ink ----
const INK_DISTANCE = 900;
const INK_DURATION_MS = 500;
const INK_SIZE = 56;
const INK_DAMAGE = 2;
const PUDDLE_RADIUS = 110;
const PUDDLE_MS = 3000;
const PUDDLE_SLOW = 0.5;
// ---- Ultimate: Infinite Loop ----
const GATHER_DISTANCE = 150;     // the pile is 150 px in front of the fish (inside the Tentacle Loop radius)
const PULL_MS = 600;
const HOLD_MS = 4000;
const SQUEEZE_INTERVAL_MS = 500; // 4000 / 500 = 8 squeezes
const SQUEEZE_DAMAGE = 1;

export default class Octopus extends Hero {
  constructor() {
    super(4, 'Octopus', 'Programming');
    this.holding = false;        // Infinite Loop in progress: no stack gain (a quiz would interrupt it)
    this.held = [];
    this.grabG = null;
    this.holdToken = 0;
  }

  getMaxHp() { return 115; }
  getBaseSpeedMultiplier() { return 0.95; }
  getStackNeeded() { return 20; }
  getSkill1CooldownMs() { return 2500; }
  getSkill2CooldownMs() { return 4500; }
  usesUltimateButton() { return true; }
  getImageWidthDp() { return 145; }
  spriteFacesRight() { return true; }

  getRoleLabel() { return '(Mage)'; }
  getSubjectLabel() { return 'การเขียนโปรแกรม (Programming)'; }
  getProfileDescription() { return 'ใช้หนวดสั่งการเขียนลูปและอัลกอริทึมในการพ่นหมึกบดบังศัตรู'; }

  getSkill1Name() { return 'Tentacle Loop'; }
  getSkill1Icon() { return '🐙'; }
  getSkill1Description() { return 'หวดหนวดรอบตัว 3 รอบ รอบละ 1 ดาเมจ ใส่ศัตรูในรัศมี'; }
  getSkill2Name() { return 'Bug Ink'; }
  getSkill2Icon() { return '🖋️'; }
  getSkill2Description() { return 'ยิงหมึก 2 ดาเมจ ทิ้งแอ่งหมึก 3 วินาที ศัตรูในแอ่งช้าลง 50%'; }
  getUltimateName() { return 'Infinite Loop'; }
  getUltimateIcon() { return '♾️'; }
  getUltimateDescription() { return 'หนวดดึงศัตรูทั้งหมดมารวมกัน ตรึง 4 วินาที บีบ 8 ครั้ง ครั้งละ 1 ดาเมจ'; }

  // =========================================================
  // Skill 1: Tentacle Loop — for (i < 3) { whip around the player }
  // =========================================================
  useSkill1(ctx) {
    const scene = ctx.gs;
    const g = scene.add.graphics().setDepth(14);
    const everHit = new Set();
    let applied = 0;
    let elapsed = 0;
    const total = SPIN_MS * LOOP_SPINS;

    ctx.addTask((dtMs) => {
      elapsed += dtMs;
      const t = Math.min(1, elapsed / total);
      const revolutions = t * LOOP_SPINS;
      const pr = ctx.playerRect();
      const cx = pr.x + pr.w / 2, cy = pr.y + pr.h / 2;
      this.drawSpin(g, cx, cy, revolutions);

      // the whip lands half-way through each spin: that is when that spin's damage is applied
      while (applied < LOOP_SPINS && revolutions >= applied + 0.5) {
        applied++;
        for (const e of ctx.getEnemies()) {
          if (!e.isAlive) continue;
          const reach = LOOP_RADIUS + Math.max(e.w, e.h) / 2;
          if (Math.hypot(e.centerX() - cx, e.centerY() - cy) <= reach) {
            // no knockback, otherwise the enemy leaves the circle after the first spin
            e.takeDamage(LOOP_DAMAGE, false);
            if (!everHit.has(e)) { everHit.add(e); if (!this.holding) ctx.onHitEnemySuccess(); }
          }
        }
      }
      if (t >= 1) { g.destroy(); return true; }
      return false;
    });
  }

  drawSpin(g, cx, cy, revolutions) {
    g.clear();
    g.lineStyle(4, 0xFF5252, 0.2);
    g.strokeCircle(cx, cy, LOOP_RADIUS);                 // the real hit radius, faint
    const base = revolutions * Math.PI * 2;
    for (let i = 0; i < TENTACLES; i++) {
      const a0 = base + Math.PI * 2 * i / TENTACLES;
      const pts = [];
      const steps = 14;
      for (let k = 0; k <= steps; k++) {
        const t = k / steps;
        const r = 30 + (LOOP_RADIUS - 30) * t;
        const a = a0 - t * 0.9;                           // tentacle tip trails behind the swing
        pts.push({ x: cx + Math.cos(a) * r, y: cy + Math.sin(a) * r });
      }
      g.lineStyle(26, 0xFF5252, 0.4);
      g.strokePoints(pts, false);
      g.lineStyle(12, 0xD32F2F, 1);
      g.strokePoints(pts, false);
    }
  }

  // =========================================================
  // Skill 2: Bug Ink — blob flies 900 px / 500 ms, 2 damage, lands as a slowing puddle
  // =========================================================
  useSkill2(ctx) {
    const scene = ctx.gs;
    const rad = ctx.getPlayerAngle() * Math.PI / 180;
    const dirX = Math.cos(rad), dirY = Math.sin(rad);
    const pr = ctx.playerRect();
    const startCx = pr.x + pr.w / 2 + dirX * 50;
    const startCy = pr.y + pr.h / 2 + dirY * 50;
    const endCx = startCx + dirX * INK_DISTANCE;
    const endCy = startCy + dirY * INK_DISTANCE;

    const blob = scene.add.circle(startCx, startCy, INK_SIZE / 2, 0x5E3DB3).setStrokeStyle(4, 0xB39DDB).setDepth(14);
    let elapsed = 0;

    ctx.addTask((dtMs) => {
      elapsed += dtMs;
      const f = Math.min(1, elapsed / INK_DURATION_MS);
      const cx = startCx + (endCx - startCx) * f;
      const cy = startCy + (endCy - startCy) * f;
      blob.setPosition(cx, cy);
      const squash = 1 + 0.18 * Math.sin(f * Math.PI * 6);   // blob stretches while flying
      blob.setScale(squash, 2 - squash);

      for (const e of ctx.getEnemies()) {
        if (!e.isAlive) continue;
        if (Math.hypot(e.centerX() - cx, e.centerY() - cy) <= INK_SIZE / 2 + Math.max(e.w, e.h) / 2) {
          blob.destroy();
          e.takeDamage(INK_DAMAGE);
          if (!this.holding) ctx.onHitEnemySuccess();
          this.spawnPuddle(ctx, cx, cy);
          return true;
        }
      }
      if (f >= 1) {
        blob.destroy();
        this.spawnPuddle(ctx, endCx, endCy);   // missed: still leaves a puddle where it lands
        return true;
      }
      return false;
    });
  }

  /** Ink puddle: every enemy inside is slowed 50% each frame (3 s, fades in the last 15%). */
  spawnPuddle(ctx, cx, cy) {
    const scene = ctx.gs;
    const g = scene.add.graphics().setDepth(5);
    g.fillStyle(0x311B92, 0.7);
    g.fillCircle(0, 0, PUDDLE_RADIUS);
    g.fillStyle(0x4527A0, 0.6);
    g.fillCircle(-PUDDLE_RADIUS * 0.3, -PUDDLE_RADIUS * 0.2, PUDDLE_RADIUS * 0.55);
    g.fillCircle(PUDDLE_RADIUS * 0.35, PUDDLE_RADIUS * 0.3, PUDDLE_RADIUS * 0.45);
    g.lineStyle(5, 0x7E57C2, 0.67);
    g.strokeCircle(0, 0, PUDDLE_RADIUS);
    g.setPosition(cx, cy).setScale(0.3);
    scene.tweens.add({ targets: g, scale: 1, duration: 180 });
    const bug = scene.add.text(cx, cy, '🐞', { fontSize: `${PUDDLE_RADIUS * 0.5}px` }).setOrigin(0.5).setDepth(5);

    let life = 0;
    ctx.addTask((dtMs) => {
      if (!ctx.isGameRunning()) { g.destroy(); bug.destroy(); return true; }
      life += dtMs;
      const f = Math.min(1, life / PUDDLE_MS);
      const alpha = f > 0.85 ? (1 - f) / 0.15 : 1;
      g.setAlpha(alpha);
      bug.setAlpha(alpha);
      for (const e of ctx.getEnemies()) {
        if (!e.isAlive) continue;
        if (Math.hypot(e.centerX() - cx, e.centerY() - cy) <= PUDDLE_RADIUS) e.applySlow(PUDDLE_SLOW, 150);
      }
      if (f >= 1) { g.destroy(); bug.destroy(); return true; }
      return false;
    });
  }

  // =========================================================
  // Ultimate: Infinite Loop — pull every enemy to a pile in front of the fish, hold 4 s, squeeze 8 times
  // =========================================================
  executeUltimateSkill(ctx) {
    const scene = ctx.gs;
    this.releaseAll(ctx);                         // used again while holding = start over

    const victims = ctx.getEnemies().filter((e) => e.isAlive && !e.swallowed);
    if (victims.length === 0) { ctx.onUltimateFinished(); return; }

    // gather point is IN FRONT of the fish (facing direction), kept on screen
    const rad = ctx.getPlayerAngle() * Math.PI / 180;
    const pr = ctx.playerRect();
    const pcx = pr.x + pr.w / 2, pcy = pr.y + pr.h / 2;
    const gx = Math.max(90, Math.min(ctx.W - 90, pcx + Math.cos(rad) * GATHER_DISTANCE));
    const gy = Math.max(90, Math.min(ctx.H - 90, pcy + Math.sin(rad) * GATHER_DISTANCE));

    this.held = victims;
    this.holding = true;

    // the pile: a small ring around the gather point so they don't overlap completely
    const n = victims.length;
    victims.forEach((e, i) => {
      e.setSwallowed(true);                        // freezes its AI: no moving or attacking
      const a = Math.PI * 2 * i / n;
      const ring = n === 1 ? 0 : 55;
      // the boss is too big to be dragged: it stays put (and still takes the squeezes)
      if (!e.isBoss) animateEnemy(ctx, e, { x: gx + Math.cos(a) * ring - e.w / 2, y: gy + Math.sin(a) * ring - e.h / 2 }, PULL_MS, accelDecel);
    });

    this.grabG = scene.add.graphics().setDepth(13);
    const token = ++this.holdToken;
    let elapsed = 0;
    let ticks = 0;
    let unlocked = false;
    let pulse = 0;

    ctx.addTask((dtMs) => {
      if (token !== this.holdToken) return true;               // released / replaced elsewhere
      if (!ctx.isGameRunning()) { this.releaseAll(ctx); return true; }
      elapsed += dtMs;
      pulse = Math.max(0, pulse - 0.05);
      this.drawGrab(ctx, Math.min(1, elapsed / PULL_MS), pulse);

      // pulled in: unlock the skill buttons so Tentacle Loop can hit the pile
      if (!unlocked && elapsed >= PULL_MS) { unlocked = true; ctx.onUltimateFinished(); }

      // squeeze every 0.5 s after the pull, 8 times in total (no knockback so the pile stays together)
      while (ticks < HOLD_MS / SQUEEZE_INTERVAL_MS && elapsed >= PULL_MS + (ticks + 1) * SQUEEZE_INTERVAL_MS) {
        ticks++;
        for (const e of this.held) if (e.isAlive) e.takeDamage(SQUEEZE_DAMAGE, false);
        pulse = 1;
      }

      const anyAlive = this.held.some((e) => e.isAlive);
      if (elapsed >= PULL_MS + HOLD_MS || !anyAlive) {
        if (!unlocked) { unlocked = true; ctx.onUltimateFinished(); }
        this.releaseAll(ctx);
        return true;
      }
      return false;
    });
  }

  /** 8 tentacles from the fish to the pile (they reach out during the pull and flash on every squeeze). */
  drawGrab(ctx, reach, pulse) {
    const g = this.grabG;
    if (!g) return;
    g.clear();
    const pr = ctx.playerRect();
    const px = pr.x + pr.w / 2, py = pr.y + pr.h / 2;
    const targets = this.held.filter((e) => e.isAlive);
    if (targets.length === 0) return;
    const cx = targets.reduce((s, e) => s + e.centerX(), 0) / targets.length;
    const cy = targets.reduce((s, e) => s + e.centerY(), 0) / targets.length;
    for (let i = 0; i < TENTACLES; i++) {
      const t = targets[i % targets.length];
      const tx = px + (t.centerX() - px) * reach;
      const ty = py + (t.centerY() - py) * reach;
      const off = Math.sin(performance.now() / 120 + i) * 40;
      const mx = (px + tx) / 2 + (ty - py) / (Math.hypot(tx - px, ty - py) || 1) * off;
      const my = (py + ty) / 2 - (tx - px) / (Math.hypot(tx - px, ty - py) || 1) * off;
      g.lineStyle(22 + 10 * pulse, 0xFF5252, 0.4);
      g.strokePoints([{ x: px, y: py }, { x: mx, y: my }, { x: tx, y: ty }], false);
      g.lineStyle(10 + 6 * pulse, 0xD32F2F, 1);
      g.strokePoints([{ x: px, y: py }, { x: mx, y: my }, { x: tx, y: ty }], false);
    }
    g.lineStyle(6, 0xFFEB3B, 0.3 + 0.7 * pulse);
    g.strokeCircle(cx, cy, 70 + 20 * pulse);
  }

  /** Free every enemy and remove the tentacles. */
  releaseAll(ctx) {
    this.holding = false;
    this.holdToken++;
    for (const e of this.held) {
      e.setSwallowed(false);
      if (e.isAlive) { const p = logicalPos(e); e._lx = undefined; e._ly = undefined; e.container.setScale(1); e.container.setAlpha(1); e.container.setPosition(p.x, p.y); }
    }
    this.held = [];
    if (this.grabG) {
      const g = this.grabG;
      this.grabG = null;
      g.scene.tweens.add({ targets: g, alpha: 0, duration: 250, onComplete: () => g.destroy() });
    }
  }

  clearAll() {
    this.holding = false;
    this.holdToken++;
    for (const e of this.held) e.setSwallowed(false);
    this.held = [];
    if (this.grabG) { this.grabG.destroy(); this.grabG = null; }
  }
}
