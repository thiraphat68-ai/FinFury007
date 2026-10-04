// Stage lock system (port of GameProgress + the unlock rules of the Android game).
// - Only stage 1 is unlocked on first launch.
// - Winning a stage unlocks the next one (unlockNext). Losing never unlocks anything.
// - Cleared stages can always be replayed (anything <= highest unlocked is playable).
// - Progress persists in localStorage.
import { MAX_STAGES } from '../config.js';
import { getInt, setItem, removeItem } from './storage.js';

const KEY_UNLOCKED = 'unlocked_stage';
const starsKey = (stage) => `stars_stage_${stage}`;

/** Highest stage number the player may start (1..MAX_STAGES). */
export function getHighestUnlocked() {
  let unlocked = getInt(KEY_UNLOCKED, 1);
  // Same safety net as Android: a stage with stars means the next one is open even if the key was lost.
  for (let stage = 1; stage <= MAX_STAGES; stage++) {
    if (getStars(stage) > 0) unlocked = Math.max(unlocked, stage + 1);
  }
  return Math.max(1, Math.min(MAX_STAGES, unlocked));
}

export function isUnlocked(stage) {
  return stage >= 1 && stage <= getHighestUnlocked();
}

/** Call when the player WINS `currentStage`. Never lowers progress, never exceeds MAX_STAGES. */
export function unlockNext(currentStage) {
  const next = Math.min(MAX_STAGES, currentStage + 1);
  if (next > getInt(KEY_UNLOCKED, 1)) setItem(KEY_UNLOCKED, next);   // compare with the STORED value (stars may already imply it)
}

/** Back to a fresh game: only stage 1 open, all stars cleared (for testing / settings reset). */
export function resetProgress() {
  removeItem(KEY_UNLOCKED);
  for (let stage = 1; stage <= MAX_STAGES; stage++) removeItem(starsKey(stage));
}

// ---- stars (best rating per stage, 0 = never won) ----
export function getStars(stage) {
  return getInt(starsKey(stage), 0);
}

/** Keeps only the best result. */
export function saveBestStars(stage, stars) {
  if (stars > getStars(stage)) setItem(starsKey(stage), stars);
}

/** 2 -> "★★☆"; 0 -> "" (same as GameProgress.starsText). */
export function starsText(stars) {
  if (stars <= 0) return '';
  let s = '';
  for (let i = 1; i <= 3; i++) s += i <= stars ? '★' : '☆';
  return s;
}
