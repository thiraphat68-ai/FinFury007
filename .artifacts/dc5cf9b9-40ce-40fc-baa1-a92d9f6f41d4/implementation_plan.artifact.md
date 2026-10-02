# Implementation Plan - Complete Enemy AI & Combat Polish Pass

## Goal Description
Implement 4 core gameplay & combat responsiveness improvements to `SeaEnemy.java`:
1. **Attack Telegraphing / Wind-Up (`WINDUP` State)**: Add a 350ms warning phase before an enemy dashes to attack, pulsing scale/red tint so players can react and counter-attack.
2. **Distinct Enemy Personalities**: Differentiate stats (speed, HP, orbit radius) for Crab 🦀, Jellyfish 🪼, Turtle 🐢, Squid 🦑, and Starfish ⭐️.
3. **Hit Reaction & Knockback**: Interrupt enemy attacks when hit by player skills and push them back with a knockback impulse.
4. **Dynamic Aggression Scaling**: Reduce attack cooldowns dynamically as enemies are defeated (2.0s with 5 enemies -> 0.9s with 1-2 enemies left).

## User Review Required
> [!IMPORTANT]
> - **Telegraphing (Wind-Up)**: Enemies will pause briefly (350ms) and pulse red with a scale boost before dashing towards the player, giving a clear window to dodge or counter-attack.
> - **Varied Enemy Stats**:
>   - 🦀 **Crab (ปูซ่า)**: Tanky (14 HP), slower speed (0.8x), close range (180px).
>   - 🦑 **Squid (หมึกยักษ์)**: Fast & aggressive (1.25x speed, 220px orbit).
>   - 🐢 **Turtle (เต่าทะเล)**: High health (16 HP), wide orbit (280px).
>   - 🪼 **Jellyfish (แมงกะพรุน)**: Medium speed, floating wide orbit (260px).
>   - ⭐️ **Starfish (ดาวทะเล)**: Balanced (10 HP, 240px orbit).
> - **Knockback & Interruption**: Attacking or winding-up enemies will be interrupted and knocked back when damaged by player attacks.
> - **Dynamic Scaling**: Fewer remaining enemies = shorter waiting time between attacks (down to 0.9s when only 1-2 enemies remain).

## Proposed Changes

### [Component: Enemy AI & Combat]

#### [MODIFY] [SeaEnemy.java](file:///C:/ADHD+++/Home_work/FIn&Fury_V1/app/src/main/java/com/example/finfury/SeaEnemy.java)
- Add `State.WINDUP` state and animation/color pulse handling.
- Add enemy personality parameter configuration based on name/emoji.
- Implement knockback and attack interruption in `takeDamage()`.
- Add dynamic cooldown calculation based on the count of remaining alive enemies.

## Verification Plan

### Automated Tests
- Run `gradle_build` (`app:assembleDebug`) to ensure no compilation errors.

### Manual Verification
- Test in `BattleActivity`.
- Observe wind-up red flash before attacks.
- Verify enemy knockback when hit by skills.
- Verify varied speed/health between different sea creatures.
- Verify faster pacing when only 1 or 2 enemies remain.
