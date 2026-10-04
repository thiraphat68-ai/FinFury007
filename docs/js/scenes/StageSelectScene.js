// Stage select (port of the stage map: 5 whirlpool icons, best stars under each, lock system).
import { FONT, STAGE_POSITIONS, MAX_STAGES } from '../config.js';
import { showVideo } from '../core/background.js';
import { playSfx } from '../core/audio.js';
import { isUnlocked, getStars, starsText } from '../core/stageProgress.js';
import { makeBackButton, makeMuteButton, showToast } from '../ui/widgets.js';

const ICON_HEIGHT = 190;          // displayed height of a stage icon (logical px)
const LOCKED_ALPHA = 0.6;         // same dimming as Android
const LOCKED_MESSAGE = 'Clear the previous stage first';

export default class StageSelectScene extends Phaser.Scene {
  constructor() { super('StageSelectScene'); }

  create() {
    const { width, height } = this.scale;
    showVideo('level');

    this.add.text(width / 2, 64, 'SELECT STAGE', {
      fontFamily: FONT, fontSize: '56px', color: '#FFFFFF', fontStyle: 'bold',
      stroke: '#000000', strokeThickness: 6,
    }).setOrigin(0.5);

    makeBackButton(this, () => {
      playSfx(this, 'sfx_button');
      this.scene.start('MenuScene');
    });

    for (let stage = 1; stage <= MAX_STAGES; stage++) this.createStageButton(stage);
    makeMuteButton(this, width - 56, 56);

    // keyboard / system back (desktop): Esc goes back like the back button
    this.input.keyboard?.on('keydown-ESC', () => this.scene.start('MenuScene'));
  }

  createStageButton(stage) {
    const { width, height } = this.scale;
    const pos = STAGE_POSITIONS[stage - 1];
    const x = width * pos.x;
    const y = height * pos.y;
    const unlocked = isUnlocked(stage);

    // stage 1 green, 2-4 blue, 5 boss icon; locked stages all show the lock
    const openKey = stage === 1 ? 'whirlpool_green' : stage === MAX_STAGES ? 'ic_boss' : 'whirlpool_blue';
    const icon = this.add.image(x, y, unlocked ? openKey : 'whirlpool_lock');
    icon.setScale(ICON_HEIGHT / icon.height);
    icon.setAlpha(unlocked ? 1 : LOCKED_ALPHA);
    if (!unlocked) icon.setTint(0xBBBBBB);   // greyed out

    // stage number on top of normal icons (boss/locked icons are self-explanatory)
    if (unlocked && stage !== MAX_STAGES) {
      this.add.text(x, y, String(stage), {
        fontFamily: FONT, fontSize: '64px', color: '#FFFFFF', fontStyle: 'bold',
        stroke: '#000000', strokeThickness: 6,
      }).setOrigin(0.5);
    }

    // best stars under the icon
    this.add.text(x, y + ICON_HEIGHT / 2 + 20, starsText(getStars(stage)), {
      fontFamily: FONT, fontSize: '34px', color: '#F1C40F',
      stroke: '#000000', strokeThickness: 4,
    }).setOrigin(0.5);

    icon.setInteractive({ useHandCursor: unlocked });
    icon.on('pointerdown', () => icon.setScale(icon.scaleX * 0.94));
    icon.on('pointerout', () => icon.setScale(ICON_HEIGHT / icon.height));
    icon.on('pointerup', () => {
      icon.setScale(ICON_HEIGHT / icon.height);
      if (!isUnlocked(stage)) {
        showToast(this, LOCKED_MESSAGE);       // locked: message only, no sound, nothing starts
        return;
      }
      playSfx(this, 'sfx_button');
      this.scene.start('HeroSelectScene', { stage });
    });
  }
}
