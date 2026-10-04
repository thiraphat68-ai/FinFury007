// Port of SeaEnemy.java: stats per enemy type, stage modifiers, AI state machine, attacks, damage and death.
// Positions are the TOP-LEFT of the enemy container in world px (same as Android's containerView.getX/Y).
// Time comes from ctx.nowMs (game time, frozen while paused) instead of System.currentTimeMillis.
import { FONT, MAX_SPEED } from '../config.js';
import { playSfx } from '../core/audio.js';
import * as fx from './effects.js';
import * as ranged from './rangedAttacks.js';

// ---- attack slots (static in Android): 1 slot, 2 on dual-attack stages ----
const slots = { a1: 0, a2: 0, dual: false, lastEnd: -1e9 };
let nextId = 1;

export function resetAttackQueue() { slots.a1 = 0; slots.a2 = 0; slots.dual = false; slots.lastEnd = -1e9; }
export function setDualAttack(enabled) { slots.dual = !!enabled; }

const ATTACK = { DASH: 'DASH', LINE_SHOT: 'LINE_SHOT', INK_CONE: 'INK_CONE' };
const STATE = { ORBIT: 0, WINDUP: 1, ATTACKING: 2 };
const STYLE = { ORBIT: 'ORBIT', SIDESTEP: 'SIDESTEP', DRIFT: 'DRIFT', PATROL: 'PATROL', KITE: 'KITE', ERRATIC: 'ERRATIC' };

const BASE_SPEED = MAX_SPEED * 0.75;
const ORBIT_SPEED_FACTOR = 0.60;
const BASE_DASH_SPEED = MAX_SPEED * 10;
const ORBIT_ROTATE_RAD_PER_S = 0.45;
const ZIGZAG_AMPLITUDE = 90;
const ZIGZAG_FREQ = 3.2;
const SEPARATION_SPEED = 400;
const ATTACK_TRIGGER_RANGE = 45;
const ENEMY_SPACING = 210;
const PASS_THROUGH_DISTANCE = 160;
const APPROACH_BEFORE_ATTACK_MS = 1200;
const LINE_SHOT_SPEED_FACTOR = 3;
const RECOIL_SPEED = 1400;
const RECOIL_DECAY = 7;
const WAVE_AMPLITUDE_PX = 110;
const WAVE_LENGTH_PX = 480;
const SHOCKWAVE_SPEED = 5000;
const BARRAGE_SHOTS = 15;
const BARRAGE_GAP_MS = 120;
const JELLY_STUN_MS = 500;
const TRAVEL_CUT_PER_STAGE_S = 0.2;
const MIN_TRAVEL_S = 0.25;
const WINDUP_MS = 500;
const KNOCKBACK_DIST = 55;
const KNOCKBACK_MS = 120;

// size of the enemy container (name + hp bar + hp text + emoji) in world px
const BOX_W = 130;
const BOX_H = 204;
const EMOJI_Y = 142;

function triangleWave(x) { return (2 / Math.PI) * Math.asin(Math.sin(x)); }

function distanceToSegment(px, py, ax, ay, bx, by) {
  const abx = bx - ax, aby = by - ay;
  const len2 = abx * abx + aby * aby;
  const t = len2 < 0.0001 ? 0 : Math.max(0, Math.min(1, ((px - ax) * abx + (py - ay) * aby) / len2));
  return Math.hypot(px - (ax + abx * t), py - (ay + aby * t));
}

