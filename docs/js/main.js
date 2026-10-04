import { GAME_WIDTH, GAME_HEIGHT } from './config.js';
import BootScene from './scenes/BootScene.js';
import MenuScene from './scenes/MenuScene.js';
import StageSelectScene from './scenes/StageSelectScene.js';
import HeroSelectScene from './scenes/HeroSelectScene.js';
import BattleScene from './scenes/BattleScene.js';
import BattleUIScene from './scenes/BattleUIScene.js';
import { installAudioUnlock } from './core/audio.js';
import { registerServiceWorker } from './core/pwa.js';

// ---- iOS Safari: stop pinch zoom, double-tap zoom and page scrolling ----
['gesturestart', 'gesturechange', 'gestureend'].forEach((type) =>
  document.addEventListener(type, (e) => e.preventDefault(), { passive: false }));
document.addEventListener('touchmove', (e) => e.preventDefault(), { passive: false });
let lastTouchEnd = 0;
document.addEventListener('touchend', (e) => {
  const now = Date.now();
  if (now - lastTouchEnd <= 350) e.preventDefault();   // double-tap zoom
  lastTouchEnd = now;
}, { passive: false });
document.addEventListener('contextmenu', (e) => e.preventDefault());

const config = {
  type: Phaser.AUTO,
  parent: 'game',
  transparent: true,                 // lets the HTML background videos show through
  width: GAME_WIDTH,
  height: GAME_HEIGHT,
  scale: {
    mode: Phaser.Scale.FIT,          // keep 16:9, scale to the biggest size that fits
    autoCenter: Phaser.Scale.CENTER_BOTH,
  },
  input: { activePointers: 3 },      // multi-touch (joystick + skill buttons later)
  render: { antialias: true, roundPixels: false, powerPreference: 'high-performance' },
  fps: { target: 60, limit: 60 },    // ProMotion iPhones run at 120 Hz: cap at 60 (all game logic is delta-time based)
  scene: [BootScene, MenuScene, StageSelectScene, HeroSelectScene, BattleScene, BattleUIScene],
};

window.game = new Phaser.Game(config);
installAudioUnlock(window.game);       // iOS: audio starts on the first touch
registerServiceWorker(window.game);    // offline cache + updates
