# คู่มือโค้ด FinFury007 แยกรายคน (พร้อมคอมเมนต์ละเอียด)

เอกสารนี้บอกว่า **ไฟล์โค้ดแต่ละไฟล์เป็นของใคร อยู่ตรงไหน** และมีสำเนาที่ใส่คอมเมนต์อธิบายละเอียดให้แล้ว
การแบ่งงานทำตามเอกสาร "FinFury: แบ่งหน้าที่โค้ดและคู่มือพรีเซ้น 5 คน" (Java 26 ไฟล์ ในแพ็กเกจ `com.example.finfury` + layout XML 4 ไฟล์)

## ข้อควรรู้ก่อนอ่าน

- **ไฟล์ในโฟลเดอร์นี้เป็น "สำเนา"** ไม่ใช่ไฟล์ที่เกมใช้ build ไฟล์จริงยังอยู่ที่ `app/src/main/...` และ **ไม่ได้ถูกแก้เลย** (เกม build เหมือนเดิม)
- สำเนาทุกไฟล์ **โค้ดเหมือนต้นฉบับทุกตัวอักษร** ต่างกันแค่มีคอมเมนต์เพิ่ม ตรวจได้เองด้วยคำสั่ง `python3 tools/verify_all.py` (รันที่โฟลเดอร์ `team-guide`)
- เลขบรรทัดที่เขียนในคู่มือนี้ (คอลัมน์ "บรรทัดในไฟล์จริง") **อ้างอิงไฟล์จริงใน `app/src/main`** ส่วนในสำเนาเลขบรรทัดจะเลื่อนเพราะมีคอมเมนต์แทรก ให้ค้นหาด้วยชื่อเมธอดแทน (Ctrl+F)
- ถ้าอยากเอาคอมเมนต์ไปใส่ในโค้ดจริง ให้คัดลอกไฟล์จากสำเนาไปทับไฟล์จริงได้เลย (ปลอดภัย เพราะโค้ดเหมือนกัน)

### ป้ายในคอมเมนต์ที่ผมเพิ่มให้ (ค้นหาด้วย Ctrl+F)

| ป้าย | ความหมาย |
|---|---|
| `[ไฟล์นี้คืออะไร]` | ภาพรวมของไฟล์ ผังไฟล์ ใครเรียกใช้ (อยู่หัวไฟล์ทุกไฟล์) |
| `[xxx = ...]` | อธิบายตัวแปร/เมธอด/คลาสนั้นว่าคืออะไรและทำอะไร |
| `[แก้ยังไง]` | ถ้าอยากปรับค่า/พฤติกรรม ให้แก้ตรงไหน |
| `[เพิ่มยังไง]` / `[จะเพิ่ม...ยังไง]` | ขั้นตอนเพิ่มของใหม่ (ฮีโร่ ด่าน โจทย์ ไอเทม ปุ่ม ฯลฯ) |
| `[ระวัง]` / `[ข้อสังเกต]` | จุดที่พลาดง่าย หรือสิ่งที่พบว่าโค้ดกับคอมเมนต์เดิมไม่ตรงกัน |

คอมเมนต์เดิมของเจ้าของโค้ดยังอยู่ครบ ผมเพิ่มเฉพาะส่วนใหม่

---

## สรุปการแบ่งงาน

| คน | บทบาท | โฟลเดอร์ในคู่มือ | ไฟล์ที่รับผิดชอบ | บรรทัดจริง (ประมาณ) |
|---|---|---|---|---|
| 1 | Controller: คุมฉากต่อสู้และกติกา | [`person1_controller/`](person1_controller) | `BattleActivity.java` (ส่วนกติกา), `BattleContext.java` | 830 |
| 2 | Model: ฮีโร่และสกิล | [`person2_model/`](person2_model) | `Hero`, `HeroFactory`, `Swordfish`, `Pufferfish`, `Shark`, `Octopus`, `ElectricEel`, `SkillEffects` | 3,050 |
| 3 | View: หน้าจอและการเปลี่ยนหน้า | [`person3_view/`](person3_view) | `MainActivity`, `SelectStageActivity`, `SettingsActivity`, `BaseActivity`, `BattleActivity.java` (ส่วน HUD/overlay) + layout XML 4 ไฟล์ | Java ~990 + XML 1,740 |
| 4 | บอท + เสียง + เอฟเฟกต์ | [`person4_bots_sound_fx/`](person4_bots_sound_fx) | `SeaEnemy`, `KrakenBoss`, `BossMinion`, `RangedAttacks`, `SoundManager`, `HitEffects`, `GhostPool` | 2,610 |
| 5 | ข้อมูล + ควิซ + สมดุลเกม | [`person5_data_quiz_items/`](person5_data_quiz_items) | `GameProgress`, `StageConfig`, `QuizManager`, `QuestionBank`, `ItemManager` | 1,040 |

