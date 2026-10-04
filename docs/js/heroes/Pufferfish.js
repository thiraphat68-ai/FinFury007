// Port of Pufferfish.java (Chemistry) + the SkillEffects it uses (inflate, venomSpray). Tank hero.
// Ultimate "Toxic Gulp": aim circle, swallow every enemy inside, squeeze 6 times, spit out.
import Hero from './Hero.js';
import { decelerate, accelDecel } from '../game/geometry.js';
import { animateEnemy, animatePlayerScale, logicalPos } from '../game/motion.js';

// ---- Ultimate: Toxic Gulp ----
const SUCK_RANGE = 380;
const AIM_MIN_MS = 2000;      // minimum aim time before it fires
const AIM_MAX_MS = 6000;      // no enemy entered the circle by then = skill expires
const INFLATE_SCALE = 1.9;
const SUCK_MS = 450;
const CHEW_TICKS = 6;
const CHEW_INTERVAL_MS = 500;
const CHEW_DAMAGE = 2;
const SPIT_DISTANCE = 240;

const PUFF_COLORS = [0x9C27B0, 0x7B1FA2, 0xAB47BC, 0x6A1B9A];

export default class Pufferfish extends Hero {
  constructor() { super(2, 'Pufferfish', 'Chemistry'); }

  getMaxHp() { return 150; }
  getBaseSpeedMultiplier() { return 0.8; }
  getStackNeeded() { return 13; }
  getSkill1CooldownMs() { return 3000; }
  getSkill2CooldownMs() { return 6000; }
  usesUltimateButton() { return true; }
  getImageWidthDp() { return 200; }          // pufferfish body is only 38% of its canvas
  spriteFacesRight() { return false; }

  getRoleLabel() { return '(Tank)'; }
  getSubjectLabel() { return 'เคมี (Chemistry)'; }
  getProfileDescription() { return 'พองตัวและปล่อยสารเคมีสะสมพิษเพื่อสร้างเกราะสะท้อนการโจมตี'; }

  getSkill1Name() { return 'Inflate'; }
  getSkill1Icon() { return '🐡'; }
  getSkill1Description() { return 'พองตัว นาน 1 วินาที ศัตรูที่ชนโดน 2 ดาเมจ'; }
  getSkill2Name() { return 'Venom Spray'; }
  getSkill2Icon() { return '☠️'; }
  getSkill2Description() { return 'พ่นพิษสีม่วงเป็นกรวยจากปาก ดาเมจ 2'; }
  getUltimateName() { return 'Toxic Gulp'; }
  getUltimateIcon() { return '🌀'; }
  getUltimateDescription() { return 'พองตัวดูดศัตรูในวงเข้าปาก บีบ 6 ครั้ง ครั้งละ 2 ดาเมจ แล้วคายออก'; }

  // Skill 1: scale x2.2 for 1 s; enemies touching the inflated body take 2 (once each)
  useSkill1(ctx) { this.inflate(ctx, 2.2, 1000, 2); }

  // Skill 2: cone of venom from the mouth, range 600 px, 50 deg, 0.6 s, 2 damage
  useSkill2(ctx) { this.venomSpray(ctx, 600, 50, 600, 2); }

  // ---- SkillEffects.inflate ----
  inflate(ctx, scale, holdMs, damage) {
    ctx.setSkillLock(true);
    const hit = new Set();
    let elapsed = 0;
    let phase = 'puff';            // puff (250 ms, deflating comes later) -> hold -> shrink (300 ms)
    let holdLeft = holdMs;
    let shrinkT = 0;
    const PUFF_MS = 250, SHRINK_MS = 300;

    ctx.addTask((dtMs) => {
      if (phase === 'puff') {
        elapsed += dtMs;
        const t = Math.min(1, elapsed / PUFF_MS);
        const s = 1 + (scale - 1) * decelerate(t);
        ctx.setPlayerScale(s);

        // scale doesn't change x/y/width, so the enlarged box is tested by hand
        const r = ctx.playerRect();
        const cx = r.x + r.w / 2, cy = r.y + r.h / 2;
        const halfW = r.w * s / 2, halfH = r.h * s / 2;
        for (const e of ctx.getEnemies()) {
          if (!e.isAlive || hit.has(e)) continue;
          const ex = e.centerX(), ey = e.centerY();
          if (Math.abs(ex - cx) < halfW + e.w / 2 && Math.abs(ey - cy) < halfH + e.h / 2) {
            hit.add(e);
            e.takeDamage(damage);
            ctx.onHitEnemySuccess();
          }
        }
        if (t >= 1) phase = 'hold';
        return false;
      }
      if (phase === 'hold') {
        holdLeft -= dtMs;
        if (holdLeft <= 0) { phase = 'shrink'; shrinkT = 0; }
        return false;
      }
      shrinkT += dtMs;
      const t = Math.min(1, shrinkT / SHRINK_MS);
      ctx.setPlayerScale(scale + (1 - scale) * accelDecel(t));
      if (t >= 1) {
        ctx.setPlayerScale(1);
        ctx.setSkillLock(false);
        return true;
      }
      return false;
    });
  }

