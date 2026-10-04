# FinFury — Web Port Notes

Source: Android app, Java, package `com.example.finfury` (`app/src/main/java/...`, `app/src/main/res/...`).
Target: web game in `docs/` (GitHub Pages friendly) that runs on iPhone Safari and any desktop/mobile browser.

> These notes were written by reading the Android source as of commit `0385ab2` ("Before web port").
> Numbers below are copied from the Java code. Anything marked **(verify)** was inferred from comments/skimming rather than read line-by-line.

---

## 1. Rules for the whole project

- Do **NOT** modify anything in the Android project. All web files go in a new folder `docs/` at the project root.
- Plain HTML, CSS and JavaScript (ES modules). **No npm, no build tools.**
- Use **Phaser 3** loaded from cdnjs.
- Keep the **same game logic and numbers** (HP, damage, cooldowns, speeds) as the Android version.
- Source files are UTF-8 (no BOM) with Thai text. Keep all Thai strings exactly as they are.

---

## 2. Things to know before porting (hazards found while reading)

1. **Android units are raw pixels, not dp.** `MAX_SPEED = 700`, dash distances (350 px), ranges (380 / 600 / 650 px), enemy hold distances, etc. are all used as pixels on the device screen. Android phones differ in density, so "same numbers" on web means: pick one **reference game-area size** (suggest the layout is designed for a landscape ~800 × 360 dp phone; at ~2.6–3× density that is roughly 2100 × 960 px, but the numbers were tuned on real devices). Recommendation: use a fixed logical resolution (e.g. 1280 × 576 or 1600 × 720, 16:9) with Phaser `Scale.FIT`, and **keep every px constant unchanged** in that logical space. Tune a single global `PX_SCALE` constant once, in one place, if distances feel off.
2. **Time**: Android uses real-time millis (`System.currentTimeMillis`) in enemy AI and `dt` (clamped to 0.05 s) in the player loop. In Phaser use the scene `delta` (clamp to 50 ms) for everything and make timers pause-aware (quiz / pause menu stop all game time).
3. **`unlocked_stage` is no longer written by `BattleActivity`.** `GameProgress.getUnlockedStage()` computes the unlocked stage from the best-star records (`stars_stage_N > 0` → stage N+1 unlocked, capped at 5) plus an optional stored `unlocked_stage`. Web: store only `stars_stage_N` and derive the unlock (same result).
4. **`StageProgress.java` does not exist** (it was removed). The lock logic is `GameProgress.getUnlockedStage`.
5. **Music files `bgm_menu` and `bgm_battle` are referenced (`SoundManager.playMusic`) but there are no such files in `res/raw`.** Only the 15 sfx `.ogg` files exist. Web: music is optional; keep the hook but ship no music (or add a placeholder).
6. **Hero id ↔ hero mapping** (the comment in `BattleActivity` about "ID=3 Electric Eel, 5 Shark" is wrong): `1 Swordfish, 2 Pufferfish, 3 Shark, 4 Octopus, 5 Electric Eel` (see `HeroFactory`). Sprites that face **right** by default: hero ids **3, 4, 5**; ids 1 and 2 face **left** by default.
7. **iPhone Safari**: audio must be unlocked by a user gesture (the first tap on the start button); no `.ogg` on older iOS Safari → convert the 15 sfx to `.mp3` or `.m4a` (keep the same names) or ship both; background videos should be `playsinline muted loop` (or replaced by a static image / canvas if autoplay fails). Add `touch-action: none`, `user-select: none`, and prevent page scroll/zoom.
8. **Dev-time visual-only code can be simplified**: `HitEffects`, `GhostPool`, `StreakView`, `SparkView`, etc. only draw effects (pooled Views/Canvas). They don't change game state. Port them as light Phaser tweens/graphics.
9. Many skills use **overlap tests on view rectangles** (`getGlobalVisibleRect` intersect). Web: use axis-aligned rectangles of the sprite display bounds (same idea) or arcade-physics bodies.
10. Emoji are used as enemy art (🦀 🪼 🐢 🦑 ⭐️) and as skill/item icons. Web can use emoji `Text` objects the same way (font rendering differs between OSes but fine).

---

## 3. Screens and flow

All menu screens in Android live in `MainActivity` (3 layouts toggled with `setVisibility`) plus separate activities:

```
MainActivity
  layoutMainMenu  ──Start──▶  layoutLevelSelect  ──tap unlocked stage──▶  layoutHeroSelect  ──FIGHT!──▶  BattleActivity
        │  ⚙ ตั้งค่า              ▲  back button / system Back                ▲  back button / system Back
        ▼                         └────────────────────────────────────────┘
  SettingsActivity (sound on/off, reset progress, back)

BattleActivity  ── pause menu (resume / restart / exit to level select)
                ── result overlay (win: next stage / replay / stage select; lose: replay / stage select)
                ── returnToLevelSelect() ─▶ MainActivity with SHOW_LEVEL_SELECT=true (skips main menu)
```

`SelectStageActivity` (stage map with draggable icons + stars) exists in the project but is **not the active level-select** — the one in use is `layoutLevelSelect` inside `activity_main.xml`. Port the `activity_main.xml` version. (SelectStageActivity extras: icons are draggable and remember positions; not needed for the port.)

### 3.1 Main menu (`layoutMainMenu`)
- Looping muted background video `bg_video.mp4`.
- Title "FIN & FURY" (`game_title`), button "START GAME" (`btn_start`), button "⚙ ตั้งค่า".
- Start → plays `sfx_button`, pauses menu video, shows level select (plays `bg_laval_video.mp4`).

### 3.2 Level select (`layoutLevelSelect`)
- Looping video `bg_laval_video.mp4` (note the original filename typo "laval"); title "SELECT STAGE".
- Top-left back button (new): back to the main menu. System Back does the same.
- 5 stage buttons `btnStage1..5` with whirlpool icons:
  - unlocked: stage 1 `ic_whirlpool_green`, stages 2–4 `ic_whirlpool_blue`, stage 5 `ic_boss`
  - locked: `ic_whirlpool_lock`, alpha 0.6, **tap does nothing** (no toast, no sound)
- Under each button: best stars text from `GameProgress.starsText` (`★★☆`, empty if never won).
- Unlock rule: stage N is playable if `N <= getUnlockedStage()` (see hazard 3). Starts with only stage 1.
- Tap unlocked stage → `sfx_button`, remember `selectedStageId`, show hero select, default hero = 1.
- Returning from a battle (`SHOW_LEVEL_SELECT`) goes straight here; stars and locks refresh on resume.

### 3.3 Hero select (`layoutHeroSelect`)
- Title "SELECT YOUR HERO", back button top-left (→ level select; also system Back).
- 5 hero cards (hero image + name + role): Swordfish (Fighter), Pufferfish (Tank), Shark (Assassin), Octopus (Mage), Electric Eel (Carry). Selected card background `#00ADB5`, others `#1A252C`.
- Detail panel for the selected hero: big image, name, subject line ("วิชา: …"), description text, and name/description of Skill 1, Skill 2 and Ultimate (taken from the Hero classes — see section 4).
- "FIGHT!" → `sfx_button` → battle with `HERO_ID` and `STAGE_ID`.
- Selected hero details strings in `MainActivity.selectHero` (Thai):
  - 1 Swordfish — "วงจรไฟฟ้า (Electrical Circuits)" — "ใช้พลังงานไฟฟ้าในการโจมตี ฝากประจุไว้บนศัตรูแล้วจุดระเบิด และเชื่อมศัตรูทั้งหมดเป็นวงจรเดียวกัน"
  - 2 Pufferfish — "เคมี (Chemistry)" — "พองตัวและปล่อยสารเคมีสะสมพิษเพื่อสร้างเกราะสะท้อนการโจมตี"
  - 3 Shark — "แคลคูลัส (Calculus)" — "ใช้ความแม่นยำในการคำนวณเวกเตอร์เพื่อพุ่งโจมตีศัตรูอย่างรวดเร็ว"
  - 4 Octopus and 5 Electric Eel — read lines ~300+ of `MainActivity.java` and copy verbatim **(verify)**.
- Default selected hero when entering = 1.

### 3.4 Settings (`SettingsActivity`)
- Sound on/off switch (`sound_enabled`, default true).
- "รีเซ็ตความคืบหน้า" button → confirm dialog ("รีเซ็ตความคืบหน้า?" / "ดาทั้งหมดจะหาย และกลับไปเหลือแค่ด่าน 1 (ค่าตั้งเสียงไม่เปลี่ยน) กู้คืนไม่ได้", buttons "ยกเลิก"/"รีเซ็ต") → clears all `stars_stage_N` and `unlocked_stage`; toast "รีเซ็ตความคืบหน้าแล้ว".
- Back button.

### 3.5 Battle (`BattleActivity`, `activity_battle.xml`)
HUD (landscape):
- Full-screen stage background image (`bg_stage_N`), `gameArea` (play field), player sprite + name label.
- Top-left: profile card with hero image, "HP" bar `barHp` with text "cur/max".
- Top-center: stage info line `txtStageInfo`: `"ด่าน %d  |  ศัตรูเหลือ %d ตัว"` or, with boss, `"ด่าน %d  |  บอสเหลือ %d ชีวิต"`; when the ULT-debuff is active append `"  |  ⚠ ULT -%d%%"` (= round((1−ultGainFactor)×100)).
- Boss HP bar (top center, stage 5 only): name "Kraken Boss ❤❤", bar and "hp/max".
- Stack bar `barStack` + text `"<HeroName> | Stack: cur/max"`.
- Buff text row `txtBuffs` (item buffs with remaining seconds, e.g. `🛡️ 8s  💨 4s`).
- Top-right: pause button (`btnBack` in battle layout).
- Bottom-left: virtual joystick. Bottom-right: Skill 1, Skill 2 buttons (+ ULT button for heroes that use it; all 5 heroes use it). Button label = `icon + "\n" + skillName`; while on cooldown shows remaining seconds `"%.1f"`.
- Ultimate-time bar (`layoutUltTimer`): text `"<UltName> %d.%ds"` + bar, only for timed ultimates (Circuit Link, Blood Frenzy).
- Over-the-head extras: bonus damage text `"+N"` (Blood Frenzy), charge bar (Railgun).
- Overlays (z-order top): quiz, result, pause, tutorial.

Pause overlay: title "⏸ หยุดเกมชั่วคราว", buttons "เล่นต่อ", "เริ่มด่านใหม่", "ออกไปหน้าเลือกด่าน", plus an item legend titled "ไอเทมในเกม" (rows from `ItemManager.Type.label()`, see 6). System Back toggles the pause menu (open ↔ resume); ignored while quiz/result is open.

Tutorial overlay (only first time on stage 1, `tutorial_seen` pref, game paused): "📖 วิธีเล่น" with the text (copy from `activity_battle.xml` line ~724: joystick, skill buttons, stack bar fills at 10 [actual max differs per hero], quiz → unlocks ULT, HP) and button "เข้าใจแล้ว เริ่มเลย!".

Result overlay: win → title "🎉 ชนะแล้ว!" (green `#2ECC71`), stars text, sub "ผ่านด่าน N", buttons: stages 1–4: "ด่านถัดไป ▶" / "เล่นอีกครั้ง" / "เลือกด่าน"; stage 5: "เล่นอีกครั้ง" / "เลือกด่าน". Lose → "💀 แพ้แล้ว" (red `#E74C3C`), sub "HP หมด ลองสู้ใหม่อีกครั้ง", buttons "เล่นอีกครั้ง" / "เลือกด่าน".

