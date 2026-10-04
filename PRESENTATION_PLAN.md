# FinFury — แบ่งงานพรีเซนต์ 5 คน

> ข้อสังเกตสำคัญ: โปรเจกต์จริงเขียนด้วย **Java** (ไม่ใช่ Kotlin) และเป็นเกม **Real-time + Quiz** ไม่ใช่ Turn-based
> ชื่อไฟล์ในร่างเดิม (TurnManager, GameController, DamageCalculator, AudioManager, FXManager, SaveManager, GameConfig)
> จึงไม่มีอยู่จริง ตารางด้านล่างแมปให้เป็นไฟล์ที่มีอยู่แล้ว เพื่อให้ตอบอาจารย์ได้ว่า "โค้ดจริงอยู่ตรงไหน"
> path โค้ดทั้งหมด: `app/src/main/java/com/example/finfury/`

## ตารางแมปชื่อในร่าง → ไฟล์จริง

| ร่างเดิม | ไฟล์จริง | หมายเหตุ |
|---|---|---|
| TurnManager / GameController | `BattleActivity.java` (ส่วน logic) + `BattleContext.java` + `QuizManager.java` | เกมวนด้วย game loop แทนเทิร์น |
| Hero + 5 ตัวละคร | `Hero.java`, `Swordfish/Pufferfish/Shark/Octopus/ElectricEel.java`, `HeroFactory.java` | ตรงตามร่าง |
| Enemy.kt | `SeaEnemy.java`, `BossMinion.java`, `KrakenBoss.java` | ย้ายไปให้คนที่ 4 (บอท) |
| DamageCalculator | ไม่มีคลาสแยก — ดาเมจอยู่ในสกิลแต่ละตัว + `damageMul` ใน `StageConfig` | ดูหัวข้อคนที่ 2 |
| AudioManager | `SoundManager.java` | SoundPool + MediaPlayer |
| FXManager | `HitEffects.java`, `SkillEffects.java`, `GhostPool.java` | shake / flash / เลขดาเมจ |
| SaveManager | `GameProgress.java` | SharedPreferences "GamePrefs" |
| GameConfig | `StageConfig.java` | ค่าความยากด่าน 1–5 |
| MainActivity / BattleActivity (View) | `MainActivity.java`, `SelectStageActivity.java`, `BattleActivity.java` (ส่วน UI), `res/layout/*` | |

---

## คนที่ 1 — Controller (ควบคุมการต่อสู้ + ควิซ)

**ไฟล์:** `BattleActivity.java` (ส่วน logic), `BattleContext.java`, `QuizManager.java`

| ต้องพรีเซนต์ | จุดในโค้ด |
|---|---|
| Game loop (แทน TurnManager) | `startGameLoop()`, `updateFish(dt)` |
| เริ่มด่าน (แทน startBattle) | `onCreate()` → `spawnStageEnemies()`, `spawnBossMinions()` |
| รับคำสั่งผู้เล่น (แทน processPlayerAction) | `canUseSkill()` + listener ปุ่ม Skill1/Skill2/ULT → `Hero.useSkill1/2()` |
| ผู้เล่นโดนตี | `damagePlayer(int)` |
| นับสแตก → ขึ้นควิซ → ปล่อย Ultimate | `onHitEnemySuccess()` → `QuizManager.show()` → `executeUltimateSkill()` → `onUltimateFinished()` |
| เช็กชนะ/แพ้ (แทน checkBattleEnd) | `onEnemyDefeated()`, `anyEnemyAlive()`, `damagePlayer()` เมื่อ HP หมด, `starsForRemainingHp()` |
| ตัวกลางประสานงาน | interface `BattleContext` — สกิลฮีโร่เรียกผ่านมัน ไม่รู้จัก Activity ตรงๆ |

**ทำอะไรได้:** วนเกมทุกเฟรม, สลับสถานะ (วิ่ง/ชน/แพ้/ชนะ), เชื่อมสกิล↔ศัตรู↔ควิซ, เซฟผลตอนชนะ
**ถ้าจะแก้:** เงื่อนไขชนะ/แพ้ → `onEnemyDefeated()` / `damagePlayer()`; เกณฑ์ดาว (70% / 35%) → `starsForRemainingHp()`; เวลาตอบควิซ/จำนวนข้อ → `QuizManager`; อยากให้สกิลทำอะไรเพิ่มกับฉาก → เพิ่มเมธอดใน `BattleContext` แล้ว implement ใน `BattleActivity`