`BattleActivity.java` เป็นไฟล์เดียวที่ **แบ่งกัน 2 คน (คนที่ 1 กับคนที่ 3) ตามฟังก์ชัน** ดูตารางแยกด้านล่าง

ลำดับพรีเซ้นที่เอกสารแบ่งงานแนะนำ: 3 → 1 → 2 → 4 → 5 (ตามลำดับที่ผู้เล่นเจอ: เมนู → ฉากต่อสู้ → สกิลฮีโร่ → ศัตรู → ควิซ/ไอเทม/เซฟ)

---

## รายละเอียดรายคน

พาธ "ไฟล์จริง" คือใต้ `app/src/main/java/com/example/finfury/` (Java) หรือ `app/src/main/res/layout/` (XML)

### คนที่ 1 — Controller (คุมฉากต่อสู้และกติกา)

| สำเนาที่คอมเมนต์แล้ว | ไฟล์จริง | บรรทัดจริง | คืออะไร |
|---|---|---|---|
| [`BattleContext.java`](person1_controller/BattleContext.java) | `BattleContext.java` | 81 | interface 26 เมธอดที่ฮีโร่/ศัตรูเรียกฉากต่อสู้ |
| [`BattleActivity_P1_rules.java`](person1_controller/BattleActivity_P1_rules.java) | `BattleActivity.java` (เฉพาะส่วนของคนที่ 1) | ~745 จาก 1,135 | game loop จอยสติ๊ก ปุ่มสกิล สแตก รับดาเมจ ชนะ/แพ้ quiz↔Ultimate |
| โค้ดเต็มของ `BattleActivity` (ทั้ง 2 คน) | [`shared_BattleActivity/BattleActivity.java`](shared_BattleActivity/BattleActivity.java) | 1,135 | ทุกส่วนมีป้าย `//@@P1` / `//@@P3` บอกเจ้าของ |

### คนที่ 2 — Model (ฮีโร่และสกิล)

| สำเนาที่คอมเมนต์แล้ว | บรรทัดจริง | คืออะไร |
|---|---|---|
| [`Hero.java`](person2_model/Hero.java) | 60 | คลาสแม่ (abstract) ของฮีโร่ทุกตัว |
| [`HeroFactory.java`](person2_model/HeroFactory.java) | 18 | โรงงานสร้างฮีโร่จากเลข 1-5 |
| [`Swordfish.java`](person2_model/Swordfish.java) | 840 | ฮีโร่ 1 วิชาวงจรไฟฟ้า: พุ่งกัด/คลื่นไฟฟ้า/ระบบประจุ/Circuit Link |
| [`Pufferfish.java`](person2_model/Pufferfish.java) | 315 | ฮีโร่ 2 วิชาเคมี: พองตัว/พ่นพิษ/Toxic Gulp ดูดศัตรู |
| [`Shark.java`](person2_model/Shark.java) | 422 | ฮีโร่ 3 วิชาแคลคูลัส: กัดตามความเร็ว/คลื่นสะสม/Blood Frenzy |
| [`Octopus.java`](person2_model/Octopus.java) | 571 | ฮีโร่ 4 วิชาโปรแกรม: หวดหนวด/แอ่งบั๊ก/Infinite Loop |
| [`ElectricEel.java`](person2_model/ElectricEel.java) | 510 | ฮีโร่ 5 วิชาฟิสิกส์: คลื่นแม่เหล็ก/เลเซอร์/Railgun |
| [`SkillEffects.java`](person2_model/SkillEffects.java) | 317 | ชิ้นส่วนสกิลสำเร็จรูป (dash/inflate/venomSpray/projectile) |