export default class SeaEnemy {
  constructor(ctx, name, emoji, posX, posY, cfg) {
    this.ctx = ctx;
    this.name = name;
    this.emoji = emoji;
    this.cfg = cfg;
    this.id = nextId++;

    this.hp = 10;
    this.maxHp = 10;
    this.isAlive = true;
    this.swallowed = false;

    // timers (game ms)
    this.slowFactor = 1;
    this.slowUntil = 0;
    this.stunUntil = 0;

    this.state = STATE.ORBIT;
    this.timeInOrbitMs = Math.random() * 800;
    this.dashTargetX = 0;
    this.dashTargetY = 0;
    this.hasHitPlayerThisDash = false;
    this.windupStartMs = 0;
    this.attackPathView = null;

    this.speedMultiplier = 1;
    this.holdMinDistance = 200;
    this.holdMaxDistance = 320;
    this.attackDamage = 5;
    this.attackType = ATTACK.DASH;
    this.aimDirX = 1;
    this.aimDirY = 0;
    this.moveStyle = STYLE.ORBIT;
    this.wanderX = 0; this.wanderY = 0; this.wanderUntil = 0;
    this.attackIntervalMs = 3000;
    this.dashSpeed = BASE_DASH_SPEED;
    this.recoilVx = 0; this.recoilVy = 0;
    this.kbX = 0; this.kbY = 0; this.kbLeftMs = 0;   // knockback still to apply

    this.animTime = Math.random() * 10;
    this.zigPhase = Math.random() * Math.PI * 2;
    this.slotOffset = Math.random() * 0.4 - 0.2;

    this.charged = false;
    this.isBoss = false;

    this.configureEnemyStats();
    this.applyStageModifiers();
    this.createView(posX, posY);
  }

  // ---------- stats ----------
  configureEnemyStats() {
    const e = this.emoji;
    if (e.includes('🦀')) {            // Crab: lots of HP, slow, side-steps
      this.maxHp = 16; this.hp = 16; this.speedMultiplier = 0.85;
      this.holdMinDistance = 160; this.holdMaxDistance = 240; this.attackDamage = 6;
      this.moveStyle = STYLE.SIDESTEP;
    } else if (e.includes('🦑')) {     // Squid: fast, kites, ink cone
      this.maxHp = 12; this.hp = 12; this.speedMultiplier = 1.25;
      this.holdMinDistance = 420; this.holdMaxDistance = 500; this.attackDamage = 5;
      this.attackType = ATTACK.INK_CONE; this.moveStyle = STYLE.KITE;
    } else if (e.includes('🐢')) {     // Turtle: most HP, slow, patrols
      this.maxHp = 18; this.hp = 18; this.speedMultiplier = 0.85;
      this.holdMinDistance = 240; this.holdMaxDistance = 340; this.attackDamage = 5;
      this.moveStyle = STYLE.PATROL;
    } else if (e.includes('🪼')) {     // Jellyfish: line-shot barrage, drifts
      this.maxHp = 9; this.hp = 9; this.speedMultiplier = 1.0;
      this.holdMinDistance = 180; this.holdMaxDistance = 220; this.attackDamage = 4;
      this.attackType = ATTACK.LINE_SHOT; this.moveStyle = STYLE.DRIFT;
    } else {                            // Starfish / other
      this.maxHp = 11; this.hp = 11; this.speedMultiplier = 1.0;
      this.holdMinDistance = 200; this.holdMaxDistance = 320; this.attackDamage = 5;
      this.moveStyle = STYLE.ERRATIC;
    }
  }

  applyStageModifiers() {
    const c = this.cfg;
    this.maxHp = Math.max(1, Math.round(this.maxHp * c.hpMul * this.hpScale()));
    this.hp = this.maxHp;
    this.speedMultiplier *= c.speedMul;
    this.attackIntervalMs = c.attackIntervalMs;
    this.dashSpeed = BASE_DASH_SPEED * c.dashSpeedMul;
    this.attackDamage = Math.max(1, Math.round(this.attackDamage * c.damageMul * this.damageScale()));
  }

