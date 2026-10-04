// Small geometry helpers shared by skills and enemies (all in world px).
export function rectsOverlap(a, b) {
  return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
}

export function distanceToSegment(px, py, ax, ay, bx, by) {
  const abx = bx - ax, aby = by - ay;
  const len2 = abx * abx + aby * aby;
  const t = len2 < 0.0001 ? 0 : Math.max(0, Math.min(1, ((px - ax) * abx + (py - ay) * aby) / len2));
  return Math.hypot(px - (ax + abx * t), py - (ay + aby * t));
}

/** Android DecelerateInterpolator (factor 1): fast start, slow end. */
export function decelerate(t) { return 1 - (1 - t) * (1 - t); }

/** Android AccelerateDecelerateInterpolator. */
export function accelDecel(t) { return Math.cos((t + 1) * Math.PI) / 2 + 0.5; }
