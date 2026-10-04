// Port of RangedAttacks.java: jellyfish line shot and squid ink cone.
// All distances are in world px (= Android px). Projectiles are "tasks" updated by the battle scene each frame,
// so they freeze while the game is paused.
import * as fx from './effects.js';

const BOLT_HIT_RADIUS = 45;
const BOLT_LENGTH = 70;
const BOLT_THICKNESS = 26;

export const LINE_BAND_WIDTH = 60;
export const INK_RANGE = 640;
export const INK_HALF_ANGLE_DEG = 32;

function distanceToSegment(px, py, ax, ay, bx, by) {
  const abx = bx - ax, aby = by - ay;
  const len2 = abx * abx + aby * aby;
  const t = len2 < 0.0001 ? 0 : Math.max(0, Math.min(1, ((px - ax) * abx + (py - ay) * aby) / len2));
  return Math.hypot(px - (ax + abx * t), py - (ay + aby * t));
}

/** Straight (or sine-wave) bolt flying along a locked direction until it leaves the screen or hits the player. */
export function fireLine(ctx, x0, y0, dirX, dirY, speed, damage, onHit, waveAmplitude = 0, waveLength = 1) {
  const scene = ctx.gs;
  const g = scene.add.graphics().setDepth(12);
  g.fillStyle(0xF48FB1, 1);
  g.fillRoundedRect(-BOLT_LENGTH / 2, -BOLT_THICKNESS / 2, BOLT_LENGTH, BOLT_THICKNESS, BOLT_THICKNESS / 2);
  g.lineStyle(4, 0xFFFFFF, 1);
  g.strokeRoundedRect(-BOLT_LENGTH / 2, -BOLT_THICKNESS / 2, BOLT_LENGTH, BOLT_THICKNESS, BOLT_THICKNESS / 2);
  g.setPosition(x0, y0);
  g.setRotation(Math.atan2(dirY, dirX));

  const maxDist = Math.hypot(Math.max(ctx.W, 1), Math.max(ctx.H, 1));
  let d = 0;
  let prev = { x: x0, y: y0 };
  let hit = false;

  ctx.addTask((dtMs) => {
    if (!ctx.isGameRunning()) { g.destroy(); return true; }
    d += speed * dtMs / 1000;
    if (d >= maxDist) { g.destroy(); return true; }

    // sine-wave path: fly straight along the locked direction and swing sideways (zig-zag)
    const phase = 2 * Math.PI * d / waveLength;
    const offset = waveAmplitude * Math.sin(phase);
    const bx = x0 + dirX * d - dirY * offset;
    const by = y0 + dirY * d + dirX * offset;
    if (waveAmplitude > 0) {
      const slope = waveAmplitude * (2 * Math.PI / waveLength) * Math.cos(phase);
      g.setRotation(Math.atan2(dirY, dirX) + Math.atan(slope));
    }
    g.setPosition(bx, by);

    if (!hit) {
      const pc = ctx.playerCenter();
      // fast bolt: test the whole segment travelled this frame
      if (distanceToSegment(pc.x, pc.y, prev.x, prev.y, bx, by) <= BOLT_HIT_RADIUS) {
        hit = true;
        ctx.damagePlayer(damage);
        if (onHit) onHit();
        fx.playerHit(scene, pc.x, pc.y, damage);
        g.destroy();
        return true;
      }
    }
    prev = { x: bx, y: by };
    return false;
  });
}

function inCone(px, py, cx, cy, dirDeg, radius, halfDeg) {
  const dx = px - cx, dy = py - cy;
  if (Math.hypot(dx, dy) > radius) return false;
  const ang = Math.atan2(dy, dx) * 180 / Math.PI;
  const diff = Math.abs((((ang - dirDeg + 540) % 360) + 360) % 360 - 180);
  return diff <= halfDeg;
}

function drawCone(g, cx, cy, dirDeg, radius, halfDeg, telegraph) {
  g.clear();
  if (radius < 1) return;
  const a0 = Phaser.Math.DegToRad(dirDeg - halfDeg);
  const a1 = Phaser.Math.DegToRad(dirDeg + halfDeg);
  g.fillStyle(telegraph ? 0xAA00FF : 0x6A1B9A, telegraph ? 0.33 : 0.8);
  g.slice(cx, cy, radius, a0, a1, false);
  g.fillPath();
  if (!telegraph) {
    const r2 = radius * 0.62;
    g.fillStyle(0x2A0845, 0.6);
    g.slice(cx, cy, r2, Phaser.Math.DegToRad(dirDeg - halfDeg * 0.8), Phaser.Math.DegToRad(dirDeg + halfDeg * 0.8), false);
    g.fillPath();
  }
  g.lineStyle(telegraph ? 5 : 4, telegraph ? 0xCE93D8 : 0xE1BEE7, 1);
  g.slice(cx, cy, radius, a0, a1, false);
  g.strokePath();
}

/** Squid: ink cone that widens while it expands (380 ms) and hits the player once. */
export function inkCone(ctx, cx, cy, dirX, dirY, damage, onHit) {
  const scene = ctx.gs;
  const g = scene.add.graphics().setDepth(12);
  const dirDeg = Math.atan2(dirY, dirX) * 180 / Math.PI;
  let elapsed = 0;
  let hit = false;
  const DURATION = 380;

  ctx.addTask((dtMs) => {
    if (!ctx.isGameRunning()) { g.destroy(); return true; }
    elapsed += dtMs;
    const t = Math.min(1, elapsed / DURATION);
    const p = 1 - (1 - t) * (1 - t);                 // decelerate
    const radius = INK_RANGE * p;
    const half = INK_HALF_ANGLE_DEG * (0.35 + 0.65 * p);
    drawCone(g, cx, cy, dirDeg, radius, half, false);
    const pc = ctx.playerCenter();
    if (!hit && inCone(pc.x, pc.y, cx, cy, dirDeg, radius, half)) {
      hit = true;
      ctx.damagePlayer(damage);
      if (onHit) onHit();
      fx.playerHit(scene, pc.x, pc.y, damage);
    }
    if (t >= 1) {
      // leave the ink for a moment, then fade
      scene.tweens.add({ targets: g, alpha: 0, duration: 350, onComplete: () => g.destroy() });
      return true;
    }
    return false;
  });
}

/** Cone telegraph shown during the squid's windup. */
export function showInkTelegraph(scene, cx, cy, dirX, dirY, durationMs) {
  const g = scene.add.graphics().setDepth(6);
  drawCone(g, cx, cy, Math.atan2(dirY, dirX) * 180 / Math.PI, INK_RANGE, INK_HALF_ANGLE_DEG, true);
  g.setAlpha(0.25);
  scene.tweens.add({ targets: g, alpha: 1, duration: durationMs });
  return g;
}