### คนที่ 3 — View (หน้าจอและการเปลี่ยนหน้า)

| สำเนาที่คอมเมนต์แล้ว | ไฟล์จริง | บรรทัดจริง | คืออะไร |
|---|---|---|---|
| [`MainActivity.java`](person3_view/MainActivity.java) | `MainActivity.java` | 367 | หน้าแรก 3 หน้าใน Activity เดียว (เมนู/เลือกด่าน/เลือกฮีโร่) |
| [`BattleActivity_P3_hud.java`](person3_view/BattleActivity_P3_hud.java) | `BattleActivity.java` (เฉพาะส่วนของคนที่ 3) | ~390 | HUD หลอดเลือด/สแตก ปุ่ม ULT เมนูหยุด สอนเล่น หน้าชนะ/แพ้ |
| [`SettingsActivity.java`](person3_view/SettingsActivity.java) | `SettingsActivity.java` | 40 | หน้าตั้งค่า เสียง/รีเซ็ต |
| [`BaseActivity.java`](person3_view/BaseActivity.java) | `BaseActivity.java` | 27 | Activity แม่ ซ่อนแถบระบบ (เต็มจอ) |
| [`SelectStageActivity.java`](person3_view/SelectStageActivity.java) | `SelectStageActivity.java` | 168 | หน้าเลือกด่านแบบแยก (เวอร์ชันเก่า ดูข้อสังเกตด้านล่าง) |
| [`activity_main.xml`](person3_view/activity_main.xml) | `res/layout/activity_main.xml` | 729 | layout 3 หน้าของ MainActivity |
| [`activity_battle.xml`](person3_view/activity_battle.xml) | `res/layout/activity_battle.xml` | 817 | layout ฉากต่อสู้ + overlay quiz/หยุด/สอนเล่น/ผลลัพธ์ |
| [`activity_select_stage.xml`](person3_view/activity_select_stage.xml) | `res/layout/activity_select_stage.xml` | 127 | layout หน้าเลือกด่านแบบแยก |
| [`activity_settings.xml`](person3_view/activity_settings.xml) | `res/layout/activity_settings.xml` | 67 | layout หน้าตั้งค่า |

### คนที่ 4 — บอท + เสียง + เอฟเฟกต์

| สำเนาที่คอมเมนต์แล้ว | บรรทัดจริง | คืออะไร |
|---|---|---|
| [`SeaEnemy.java`](person4_bots_sound_fx/SeaEnemy.java) | 948 | ศัตรูธรรมดา 5 ชนิด AI 3 สถานะ ระบบคิวโจมตี รับดาเมจ |
| [`KrakenBoss.java`](person4_bots_sound_fx/KrakenBoss.java) | 476 | บอสด่าน 5 สองเฟส คลื่นพลัง หางฟาด |
| [`BossMinion.java`](person4_bots_sound_fx/BossMinion.java) | 43 | ลูกน้องบอส (ตายแล้วบอสเสียเลือด 10) |
| [`RangedAttacks.java`](person4_bots_sound_fx/RangedAttacks.java) | 275 | โจมตีระยะไกล: ลำพลังแมงกะพรุน/กรวยหมึก |
| [`SoundManager.java`](person4_bots_sound_fx/SoundManager.java) | 251 | เสียงเอฟเฟกต์ + เพลง + เปิด/ปิดเสียง |
| [`HitEffects.java`](person4_bots_sound_fx/HitEffects.java) | 481 | วงกระแทก ตัวเลขดาเมจ จอสั่น แฟลชแดง แถบเตือนเส้นทาง |
| [`GhostPool.java`](person4_bots_sound_fx/GhostPool.java) | 133 | กองวงกลมเงา/ประกาย/ละอองที่ใช้ซ้ำ |

### คนที่ 5 — ข้อมูล + ควิซ + สมดุลเกม