**คำถามที่อาจารย์น่าจะถาม:** ทำไมใช้ interface BattleContext? (ตัดการผูกกับ Activity, ทดสอบ/แยกงานง่าย) · ควิซเชื่อมกับ Ultimate ยังไง? · แพ้/ชนะตัดสินตรงไหน?

---

## คนที่ 2 — Model (ฮีโร่ + ดาเมจ + ไอเทม)

**ไฟล์:** `Hero.java`, `Swordfish.java`, `Pufferfish.java`, `Shark.java`, `Octopus.java`, `ElectricEel.java`, `HeroFactory.java`, `ItemManager.java`

| ฮีโร่ | วิชา | HP | ความเร็ว | สแตกเต็ม | Skill1 / Skill2 / Ultimate |
|---|---|---|---|---|---|
| Swordfish | Circuits | 70 | 1.25 | 20 | Charge Bite / Discharge / Circuit Link |
| Pufferfish | Chemistry | 150 | 0.8 | 13 | Inflate / Venom Spray / Toxic Gulp |
| Shark | Calculus | 100 | 1.0 | 16 | Derivative Bite / Accumulating Wave / Blood Frenzy |
| Octopus | Programming | 115 | 0.95 | 20 | Tentacle Loop / Bug Ink / Infinite Loop |
| Electric Eel | Physics | 85 | 1.1 | 20 | Magnetic Repulsion / Laser Beam / Railgun |

**โค้ดทำอะไร:** `Hero` เป็นคลาสแม่ (abstract) มีค่าพื้นฐาน `getMaxHp()`, `getBaseSpeedMultiplier()`, `getStackNeeded()`, `getSkill1/2CooldownMs()` และเมธอดนามธรรม `useSkill1/2()`, `executeUltimateSkill()` — ลูกแต่ละตัว override (Polymorphism). `HeroFactory.createHero(id)` สร้างฮีโร่จาก id. `ItemManager` จัดดรอป/บัฟ (หัวใจ ความเร็ว คูลดาวน์ ดาเมจ โล่)
**เรื่อง DamageCalculator:** โปรเจกต์ไม่มีสูตร ATK−DEF; ดาเมจของสกิลกำหนดในแต่ละฮีโร่ และดาเมจศัตรูคูณด้วย `StageConfig.damageMul` (+20%/ด่าน) — พรีเซนต์ตามจริงนี้ ห้ามบอกว่ามี DamageCalculator
**ถ้าจะแก้:** ปรับค่าตัวละคร → override `getMaxHp()/getStackNeeded()/Cooldown` ในไฟล์ฮีโร่นั้น; เพิ่มฮีโร่ใหม่ → สร้างคลาส extends `Hero` + เพิ่ม case ใน `HeroFactory` + เพิ่มการ์ดใน `activity_main.xml`; เปลี่ยนชื่อ/ไอคอนสกิล → `getSkillXName/Icon/Description()`

**คำถามที่อาจารย์น่าจะถาม:** OOP ใช้ตรงไหน? (abstract class + override) · ฮีโร่ตัวไหนเก่งสุด/ทำไมบาลานซ์แบบนี้ · เพิ่มฮีโร่ตัวที่ 6 ต้องแก้กี่ไฟล์?

---

## คนที่ 3 — View (UI + เปลี่ยนหน้า)

**ไฟล์:** `MainActivity.java`, `SelectStageActivity.java`, `BaseActivity.java`, `SettingsActivity.java`, `BattleActivity.java` (ส่วน UI), `res/layout/activity_*.xml`

| ต้องพรีเซนต์ | จุดในโค้ด |
|---|---|
| สลับ 3 หน้า Menu → Level Select → Hero Select | `MainActivity.onCreate()`, `backFromHeroSelect()`, `backFromLevelSelect()` |
| ปุ่มเริ่ม / ด่าน 1–5 / การ์ดฮีโร่ / ปุ่ม Fight | listener ใน `MainActivity` (`selectHero()`, ส่ง `HERO_ID`,`STAGE_ID` ผ่าน Intent ที่บรรทัด ~209) |
| ล็อกด่าน + ดาวบนไอคอน | `updateStageLocks()`, `updateStageStars()` |
| วิดีโอพื้นหลังเมนู | `onPause/onResume`, `reloadVideo()` |
| หลอด HP ผู้เล่น/ศัตรู | `updateHpUI()`, `updateStageInfo()`, `updatePlayerOverlays()` |
| ปุ่ม Skill + คูลดาวน์ | `startCooldownUI()` |
| หน้าสอนเล่น / ผลชนะ-แพ้ / pause | `showTutorialIfFirstTime()`, `setupOverlays()`, `showResultOverlay()` |