### 3.6 Win / lose rules
- **Lose**: player HP reaches 0 → game stops → `sfx_lose` (played instead of `sfx_player_hurt`) → result overlay (lose).
- **Win**: every enemy dead → game stops → `sfx_win`. On the boss stage only `KrakenBoss` must be dead (minions don't count).
- **Stars on win** (HP ratio at the moment of winning): `>= 0.70` → 3, `>= 0.35` → 2, else 1. Best stars per stage are kept (only overwritten when higher).
- Restart = re-create the battle with the same hero/stage.

---

## 4. Heroes

Common (`Hero` base): `maxHp` (default 100), `baseSpeedMultiplier` (1.0), `stackNeeded` (10 — all heroes override), `skill1/2 cooldown` ms, `useSkill1/2(ctx)`, `executeUltimateSkill(ctx)`, `usesUltimateButton()` (**true for all 5 heroes**: a correct quiz answer only unlocks the ULT button; the player presses it).
Player movement: `MAX_SPEED = 700 px/s`; target velocity = `joystick × 700 × heroBaseSpeed × speedMultiplier × slowFactor × itemSpeed`; velocity eases toward target with `k = 1 − exp(−8·dt)`; position clamped to the game area. Facing flips with `moveX > 0.15` / `< −0.15`. Skills lock normal movement (`setSkillLock`) while dashing.

| id | Hero | Subject (`getSubject`) | HP | Speed× | Stack needed | CD skill1 / skill2 (ms) |
|---|---|---|---|---|---|---|
| 1 | Swordfish | Circuits (วงจรไฟฟ้า) | 70 | 1.25 | 20 | 1500 / 3000 |
| 2 | Pufferfish | Chemistry (เคมี) | 150 | 0.8 | 13 | 3000 / 6000 |
| 3 | Shark | Calculus (แคลคูลัส) | 100 | 1.0 | 16 | 2000 / 4000 |
| 4 | Octopus | Programming (โปรแกรม) | 115 | 0.95 | 20 | 2500 / 4500 |
| 5 | Electric Eel | Physics (ฟิสิกส์) | 85 | 1.1 | 20 | 2000 / 2500 |

Player art display width (dp → used as relative width): Swordfish 120, Pufferfish 200, Shark 105, Octopus 145, Eel 95; height = width × 0.545 (image aspect 369/677; hero_5 is 1698×926, same ratio).
All damage the player deals goes through `SeaEnemy.takeDamage(d)` = `d + playerDamageBonus` (item buff, +1 while active) with knockback 55 px unless noted.

### 4.1 Swordfish (Circuits) — electric / charge system
- **Skill 1 "Charge Bite" ⚡** — dash 350 px along the joystick angle in 150 ms (decelerate), passes through enemies, hits each once for **3** (via `hitEnemy`, skill id 1). Clamped to area.
- **Skill 2 "Discharge" 🔋** — bolt projectile (46×16 px) fired from the mouth in the mouth's facing direction; travels 1100 px in 550 ms; stops on the first enemy hit; **2** damage (skill id 2). Mouth point on the sprite = (0.38 w, 0.55 h) of the un-flipped image; direction = from point (0.68 w) → mouth, mapped through current flip/rotation (fallback: joystick angle).
- **Charge mechanic**: a hit applies a "charge" mark (4000 ms, ⚡ status icon) to the enemy. If an enemy already charged by the **other** skill is hit, the mark is consumed: damage becomes **5** (detonate), then an arc hits the nearest other living enemy for **3**. Same-skill hit refreshes the mark (no detonate). Dead enemies lose marks.
- **Stack**: each hit (not while the link ult is active) calls `onHitEnemySuccess()`.
- **Ultimate "Circuit Link" 🔗** — ULT button. Links every living enemy for **5000 ms** (`showUltimateDuration`); while linked, damage dealt to one linked enemy is dealt equally to all other linked living enemies. Stack is **not** gained during the link. Button unlocks (`onUltimateFinished`) immediately so skills stay usable. Time stops while the game is paused (quiz open).
- Thai skill names/descriptions: see `Swordfish.getSkill*` (copy verbatim): Skill 1 "พุ่งกัด สร้างดาเมจ 3 หน่วย ศัตรูที่โดนจะติดประจุ 4 วินาที"; Skill 2 "ยิงคลื่นไฟฟ้า ดาเมจ 2 หน่วย ถ้าโดนศัตรูที่ติดประจุจากอีกสกิล ประจุระเบิด 5 ดาเมจและกระโดดไปตัวใกล้สุด"; Ult "ต่อศัตรูทั้งหมดเป็นวงจร 5 วินาที โจมตีโดนตัวไหนตัวอื่นโดนด้วย".

### 4.2 Pufferfish (Chemistry) — tank
- **Skill 1 "Inflate" 🐡** — `SkillEffects.inflate(scale 2.2, hold 1000 ms, damage 2)`: body scales ×2.2 for 1 s; each enemy touching the inflated body takes **2** once. Description "พองตัว นาน 1 วินาที ศัตรูที่ชนโดน 2 ดาเมจ".
- **Skill 2 "Venom Spray" ☠️** — `SkillEffects.venomSpray(range 600, cone 50°, duration 600 ms, damage 2)`: cone from the mouth along the joystick direction; each enemy in the cone takes **2** once. "พ่นพิษสีม่วงเป็นกรวยจากปาก ดาเมจ 2".
- **Ultimate "Toxic Gulp" 🌀** — aim phase: shows a circle of radius **380 px** around the player; the player moves to put enemies inside. After at least **2000 ms** of aiming **and** ≥1 enemy in the circle, it fires; if no enemy is inside within **6000 ms**, it fizzles (`onUltimateFinished`). Fire: body scales ×1.9, all enemies in the circle are swallowed (AI stops, no knockback) and pulled to the mouth in 450 ms; then **6 chew ticks every 500 ms**, each dealing **2** to every swallowed living enemy; then it spits survivors out radially 240 px (300 ms) and releases the lock. Player is skill-locked during it. "พองตัวดูดศัตรูในวงเข้าปาก บีบ 6 ครั้ง ครั้งละ 2 ดาเมจ แล้วคายออก".

### 4.3 Shark (Calculus) — speed-based damage
- **Skill 1 "Derivative Bite" 🦈** — dash 350 px / 150 ms; damage = `2 + round(speedRatio × (4−2))` where `speedRatio = min(1, |vel|/700)` at button press (2 standing still → 4 at full speed) **+ biteStack** during Frenzy; hits each enemy once; ghost trail colour blue→red by speedRatio. During Frenzy, hitting ≥1 enemy → `biteStack += 1`, missing → `biteStack = 0`; `setPlayerBonusDamage(biteStack)`.
- **Skill 2 "Accumulating Wave" 🌊** — wave front (36 × 150 px) travels 1200 px in 650 ms from 50 px in front of the player, passes through everything; enemy within `70 + max(w,h)/2` px of the wave centre is hit once; **n-th enemy hit gets n damage** (1, 2, 3, …).
- Each hit calls `onHitEnemySuccess()` **except while Frenzy is active**.
- **Ultimate "Blood Frenzy" 🩸** — 6000 ms: speedMultiplier ×1.5 and cooldownMultiplier ×0.5 (button cooldowns halve), bite-stack rule above, timer bar; no stack gain during frenzy; restarts if used again; pauses with game pause. Ends by resetting multipliers/bonus.
- Descriptions: Skill 1 "พุ่งกัด ดาเมจ 2-4 ตามความเร็วที่ว่ายอยู่ตอนกด ยิ่งเร็วยิ่งแรง"; Skill 2 "คลื่นทะลุทุกตัว ตัวแรก 1 ดาเมจ ตัวถัดไปบวกเพิ่มทีละ 1 (1, 2, 3...)"; Ult "6 วินาที ว่ายเร็วขึ้น คูลดาวน์สั้นลง กัดโดนติดกันแรงขึ้นทีละ 1 กัดพลาดรีเซ็ต".

### 4.4 Octopus (Programming) — control
- **Skill 1 "Tentacle Loop" 🐙** — no dash. 3 spins × 300 ms (900 ms total) around the player; at the half-way point of each spin every living enemy within `200 + max(w,h)/2` px takes **1** damage (3 total), **no knockback** (`takeDamage(1,false)`); stack gained once per enemy per cast (not while holding with the ult).
- **Skill 2 "Bug Ink" 🖋️** — ink blob (56 px) flies up to 900 px in 500 ms in the joystick direction; **2** damage on the first enemy hit; leaves an ink puddle (radius **110 px**) lasting **3000 ms** where enemies move at **×0.5** (slow).
- **Ultimate "Infinite Loop" ♾️** — 8 tentacles pull every living enemy to a pile **150 px** in front of the fish within 600 ms; held **4000 ms**; squeeze **1** damage to all held every **500 ms** (8 times); no stack gain while holding. "หนวดดึงศัตรูทั้งหมดมารวมกัน ตรึง 4 วินาที บีบ 8 ครั้ง ครั้งละ 1 ดาเมจ".
- Thai: Skill 1 "หวดหนวดรอบตัว 3 รอบ รอบละ 1 ดาเมจ ใส่ศัตรูในรัศมี"; Skill 2 "ยิงหมึก 2 ดาเมจ ทิ้งแอ่งหมึก 3 วินาที ศัตรูในแอ่งช้าลง 50%".
- Puddle/ult details beyond the doc comment **(verify in `Octopus.java` lines 140–571)**.

### 4.5 Electric Eel (Physics) — ranged
- **Skill 1 "Magnetic Repulsion" 🧲** — one fan-shaped wave (span 120°, ±60°) travels 650 px in 550 ms along the joystick angle; each enemy the wave front reaches (once) takes **2** and is knocked back **450 px** over 450 ms in the wave direction; if it hits the screen edge or another enemy it takes an extra **2** collision damage.
- **Skill 2 "Laser Beam" 🔆** — straight beam 650 px long, 36 px wide, pierces everything in line: **3** damage. Short cooldown (2500).
- **Ultimate "Railgun" 🚀** — charge **1000 ms** (cannot move, charge bar over head), then a huge beam (width **110 px**, full screen length) pierces all: **8** damage + **stun 2000 ms** (enemy stun: no move/attack, can still be hit). Fires once; aiming miss = wasted.
- Descriptions copied from `ElectricEel.getSkill*` **(copy verbatim when writing UI data)**.
- Kinematics/knockback/collision details **(verify in `ElectricEel.java` lines 130–510)**.

### 4.6 Common skill helpers (`SkillEffects`, `RangedAttacks` are for enemies)
- `SkillEffects.dash/inflate/venomSpray/projectile` — generic helpers (Pufferfish uses inflate + venomSpray). Hit rule: once per enemy per cast; overlap by rects / distance.
- `HitEffects` (impact numbers/flash, `playerHit` red flash, attack-path bands, trails) and `GhostPool` (pooled ghost/spark circles) are cosmetic.

---

## 5. Stack gauge, quiz and ultimate

### 5.1 Stack gauge
- `currentStack` 0..`maxStack` (= hero `stackNeeded`). UI: bar + `"<HeroName> | Stack: cur/max"`.
- `onHitEnemySuccess()` (called by skills when they hit): ignored if the game isn't running or no enemy is alive. While `currentStack < maxStack`: `stackFraction += (ultDebuffActive ? stageConfig.ultGainFactor : 1)`, `gained = floor(stackFraction)`, `stackFraction -= gained`, stack increases by `gained` (capped). When it reaches max → `quizManager.show()`.
- Item `ENERGY` adds `+2` stack directly (`addStack`), also triggers quiz when it fills.
- **ULT debuff** (stages with `ultDebuffEveryHits > 0`): every N-th hit the player takes sets a 5000 ms debuff (`ULT_DEBUFF_DURATION_MS = 5000`) during which stack gain per hit uses `ultGainFactor` (stage 2: every 3 hits, ×0.85; stage 3: every 2, ×0.65; stage 4: every 2, ×0.6). Status line shows `⚠ ULT -X%`.
- **Energy drain** (stage ≥ 3): every 3rd hit taken (`hitsTaken % 3 == 0`), stack −= round(maxStack × 20%) (min 0) and `stackFraction = 0` — only if the ULT is not ready, not running and no quiz is showing.
- Stack resets to 0 on: wrong/timeout answer, ultimate finished (`onUltimateFinished`).

### 5.2 Quiz (`QuizManager`, `QuestionBank`)
- Triggered when the stack becomes full. Gate: game running, ≥1 enemy alive, and no pause/result/tutorial overlay open; if blocked, it is re-opened when the pause menu closes (`openPendingQuiz`).
- `onQuizShown`: plays `sfx_quiz_show`, game **paused** (all time stops), joystick reset.
- Overlay: title `"<Subject> Quiz"` (subject string = hero subject, e.g. "Circuits Quiz"), a **3-2-1 countdown** (1 s steps, content hidden, can't answer), then question text + optional formula line (text after `"\nสูตร:"` is shown on its own line), 4 options labelled `A. … D. …`, timer bar + `mm:ss` (**30 s**, `QUIZ_TIME_MS = 30000`; `sfx_quiz_tick` each second during the last 10 s).
- Questions are drawn from a shuffled deck (no repeats until all 20 per subject are used; first of a new deck ≠ last of previous). **20 questions per subject**, 5 subjects, selected by the hero's subject: `chem` → chemistry, `phys` → physics, `program` → programming, `circuit/electric` → circuits, otherwise calculus. Data lives in `QuestionBank.java` (`add(list, question, correctIndex, opt1..opt4)`); port it to `data/questions.js` verbatim.
- Answer or timeout: highlight correct option green (`#27AE60`), wrong pick red (`#C0392B`), others `#2C3E50`; feedback text "✅ ถูกต้อง!" / "❌ ผิด! คำตอบที่ถูกคือข้อสีเขียว" / "⏰ หมดเวลา! คำตอบที่ถูกคือข้อสีเขียว"; wait **2000 ms** then close and call the result.
- Result: correct → `sfx_quiz_correct`; wrong or timeout → `sfx_quiz_wrong`, game unpaused, stack reset to 0.
- Dismissed immediately (no callbacks) when the stage ends.

### 5.3 Ultimate unlock (all heroes use the button)
- Correct answer → game unpaused, `ultimateReady = true`, button becomes enabled/bright with a pop animation, `sfx_ult_ready`. Button label: locked `"🔒 ULT"` (alpha 0.4, disabled) → ready `"ULT!"`.
- Press ULT (only if ready, not already running, game running and not paused): `ultimateReady=false`, `ultimateRunning=true`, `sfx_ultimate`, call `hero.executeUltimateSkill(ctx)`. While `ultimateRunning`, Skill 1/2 are unusable (`canUseSkill`).
- Each ultimate must call `ctx.onUltimateFinished()` when its blocking part ends: it sets `ultimateRunning=false`, `currentStack=0`, refreshes UI. (Circuit Link and Blood Frenzy call it right after starting so skills are usable during the timed effect; the timer bar keeps counting.)
- Skill button press: `canUseSkill()` (game running, not paused, not `ultimateRunning`), plays `sfx_skill1`/`sfx_skill2`, starts the cooldown timer `baseCooldown × cooldownMultiplier × itemCooldownFactor`, button pop animation (0.85× → 1× in 160 ms), then calls the hero skill.

---

## 6. Items (`ItemManager`)

- Enemy death: 25 % (`DROP_CHANCE`) chance to drop a weighted random item; the boss losing a life drops a guaranteed ❤️ (`forceHeart`).
- Random spawn: every 12–15 s (random per cycle), max 2 random items on the map, max 5 items total; spawn position random with 60 px margin. Items float 8000 ms (blink the last 2000 ms), bob up/down 4 px; pickup radius `36 dp + min(playerW,playerH)/2`. ❤️ is not picked up at full HP. Time stops while paused/quiz.
- Types (icon, ring colour, weight):

| Type | Icon | Colour | Weight | Effect |
|---|---|---|---|---|
| HEART | ❤️ | #E53935 | 25 | heal 15 % of max HP (min 1) |
| ENERGY | ⚡ | #FBC02D | 25 | +2 stack |
| SHIELD | 🛡️ | #1E88E5 | 15 | absorb the next hit (10 s); absorbed hit also counts neither as damage nor hit |
| SPEED | 💨 | #26C6DA | 12 | player speed ×1.3 for 6 s |
| COOLDOWN | ⏱️ | #8E24AA | 12 | skill cooldowns ×0.5 for 8 s |
| DAMAGE | 🔥 | #FB8C00 | 6 | +1 damage per hit for 8 s |
| FREEZE | 🧊 | #4FC3F7 | 5 | all enemies slowed ×0.5 for 4 s |

- Pickup popup: `"<icon> <name>: <desc>"` floating text for 1.5 s, colour per item, clamped inside the game area.
- Thai names/descriptions are built in `ItemManager.Type` (nameTh/descTh/`label()`), e.g. `"🛡️ โล่: กันดาเมจ 1 ครั้ง (นาน 10 วินาที)"`. Port the same constants and generate the text in code.
- `SeaEnemy.playerDamageBonus` is set each frame from the DAMAGE buff; HUD buff text updates once per second.

---

## 7. Damage / invulnerability / status effects on the player

- `damagePlayer(d)`: ignored if game not running; **350 ms invulnerability** after each accepted hit; SHIELD absorbs (no HP loss, no hit counted); else HP −= d (min 0), `hitsTaken++`, ULT-debuff and energy-drain checks (see 5.1), sound: `sfx_player_hurt` (or `sfx_lose` if HP ≤ 0), player flashes alpha 0.5 for 100 ms; HP ≤ 0 → lose.
- Slow (`slowPlayer(factor, ms)`): sets slow factor (reset timer, no stacking). Stun (`stunPlayer`): no movement for ms; skills still usable; after a stun ends, **1500 ms stun immunity**.
- Knockback (`knockbackPlayer`): adds velocity along a direction (`vel += dir × speed`), decays through the normal easing.

---

## 8. Enemies and stages

### 8.1 Stage table (`StageConfig`)
`damageMul = 1 + 0.20 × (stage − 1)`; attack interval = `max(1300, round(3000 / 1.4^(stage−1)))` ms, but stage 5 uses 3000.

| Stage | boss | squid | enemies | speedMul | hpMul | attack interval ms | dashSpeedMul | dualAttack | ultDebuffEveryHits | ultGainFactor | damageMul |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | no | yes | 5 | 1.10 | 1.15 | 3000 | 1.05 | no | 0 | 1.0 | 1.0 |
| 2 | no | yes | 5 | 1.20 | 1.40 | 2143 | 1.10 | no | 3 | 0.85 | 1.2 |
| 3 | no | yes | 5 | 1.30 | 1.65 | 1531 | 1.20 | **yes** | 2 | 0.65 | 1.4 |
| 4 | no | yes | 5 | 1.40 | 1.95 | 1300 (min clamp) | 1.30 | **yes** | 2 | 0.6 | 1.6 |
| 5 | **yes** | yes | 1 (boss) + 5 minions | 1.0 | 1.0 | 3000 | 1.0 | no (set true for minions) | 0 | 1.0 | 1.8 |

`includeSquid` is `true` for every stage in the current code (the comment "no squid on stage 4" in StageConfig is stale), so stage 4 also has the squid: 5 enemies.
Stage backgrounds: `bg_stage_1..5` (fallback `bg_level_video.png`). Stage 1 shows the tutorial the first time.

### 8.2 Normal stage spawn (stages 1–4; positions as fractions of the game area W×H)
🦀 Crab (0.70W, 0.15H), 🪼 Jellyfish (0.85W, 0.35H), 🐢 Turtle (0.95W, 0.55H), 🦑 Kraken/squid (0.90W, 0.75H) [if `includeSquid`], ⭐️ Starfish (0.65W, 0.85H). Display name = English name; HP bar (red), name (red, 11 sp), hp text (yellow); status icons above: ⚡ charged, 🐌 slowed, 💫 stunned.

### 8.3 Base stats (before stage modifiers)
Final HP = `round(baseHp × hpMul × hpScale)`, final attack damage = `round(baseDamage × damageMul × damageScale)` (min 1), speed ×= speedMul, dash speed = `BASE_DASH_SPEED × dashSpeedMul`, attack interval from stage.

| Enemy | HP | speedMultiplier | hold min–max px | base damage | attack type | idle move style |
|---|---|---|---|---|---|---|
| 🦀 Crab | 16 | 0.85 | 160–240 | 6 | DASH | SIDESTEP (side steps) |
| 🦑 Kraken (squid) | 12 | 1.25 | 420–500 | 5 | INK_CONE | KITE (retreats when player closer than min) |
| 🐢 Turtle | 18 | 0.85 | 240–340 | 5 | DASH | PATROL (random map points) |
| 🪼 Jellyfish | 9 | 1.0 | 180–220 | 4 | LINE_SHOT | DRIFT (wavy bob) |
| ⭐️ Starfish / other | 11 | 1.0 | 200–320 | 5 | DASH | ERRATIC (random points around player) |

### 8.4 Enemy AI (`SeaEnemy.updateAI`)
Constants: `BASE_SPEED = 700 × 0.75 = 525`, `ORBIT_SPEED_FACTOR = 0.60`, `BASE_DASH_SPEED = 7000 px/s`, `ORBIT_ROTATE = 0.45 rad/s`, `ZIGZAG_AMPLITUDE = 90`, `ZIGZAG_FREQ = 3.2`, `ENEMY_SPACING = 210`, `SEPARATION_SPEED = 400`, `ATTACK_TRIGGER_RANGE = 45`, `PASS_THROUGH_DISTANCE = 160`, `WINDUP_MS = 500`, `APPROACH_BEFORE_ATTACK_MS = 1200`, slot angle = 360°/aliveCount (non-boss, non-swallowed) + random ±0.2 rad.

State machine per enemy: `ORBIT_AND_WAIT → WINDUP (500 ms) → ATTACKING (DASH only) → ORBIT_AND_WAIT`.
1. **ORBIT_AND_WAIT**: accumulate `timeInOrbitMs` (starts random 0–800). Walk toward the hold point per move style (orbit speed = 60 % of walk speed unless far away > holdMax+60; KITE retreat at full speed). Ready to attack when `timeInOrbit ≥ attackInterval − 500` and ≥300 ms since the last attack ended; squid additionally requires player within `INK_RANGE × 0.9` (= 576 px). Needs an **attack slot** (1 slot; 2 on dual-attack stages); the longest-waiting ready enemy gets priority.
2. **WINDUP** (500 ms): enemy stops; `sfx_enemy_warn`; the attack path/zone is locked and shown:
   - DASH: band from enemy to target point = player centre + 160 px beyond (clamped inside the area), width 90 px.
   - LINE_SHOT (jellyfish): aims at player's centre, leading the player by `700 × speedRatio × 0.5 s` along the joystick angle; band width 60 px.
   - INK_CONE (squid): cone telegraph toward the player.
3. **ATTACKING (DASH)**: moves at dash speed to the locked target; damage check = distance from player centre to the segment of this frame's movement ≤ 45 px (once per dash) → `damagePlayer(attackDamage)` + `onPlayerHit(dir)`. Ends when within 15 px of target. Releases the slot, `timeInOrbit = 0`.
4. **Ranged**: after WINDUP it fires immediately, releases the slot, returns to orbit.
   - Jellyfish LINE_SHOT: barrage of **15 shots**, one per **120 ms**, along the locked direction, projectile speed = `525 × speedMul × 3` adjusted so the crossing time is `max(0.25, maxDist/speed − 0.2×(stage−1))` s; each shot is a 70×26 px bolt, hit radius 45 px, zig-zag wave amplitude 110 px / wavelength 480 px; damage per shot = `max(1, round(attackDamage / 3))`; recoil of the jelly opposite to the shot (initial 1400 px/s, decay 7/s). Hits stun the player 500 ms (all stages).
   - Squid INK_CONE: cone range 640 px, half angle 32° (total 64°), damage = attackDamage.
5. Taking any damage during WINDUP/ATTACKING interrupts the attack (slot released, back to orbit).
6. Enemy separation: while orbiting, if another living non-boss enemy is closer than 210 px, push apart with `(210−d)/210 × 400 × dt`.
7. Clamp inside the game area. Sprite flips toward the player; wobble: `rotation = sin(t·1.5)·7°`, `scaleX = facing·(1+0.06·sin(2.5t))`, `scaleY = 1−0.06·sin(2.5t)`.
8. **On-hit effects on the player (stages 2–4 only for turtle/starfish)**: Jellyfish (all stages) stun 500 ms; Turtle slow ×0.5 for 2000 ms; Starfish knockback 2000 px/s. (Crab and squid have no extra.)
9. **Status**: `applySlow(factor, ms)` scales the enemy's dt (move/attack time); `stun(ms)` freezes it and cancels any attack; `swallowed` (Pufferfish ult) freezes AI and knockback.
10. **Taking damage**: `hp -= d`; `sfx_hit_enemy` (or `sfx_enemy_die` if dead); hit impact effect; knockback 55 px over 120 ms away from the player (unless `allowKnockback()` false or swallowed); flash alpha 0.3→1.0 (80 ms each); on death: shrink/fade 250 ms, **death shockwave** (pushes player away from the corpse at 5000 px/s, no damage; white expanding ring), `dropItemAt(centre)`, `onDefeated()`, then `ctx.onEnemyDefeated()` (updates stage text and checks win).
11. `attackQueue` state is static: reset at stage start (`resetAttackQueue`, then `setDualAttack`).

### 8.5 Stage 5 — Kraken Boss (`KrakenBoss`) + minions (`BossMinion`)
- One `KrakenBoss` at (0.6W, 0.2H) then re-placed at "home" = (0.72W − w/2, centre vertically). Image `boss_image.png` with 47-frame idle animation `octo_idle_00..46` (256×170, 24 fps; ×1.6 faster while winding up a wave). Visual scale 8× (base 65 dp); max height 95 % of the area; frame aspect 170/256.
- **2 lives**: life 1 HP **100**; after dying once → phase 2 with HP **30** (`PHASE2_HP`), size ×2, attack rate ×1.5, plays `sfx_enemy_warn`, drops a ❤️, removes **all minions silently** (`removeSilently`: no shockwave, no HP loss).
- Boss immune to stun/slow/swallow/knockback. Player damage vs boss is ×**1.15** with carried fractional remainder (`damageCarry`). Minion deaths call `takeFixedDamage(10)` (exact, no multiplier) on the boss.
- Name label/HUD: "Kraken Boss ❤❤" (one ❤ per life left); boss HP bar at top center; stage text shows lives left.
- **Wave attack**: IDLE → WAVE_WINDUP (500 ms, telegraph bands) → fires `waveLines` lines at spread angle, `waveVolleys` volleys (volley gap = 500 ms windup), then cooldown **5000 ms** (counts down faster in phase 2 ×1.5). Phase 1: 5 lines, spread 14°, 2 volleys, speed 1700 px/s. Phase 2: 7 lines, spread 10°, 3 volleys, speed 2200 px/s. Per-line damage = `max(1, round(5 × damageMul(1.8)))` = **9**. First cooldown 2000 ms at start, 1000 ms after entering phase 2.
- **Tail slam (phase 2 only)**: when the player is within `max(w,h)/2 + 120` px and cooldown ≤ 0 → TAIL_WINDUP 600 ms with a circle telegraph; if the player is still inside the radius at the end → **15** damage; cooldown 5000 ms (fixed; first one 2000 ms after phase 2 starts). Phase 2 also creeps toward the player at 90 px/s when idle and farther than 0.8×tail radius.
- **Minions** (`BossMinion extends SeaEnemy`, stage-3 stats): Crab, Jellyfish, Turtle, Kraken, Starfish spawned at x = (0.10, 0.25, 0.05, 0.22, 0.12)·W and y = (0.15, 0.35, 0.55, 0.75, 0.85)·H (left side), dual attack = true, damage ×0.25, HP ×1.2, on player hit → slow ×0.7 for 2000 ms (replaces turtle/starfish/jelly effects), on death → boss −10 HP.
- Win: boss fully dead (2 lives gone); minions need not die.

---

## 9. Assets in `res/` and their use

### 9.1 Images (`res/drawable-nodpi`, `res/drawable`)
| File | Size | Used for |
|---|---|---|
| `hero_1..4.png` | 677×369 RGBA | hero sprite in battle, profile card, hero select card/portrait (1 Swordfish, 2 Pufferfish, 3 Shark, 4 Octopus) |
| `hero_5.png` | 1698×926 RGBA | Electric Eel (same use) |
| `boss_image.png` | 665×375 | Kraken boss (placeholder before frames load) |
| `octo_idle_00..46.png` | 256×170 each (47 frames) | boss idle animation, 24 fps (also `drawable/octo_idle.xml` = animation-list for Android; ignore) |
| `ic_whirlpool_green.png` / `_blue.png` / `_lock.png` | ~413×465 / 383×442 / 404×445 | stage button icons (stage 1 green, 2–4 blue, locked lock) |
| `ic_boss.png` | 579×431 | stage 5 button icon when unlocked |
| `bg_stage_1.png`, `bg_stage_2.png`, `bg_stage_3.jpg`, `bg_stage_4.png`, `bg_stage_5.jpg` | ~1024×571 … 1376×768 | battle backgrounds for stages 1–5 |
| `bg_level_video.png` | — | fallback battle background |
| `drawable/bg_skill_chip.xml`, `ic_launcher_*` | vector/XML | UI chip / app icon (not needed; recreate chips in CSS; make a web favicon from the launcher art) |

### 9.2 Video (`res/raw`)
- `bg_video.mp4` — main-menu background (loop, muted).
- `bg_laval_video.mp4` — stage-select background (loop, muted). (Typo in the filename kept in the Android project; on web name it `bg_level_video.mp4` or keep as is.)

### 9.3 Sound effects (`res/raw/*.ogg`) → `SoundManager.Sfx`
| Sfx enum | File | Played when |
|---|---|---|
| SKILL1 | `sfx_skill1.ogg` | Skill 1 button |
| SKILL2 | `sfx_skill2.ogg` | Skill 2 button |
| ULTIMATE | `sfx_ultimate.ogg` | ULT pressed / auto-ult |
| HIT_ENEMY | `sfx_hit_enemy.ogg` | enemy takes damage (survives) |
| PLAYER_HURT | `sfx_player_hurt.ogg` | player takes damage (HP > 0) |
| ENEMY_DIE | `sfx_enemy_die.ogg` | enemy killed |
| ENEMY_WARN | `sfx_enemy_warn.ogg` | enemy attack windup; boss phase change |
| QUIZ_SHOW | `sfx_quiz_show.ogg` | quiz opens |
| QUIZ_CORRECT | `sfx_quiz_correct.ogg` | correct answer |
| QUIZ_WRONG | `sfx_quiz_wrong.ogg` | wrong or timeout |
| QUIZ_TICK | `sfx_quiz_tick.ogg` | each of the last 10 s of the quiz timer |
| BUTTON | `sfx_button.ogg` | any menu button / stage / hero card tap |
| ULT_READY | `sfx_ult_ready.ogg` | ULT unlocked; also item pickup |
| WIN | `sfx_win.ogg` | stage cleared |
| LOSE | `sfx_lose.ogg` | player HP 0 |

Sound rules: global enable flag (default on, saved), max 8 simultaneous sfx, SFX volume 0.9, music volume 0.45, the same sfx can't replay within 50 ms. `playMusic("bgm_menu"/"bgm_battle")` exists but **there are no music files** (hazard 5).

---

## 10. Persistence (SharedPreferences "GamePrefs" → `localStorage`)
| Key | Meaning |
|---|---|
| `stars_stage_<N>` (N=1..5) | best star rating (0 = never won) |
| `unlocked_stage` | optional stored unlock (legacy; read but not written by battle any more) |
| `tutorial_seen` | boolean, stage-1 tutorial shown |
| `sound_enabled` | boolean, default true |
| `StageIconPositions` prefs `x<N>`/`y<N>` | only SelectStageActivity (unused) |

Unlocked stage = `clamp(max(unlocked_stage, 1, max over stages with stars>0 of N+1), 1, 5)`.

---

## 11. Java class → JavaScript file mapping

Target layout (all under `docs/`):
```
docs/
  index.html                (Phaser from cdnjs, canvas container, mobile meta tags, DOM overlays)
  style.css                 (menus, overlays, HUD, joystick, buttons)
  PORT_NOTES.md
  assets/img/…  assets/video/…  assets/audio/…
  js/
    main.js                 (Phaser.Game config, scene list, scaling)
    config.js               (logical resolution, MAX_SPEED, global constants)
    data/stages.js
    data/questions.js
    data/heroes.js          (names, stats, Thai text)
    game/…                  (battle logic)
    ui/…                    (DOM menus / overlays)
    core/…                  (storage, audio)
```

| Java class / resource | JS file | Notes |
|---|---|---|
| `MainActivity` + `activity_main.xml` (main menu, level select, hero select) | `js/scenes/MenuScene.js` (or DOM in `js/ui/menus.js`) | 3 sub-screens, video backgrounds, back buttons, SHOW_LEVEL_SELECT equivalent via scene data |
| `SelectStageActivity` | — (not ported; unused) | — |
| `SettingsActivity` | `js/ui/settings.js` | sound toggle, reset progress, confirm |
| `BaseActivity` (fullscreen) | `js/main.js` / CSS | fullscreen/PWA meta tags |
| `BattleActivity` + `BattleContext` | `js/scenes/BattleScene.js` + `js/game/BattleContext.js` | game loop, HUD, pause, quiz gate, stack/ULT, win/lose, damage rules; `BattleContext` becomes the object passed to hero skills |
| `activity_battle.xml` | `index.html` DOM overlays + `js/ui/hud.js` | HUD, joystick, skill buttons, overlays |
| `Hero`, `HeroFactory` | `js/heroes/Hero.js`, `js/heroes/index.js` (factory) | base class + `createHero(id)` |
| `Swordfish` | `js/heroes/Swordfish.js` | charge marks, circuit link |
| `Pufferfish` | `js/heroes/Pufferfish.js` | inflate, venom, toxic gulp |
| `Shark` | `js/heroes/Shark.js` | bite, wave, frenzy |
| `Octopus` | `js/heroes/Octopus.js` | tentacle loop, ink puddle, infinite loop |
| `ElectricEel` | `js/heroes/ElectricEel.js` | repulsion, laser, railgun |
| `SkillEffects` | `js/game/skillEffects.js` | dash / inflate / venomSpray / projectile helpers |
| `SeaEnemy` | `js/game/SeaEnemy.js` | AI state machine, slots, stats, damage |
| `BossMinion` | `js/game/BossMinion.js` | |
| `KrakenBoss` | `js/game/KrakenBoss.js` | 2 lives, waves, tail slam, HUD bar |
| `RangedAttacks` | `js/game/rangedAttacks.js` | line shot (jelly), ink cone (squid) |
| `StageConfig` | `js/data/stages.js` | table in 8.1 |
| `GameProgress` | `js/core/progress.js` | stars, unlocked stage, reset |
| `ItemManager` | `js/game/ItemManager.js` | types, drop, buffs, popups |
| `QuizManager` | `js/ui/quiz.js` | overlay, 3-2-1 countdown, timer, deck shuffle |
| `QuestionBank` | `js/data/questions.js` | 5 subjects × 20 questions, copy verbatim |
| `SoundManager` | `js/core/audio.js` | Web Audio/HTMLAudio, unlock on first tap, enable flag |
| `HitEffects`, `GhostPool` | `js/game/effects.js` | cosmetic; pool or use tweens |
| `res/values/strings.xml` | `js/data/strings.js` | Thai/English labels (note: hero skill strings in strings.xml are **outdated**; the live source for skill text is the Hero classes) |

---

## 12. Suggested porting order (for the next steps, not done here)
1. Scaffolding: `index.html`, Phaser config, scaling, asset preload (copy/convert assets into `docs/assets/`).
2. Core: storage/progress, audio, stage data, question bank.
3. Menus: main menu → level select → hero select → settings.
4. Battle core: player movement + joystick, HP/stack HUD, enemy AI (SeaEnemy), win/lose, stars.
5. Heroes one by one (Swordfish first), quiz + ULT flow.
6. Items, stage scaling, then boss stage.
7. Polish: effects, pause/tutorial, iOS audio unlock, performance check on iPhone.

---

## 13. Step 2 — what has been built (web skeleton)

Status: **menus + stage lock work; battle is a placeholder.** Verified with a local server and headless Edge screenshots
(menu with video background, stage select with lock icons, placeholder battle, portrait "rotate" overlay).
Not yet tested on a real iPhone.

### 13.1 How to run it
Must be served over HTTP (ES modules do not work from `file://`).
```
powershell -ExecutionPolicy Bypass -File docs\serve.ps1          # port 8000
powershell -ExecutionPolicy Bypass -File docs\serve.ps1 -Port 9000
```
`docs/serve.ps1` is a tiny no-install server (multi-threaded, HTTP Range support for iOS video). It prints the
PC URL and the Wi-Fi URL(s) to open on the iPhone (e.g. `http://192.168.1.9:8000/`). Windows Firewall may ask once to
allow it: choose **Private networks**. Python alternative (if installed): `python -m http.server 8000 --bind 0.0.0.0` inside `docs/`.
Dev URL options: `?scene=StageSelectScene` / `?scene=BattleScene` jump to a scene; `?debug=1` adds a
"[TEST] Win stage → unlock next" button on the placeholder battle.

### 13.2 Files (all in `docs/`)
| File | Role |
|---|---|
| `index.html` | viewport meta (`viewport-fit=cover`, no zoom), 2 `<video>` backgrounds, `#game` container, rotate overlay, Phaser 3.80.1 from cdnjs, `js/main.js` as ES module |
| `style.css` | no scroll/zoom (`touch-action:none`, fixed body), `#game` inset by `env(safe-area-inset-*)`, rotate overlay shown by `@media (orientation: portrait)` |
| `js/main.js` | Phaser config (1280×720 logical, `Scale.FIT` + `CENTER_BOTH`, transparent canvas, 3 pointers), iOS gesture/double-tap blockers |
| `js/config.js` | `GAME_WIDTH/HEIGHT`, `MAX_STAGES`, colours, stage icon positions (same biases as Android) |
| `js/core/storage.js` | safe `localStorage` wrapper (falls back to memory if blocked, keys prefixed `finfury.`) |
| `js/core/stageProgress.js` | **stage lock**: `isUnlocked`, `unlockNext`, `getHighestUnlocked`, `resetProgress` (+ `getStars`, `saveBestStars`, `starsText`). Keys `unlocked_stage`, `stars_stage_N` |
| `js/core/audio.js` | `playSfx`, enable flag (`sound_enabled`); never throws |
| `js/core/background.js` | `showVideo('menu' | 'level' | null)` toggles the HTML videos behind the canvas |
| `js/ui/widgets.js` | `makeButton`, `makeBackButton`, `showToast` |
| `js/scenes/BootScene.js` | loading bar + preload of images/audio, then starts `MenuScene` |
| `js/scenes/MenuScene.js` | main menu: title, START GAME, ⚙ ตั้งค่า (settings panel: sound toggle, reset progress with confirm) |
| `js/scenes/StageSelectScene.js` | 5 stage icons, stars, back button, lock behaviour |
| `js/scenes/BattleScene.js` | **placeholder**: stage number + back button (+ `?debug=1` test button) |
| `serve.ps1` | local test server (see 13.1) |
| `assets/img/*` | `ic_whirlpool_green/blue/lock.png`, `ic_boss.png`, `bg_stage_1..5` (copied unchanged) |
| `assets/video/bg_menu.mp4`, `bg_level.mp4` | copies of `bg_video.mp4` and `bg_laval_video.mp4` (renamed; typo fixed) |
| `assets/audio/sfx_button.ogg` | only sound copied so far |

### 13.3 Behaviour implemented
- **Scaling**: fixed 1280×720 logical canvas, scaled to fit and centred; bars are black. Container is inset by the iOS safe area.
- **Portrait**: full-screen overlay "Please rotate your phone" (+ Thai line) whenever `orientation: portrait`.
- **Zoom/scroll**: `user-scalable=no`, `touch-action:none`, fixed body, `gesture*` / `touchmove` prevented, double-tap zoom blocked.
- **Stage lock**: only stage 1 open on first run. Locked stages show the lock icon, grey tint and alpha 0.6; tapping one shows the
  toast **"Clear the previous stage first"** and does nothing else (no sound, no scene change). Unlocked stage → `BattleScene({stage})`.
  Stage 1 green icon, 2–4 blue with the stage number, 5 boss icon when open. Best stars shown under each icon.
  `unlockNext(stage)` is ready for the real battle's win handler; `resetProgress()` is wired to the settings panel.
- **Menu flow** = `MenuScene → StageSelectScene → BattleScene(placeholder)`. Hero select is **not built yet** (Android has Stage → Hero → Battle);
  it will be inserted between StageSelectScene and BattleScene.
- Note: the toast on locked stages was removed from the Android version earlier, but the web spec for this step asks for it, so the web version shows it.

### 13.4 Known gaps / TODO
- **Sound on old iPhones**: only `sfx_button.ogg` is shipped; Safari before 18.4 cannot play Ogg. No ffmpeg on this PC, so I could not convert.
  Convert all 15 `.ogg` to `.mp3` (or `.m4a`) and list the mp3 first in `BootScene` (`load.audio(key, [mp3, ogg])`).
- The 2 videos total ~4.6 MB; if autoplay is blocked (iOS low-power mode) the video starts on the first tap.
- Hero select, real battle, quiz, items, boss: later steps (see section 12).

---

## 14. Step 3 — core battle with Swordfish (built)

Status: **playable battle for stages 1–4 with Swordfish (skill 1 + skill 2).** Verified in headless Edge driven over the
DevTools protocol (real time): idle start, auto-playing bot (kills enemies, takes damage), keyboard move + J/K skills,
forced win (stars saved, next stage unlocked, stage select shows stage 2 open), forced lose, pause overlay, stage 4 run
(no console errors). Not yet tested with real touch on an iPhone.

### 14.1 Files added / changed
| File | Role |
|---|---|
| `js/scenes/BattleScene.js` | world + game logic (port of `BattleActivity` / `BattleContext`): player movement, damage, status, win/lose, pause, skill cooldowns, task scheduler |
| `js/scenes/BattleUIScene.js` | HUD, virtual joystick, skill buttons, keyboard, pause + result overlays (runs on top of BattleScene) |
| `js/game/SeaEnemy.js` | port of `SeaEnemy` (stats, stage modifiers, AI state machine, attack slots, ranged attacks, damage/death) |
| `js/game/rangedAttacks.js` | jellyfish line-shot bolts and squid ink cone (+ telegraphs) |
| `js/game/effects.js` | cosmetic effects (ring, spark, lightning arc, damage numbers, attack-path band) |
| `js/heroes/Hero.js`, `js/heroes/Swordfish.js` | hero base class + Swordfish (skills, charge marks, detonation, mouth aim) |
| `js/data/stages.js` | `StageConfig` table (stages 1–5; stage 5 boss data only) |
| `js/config.js` | added `WORLD_W/H`, `DENSITY`, `dp()`, `MAX_SPEED`, `UI_PER_DP` |
| `js/core/stageProgress.js` | `unlockNext` now compares with the *stored* value (stars could already imply the unlock) |
| `js/scenes/BootScene.js` | also loads `hero_1` and all 15 sfx (`.ogg`); `main.js` registers `BattleScene` + `BattleUIScene` |
| `assets/img/hero_1.png`, `assets/audio/sfx_*.ogg` | copied unchanged from `res/` |

### 14.2 Coordinate system (important for every later step)
- Battle **world = 1920×1080 "Android px"** (reference: a 1080p phone at density 2.75). The battle camera shows it at zoom **2/3**
  inside the 1280×720 canvas, so **every px constant from the Java code is used unchanged** (350 px dash, 700 px/s speed,
  210 px enemy spacing, ranges, ...). `dp(v)` = v × 2.75 world px (used for sprite sizes: hero width 120 dp = 330 px).
- HUD / controls are drawn in **canvas units** (1 Android dp = 2 units): joystick 100 dp → radius 100, skill buttons 84 dp → radius 84, margins 24 dp → 48.
- If distances feel off on real devices, tune `WORLD_W/H`/`DENSITY` in `config.js` (single place).
- `BattleScene.gs` is the scene handle passed to skills/enemies as `ctx.gs` (`Phaser.Scene.scene` is the ScenePlugin, so `ctx.scene` must **not** be used).
- Enemy/player positions are the **top-left** of their boxes (like Android `getX()/getY()`); the AI targets the player's top-left. Enemy box 130×200, player box 330×(36 + 180).

### 14.3 What is ported (same numbers as Android)
- **Player**: velocity easing `1−exp(−8dt)`, `MAX_SPEED 700 × hero speed 1.25`, clamp to area, facing flip (hero_1 faces left in the source image), tilt / wiggle / squash / bob animation, slow, stun (+1500 ms immunity), knockback, 350 ms hit invulnerability, damage flash.
- **Enemies**: Crab, Jellyfish, Turtle, Kraken (squid), Starfish with stats, hold distances, move styles (side-step / drift / patrol / kite / erratic), orbit + zig-zag, attack slots (1, or 2 on stages 3–4), windup 500 ms with telegraph bands, dash with segment hit test, jellyfish 15-shot sine-wave barrage (+ recoil, stun 500 ms), squid ink cone (380 ms expand), turtle slow / starfish knockback on stages 2–4, enemy separation, knockback 55 px on hit, interrupt on damage, death shockwave (5000 px/s push), status icons (⚡ 🐌 💫), HP bar + text, per-stage HP / speed / damage / attack interval / dash speed.
- **Stages 1–4**: enemy count and spawn positions as in `spawnStageEnemies`; stage backgrounds.
- **Swordfish skill 1 "Charge Bite"**: 350 px dash in 150 ms (decelerate), hits each enemy once for 3, applies charge (4 s). **Skill 2 "Discharge"**: 46×16 bolt from the mouth (mouth point 0.38/0.55 of the image, direction through current flip/tilt), 1100 px in 550 ms, stops at first enemy, 2 damage. **Charge system**: other-skill hit on a charged enemy = detonate 5 damage + arc to nearest enemy for 3; same-skill hit refreshes the charge; charges expire after 4 s or on death.
- **Cooldowns** 1500 / 3000 ms (× cooldown multiplier 1), button shows remaining seconds.
- **Win**: all enemies dead → `sfx_win`, stars (≥70 % HP → 3, ≥35 % → 2, else 1; best kept), `unlockNext(stage)`, win overlay (Next stage ▶ / Play again / Stage select). **Lose**: HP 0 → `sfx_lose`, overlay (Play again / Stage select). Losing never unlocks.
- **Pause**: HUD button, `P` / `Esc`, and automatically when the tab/app goes to the background. Resume / Restart stage / Exit to stage select. Everything time-based (AI, projectiles, delayed shots, cooldowns) freezes while paused.
- **Controls**: virtual joystick (fixed, bottom-left, generous touch zone) tracked by pointer id + separate skill buttons (bottom-right) → **multi-touch works** (move and use skills at once). Keyboard: WASD / arrows, `J` skill 1, `K` skill 2.
- **Sounds** (when the browser can decode Ogg): skill 1/2, enemy hit/die/warn, player hurt, win, lose, button.
- **HUD**: hero portrait + HP bar `cur/max`, `ด่าน N | ศัตรูเหลือ N ตัว`, pause button.

### 14.4 Not ported yet (next steps)
- Stack gauge, **quiz**, **ULT button / Circuit Link** (Swordfish ultimate), ULT debuff and energy-drain rules, tutorial overlay.
- **Items** (drops, buffs, pickup popups) — `dropItemAt` / `damageBonus` are stubs.
- **Boss stage (5)**: shows a "boss coming soon" placeholder; `KrakenBoss` and `BossMinion` not ported. After winning stage 4 "ด่านถัดไป ▶" opens that placeholder.
- Hero select (battle always uses Swordfish) and the other 4 heroes; other hero art.
- Fine hit-testing: collisions use axis-aligned boxes (player box 330×216, enemy box 130×200, rotated bolt → bounding box), like Android's view rectangles; the exact emoji view size on Android is approximated (box sizes are estimates → **verify feel against the phone**).
- Dash streak / ghost trails are simplified cosmetics.
- Sounds on iOS Safari < 18.4 still need `.mp3`/`.m4a` conversions (no ffmpeg here).

### 14.5 Dev helpers
- `?scene=BattleScene` jumps straight into stage 1; `?bot=1` makes the HUD scene play by itself (chases the nearest enemy, spams skills) for quick testing.
- Console: `game.scene.getScene('BattleScene')` exposes `hp`, `getEnemies()`, `openPause()`, `finish(true/false)` ...

---

## 15. Step 4 — all 5 heroes + hero select (built)

Status: **5 playable heroes (stats + skill 1 + skill 2) and the Stage → Hero → Battle flow.** Ultimates, stack gauge and quiz are still NOT ported.
Verified in headless Edge (DevTools protocol, real time): hero-select screen for each hero; every hero in a real battle with the
bot (no console errors); a scripted damage test per hero (enemies frozen in a row, skills fired, damage read back — results below);
keyboard movement; the full flow Stage select → Hero select → FIGHT → pause/resume → restart → win → next stage (hero kept).
Touch input is shared by all heroes (joystick + 2 skill buttons from `BattleUIScene`), so every hero works with touch and keyboard;
not yet tested on a real phone.

### 15.1 Files
| File | Role |
|---|---|
| `js/heroes/Hero.js` | base class (id, stats, sprite width / facing, skill texts, hero-select texts, `update`/`clearAll` hooks) |
| `js/heroes/Swordfish.js`, `Pufferfish.js`, `Shark.js`, `Octopus.js`, `ElectricEel.js` | one file per hero (stats, skill 1, skill 2, names/icons/descriptions copied from the Java classes) |
| `js/heroes/heroFactory.js` | `createHero(id)` (1 Swordfish, 2 Pufferfish, 3 Shark, 4 Octopus, 5 Electric Eel) and `HERO_IDS` |
| `js/game/geometry.js` | `rectsOverlap`, `distanceToSegment`, `decelerate`, `accelDecel` (Android interpolators) |
| `js/scenes/HeroSelectScene.js` | port of `layoutHeroSelect`: 5 cards (image, name, role), detail panel (image, name, subject, description, FIGHT!, Skill 1 / Skill 2 / Ultimate rows) |
| `js/ui/widgets.js` | new `wrapText()` — word-wrap that works for Thai (uses `Intl.Segmenter`) |
| `js/scenes/BattleScene.js` | uses `createHero`, per-hero sprite width/facing, player scale (Pufferfish inflate), hero kept on restart / next stage |
| `js/scenes/BattleUIScene.js` | HUD portrait + skill button labels come from the chosen hero |
| `js/scenes/StageSelectScene.js`, `BootScene.js`, `main.js` | stage tap now opens `HeroSelectScene`; all 5 hero images are loaded |
| `assets/img/hero_2..5.png` | copied unchanged from `res/` |

Flow = `Menu → StageSelect → HeroSelect (default hero 1 each time) → Battle`; back on hero select returns to stage select (button and Esc).
Dev URL: `?scene=BattleScene&hero=3&stage=2&bot=1`, `?scene=HeroSelectScene`.

### 15.2 Heroes (all numbers from the Java classes)
| id | Hero | HP | Speed× | Cooldown 1 / 2 (ms) | Image width (dp) | Source image faces |
|---|---|---|---|---|---|---|
| 1 | Swordfish | 70 | 1.25 | 1500 / 3000 | 120 | left |
| 2 | Pufferfish | 150 | 0.8 | 3000 / 6000 | 200 | left |
| 3 | Shark | 100 | 1.0 | 2000 / 4000 | 105 | right |
| 4 | Octopus | 115 | 0.95 | 2500 / 4500 | 145 | right |
| 5 | Electric Eel | 85 | 1.1 | 2000 / 2500 | 95 | right |

Skills as ported (see section 4 for the full description):
- **Swordfish**: Charge Bite (dash 350 px/150 ms, 3 dmg + charge), Discharge (bolt 1100 px/550 ms, 2 dmg), charge → detonate 5 + arc 3.
- **Pufferfish**: Inflate (scale 2.2, puff 250 ms + hold 1000 ms + shrink 300 ms, 2 dmg to enemies touching the enlarged box, once each; movement locked the whole time); Venom Spray (cone 50°, range 600, 0.6 s, beam grows `range × min(1, 2.5·t)`, 2 dmg once each; origin = edge of the fish on the facing side, uses the inflated size).
- **Shark**: Derivative Bite (dash 350 px/150 ms, damage `2 + round(speedRatio × 2)` read before the lock), Accumulating Wave (1200 px/650 ms, hit radius `70 + max(w,h)/2`, n-th enemy hit takes n).
- **Octopus**: Tentacle Loop (3 spins × 300 ms, damage 1 at the half-way point of each spin to enemies within `200 + max(w,h)/2`, no knockback); Bug Ink (blob 900 px/500 ms, 2 dmg, puddle radius 110 for 3 s that re-applies slow ×0.5 each frame; a miss still leaves a puddle at the end).
- **Electric Eel**: Magnetic Repulsion (fan ±60°, range 650/550 ms, 2 dmg no knockback, pushes 450 px/450 ms, enemy stunned meanwhile; edge or enemy ahead → +2 damage and stop); Laser Beam (instant, 650 px, width 36, hit if distance to the segment ≤ `18 + max(w,h)×0.4`, 3 dmg).

Measured with the scripted test (enemy centres 250/450/650 px to the right + one 150 px up, one at 900 px; all frozen, HP 100):
| Hero | Skill 1 damage per enemy [crab, jelly, turtle, squid, starfish] | Skill 2 |
|---|---|---|
| Swordfish | [3,3,0,3,0] (dash passes through) | [2,0,0,0,0] (stops at first) |
| Pufferfish | [2,2,2,2,0] (enlarged box) | [0,2,2,0,2] (enemy behind the mouth not hit) |
| Shark | [2,2,0,2,0] (standing still = 2) | [1,3,4,2,5] (1,2,3,4,5 in order met) |
| Octopus | [3,0,0,3,0] (radius ~300) | [2,0,0,0,0] + puddle |
| Electric Eel | [4,4,4,4,0] (2 + 2 collision) | [3,3,3,0,0] |

### 15.3 Port details worth knowing
- Player scaling: Android scales a View around its centre; the Phaser container is shifted to emulate that (`setPlayerScale`).
- `ctx.gs` is the battle scene handle for skills (never `ctx.scene`).
- Everything time-based is a battle "task" (`ctx.addTask`, `ctx.after`) so pause freezes all skills.
- Hero-select texts that are not in the Hero classes (role, subject, profile description) were taken from `strings.xml` / `MainActivity.selectHero`;
  skill names/descriptions come from the hero class (like Android). The old text in `strings.xml` (e.g. "Aqua Slash") is outdated and unused.

### 15.4 Not ported yet
- **Ultimates** (Circuit Link, Toxic Gulp, Blood Frenzy, Infinite Loop, Railgun), ULT button, stack gauge, quiz, ULT debuff / energy drain.
- Frenzy-dependent branches of Shark (bite stack, no-stack-gain) because Frenzy is the ultimate.
- Items, tutorial overlay, boss stage 5 (still the "coming soon" screen).
- Cosmetic simplifications: no ghost trails for the dash, simpler tentacle / wave / puddle art (the Android views are custom-drawn).
- Sounds on iOS Safari < 18.4 still need mp3/m4a copies.

---

## 16. Step 5 — stack gauge, quiz and ultimates (built)

Status: **the full Android combat loop for stages 1–4 is ported**: hit enemies → stack gauge fills → quiz → correct answer unlocks the ULT
button → ultimate. Verified in headless Edge (real time, DevTools protocol): quiz layout (normal + formula question), wrong / timeout /
correct paths, quiz blocked by the pause menu then opened on resume, stage end during a quiz, and one scripted test per ultimate (numbers below).
Not yet tested with real touch on a phone.

### 16.1 Files
| File | Role |
|---|---|
| `js/data/questions.js` | **100 questions copied from `QuestionBank.java` by a script** (5 subjects × 20; formula lines kept in the question text after `\nสูตร:`); `forSubject(subject)` picks the set |
| `js/ui/quiz.js` | port of `QuizManager`: overlay, 3-2-1 countdown, 30 s timer, shuffled deck, feedback 2 s, `dismiss()` |
| `js/scenes/BattleScene.js` | stack gauge logic, quiz listener, ULT unlock/use, ULT debuff + energy drain, ultimate HUD hooks (`BattleContext`) |
| `js/scenes/BattleUIScene.js` | stack box, ULT button + timer bar, quiz hookup, `L`/`U` key |
| `js/game/motion.js` | task-driven move/scale/fade for enemies and the player (used by Toxic Gulp and Infinite Loop) |
| `js/heroes/*.js` | `executeUltimateSkill()` added to every hero (+ Shark frenzy bonus, Octopus `holding`, Swordfish link damage sharing) |

### 16.2 Stack gauge (BattleActivity rules)
- `maxStack` = hero `stackNeeded` (Swordfish 20, Pufferfish 13, Shark 16, Octopus 20, Eel 20). Skills call `onHitEnemySuccess()` when they hit; it is ignored when the stage is over or no enemy is alive.
- Gain per hit = 1, or `stageConfig.ultGainFactor` while the **ULT debuff** is active; the fractional remainder is kept (`stackFraction`), `floor()` is added.
- **Debuff**: stages with `ultDebuffEveryHits > 0` (2: every 3 hits ×0.85; 3: every 2 ×0.65; 4: every 2 ×0.6): every N-th hit taken starts a 5000 ms debuff; the stage line shows `⚠ ULT -X%`.
- **Energy drain** (stage ≥ 3): every 3rd hit taken removes round(max × 20 %) from the gauge and clears the fraction, unless the ULT is ready/running or a quiz is open.
- Gauge shown top-centre as `<Hero> | Stack: cur/max` + bar (the static "Stack" label of the Android layout was dropped to fit the narrower canvas).
- Reset to 0: wrong answer / timeout, and when an ultimate finishes. Not gained during Circuit Link, Blood Frenzy, Infinite Loop hold.

### 16.3 Quiz
- Opens when the gauge becomes full (gate: stage running, an enemy alive, no pause/result overlay). The **battle pauses** (all game time stops) and the joystick resets.
  If the pause menu was open, the quiz opens right after "Resume" (`openPendingQuiz`).
- Title `"<subject> Quiz"` (subject = hero subject: Circuits, Chemistry, Calculus, Programming, Physics), **3-2-1** countdown (content hidden), then
  question (38 px bold) + formula line (30 px yellow) + **four 576×108 answer buttons** (32 px bold, whole button is the tap target, Thai text wrapped with `Intl.Segmenter`) + 30 s timer bar and `mm:ss`; `sfx_quiz_tick` each second of the last 10 s.
- Questions: shuffled deck, no repeats until all 20 were used; a new deck never starts with the last question of the old one.
- After an answer or timeout: correct option green (`#27AE60`), wrong pick red (`#C0392B`), feedback line (✅ ถูกต้อง! / ❌ ผิด! … / ⏰ หมดเวลา! …) for 2 s, then close:
  correct → `sfx_quiz_correct`, battle resumes, **ULT unlocked**; wrong/timeout → `sfx_quiz_wrong`, battle resumes, gauge = 0.
- Stage end (win/lose) while a quiz is open: closed immediately, no callbacks.

### 16.4 ULT button
- Above the skill buttons: locked = `🔒 ULT`, alpha 0.4; ready = `ULT!` bright with a pop; press only when ready, not already running, battle not paused (`sfx_ultimate`). Keyboard: **`L` or `U`**.
- While an ultimate is running (`ultimateRunning`) Skill 1/2 are blocked. Each ultimate calls `onUltimateFinished()` (gauge → 0, skills unlocked); Circuit Link, Blood Frenzy and Infinite Loop call it early (after starting / after the pull) so skills can be used during the effect. Timed ultimates show `"<Name> %d.%ds"` + bar above the button.

### 16.5 Ultimates (verified numbers)
| Hero | Ultimate | Behaviour as ported | Scripted test result |
|---|---|---|---|
| Swordfish | Circuit Link | all living enemies linked 5000 ms; damage to one linked enemy = same damage to all others (+ arc effect); no stack gain; skills usable | one 3-dmg hit → `[3,3,3,3,3]`; link ends after 5 s |
| Pufferfish | Toxic Gulp | aim circle r=380 (follows the fish, you can move); fires when ≥1 enemy inside AND ≥2 s aimed; expires after 6 s empty; inflate ×1.9 (300 ms), enemies pulled in 450 ms, **6 squeezes × 2 dmg every 500 ms**, spit out 240 px; movement locked | in-circle enemies took 12 each, outside 0, all freed |
| Shark | Blood Frenzy | 6000 ms, speed ×1.5, cooldown ×0.5, bite +1 damage per consecutive hit, miss resets, `+N` above the head, no stack gain | multipliers 1.5/0.5; bite damage 2 → then 2+1 → `bonus` 1, 2; miss → 0; all reset after 6 s |
| Octopus | Infinite Loop | all enemies pulled (600 ms) to a pile 150 px in front of the fish (clamped on screen), held 4000 ms, **8 squeezes × 1 dmg** (no knockback), AI frozen; skills unlock after the pull; no stack gain | all 5 held; 8 damage each; all freed |
| Electric Eel | Railgun | charge 1000 ms (cannot move, can aim, bar over head), one beam: width 110, full screen length, **8 damage + 2 s stun** (hit test `55 + max(w,h)·0.3`), fired once | enemies on the beam line took 8, others 0; lock released |

### 16.6 Not ported yet / notes
- **Items**, tutorial overlay, **boss stage 5** (and its minions) are still missing; the "ด่านถัดไป" after stage 4 opens the boss placeholder.
- Cosmetic simplifications: tentacle/grab/charge/frenzy visuals are simpler than Android's custom-drawn views.
- Dev helpers: `?stack=3` shrinks the gauge, `?bot=1` also answers the quiz correctly and fires the ULT; keys `L`/`U` = ULT.
- Sounds on iOS Safari < 18.4 still need mp3/m4a copies of the `.ogg` files.

---

## 17. Step 6 — boss stage (built)

Status: **stage 5 is playable with all 5 heroes.** Verified in headless Edge (real time): boss visuals + HUD, phase 1 and phase 2,
tail slam, damage bonus carry, victory screen, every hero vs the boss with the bot (no console errors), and the lock
(stage 5 stays locked until stage 4 has stars, then shows the boss icon).

### 17.1 Decision: the boss is the ONLY enemy
The request for this step says the boss stage has no other enemy bots. The Android version also spawns **5 minions** during life 1
(each minion's death costs the boss 10 HP). The web port follows the request: **no minions** (`BOSS_MINIONS = false` in `js/config.js`).
`BossMinion` is ported anyway; set the flag to `true` to get the Android behaviour back (minions on the left, removed silently when the boss enters phase 2, win = boss dead).
Consequence: the boss's life-1 HP (100) can only be removed by the player's own skills.

### 17.2 Files
| File | Role |
|---|---|
| `js/game/KrakenBoss.js` | port of `KrakenBoss` (stats, AI phases, telegraphs, 47-frame animation, size change, damage bonus) |
| `js/game/BossMinion.js` | port of `BossMinion` (disabled by default) |
| `js/game/SeaEnemy.js` | new hooks: `hpScale/damageScale/allowKnockback/reviveOnDepleted/onDefeated/livesLeft`, `removeSilently()`, `isBoss` |
| `js/scenes/BattleScene.js` | spawns the boss on stage 5 (`this.boss`), boss win rule, no more placeholder |
| `js/scenes/BattleUIScene.js` | big boss HP bar, stage line `ด่าน 5 \| บอสเหลือ N ชีวิต`, **special boss victory screen**; no "next stage" after stage 5 |
| `assets/img/boss/octo_idle_00..46.png` | the 47 animation frames (256×170, 2.2 MB), loaded as `boss_00..46` |

### 17.3 Boss (numbers from `KrakenBoss.java`)
- **2 lives**: life 1 HP **100**, life 2 (phase 2) HP **30**. HUD name `Kraken Boss ❤❤`.
- **Box**: image 65 × 8 = **520 px** wide (aspect 170/256 → 345 px tall), phase 2 ×2 (1040 × 691, never taller than 95 % of the screen); rest position x = 0.72·W − w/2, vertically centred; starts there. Boss physics box = image box (hit tests use it).
- **Immunities**: stun, slow, swallow and hit-knockback ignored. **Player damage ×1.15** with the fractional remainder carried (3 hits of 2 = 2 + 2 + 2, remainder 0.9 kept) — verified.
- **Wave attack** (IDLE → WAVE_WINDUP 500 ms → fire): bolts aimed at the player, fan with `i − floor(lines/2)` × spread. Life 1: 5 lines, 14° spread, 2 volleys (second volley re-aimed, 0.5 s later), speed 1700 px/s. Phase 2: 7 lines, 10°, 3 volleys, 2200 px/s. Damage per bolt `round(5 × damageMul 1.8)` = **9**; a bolt hit slows the player ×0.5 for 3 s. Cooldown 5000 ms (first wave after 2000 ms; after the phase change 1000 ms); in phase 2 the cooldown runs 1.5× faster. Red warning bands (width 60) fade in during the windup, `sfx_enemy_warn`.
- **Tail slam (phase 2 only)**: when the player is within `max(w,h)/2 + 120` px and the 5 s cooldown is over: red circle telegraph 600 ms, then **15 damage** + slow ×0.5/3 s if the player is still inside (stepping out in time avoids it). First slam possible 2 s after phase 2 starts.
- **Phase 2 movement**: creeps toward the player at 90 px/s while idle and farther than 0.8 × the slam radius.
- **Visuals**: 47-frame idle loop at 24 fps (×1.6 faster while charging a wave), breathing scale wobble ±3 %, rotation ±4° (violent shake while charging a wave), bolts like the jellyfish's.
- Win = boss dead: boss shrinks away, no death shockwave, no item drop.

### 17.4 HUD and victory screen
- **Boss bar**: 760 px wide, 32 px tall purple bar (`#AA00FF`) with name + hearts above and `hp/max` inside, directly under the stage line at the top centre.
- **Victory screen** (only when the boss dies): gold-framed panel "🏆 ปราบ Kraken Boss สำเร็จ!", "คุณคือผู้พิทักษ์ท้องทะเล!", stars, "ผ่านครบทุกด่านด้วย <hero>", falling confetti emojis, buttons เล่นอีกครั้ง / เลือกด่าน. Stars and `unlockNext` use the normal win path (stars by HP left; stage 5 is the last stage).

### 17.5 Lock system
Unchanged: stage 5 unlocks when stage 4 is won (stars on stage 4 ⇒ highest unlocked 5). Stage select shows the lock until then and the boss icon afterwards; locked taps show "Clear the previous stage first".

### 17.6 Deviations from Android (intentional)
- **No minions** (see 17.1).
- **The boss is not dragged around by hero skills**: Pufferfish *Toxic Gulp* and Octopus *Infinite Loop* still damage it but don't pull/scale it (Android moves its view to the pile), and Electric Eel *Magnetic Repulsion* damages it without pushing it 450 px.
- Boss damage text/flash uses the generic enemy effects; no custom boss hit animation.

### 17.7 Still not ported
Items, tutorial overlay, and the optional minion visuals beyond the flag. iOS Safari < 18.4 still needs mp3/m4a sounds.

---

## 18. Step 7 — sound, mute, reset, home-screen app, offline (built)

Verified in headless Edge: service worker registered + 136 files cached (incl. Phaser), **page loaded with the server stopped** (offline),
mute toggle (state saved, `game.sound.mute` follows), reset-progress dialog (cancel keeps data, confirm clears), WAV copies decode.
Not yet tested on a real iPhone (Add to Home Screen, install prompt, audio unlock).

### 18.1 Sound
- The Android project has **15 sound effects and no music**: `SoundManager.playMusic("bgm_menu"/"bgm_battle")` exists but `res/raw` has no music files, so the web port has no music either.
- All 15 effects load as `[.ogg, .wav]`: Chrome/Firefox/new Safari use the small `.ogg`; **Safari before 18.4 falls back to the `.wav`** copies (made from the `.ogg` files with the browser's decoder: mono, 44.1 kHz, 16-bit, 0.6 MB in total) — this fixes the old iOS silence problem.
- **Audio unlock** (`installAudioUnlock` in `js/core/audio.js`): the first touch / click / key press resumes the AudioContext and plays a silent buffer (iOS trick); it also resumes when the app comes back to the foreground.
- **Mute button** (`makeMuteButton`, 🔊/🔇): main menu, stage select, hero select, battle HUD (left of the pause button) and the pause menu. Saved in `sound_enabled`, mutes sounds that are already playing. The settings panel keeps its sound switch.

### 18.2 Reset progress
- New button **"🗑 รีเซ็ตความคืบหน้า" on the main menu** plus the one in the settings panel; both open the same confirmation dialog (`confirmResetDialog`: "รีเซ็ตความคืบหน้า? ดาทั้งหมดจะหาย … กู้คืนไม่ได้", ยกเลิก / รีเซ็ต). Reset clears all stars and the unlock (back to stage 1), keeps the sound setting, then shows a toast.

### 18.3 Home-screen app (PWA)
| File | Role |
|---|---|
| `manifest.json` | name "Fin & Fury", `display: fullscreen` (+ `display_override`), landscape, theme `#0A0E1A`, icons incl. maskable, relative `start_url` / `scope` (works under a GitHub Pages sub-path) |
| `icons/` | made from the game's boss emblem (`ic_boss.png`) on a dark-blue gradient: `apple-touch-icon.png` (180), `icon-152/167/192/512.png`, `icon-maskable-512.png`, `favicon-32.png` |
| `index.html` | `<link rel="manifest">`, apple-touch-icons, `apple-mobile-web-app-capable`, `-title`, `-status-bar-style black-translucent`, `theme-color`, `viewport-fit=cover` (safe-area insets already respected) |
- iPhone: Safari → Share → **Add to Home Screen**. Launched from the icon it opens **fullscreen without the address bar** (iOS standalone mode; the status bar is translucent over the game).

### 18.4 Service worker (offline + updates)
- `sw.js` precaches every game file (list in `sw-precache.js`, generated) + Phaser from cdnjs, **cache-first**; answers `Range` requests (iOS video) from the cache with proper 206 responses; unknown origins go straight to the network.
- **Cache version**: `self.CACHE_VERSION` in `sw-precache.js` → cache name `finfury-cache-v<N>`. **After changing any game file run**
  `powershell -ExecutionPolicy Bypass -File docs\tools\build-precache.ps1` (bumps N by 1 and regenerates the file list; `-Version 7` sets it), then publish.
  The new worker installs in the background, deletes old caches, takes over; the page reloads itself on the next menu screen (never during a battle).
- **Requirements/limits**: service workers need **https** (or localhost). Over plain `http://192.168.x.x` (the `serve.ps1` LAN test) there is **no** service worker and no real offline mode — publish to **GitHub Pages** (Settings → Pages → branch `main`, folder `/docs`) to get HTTPS; all paths are relative so the `/FinFury007/` sub-path works.
- Dev: on `localhost` the worker is **not** registered unless the URL has `?sw=1` (so development isn't served from a stale cache); `?sw=0` unregisters it and clears the caches.
- First visit needs a connection (about 12 MB: images, 2 videos, sounds, Phaser); afterwards the game starts from the cache and works offline.

### 18.5 Small fixes in this step
- Hero select: divider/skill rows moved down a little so a 4-line profile description no longer overlaps them.

---

## 19. Step 8 — final review (FINAL STATE)

**This section supersedes the "not ported yet" lists of the earlier sections.** Everything in the Android game that affects play is now in the web version.

### 19.1 Android vs web: what was missing and is now fixed
| Was missing | Now |
|---|---|
| **Items** (drops 25 %, random spawn 12–15 s, 7 types, buffs, pickup popup, buff text, boss life-lost heart) | `js/game/ItemManager.js` (same constants; Thai names/descriptions built from the constants), wired into the battle: shield absorbs a hit, speed ×1.3, cooldown ×0.5, +1 damage, freeze (enemies ×0.5), energy +2 stack, heal 15 %; HUD buff row `🛡️ 8s 💨 4s`; verified numerically |
| **Item guide in the pause menu** ("ไอเทมในเกม") | built from the item list; the pause panel is now two columns |
| **Tutorial overlay** (first time on stage 1, `tutorial_seen`) | copied text, battle paused until "เข้าใจแล้ว เริ่มเลย!" |

### 19.2 Remaining differences from Android (all intentional or cosmetic)
- **No minions on the boss stage** (requested); flag `BOSS_MINIONS` in `js/config.js` restores them. The boss is not pulled/pushed by Toxic Gulp / Infinite Loop / Magnetic Repulsion (it still takes their damage).
- **No music**: the Android project has none (only references to missing `bgm_*` files). Sound effects: 15, `.ogg` + `.wav` fallback.
- **Hit boxes**: Android uses real view rectangles; the web port uses estimated boxes (player 330×(36+image height), enemies 130×204), so the "feel" of collisions may differ slightly. Tune in `SeaEnemy.js` (`BOX_W/BOX_H`) and `BattleScene.createPlayer`.
- **Layout**: the HUD is arranged for a 1280×720 canvas; the stack box shows `Hero | Stack: n/max` like Android but without the extra static "Stack" label.
- **Cosmetic effects** (tentacles, trails, ghost images, dash streak, puddle art, boss hit animation) are simpler than Android's custom-drawn views; gameplay numbers are identical.
- **Controls added for desktop**: WASD/arrows, J/K skills, L/U ultimate, 1–4 answer the quiz, P/Esc pause.
- Android system Back button ↔ web: Esc / P (pause menu) and the on-screen back buttons.

### 19.3 iPhone Safari review
- **Touch / multi-touch**: joystick tracked by pointer id + separate skill/ULT buttons, 3 simultaneous pointers (`input.activePointers: 3`); quiz answer buttons are 576×108 canvas px; pinch/double-tap zoom, scrolling, long-press menu and pull-to-refresh are blocked (`touch-action:none`, fixed body, gesture/touchmove handlers). In normal Safari an edge-swipe from the left screen edge can still trigger "back"; as a home-screen app it can't.
- **Safe area / notch**: the game container is inset by `env(safe-area-inset-*)` with `viewport-fit=cover`; portrait shows the "Please rotate your phone" overlay.
- **Scaling**: fixed 1280×720 logical canvas with `Scale.FIT` (letter-boxed, centred). On a small phone (iPhone SE, 667×375 css px) the scale is about 0.52, so fonts were raised (HP 24, stage line 28, enemy names 32, skill labels 24, quiz 38/32, menus at least 24): smallest text is about 11–12 css px. The canvas renders at 1280×720 and is upscaled, so it is slightly soft on 3× screens: the price of keeping it fast.
- **Performance**: capped at 60 FPS (`fps.limit`; ProMotion phones run 120 Hz), `powerPreference: high-performance`; per-frame allocations removed from hot paths (task list compacted in place, no `filter()`/spread copies per frame, enemy hold-point array reused, cooldown text rewritten only when the shown value changes, HUD redrawn only on change). In headless Edge (software rendering) bot battles on stages 2–5 ran at **58–60 FPS**. **Still to verify on the phone.**
- **Audio**: unlock on first touch, `.wav` fallback for Safari < 18.4, mute button everywhere, auto-pause when the app is backgrounded.

### 19.4 GitHub Pages (`/FinFury007/`) readiness
- Every path is **relative** (`index.html`, `manifest.json` `start_url "./index.html"` / `scope "./"`, `sw.js` registered as `sw.js`, all asset URLs): no leading `/` anywhere (checked by script).
- **Case check** (script, exact-case comparison against the real file system): all 103 references (JS imports, `index.html`, `manifest.json`, CSS) match file/folder names exactly; all asset file names are lowercase; dynamic names (`hero_N`, `boss_NN`, `sfx_*`) are lowercase and load without a single 404 in the test runs.
- `docs/.nojekyll` added. Publish: Settings → Pages → branch `main`, folder `/docs` → `https://<user>.github.io/FinFury007/`.
- **Before every publish** run `powershell -ExecutionPolicy Bypass -File docs\tools\build-precache.ps1` (bumps the cache version so installed copies update). Current version: see `docs/sw-precache.js` (2).

### 19.5 Console check
Menu, stage select, hero select and battles (stage 1 tutorial; stage 2 Pufferfish, 3 Octopus, 4 Eel, 5 Shark and Swordfish with the bot; quiz, ultimates, items) were run in Edge with exceptions, console errors and HTTP/network failures captured: **no exceptions, no console errors, no failed requests.** The only message is Edge's "Tracking Prevention blocked access to storage for cdnjs…phaser.min.js" (a browser privacy notice about the CDN, not a game error).

### 19.6 Hand-test checklist for your iPhone
1. **Install**: open the https GitHub Pages URL in Safari → Share → *Add to Home Screen* → launch from the icon: fullscreen, no address bar, boss emblem icon, name "Fin & Fury".
2. **Rotate**: portrait → "Please rotate your phone"; landscape → game; try both landscape directions (notch left and right): HUD and joystick must not sit under the notch / home bar.
3. **Audio**: first tap anywhere → button sound; mute button (top-right) silences / restores; sounds still work after locking the phone and after switching apps and back.
4. **Videos**: menu and stage-select backgrounds play (muted); after backgrounding the app they resume.
5. **Offline**: open once with internet, then Airplane mode, fully close the app and reopen from the icon: it must start and play (menu, battle, sounds).
6. **Update**: after publishing a new version (run the precache script first) open the app online, close it, reopen: the new version appears (the page reloads itself once from a menu screen).
7. **Menus**: Start → stage select (only stage 1 open; tap a locked stage → "Clear the previous stage first") → hero select (5 heroes, FIGHT) → battle; back buttons everywhere; settings (sound switch); 🗑 reset (cancel keeps progress, confirm resets to stage 1).
8. **First battle (stage 1)**: tutorial appears once and the game stays paused until the button; joystick moves the fish; **move with the left thumb while pressing skills with the right thumb** (multi-touch); cooldown numbers show; HP, stack and enemy counter update.
9. **Every hero** (5) in at least one battle: skill 1, skill 2, fill the stack gauge → quiz.
10. **Quiz**: 3-2-1, readable question (also a calculus one with the yellow formula line), answer buttons easy to hit with a thumb; correct → ULT lights up, press it; wrong/timeout → gauge empties; the pause button must not open during the quiz.
11. **Ultimates**: Circuit Link, Toxic Gulp (aim circle), Blood Frenzy (timer bar), Infinite Loop, Railgun (charge bar).
12. **Items**: let some drop; pick up each type (popup readable and inside the screen); open pause → item list readable.
13. **Pause**: button, resume, restart, exit; switching to another app pauses the battle.
14. **Win/lose**: win screen with stars and "next stage" (next stage becomes unlocked); lose screen retry/back.
15. **Boss (stage 5)**: unlocks after beating stage 4; boss bar at the top; dodge red bands/circle; phase 2 (bigger boss, more bolts, tail slam); victory screen with confetti.
16. **Performance feel**: no stutter in busy fights (stage 4, ultimates, boss phase 2); phone not hot after a few minutes. If it stutters, tell me which stage and hero.
17. **Text sizes**: HUD text, enemy names, item list and hero-select descriptions comfortable to read? If not, tell me which and I'll enlarge them.