  // ---- SkillEffects.venomSpray ----
  venomSpray(ctx, range, coneDeg, durationMs, damage) {
    const scene = ctx.gs;
    const angle = ctx.getPlayerAngle();
    const rad = angle * Math.PI / 180;
    const dirX = Math.cos(rad), dirY = Math.sin(rad);
    const halfCone = coneDeg / 2;

    // the mouth is at the edge of the fish on the side it faces (uses the real, possibly inflated, size)
    const r = ctx.playerRect();
    const mouthReach = r.w * ctx.playerScale() / 2;
    const originX = r.x + r.w / 2 + dirX * mouthReach;
    const originY = r.y + r.h / 2 + dirY * mouthReach;

    const hit = new Set();
    let elapsed = 0;

    ctx.addTask((dtMs) => {
      elapsed += dtMs;
      const frac = Math.min(1, elapsed / durationMs);

      // 3 spray particles per frame: spread inside the cone, drift out and fade
      for (let i = 0; i < 3; i++) {
        const a = (angle + (Math.random() - 0.5) * coneDeg) * Math.PI / 180;
        const dist = range * (0.5 + Math.random() * 0.5);
        const size = 30 + Math.random() * 40;
        const c = scene.add.circle(originX, originY, size / 2, PUFF_COLORS[Math.floor(Math.random() * PUFF_COLORS.length)], 0.67)
          .setDepth(25).setScale(0.4);
        scene.tweens.add({
          targets: c, scale: 2, alpha: 0, x: originX + Math.cos(a) * dist, y: originY + Math.sin(a) * dist,
          duration: 450 + Math.random() * 200, onComplete: () => c.destroy(),
        });
      }

      // the venom beam gets longer over time; test enemies against the current length
      const reach = range * Math.min(1, frac * 2.5);
      for (const e of ctx.getEnemies()) {
        if (!e.isAlive || hit.has(e)) continue;
        const dx = e.centerX() - originX;
        const dy = e.centerY() - originY;
        const dist = Math.hypot(dx, dy);
        if (dist > reach + e.w / 2) continue;
        let diff = Math.atan2(dy, dx) * 180 / Math.PI - angle;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        // big enemies: widen the accepted angle with distance (close ones don't slip out of the cone)
        const slack = Math.atan2(e.w / 2, Math.max(dist, 1)) * 180 / Math.PI;
        if (Math.abs(diff) <= halfCone + slack) {
          hit.add(e);
          e.takeDamage(damage);
          ctx.onHitEnemySuccess();
        }
      }
      return frac >= 1;
    });
  }

  // =========================================================
  // Ultimate: Toxic Gulp — aim circle (move the fish so enemies enter it), then swallow, squeeze 6x, spit out
  // =========================================================
  executeUltimateSkill(ctx) {
    const scene = ctx.gs;
    const aimG = scene.add.graphics().setDepth(14);
    let elapsed = 0;
    let phase = 0;

    ctx.addTask((dtMs) => {
      if (!ctx.isGameRunning()) { aimG.destroy(); return true; }
      elapsed += dtMs;
      phase += 6 * dtMs / 16.7;

      // the aim phase does not lock movement: the player moves the fish to put enemies inside the circle
      const pr = ctx.playerRect();
      const cx = pr.x + pr.w / 2, cy = pr.y + pr.h / 2;
      const inRange = ctx.getEnemies().filter((e) => e.isAlive && !e.swallowed
        && Math.hypot(e.centerX() - cx, e.centerY() - cy) <= SUCK_RANGE);
      this.drawAim(aimG, cx, cy, inRange, Math.min(1, elapsed / AIM_MIN_MS), phase);

      if (inRange.length > 0 && elapsed >= AIM_MIN_MS) {
        aimG.destroy();
        this.suckIn(ctx, inRange);
        return true;
      }
      if (elapsed >= AIM_MAX_MS) {       // nobody entered the circle in time
        aimG.destroy();
        ctx.onUltimateFinished();
        return true;
      }
      return false;
    });
  }

