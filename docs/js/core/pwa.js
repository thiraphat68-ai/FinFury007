// Service worker registration + update handling.
// - Works only in a secure context (https:// or http://localhost). A phone opening http://192.168.x.x does NOT get a service worker.
// - On localhost the worker is NOT registered unless the URL has ?sw=1 (so development isn't served from a stale cache);
//   ?sw=0 unregisters it and clears the caches.
// - A new cache version (see sw-precache.js) installs in the background; when it takes over, the page reloads as soon as no
//   battle is running (never in the middle of a fight).
export function registerServiceWorker(game) {
  if (!('serviceWorker' in navigator)) return;
  const params = new URLSearchParams(window.location.search);
  const isLocal = ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname);

  if (params.get('sw') === '0') {
    navigator.serviceWorker.getRegistrations().then((regs) => regs.forEach((r) => r.unregister()));
    if (window.caches) caches.keys().then((keys) => keys.forEach((k) => caches.delete(k)));
    return;
  }
  if (isLocal && params.get('sw') !== '1') return;

  const hadController = !!navigator.serviceWorker.controller;
  let reloadPending = false;

  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!hadController) return;            // first install: nothing to refresh
    reloadPending = true;
  });

  // reload for an update only from the menus (a battle in progress is never interrupted)
  setInterval(() => {
    if (!reloadPending) return;
    const inBattle = game.scene.isActive('BattleScene') || game.scene.isActive('BattleUIScene');
    if (!inBattle) window.location.reload();
  }, 2000);

  window.addEventListener('load', () => {
    navigator.serviceWorker.register('sw.js').then((reg) => {
      reg.update().catch(() => {});        // look for a new version on every launch
    }).catch((err) => console.warn('Service worker registration failed', err));
  });
}
