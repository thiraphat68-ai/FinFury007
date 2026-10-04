// Port of StageConfig.java: difficulty per stage. Later stages inherit the "accumulated" difficulty.
const BASE_ATTACK_INTERVAL_MS = 3000;
const ATTACK_SPEED_PER_STAGE = 1.4;
const MIN_ATTACK_INTERVAL_MS = 1300;

function intervalForStage(stage) {
  return Math.max(MIN_ATTACK_INTERVAL_MS,
    Math.round(BASE_ATTACK_INTERVAL_MS / Math.pow(ATTACK_SPEED_PER_STAGE, stage - 1)));
}

function make(stage, boss, includeSquid, enemyCount, speedMul, hpMul, attackIntervalMs, dashSpeedMul,
              dualAttack, ultDebuffEveryHits, ultGainFactor) {
  return {
    stage, boss, includeSquid, enemyCount, speedMul, hpMul, attackIntervalMs, dashSpeedMul, dualAttack,
    ultDebuffEveryHits, ultGainFactor,
    damageMul: 1 + 0.20 * (stage - 1),
  };
}

export function stageConfig(stage) {
  switch (stage) {
    case 2: return make(2, false, true, 5, 1.20, 1.40, intervalForStage(2), 1.10, false, 3, 0.85);
    case 3: return make(3, false, true, 5, 1.30, 1.65, intervalForStage(3), 1.20, true, 2, 0.65);
    case 4: return make(4, false, true, 5, 1.40, 1.95, intervalForStage(4), 1.30, true, 2, 0.6);
    case 5: return make(5, true, true, 1, 1.0, 1.0, 3000, 1.0, false, 0, 1);
    case 1:
    default: return make(1, false, true, 5, 1.10, 1.15, intervalForStage(1), 1.05, false, 0, 1);
  }
}