  // ---------- view ----------
  createView(posX, posY) {
    const scene = this.ctx.gs;
    const c = scene.add.container(posX, posY).setDepth(11);
    c.setSize(BOX_W, BOX_H);
    this.container = c;
    this.w = BOX_W;
    this.h = BOX_H;

    this.txtName = scene.add.text(BOX_W / 2, 0, this.name, {
      fontFamily: FONT, fontSize: '32px', color: '#FF5252',
    }).setOrigin(0.5, 0);
    this.barG = scene.add.graphics();
    this.txtHp = scene.add.text(BOX_W / 2, 56, '', {
      fontFamily: FONT, fontSize: '27px', color: '#FFFF00',
    }).setOrigin(0.5, 0);
    this.avatar = scene.add.text(BOX_W / 2, EMOJI_Y, this.emoji, {
      fontFamily: FONT, fontSize: '99px',
    }).setOrigin(0.5);

    // status icons above the enemy
    this.iconCharged = scene.add.text(BOX_W / 2 - 40, -26, '⚡', { fontFamily: FONT, fontSize: '34px' }).setOrigin(0.5).setVisible(false);
    this.iconSlowed = scene.add.text(BOX_W / 2, -26, '🐌', { fontFamily: FONT, fontSize: '34px' }).setOrigin(0.5).setVisible(false);
    this.iconStunned = scene.add.text(BOX_W / 2 + 40, -26, '💫', { fontFamily: FONT, fontSize: '34px' }).setOrigin(0.5).setVisible(false);

    c.add([this.txtName, this.barG, this.txtHp, this.avatar, this.iconCharged, this.iconSlowed, this.iconStunned]);
    this.refreshHpUi();
  }

  refreshHpUi() {
    const g = this.barG;
    g.clear();
    const bw = 90, bh = 14, x = (BOX_W - bw) / 2, y = 38;
    g.fillStyle(0x333333, 1);
    g.fillRect(x, y, bw, bh);
    g.fillStyle(0xFF0000, 1);
    g.fillRect(x, y, bw * (this.hp / this.maxHp), bh);
    this.txtHp.setText(`${this.hp}/${this.maxHp}`);
  }

  get x() { return this.container.x; }
  get y() { return this.container.y; }
  centerX() { return this.container.x + this.w / 2; }
  centerY() { return this.container.y + this.h / 2; }
  rect() { return { x: this.container.x, y: this.container.y, w: this.w, h: this.h }; }

  // ---------- attack slots ----------
  holdsAttackSlot() { return slots.a1 === this.id || slots.a2 === this.id; }
  attackSlotAvailable() { return this.holdsAttackSlot() || slots.a1 === 0 || (slots.dual && slots.a2 === 0); }
  claimAttackSlot() {
    if (this.holdsAttackSlot()) return;
    if (slots.a1 === 0) slots.a1 = this.id; else slots.a2 = this.id;
  }
  releaseAttackSlot(now) {
    if (slots.a1 === this.id) slots.a1 = 0;
    else if (slots.a2 === this.id) slots.a2 = 0;
    else return;
    slots.lastEnd = now;
  }

  // ---------- status ----------
  applySlow(factor, durationMs) {
    this.slowFactor = factor;
    this.slowUntil = this.ctx.nowMs + durationMs;
    this.iconSlowed.setVisible(true);
  }

  stun(durationMs) {
    this.stunUntil = Math.max(this.stunUntil, this.ctx.nowMs + durationMs);
    this.iconStunned.setVisible(true);
    this.cancelAttack();
  }

  setSwallowed(value) {
    this.swallowed = value;
    if (value) this.cancelAttack();
  }

  setCharged(value) {
    this.charged = value;
    this.iconCharged.setVisible(value);
  }

  cancelAttack() {
    this.clearAttackPath();
    this.releaseAttackSlot(this.ctx.nowMs);
    this.timeInOrbitMs = 0;
    this.state = STATE.ORBIT;
  }

  clearAttackPath() {
    if (this.attackPathView) {
      this.attackPathView.destroy();
      this.attackPathView = null;
    }
  }

  /** Effect on the player when this enemy's attack lands (jellyfish stun every stage; turtle/starfish stages 2-4). */
  onPlayerHit(dirX, dirY) {
    if (this.name === 'Jellyfish') { this.ctx.stunPlayer(JELLY_STUN_MS); return; }
    const stage = this.cfg.stage;
    if (stage < 2 || stage > 4) return;
    if (this.name === 'Turtle') this.ctx.slowPlayer(0.5, 2000);
    else if (this.name === 'Starfish') this.ctx.knockbackPlayer(dirX, dirY, 2000);
  }

