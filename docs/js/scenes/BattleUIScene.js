// HUD + controls + overlays for the battle (runs on top of BattleScene, in 1280x720 canvas units).
// 1 Android dp = 2 canvas units here. Multi-touch: the joystick tracks its own pointer id and the skill
// buttons are separate interactive objects, so moving and using skills at the same time works.
// Keyboard: WASD / arrows move, J = skill 1, K = skill 2, L (or U) = ultimate, P or Esc = pause.
import { FONT, COLORS, UI_PER_DP, MAX_STAGES } from '../config.js';
import { playSfx } from '../core/audio.js';
import { starsText } from '../core/stageProgress.js';
import { makeButton, makeMuteButton, wrapText } from '../ui/widgets.js';
import { ITEM_LIST, itemLabel } from '../game/ItemManager.js';
import { getBool, setItem } from '../core/storage.js';
import Quiz from '../ui/quiz.js';
import { forSubject } from '../data/questions.js';

const M = 48;                      // 24 dp margins
const JOY_R = 100;                 // joystick base: 100 dp diameter -> radius 100 units
const KNOB_R = 42;
const JOY_TOUCH_R = 170;           // generous touch zone around the base
const SKILL_R = 84;                // 84 dp diameter buttons -> radius 84 units

export default class BattleUIScene extends Phaser.Scene {
  constructor() { super('BattleUIScene'); }

  init(data) {
    this.battle = data.battle;
  }

  create() {
    const b = this.battle;
    b.ui = this;
    const { width, height } = this.scale;
    this.joyId = null;
    this.joyVec = { x: 0, y: 0 };
    this.overlay = null;
    this.lastHp = -1;
    this.botT = 0;

    this.createHud(width);
    this.createJoystick(width, height);
    this.createSkillButtons(width, height);
    this.createStackHud(width);
    if (b.boss) this.createBossHud(width);
    this.buffText = this.add.text(this.hpBarX, this.hpBarY + 38, '', {
      fontFamily: FONT, fontSize: '24px', color: '#FFFFFF', fontStyle: 'bold', stroke: '#000000', strokeThickness: 4,
    });
    this.createUltButton(width, height);
    this.createKeyboard();

    // quiz (opens when the stack gauge is full; the battle pauses while it is open)
    this.quiz = new Quiz(this, b.hero.subject, forSubject(b.hero.subject), {
      onQuizShown: () => b.onQuizShown(),
      onCorrect: () => b.onQuizCorrect(),
      onWrong: () => b.onQuizWrong(),
      onTimeout: () => b.onQuizWrong(),
    }, () => b.canOpenQuiz());

    this.showTutorialIfFirstTime();

    // ---- joystick pointer handling (multi-touch safe) ----
    this.input.on('pointerdown', (p) => {
      if (this.joyId !== null || this.overlayOpen()) return;
      if (Math.hypot(p.x - this.joyBase.x, p.y - this.joyBase.y) <= JOY_TOUCH_R) {
        this.joyId = p.id;
        this.moveJoy(p);
      }
    });
    this.input.on('pointermove', (p) => { if (p.id === this.joyId) this.moveJoy(p); });
    const release = (p) => { if (p.id === this.joyId) this.resetJoy(); };
    this.input.on('pointerup', release);
    this.input.on('pointerupoutside', release);
  }

