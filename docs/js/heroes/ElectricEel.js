// Port of ElectricEel.java (Physics): Magnetic Repulsion (fan wave + knockback) + Laser Beam (instant piercing beam).
// Ultimate "Railgun": 1 s charge (cannot move, can aim), then one huge piercing beam.
import Hero from './Hero.js';
import * as fx from '../game/effects.js';
import { distanceToSegment, decelerate } from '../game/geometry.js';

// ---- Skill 1: Magnetic Repulsion ----
const WAVE_RANGE = 650;
const WAVE_MS = 550;
const WAVE_SPAN_DEG = 120;
const REPEL_DAMAGE = 2;
const KNOCKBACK_DISTANCE = 450;
const KNOCKBACK_MS = 450;
const COLLISION_DAMAGE = 2;
// ---- Skill 2: Laser Beam ----
const LASER_RANGE = 650;
const LASER_WIDTH = 36;
const LASER_DAMAGE = 3;
// ---- Ultimate: Railgun ----
const CHARGE_MS = 1000;
const BEAM_WIDTH = 110;
const BEAM_DAMAGE = 8;
const BEAM_STUN_MS = 2000;

export default class ElectricEel extends Hero {
  constructor() { super(5, 'Electric Eel', 'Physics'); }

  getMaxHp() { return 85; }
  getBaseSpeedMultiplier() { return 1.1; }
  getStackNeeded() { return 20; }
  getSkill1CooldownMs() { return 2000; }
  getSkill2CooldownMs() { return 2500; }
  usesUltimateButton() { return true; }
  getImageWidthDp() { return 95; }           // the image already fills its canvas
  spriteFacesRight() { return true; }

  getRoleLabel() { return '(Carry)'; }
  getSubjectLabel() { return 'ฟิสิกส์ (Physics)'; }
  getProfileDescription() { return 'ปล่อยกระแสไฟฟ้าแรงสูงตามกฎการนำไฟฟ้าของฟิสิกส์เพื่อช็อตศัตรู'; }

  getSkill1Name() { return 'Magnetic Repulsion'; }
  getSkill1Icon() { return '🧲'; }
  getSkill1Description() {
    return 'ปล่อยคลื่นแม่เหล็ก 1 ลูกพุ่งออกไปทางที่ปลาหัน ศัตรูที่คลื่นผ่านโดน 2 ดาเมจ ถูกผลักไกล 450 px ชนขอบจอ/ศัตรูอื่นโดนอีก 2 ดาเมจ';
  }
  getSkill2Name() { return 'Laser Beam'; }
  getSkill2Icon() { return '🔆'; }
  getSkill2Description() { return 'ยิงเลเซอร์ตรงไปข้างหน้า ระยะจำกัด ทะลุทุกตัวในแนว 3 ดาเมจ คูลดาวน์สั้น'; }
  getUltimateName() { return 'Railgun'; }
  getUltimateIcon() { return '🚀'; }
  getUltimateDescription() { return 'ชาร์จ 1 วินาที ยิงลำแสงทะลุทั้งจอ 8 ดาเมจ + สตัน 2 วินาที ยิงได้ครั้งเดียว'; }

  // =========================================================
  // Skill 1: Magnetic Repulsion
  // =========================================================
  useSkill1(ctx) {
    const scene = ctx.gs;
    const pr = ctx.playerRect();
    const cx = pr.x + pr.w / 2, cy = pr.y + pr.h / 2;

    // one wave in the direction the fish faces (last joystick direction); hit enemies are pushed the same way
    const angleDeg = ctx.getPlayerAngle();
    const pushRad = angleDeg * Math.PI / 180;
    const pushX = Math.cos(pushRad), pushY = Math.sin(pushRad);
    const minCos = Math.cos((WAVE_SPAN_DEG / 2) * Math.PI / 180);

    const g = scene.add.graphics().setDepth(14);
    const alreadyHit = new Set();
    let elapsed = 0;

    ctx.addTask((dtMs) => {
      if (!ctx.isGameRunning()) { g.destroy(); return true; }
      elapsed += dtMs;
      const p = Math.min(1, elapsed / WAVE_MS);
      const r = WAVE_RANGE * p;

      // fan-shaped magnetic arc growing outward
      g.clear();
      const half = (WAVE_SPAN_DEG / 2) * Math.PI / 180;
      g.lineStyle(26, 0x7C4DFF, 0.35 * (1 - 0.5 * p));
      g.beginPath(); g.arc(cx, cy, r, pushRad - half, pushRad + half, false); g.strokePath();
      g.lineStyle(8, 0xE1BEE7, 1 - 0.5 * p);
      g.beginPath(); g.arc(cx, cy, r, pushRad - half, pushRad + half, false); g.strokePath();

      for (const e of ctx.getEnemies()) {
        if (!e.isAlive || alreadyHit.has(e)) continue;
        const dx = e.centerX() - cx, dy = e.centerY() - cy;
        const dist = Math.hypot(dx, dy);
        // inside the fan (overlapping the fish counts as a hit) and the wave front has reached the enemy
        if (dist > 0.01 && (dx * pushX + dy * pushY) / dist < minCos) continue;
        if (r + Math.max(e.w, e.h) / 2 < dist) continue;

        alreadyHit.add(e);
        e.takeDamage(REPEL_DAMAGE, false);
        ctx.onHitEnemySuccess();
        if (e.isAlive && !e.isBoss) this.knockBack(ctx, e, pushX, pushY);   // the boss takes the damage but is not pushed around
      }
      if (p >= 1) {
        scene.tweens.add({ targets: g, alpha: 0, duration: 150, onComplete: () => g.destroy() });
        return true;
      }
      return false;
    });
  }

