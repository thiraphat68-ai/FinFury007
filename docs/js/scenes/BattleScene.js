// Battle world (port of BattleActivity's game logic). The HUD, joystick, buttons and overlays live in BattleUIScene.
// World units = Android px on a 1920x1080 reference screen (see config.js); the camera shows it at zoom 2/3.
// Hero for now: Swordfish only (skills 1 and 2). Boss stage (5) is a placeholder.
import { FONT, WORLD_W, WORLD_H, MAX_SPEED, dp, BOSS_MINIONS } from '../config.js';
import { showVideo } from '../core/background.js';
import { playSfx } from '../core/audio.js';
import { unlockNext, saveBestStars } from '../core/stageProgress.js';
import { stageConfig } from '../data/stages.js';
import { createHero } from '../heroes/heroFactory.js';
import SeaEnemy, { resetAttackQueue, setDualAttack } from '../game/SeaEnemy.js';
import KrakenBoss from '../game/KrakenBoss.js';
import BossMinion from '../game/BossMinion.js';
import ItemManager from '../game/ItemManager.js';
import * as fx from '../game/effects.js';

const HIT_INVULN_MS = 350;
const STUN_IMMUNE_AFTER_MS = 1500;
const NAME_H = 36;                       // height of the name label above the hero image (world px)
const PLAYER_DEPTH = 10;

// ULT debuff / energy drain (BattleActivity constants)
const ULT_DEBUFF_DURATION_MS = 5000;
const ENERGY_DRAIN_FROM_STAGE = 3;
const ENERGY_DRAIN_EVERY_HITS = 3;
const ENERGY_DRAIN_PERCENT = 20;

export default class BattleScene extends Phaser.Scene {
  constructor() { super('BattleScene'); }

  init(data) {
    this.stage = (data && data.stage) || 1;
    this.heroId = (data && data.heroId) || 1;
  }

  create() {
    showVideo(null);

    this.gs = this;                    // "game scene" handle used by skills/enemies (Phaser.Scene.scene is the ScenePlugin)
    this.W = WORLD_W;
    this.H = WORLD_H;
    this.cfg = stageConfig(this.stage);

    // ---- camera: show the whole 1920x1080 world inside the 1280x720 canvas ----
    const cam = this.cameras.main;
    cam.setZoom(2 / 3);
    cam.centerOn(this.W / 2, this.H / 2);

    // ---- background (cover) ----
    const key = `bg_stage_${this.stage}`;
    if (this.textures.exists(key)) {
      const bg = this.add.image(this.W / 2, this.H / 2, key).setDepth(0);
      bg.setScale(Math.max(this.W / bg.width, this.H / bg.height));
    } else {
      this.add.rectangle(this.W / 2, this.H / 2, this.W, this.H, 0x0A1420).setDepth(0);
    }

    // ---- state ----
    this.running = true;
    this.paused = false;
    this.nowMs = 0;
    this.tasks = [];
    this.skillLock = false;
    this.pScale = 1;
    this.moveX = 0; this.moveY = 0;
    this.playerAngle = 0;
    this.velX = 0; this.velY = 0;
    this.faceScale = -1;
    this.tilt = 0;
    this.swimTime = 0;
    this.isFacingRight = true;
    this.speedMultiplier = 1;
    this.cooldownMultiplier = 1;
    this.slowFactor = 1; this.slowRemainingMs = 0;
    this.stunRemainingMs = 0; this.stunImmuneMs = 0;
    this.lastDamageMs = -1e9;
    this.cdEnd = [0, 0, 0];
    this.cdTotal = [0, 0, 0];
    this.enemies = [];

    // ---- stack gauge / quiz / ultimate state (BattleActivity) ----
    this.currentStack = 0;
    this.stackFraction = 0;
    this.hitsTaken = 0;
    this.ultDebuffMs = 0;                 // ULT debuff: less stack gain per hit
    this.ultimateReady = false;           // quiz answered correctly, waiting for the player to press ULT
    this.ultimateRunning = false;         // an ultimate is in progress
    this.ultTimerMs = 0; this.ultTimerTotal = 1;
    this.bonusDamage = null;              // Shark Blood Frenzy: number shown above the head
    this.chargeProgress = null;           // Railgun: 0..1 charge bar above the head

    this.hero = createHero(this.heroId);
    this.maxHp = this.hero.getMaxHp();
    this.hp = this.maxHp;
    this.heroBaseSpeed = this.hero.getBaseSpeedMultiplier();
    this.maxStack = this.hero.getStackNeeded();
    const devStack = parseInt(new URLSearchParams(window.location.search).get('stack'), 10);   // dev: ?stack=3 shrinks the gauge for testing
    if (devStack > 0) this.maxStack = devStack;

    this.createPlayer();
    resetAttackQueue();
    this.spawnStageEnemies();
    this.items = new ItemManager(this, true);

    // pause automatically when the tab / app goes to the background
    this.onHidden = () => { if (document.hidden) this.openPause(); };
    document.addEventListener('visibilitychange', this.onHidden);

    this.events.once('shutdown', () => this.cleanup());
    this.scene.launch('BattleUIScene', { battle: this });
  }