  // ---------------------------------------------------------
  // HUD: hero portrait + HP bar (top-left), stage info (top-centre), pause button (top-right)
  // ---------------------------------------------------------
  createHud(width) {
    const b = this.battle;
    // profile circle (48 dp at 20/16 dp margins)
    const cx = 40 + 48, cy = 32 + 48;
    this.add.circle(cx, cy, 48, 0x34495E);
    const portrait = this.add.image(cx, cy, b.hero.getImageKey());
    const s = 96 / Math.min(portrait.width, portrait.height);
    portrait.setScale(s);
    const maskShape = this.make.graphics({ x: 0, y: 0 }, false);
    maskShape.fillCircle(cx, cy, 48);
    portrait.setMask(maskShape.createGeometryMask());

    this.add.text(cx + 48 + 20, 32, 'HP', { fontFamily: FONT, fontSize: '24px', color: '#FFFFFF', fontStyle: 'bold' });
    this.hpBarX = cx + 48 + 20;
    this.hpBarY = 32 + 34;
    this.hpG = this.add.graphics();
    this.hpText = this.add.text(this.hpBarX + 120, this.hpBarY + 16, '', {
      fontFamily: FONT, fontSize: '24px', color: '#FFFFFF', fontStyle: 'bold', stroke: '#000000', strokeThickness: 3,
    }).setOrigin(0.5);

    // stage info (top-centre)
    this.stageBg = this.add.rectangle(width / 2, 118, 10, 40, 0x000000, 0.4);
    this.stageText = this.add.text(width / 2, 118, '', {
      fontFamily: FONT, fontSize: '28px', color: '#FFFFFF', fontStyle: 'bold',
    }).setOrigin(0.5);
    this.lastAlive = -1;

    // pause button (44 dp at 16/20 dp margins)
    const px = width - 40 - 44, py = 32 + 44;
    const pause = this.add.container(px, py);
    pause.add([
      this.add.circle(0, 0, 44, 0x000000, 0.35),
      this.add.rectangle(-12, 0, 12, 40, 0xFFFFFF),
      this.add.rectangle(12, 0, 12, 40, 0xFFFFFF),
    ]);
    pause.setSize(88, 88).setInteractive({ useHandCursor: true });
    pause.on('pointerup', () => { playSfx(this, 'sfx_button'); b.openPause(); });
    makeMuteButton(this, px - 100, py);
  }

  refreshHud() {
    const b = this.battle;
    if (b.hp !== this.lastHp) {
      this.lastHp = b.hp;
      const w = 240, h = 32;
      this.hpG.clear();
      this.hpG.fillStyle(0x333333, 1);
      this.hpG.fillRect(this.hpBarX, this.hpBarY, w, h);
      this.hpG.fillStyle(0x2ECC71, 1);
      this.hpG.fillRect(this.hpBarX, this.hpBarY, w * (b.hp / b.maxHp), h);
      this.hpText.setText(`${b.hp}/${b.maxHp}`);
    }
    const alive = b.aliveCount();
    const debuff = b.ultDebuffMs > 0;
    const lives = b.boss ? b.boss.livesLeft() : 0;
    if (alive !== this.lastAlive || debuff !== this.lastDebuff || lives !== this.lastLives) {
      this.lastLives = lives;
      this.lastDebuff = debuff;
      this.lastAlive = alive;
      const bossLives = b.boss && b.boss.isAlive ? b.boss.livesLeft() : 0;
      let info = bossLives > 0
        ? `ด่าน ${b.stage}  |  บอสเหลือ ${bossLives} ชีวิต`
        : `ด่าน ${b.stage}  |  ศัตรูเหลือ ${alive} ตัว`;
      if (debuff) info += `  |  ⚠ ULT -${Math.round((1 - b.cfg.ultGainFactor) * 100)}%`;
      this.stageText.setText(info);
      this.stageBg.setSize(this.stageText.width + 40, 44);
    }
  }

  // ---------------------------------------------------------
  // Boss HP bar (stage 5): big bar at the top centre under the stage line
  // ---------------------------------------------------------
  createBossHud(width) {
    const w = 760, cx = width / 2;
    this.bossX = cx - w / 2;
    this.bossW = w;
    this.bossBarY = 176;
    this.add.rectangle(cx, 168, w + 28, 84, 0x000000, 0.45);
    this.bossName = this.add.text(cx, 142, '', {
      fontFamily: FONT, fontSize: '28px', color: '#B388FF', fontStyle: 'bold', stroke: '#000000', strokeThickness: 4,
    }).setOrigin(0.5);
    this.bossG = this.add.graphics();
    this.bossHpText = this.add.text(cx, this.bossBarY + 16, '', {
      fontFamily: FONT, fontSize: '24px', color: '#FFFFFF', fontStyle: 'bold', stroke: '#000000', strokeThickness: 4,
    }).setOrigin(0.5);
    this.lastBossKey = '';
  }