| สำเนาที่คอมเมนต์แล้ว | บรรทัดจริง | คืออะไร |
|---|---|---|
| [`GameProgress.java`](person5_data_quiz_items/GameProgress.java) | 60 | เซฟดาวและการปลดล็อกด่าน (SharedPreferences) |
| [`StageConfig.java`](person5_data_quiz_items/StageConfig.java) | 59 | ตารางความยาก 5 ด่าน |
| [`QuizManager.java`](person5_data_quiz_items/QuizManager.java) | 353 | หน้าต่างโจทย์: นับ 3-2-1, จับเวลา 30 วินาที, เฉลย |
| [`QuestionBank.java`](person5_data_quiz_items/QuestionBank.java) | 206 | คลังโจทย์ 5 วิชา x 20 ข้อ (วิธีเพิ่มโจทย์อยู่หัวไฟล์) |
| [`ItemManager.java`](person5_data_quiz_items/ItemManager.java) | 360 | ไอเทม 7 ชนิด ดรอป/สุ่มเกิด/บัฟ |

---

## `BattleActivity.java` แบ่งกัน 2 คนอย่างไร

สำเนาแยกคือ [`person1_controller/BattleActivity_P1_rules.java`](person1_controller/BattleActivity_P1_rules.java) และ [`person3_view/BattleActivity_P3_hud.java`](person3_view/BattleActivity_P3_hud.java)
(ไฟล์ทั้งสองสร้างอัตโนมัติจาก [`shared_BattleActivity/BattleActivity.java`](shared_BattleActivity/BattleActivity.java) ด้วย `tools/split_battleactivity.py`)

ฟังก์ชันที่ไม่อยู่ในเอกสารแบ่งงานเดิม ผมจัดตามหน้าที่ (ถ้าไม่เห็นด้วยแก้ป้าย `//@@P1`/`//@@P3` ในไฟล์ shared แล้วรัน script ใหม่)

| เจ้าของ | ฟังก์ชัน / ส่วน (บรรทัดในไฟล์จริง) |
|---|---|
| **คนที่ 1** | ฟิลด์ตัวแปรเกม (31-102) · เมธอด BattleContext: `getContext`…`setCooldownMultiplier` (107-149), `isGameRunning`…`freezeEnemies` (246-312) · `updateItems` (314) · `onUltimateFinished` (326) · `onCreate` (337) · `resetJoystick` (480) · `canUseSkill` (490) · game loop `frameCallback` (494) · `startGameLoop` (530) · `updateFish` (534) · `spawnBossMinions` (599) · `spawnStageEnemies` (613) · `startCooldownUI` (637) · `onHitEnemySuccess` (654) · `anyEnemyAlive` (670) · `damagePlayer` (678) · `starsForRemainingHp` (772) · `openPendingQuiz` (860) · `canOpenQuiz` (867) · `cancelEverythingForStageEnd` (880) · `restartStage` (897) · `checkWinCondition` (964) · `setupQuiz` (986) · `resetStackAfterQuiz` (1036) · `onResume`/`onPause`/`onDestroy` (1110-1135) |
| **คนที่ 3** | HUD เหนือหัว/หลอด Ult: ฟิลด์ (152-158), `showUltimateDuration`…`updateUltimateTimerUI` (160-205), `setPlayerBonusDamage`…`updatePlayerOverlays` (207-244) · `setupStageBackground` (728) · `updateHpUI` (741) · `updateStageInfo` (748) · `showTutorialIfFirstTime` (782) · `buildItemGuide` (798) · `setupOverlays` (816) · `isOverlayOpen` (836) · `openPauseMenu` (843) · `closePauseMenu` (850) · `showResultOverlay` (901) · `setResultButton` (942) · `goToNextStage` (948) · `returnToLevelSelect` (956) · `updateStackUI` (1043) · `updateUltimateButton` (1052) · `setupHeroAndSkills` (1072) · `animateButton` (1102) |

---

## ข้อสังเกตที่พบระหว่างอ่านโค้ด (คอมเมนต์เดิมกับโค้ดจริงไม่ตรงกัน หรือของที่ยังไม่ได้ใช้)

เผื่อกรรมการ/อาจารย์ถาม เจ้าของไฟล์นั้นควรรู้ไว้ ผมไม่ได้แก้อะไรในโค้ดจริง

