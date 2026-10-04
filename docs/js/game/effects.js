// Cosmetic effects (port of HitEffects / GhostPool / the small View effects). They never change game state.
import { FONT } from '../config.js';

const DEPTH_FX = 30;

export function ring(scene, cx, cy, size, color) {
  const d = Math.max(20, size);
  const c = scene.add.circle(cx, cy, d / 2).setStrokeStyle(6, color).setDepth(DEPTH_FX);
  c.isFilled = false;
  c.setScale(0.3);
  scene.tweens.add({ targets: c, scale: 1.4, alpha: 0, duration: 320, ease: 'Cubic.easeOut', onComplete: () => c.destroy() });
}

export function spark(scene, cx, cy, size, color, durationMs = 300) {
  const d = Math.max(8, size);
  const c = scene.add.circle(cx + Math.random() * 10 - 5, cy + Math.random() * 10 - 5, d / 2, color & 0xFFFFFF,
    ((color >>> 24) & 0xFF) / 255 || 0.6).setDepth(DEPTH_FX);
  scene.tweens.add({ targets: c, scale: 0.2, alpha: 0, duration: durationMs, onComplete: () => c.destroy() });
}

/** Jagged lightning line between two points (glow + white core). */
export function drawJagged(g, x0, y0, x1, y1, jag, glowColor = 0xFFEB3B, glowW = 16, coreW = 5) {
  const dx = x1 - x0, dy = y1 - y0;
  const len = Math.max(Math.hypot(dx, dy), 1);
  const nx = -dy / len, ny = dx / len;
  const steps = Math.max(2, Math.floor(len / 45));
  const pts = [{ x: x0, y: y0 }];
  for (let i = 1; i < steps; i++) {
    const t = i / steps;
    const off = (Math.random() * 2 - 1) * jag;
    pts.push({ x: x0 + dx * t + nx * off, y: y0 + dy * t + ny * off });
  }
  pts.push({ x: x1, y: y1 });
  g.lineStyle(glowW, glowColor, 0.55);
  g.strokePoints(pts, false);
  g.lineStyle(coreW, 0xFFFFFF, 1);
  g.strokePoints(pts, false);
}

export function arc(scene, x0, y0, x1, y1) {
  const g = scene.add.graphics().setDepth(DEPTH_FX);
  drawJagged(g, x0, y0, x1, y1, 30);
  scene.tweens.add({ targets: g, alpha: 0, duration: 350, onComplete: () => g.destroy() });
}

/** Floating damage number above a world position. */
export function damageText(scene, x, y, amount, color = '#FFFFFF') {
  const t = scene.add.text(x, y, `-${amount}`, {
    fontFamily: FONT, fontSize: '44px', fontStyle: 'bold', color, stroke: '#000000', strokeThickness: 6,
  }).setOrigin(0.5).setDepth(DEPTH_FX + 1);
  scene.tweens.add({ targets: t, y: y - 70, alpha: 0, duration: 600, ease: 'Cubic.easeOut', onComplete: () => t.destroy() });
}

/** Warning band shown during an attack windup (from enemy to the locked target). Returns an object with destroy(). */
export function attackPath(scene, x0, y0, x1, y1, width, durationMs) {
  const len = Math.hypot(x1 - x0, y1 - y0);
  const r = scene.add.rectangle(x0, y0, len, width, 0xFF1744, 0.3).setOrigin(0, 0.5).setDepth(6);
  r.setRotation(Math.atan2(y1 - y0, x1 - x0));
  r.setAlpha(0.25);
  scene.tweens.add({ targets: r, alpha: 1, duration: durationMs });
  return r;
}

/** Red flash on the player + damage number. */
export function playerHit(scene, cx, cy, damage) {
  damageText(scene, cx, cy - 60, damage, '#FF5252');
  ring(scene, cx, cy, 160, 0xFF5252);
}