  refreshBossHud() {
    const boss = this.battle.boss;
    if (!boss || !this.bossG) return;
    const key = `${boss.hp}/${boss.maxHp}/${boss.lives}`;
    if (key === this.lastBossKey) return;
    this.lastBossKey = key;
    const h = 32;
    this.bossG.clear();
    this.bossG.fillStyle(0x2A1B3D, 1);
    this.bossG.fillRect(this.bossX, this.bossBarY, this.bossW, h);
    this.bossG.fillStyle(0xAA00FF, 1);
    this.bossG.fillRect(this.bossX, this.bossBarY, this.bossW * Math.max(0, boss.hp) / boss.maxHp, h);
    this.bossG.lineStyle(3, 0xE1BEE7, 0.8);
    this.bossG.strokeRect(this.bossX, this.bossBarY, this.bossW, h);
    this.bossName.setText(`Kraken Boss ${'❤'.repeat(Math.max(0, boss.lives))}`);
    this.bossHpText.setText(`${Math.max(0, boss.hp)}/${boss.maxHp}`);
  }

  // ---------------------------------------------------------
  // Stack gauge (top-centre): "<Hero> | Stack: cur/max" + bar
  // ---------------------------------------------------------
  createStackHud(width) {
    const w = 470, h = 50, cx = width / 2, cy = 56;
    this.add.rectangle(cx, cy, w, h, 0x000000, 0.4);
    this.stackBarX = cx - w / 2 + 16;
    this.stackBarY = cy - 12;
    this.stackG = this.add.graphics();
    this.stackText = this.add.text(this.stackBarX + 180 + 14, cy, '', {
      fontFamily: FONT, fontSize: '22px', color: '#FFFFFF',
    }).setOrigin(0, 0.5);
    this.lastStack = -1;
  }

  refreshStackHud() {
    const b = this.battle;
    if (b.currentStack === this.lastStack) return;
    this.lastStack = b.currentStack;
    this.stackG.clear();
    this.stackG.fillStyle(0x333333, 1);
    this.stackG.fillRect(this.stackBarX, this.stackBarY, 180, 24);
    this.stackG.fillStyle(0xF39C12, 1);
    this.stackG.fillRect(this.stackBarX, this.stackBarY, 180 * (b.currentStack / b.maxStack), 24);
    this.stackText.setText(`${b.hero.name} | Stack: ${b.currentStack}/${b.maxStack}`);
  }

  // ---------------------------------------------------------
  // ULT button (above the skill buttons) + timed-ultimate bar
  // ---------------------------------------------------------
  createUltButton(width, height) {
    const cx = width - M - SKILL_R - SKILL_R - 12;          // centred above the two skill buttons
    const cy = height - M - SKILL_R * 2 - 24 - 76;           // 12 dp gap, radius 76
    const c = this.add.container(cx, cy);
    const circle = this.add.circle(0, 0, 76, 0xF39C12).setStrokeStyle(4, 0xFFFFFF, 0.6);
    const text = this.add.text(0, 0, '🔒 ULT', {
      fontFamily: FONT, fontSize: '26px', color: '#FFFFFF', fontStyle: 'bold',
    }).setOrigin(0.5);
    c.add([circle, text]);
    c.setSize(152, 152).setInteractive(new Phaser.Geom.Circle(76, 76, 76), Phaser.Geom.Circle.Contains);
    c.on('pointerdown', () => this.pressUltimate());
    c.setAlpha(0.4);
    this.ultBtn = c;
    this.ultText = text;
    this.ultState = 'locked';

    // remaining time of timed ultimates (Circuit Link, Blood Frenzy)
    this.ultTimerG = this.add.graphics();
    this.ultTimerText = this.add.text(cx, cy - 76 - 34, '', {
      fontFamily: FONT, fontSize: '22px', color: '#FFFFFF', fontStyle: 'bold', stroke: '#000000', strokeThickness: 4,
    }).setOrigin(0.5).setVisible(false);
    this.ultTimerPos = { x: cx, y: cy - 76 - 14 };
  }

