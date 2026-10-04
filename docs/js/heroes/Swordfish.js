// Port of Swordfish.java (Circuits): Charge Bite + Discharge + charge/detonate system.
// Ultimate "Circuit Link": links all enemies for 5 s (damage to one = damage to all).
import Hero from './Hero.js';
import * as fx from '../game/effects.js';
import { rectsOverlap, decelerate } from '../game/geometry.js';

// ---- Skill 1: Charge Bite ----
const DASH_DISTANCE = 350;
const DASH_DURATION_MS = 150;
const DASH_DAMAGE = 3;
// ---- Skill 2: Discharge ----
const BOLT_LENGTH = 46;
const BOLT_THICKNESS = 16;
const PROJECTILE_DURATION_MS = 550;
const PROJECTILE_DISTANCE = 1100;
const PROJECTILE_DAMAGE = 2;
// ---- charge ----
const CHARGE_MS = 4000;
const DETONATE_DAMAGE = 5;
const ARC_DAMAGE = 3;
// ---- Ultimate: Circuit Link ----
const LINK_MS = 5000;
// mouth position on the un-flipped hero_1 image (0 = left/top edge, 1 = right/bottom edge)
const MOUTH_X_RATIO = 0.38;
const MOUTH_Y_RATIO = 0.55;


export default class Swordfish extends Hero {
  constructor() {
    super(1, 'Swordfish', 'Circuits');
    this.marks = new Map();   // enemy -> { skill, until, g }
    // Circuit Link (ultimate) state
    this.linkActive = false;
    this.linked = [];
    this.circuitG = null;
  }

  getMaxHp() { return 70; }
  getBaseSpeedMultiplier() { return 1.25; }
  getStackNeeded() { return 20; }
  getSkill1CooldownMs() { return 1500; }
  getSkill2CooldownMs() { return 3000; }
  usesUltimateButton() { return true; }
  getImageWidthDp() { return 120; }
  spriteFacesRight() { return false; }
  getRoleLabel() { return '(Fighter)'; }
  getSubjectLabel() { return 'วงจรไฟฟ้า (Electrical Circuits)'; }
  getProfileDescription() { return 'ใช้พลังงานไฟฟ้าในการโจมตี ฝากประจุไว้บนศัตรูแล้วจุดระเบิด และเชื่อมศัตรูทั้งหมดเป็นวงจรเดียวกัน'; }

  getSkill1Name() { return 'Charge Bite'; }
  getSkill1Icon() { return '⚡'; }
  getSkill1Description() { return 'พุ่งกัด สร้างดาเมจ 3 หน่วย ศัตรูที่โดนจะติดประจุ 4 วินาที'; }
  getSkill2Name() { return 'Discharge'; }
  getSkill2Icon() { return '🔋'; }
  getSkill2Description() {
    return 'ยิงคลื่นไฟฟ้า ดาเมจ 2 หน่วย ถ้าโดนศัตรูที่ติดประจุจากอีกสกิล ประจุระเบิด 5 ดาเมจและกระโดดไปตัวใกล้สุด';
  }
  getUltimateName() { return 'Circuit Link'; }
  getUltimateIcon() { return '🔗'; }
  getUltimateDescription() { return 'ต่อศัตรูทั้งหมดเป็นวงจร 5 วินาที โจมตีโดนตัวไหนตัวอื่นโดนด้วย'; }

