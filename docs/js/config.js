// Global constants. Logical game resolution is fixed (16:9); Phaser scales it to fit any screen.
export const GAME_WIDTH = 1280;
export const GAME_HEIGHT = 720;

export const MAX_STAGES = 5;

export const FONT = 'system-ui, -apple-system, "Segoe UI", Tahoma, "Noto Sans Thai", sans-serif';

export const COLORS = {
  teal: 0x00ADB5,        // START / selected (same as Android btnStart)
  slate: 0x34495E,       // settings button
  danger: 0xC0392B,
  dark: 0x0A0E1A,
  panel: 0x1A252C,
  gold: '#F1C40F',
};

// Stage icon positions on the stage-select screen: centre as a fraction of the screen
// (same biases as the Android layout: x 0.15..0.85, y 0.2..0.8)
export const STAGE_POSITIONS = [
  { x: 0.15, y: 0.20 },
  { x: 0.32, y: 0.35 },
  { x: 0.50, y: 0.50 },
  { x: 0.68, y: 0.65 },
  { x: 0.85, y: 0.80 },
];

// ---- Battle world (reference Android screen) ----
// The Android battle works in raw device pixels. The web battle keeps those numbers unchanged by using a
// "world" of 1920x1080 reference px (a 1080p phone at density 2.75 = ~698x393 dp) that the battle camera
// shows at zoom 2/3 inside the 1280x720 canvas. HUD / controls are drawn in 1280x720 canvas units
// (1 Android dp = 2 canvas units, because the reference screen is 360 dp tall -> 720 units).
export const WORLD_W = 1920;
export const WORLD_H = 1080;
export const DENSITY = 2.75;                 // Android px per dp on the reference screen
export const dp = (v) => v * DENSITY;         // Android dp -> world px
export const MAX_SPEED = 700;                 // BattleActivity.MAX_SPEED (px/s)
export const UI_PER_DP = 2;                   // canvas units per dp for HUD sizes

// Boss stage: the web boss stage has the boss as its ONLY enemy. The Android version also spawns 5 minions
// (BossMinion) during life 1 that hurt the boss when they die; set this to true to bring them back.
export const BOSS_MINIONS = false;