  pressUltimate() {
    if (this.battle.useUltimate()) {
      this.tweens.add({ targets: this.ultBtn, scale: 0.85, duration: 80, yoyo: true });
    }
  }

  /** locked = dim + disabled, ready = bright + pop (BattleActivity.updateUltimateButton) */
  refreshUltUi() {
    const b = this.battle;
    const state = b.ultimateReady ? 'ready' : 'locked';
    if (state !== this.ultState) {
      this.ultState = state;
      this.tweens.killTweensOf(this.ultBtn);
      this.ultBtn.setScale(1);
      if (state === 'ready') {
        this.ultBtn.setAlpha(1);
        this.ultText.setText('ULT!');
        this.tweens.add({ targets: this.ultBtn, scale: 1.15, duration: 150, yoyo: true });
      } else {
        this.ultBtn.setAlpha(0.4);
        this.ultText.setText('🔒 ULT');
      }
    }

    const g = this.ultTimerG;
    g.clear();
    if (b.ultTimerMs > 0) {
      const w = 192, h = 18;                              // 96 dp wide, 9 dp tall
      const x = this.ultTimerPos.x - w / 2, y = this.ultTimerPos.y;
      g.fillStyle(0x000000, 0.33); g.fillRect(x, y, w, h);
      g.fillStyle(0xF39C12, 1); g.fillRect(x, y, w * (b.ultTimerMs / b.ultTimerTotal), h);
      const tenths = Math.floor(b.ultTimerMs / 100);
      this.ultTimerText.setText(`${b.hero.getUltimateName()} ${Math.floor(tenths / 10)}.${tenths % 10}s`)
        .setPosition(this.ultTimerPos.x, y - 16).setVisible(true);
    } else {
      this.ultTimerText.setVisible(false);
    }
  }

  // ---------------------------------------------------------
  // Virtual joystick (bottom-left)
  // ---------------------------------------------------------
  createJoystick(width, height) {
    const cx = M + JOY_R, cy = height - M - JOY_R;
    this.joyBase = { x: cx, y: cy };
    this.add.circle(cx, cy, JOY_R, 0xFFFFFF, 0.18).setStrokeStyle(4, 0xFFFFFF, 0.4);
    this.knob = this.add.circle(cx, cy, KNOB_R, 0xFFFFFF, 0.75);
  }

  moveJoy(p) {
    const dx = p.x - this.joyBase.x;
    const dy = p.y - this.joyBase.y;
    const dist = Math.hypot(dx, dy);
    const clamped = Math.min(dist, JOY_R);
    if (dist > 0) {
      this.knob.setPosition(this.joyBase.x + dx / dist * clamped, this.joyBase.y + dy / dist * clamped);
      this.joyVec = { x: dx / dist * (clamped / JOY_R), y: dy / dist * (clamped / JOY_R) };
    }
  }

  resetJoy() {
    this.joyId = null;
    this.joyVec = { x: 0, y: 0 };
    this.knob.setPosition(this.joyBase.x, this.joyBase.y);
  }

  // ---------------------------------------------------------
  // Skill buttons (bottom-right) with cooldown display
  // ---------------------------------------------------------
  createSkillButtons(width, height) {
    const b = this.battle;
    const y = height - M - SKILL_R;
    const x2 = width - M - SKILL_R;
    const x1 = x2 - SKILL_R * 2 - 24;
    this.skillBtns = [
      null,
      this.makeSkillButton(x1, y, 0xE74C3C, `${b.hero.getSkill1Icon()}\n${b.hero.getSkill1Name()}`, 1),
      this.makeSkillButton(x2, y, 0x2980B9, `${b.hero.getSkill2Icon()}\n${b.hero.getSkill2Name()}`, 2),
    ];
  }

