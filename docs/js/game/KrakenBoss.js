// Port of KrakenBoss.java: the giant voxel octopus of stage 5 (2 lives, 47-frame idle animation).
// - Life 1: HP 100. Stands still on the right side and fires fan-shaped wave volleys.
// - Life 2 (phase 2): HP 30, twice as big, attacks 1.5x as often, 7 faster wave lines x 3 volleys, creeps toward the player
//   and slams its tail (15 damage) when the player is close.
// - Immune to stun / slow / swallow / knockback. The player deals +15% damage to it (fractions carried over).
// Positions are the TOP-LEFT of the boss box in world px (like Android's containerView.getX/Y).
import { FONT } from '../config.js';
import { playSfx } from '../core/audio.js';
import SeaEnemy from './SeaEnemy.js';
import * as fx from './effects.js';
import * as ranged from './rangedAttacks.js';

const BOSS_HP = 100;
const MAX_LIVES = 2;
const PHASE2_HP = 30;

const WAVE_WINDUP_MS = 500;
const WAVE_COOLDOWN_MS = 5000;
const WAVE_DAMAGE = 5;
const WAVE_SPREAD_DEG = 14;
const WAVE_SPEED = 1700;
const HIT_SLOW_FACTOR = 0.5;          // being hit by the boss: movement x0.5 ...
const HIT_SLOW_MS = 3000;             // ... for 3 s
const PHASE2_SIZE_MUL = 2;
const PHASE2_MOVE_SPEED = 90;         // px/s, phase 2 creeps toward the player
const PHASE2_WAVE_LINES = 7;
const PHASE2_WAVE_SPREAD_DEG = 10;
const PHASE2_WAVE_VOLLEYS = 3;
const PHASE2_WAVE_SPEED = 2200;
const TAIL_DAMAGE = 15;
const TAIL_COOLDOWN_MS = 5000;
const TAIL_WINDUP_MS = 600;
const TAIL_EXTRA_RADIUS = 120;
const PHASE2_RATE_MUL = 1.5;
const BOSS_SCALE = 8;                 // image box width = 65 * 8 = 520 px (phase 2: x2)
const FRAME_ASPECT = 170 / 256;
const WAVE_VOLLEYS = 2;
const PLAYER_DAMAGE_MUL = 1.15;       // the player deals +15% to the boss

const FRAME_COUNT = 47;
const FRAME_FPS = 24;
const FRAME_W = 256, FRAME_H = 170;

export const BOSS_BASE_WIDTH = 65 * BOSS_SCALE;

const PHASE = { IDLE: 0, WAVE_WINDUP: 1, TAIL_WINDUP: 2 };

export default class KrakenBoss extends SeaEnemy {
  constructor(ctx, posX, posY, cfg) {
    super(ctx, 'Kraken Boss', '🦑', posX, posY, cfg);   // SeaEnemy builds the stats; createView() is overridden below
    this.isBoss = true;
    this.damageMul = cfg.damageMul;

    this.lives = MAX_LIVES;
    this.phase = PHASE.IDLE;
    this.phaseMs = 0;
    this.waveCooldownMs = 2000;           // first wave comes 2 s after the start
    this.tailCooldownMs = 0;
    this.attackRate = 1;
    this.sizeMul = 1;
    this.phase2 = false;
    this.waveLines = 5;
    this.waveSpreadDeg = WAVE_SPREAD_DEG;
    this.waveVolleys = WAVE_VOLLEYS;
    this.waveSpeed = WAVE_SPEED;
    this.waveVolley = 0;
    this.damageCarry = 0;
    this.frameTime = 0;
    this.shownFrame = -1;
    this.animT = 0;
    this.waveTelegraphs = [];
    this.waveDirX = new Array(PHASE2_WAVE_LINES).fill(0);
    this.waveDirY = new Array(PHASE2_WAVE_LINES).fill(0);
    this.tailTelegraph = null;

    this.applyBossSize();
    this.computeHome();
    this.container.setPosition(this.homeX, this.homeY);
  }