  // =========================================================
  // Player
  // =========================================================
  createPlayer() {
    const pw = dp(this.hero.getImageWidthDp());   // hero image width in dp (setupHeroAndSkills)
    const ih = pw * 0.545;                    // image aspect 369/677
    this.pw = pw;
    this.ph = NAME_H + ih;
    this.imgH = ih;

    const x = dp(80);                         // layout_marginStart 80dp
    const y = (this.H - this.ph) / 2;         // layout_gravity center_vertical
    this.px = x;
    this.py = y;

    const c = this.add.container(x, y).setDepth(PLAYER_DEPTH);
    this.playerC = c;
    const name = this.add.text(pw / 2, 0, this.hero.name, {
      fontFamily: FONT, fontSize: '30px', color: '#2ECC71',
    }).setOrigin(0.5, 0);
    this.img = this.add.image(pw / 2, NAME_H + ih / 2, this.hero.getImageKey());
    this.baseScaleX = pw / this.img.width;
    this.baseScaleY = ih / this.img.height;
    this.img.setScale(-this.baseScaleX, this.baseScaleY);
    c.add([name, this.img]);
  }

  playerRect() { return { x: this.px, y: this.py, w: this.pw, h: this.ph }; }
  playerPos() { return { x: this.px, y: this.py }; }
  playerCenter() { return { x: this.px + this.pw / 2, y: this.py + this.ph / 2 }; }

  clampPlayerPos(x, y) {
    return {
      x: Math.max(0, Math.min(this.W - this.pw, x)),
      y: Math.max(0, Math.min(this.H - this.ph, y)),
    };
  }

  setPlayerPos(x, y) {
    const p = this.clampPlayerPos(x, y);
    this.px = p.x;
    this.py = p.y;
    this.applyPlayerTransform();
  }

  // Android scales a View around its centre (x/y/width stay the same); a Phaser container scales around its
  // origin, so the position is shifted to keep the centre fixed.
  applyPlayerTransform() {
    const s = this.pScale;
    this.playerC.setScale(s);
    this.playerC.setPosition(this.px + this.pw / 2 * (1 - s), this.py + this.ph / 2 * (1 - s));
  }

  playerScale() { return this.pScale; }

  setPlayerScale(s) {
    this.pScale = s;
    this.applyPlayerTransform();
  }

  /** Transform of the hero image (for skills that shoot from the mouth). */
  playerBodyTransform() {
    return {
      cx: this.px + this.pw / 2,
      cy: this.py + NAME_H + this.imgH / 2,
      w: this.pw, h: this.imgH,
      sx: this.img.scaleX / this.baseScaleX,
      sy: this.img.scaleY / this.baseScaleY,
      rot: this.img.rotation,
      transY: this.img.y - (NAME_H + this.imgH / 2),
    };
  }

  getPlayerAngle() { return this.playerAngle; }

  getPlayerSpeedRatio() {
    return Math.min(1, Math.hypot(this.velX, this.velY) / MAX_SPEED);
  }

  /** Called by the UI: joystick / keyboard vector (length 0..1). */
  setMove(mx, my) {
    this.moveX = mx;
    this.moveY = my;
    if (mx !== 0 || my !== 0) this.playerAngle = Math.atan2(my, mx) * 180 / Math.PI;
  }