  makeSkillButton(x, y, color, label, n) {
    const c = this.add.container(x, y);
    const circle = this.add.circle(0, 0, SKILL_R, color).setStrokeStyle(4, 0xFFFFFF, 0.5);
    const text = this.add.text(0, 0, label, {
      fontFamily: FONT, fontSize: '22px', color: '#FFFFFF', fontStyle: 'bold', align: 'center',
      wordWrap: { width: SKILL_R * 1.7 },
    }).setOrigin(0.5);
    c.add([circle, text]);
    c.setSize(SKILL_R * 2, SKILL_R * 2);
    c.setInteractive(new Phaser.Geom.Circle(SKILL_R, SKILL_R, SKILL_R), Phaser.Geom.Circle.Contains);
    c.on('pointerdown', () => this.pressSkill(n));
    c.label = label;
    c.text = text;
    c.circle = circle;
    return c;
  }

  pressSkill(n) {
    const btn = this.skillBtns[n];
    if (this.battle.useSkill(n) && btn) {
      this.tweens.add({ targets: btn, scale: 0.85, duration: 80, yoyo: true });
    }
  }

  refreshSkillButtons() {
    const b = this.battle;
    for (let n = 1; n <= 2; n++) {
      const btn = this.skillBtns[n];
      const left = b.cooldownLeftMs(n);
      // only touch the text when what is displayed actually changes (not every frame)
      const shown = left > 0 ? (left / 1000).toFixed(1) : btn.label;
      if (shown !== btn.shown) {
        btn.shown = shown;
        btn.text.setText(shown);
        btn.text.setFontSize(left > 0 ? '36px' : '24px');
        btn.circle.setAlpha(left > 0 ? 0.45 : 1);
      }
    }
  }

  // ---------------------------------------------------------
  // Keyboard (desktop)
  // ---------------------------------------------------------
  createKeyboard() {
    const kb = this.input.keyboard;
    if (!kb) return;
    this.keys = kb.addKeys('W,A,S,D,UP,DOWN,LEFT,RIGHT');
    kb.on('keydown-J', () => this.pressSkill(1));
    kb.on('keydown-K', () => this.pressSkill(2));
    kb.on('keydown-L', () => this.pressUltimate());
    // desktop: keys 1-4 answer the quiz
    ['ONE', 'TWO', 'THREE', 'FOUR'].forEach((name, i) => kb.on(`keydown-${name}`, () => { if (this.quiz && this.quiz.state === 'question') this.quiz.resolve(i); }));
    kb.on('keydown-U', () => this.pressUltimate());
    const togglePause = () => {
      if (this.overlay && this.overlay.kind === 'pause') this.hideOverlay(true);
      else this.battle.openPause();
    };
    kb.on('keydown-P', togglePause);
    kb.on('keydown-ESC', togglePause);
  }

  keyboardVector() {
    const k = this.keys;
    if (!k) return { x: 0, y: 0 };
    let x = 0, y = 0;
    if (k.A.isDown || k.LEFT.isDown) x -= 1;
    if (k.D.isDown || k.RIGHT.isDown) x += 1;
    if (k.W.isDown || k.UP.isDown) y -= 1;
    if (k.S.isDown || k.DOWN.isDown) y += 1;
    const len = Math.hypot(x, y);
    return len > 0 ? { x: x / len, y: y / len } : { x: 0, y: 0 };
  }

  // ---------------------------------------------------------
  // Frame update
  // ---------------------------------------------------------
  update(time, delta) {
    const b = this.battle;
    if (!b || !b.playerC) return;

    // movement input: joystick wins, otherwise keyboard (blocked while an overlay is open)
    let v = { x: 0, y: 0 };
    if (!this.overlayOpen()) {
      v = this.joyId !== null ? this.joyVec : this.keyboardVector();
      if (this.botEnabled()) v = this.botVector(delta);
    }
    b.setMove(v.x, v.y);

    this.refreshHud();
    this.refreshSkillButtons();
    this.refreshStackHud();
    this.refreshBossHud();
    if (this.battle.items.buffTextChanged()) this.buffText.setText(this.battle.items.buffText());
    this.refreshUltUi();
    this.quiz.update(delta);
    if (this.botEnabled()) {       // dev bot: answers the quiz correctly and fires the ultimate
      if (this.quiz.state === 'question') this.quiz.resolve(this.quiz.current.answer);
      if (this.battle.ultimateReady && !this.battle.ultimateRunning) this.pressUltimate();
    }
  }

