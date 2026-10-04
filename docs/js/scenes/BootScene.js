// Loads every asset with a loading bar, then starts the menu.
import { FONT } from '../config.js';

export default class BootScene extends Phaser.Scene {
  constructor() { super('BootScene'); }

  preload() {
    const { width, height } = this.scale;
    const barW = Math.min(640, width * 0.6);
    const barH = 24;
    const x = (width - barW) / 2;
    const y = height / 2;

    this.add.text(width / 2, y - 70, 'FIN & FURY', {
      fontFamily: FONT, fontSize: '56px', color: '#FFFFFF', fontStyle: 'bold italic',
    }).setOrigin(0.5);
    const label = this.add.text(width / 2, y + 50, 'Loading… 0%', {
      fontFamily: FONT, fontSize: '24px', color: '#CCCCCC',
    }).setOrigin(0.5);

    const frame = this.add.graphics();
    frame.lineStyle(3, 0xFFFFFF, 0.9);
    frame.strokeRoundedRect(x - 4, y - 4, barW + 8, barH + 8, 10);
    const fill = this.add.graphics();

    this.load.on('progress', (p) => {
      fill.clear();
      fill.fillStyle(0x00ADB5, 1);
      fill.fillRoundedRect(x, y, barW * p, barH, 6);
      label.setText(`Loading… ${Math.round(p * 100)}%`);
    });
    // a missing/unsupported file must never block the game from starting
    this.load.on('loaderror', (file) => console.warn('Could not load', file && file.key));

    // images
    this.load.image('whirlpool_green', 'assets/img/ic_whirlpool_green.png');
    this.load.image('whirlpool_blue', 'assets/img/ic_whirlpool_blue.png');
    this.load.image('whirlpool_lock', 'assets/img/ic_whirlpool_lock.png');
    this.load.image('ic_boss', 'assets/img/ic_boss.png');
    this.load.image('bg_stage_1', 'assets/img/bg_stage_1.png');
    this.load.image('bg_stage_2', 'assets/img/bg_stage_2.png');
    this.load.image('bg_stage_3', 'assets/img/bg_stage_3.jpg');
    this.load.image('bg_stage_4', 'assets/img/bg_stage_4.png');
    this.load.image('bg_stage_5', 'assets/img/bg_stage_5.jpg');

    // sounds (the Android project has 15 sound effects and no music files)
    for (const name of ['sfx_button', 'sfx_skill1', 'sfx_skill2', 'sfx_ultimate', 'sfx_hit_enemy', 'sfx_player_hurt',
      'sfx_enemy_die', 'sfx_enemy_warn', 'sfx_quiz_show', 'sfx_quiz_correct', 'sfx_quiz_wrong', 'sfx_quiz_tick',
      'sfx_ult_ready', 'sfx_win', 'sfx_lose']) {
      // .ogg first (small); Safari before 18.4 can't play Ogg, so Phaser falls back to the .wav copy
      this.load.audio(name, [`assets/audio/${name}.ogg`, `assets/audio/${name}.wav`]);
    }

    // hero
    for (let i = 1; i <= 5; i++) this.load.image(`hero_${i}`, `assets/img/hero_${i}.png`);

    // boss: 47-frame idle animation (octo_idle_00..46, 256x170)
    for (let i = 0; i < 47; i++) {
      const n = String(i).padStart(2, '0');
      this.load.image(`boss_${n}`, `assets/img/boss/octo_idle_${n}.png`);
    }
  }

  create() {
    // dev shortcut: ?scene=HeroSelectScene / BattleScene&stage=2&hero=3 jumps straight to that scene
    const wanted = new URLSearchParams(window.location.search).get('scene');
    const q = new URLSearchParams(window.location.search);
    this.scene.start(wanted && this.scene.manager.keys[wanted] ? wanted : 'MenuScene',
      { stage: parseInt(q.get('stage'), 10) || 1, heroId: parseInt(q.get('hero'), 10) || 1 });
  }
}