  // ---- stats ----
  configureEnemyStats() { this.maxHp = BOSS_HP; this.hp = BOSS_HP; }
  allowKnockback() { return false; }
  livesLeft() { return this.lives; }
  stun() { /* the boss can't be stunned */ }
  applySlow() { /* ... slowed */ }
  setSwallowed() { /* ... or swallowed */ }

  // ---- view: only the animated octopus image (name / hp bar live in the HUD) ----
  createView(posX, posY) {
    const scene = this.ctx.gs;
    const c = scene.add.container(posX, posY).setDepth(11);
    this.container = c;
    this.w = BOSS_BASE_WIDTH;
    this.h = BOSS_BASE_WIDTH * FRAME_ASPECT;
    c.setSize(this.w, this.h);

    this.avatar = scene.add.image(this.w / 2, this.h / 2, 'boss_00');
    // status icons (Swordfish charge etc. still show on the boss)
    this.iconCharged = scene.add.text(this.w / 2 - 40, -26, '⚡', { fontFamily: FONT, fontSize: '34px' }).setOrigin(0.5).setVisible(false);
    this.iconSlowed = scene.add.text(this.w / 2, -26, '🐌', { fontFamily: FONT, fontSize: '34px' }).setOrigin(0.5).setVisible(false);
    this.iconStunned = scene.add.text(this.w / 2 + 40, -26, '💫', { fontFamily: FONT, fontSize: '34px' }).setOrigin(0.5).setVisible(false);
    c.add([this.avatar, this.iconCharged, this.iconSlowed, this.iconStunned]);
  }

  refreshHpUi() { /* the boss HP is drawn by the battle HUD */ }

  /** Image box = 65*8*sizeMul px wide, in the frame's aspect ratio, never taller than 95% of the screen. */
  applyBossSize() {
    let w = BOSS_BASE_WIDTH * this.sizeMulSafe();
    let h = w * FRAME_ASPECT;
    if (h > this.ctx.H * 0.95) { h = this.ctx.H * 0.95; w = h / FRAME_ASPECT; }
    this.w = w;
    this.h = h;
    this.container.setSize(w, h);
    this.avatar.setPosition(w / 2, h / 2);
    this.baseSx = w / FRAME_W;
    this.baseSy = h / FRAME_H;
    this.avatar.setScale(this.baseSx, this.baseSy);
    this.iconCharged.setPosition(w / 2 - 40, -26);
    this.iconSlowed.setPosition(w / 2, -26);
    this.iconStunned.setPosition(w / 2 + 40, -26);
  }

  sizeMulSafe() { return this.sizeMul || 1; }

  computeHome() {
    this.homeX = Math.min(Math.max(0, this.ctx.W * 0.72 - this.w / 2), Math.max(0, this.ctx.W - this.w));
    this.homeY = Math.max(0, (this.ctx.H - this.h) / 2);
  }

  // ---- damage ----
  takeDamage(damage, knockback = true) {
    // +15% for the player; the fraction is carried over so small hits don't lose the bonus
    const total = damage * PLAYER_DAMAGE_MUL + this.damageCarry;
    const dealt = Math.floor(total);
    this.damageCarry = total - dealt;
    if (dealt <= 0) return;
    super.takeDamage(dealt, knockback);
  }

  /** Exact damage with no bonus (used when a minion dies: -10). */
  takeFixedDamage(damage) {
    super.takeDamage(damage, false);
  }

  /** HP ran out: if a life is left, come back angrier (phase 2). */
  reviveOnDepleted() {
    this.lives--;
    this.cancelTelegraphs();
    if (this.lives <= 0) return false;                 // last life: dies normally
    this.maxHp = PHASE2_HP;
    this.hp = this.maxHp;
    playSfx(this.ctx.gs, 'sfx_enemy_warn');
    this.phase = PHASE.IDLE;
    this.phaseMs = 0;
    this.waveCooldownMs = 1000;
    this.ctx.onEnemyLifeLost();                        // minions leave, the HUD updates
    this.enterPhase2();
    return true;
  }