  // ---------------------------------------------------------
  // Dev helper: ?bot=1 plays by itself (chases the nearest enemy and spams skills) for quick testing
  // ---------------------------------------------------------
  botEnabled() { return new URLSearchParams(window.location.search).get('bot') === '1'; }

  botVector(delta) {
    const b = this.battle;
    const pc = b.playerCenter();
    let best = null, bd = Infinity;
    for (const e of b.getEnemies()) {
      if (!e.isAlive) continue;
      const d = Math.hypot(e.centerX() - pc.x, e.centerY() - pc.y);
      if (d < bd) { bd = d; best = e; }
    }
    if (!best) return { x: 0, y: 0 };
    const dx = best.centerX() - pc.x, dy = best.centerY() - pc.y;
    const len = Math.hypot(dx, dy) || 1;
    this.botT += delta;
    if (this.botT > 400) {
      this.botT = 0;
      if (bd < 450) this.pressSkill(1); else this.pressSkill(2);
    }
    return { x: dx / len, y: dy / len };
  }

  // ---------------------------------------------------------
  // Overlays: pause and result
  // ---------------------------------------------------------
  /** any overlay (pause / result / quiz) blocks input and the pause button */
  overlayOpen() { return !!this.overlay || this.quiz.isShowing(); }
  quizShowing() { return this.quiz.isShowing(); }
  /** overlays other than the quiz (a quiz may not open on top of these) */
  otherOverlayOpen() { return !!this.overlay; }

  hideOverlay(resume) {
    if (!this.overlay) return;
    this.overlay.items.forEach((o) => o.destroy());
    this.overlay = null;
    if (resume) this.battle.closePause();
  }

  buildOverlay(kind, depth = 200) {
    const { width, height } = this.scale;
    const items = [];
    const add = (o) => { o.setDepth(depth); items.push(o); return o; };
    add(this.add.rectangle(width / 2, height / 2, width, height, 0x000000, 0.7).setInteractive());
    this.overlay = { kind, items };
    this.resetJoy();
    return { add, width, height };
  }

  showPause() {
    if (this.overlay) return;
    const { add, width, height } = this.buildOverlay('pause');
    const cx = width / 2 - 250;             // left column: buttons
    const gx = width / 2 + 20;              // right column: item guide
    add(this.add.rectangle(width / 2, height / 2, 1040, 500, COLORS.panel).setStrokeStyle(3, 0x00ADB5));
    add(this.add.text(cx, height / 2 - 195, '⏸ หยุดเกมชั่วคราว', {
      fontFamily: FONT, fontSize: '38px', color: '#FFFFFF', fontStyle: 'bold',
    }).setOrigin(0.5));
    add(makeMuteButton(this, cx + 200, height / 2 - 195, 28));
    add(makeButton(this, cx, height / 2 - 100, 440, 84, 'เล่นต่อ', 0x27AE60, () => {
      playSfx(this, 'sfx_button');
      this.hideOverlay(true);
    }, 32));
    add(makeButton(this, cx, height / 2 + 5, 440, 84, 'เริ่มด่านใหม่', 0x2980B9, () => {
      playSfx(this, 'sfx_button');
      this.battle.restart();
    }, 32));
    add(makeButton(this, cx, height / 2 + 110, 440, 84, 'ออกไปหน้าเลือกด่าน', 0xC0392B, () => {
      playSfx(this, 'sfx_button');
      this.battle.exitToStageSelect();
    }, 30));

    // item guide: one row per item type (built from ITEM_LIST so it never gets out of sync)
    add(this.add.text(gx, height / 2 - 215, 'ไอเทมในเกม', {
      fontFamily: FONT, fontSize: '32px', color: '#F39C12', fontStyle: 'bold',
    }));
    let y = height / 2 - 170;
    for (const t of ITEM_LIST) {
      const row = add(this.add.text(gx, y, wrapText(itemLabel(t), 22, 440), {
        fontFamily: FONT, fontSize: '22px', color: '#FFFFFF', lineSpacing: 2,
      }));
      y += row.height + 10;
    }
  }

