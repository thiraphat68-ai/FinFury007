// Small reusable UI pieces for Phaser scenes.
import { FONT } from '../config.js';
import { isSoundEnabled, setSoundEnabled, playSfx } from '../core/audio.js';

/** Rounded rectangle button (container) with a label and press feedback. */
export function makeButton(scene, x, y, width, height, label, color, onClick, fontSize = 28) {
  const container = scene.add.container(x, y);
  const bg = scene.add.graphics();
  bg.fillStyle(color, 1);
  bg.fillRoundedRect(-width / 2, -height / 2, width, height, 14);
  const text = scene.add.text(0, 0, label, {
    fontFamily: FONT, fontSize: `${fontSize}px`, color: '#FFFFFF', fontStyle: 'bold',
  }).setOrigin(0.5);
  container.add([bg, text]);
  container.setSize(width, height);
  container.setInteractive({ useHandCursor: true });
  container.on('pointerdown', () => container.setScale(0.95));
  container.on('pointerout', () => container.setScale(1));
  container.on('pointerup', () => {
    container.setScale(1);
    onClick();
  });
  container.setLabel = (t) => text.setText(t);
  return container;
}

/** Temporary message in the lower part of the screen (e.g. "Clear the previous stage first"). */
export function showToast(scene, message) {
  const { width, height } = scene.scale;
  if (scene._toast) {
    scene._toast.destroy();
    scene._toast = null;
  }
  const text = scene.add.text(width / 2, height - 80, message, {
    fontFamily: FONT, fontSize: '30px', color: '#FFFFFF', fontStyle: 'bold',
    backgroundColor: '#CC000000', padding: { x: 24, y: 12 }, align: 'center',
  }).setOrigin(0.5).setDepth(1000);
  scene._toast = text;
  scene.tweens.add({
    targets: text, alpha: 0, delay: 1400, duration: 400,
    onComplete: () => { if (scene._toast === text) scene._toast = null; text.destroy(); },
  });
}

/** Round back button for the top-left corner (same idea as the Android back button). */
export function makeBackButton(scene, onClick) {
  const x = 56, y = 56;
  const container = scene.add.container(x, y);
  const bg = scene.add.circle(0, 0, 32, 0x000000, 0.5);
  const arrow = scene.add.text(0, -2, '↩', {
    fontFamily: FONT, fontSize: '40px', color: '#FFFFFF', fontStyle: 'bold',
  }).setOrigin(0.5);
  container.add([bg, arrow]);
  container.setSize(72, 72);
  container.setInteractive({ useHandCursor: true });
  container.on('pointerdown', () => container.setScale(0.92));
  container.on('pointerout', () => container.setScale(1));
  container.on('pointerup', () => {
    container.setScale(1);
    onClick();
  });
  return container;
}

// ---- text wrapping that also works for Thai (no spaces between words) ----
let measureCtx = null;
const segmenter = (typeof Intl !== 'undefined' && Intl.Segmenter) ? new Intl.Segmenter('th', { granularity: 'word' }) : null;

/**
 * Insert line breaks so that no line is wider than maxWidth px (measured with the given font).
 * Breaks at word boundaries (Intl.Segmenter) and falls back to single characters where the browser has no segmenter.
 */
export function wrapText(text, fontSize, maxWidth, fontWeight = 'normal') {
  if (!measureCtx) measureCtx = document.createElement('canvas').getContext('2d');
  measureCtx.font = `${fontWeight} ${fontSize}px ${FONT}`;
  const lines = [];
  for (const paragraph of String(text).split('\n')) {
    const pieces = segmenter
      ? Array.from(segmenter.segment(paragraph), (s) => s.segment)
      : Array.from(paragraph);
    let line = '';
    for (const piece of pieces) {
      const test = line + piece;
      if (line && measureCtx.measureText(test.trimEnd()).width > maxWidth) {
        lines.push(line.trimEnd());
        line = piece.trimStart();
      } else {
        line = test;
      }
    }
    lines.push(line.trimEnd());
  }
  return lines.join('\n');
}

// ---- mute button (speaker icon) ----

/** Round speaker button that toggles all sound (saved). Works in every scene. */
export function makeMuteButton(scene, x, y, radius = 32) {
  const c = scene.add.container(x, y);
  const bg = scene.add.circle(0, 0, radius, 0x000000, 0.5);
  const icon = scene.add.text(0, 0, '', { fontFamily: FONT, fontSize: `${Math.round(radius * 1.1)}px` }).setOrigin(0.5);
  c.add([bg, icon]);
  const refresh = () => icon.setText(isSoundEnabled() ? '🔊' : '🔇');
  refresh();
  c.setSize(radius * 2.2, radius * 2.2).setInteractive({ useHandCursor: true });
  c.on('pointerdown', () => c.setScale(0.92));
  c.on('pointerout', () => c.setScale(1));
  c.on('pointerup', () => {
    c.setScale(1);
    setSoundEnabled(!isSoundEnabled());
    refresh();
    playSfx(scene, 'sfx_button');          // (silent when just muted)
  });
  c.refresh = refresh;
  return c;
}

/**
 * "Reset progress?" confirmation (stars + unlocked stages are erased, sound setting is kept).
 * Draws a modal dialog in the scene; onDone() is called after the reset.
 */
export function confirmResetDialog(scene, resetFn, onDone) {
  const { width, height } = scene.scale;
  const items = [];
  const add = (o) => { o.setDepth(900); items.push(o); return o; };
  add(scene.add.rectangle(width / 2, height / 2, width, height, 0x000000, 0.7).setInteractive());
  add(scene.add.rectangle(width / 2, height / 2, 780, 340, 0x1A252C).setStrokeStyle(3, 0xC0392B));
  add(scene.add.text(width / 2, height / 2 - 108, 'รีเซ็ตความคืบหน้า?', {
    fontFamily: FONT, fontSize: '40px', color: '#FFFFFF', fontStyle: 'bold',
  }).setOrigin(0.5));
  add(scene.add.text(width / 2, height / 2 - 28, 'ดาทั้งหมดจะหาย และกลับไปเหลือแค่ด่าน 1\n(ค่าตั้งเสียงไม่เปลี่ยน) กู้คืนไม่ได้', {
    fontFamily: FONT, fontSize: '27px', color: '#DDDDDD', align: 'center',
  }).setOrigin(0.5));
  const close = () => items.forEach((o) => o.destroy());
  add(makeButton(scene, width / 2 - 170, height / 2 + 100, 280, 80, 'ยกเลิก', 0x34495E, () => {
    playSfx(scene, 'sfx_button');
    close();
  }, 30));
  add(makeButton(scene, width / 2 + 170, height / 2 + 100, 280, 80, 'รีเซ็ต', 0xC0392B, () => {
    resetFn();
    close();
    showToast(scene, 'รีเซ็ตความคืบหน้าแล้ว');
    if (onDone) onDone();
  }, 30));
}