  // ---------- AI ----------
  slotAngle() {
    let idx = 0, n = 0;
    const all = this.ctx.getEnemies();
    for (let i = 0; i < all.length; i++) {
      const e = all[i];
      if (e === this) idx = n;
      if (e.isAlive && !e.isBoss && (e === this || !e.swallowed)) n++;
    }
    return Math.PI * 2 * idx / Math.max(1, n) + this.slotOffset;
  }

  /** Reused result array (this runs for every enemy every frame: no allocation). */
  holdPoint(x, y) {
    if (!this._hold) this._hold = [0, 0];
    this._hold[0] = x;
    this._hold[1] = y;
    return this._hold;
  }

  computeHoldPoint(targetX, targetY, curX, curY, distance, tSec, now) {
    const mid = (this.holdMinDistance + this.holdMaxDistance) / 2;
    const baseAngle = this.slotAngle() + ORBIT_ROTATE_RAD_PER_S * tSec;
    const approaching = this.timeInOrbitMs >= this.attackIntervalMs - WINDUP_MS - APPROACH_BEFORE_ATTACK_MS;

    let angle = baseAngle;
    let dist = mid;
    switch (this.moveStyle) {
      case STYLE.SIDESTEP:
        angle = this.slotAngle() + Math.sin(tSec * 1.1 + this.zigPhase) * 0.9;
        dist = mid + triangleWave(tSec * 1.6 + this.zigPhase) * 25;
        break;
      case STYLE.DRIFT: {
        dist = mid + Math.sin(tSec * 0.9 + this.zigPhase) * (this.holdMaxDistance - this.holdMinDistance + 60);
        const bob = Math.sin(tSec * 2.2 + this.zigPhase) * 70;
        return this.holdPoint(targetX + Math.cos(baseAngle) * dist, targetY + Math.sin(baseAngle) * dist + bob);
      }
      case STYLE.KITE:
        if (distance < this.holdMinDistance && distance > 0.001) {
          return this.holdPoint(curX + (curX - targetX) / distance * 260, curY + (curY - targetY) / distance * 260);
        }
        dist = mid + triangleWave(tSec * ZIGZAG_FREQ + this.zigPhase) * 25;
        break;
      case STYLE.PATROL:
      case STYLE.ERRATIC:
        if (approaching) break;
        if (now >= this.wanderUntil || Math.hypot(this.wanderX - curX, this.wanderY - curY) < 40) {
          this.pickWanderPoint(targetX, targetY, now);
        }
        return this.holdPoint(this.wanderX, this.wanderY);
      default: {
        const zig = triangleWave(tSec * ZIGZAG_FREQ + this.zigPhase);
        dist = mid + zig * ZIGZAG_AMPLITUDE;
      }
    }
    dist = Math.max(this.holdMinDistance, Math.min(this.holdMaxDistance, dist));
    return this.holdPoint(targetX + Math.cos(angle) * dist, targetY + Math.sin(angle) * dist);
  }

  pickWanderPoint(targetX, targetY, now) {
    const { W, H } = this.ctx;
    const maxX = Math.max(0, W - this.w);
    const maxY = Math.max(0, H - this.h);
    if (this.moveStyle === STYLE.ERRATIC) {
      const a = Math.random() * Math.PI * 2;
      const r = this.holdMinDistance + Math.random() * (this.holdMaxDistance - this.holdMinDistance + 150);
      this.wanderX = targetX + Math.cos(a) * r;
      this.wanderY = targetY + Math.sin(a) * r;
      this.wanderUntil = now + 900 + Math.random() * 1200;
    } else {
      this.wanderX = Math.random() * maxX;
      this.wanderY = Math.random() * maxY;
      this.wanderUntil = now + 2500 + Math.random() * 2500;
    }
    this.wanderX = Math.max(0, Math.min(maxX, this.wanderX));
    this.wanderY = Math.max(0, Math.min(maxY, this.wanderY));
  }