  // ---------------------------------------------------------
  // Tutorial (first time on stage 1): the battle stays paused until the player taps the button
  // ---------------------------------------------------------
  showTutorialIfFirstTime() {
    const b = this.battle;
    if (b.stage !== 1 || getBool('tutorial_seen', false)) return;
    b.setPaused(true);
    b.setMove(0, 0);
    const { add, width, height } = this.buildOverlay('tutorial', 250);
    add(this.add.rectangle(width / 2, height / 2, 1120, 620, 0x0A1420, 0.98).setStrokeStyle(3, 0xF39C12));
    add(this.add.text(width / 2, height / 2 - 270, '📖 วิธีเล่น', {
      fontFamily: FONT, fontSize: '44px', color: '#F39C12', fontStyle: 'bold',
    }).setOrigin(0.5));
    const text = '🕹️ จอยสติ๊ก (มุมซ้ายล่าง): ลากเพื่อว่ายน้ำ ทิศที่ลากคือทิศที่สกิลจะพุ่งหรือยิงไป\n\n🔴🔵 ปุ่มสกิล 1 และ 2 (มุมขวาล่าง): โจมตีศัตรู แต่ละปุ่มมีคูลดาวน์สั้นๆ ชื่อและไอคอนบนปุ่มบอกว่าเป็นสกิลอะไร\n\n📊 หลอด Stack (ตรงกลางบน): ทุกครั้งที่สกิลโดนศัตรู หลอดจะเพิ่ม 1 เมื่อเต็ม 10 จะมีโจทย์ขึ้นมา\n\n❓ โจทย์ Quiz: ตอบถูกจะปลดล็อกปุ่ม ULT สกิลพิเศษที่แรงที่สุด ตอบผิดหรือหมดเวลา Stack จะรีเซ็ต\n\n❤️ HP (มุมซ้ายบน): ถ้า HP หมดจะแพ้ ฆ่าศัตรูให้หมดเพื่อชนะด่าน';
    add(this.add.text(width / 2 - 520, height / 2 - 225, wrapText(text, 27, 1040), {
      fontFamily: FONT, fontSize: '27px', color: '#FFFFFF', lineSpacing: 4,
    }));
    add(makeButton(this, width / 2, height / 2 + 255, 420, 84, 'เข้าใจแล้ว เริ่มเลย!', 0x27AE60, () => {
      playSfx(this, 'sfx_button');
      setItem('tutorial_seen', true);
      this.hideOverlay(true);
    }, 32));
  }

