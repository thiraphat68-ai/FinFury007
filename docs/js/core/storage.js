// localStorage wrapper. Safari private mode / blocked storage can throw, so every call is guarded
// and falls back to an in-memory map for the current session.
const PREFIX = 'finfury.';
const memory = new Map();

export function getItem(key, fallback = null) {
  try {
    const v = window.localStorage.getItem(PREFIX + key);
    if (v !== null) return v;
  } catch (e) { /* ignore */ }
  return memory.has(key) ? memory.get(key) : fallback;
}

export function setItem(key, value) {
  const s = String(value);
  memory.set(key, s);
  try { window.localStorage.setItem(PREFIX + key, s); } catch (e) { /* ignore */ }
}

export function removeItem(key) {
  memory.delete(key);
  try { window.localStorage.removeItem(PREFIX + key); } catch (e) { /* ignore */ }
}

export function getInt(key, fallback = 0) {
  const n = parseInt(getItem(key, ''), 10);
  return Number.isNaN(n) ? fallback : n;
}

export function getBool(key, fallback = false) {
  const v = getItem(key, null);
  return v === null ? fallback : v === 'true';
}