  /** Dashed rotating range circle + growing inner circle + red lock-on marks on every enemy inside. */
  drawAim(g, cx, cy, targets, charge, phase) {
    g.clear();
    g.fillStyle(0x9C27B0, 0.13);
    g.fillCircle(cx, cy, SUCK_RANGE);
    const dashArc = (radius) => {
      g.lineStyle(6, 0xCE93D8, 0.8);
      const n = 40;
      for (let i = 0; i < n; i++) {
        const a0 = (i / n) * Math.PI * 2 + phase * 0.02;
        g.beginPath();
        g.arc(cx, cy, radius, a0, a0 + (Math.PI * 2 / n) * 0.62, false);
        g.strokePath();
      }
    };
    dashArc(SUCK_RANGE);
    dashArc(Math.max(1, SUCK_RANGE * charge));
    g.lineStyle(7, 0xFF5252, 1);
    for (const t of targets) {
      const tx = t.centerX(), ty = t.centerY();
      const r = Math.max(t.w, t.h) * 0.6;
      const gap = r * 0.45;
      g.strokeCircle(tx, ty, r);
      g.lineBetween(tx - r - gap, ty, tx - r + gap, ty);
      g.lineBetween(tx + r - gap, ty, tx + r + gap, ty);
      g.lineBetween(tx, ty - r - gap, tx, ty - r + gap);
      g.lineBetween(tx, ty + r - gap, tx, ty + r + gap);
    }
  }

  suckIn(ctx, victims) {
    const scene = ctx.gs;
    ctx.setSkillLock(true);
    const pr = ctx.playerRect();
    const cx = pr.x + pr.w / 2, cy = pr.y + pr.h / 2;

    // inflate, and suction rings shrink toward the mouth
    animatePlayerScale(ctx, ctx.playerScale(), INFLATE_SCALE, 300, decelerate);
    for (let i = 0; i < 3; i++) {
      const ring = scene.add.circle(cx, cy, 350).setStrokeStyle(8, 0xAB47BC, 0.8).setDepth(14).setAlpha(0);
      ring.isFilled = false;
      scene.tweens.add({ targets: ring, alpha: 1, duration: 1, delay: i * 140, onComplete: () => {
        scene.tweens.add({ targets: ring, scale: 0.1, alpha: 0, duration: SUCK_MS, ease: 'Quad.easeIn', onComplete: () => ring.destroy() });
      } });
    }

    // every enemy in the circle is pulled to the mouth (spread a little so they don't overlap exactly)
    for (const e of victims) {
      e.setSwallowed(true);
      const tx = cx - e.w / 2 + (Math.random() * 60 - 30);
      const ty = cy - e.h / 2 + (Math.random() * 60 - 30);
      if (!e.isBoss) animateEnemy(ctx, e, { x: tx, y: ty, scale: 0.35, alpha: 0.75 }, SUCK_MS, (t) => t * t);   // the boss is too big to be pulled (it still takes the squeezes)
    }
    ctx.after(SUCK_MS, () => this.chew(ctx, victims, 0));
  }

  chew(ctx, victims, tick) {
    if (!ctx.isGameRunning()) return;
    const anyAlive = victims.some((e) => e.isAlive);
    if (tick >= CHEW_TICKS || !anyAlive) { this.spitOut(ctx, victims); return; }

    // the fish throbs with each squeeze, every enemy in the mouth takes damage
    animatePlayerScale(ctx, INFLATE_SCALE, INFLATE_SCALE * 1.12, 120, (t) => t, () =>
      animatePlayerScale(ctx, INFLATE_SCALE * 1.12, INFLATE_SCALE, 120));
    for (const e of victims) if (e.isAlive) e.takeDamage(CHEW_DAMAGE);
    ctx.after(CHEW_INTERVAL_MS, () => this.chew(ctx, victims, tick + 1));
  }

  spitOut(ctx, victims) {
    // deflate, then unlock; survivors are spat out in different directions around the fish
    animatePlayerScale(ctx, ctx.playerScale(), 1, 350, (t) => t, () => {
      ctx.setSkillLock(false);
      ctx.onUltimateFinished();
    });

    const alive = victims.filter((e) => e.isAlive).length;
    let i = 0;
    for (const e of victims) {
      e.setSwallowed(false);
      if (!e.isAlive) continue;
      const ang = Math.PI * 2 * i / Math.max(alive, 1);
      i++;
      const from = logicalPos(e);
      const outX = Math.max(0, Math.min(ctx.W - e.w, from.x + Math.cos(ang) * SPIT_DISTANCE));
      const outY = Math.max(0, Math.min(ctx.H - e.h, from.y + Math.sin(ang) * SPIT_DISTANCE));
      if (!e.isBoss) animateEnemy(ctx, e, { x: outX, y: outY, scale: 1, alpha: 1 }, 300, decelerate);
    }
  }

  clearAll() {
    // enemies still swallowed when the stage ends are simply left where they are
  }
}