  showResult(win, stars) {
    if (win && this.battle.cfg.boss) { this.showBossVictory(stars); return; }
    this.hideOverlay(false);
    const { add, width, height } = this.buildOverlay('result');
    const b = this.battle;
    add(this.add.rectangle(width / 2, height / 2, 620, win ? 520 : 440, COLORS.panel)
      .setStrokeStyle(3, win ? 0x2ECC71 : 0xE74C3C));
    add(this.add.text(width / 2, height / 2 - (win ? 190 : 150), win ? '🎉 ชนะแล้ว!' : '💀 แพ้แล้ว', {
      fontFamily: FONT, fontSize: '56px', fontStyle: 'bold', color: win ? '#2ECC71' : '#E74C3C',
    }).setOrigin(0.5));
    if (win) {
      add(this.add.text(width / 2, height / 2 - 120, starsText(stars), {
        fontFamily: FONT, fontSize: '52px', color: '#F1C40F',
      }).setOrigin(0.5));
    }
    add(this.add.text(width / 2, height / 2 - (win ? 60 : 85), win ? `ผ่านด่าน ${b.stage}` : 'HP หมด ลองสู้ใหม่อีกครั้ง', {
      fontFamily: FONT, fontSize: '30px', color: '#DDDDDD',
    }).setOrigin(0.5));

    const buttons = [];
    if (win) {
      if (b.stage < MAX_STAGES) buttons.push(['ด่านถัดไป ▶', 0x27AE60, () => b.nextStage()]);   // no next stage after the boss
      buttons.push(['เล่นอีกครั้ง', 0x2980B9, () => b.restart()]);
      buttons.push(['เลือกด่าน', 0x34495E, () => b.exitToStageSelect()]);
    } else {
      buttons.push(['เล่นอีกครั้ง', 0x2980B9, () => b.restart()]);
      buttons.push(['เลือกด่าน', 0x34495E, () => b.exitToStageSelect()]);
    }
    const top = height / 2 + (win ? 20 : 0);
    buttons.forEach(([label, color, fn], i) => {
      add(makeButton(this, width / 2, top + i * 92, 440, 76, label, color, () => {
        playSfx(this, 'sfx_button');
        fn();
      }, 30));
    });
  }

  // ---------------------------------------------------------
  // Special victory screen for beating the boss (stage 5)
  // ---------------------------------------------------------
  showBossVictory(stars) {
    this.hideOverlay(false);
    const { add, width, height } = this.buildOverlay('result');
    const b = this.battle;

    // golden light + falling confetti
    add(this.add.rectangle(width / 2, height / 2, width, height, 0xFFD54F, 0.12));
    const confetti = ['✨', '🎉', '⭐', '🎊', '🐙', '🌟'];
    for (let i = 0; i < 36; i++) {
      const t = add(this.add.text(Math.random() * width, -40 - Math.random() * 300,
        confetti[i % confetti.length], { fontSize: `${24 + Math.random() * 24}px` }));
      this.tweens.add({
        targets: t, y: height + 60, x: t.x + (Math.random() * 120 - 60), angle: Math.random() * 360,
        duration: 3500 + Math.random() * 3000, delay: Math.random() * 2500, repeat: -1,
        onRepeat: () => { t.y = -40; t.x = Math.random() * width; },
      });
    }

    add(this.add.rectangle(width / 2, height / 2, 760, 560, 0x1A1530).setStrokeStyle(6, 0xFFD54F));
    const title = add(this.add.text(width / 2, height / 2 - 205, '🏆 ปราบ Kraken Boss สำเร็จ!', {
      fontFamily: FONT, fontSize: '50px', fontStyle: 'bold', color: '#FFD54F', stroke: '#000000', strokeThickness: 6,
    }).setOrigin(0.5));
    this.tweens.add({ targets: title, scale: 1.06, duration: 700, yoyo: true, repeat: -1 });

    add(this.add.text(width / 2, height / 2 - 140, 'คุณคือผู้พิทักษ์ท้องทะเล!', {
      fontFamily: FONT, fontSize: '32px', color: '#FFFFFF', fontStyle: 'bold',
    }).setOrigin(0.5));
    add(this.add.text(width / 2, height / 2 - 75, starsText(stars), {
      fontFamily: FONT, fontSize: '70px', color: '#F1C40F', stroke: '#000000', strokeThickness: 5,
    }).setOrigin(0.5));
    add(this.add.text(width / 2, height / 2 - 5, `ผ่านครบทุกด่านด้วย ${b.hero.name}`, {
      fontFamily: FONT, fontSize: '28px', color: '#DDDDDD',
    }).setOrigin(0.5));

    [['เล่นอีกครั้ง', 0x2980B9, () => b.restart()], ['เลือกด่าน', 0x34495E, () => b.exitToStageSelect()]]
      .forEach(([label, color, fn], i) => {
        add(makeButton(this, width / 2, height / 2 + 90 + i * 92, 440, 76, label, color, () => {
          playSfx(this, 'sfx_button');
          fn();
        }, 30));
      });
  }
}
