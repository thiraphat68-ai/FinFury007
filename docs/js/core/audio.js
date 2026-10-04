// Sound manager (port of SoundManager). 15 sound effects; the Android project ships NO music files
// (SoundManager.playMusic("bgm_menu"/"bgm_battle") exists but res/raw has no such files), so there is no music here either.
// - Global enable flag ("sound_enabled", default on) = the mute button; also mutes sounds already playing.
// - iOS blocks audio until a user gesture: installAudioUnlock() resumes the AudioContext on the first touch / click / key.
// - Never throws if a sound is missing or can't be decoded.
import { getBool, setItem } from './storage.js';

const KEY_SOUND = 'sound_enabled';
const SFX_VOLUME = 0.9;
const MIN_REPEAT_MS = 50;

let soundEnabled = getBool(KEY_SOUND, true);
const lastPlayed = {};

export function isSoundEnabled() { return soundEnabled; }

/** Apply the mute flag to the running game (mutes sounds that are already playing). */
function applyMute() {
  try {
    if (window.game && window.game.sound) window.game.sound.mute = !soundEnabled;
  } catch (e) { /* ignore */ }
}

export function setSoundEnabled(enabled) {
  soundEnabled = !!enabled;
  setItem(KEY_SOUND, soundEnabled);
  applyMute();
}

/** @param scene any Phaser scene (used for scene.sound) */
export function playSfx(scene, key) {
  if (!soundEnabled || !scene || !scene.sound) return;
  try {
    if (!scene.cache.audio.exists(key)) return;
    const now = performance.now();
    if (lastPlayed[key] !== undefined && now - lastPlayed[key] < MIN_REPEAT_MS) return;
    lastPlayed[key] = now;
    scene.sound.play(key, { volume: SFX_VOLUME });
  } catch (e) { /* ignore: sound must never break the game */ }
}

/**
 * iOS Safari (and Chrome) keep the AudioContext suspended until a user gesture. On the first touch / click / key press we resume it
 * and play a one-sample silent buffer (the classic iOS unlock). Also resumes when the app returns to the foreground.
 */
export function installAudioUnlock(game) {
  applyMute();
  const unlock = () => {
    try {
      const ctx = game.sound && game.sound.context;
      if (!ctx) return;
      if (ctx.state !== 'running') ctx.resume().catch(() => {});
      const buffer = ctx.createBuffer(1, 1, 22050);
      const src = ctx.createBufferSource();
      src.buffer = buffer;
      src.connect(ctx.destination);
      src.start(0);
      if (ctx.state === 'running') remove();
    } catch (e) { /* ignore */ }
  };
  const events = ['touchstart', 'touchend', 'pointerdown', 'mousedown', 'keydown'];
  const remove = () => events.forEach((e) => document.removeEventListener(e, unlock, true));
  events.forEach((e) => document.addEventListener(e, unlock, true));

  document.addEventListener('visibilitychange', () => {
    if (document.hidden) return;
    try {
      const ctx = game.sound && game.sound.context;
      if (ctx && ctx.state !== 'running') ctx.resume().catch(() => {});
    } catch (e) { /* ignore */ }
  });
}