  /** Push an enemy 450 px along (dx, dy); hitting the screen edge or another enemy = 2 extra damage and stop. */
  knockBack(ctx, enemy, dx, dy) {
    const startX = enemy.x, startY = enemy.y;
    const maxX = Math.max(0, ctx.W - enemy.w);
    const maxY = Math.max(0, ctx.H - enemy.h);
    let collided = false;
    let elapsed = 0;

    // the AI must not fight the push, so the enemy is stunned for its duration
    enemy.stun(KNOCKBACK_MS + 60);

    ctx.addTask((dtMs) => {
      if (!enemy.isAlive || !ctx.isGameRunning()) return true;
      elapsed += dtMs;
      const t = Math.min(1, elapsed / KNOCKBACK_MS);
      const p = decelerate(t);
      const wantX = startX + dx * KNOCKBACK_DISTANCE * p;
      const wantY = startY + dy * KNOCKBACK_DISTANCE * p;
      const nx = Math.max(0, Math.min(maxX, wantX));
      const ny = Math.max(0, Math.min(maxY, wantY));
      enemy.container.setPosition(nx, ny);

      const hitEdge = nx !== wantX || ny !== wantY;
      const hitOther = hitEdge ? null : this.findBlocker(ctx, enemy, dx, dy);
      if ((hitEdge || hitOther) && !collided) {
        collided = true;
        fx.ring(ctx.gs, nx + enemy.w / 2, ny + enemy.h / 2, 160, 0xFFFFFF);
        enemy.takeDamage(COLLISION_DAMAGE, false);
        return true;
      }
      return t >= 1;
    });
  }

  /** Another enemy AHEAD in the push direction and close enough to collide (ones behind don't count). */
  findBlocker(ctx, self, dx, dy) {
    const ax = self.centerX(), ay = self.centerY();
    for (const o of ctx.getEnemies()) {
      if (o === self || !o.isAlive) continue;
      const ox = o.centerX() - ax, oy = o.centerY() - ay;
      const reach = (Math.max(self.w, self.h) + Math.max(o.w, o.h)) * 0.4;
      if (ox * dx + oy * dy > 0 && Math.hypot(ox, oy) < reach) return o;
    }
    return null;
  }

  // =========================================================
  // Skill 2: Laser Beam — instant beam, 650 px, pierces everything in line, 3 damage
  // =========================================================
  useSkill2(ctx) {
    const scene = ctx.gs;
    const rad = ctx.getPlayerAngle() * Math.PI / 180;
    const dirX = Math.cos(rad), dirY = Math.sin(rad);
    const pr = ctx.playerRect();
    const sx = pr.x + pr.w / 2 + dirX * 30;
    const sy = pr.y + pr.h / 2 + dirY * 30;
    const ex = sx + dirX * LASER_RANGE;
    const ey = sy + dirY * LASER_RANGE;

    // beam flashes then fades
    const g = scene.add.graphics().setDepth(14);
    g.lineStyle(LASER_WIDTH, 0x00E5FF, 0.45);
    g.lineBetween(sx, sy, ex, ey);
    g.lineStyle(LASER_WIDTH * 0.4, 0xFFFFFF, 1);
    g.lineBetween(sx, sy, ex, ey);
    scene.tweens.add({ targets: g, alpha: 0, delay: 60, duration: 240, onComplete: () => g.destroy() });

    for (const e of ctx.getEnemies()) {
      if (!e.isAlive) continue;
      const px = e.centerX(), py = e.centerY();
      const reach = LASER_WIDTH / 2 + Math.max(e.w, e.h) * 0.4;
      if (distanceToSegment(px, py, sx, sy, ex, ey) <= reach) {
        fx.ring(scene, px, py, 120, 0x00E5FF);
        e.takeDamage(LASER_DAMAGE);
        ctx.onHitEnemySuccess();
      }
    }
  }