  enterPhase2() {
    const cx = this.container.x + this.w / 2;
    const cy = this.container.y + this.h / 2;
    this.sizeMul = PHASE2_SIZE_MUL;
    this.attackRate = PHASE2_RATE_MUL;
    this.phase2 = true;
    this.waveLines = PHASE2_WAVE_LINES;
    this.waveSpreadDeg = PHASE2_WAVE_SPREAD_DEG;
    this.waveVolleys = PHASE2_WAVE_VOLLEYS;
    this.waveSpeed = PHASE2_WAVE_SPEED;
    this.tailCooldownMs = 2000;                        // first tail slam comes a bit later
    this.applyBossSize();
    // grow around the old centre, then recompute the resting point for the new size
    this.container.setPosition(
      Math.max(0, Math.min(cx - this.w / 2, this.ctx.W - this.w)),
      Math.max(0, Math.min(cy - this.h / 2, this.ctx.H - this.h)));
    this.computeHome();
  }

  // ---- AI ----
  updateAI(targetX, targetY, dtMs) {
    const ctx = this.ctx;
    if (!this.isAlive || !ctx.isGameRunning() || ctx.isGamePaused()) return;

    const dt = dtMs / 1000;
    this.phaseMs += dtMs;
    this.waveCooldownMs -= Math.round(dtMs * this.attackRate);   // phase 2: cooldown runs faster = attacks more often
    this.tailCooldownMs -= dtMs;                                  // tail slam: fixed 5 s cooldown

    const pr = ctx.playerRect();
    const pcx = targetX + pr.w / 2, pcy = targetY + pr.h / 2;
    const bw = this.w, bh = this.h;
    const bcx = this.container.x + bw / 2, bcy = this.container.y + bh / 2;

    switch (this.phase) {
      case PHASE.IDLE: {
        const tailRadius = this.tailRadius();
        const dist = Math.hypot(pcx - bcx, pcy - bcy);
        if (this.phase2 && this.tailCooldownMs <= 0 && dist <= tailRadius) {
          this.enterPhase(PHASE.TAIL_WINDUP);
          this.showTailTelegraph(bcx, bcy, tailRadius);
        } else if (this.waveCooldownMs <= 0) {
          this.enterPhase(PHASE.WAVE_WINDUP);
          this.waveVolley = 0;
          this.showWaveTelegraphs(bcx, bcy, pcx, pcy);
        } else if (this.phase2 && dist > tailRadius * 0.8) {
          // phase 2: creep toward the player (only while not preparing an attack)
          this.moveToward(pcx - bw / 2, pcy - bh / 2, PHASE2_MOVE_SPEED * dt);
        }
        break;
      }
      case PHASE.TAIL_WINDUP: {
        if (this.phaseMs >= TAIL_WINDUP_MS) {
          this.removeTailTelegraph();
          if (Math.hypot(pcx - bcx, pcy - bcy) <= this.tailRadius()) {   // stepped out of the circle in time = no hit
            ctx.damagePlayer(TAIL_DAMAGE);
            ctx.slowPlayer(HIT_SLOW_FACTOR, HIT_SLOW_MS);
            fx.playerHit(ctx.gs, pcx, pcy, TAIL_DAMAGE);
          }
          this.tailCooldownMs = TAIL_COOLDOWN_MS;
          this.enterPhase(PHASE.IDLE);
        }
        break;
      }
      case PHASE.WAVE_WINDUP: {
        if (this.phaseMs >= WAVE_WINDUP_MS) {
          this.releaseWaveTelegraphs();
          const dmg = this.scaled(WAVE_DAMAGE);
          for (let i = 0; i < this.waveLines; i++) {
            ranged.fireLine(ctx, bcx, bcy, this.waveDirX[i], this.waveDirY[i], this.waveSpeed, dmg,
              () => ctx.slowPlayer(HIT_SLOW_FACTOR, HIT_SLOW_MS));
          }
          if (++this.waveVolley < this.waveVolleys) {
            // next volley: aim at the player again, warn again, fire 0.5 s later
            this.phaseMs = 0;
            this.showWaveTelegraphs(bcx, bcy, pcx, pcy);
          } else {
            this.waveCooldownMs = WAVE_COOLDOWN_MS;
            this.enterPhase(PHASE.IDLE);
          }
        }
        break;
      }
      default: break;
    }

    this.animate(dt);
  }