  updatePlayer(dt) {
    const dtMs = dt * 1000;
    const k = 1 - Math.exp(-8 * dt);

    if (this.slowRemainingMs > 0) {
      this.slowRemainingMs -= dtMs;
      if (this.slowRemainingMs <= 0) { this.slowRemainingMs = 0; this.slowFactor = 1; }
    }
    const stunned = this.stunRemainingMs > 0;
    if (stunned) {
      this.stunRemainingMs = Math.max(0, this.stunRemainingMs - dtMs);
      if (this.stunRemainingMs === 0) this.stunImmuneMs = STUN_IMMUNE_AFTER_MS;
    } else if (this.stunImmuneMs > 0) {
      this.stunImmuneMs = Math.max(0, this.stunImmuneMs - dtMs);
    }

    const effSpeed = this.heroBaseSpeed * this.speedMultiplier * this.slowFactor * this.items.speedFactor();
    const locked = this.skillLock || stunned;
    const targetVx = locked ? 0 : this.moveX * MAX_SPEED * effSpeed;
    const targetVy = locked ? 0 : this.moveY * MAX_SPEED * effSpeed;
    this.velX += (targetVx - this.velX) * k;
    this.velY += (targetVy - this.velY) * k;

    if (!this.skillLock) this.setPlayerPos(this.px + this.velX * dt, this.py + this.velY * dt);

    if (this.moveX > 0.15) this.isFacingRight = true;
    else if (this.moveX < -0.15) this.isFacingRight = false;

    // heroes 1 and 2 face LEFT in the source image, 3/4/5 face RIGHT
    const defaultFacingRight = this.hero.spriteFacesRight();
    const targetScaleX = defaultFacingRight ? (this.isFacingRight ? 1 : -1) : (this.isFacingRight ? -1 : 1);
    this.faceScale += (targetScaleX - this.faceScale) * Math.min(1, 15 * dt);

    const speed = Math.min(1, Math.hypot(this.velX, this.velY) / MAX_SPEED);
    this.swimTime += (2 + 8 * speed) * dt;
    const targetTilt = (this.velY / MAX_SPEED) * 20 * (this.isFacingRight ? 1 : -1);
    this.tilt += (targetTilt - this.tilt) * Math.min(1, 10 * dt);
    const wiggle = Math.sin(this.swimTime * 2) * (2 + 8 * speed);
    const squash = Math.sin(this.swimTime * 4) * 0.04 * (0.3 + speed);

    this.img.setRotation(Phaser.Math.DegToRad(this.tilt + wiggle));
    this.img.setScale(this.baseScaleX * this.faceScale * (1 + squash), this.baseScaleY * (1 - squash));
    this.img.y = NAME_H + this.imgH / 2 + Math.sin(this.swimTime) * 4 * (1 - speed);
  }

  // =========================================================
  // Player status (used by enemies)
  // =========================================================
  setSkillLock(locked) { this.skillLock = locked; }

  knockbackPlayer(dirX, dirY, speed) {
    this.velX += dirX * speed;     // updatePlayer eases the velocity back, so the push fades by itself
    this.velY += dirY * speed;
  }

  stunPlayer(durationMs) {
    if (this.stunImmuneMs > 0) return;
    this.stunRemainingMs = Math.max(this.stunRemainingMs, durationMs);
  }

  slowPlayer(factor, durationMs) {
    this.slowFactor = factor;
    this.slowRemainingMs = durationMs;
  }

  damagePlayer(damage) {
    if (!this.running) return;
    // short invulnerability so overlapping bolts don't drain the HP bar in one frame
    if (this.nowMs - this.lastDamageMs < HIT_INVULN_MS) return;
    this.lastDamageMs = this.nowMs;
    if (this.items.absorbHit()) return;          // shield: no damage, not counted as a hit

    this.hp = Math.max(0, this.hp - damage);

    // every Nth hit taken, the enemies cut the ULT energy gained per hit for a while (stages 2-4)
    this.hitsTaken++;
    if (this.cfg.ultDebuffEveryHits > 0 && this.hp > 0 && this.hitsTaken % this.cfg.ultDebuffEveryHits === 0) {
      this.ultDebuffMs = ULT_DEBUFF_DURATION_MS;
    }
    // stage 3+: every 3rd hit taken drains 20% of the gauge (not while a quiz / ULT is pending or running)
    if (this.stage >= ENERGY_DRAIN_FROM_STAGE && this.hp > 0 && this.hitsTaken % ENERGY_DRAIN_EVERY_HITS === 0
      && !this.ultimateReady && !this.ultimateRunning && !(this.ui && this.ui.quizShowing())) {
      const drain = Math.round(this.maxStack * ENERGY_DRAIN_PERCENT / 100);
      this.currentStack = Math.max(0, this.currentStack - drain);
      this.stackFraction = 0;
    }

    playSfx(this, this.hp <= 0 ? 'sfx_lose' : 'sfx_player_hurt');

    this.playerC.setAlpha(0.5);
    this.tweens.add({ targets: this.playerC, alpha: 1, duration: 100 });

    if (this.hp <= 0) this.finish(false);
  }