1. **`SelectStageActivity` (คนที่ 3)** ลงทะเบียนใน `AndroidManifest.xml` แล้ว แต่ไม่มีโค้ดส่วนไหนเปิดหน้านี้ หน้าเลือกด่านที่ใช้จริงอยู่ใน `MainActivity` (`layoutLevelSelect`)
2. **`unlocked_stage` (คนที่ 5)** `GameProgress.getUnlockedStage` อ่านคีย์นี้ แต่ไม่มีโค้ดเขียน (คอมเมนต์บอกว่า BattleActivity บันทึก) การปลดล็อกจึงมาจากดาวอย่างเดียว ซึ่งทำงานถูกต้อง
3. **ไม่มีไฟล์เพลง `bgm_menu` และ `bgm_battle` (คนที่ 4)** ใน `res/raw` โค้ด `SoundManager.playMusic` จึงเงียบเฉย ๆ (ไม่ crash) มีเฉพาะเสียงเอฟเฟกต์ `sfx_*`
4. **ข้อความสอนเล่นบอก "สแตกเต็ม 10" (คนที่ 3)** แต่จำนวนสแตกที่ต้องใช้จริงต่างกันตามฮีโร่: Pufferfish 13, Shark 16, Swordfish/Octopus/Electric Eel 20 (ค่าเริ่มต้นใน `Hero` คือ 10)
5. **`StageConfig.includeSquid` (คนที่ 5)** คอมเมนต์บอกว่า "false = ไม่มีหมึก (ด่าน 4)" แต่ทุกด่านตั้งเป็น `true` ในโค้ดปัจจุบัน
6. **`ULT_DEBUFF_DURATION_MS` (คนที่ 1)** คอมเมนต์เดิมบอก 8 วินาที แต่ค่าในโค้ดคือ 5000 ms (5 วินาที)
7. **คอมเมนต์ `ElectricEel.useSkill2` (คนที่ 2)** ยังเขียนชื่อเก่า "Particle Accelerator Shot" ทั้งที่สกิลคือ Laser Beam (ยิงทันที ไม่ใช่ "ยิ่งไกลยิ่งเร็ว")
8. **id `btnBack` ใน `activity_battle.xml` (คนที่ 3)** ชื่อเป็น Back แต่ทำหน้าที่ "หยุดเกม" (เปิดเมนูหยุด)
9. **`SkillEffects.dash` และ `projectile` (คนที่ 2)** ยังไม่มีฮีโร่ตัวไหนเรียกใช้ (มีแต่ `inflate` กับ `venomSpray` ที่ Pufferfish ใช้)
10. โฟลเดอร์ `docs/` ใน repo เป็นเวอร์ชันเว็บ (JavaScript) แยกต่างหาก **ไม่รวม** ในการแบ่งงาน 5 คนและคู่มือนี้

---

## เครื่องมือในโฟลเดอร์ `tools/` (ไว้ทำซ้ำ/ตรวจ)

| ไฟล์ | ทำอะไร |
|---|---|
| `verify_all.py` | ตรวจว่าทุกสำเนา "โค้ดเหมือนต้นฉบับ" และไม่มีไฟล์ต้นฉบับตกหล่น |
| `verify_same_code.py` | ตรวจทีละไฟล์ (ตัดคอมเมนต์ออกทั้งสองฝั่งแล้วเทียบ) |
| `annotate.py` | แทรกคอมเมนต์จากไฟล์บันทึก `_notes/*.notes` ลงไฟล์ต้นฉบับ แล้วตรวจอัตโนมัติ |
| `split_battleactivity.py` | แยก `shared_BattleActivity/BattleActivity.java` เป็นไฟล์ของคนที่ 1 กับคนที่ 3 |

โฟลเดอร์ `_notes/` เก็บ "บันทึกคอมเมนต์" ต้นทางของแต่ละไฟล์ (รูปแบบ `@@ เลขบรรทัด | ข้อความตรวจ` แล้วตามด้วยข้อความคอมเมนต์)
**ถ้าจะแก้คอมเมนต์** แก้ในไฟล์ `.notes` แล้วรัน
`python3 tools/annotate.py ../app/src/main/java/com/example/finfury/ชื่อไฟล์.java _notes/ชื่อไฟล์.notes personN_xxx/ชื่อไฟล์.java`
(เฉพาะ `Hero`, `HeroFactory`, `BattleContext` และ `BattleActivity` เขียนคอมเมนต์ลงไฟล์สำเนาโดยตรง ไม่มี `.notes`)