  isReadyToAttack(targetX, targetY, now) {
    if (!this.isAlive || this.state !== STATE.ORBIT) return false;
    if (this.timeInOrbitMs < this.attackIntervalMs - WINDUP_MS) return false;
    if (now - slots.lastEnd < 300) return false;
    if (this.attackType === ATTACK.INK_CONE) {
      const d = Math.hypot(targetX - this.container.x, targetY - this.container.y);
      return d <= ranged.INK_RANGE * 0.9;
    }
    return true;
  }

  /** targetX/Y = player container top-left (world px). dtMs = game ms since last frame. */
  updateAI(targetX, targetY, dtMs) {
    const ctx = this.ctx;
    if (!this.isAlive || this.swallowed || !ctx.isGameRunning() || ctx.isGamePaused()) return;

    const now = ctx.nowMs;
    // status icons
    this.iconSlowed.setVisible(now < this.slowUntil);
    this.iconStunned.setVisible(now < this.stunUntil);
    if (now < this.stunUntil) return;   // stunned: no move, no attack (still takes damage)

    const dt = dtMs / 1000 * (now < this.slowUntil ? this.slowFactor : 1);
    const moveSpeed = BASE_SPEED * this.speedMultiplier;

    const currentX = this.container.x;
    const currentY = this.container.y;
    const dx = targetX - currentX;
    const dy = targetY - currentY;
    const distance = Math.hypot(dx, dy);

    let nextX = currentX;
    let nextY = currentY;

    switch (this.state) {
      case STATE.ORBIT: {
        this.timeInOrbitMs += dtMs;
        const tSec = now / 1000;
        const hold = this.computeHoldPoint(targetX, targetY, currentX, currentY, distance, tSec, now);
        let orbitSpeed = (distance > this.holdMaxDistance + 60 && this.moveStyle !== STYLE.PATROL
          && this.moveStyle !== STYLE.ERRATIC) ? moveSpeed : moveSpeed * ORBIT_SPEED_FACTOR;
        if (this.moveStyle === STYLE.KITE && distance < this.holdMinDistance) orbitSpeed = moveSpeed;

        const hdx = hold[0] - currentX;
        const hdy = hold[1] - currentY;
        const hdist = Math.hypot(hdx, hdy);
        if (hdist > 0.001) {
          const step = Math.min(orbitSpeed * dt, hdist);
          nextX += (hdx / hdist) * step;
          nextY += (hdy / hdist) * step;
        }

        // attack permission: longest-waiting ready enemy goes first
        const slotFree = this.attackSlotAvailable();
        let outranked = false;
        const all = ctx.getEnemies();
        for (let i = 0; i < all.length; i++) {
          const o = all[i];
          if (o !== this && o.isReadyToAttack(targetX, targetY, now) && o.timeInOrbitMs > this.timeInOrbitMs) {
            outranked = true;
            break;
          }
        }
        if (this.isReadyToAttack(targetX, targetY, now) && slotFree && !outranked) {
          this.claimAttackSlot();
          this.state = STATE.WINDUP;
          this.windupStartMs = now;
          this.startWindup(targetX, targetY);
        }
        break;
      }

      case STATE.WINDUP: {
        if (now - this.windupStartMs >= WINDUP_MS) {
          this.clearAttackPath();
          if (this.attackType === ATTACK.DASH) {
            this.hasHitPlayerThisDash = false;
            this.state = STATE.ATTACKING;
          } else {
            this.fireRangedAttack();
            this.releaseAttackSlot(now);
            this.timeInOrbitMs = 0;
            this.state = STATE.ORBIT;
          }
        }
        break;
      }

      case STATE.ATTACKING: {
        const ddx = this.dashTargetX - currentX;
        const ddy = this.dashTargetY - currentY;
        const ddist = Math.hypot(ddx, ddy);
        if (ddist > 0.001) {
          const step = Math.min(this.dashSpeed * dt, ddist);
          nextX += (ddx / ddist) * step;
          nextY += (ddy / ddist) * step;
        }
        // fast dash: test the whole path travelled this frame, using centres
        const pr = ctx.playerRect();
        const pcx = targetX + pr.w / 2;
        const pcy = targetY + pr.h / 2;
        const ehw = this.w / 2, ehh = this.h / 2;
        if (!this.hasHitPlayerThisDash
          && distanceToSegment(pcx, pcy, currentX + ehw, currentY + ehh, nextX + ehw, nextY + ehh) <= ATTACK_TRIGGER_RANGE) {
          ctx.damagePlayer(this.attackDamage);
          fx.playerHit(ctx.gs, pcx, pcy, this.attackDamage);
          this.hasHitPlayerThisDash = true;
          if (ddist > 0.001) this.onPlayerHit(ddx / ddist, ddy / ddist);
        }
        if (ddist <= 15) {
          this.releaseAttackSlot(now);
          this.timeInOrbitMs = 0;
          this.state = STATE.ORBIT;
        }
        break;
      }
      default: break;
    }

    // keep distance from the other enemies (an enemy that is dashing is not pushed)
    const all = ctx.getEnemies();
    for (let i = 0; i < all.length; i++) {
      const other = all[i];
      if (this.state === STATE.ORBIT && other !== this && other.isAlive && !other.isBoss) {
        const ox = other.container.x, oy = other.container.y;
        const d = Math.hypot(nextX - ox, nextY - oy);
        if (d > 0.001 && d < ENEMY_SPACING) {
          const force = (ENEMY_SPACING - d) / ENEMY_SPACING * SEPARATION_SPEED * dt;
          nextX += (nextX - ox) / d * force;
          nextY += (nextY - oy) / d * force;
        }
      }
    }

    // recoil after shooting (jellyfish)
    if (this.recoilVx !== 0 || this.recoilVy !== 0) {
      nextX += this.recoilVx * dt;
      nextY += this.recoilVy * dt;
      const decay = Math.exp(-RECOIL_DECAY * dt);
      this.recoilVx *= decay;
      this.recoilVy *= decay;
      if (Math.abs(this.recoilVx) + Math.abs(this.recoilVy) < 5) this.recoilVx = this.recoilVy = 0;
    }

    // knockback from being hit (55 px over 120 ms)
    if (this.kbLeftMs > 0) {
      const part = Math.min(dtMs, this.kbLeftMs) / KNOCKBACK_MS;
      nextX += this.kbX * part;
      nextY += this.kbY * part;
      this.kbLeftMs -= dtMs;
    }

    nextX = Math.max(0, Math.min(ctx.W - this.w, nextX));
    nextY = Math.max(0, Math.min(ctx.H - this.h, nextY));
    this.container.setPosition(nextX, nextY);

    // sprite animation: flip toward the player and wobble
    this.animTime += 0.08;
    const facing = dx < 0 ? 1 : -1;
    const wave = Math.sin(this.animTime * 2.5);
    this.avatar.setRotation(Phaser.Math.DegToRad(Math.sin(this.animTime * 1.5) * 7));
    this.avatar.setScale(facing * (1 + wave * 0.06), 1 - wave * 0.06);
  }

