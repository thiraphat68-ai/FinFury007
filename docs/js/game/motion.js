// Time-based animations driven by the battle's task list (so they freeze while the game is paused).

/** Position an enemy by its logical top-left (lx, ly) and scale s about ITS CENTRE (Android Views scale about the centre). */
export function placeEnemy(e, lx, ly, s = e.container.scaleX, alpha = e.container.alpha) {
  e._lx = lx;
  e._ly = ly;
  e.container.setScale(s);
  e.container.setPosition(lx + e.w / 2 * (1 - s), ly + e.h / 2 * (1 - s));
  e.container.setAlpha(alpha);
}

/** Logical top-left of an enemy (ignores the scale compensation). */
export function logicalPos(e) {
  return { x: e._lx !== undefined ? e._lx : e.container.x, y: e._ly !== undefined ? e._ly : e.container.y };
}

/** Move/scale/fade an enemy to a target over ms using an easing function (t in 0..1 -> 0..1). */
export function animateEnemy(ctx, e, target, ms, ease = (t) => t) {
  const from = logicalPos(e);
  const s0 = e.container.scaleX;
  const a0 = e.container.alpha;
  const tx = target.x !== undefined ? target.x : from.x;
  const ty = target.y !== undefined ? target.y : from.y;
  const ts = target.scale !== undefined ? target.scale : s0;
  const ta = target.alpha !== undefined ? target.alpha : a0;
  let elapsed = 0;
  ctx.addTask((dtMs) => {
    elapsed += dtMs;
    const t = Math.min(1, elapsed / ms);
    const p = ease(t);
    placeEnemy(e, from.x + (tx - from.x) * p, from.y + (ty - from.y) * p, s0 + (ts - s0) * p, a0 + (ta - a0) * p);
    if (t >= 1 && ts === 1) { e._lx = undefined; e._ly = undefined; }   // back to normal scale: container position is the real one again
    return t >= 1;
  });
}

/** Animate the player's scale over ms. */
export function animatePlayerScale(ctx, from, to, ms, ease = (t) => t, onDone) {
  let elapsed = 0;
  ctx.addTask((dtMs) => {
    elapsed += dtMs;
    const t = Math.min(1, elapsed / ms);
    ctx.setPlayerScale(from + (to - from) * ease(t));
    if (t >= 1) { if (onDone) onDone(); return true; }
    return false;
  });
}