  // =========================================================
  // Skill 1: Charge Bite — dash 350 px along the joystick angle, 3 damage + charge
  // =========================================================
  useSkill1(ctx) {
    const scene = ctx.gs;
    ctx.setSkillLock(true);
    const rad = ctx.getPlayerAngle() * Math.PI / 180;
    const start = ctx.playerPos();
    const end = ctx.clampPlayerPos(start.x + Math.cos(rad) * DASH_DISTANCE, start.y + Math.sin(rad) * DASH_DISTANCE);

    const pr = ctx.playerRect();
    const startCx = start.x + pr.w / 2, startCy = start.y + pr.h / 2;
    fx.ring(scene, startCx, startCy, pr.w * 1.4, 0xFFEB3B);

    const streak = scene.add.graphics().setDepth(14);
    const hitEnemies = new Set();
    let elapsed = 0;
    let sparkTimer = 0;

    ctx.addTask((dtMs) => {
      elapsed += dtMs;
      const t = Math.min(1, elapsed / DASH_DURATION_MS);
      const p = decelerate(t);
      ctx.setPlayerPos(start.x + (end.x - start.x) * p, start.y + (end.y - start.y) * p);

      const r = ctx.playerRect();
      const cx = r.x + r.w / 2, cy = r.y + r.h / 2;
      streak.clear();
      streak.lineStyle(44, 0xFFEB3B, 0.35);
      streak.lineBetween(startCx, startCy, cx, cy);
      streak.lineStyle(14, 0xFFFFFF, 0.9);
      streak.lineBetween(startCx, startCy, cx, cy);
      sparkTimer += dtMs;
      if (sparkTimer >= 40) { sparkTimer = 0; fx.spark(scene, cx, cy, r.h * 0.5, 0xCCFFF59D); }

      // every enemy touched is hit once, then the dash continues through
      const enemies = ctx.getEnemies();
      for (let i = 0; i < enemies.length; i++) {
        const e = enemies[i];
        if (e.isAlive && !hitEnemies.has(e) && rectsOverlap(r, e.rect())) {
          hitEnemies.add(e);
          this.hitEnemy(ctx, e, DASH_DAMAGE, 1);
        }
      }

      if (t >= 1) {
        ctx.setSkillLock(false);
        scene.tweens.add({ targets: streak, alpha: 0, duration: 280, onComplete: () => streak.destroy() });
        return true;
      }
      return false;
    });
  }

  // =========================================================
  // Skill 2: Discharge — electric bolt from the mouth, 2 damage, stops at the first enemy
  // =========================================================
  useSkill2(ctx) {
    const scene = ctx.gs;
    const aim = this.getMouthAim(ctx);
    const dirX = aim.dirX, dirY = aim.dirY;
    // the tail of the bolt starts at the mouth, the bolt flies forward
    const startCx = aim.x + dirX * (BOLT_LENGTH / 2);
    const startCy = aim.y + dirY * (BOLT_LENGTH / 2);

    const g = scene.add.graphics().setDepth(14);
    g.fillStyle(0xFFEB3B, 0.47);
    g.fillRoundedRect(-BOLT_LENGTH / 2, -BOLT_THICKNESS / 2, BOLT_LENGTH, BOLT_THICKNESS, BOLT_THICKNESS / 2);
    const inset = BOLT_THICKNESS * 0.28;
    g.fillStyle(0xFFFDE7, 1);
    g.fillRoundedRect(-BOLT_LENGTH / 2 + BOLT_LENGTH * 0.25, -BOLT_THICKNESS / 2 + inset,
      BOLT_LENGTH * 0.75 - inset, BOLT_THICKNESS - inset * 2, (BOLT_THICKNESS - inset * 2) / 2);
    g.setRotation(Math.atan2(dirY, dirX));
    g.setPosition(startCx, startCy);
    fx.ring(scene, aim.x, aim.y, 90, 0xFFF9C4);

    const halfW = Math.abs(dirX) * BOLT_LENGTH / 2 + Math.abs(dirY) * BOLT_THICKNESS / 2;
    const halfH = Math.abs(dirY) * BOLT_LENGTH / 2 + Math.abs(dirX) * BOLT_THICKNESS / 2;
    let elapsed = 0;
    let sparkTimer = 0;

    ctx.addTask((dtMs) => {
      elapsed += dtMs;
      const f = Math.min(1, elapsed / PROJECTILE_DURATION_MS);
      const cx = startCx + dirX * PROJECTILE_DISTANCE * f;
      const cy = startCy + dirY * PROJECTILE_DISTANCE * f;
      g.setPosition(cx, cy);

      sparkTimer += dtMs;
      if (sparkTimer >= 40) {
        sparkTimer = 0;
        fx.spark(scene, cx - dirX * BOLT_LENGTH * 0.4, cy - dirY * BOLT_LENGTH * 0.4, BOLT_THICKNESS * 1.1, 0x88FFEB3B);
      }

      const rect = { x: cx - halfW, y: cy - halfH, w: halfW * 2, h: halfH * 2 };
      const enemies = ctx.getEnemies();
      for (let i = 0; i < enemies.length; i++) {
        const e = enemies[i];
        if (e.isAlive && rectsOverlap(rect, e.rect())) {
          g.destroy();
          this.hitEnemy(ctx, e, PROJECTILE_DAMAGE, 2);
          return true;
        }
      }
      if (f >= 1) { g.destroy(); return true; }
      return false;
    });
  }

