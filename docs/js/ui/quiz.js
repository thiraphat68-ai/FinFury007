// Port of QuizManager.java: the quiz overlay that appears when the stack gauge is full.
// - 3-2-1 countdown (1 s each, question hidden), then the question with 4 big answer buttons and a 30 s timer
//   (tick sound each second of the last 10 s).
// - Questions come from a shuffled deck: no repeats until all 20 were used (and the first of a new deck != last of the old one).
// - After an answer / timeout the correct option is shown in green (a wrong pick in red) for 2 s, then the listener is called.
// The quiz runs in real time (driven by the UI scene's update), because the battle is paused while it is open.
import { FONT } from '../config.js';
import { playSfx } from '../core/audio.js';
import { wrapText } from './widgets.js';

const QUIZ_TIME_MS = 30000;
const FEEDBACK_MS = 2000;
const COUNTDOWN_FROM = 3;
const COUNTDOWN_STEP_MS = 1000;

const COLOR_OPTION = 0x2C3E50;
const COLOR_CORRECT = 0x27AE60;
const COLOR_WRONG = 0xC0392B;

export default class Quiz {
  /**
   * @param scene   the scene to draw into (BattleUIScene)
   * @param title   subject name, shown as "<title> Quiz"
   * @param bank    [{ q, options, answer }]
   * @param listener { onQuizShown(), onCorrect(), onWrong(), onTimeout() }
   * @param gate    () => boolean — may the quiz open right now?
   */
  constructor(scene, title, bank, listener, gate) {
    this.scene = scene;
    this.title = title;
    this.bank = bank;
    this.listener = listener;
    this.gate = gate;
    this.deck = [];
    this.lastIndex = -1;
    this.state = 'closed';        // closed | countdown | question | feedback
    this.items = [];
    this.timeLeft = 0;
    this.stateMs = 0;
    this.lastTickSecond = -1;
    this.current = null;
  }

  isShowing() { return this.state !== 'closed'; }

  /** Shuffled deck without repeats (QuizManager.nextQuestionIndex). */
  nextQuestionIndex() {
    if (this.deck.length === 0) {
      for (let i = 0; i < this.bank.length; i++) this.deck.push(i);
      for (let i = this.deck.length - 1; i > 0; i--) {         // Fisher-Yates
        const j = Math.floor(Math.random() * (i + 1));
        [this.deck[i], this.deck[j]] = [this.deck[j], this.deck[i]];
      }
      const top = this.deck.length - 1;
      if (this.deck.length > 1 && this.deck[top] === this.lastIndex) {
        [this.deck[top], this.deck[0]] = [this.deck[0], this.deck[top]];
      }
    }
    this.lastIndex = this.deck.pop();
    return this.lastIndex;
  }

  show() {
    if (this.bank.length === 0 || this.isShowing()) return;
    if (this.gate && !this.gate()) return;      // stage over, or another overlay is open

    this.listener.onQuizShown();
    this.current = this.bank[this.nextQuestionIndex()];
    this.build();
    this.state = 'countdown';
    this.stateMs = 0;
    this.countdownNumber = COUNTDOWN_FROM;
    this.showCountdown(this.countdownNumber);
  }

  // ---------------------------------------------------------
  // layout (1280 x 720, big touch targets)
  // ---------------------------------------------------------
  build() {
    const s = this.scene;
    const W = 1280;
    const DEPTH = 300;
    const add = (o) => { o.setDepth(DEPTH); this.items.push(o); return o; };

    // dim background; also swallows taps so nothing behind the quiz is touched
    add(s.add.rectangle(W / 2, 360, W, 720, 0x0A1420, 0.94).setInteractive());

    this.content = [];
    const addContent = (o) => { this.content.push(add(o)); return o; };

    addContent(s.add.text(56, 30, `${this.title} Quiz`, {
      fontFamily: FONT, fontSize: '32px', color: '#00ADB5', fontStyle: 'bold',
    }));
    this.txtTime = addContent(s.add.text(W - 56, 30, '', {
      fontFamily: FONT, fontSize: '32px', color: '#F39C12', fontStyle: 'bold',
    }).setOrigin(1, 0));
    this.barG = addContent(s.add.graphics());

    // question (+ formula line, shown separately in yellow)
    const full = this.current.q;
    const cut = full.indexOf('\nสูตร:');
    const questionText = cut < 0 ? full : full.substring(0, cut);
    const formulaText = cut < 0 ? '' : full.substring(cut + 1);
    const textW = W - 112;

    const q = addContent(s.add.text(56, 96, wrapText(questionText, 38, textW, 'bold'), {
      fontFamily: FONT, fontSize: '38px', color: '#FFFFFF', fontStyle: 'bold', lineSpacing: 6,
    }));
    if (formulaText) {
      addContent(s.add.text(56, q.y + q.height + 14, wrapText(formulaText, 30, textW), {
        fontFamily: FONT, fontSize: '30px', color: '#F1C40F', lineSpacing: 4,
      }));
    }

    this.txtFeedback = add(s.add.text(W / 2, 380, '', {
      fontFamily: FONT, fontSize: '34px', fontStyle: 'bold', color: '#FFFFFF',
    }).setOrigin(0.5).setVisible(false));

    // 2 x 2 answer buttons (each 576 x 108)
    this.buttons = [];
    const bw = 576, bh = 108, gx = 16, gy = 16;
    const left = 56 + bw / 2;
    const top = 436 + bh / 2;
    for (let i = 0; i < 4; i++) {
      const col = i % 2, row = Math.floor(i / 2);
      const x = left + col * (bw + gx);
      const y = top + row * (bh + gy);
      const c = addContent(s.add.container(x, y));
      const bg = s.add.rectangle(0, 0, bw, bh, COLOR_OPTION).setStrokeStyle(3, 0xFFFFFF, 0.25);
      const label = `${String.fromCharCode(65 + i)}.  ${this.current.options[i]}`;
      const txt = s.add.text(0, 0, wrapText(label, 32, bw - 48, 'bold'), {
        fontFamily: FONT, fontSize: '32px', color: '#FFFFFF', fontStyle: 'bold', align: 'center',
      }).setOrigin(0.5);
      c.add([bg, txt]);
      c.setSize(bw, bh).setInteractive({ useHandCursor: true });
      c.on('pointerdown', () => bg.setFillStyle(0x3E5871));
      c.on('pointerout', () => { if (this.state === 'question') bg.setFillStyle(COLOR_OPTION); });
      c.on('pointerup', () => this.resolve(i));
      c.bg = bg;
      this.buttons.push(c);
    }

    // everything except the countdown stays hidden until the 3-2-1 is over
    this.content.forEach((o) => o.setVisible(false));

    this.txtCountdown = add(s.add.text(W / 2, 360, '', {
      fontFamily: FONT, fontSize: '220px', color: '#F39C12', fontStyle: 'bold', stroke: '#000000', strokeThickness: 12,
    }).setOrigin(0.5).setVisible(false));

    this.updateBar(QUIZ_TIME_MS);
    this.updateTimeText(QUIZ_TIME_MS);
  }