**ทำอะไรได้:** แสดงผลทุกหน้าจอและรับการกดปุ่ม ส่งข้อมูลระหว่างหน้าด้วย Intent
**ถ้าจะแก้:** หน้าตา → `res/layout/*.xml`; พฤติกรรมปุ่ม → listener ใน Activity ที่เกี่ยวข้อง; ภาพฮีโร่/ขนาดในฉากสู้ → `setupHeroAndSkills()`; พื้นหลังด่าน → `setupStageBackground()`
**หมายเหตุ:** ร่างเดิมมี "Action Log" ข้อความ — โปรเจกต์ใช้ **ตัวเลขดาเมจเด้ง** (`HitEffects.damagePopup`) แทน ให้บอกตามจริง ถ้าอาจารย์อยากได้ Action Log ต้องเพิ่มเอง

**คำถามที่อาจารย์น่าจะถาม:** Intent ส่งอะไรบ้างระหว่างหน้า? · ล็อก/ปลดล็อกด่านอัปเดตตอนไหน? (`onResume`) · เปลี่ยนปุ่มต้องแก้ XML หรือ Java?

---

## คนที่ 4 — Audio & FX + บอท (ศัตรูอัตโนมัติ)

**ไฟล์ Audio/FX:** `SoundManager.java`, `HitEffects.java`, `SkillEffects.java`, `GhostPool.java`
**ไฟล์บอท:** `SeaEnemy.java`, `BossMinion.java`, `KrakenBoss.java`, `RangedAttacks.java`

**Audio — `SoundManager`:** `init()` โหลด SoundPool, `play(Sfx)` เสียงสั้น (กดปุ่ม/สกิล/โดนตี/ควิซ ฯลฯ — enum `Sfx` ผูกกับไฟล์ใน `res/raw`), `playMusic/pauseMusic/resumeMusic/stopMusic` เพลงพื้นหลังวนลูปด้วย MediaPlayer, `setSoundEnabled()` เปิด/ปิดเสียงจากหน้า Settings. ไฟล์เสียงหายไม่ crash แค่เงียบ
**FX — `HitEffects`:** `impact()`/`playerHit()` = เลขดาเมจเด้ง + สะเก็ด + จอสั่น (`shake`) + จอแดงวาบ (`flashRed`); `GhostPool` = object pool ลดการสร้าง View ซ้ำ (ประสิทธิภาพ)
**บอท — `SeaEnemy.updateAI()`:** state machine 3 สถานะ `ORBIT_AND_WAIT → WINDUP (เตือน 500ms แสดงเส้นทาง) → ATTACKING`; ท่าเคลื่อนที่ต่างกันต่อชนิด (ORBIT/SIDESTEP/DRIFT/PATROL/KITE/ERRATIC); ท่าโจมตี DASH / LINE_SHOT (แมงกะพรุน) / INK_CONE (หมึก, `RangedAttacks.inkCone`); ศัตรูผลัดกันโจมตีด้วยระบบ attack slot (`claimAttackSlot`, `setDualAttack`); โดนตีแล้วถอย+ขัดจังหวะการโจมตี. `KrakenBoss` = บอสด่าน 5 มี 2 เฟส (`enterPhase2`), ท่า WAVE/TAIL พร้อมวงเตือน, ฟื้นชีพ (`reviveOnDepleted`), เรียก `BossMinion`
**ถ้าจะแก้:** เพิ่ม/ปรับเสียง → ใส่ไฟล์ใน `res/raw` + เพิ่มค่า `Sfx`; แรงสั่นจอ → `HitEffects.shake` amplitude; พฤติกรรมศัตรู → `updateAI()`/`MoveStyle`; เวลาเตือนก่อนโจมตี → `WINDUP_MS`; ชนิดศัตรูใหม่ → เพิ่ม branch ใน `configureEnemyStats()` (ตัวเลขพลังศัตรูให้คนที่ 5 เป็นคนอธิบาย)

**คำถามที่อาจารย์น่าจะถาม:** บอทตัดสินใจยังไง? (state machine) · ทำไมมี WINDUP? (ให้ผู้เล่นหลบได้ — แฟร์) · ทำไมต้องมี pool? · เสียงสั้น/ยาวใช้ API ต่างกันทำไม?

---