  /** Mouth point + facing direction in world coordinates, following flip / tilt / bob of the hero image. */
  getMouthAim(ctx) {
    const b = ctx.playerBodyTransform();
    const fallback = () => {
      const rad = ctx.getPlayerAngle() * Math.PI / 180;
      const r = ctx.playerRect();
      return { x: r.x + r.w / 2, y: r.y + r.h / 2, dirX: Math.cos(rad), dirY: Math.sin(rad) };
    };
    if (!b) return fallback();

    const toWorld = (u, v) => {
      const lx = (u * b.w - b.w / 2) * b.sx;
      const ly = (v * b.h - b.h / 2) * b.sy;
      const c = Math.cos(b.rot), s = Math.sin(b.rot);
      return { x: b.cx + lx * c - ly * s, y: b.cy + b.transY + lx * s + ly * c };
    };
    const mouth = toWorld(MOUTH_X_RATIO, MOUTH_Y_RATIO);
    const behind = toWorld(MOUTH_X_RATIO + 0.3, MOUTH_Y_RATIO);
    const dx = mouth.x - behind.x, dy = mouth.y - behind.y;
    const len = Math.hypot(dx, dy);
    if (len < 1) return fallback();   // turning around (scaleX ~ 0): use the joystick direction
    return { x: mouth.x, y: mouth.y, dirX: dx / len, dirY: dy / len };
  }

  // =========================================================
  // Charge system: every hit of skill 1 / 2 goes through here
  // =========================================================
  hitEnemy(ctx, enemy, baseDamage, skill) {
    const mark = this.marks.get(enemy);
    let damage = baseDamage;
    const detonate = !!mark && mark.skill !== skill;
    let ex = 0, ey = 0;

    if (detonate) {
      damage = DETONATE_DAMAGE;
      ex = enemy.centerX();
      ey = enemy.centerY();
      this.clearMark(enemy);
      fx.ring(ctx.gs, ex, ey, Math.max(enemy.w, 120) * 1.6, 0xFFEB3B);
    }

    this.dealDamage(ctx, enemy, damage);

    if (detonate) {
      this.arcToNearest(ctx, enemy, ex, ey);
    } else if (enemy.isAlive) {
      // no detonation: apply a charge (same-skill hit just refreshes it)
      this.addMark(ctx, enemy, skill);
    }
    if (!this.linkActive) ctx.onHitEnemySuccess();   // no stack gain during the link (a quiz would interrupt the 5 s)
  }

  /** Deals damage; while the circuit is active every other linked enemy takes the same damage. */
  dealDamage(ctx, enemy, damage) {
    enemy.takeDamage(damage);
    if (!enemy.isAlive) this.clearMark(enemy);

    if (!this.linkActive || !this.linked.includes(enemy)) return;
    for (const other of [...this.linked]) {
      if (other === enemy || !other.isAlive) continue;
      fx.arc(ctx.gs, enemy.centerX(), enemy.centerY(), other.centerX(), other.centerY());
      other.takeDamage(damage);
      if (!other.isAlive) this.clearMark(other);
    }
    this.circuitPulse = 1;
  }

  // =========================================================
  // Ultimate: Circuit Link — all enemies on one circuit for 5 s (damage to one = damage to all)
  // =========================================================
  executeUltimateSkill(ctx) {
    this.endLink();                                   // used again while active = restart the timer
    this.linked = ctx.getEnemies().filter((e) => e.isAlive);
    if (this.linked.length === 0) { ctx.onUltimateFinished(); return; }

    this.circuitG = ctx.gs.add.graphics().setDepth(13);
    this.linkActive = true;
    this.circuitPulse = 0;

    // skills are usable again at once: the player must hit enemies with Skill 1 / 2 during these 5 seconds
    ctx.onUltimateFinished();
    ctx.showUltimateDuration(LINK_MS);

    const token = this.linkToken = (this.linkToken || 0) + 1;     // a newer activation replaces this one
    let remaining = LINK_MS;
    ctx.addTask((dtMs) => {
      if (!this.linkActive || token !== this.linkToken) return true;   // ended or replaced
      if (!ctx.isGameRunning()) { this.endLink(); return true; }
      remaining -= dtMs;
      if (remaining <= 0) { this.endLink(); return true; }
      this.drawCircuit(remaining / LINK_MS);
      return false;
    });
  }