  // ---------- attacks ----------
  fireRangedAttack() {
    const ctx = this.ctx;
    const ecx = this.centerX();
    const ecy = this.centerY();
    const ax = this.aimDirX, ay = this.aimDirY;
    const onHit = () => this.onPlayerHit(ax, ay);

    if (this.attackType === ATTACK.LINE_SHOT) {
      let speed = BASE_SPEED * this.speedMultiplier * LINE_SHOT_SPEED_FACTOR;
      // every stage passed, the time a bolt needs to cross the screen is 0.2 s shorter (faster bolts)
      const maxDist = Math.hypot(ctx.W, ctx.H);
      const travelS = Math.max(MIN_TRAVEL_S, maxDist / speed - TRAVEL_CUT_PER_STAGE_S * (this.cfg.stage - 1));
      speed = maxDist / travelS;
      // 15-shot barrage along the locked direction, 120 ms apart; damage per shot is 1/3 so a barrage can't one-shot
      this.recoilVx = -ax * RECOIL_SPEED;
      this.recoilVy = -ay * RECOIL_SPEED;
      const shotDamage = Math.max(1, Math.round(this.attackDamage / 3));
      for (let i = 0; i < BARRAGE_SHOTS; i++) {
        const shot = () => {
          if (this.isAlive && ctx.isGameRunning() && !ctx.isGamePaused()) {
            ranged.fireLine(ctx, ecx, ecy, ax, ay, speed, shotDamage, onHit, WAVE_AMPLITUDE_PX, WAVE_LENGTH_PX);
          }
        };
        if (i === 0) shot(); else ctx.after(i * BARRAGE_GAP_MS, shot);
      }
    } else if (this.attackType === ATTACK.INK_CONE) {
      ranged.inkCone(ctx, ecx, ecy, this.aimDirX, this.aimDirY, this.attackDamage, onHit);
    }
  }