  showCountdown(n) {
    this.txtCountdown.setText(String(n)).setVisible(true).setScale(1.6).setAlpha(1);
    this.scene.tweens.add({ targets: this.txtCountdown, scale: 1, alpha: 0.7, duration: COUNTDOWN_STEP_MS });
  }

  // ---------------------------------------------------------
  // per-frame (real time)
  // ---------------------------------------------------------
  update(deltaMs) {
    if (this.state === 'closed') return;
    this.stateMs += deltaMs;

    if (this.state === 'countdown') {
      if (this.stateMs >= COUNTDOWN_STEP_MS) {
        this.stateMs -= COUNTDOWN_STEP_MS;
        this.countdownNumber--;
        if (this.countdownNumber > 0) {
          this.showCountdown(this.countdownNumber);
        } else {
          this.txtCountdown.setVisible(false);
          this.content.forEach((o) => o.setVisible(true));
          this.state = 'question';
          this.stateMs = 0;
          this.timeLeft = QUIZ_TIME_MS;
          this.lastTickSecond = -1;
        }
      }
    } else if (this.state === 'question') {
      this.timeLeft = Math.max(0, QUIZ_TIME_MS - this.stateMs);
      this.updateBar(this.timeLeft);
      this.updateTimeText(this.timeLeft);
      // tick sound once per second during the last 10 seconds
      const secondsLeft = Math.floor((this.timeLeft + 999) / 1000);
      if (secondsLeft <= 10 && secondsLeft !== this.lastTickSecond) {
        this.lastTickSecond = secondsLeft;
        playSfx(this.scene, 'sfx_quiz_tick');
      }
      if (this.timeLeft <= 0) this.resolve(-1);
    } else if (this.state === 'feedback') {
      if (this.stateMs >= FEEDBACK_MS) this.finishFeedback();
    }
  }

  updateBar(msLeft) {
    const w = 1280 - 112;
    this.barG.clear();
    this.barG.fillStyle(0x333333, 1);
    this.barG.fillRect(56, 70, w, 16);
    this.barG.fillStyle(0xF39C12, 1);
    this.barG.fillRect(56, 70, w * (msLeft / QUIZ_TIME_MS), 16);
  }

  updateTimeText(msLeft) {
    const total = Math.floor((msLeft + 999) / 1000);
    const mm = String(Math.floor(total / 60)).padStart(2, '0');
    const ss = String(total % 60).padStart(2, '0');
    this.txtTime.setText(`${mm}:${ss}`);
  }

  /** choice = tapped option, -1 = time ran out. */
  resolve(choice) {
    if (this.state !== 'question') return;
    this.choice = choice;
    this.state = 'feedback';
    this.stateMs = 0;

    const correct = choice === this.current.answer;
    const timedOut = choice < 0;
    this.buttons[this.current.answer].bg.setFillStyle(COLOR_CORRECT);
    if (!correct && !timedOut) this.buttons[choice].bg.setFillStyle(COLOR_WRONG);

    this.txtFeedback.setVisible(true);
    if (correct) {
      this.txtFeedback.setColor('#27AE60').setText('✅ ถูกต้อง!');
    } else if (timedOut) {
      this.txtFeedback.setColor('#E74C3C').setText('⏰ หมดเวลา! คำตอบที่ถูกคือข้อสีเขียว');
    } else {
      this.txtFeedback.setColor('#E74C3C').setText('❌ ผิด! คำตอบที่ถูกคือข้อสีเขียว');
    }
  }

  finishFeedback() {
    const correct = this.choice === this.current.answer;
    const timedOut = this.choice < 0;
    this.close();
    if (correct) this.listener.onCorrect();
    else if (timedOut) this.listener.onTimeout();
    else this.listener.onWrong();
  }

  close() {
    this.items.forEach((o) => o.destroy());
    this.items = [];
    this.content = [];
    this.buttons = [];
    this.state = 'closed';
    this.current = null;
  }

  /** Stage ended while the quiz was open: close at once WITHOUT calling any listener method. */
  dismiss() {
    if (this.state === 'closed') return;
    this.close();
  }
}
