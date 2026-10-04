// Main menu (port of MainActivity.layoutMainMenu): title, START GAME, settings, plus mute and reset progress.
import { COLORS, FONT } from '../config.js';
import { showVideo } from '../core/background.js';
import { playSfx, isSoundEnabled, setSoundEnabled } from '../core/audio.js';
import { resetProgress } from '../core/stageProgress.js';
import { makeButton, makeMuteButton, confirmResetDialog } from '../ui/widgets.js';

export default class MenuScene extends Phaser.Scene {
  constructor() { super('MenuScene'); }

  create() {
    const { width, height } = this.scale;
    showVideo('menu');

    this.add.text(width / 2, height * 0.27, 'FIN & FURY', {
      fontFamily: FONT, fontSize: '110px', color: '#FFFFFF', fontStyle: 'bold italic',
      stroke: '#000000', strokeThickness: 8,
    }).setOrigin(0.5);

    makeButton(this, width / 2, height * 0.52, 440, 100, 'START GAME', COLORS.teal, () => {
      playSfx(this, 'sfx_button');
      this.scene.start('StageSelectScene');
    }, 38);

    makeButton(this, width / 2, height * 0.52 + 120, 440, 80, '⚙ ตั้งค่า', COLORS.slate, () => {
      playSfx(this, 'sfx_button');
      this.openSettings();
    }, 30);

    // reset progress is available straight from the menu (with a confirmation dialog)
    makeButton(this, width / 2, height * 0.52 + 220, 440, 70, '🗑 รีเซ็ตความคืบหน้า', 0x7B2D26, () => {
      playSfx(this, 'sfx_button');
      confirmResetDialog(this, resetProgress);
    }, 26);

    makeMuteButton(this, width - 56, 56);
  }

  // ---- Settings panel (port of SettingsActivity: sound switch + reset progress) ----
  openSettings() {
    if (this.settingsPanel) return;
    const { width, height } = this.scale;
    const items = [];
    const track = (o) => { items.push(o); return o; };

    const dim = track(this.add.rectangle(width / 2, height / 2, width, height, 0x000000, 0.7)
      .setInteractive().setDepth(500));
    const panel = track(this.add.rectangle(width / 2, height / 2, 700, 460, COLORS.panel)
      .setStrokeStyle(3, 0x00ADB5).setDepth(501));
    track(this.add.text(width / 2, height / 2 - 170, '⚙ ตั้งค่า', {
      fontFamily: FONT, fontSize: '44px', color: '#FFFFFF', fontStyle: 'bold',
    }).setOrigin(0.5).setDepth(502));

    const soundBtn = track(makeButton(this, width / 2, height / 2 - 60, 520, 84,
      this.soundLabel(), COLORS.slate, () => {
        setSoundEnabled(!isSoundEnabled());
        soundBtn.setLabel(this.soundLabel());
        playSfx(this, 'sfx_button');
      }, 30).setDepth(502));

    track(makeButton(this, width / 2, height / 2 + 50, 520, 84, 'รีเซ็ตความคืบหน้า', COLORS.danger, () => {
      confirmResetDialog(this, resetProgress);
    }, 30).setDepth(502));

    const close = () => {
      items.forEach((o) => o.destroy());
      this.settingsPanel = null;
    };
    track(makeButton(this, width / 2, height / 2 + 160, 260, 76, 'กลับ', COLORS.teal, () => {
      playSfx(this, 'sfx_button');
      close();
    }, 30).setDepth(502));

    this.settingsPanel = { close, dim, panel };
  }

  soundLabel() {
    return isSoundEnabled() ? '🔊 เสียง: เปิด' : '🔇 เสียง: ปิด';
  }
}
