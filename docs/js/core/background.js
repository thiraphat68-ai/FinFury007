// Background videos live in the HTML behind the transparent Phaser canvas
// (more reliable on iOS Safari than Phaser's video game object).
//   showVideo('menu')  -> main menu video (bg_video.mp4)
//   showVideo('level') -> stage select video (bg_laval_video.mp4)
//   showVideo(null)    -> no video (e.g. battle)
const videos = {
  menu: document.getElementById('videoMenu'),
  level: document.getElementById('videoLevel'),
};

function tryPlay(video) {
  const p = video.play();
  if (p && typeof p.catch === 'function') {
    // autoplay refused (low-power mode etc.): retry on the first touch
    p.catch(() => {
      const retry = () => { video.play().catch(() => {}); };
      window.addEventListener('pointerdown', retry, { once: true });
    });
  }
}

export function showVideo(name) {
  for (const [key, video] of Object.entries(videos)) {
    if (!video) continue;
    if (key === name) {
      video.classList.remove('hidden');
      tryPlay(video);
    } else {
      video.classList.add('hidden');
      video.pause();
    }
  }
}

/** Resume the visible video after the tab/app returns to the foreground (iOS pauses it). */
document.addEventListener('visibilitychange', () => {
  if (document.hidden) return;
  for (const video of Object.values(videos)) {
    if (video && !video.classList.contains('hidden')) tryPlay(video);
  }
});
