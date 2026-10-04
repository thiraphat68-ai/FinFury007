// Hero select (port of MainActivity.layoutHeroSelect): 5 hero cards on the left, details + FIGHT! on the right.
// Flow (same as Android): Stage select -> Hero select -> Battle. Back returns to stage select.
import { FONT, COLORS } from '../config.js';
import { showVideo } from '../core/background.js';
import { playSfx } from '../core/audio.js';
import { createHero, HERO_IDS } from '../heroes/heroFactory.js';
import { makeBackButton, makeButton, makeMuteButton, wrapText } from '../ui/widgets.js';

const LEFT_X = 48;              // 24 dp side padding
const LEFT_W = 392;             // 196 dp card column
const TOP = 88;                 // 44 dp title row
const BOTTOM = 704;             // 8 dp bottom margin
const GAP = 14;

export default class HeroSelectScene extends Phaser.Scene {
  constructor() { super('HeroSelectScene'); }

  init(data) {
    this.stage = (data && data.stage) || 1;
    this.selected = 1;            // default hero each time this screen opens
  }

  create() {
    const { width } = this.scale;
    showVideo(null);
    this.add.rectangle(width / 2, 360, width, 720, 0x040D14);   // #040D14 background

    this.add.text(width / 2, 44, 'SELECT YOUR HERO', {
      fontFamily: FONT, fontSize: '44px', color: '#F39C12', fontStyle: 'bold',
    }).setOrigin(0.5);
    makeBackButton(this, () => {
      playSfx(this, 'sfx_button');
      this.scene.start('StageSelectScene');
    });
    this.input.keyboard?.on('keydown-ESC', () => this.scene.start('StageSelectScene'));

    this.heroes = HERO_IDS.map((id) => createHero(id));
    makeMuteButton(this, width - 56, 44);
    this.createCards();
    this.createDetailPanel();
    this.selectHero(1);
  }

  // ---- left column: 5 cards with equal height ----
  createCards() {
    const n = this.heroes.length;
    const cardH = (BOTTOM - TOP - GAP * (n - 1)) / n;
    this.cards = [];
    this.heroes.forEach((hero, i) => {
      const y = TOP + i * (cardH + GAP) + cardH / 2;
      const c = this.add.container(LEFT_X + LEFT_W / 2, y);
      const bg = this.add.rectangle(0, 0, LEFT_W, cardH, 0x1A252C);
      const img = this.add.image(-LEFT_W / 2 + 16 + 60, 0, hero.getImageKey());
      img.setScale(Math.min(120 / img.width, 68 / img.height));
      const name = this.add.text(-LEFT_W / 2 + 16 + 136, -16, hero.name, {
        fontFamily: FONT, fontSize: '28px', color: '#FFFFFF', fontStyle: 'bold',
      }).setOrigin(0, 0.5);
      const role = this.add.text(-LEFT_W / 2 + 16 + 136, 18, hero.getRoleLabel(), {
        fontFamily: FONT, fontSize: '24px', color: '#2980B9',
      }).setOrigin(0, 0.5);
      c.add([bg, img, name, role]);
      c.setSize(LEFT_W, cardH).setInteractive({ useHandCursor: true });
      c.on('pointerup', () => {
        playSfx(this, 'sfx_button');
        this.selectHero(hero.id);
      });
      c.bg = bg;
      this.cards.push(c);
    });
  }

  // ---- right column: details ----
  createDetailPanel() {
    const x0 = LEFT_X + LEFT_W + GAP;
    const w = 1280 - 48 - x0;
    this.panelX = x0;
    this.panelW = w;
    this.add.rectangle(x0 + w / 2, (TOP + BOTTOM) / 2, w, BOTTOM - TOP, 0x1A252C);

    const px = x0 + 20;
    this.imgSelected = this.add.image(px + 100, TOP + 70, 'hero_1');
    this.txtName = this.add.text(px + 232, TOP + 8, '', {
      fontFamily: FONT, fontSize: '40px', color: '#00ADB5', fontStyle: 'bold',
    });
    this.txtSubject = this.add.text(px + 232, TOP + 54, '', {
      fontFamily: FONT, fontSize: '22px', color: '#F39C12', fontStyle: 'bold',
    });
    this.txtDesc = this.add.text(px + 232, TOP + 88, '', {
      fontFamily: FONT, fontSize: '22px', color: '#BDC3C7',
    });

    makeButton(this, x0 + w - 20 - 120, TOP + 62, 240, 104, 'FIGHT!', 0xC0392B, () => {
      playSfx(this, 'sfx_button');
      this.scene.start('BattleScene', { stage: this.stage, heroId: this.selected });
    }, 38);

    this.add.rectangle(x0 + w / 2, TOP + 214, w - 40, 2, 0xFFFFFF, 0.2);

    // three skill rows: Skill 1, Skill 2, Ultimate
    const rowH = (BOTTOM - TOP - 224) / 3;
    this.skillRows = [];
    const chips = [['Skill 1', '#00ADB5'], ['Skill 2', '#00ADB5'], ['Ultimate Skill', '#F5B041']];
    chips.forEach(([label, color], i) => {
      const y = TOP + 224 + i * rowH;
      const chipBg = this.add.rectangle(px, y + 18, 10, 34, 0x000000, 0.35).setOrigin(0, 0.5);
      const chip = this.add.text(px + 12, y + 18, label, {
        fontFamily: FONT, fontSize: '22px', color, fontStyle: 'bold',
      }).setOrigin(0, 0.5);
      chipBg.setSize(chip.width + 24, 34);
      const name = this.add.text(px + chip.width + 36, y + 18, '', {
        fontFamily: FONT, fontSize: '28px', color: '#FFFFFF',
      }).setOrigin(0, 0.5);
      const desc = this.add.text(px, y + 42, '', {
        fontFamily: FONT, fontSize: '24px', color: '#ECF0F1',
      });
      this.skillRows.push({ name, desc });
    });
  }

  selectHero(id) {
    this.selected = id;
    this.cards.forEach((c, i) => c.bg.setFillStyle(this.heroes[i].id === id ? COLORS.teal : 0x1A252C));
    const hero = this.heroes.find((h) => h.id === id);

    this.imgSelected.setTexture(hero.getImageKey());
    this.imgSelected.setScale(Math.min(200 / this.imgSelected.width, 116 / this.imgSelected.height));
    this.txtName.setText(hero.name);
    const textW = this.panelW - 232 - 20 - 240 - 16;     // between the image and the FIGHT button
    this.txtSubject.setText(wrapText(`วิชา: ${hero.getSubjectLabel()}`, 22, textW, 'bold'));
    this.txtDesc.y = this.txtSubject.y + this.txtSubject.height + 6;
    this.txtDesc.setText(wrapText(hero.getProfileDescription(), 22, textW));

    const rows = [
      [`${hero.getSkill1Icon()} ${hero.getSkill1Name()}`, hero.getSkill1Description()],
      [`${hero.getSkill2Icon()} ${hero.getSkill2Name()}`, hero.getSkill2Description()],
      [`${hero.getUltimateIcon()} ${hero.getUltimateName()}`, hero.getUltimateDescription()],
    ];
    rows.forEach(([name, desc], i) => {
      this.skillRows[i].name.setText(name);
      this.skillRows[i].desc.setText(wrapText(desc, 24, this.panelW - 40));
    });
  }
}