  startWindup(targetX, targetY) {
    const ctx = this.ctx;
    playSfx(ctx.gs, 'sfx_enemy_warn');
    const pr = ctx.playerRect();
    const ew = this.w, eh = this.h;
    const ecx = this.container.x + ew / 2;
    const ecy = this.container.y + eh / 2;
    let pcx = targetX + pr.w / 2;
    let pcy = targetY + pr.h / 2;

    if (this.attackType === ATTACK.LINE_SHOT) {
      // lead the target along the direction the player is moving
      const ratio = ctx.getPlayerSpeedRatio();
      if (ratio > 0.1) {
        const rad = ctx.getPlayerAngle() * Math.PI / 180;
        const lead = MAX_SPEED * ratio * WINDUP_MS / 1000;
        pcx += Math.cos(rad) * lead;
        pcy += Math.sin(rad) * lead;
      }
    }

    if (this.attackType !== ATTACK.DASH) {
      const adx = pcx - ecx, ady = pcy - ecy;
      const alen = Math.max(Math.hypot(adx, ady), 1);
      this.aimDirX = adx / alen;
      this.aimDirY = ady / alen;
      this.clearAttackPath();
      if (this.attackType === ATTACK.LINE_SHOT) {
        const reach = Math.hypot(ctx.W, ctx.H);
        this.attackPathView = fx.attackPath(ctx.gs, ecx, ecy, ecx + this.aimDirX * reach, ecy + this.aimDirY * reach,
          ranged.LINE_BAND_WIDTH, WINDUP_MS);
      } else {
        this.attackPathView = ranged.showInkTelegraph(ctx.gs, ecx, ecy, this.aimDirX, this.aimDirY, WINDUP_MS);
      }
      return;
    }

    // dash: run through the player and out the other side
    const ddx = pcx - ecx, ddy = pcy - ecy;
    const len = Math.max(Math.hypot(ddx, ddy), 1);
    const tx = pcx + ddx / len * PASS_THROUGH_DISTANCE;
    const ty = pcy + ddy / len * PASS_THROUGH_DISTANCE;
    // the destination must be inside the screen or the enemy would be clamped at the edge and never arrive
    this.dashTargetX = Math.max(0, Math.min(ctx.W - ew, tx - ew / 2));
    this.dashTargetY = Math.max(0, Math.min(ctx.H - eh, ty - eh / 2));
    this.clearAttackPath();
    this.attackPathView = fx.attackPath(ctx.gs, ecx, ecy, this.dashTargetX + ew / 2, this.dashTargetY + eh / 2,
      ATTACK_TRIGGER_RANGE * 2, WINDUP_MS);
  }

