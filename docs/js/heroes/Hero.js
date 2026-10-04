// Port of Hero.java (base class). Skills receive the battle "ctx" (the BattleScene).
// Ultimates / stack / quiz are not ported yet, so only stats + skill 1 + skill 2 + texts live here for now.
export default class Hero {
  constructor(id, name, subject) {
    this.id = id;
    this.name = name;
    this.subject = subject;
  }

  // ---- stats (heroes override) ----
  getMaxHp() { return 100; }
  getBaseSpeedMultiplier() { return 1; }
  getStackNeeded() { return 10; }
  getSkill1CooldownMs() { return 500; }
  getSkill2CooldownMs() { return 500; }
  usesUltimateButton() { return false; }

  /** Ultimate (after a correct quiz answer + ULT press). Must call ctx.onUltimateFinished() when its blocking part ends. */
  executeUltimateSkill(ctx) { ctx.onUltimateFinished(); }

  // ---- sprite (BattleActivity.setupHeroAndSkills) ----
  /** Width of the hero image box in dp (height = width x 0.545). */
  getImageWidthDp() { return 120; }
  /** true if the source image faces RIGHT (heroes 3, 4, 5); false = faces LEFT (heroes 1, 2). */
  spriteFacesRight() { return false; }
  getImageKey() { return `hero_${this.id}`; }

  // ---- skills ----
  useSkill1(ctx) {}
  useSkill2(ctx) {}
  /** per-frame hook (e.g. Swordfish charge sparks) */
  update(ctx) {}
  /** stage end / restart cleanup */
  clearAll() {}

  // ---- texts (names/icons/descriptions come from the hero class, like Android) ----
  getSkill1Name() { return 'Skill 1'; }
  getSkill1Icon() { return '⚔️'; }
  getSkill1Description() { return ''; }
  getSkill2Name() { return 'Skill 2'; }
  getSkill2Icon() { return '🌀'; }
  getSkill2Description() { return ''; }
  getUltimateName() { return 'Ultimate'; }
  getUltimateIcon() { return '💥'; }
  getUltimateDescription() { return ''; }

  // ---- hero-select texts (MainActivity.selectHero / strings.xml) ----
  getRoleLabel() { return ''; }          // e.g. "(Fighter)"
  getSubjectLabel() { return ''; }       // e.g. "วงจรไฟฟ้า (Electrical Circuits)"
  getProfileDescription() { return ''; }
}