## คนที่ 5 — Data & Balance (เซฟ + ความยาก + คลังคำถาม)

**ไฟล์:** `GameProgress.java`, `StageConfig.java`, `QuestionBank.java`

**เซฟ — `GameProgress`** (SharedPreferences "GamePrefs"): `saveBestStars()` เซฟเฉพาะเมื่อดีกว่าเดิม, `getStars()`, `getUnlockedStage()` (อ่านค่า `unlocked_stage` + สำรองจากดาวที่มี), `reset()` ล้างความคืบหน้า, `starsText()` แปลงเป็น ★★☆
**ความยาก — `StageConfig.forStage(n)`:**

| ด่าน | ศัตรู | speed | hp | โจมตีทุก (ms) | dual | ดีบัฟ ULT | ดาเมจ x |
|---|---|---|---|---|---|---|---|
| 1 | 5 | 1.10 | 1.15 | 3000 | ✗ | – | 1.0 |
| 2 | 5 | 1.20 | 1.40 | ~2143 | ✗ | ทุก 3 ฮิต (×0.85) | 1.2 |
| 3 | 5 | 1.30 | 1.65 | ~1531 | ✓ | ทุก 2 ฮิต (×0.65) | 1.4 |
| 4 | 5 (ไม่มีหมึก) | 1.40 | 1.95 | 1300 (ขั้นต่ำ) | ✓ | ทุก 2 ฮิต (×0.6) | 1.6 |
| 5 | Boss 1 | 1.0 | 1.0 | 3000 | ✗ | – | 1.8 |

ความถี่โจมตีเร็วขึ้น ×1.4 ต่อด่าน (`ATTACK_SPEED_PER_STAGE`) มีขั้นต่ำ `MIN_ATTACK_INTERVAL_MS`; ดาเมจ +20%/ด่าน. ค่าเลือดฐานของศัตรูแต่ละชนิด (ปู 16, หมึก 12, เต่า 18, แมงกะพรุน 9, ดาวทะเล 11) อยู่ใน `SeaEnemy.configureEnemyStats()`
**QuestionBank:** คลังคำถามแยกตามวิชาของฮีโร่ (`forSubject()` → calculus/circuit/chemistry/programming/physics) ใช้เพิ่มคำถามผ่าน `add(...)`
**ถ้าจะแก้:** ทำด่านง่าย/ยากขึ้น → ปรับตัวเลขใน `forStage()`; เพิ่มคำถาม → `QuestionBank.xxxQuestions()`; เปลี่ยนเกณฑ์ดาว → `BattleActivity.starsForRemainingHp()`; ล้างเซฟตอนสาธิต → `GameProgress.reset()` (ปุ่มใน Settings)
**หมายเหตุ:** ร่างเดิมระบุ "ด่าน 1 HP 100 / ATK 15, บอส HP 1200 / ATK 85" — ในโปรเจกต์จริงใช้ตัวคูณ (`hpMul`, `damageMul`) ไม่ใช่ค่าตายตัว ให้พรีเซนต์ตามตารางด้านบน

**คำถามที่อาจารย์น่าจะถาม:** SharedPreferences คืออะไร/ข้อมูลหายเมื่อไหร่? · ทำไมความยากใช้ตัวคูณ? · ปลดล็อกด่านถัดไปทำงานยังไง?

---

## แบ่งเวลาแนะนำ (ประมาณ 15–20 นาที)

1. คนที่ 3 — เดโมเกม (เมนู → เลือกด่าน → เลือกฮีโร่) 3 นาที
2. คนที่ 2 — ฮีโร่ 5 ตัว + OOP 3 นาที
3. คนที่ 1 — Game loop / Quiz / แพ้-ชนะ 4 นาที
4. คนที่ 4 — บอท + เสียง/เอฟเฟกต์ 4 นาที
5. คนที่ 5 — เซฟ + ตารางความยาก + Q&A 3 นาที

## เช็กลิสต์ก่อนพรีเซนต์ (ทุกคน)
- เปิดไฟล์ของตัวเองได้ทันที และอธิบายได้ว่า "ฟังก์ชันหลัก 3 ตัวทำอะไร"
- ลองแก้ค่า 1 จุดแล้วรันให้ดู (เช่น คนที่ 5 เปลี่ยน `hpMul`, คนที่ 2 เปลี่ยน `getMaxHp()`)
- รู้ว่าไฟล์ตัวเองเรียก/ถูกเรียกโดยไฟล์ของใคร (ดูส่วน "ตัวกลาง" ของคนที่ 1)