  // =========================================================
  // Ultimate: Railgun — charge 1 s (movement locked, aiming with the joystick still works), then fire once
  // =========================================================
  executeUltimateSkill(ctx) {
    const scene = ctx.gs;
    ctx.setSkillLock(true);
    const chargeG = scene.add.graphics().setDepth(14);
    let elapsed = 0;
    let aim = ctx.getPlayerAngle();

    ctx.addTask((dtMs) => {
      if (!ctx.isGameRunning()) { chargeG.destroy(); ctx.hidePlayerChargeBar(); return true; }
      elapsed += dtMs;
      const f = Math.min(1, elapsed / CHARGE_MS);
      aim = ctx.getPlayerAngle();
      const pr = ctx.playerRect();
      const cx = pr.x + pr.w / 2, cy = pr.y + pr.h / 2;

      // aim line grows brighter and thicker while charging, plus a pulsing ring around the fish
      const len = Math.max(ctx.W, ctx.H) * 1.5;
      const rad = aim * Math.PI / 180;
      const ex = cx + Math.cos(rad) * len, ey = cy + Math.sin(rad) * len;
      chargeG.clear();
      chargeG.lineStyle(8 + 40 * f, 0x00E5FF, 0.1 + 0.3 * f);
      chargeG.lineBetween(cx, cy, ex, ey);
      chargeG.lineStyle(2 + 6 * f, 0xFFFFFF, 0.3 + 0.6 * f);
      chargeG.lineBetween(cx, cy, ex, ey);
      chargeG.lineStyle(6, 0x00E5FF, 0.4 + 0.5 * f);
      chargeG.strokeCircle(cx, cy, 60 + 90 * (1 - f) + 10 * Math.sin(elapsed / 40));
      ctx.setPlayerChargeProgress(f);

      if (f >= 1) {
        chargeG.destroy();
        ctx.hidePlayerChargeBar();
        this.fireRailgun(ctx, aim);
        return true;
      }
      return false;
    });
  }

  fireRailgun(ctx, angleDeg) {
    const scene = ctx.gs;
    const rad = angleDeg * Math.PI / 180;
    const dirX = Math.cos(rad), dirY = Math.sin(rad);
    const pr = ctx.playerRect();
    const sx = pr.x + pr.w / 2, sy = pr.y + pr.h / 2;
    // longer than the screen diagonal in every direction = "full screen length"
    const len = Math.hypot(Math.max(ctx.W, 1), Math.max(ctx.H, 1));
    const ex = sx + dirX * len, ey = sy + dirY * len;

    const g = scene.add.graphics().setDepth(14);
    g.lineStyle(BEAM_WIDTH, 0x00E5FF, 0.45);
    g.lineBetween(sx, sy, ex, ey);
    g.lineStyle(BEAM_WIDTH * 0.4, 0xFFFFFF, 1);
    g.lineBetween(sx, sy, ex, ey);
    scene.tweens.add({ targets: g, alpha: 0, delay: 150, duration: 450, onComplete: () => g.destroy() });

    const flash = scene.add.rectangle(ctx.W / 2, ctx.H / 2, ctx.W, ctx.H, 0xFFFFFF, 0.33).setDepth(30);
    scene.tweens.add({ targets: flash, alpha: 0, duration: 220, onComplete: () => flash.destroy() });

    // pierces everything in line: 8 damage + 2 s stun (stun only if it survives)
    for (const e of ctx.getEnemies()) {
      if (!e.isAlive) continue;
      const px = e.centerX(), py = e.centerY();
      const reach = BEAM_WIDTH / 2 + Math.max(e.w, e.h) * 0.3;
      if (distanceToSegment(px, py, sx, sy, ex, ey) <= reach) {
        e.takeDamage(BEAM_DAMAGE);
        if (e.isAlive) e.stun(BEAM_STUN_MS);
      }
    }

    // one shot only, hit or miss
    ctx.setSkillLock(false);
    ctx.onUltimateFinished();
  }

  clearAll() {
    // stage over: the scene hides the charge bar; nothing persistent to remove
  }
}