  tailRadius() { return Math.max(this.w, this.h) / 2 + TAIL_EXTRA_RADIUS; }

  moveToward(tx, ty, step) {
    const cx = this.container.x, cy = this.container.y;
    const dx = tx - cx, dy = ty - cy;
    const d = Math.hypot(dx, dy);
    if (d < 0.5) return;
    const k = Math.min(step, d) / d;
    this.container.setPosition(
      Math.max(0, Math.min(this.ctx.W - this.w, cx + dx * k)),
      Math.max(0, Math.min(this.ctx.H - this.h, cy + dy * k)));
  }

  scaled(base) { return Math.max(1, Math.round(base * this.damageMul)); }

  enterPhase(next) { this.phase = next; this.phaseMs = 0; }

  // ---- telegraphs ----
  showTailTelegraph(cx, cy, r) {
    const scene = this.ctx.gs;
    this.removeTailTelegraph();
    const c = scene.add.circle(cx, cy, r, 0xFF1744, 0.33).setStrokeStyle(6, 0xFF8A80).setDepth(6).setAlpha(0.25);
    scene.tweens.add({ targets: c, alpha: 1, duration: TAIL_WINDUP_MS });
    this.tailTelegraph = c;
    playSfx(scene, 'sfx_enemy_warn');
  }

  removeTailTelegraph() {
    if (!this.tailTelegraph) return;
    this.tailTelegraph.destroy();
    this.tailTelegraph = null;
  }

  showWaveTelegraphs(bcx, bcy, pcx, pcy) {
    const ctx = this.ctx;
    const base = Math.atan2(pcy - bcy, pcx - bcx);
    const reach = Math.hypot(ctx.W, ctx.H);
    this.releaseWaveTelegraphs();
    for (let i = 0; i < this.waveLines; i++) {
      const a = base + (i - Math.floor(this.waveLines / 2)) * this.waveSpreadDeg * Math.PI / 180;
      this.waveDirX[i] = Math.cos(a);
      this.waveDirY[i] = Math.sin(a);
      this.waveTelegraphs[i] = fx.attackPath(ctx.gs, bcx, bcy, bcx + this.waveDirX[i] * reach, bcy + this.waveDirY[i] * reach,
        ranged.LINE_BAND_WIDTH, WAVE_WINDUP_MS);
    }
    playSfx(ctx.gs, 'sfx_enemy_warn');
  }

  releaseWaveTelegraphs() {
    for (const t of this.waveTelegraphs) if (t) t.destroy();
    this.waveTelegraphs = [];
  }

  cancelTelegraphs() {
    this.removeTailTelegraph();
    this.releaseWaveTelegraphs();
  }

  // ---- animation: 24 fps idle loop (x1.6 faster when charging a wave), breathing wobble, shaking while charging ----
  animate(dt) {
    this.animT += dt;
    const fps = this.phase === PHASE.WAVE_WINDUP ? FRAME_FPS * 1.6 : FRAME_FPS;
    this.frameTime += dt * fps;
    const idx = Math.floor(this.frameTime) % FRAME_COUNT;
    if (idx !== this.shownFrame) {
      this.shownFrame = idx;
      this.avatar.setTexture(`boss_${String(idx).padStart(2, '0')}`);
    }
    const wave = Math.sin(this.animT * 2.5);
    let rot = Math.sin(this.animT * 1.5) * 4;
    if (this.phase === PHASE.WAVE_WINDUP) rot = Math.sin(this.animT * 40) * 4;   // shakes while preparing to fire
    this.avatar.setRotation(Phaser.Math.DegToRad(rot));
    this.avatar.setScale(this.baseSx * (1 + wave * 0.03), this.baseSy * (1 - wave * 0.03));
  }

  destroy() {
    this.cancelTelegraphs();
    super.destroy();
  }
}