  // =========================================================
  // Enemies
  // =========================================================
  spawnStageEnemies() {
    const W = this.W, H = this.H;

    // boss stage: the boss is the only enemy (BOSS_MINIONS = false), standing in its resting spot on the right
    if (this.cfg.boss) {
      setDualAttack(BOSS_MINIONS);
      this.boss = new KrakenBoss(this, W * 0.6, H * 0.2, this.cfg);
      this.enemies.push(this.boss);
      if (BOSS_MINIONS) this.spawnBossMinions();
      return;
    }

    setDualAttack(this.cfg.dualAttack);
    const mk = (name, emoji, fx0, fy0) => this.enemies.push(new SeaEnemy(this, name, emoji, W * fx0, H * fy0, this.cfg));
    mk('Crab', '🦀', 0.70, 0.15);
    mk('Jellyfish', '🪼', 0.85, 0.35);
    mk('Turtle', '🐢', 0.95, 0.55);
    if (this.cfg.includeSquid) mk('Kraken', '🦑', 0.90, 0.75);
    mk('Starfish', '⭐️', 0.65, 0.85);
  }

  /** Optional (BOSS_MINIONS): the 5 stage-3 enemies on the left side, life 1 only. */
  spawnBossMinions() {
    const names = ['Crab', 'Jellyfish', 'Turtle', 'Kraken', 'Starfish'];
    const emojis = ['🦀', '🪼', '🐢', '🦑', '⭐️'];
    const xs = [0.10, 0.25, 0.05, 0.22, 0.12];
    const ys = [0.15, 0.35, 0.55, 0.75, 0.85];
    names.forEach((n, i) => this.enemies.push(new BossMinion(this, n, emojis[i], this.W * xs[i], this.H * ys[i])));
  }

  /** The boss lost its first life: minions leave without counting as defeated. */
  onEnemyLifeLost() {
    for (const e of this.enemies) if (e.isMinion) e.removeSilently();
    // the boss lost a life: it drops a heart for sure
    if (this.boss) this.dropItemAt(this.boss.centerX(), this.boss.centerY(), true);
  }

  getEnemies() { return this.enemies; }
  aliveCount() {
    let n = 0;
    for (let i = 0; i < this.enemies.length; i++) if (this.enemies[i].isAlive) n++;
    return n;
  }

  onEnemyDefeated() { this.checkWinCondition(); }

  // =========================================================
  // Stack gauge -> quiz -> ULT button (BattleActivity)
  // =========================================================
  /** Called by skills when they hit an enemy: fills the stack gauge (ignored once the stage is over). */
  onHitEnemySuccess() {
    if (!this.running || !this.enemies.some((e) => e.isAlive)) return;
    if (this.currentStack >= this.maxStack) return;
    // with the debuff each hit gives less (e.g. 0.65); the remainder is kept until it adds up to 1
    this.stackFraction += this.ultDebuffMs > 0 ? this.cfg.ultGainFactor : 1;
    const gained = Math.floor(this.stackFraction);
    if (gained <= 0) return;
    this.stackFraction -= gained;
    this.currentStack = Math.min(this.maxStack, this.currentStack + gained);
    if (this.currentStack >= this.maxStack) this.requestQuiz();
  }

  requestQuiz() {
    if (this.ui) this.ui.quiz.show();
  }

  /** Can the quiz open now? Stage still running, an enemy alive and no other overlay open. */
  canOpenQuiz() {
    return this.running && this.enemies.some((e) => e.isAlive) && !(this.ui && this.ui.otherOverlayOpen());
  }

  /** A quiz that was blocked (pause menu open when the gauge filled) opens once the menu closes. */
  openPendingQuiz() {
    if (this.currentStack >= this.maxStack && !this.ultimateReady && !this.ultimateRunning
      && !(this.ui && this.ui.quizShowing())) this.requestQuiz();
  }

  // quiz listener (QuizManager.Listener)
  onQuizShown() {
    playSfx(this, 'sfx_quiz_show');
    this.setPaused(true);
    this.setMove(0, 0);
  }

  onQuizCorrect() {
    playSfx(this, 'sfx_quiz_correct');
    // a correct answer only UNLOCKS the ULT button; the player presses it whenever they like
    this.setPaused(false);
    this.ultimateReady = true;
    playSfx(this, 'sfx_ult_ready');
  }