  // ---------- taking damage ----------
  takeDamage(damage, knockback = true) {
    const ctx = this.ctx;
    damage = damage + ctx.damageBonus();
    if (!this.isAlive || !ctx.isGameRunning()) return;

    this.hp = Math.max(0, this.hp - damage);
    playSfx(ctx.gs, this.hp <= 0 ? 'sfx_enemy_die' : 'sfx_hit_enemy');
    this.refreshHpUi();
    fx.damageText(ctx.gs, this.centerX(), this.container.y, damage, '#FFEB3B');

    // knockback: away from the player (applied smoothly by updateAI)
    if (knockback && this.allowKnockback() && !this.swallowed) {
      const pr = ctx.playerRect();
      const kdx = this.container.x - pr.x;
      const kdy = this.container.y - pr.y;
      const kd = Math.hypot(kdx, kdy);
      if (kd > 0.001) {
        this.kbX = kdx / kd * KNOCKBACK_DIST;
        this.kbY = kdy / kd * KNOCKBACK_DIST;
        this.kbLeftMs = KNOCKBACK_MS;
      }
    }

    // being hit interrupts a windup / dash
    if (this.state === STATE.ATTACKING || this.state === STATE.WINDUP) {
      this.clearAttackPath();
      this.releaseAttackSlot(ctx.nowMs);
      this.timeInOrbitMs = 0;
      this.state = STATE.ORBIT;
    }

    // hit flash
    if (!this.swallowed) {
      ctx.gs.tweens.add({ targets: this.container, alpha: 0.3, duration: 80, yoyo: true });
    }

    // multi-life enemies (boss): this life is over but it comes back (reviveOnDepleted returns true)
    if (this.hp <= 0 && this.reviveOnDepleted()) return;

    if (this.hp <= 0) {
      this.isAlive = false;
      this.clearAttackPath();
      ctx.gs.tweens.add({
        targets: this.container, scaleX: 0, scaleY: 0, alpha: 0, duration: 250,
        onComplete: () => this.container.setVisible(false),
      });
      if (!this.isBoss) {
        this.deathShockwave();
        ctx.dropItemAt(this.centerX(), this.centerY(), false);
      }
      this.onDefeated();
      ctx.onEnemyDefeated();
    }
  }

  // ---- hooks for special enemies (boss / boss minions) ----
  damageScale() { return 1; }
  hpScale() { return 1; }
  allowKnockback() { return true; }
  reviveOnDepleted() { return false; }
  onDefeated() {}
  livesLeft() { return 1; }

  /** Remove quietly: not counted as defeated (no shockwave, no boss HP loss, no win check). */
  removeSilently() {
    if (!this.isAlive) return;
    this.isAlive = false;
    this.clearAttackPath();
    if (this.state !== STATE.ORBIT) this.releaseAttackSlot(this.ctx.nowMs);
    this.ctx.gs.tweens.add({
      targets: this.container, scaleX: 0, scaleY: 0, alpha: 0, duration: 300,
      onComplete: () => this.container.setVisible(false),
    });
  }

  /** On death: push the player away from the corpse (no damage) + expanding ring. */
  deathShockwave() {
    const ctx = this.ctx;
    if (!ctx.isGameRunning()) return;
    const pc = ctx.playerCenter();
    const ecx = this.centerX(), ecy = this.centerY();
    let dx = pc.x - ecx, dy = pc.y - ecy;
    let len = Math.hypot(dx, dy);
    if (len < 1) {
      const a = Math.random() * Math.PI * 2;
      dx = Math.cos(a); dy = Math.sin(a); len = 1;
    }
    ctx.knockbackPlayer(dx / len, dy / len, SHOCKWAVE_SPEED);
    const ring = ctx.gs.add.circle(ecx, ecy, 100).setStrokeStyle(10, 0xFFFFFF, 0.7).setDepth(30);
    ring.isFilled = false;
    ctx.gs.tweens.add({ targets: ring, scale: 6, alpha: 0, duration: 450, onComplete: () => ring.destroy() });
  }

  destroy() {
    this.clearAttackPath();
    this.container.destroy();
  }
}