  endLink() {
    this.linkActive = false;
    this.linked = [];
    if (this.circuitG) {
      const g = this.circuitG;
      this.circuitG = null;
      g.scene.tweens.add({ targets: g, alpha: 0, duration: 200, onComplete: () => g.destroy() });
    }
  }

  /** Wire joining all linked enemies in a closed loop + a countdown ring around each. */
  drawCircuit(fraction) {
    const g = this.circuitG;
    if (!g) return;
    g.clear();
    const nodes = this.linked.filter((e) => e.isAlive).map((e) => ({
      x: e.centerX(), y: e.centerY(), r: Math.max(40, Math.max(e.w, e.h) * 0.6),
    }));
    if (nodes.length === 0) return;
    const mx = nodes.reduce((s, n) => s + n.x, 0) / nodes.length;
    const my = nodes.reduce((s, n) => s + n.y, 0) / nodes.length;
    nodes.sort((a, b) => Math.atan2(a.y - my, a.x - mx) - Math.atan2(b.y - my, b.x - mx));   // closed loop, no crossings

    const blink = fraction < 0.25 && (Math.floor(performance.now() / 120) % 2 === 0);   // last 25%: blinking warning
    const alpha = blink ? 0.35 : 1;
    const pulse = this.circuitPulse || 0;
    if (nodes.length >= 2) {
      const segments = nodes.length === 2 ? 1 : nodes.length;
      for (let i = 0; i < segments; i++) {
        const a = nodes[i], b = nodes[(i + 1) % nodes.length];
        fx.drawJagged(g, a.x, a.y, b.x, b.y, 16, 0x00E5FF, 18 + 14 * pulse, 5 + 4 * pulse);
      }
    }
    g.lineStyle(8, 0xFFEB3B, alpha);
    for (const n of nodes) {
      g.beginPath();
      g.arc(n.x, n.y, n.r, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * fraction, false);
      g.strokePath();
    }
    this.circuitPulse = Math.max(0, pulse - 0.06);
  }

  arcToNearest(ctx, from, fx0, fy0) {
    let best = null;
    let bestDist = Infinity;
    for (const e of ctx.getEnemies()) {
      if (e === from || !e.isAlive) continue;
      const d = Math.hypot(e.centerX() - fx0, e.centerY() - fy0);
      if (d < bestDist) { bestDist = d; best = e; }
    }
    if (!best) return;
    fx.arc(ctx.gs, fx0, fy0, best.centerX(), best.centerY());
    this.dealDamage(ctx, best, ARC_DAMAGE);
  }

  addMark(ctx, enemy, skill) {
    this.clearMark(enemy);
    const g = ctx.gs.add.graphics().setDepth(13);
    this.marks.set(enemy, { skill, until: ctx.nowMs + CHARGE_MS, g });
    enemy.setCharged(true);
  }

  clearMark(enemy) {
    const m = this.marks.get(enemy);
    if (!m) return;
    this.marks.delete(enemy);
    m.g.destroy();
    enemy.setCharged(false);
  }

  /** Per-frame: expire charges and draw the sparks around charged enemies. */
  update(ctx) {
    for (const [enemy, m] of this.marks) {   // (deleting entries while iterating a Map is safe)
      if (!enemy.isAlive || ctx.nowMs >= m.until || !ctx.isGameRunning()) {
        this.clearMark(enemy);
        continue;
      }
      const cx = enemy.centerX(), cy = enemy.centerY();
      const r = Math.max(40, Math.max(enemy.w, enemy.h) * 0.55);
      m.g.clear();
      for (let i = 0; i < 5; i++) {
        const a = Math.random() * Math.PI * 2;
        const r0 = r * 0.55;
        const r1 = r * (0.9 + Math.random() * 0.4);
        fx.drawJagged(m.g, cx + Math.cos(a) * r0, cy + Math.sin(a) * r0,
          cx + Math.cos(a + 0.25) * r1, cy + Math.sin(a + 0.25) * r1, 8, 0xFFEB3B, 9, 3);
      }
    }
  }

  /** Remove everything (stage end / restart). */
  clearAll() {
    for (const enemy of [...this.marks.keys()]) this.clearMark(enemy);
    this.endLink();
  }
}