  onQuizWrong() {
    playSfx(this, 'sfx_quiz_wrong');
    this.setPaused(false);
    this.currentStack = 0;
  }

  /** ULT button pressed. */
  useUltimate() {
    if (!this.ultimateReady || this.ultimateRunning || this.paused || !this.running) return false;
    this.ultimateReady = false;
    this.ultimateRunning = true;
    playSfx(this, 'sfx_ultimate');
    this.hero.executeUltimateSkill(this);
    return true;
  }

  /** Called by an ultimate when its blocking part ends: unlocks skills and empties the gauge. */
  onUltimateFinished() {
    this.ultimateRunning = false;
    this.currentStack = 0;
  }

  // ---- ItemManager host ----
  healPlayer(amount) { this.hp = Math.min(this.maxHp, this.hp + amount); }

  /** Energy item: +stack directly (opens the quiz when the gauge fills). */
  addStack(amount) {
    if (!this.running || this.currentStack >= this.maxStack) return;
    this.currentStack = Math.min(this.maxStack, this.currentStack + amount);
    if (this.currentStack >= this.maxStack) this.requestQuiz();
  }

  /** Freeze item: every living enemy moves/attacks slower for a while. */
  freezeEnemies(factor, durationMs) {
    for (const e of this.enemies) if (e.isAlive) e.applySlow(factor, durationMs);
  }

  // ---- HUD helpers used by ultimates (BattleContext) ----
  setSpeedMultiplier(m) { this.speedMultiplier = m; }
  setCooldownMultiplier(m) { this.cooldownMultiplier = m; }

  showUltimateDuration(ms) {
    this.ultTimerTotal = Math.max(1, ms);
    this.ultTimerMs = ms;
  }
  hideUltimateDuration() { this.ultTimerMs = 0; }

  setPlayerBonusDamage(n) { this.bonusDamage = n; }
  clearPlayerBonusDamage() { this.bonusDamage = null; }
  setPlayerChargeProgress(p) { this.chargeProgress = Math.max(0, Math.min(1, p)); }
  hidePlayerChargeBar() { this.chargeProgress = null; }

  /** Bonus-damage number and charge bar float above the player's head. */
  updatePlayerOverlays() {
    if (!this.bonusText) {
      this.bonusText = this.add.text(0, 0, '', {
        fontFamily: FONT, fontSize: '44px', fontStyle: 'bold', color: '#FF5252', stroke: '#000000', strokeThickness: 6,
      }).setOrigin(0.5, 1).setDepth(20).setVisible(false);
      this.chargeG = this.add.graphics().setDepth(20);
    }
    const cx = this.px + this.pw / 2;
    const top = this.py;
    if (this.bonusDamage !== null) {
      this.bonusText.setText(`+${this.bonusDamage}`).setPosition(cx, top - 4).setVisible(true);
    } else {
      this.bonusText.setVisible(false);
    }
    if (this.chargeDrawn || this.chargeProgress !== null) this.chargeG.clear();
    this.chargeDrawn = false;
    if (this.chargeProgress !== null) {
      const w = 220, h = 25;
      this.chargeG.fillStyle(0x000000, 0.35);
      this.chargeG.fillRect(cx - w / 2, top - h - 8, w, h);
      this.chargeDrawn = true;
      this.chargeG.fillStyle(0x00E5FF, 1);
      this.chargeG.fillRect(cx - w / 2, top - h - 8, w * this.chargeProgress, h);
    }
  }
  /** An enemy died at (cx, cy): maybe drop an item (forceHeart = boss lost a life). */
  dropItemAt(cx, cy, forceHeart) {
    if (this.items && this.running) this.items.onEnemyDefeated(cx, cy, forceHeart);
  }
  damageBonus() { return this.items ? this.items.damageBonus() : 0; }

  checkWinCondition() {
    if (!this.running) return;
    // boss stage: the stage is won when the boss is dead (minions, if enabled, do not have to be killed)
    const needed = this.cfg.boss ? this.enemies.filter((e) => e.isBoss) : this.enemies;
    if (needed.some((e) => e.isAlive)) return;
    this.finish(true);
  }

  // =========================================================
  // Game state
  // =========================================================
  isGameRunning() { return this.running; }
  isGamePaused() { return this.paused; }

  /** Stars by HP left: >= 70% -> 3, >= 35% -> 2, else 1. */
  starsForRemainingHp() {
    const ratio = this.hp / this.maxHp;
    if (ratio >= 0.70) return 3;
    if (ratio >= 0.35) return 2;
    return 1;
  }

  finish(win) {
    if (!this.running) return;
    this.running = false;
    this.setMove(0, 0);
    this.hero.clearAll();
    this.items.clear();
    // stage over: close the quiz and drop every ultimate effect so only the result screen remains
    if (this.ui) this.ui.quiz.dismiss();
    this.hideUltimateDuration();
    this.clearPlayerBonusDamage();
    this.hidePlayerChargeBar();
    this.updatePlayerOverlays();
    let stars = 0;
    if (win) {
      playSfx(this, 'sfx_win');
      stars = this.starsForRemainingHp();
      saveBestStars(this.stage, stars);
      unlockNext(this.stage);                 // winning unlocks the next stage; losing never does
    }
    if (this.ui) this.ui.showResult(win, stars);
  }

  openPause() {
    if (!this.running || this.paused || (this.ui && this.ui.overlayOpen())) return;
    this.setPaused(true);
    this.setMove(0, 0);
    if (this.ui) this.ui.showPause();
  }

  closePause() {
    this.setPaused(false);
    this.openPendingQuiz();   // a quiz blocked by the pause menu opens now
  }

  setPaused(p) {
    this.paused = p;
    if (p) this.tweens.pauseAll(); else this.tweens.resumeAll();
  }

  restart() { this.scene.restart({ stage: this.stage, heroId: this.heroId }); }
  nextStage() { this.scene.restart({ stage: this.stage + 1, heroId: this.heroId }); }
  exitToStageSelect() { this.scene.start('StageSelectScene'); }

  cleanup() {
    if (this.onHidden) document.removeEventListener('visibilitychange', this.onHidden);
    this.scene.stop('BattleUIScene');
    resetAttackQueue();
    this.ui = null;
  }

  // =========================================================
  // Tasks: time-based effects that must freeze while paused
  // =========================================================
  addTask(fn) { this.tasks.push(fn); }

  /** Run fn after ms of GAME time (frozen while paused). */
  after(ms, fn) {
    let left = ms;
    this.addTask((dtMs) => {
      left -= dtMs;
      if (left > 0) return false;
      fn();
      return true;
    });
  }

  // =========================================================
  // Skills (buttons call these)
  // =========================================================
  cooldownLeftMs(n) { return Math.max(0, this.cdEnd[n] - this.nowMs); }
  cooldownTotalMs(n) { return this.cdTotal[n]; }

  // skills are blocked while an ultimate is running (BattleActivity.canUseSkill)
  canUseSkill() { return this.running && !this.paused && !this.ultimateRunning; }

  useSkill(n) {
    if (!this.canUseSkill() || this.cooldownLeftMs(n) > 0) return false;
    const base = n === 1 ? this.hero.getSkill1CooldownMs() : this.hero.getSkill2CooldownMs();
    const total = base * this.cooldownMultiplier * this.items.cooldownFactor();
    this.cdTotal[n] = total;
    this.cdEnd[n] = this.nowMs + total;
    playSfx(this, n === 1 ? 'sfx_skill1' : 'sfx_skill2');
    if (n === 1) this.hero.useSkill1(this); else this.hero.useSkill2(this);
    return true;
  }

  // =========================================================
  // Frame loop
  // =========================================================
  /** Timers that only run while the game is running and not paused. */
  tickTimers(dtMs) {
    if (this.ultDebuffMs > 0) this.ultDebuffMs = Math.max(0, this.ultDebuffMs - dtMs);
    if (this.ultTimerMs > 0) this.ultTimerMs = Math.max(0, this.ultTimerMs - dtMs);
  }

  update(time, delta) {
    if (!this.running || this.paused || !this.playerC) return;
    const dtMs = Math.min(delta, 50);
    const dt = dtMs / 1000;
    this.nowMs += dtMs;

    this.updatePlayer(dt);
    this.items.update(dt);
    this.tickTimers(dtMs);

    // run time-based effects (dash, bolts, ranged attacks, delayed calls)
    // (in-place loop: no new arrays every frame; tasks added while running are appended and kept)
    const t = this.tasks;
    const n = t.length;
    let w = 0;
    for (let i = 0; i < n; i++) if (!t[i](dtMs)) t[w++] = t[i];
    for (let i = n; i < t.length; i++) t[w++] = t[i];
    t.length = w;
    this.hero.update(this);
    this.updatePlayerOverlays();

    for (let i = 0; i < this.enemies.length; i++) {
      this.enemies[i].updateAI(this.px, this.py, dtMs);
    }
  }
}
