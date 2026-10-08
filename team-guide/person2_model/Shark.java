// =====================================================================================
// [คนที่ 2 - Model ฮีโร่และสกิล]  ไฟล์: Shark.java  (422 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/Shark.java
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   ฮีโร่ตัวที่ 3 "Shark" (ฉลาม) วิชา "แคลคูลัส" (Calculus) สายพุ่งกัดสมดุล
//     - Skill 1 Derivative Bite   พุ่งกัด ดาเมจ 2-4 ตามความเร็วตอนกด (ธีม: อนุพันธ์ = อัตราการเปลี่ยนแปลง)
//     - Skill 2 Accumulating Wave คลื่นทะลุทุกตัว ดาเมจสะสม 1,2,3,... ตามลำดับที่โดน (ธีม: ผลรวม/อินทิกรัล)
//     - Ultimate Blood Frenzy     6 วินาที ว่ายเร็ว 1.5 เท่า คูลดาวน์เหลือครึ่ง กัดโดนติดกันแรงขึ้นทีละ 1 พลาด = รีเซ็ต
//   ใช้ปุ่ม ULT แยก
//
// [ผังไฟล์] สเตตัส -> ค่าคงที่ -> สถานะ Frenzy -> constructor/ข้อความ -> useSkill1 -> useSkill2
//           -> executeUltimateSkill -> endFrenzy -> ตัวช่วย blend/overlaps -> WaveView, FrenzyView (วาดเอง)
//
// [จะแก้/เพิ่มอะไรบ่อย ๆ]
//   - ดาเมจกัด: BITE_MIN_DAMAGE / BITE_MAX_DAMAGE | ระยะกัด: BITE_DISTANCE
//   - โหมดคลั่ง: FRENZY_MS (เวลา) FRENZY_SPEED (ความเร็ว) FRENZY_COOLDOWN (ตัวคูณคูลดาวน์ น้อย = ใช้สกิลถี่)
// =====================================================================================
package com.example.finfury;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

// [คอมเมนต์คลาส - สรุปสกิลโดยเจ้าของไฟล์เดิม]
/**
 * Shark (วิชาแคลคูลัส):
 *  - Skill 1 Derivative Bite   : พุ่งกัด 350 px ดาเมจขึ้นกับ "ความเร็ว" ตอนกดปุ่ม (2 = หยุดนิ่ง ... 4 = เต็มสปีด)
 *  - Skill 2 Accumulating Wave : คลื่นกระแทกทะลุทุกตัว ตัวแรก 1 ดาเมจ ตัวต่อไปบวกเพิ่มทีละ 1 (1, 2, 3...)
 *  - Ultimate Blood Frenzy     : 6 วินาที ว่ายเร็วขึ้น คูลดาวน์สั้นลง และกัดโดนแต่ละครั้งแรงขึ้นอีก 1 (กัดพลาด = รีเซ็ต)
 */
public class Shark extends Hero {
    // [สเตตัสของ Shark] เลือด 100 / ความเร็ว 1.0 / ต้องสะสม 16 ฮิต / คูลดาวน์ Skill 1 = 2 วินาที, Skill 2 = 4 วินาที
    //   [แก้ได้โดยเปลี่ยนตัวเลข] ดูคำอธิบายที่ Hero.java
    // ---- สเตตัสพื้นฐาน (สมดุล) ----
    @Override public int getMaxHp() { return 100; }
    @Override public float getBaseSpeedMultiplier() { return 1.0f; }
    @Override public int getStackNeeded() { return 16; }
    @Override public long getSkill1CooldownMs() { return 2000; }
    @Override public long getSkill2CooldownMs() { return 4000; }


    // [ค่าคงที่ Skill 1] BITE_DISTANCE ระยะพุ่ง (px) | BITE_DURATION_MS เวลาพุ่ง | ดาเมจต่ำสุดตอนยืนนิ่ง 2 ถึงสูงสุดตอนเต็มสปีด 4
    // ---------- Skill 1: Derivative Bite ----------
    private static final float BITE_DISTANCE = 350f;
    private static final long BITE_DURATION_MS = 150;
    private static final int BITE_MIN_DAMAGE = 2;          // ยืนนิ่ง
    private static final int BITE_MAX_DAMAGE = 4;          // ว่ายเต็มสปีด

    // [ค่าคงที่ Skill 2] WAVE_DURATION_MS เวลาที่คลื่นวิ่ง | WAVE_DISTANCE ระยะทางไกลสุด | WAVE_RADIUS รัศมีที่นับว่าโดนศัตรู
    //   WAVE_THICKNESS/SPAN ขนาดหน้าคลื่นที่วาด (หนา x กว้างขวางทิศ)
    // ---------- Skill 2: Accumulating Wave ----------
    private static final long WAVE_DURATION_MS = 650;
    private static final float WAVE_DISTANCE = 1200f;
    private static final float WAVE_RADIUS = 70f;          // รัศมีโดนศัตรู (px)
    private static final int WAVE_THICKNESS = 36;
    private static final int WAVE_SPAN = 150;              // ความกว้างหน้าคลื่นขวางทิศ

    // [ค่าคงที่ Ultimate] FRENZY_MS ระยะเวลาคลั่ง | FRENZY_SPEED ตัวคูณความเร็ว | FRENZY_COOLDOWN ตัวคูณคูลดาวน์ (0.5 = เร็วขึ้นเท่าตัว)
    // ---------- Ultimate: Blood Frenzy ----------
    private static final long FRENZY_MS = 6000;
    private static final float FRENZY_SPEED = 1.5f;
    private static final float FRENZY_COOLDOWN = 0.5f;

    // [ตัวแปรสถานะ Frenzy] frenzyActive = กำลังคลั่งไหม | biteStack = กัดโดนติดกันกี่ครั้ง (นี่คือดาเมจโบนัส)
    //   frenzyLoop = ตัวจับเวลา | frenzyView = ภาพขอบจอแดง+ข้อความ
    // สถานะ Frenzy
    private boolean frenzyActive = false;
    private int biteStack = 0;                 // กัดโดนติดกันมาแล้วกี่ครั้ง (ดาเมจโบนัส = ค่านี้)
    private ValueAnimator frenzyLoop;
    private FrenzyView frenzyView;

    // [constructor] ชื่อ "Shark" วิชา "Calculus" (QuestionBank.forSubject ใช้วิชานี้เลือกโจทย์แคลคูลัส เป็นค่าเริ่มต้นเมื่อไม่ตรงวิชาอื่น)
    public Shark() {
        super("Shark", "Calculus");
    }

    // [ปุ่ม ULT แยก] true
    @Override
    public boolean usesUltimateButton() {
        return true;
    }

    // [ข้อความสกิล] ชื่อ ไอคอน คำอธิบาย ของสกิลทั้ง 3 (แสดงบนปุ่มและหน้าเลือกฮีโร่)
    @Override public String getSkill1Name() { return "Derivative Bite"; }
    @Override public String getSkill1Icon() { return "🦈"; }
    @Override public String getSkill1Description() { return "พุ่งกัด ดาเมจ 2-4 ตามความเร็วที่ว่ายอยู่ตอนกด ยิ่งเร็วยิ่งแรง"; }

    @Override public String getSkill2Name() { return "Accumulating Wave"; }
    @Override public String getSkill2Icon() { return "🌊"; }
    @Override public String getSkill2Description() { return "คลื่นทะลุทุกตัว ตัวแรก 1 ดาเมจ ตัวถัดไปบวกเพิ่มทีละ 1 (1, 2, 3...)"; }

    @Override public String getUltimateName() { return "Blood Frenzy"; }
    @Override public String getUltimateIcon() { return "🩸"; }
    @Override public String getUltimateDescription() { return "6 วินาที ว่ายเร็วขึ้น คูลดาวน์สั้นลง กัดโดนติดกันแรงขึ้นทีละ 1 กัดพลาดรีเซ็ต"; }

    // =========================================================
    // Skill 1: Derivative Bite ดาเมจขึ้นกับความเร็ว ณ ตอนกดปุ่ม
    // =========================================================
    // [Skill 1: Derivative Bite - ลำดับการทำงาน]
    //   1) อ่านความเร็วผู้เล่นตอนกด "ก่อนล็อก" (ctx.getPlayerSpeedRatio 0..1) เพราะพอล็อกแล้วความเร็วจะเป็น 0
    //   2) ดาเมจ = ต่ำสุด + ปัดเศษ(สัดส่วนความเร็ว x (สูงสุด - ต่ำสุด)) และบวก biteStack ถ้ากำลังคลั่ง
    //   3) ล็อกการเดิน คำนวณจุดปลายตามมุมจอยสติ๊กและบีบให้อยู่ในจอ
    //   4) พุ่งด้วย ValueAnimator (ชะลอ) ทุกเฟรม: ทิ้งเงาปลา (GhostPool) สีฟ้า -> แดงตามความเร็ว
    //      และเช็กชนศัตรู ตัวละ 1 ครั้ง: takeDamage + onHitEnemySuccess (ยกเว้นช่วงคลั่งไม่สะสมสแตก)
    //   5) จบพุ่ง: ปลดล็อก; ถ้าคลั่ง: กัดโดน = biteStack+1 , กัดพลาด = รีเซ็ตเป็น 0 แล้วอัปเดตโบนัสดาเมจเหนือหัว
    //   [แก้ยังไง] ความไวต่อความเร็ว: แก้สูตร baseDamage | ให้ฟ้า/แดงเปลี่ยนสี: แก้สีใน Color.parseColor บรรทัดที่เรียก blend
    @Override
    public void useSkill1(BattleContext ctx) {
        FrameLayout gameArea = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (gameArea == null || player == null) return;

        // อ่านความเร็วก่อนล็อกการเคลื่อนที่ ไม่งั้นความเร็วถูกกดเป็น 0
        final float speedRatio = ctx.getPlayerSpeedRatio();
        final int baseDamage = BITE_MIN_DAMAGE + Math.round(speedRatio * (BITE_MAX_DAMAGE - BITE_MIN_DAMAGE));
        final int damage = baseDamage + (frenzyActive ? biteStack : 0);

        ctx.setSkillLock(true);

        float rad = (float) Math.toRadians(ctx.getPlayerAngle());
        final float startTx = player.getTranslationX();
        final float startTy = player.getTranslationY();

        float endTx = startTx + (float) Math.cos(rad) * BITE_DISTANCE;
        float endTy = startTy + (float) Math.sin(rad) * BITE_DISTANCE;

        if (gameArea.getWidth() > 0 && gameArea.getHeight() > 0) {
            endTx = Math.max(-player.getLeft(), Math.min(gameArea.getWidth() - player.getRight(), endTx));
            endTy = Math.max(-player.getTop(), Math.min(gameArea.getHeight() - player.getBottom(), endTy));
        }

        final float finalEndTx = endTx;
        final float finalEndTy = endTy;
        final List<SeaEnemy> hitEnemies = new ArrayList<>();

        // สีเงาตามแรงกัด: ฟ้า (ช้า) -> แดง (เร็ว) ให้ผู้เล่นเห็นว่ากดตอนเร็วพอหรือยัง
        final int ghostColor = blend(Color.parseColor("#4400E5FF"), Color.parseColor("#88FF1744"), speedRatio);

        final long[] lastGhost = {0};   // เสกเงาไม่ถี่กว่า 40 ms
        ValueAnimator bite = ValueAnimator.ofFloat(0f, 1f);
        bite.setDuration(BITE_DURATION_MS);
        bite.setInterpolator(new DecelerateInterpolator());

        bite.addUpdateListener(animation -> {
            float p = (float) animation.getAnimatedValue();
            player.setTranslationX(startTx + (finalEndTx - startTx) * p);
            player.setTranslationY(startTy + (finalEndTy - startTy) * p);

            if (GhostPool.due(lastGhost)) {
                GhostPool.ghosts(gameArea).ghost(player.getX(), player.getY(),
                        player.getWidth(), player.getHeight(), ghostColor);
            }

            for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
                SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);
                if (enemy.isAlive && enemy.containerView != null
                        && !hitEnemies.contains(enemy)
                        && overlaps(player, enemy.containerView)) {
                    hitEnemies.add(enemy);
                    enemy.takeDamage(damage);
                    if (!frenzyActive) ctx.onHitEnemySuccess();
                }
            }
        });

        bite.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ctx.setSkillLock(false);
                // Frenzy: กัดโดน = สแตกเพิ่ม 1, กัดพลาด = รีเซ็ต
                if (frenzyActive) {
                    biteStack = hitEnemies.isEmpty() ? 0 : biteStack + 1;
                    if (frenzyView != null) frenzyView.setStack(biteStack);
                    ctx.setPlayerBonusDamage(biteStack);
                }
            }
        });

        bite.start();
    }

    // =========================================================
    // Skill 2: Accumulating Wave คลื่นทะลุทุกตัว ดาเมจ 1, 2, 3... ตามลำดับที่โดน
    // =========================================================
    // [Skill 2: Accumulating Wave - ลำดับการทำงาน]
    //   1) คำนวณทิศจากมุมจอยสติ๊ก จุดเริ่มห่างตัวปลา 50 px จุดสิ้นสุด = เริ่ม + ทิศ x WAVE_DISTANCE
    //   2) สร้าง WaveView (หน้าคลื่นโค้ง) หมุนตามมุมและวางที่จุดเริ่ม
    //   3) ValueAnimator เลื่อนคลื่นเป็นเส้นตรง (ความเร็วคงที่) พร้อมจางลงครึ่งหนึ่งเมื่อถึงปลายทาง
    //   4) ทุกเฟรม: ศัตรูตัวไหนอยู่ในระยะ WAVE_RADIUS + ครึ่งขนาดตัว และยังไม่เคยโดน -> เก็บใน hitEnemies
    //      ดาเมจ = จำนวนที่โดนแล้วรวมตัวนี้ (ตัวแรก 1 ตัวที่สอง 2 ตัวที่สาม 3) แล้วคลื่นทะลุต่อ
    //   5) จบ: ลบคลื่นออก
    //   [แก้ยังไง] อยากให้ดาเมจเพิ่มทีละ 2: เปลี่ยน hitEnemies.size() เป็น hitEnemies.size() * 2
    @Override
    public void useSkill2(BattleContext ctx) {
        FrameLayout gameArea = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (gameArea == null || player == null) return;

        float angleDeg = ctx.getPlayerAngle();
        float rad = (float) Math.toRadians(angleDeg);
        final float dirX = (float) Math.cos(rad);
        final float dirY = (float) Math.sin(rad);

        float playerCx = player.getX() + player.getWidth() / 2f;
        float playerCy = player.getY() + player.getHeight() / 2f;
        final float startCx = playerCx + dirX * 50f;
        final float startCy = playerCy + dirY * 50f;
        final float endCx = startCx + dirX * WAVE_DISTANCE;
        final float endCy = startCy + dirY * WAVE_DISTANCE;

        WaveView wave = new WaveView(ctx.getContext());
        wave.setLayoutParams(new FrameLayout.LayoutParams(WAVE_THICKNESS, WAVE_SPAN));
        wave.setRotation(angleDeg);
        wave.setX(startCx - WAVE_THICKNESS / 2f);
        wave.setY(startCy - WAVE_SPAN / 2f);
        gameArea.addView(wave);

        final List<SeaEnemy> hitEnemies = new ArrayList<>();

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(WAVE_DURATION_MS);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(animation -> {
            float f = animation.getAnimatedFraction();
            float cx = startCx + (endCx - startCx) * f;
            float cy = startCy + (endCy - startCy) * f;
            wave.setX(cx - WAVE_THICKNESS / 2f);
            wave.setY(cy - WAVE_SPAN / 2f);
            wave.setAlpha(1f - 0.5f * f);

            for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
                SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);
                if (!enemy.isAlive || enemy.containerView == null || hitEnemies.contains(enemy)) continue;
                View ev = enemy.containerView;
                float ex = ev.getX() + ev.getWidth() / 2f;
                float ey = ev.getY() + ev.getHeight() / 2f;
                float reach = WAVE_RADIUS + Math.max(ev.getWidth(), ev.getHeight()) / 2f;
                if (Math.hypot(ex - cx, ey - cy) <= reach) {
                    hitEnemies.add(enemy);
                    // ตัวที่ n ที่โดน = n ดาเมจ (ทะลุต่อไปหาตัวถัดไป ไม่หายไปเมื่อโดน)
                    enemy.takeDamage(hitEnemies.size());
                    if (!frenzyActive) ctx.onHitEnemySuccess();
                }
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                gameArea.removeView(wave);
            }
        });
        anim.start();
    }

    // =========================================================
    // Ultimate: Blood Frenzy 6 วินาที
    // =========================================================
    // [Ultimate: Blood Frenzy - ลำดับการทำงาน]
    //   1) endFrenzy เคลียร์ของเก่า 2) frenzyActive=true , biteStack=0
    //   3) ctx.setSpeedMultiplier / setCooldownMultiplier เปิดโหมดเร็ว 4) สร้าง FrenzyView ขอบจอแดง
    //   5) ctx.onUltimateFinished() ปลดล็อกปุ่มสกิลทันที (ผู้เล่นต้องกัดเอง) + showUltimateDuration โชว์หลอดเวลา
    //   6) frenzyLoop นับถอยหลังเอง: เกมจบ = ปิด | เกมหยุด (quiz) = หยุดนับ | หมดเวลา = ปิด
    //   [ระวัง] ต้องคืนค่า setSpeedMultiplier(1f) และ setCooldownMultiplier(1f) ตอนจบ (ทำใน endFrenzy) ไม่งั้นปลาเร็วค้างตลอดเกม
    @Override
    public void executeUltimateSkill(BattleContext ctx) {
        FrameLayout area = ctx.getGameArea();
        if (area == null) {
            ctx.onUltimateFinished();
            return;
        }

        endFrenzy(ctx);   // ใช้ซ้ำระหว่างทำงาน = เริ่มนับใหม่

        frenzyActive = true;
        biteStack = 0;
        ctx.setSpeedMultiplier(FRENZY_SPEED);
        ctx.setCooldownMultiplier(FRENZY_COOLDOWN);

        frenzyView = new FrenzyView(ctx.getContext());
        frenzyView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(frenzyView);

        // ปลดล็อกปุ่มสกิลทันที: ผู้เล่นต้องกัดต่อเนื่องเองระหว่างที่คลั่ง
        ctx.onUltimateFinished();
        ctx.showUltimateDuration(FRENZY_MS);
        ctx.setPlayerBonusDamage(0);

        final long[] remaining = {FRENZY_MS};
        final long[] lastNs = {System.nanoTime()};
        frenzyLoop = ValueAnimator.ofFloat(0f, 1f);
        frenzyLoop.setDuration(60_000);   // เพดานกันค้าง จบจริงด้วยตัวนับเวลาด้านล่าง
        frenzyLoop.setInterpolator(new LinearInterpolator());
        frenzyLoop.addUpdateListener(animation -> {
            long now = System.nanoTime();
            long dtMs = (now - lastNs[0]) / 1_000_000L;
            lastNs[0] = now;

            if (!ctx.isGameRunning()) {
                endFrenzy(ctx);
                return;
            }
            if (ctx.isGamePaused()) return;   // quiz ขึ้นอยู่ เวลาหยุดนับ

            remaining[0] -= dtMs;
            if (remaining[0] <= 0) {
                endFrenzy(ctx);
                return;
            }
            frenzyView.update(remaining[0] / (float) FRENZY_MS, remaining[0]);
        });
        frenzyLoop.start();
    }

    // [endFrenzy = ปิดโหมดคลั่ง] รีเซ็ตสถานะ คืนความเร็ว/คูลดาวน์ ลบโบนัสดาเมจเหนือหัว ซ่อนหลอดเวลา
    //   ยกเลิกตัวจับเวลา และทำให้ FrenzyView จางหาย 250 ms ก่อนลบ  (ถูกเรียกทั้งตอนหมดเวลา เกมจบ และตอนใช้ Ultimate ซ้ำ)
    private void endFrenzy(BattleContext ctx) {
        frenzyActive = false;
        biteStack = 0;
        ctx.setSpeedMultiplier(1f);
        ctx.setCooldownMultiplier(1f);
        ctx.clearPlayerBonusDamage();
        ctx.hideUltimateDuration();
        if (frenzyLoop != null) {
            ValueAnimator l = frenzyLoop;
            frenzyLoop = null;
            l.removeAllUpdateListeners();
            l.cancel();
        }
        if (frenzyView != null) {
            View v = frenzyView;
            frenzyView = null;
            v.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                if (v.getParent() instanceof FrameLayout) ((FrameLayout) v.getParent()).removeView(v);
            }).start();
        }
    }

    // =========================================================
    // ตัวช่วย
    // =========================================================
    // [blend = ผสมสอง สี] เช่น ฟ้า -> แดง ตามค่า t (0..1) ผสมแต่ละช่อง A R G B ด้วยการเฉลี่ยถ่วงน้ำหนัก
    private static int blend(int c0, int c1, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.argb(
                (int) (Color.alpha(c0) + (Color.alpha(c1) - Color.alpha(c0)) * t),
                (int) (Color.red(c0) + (Color.red(c1) - Color.red(c0)) * t),
                (int) (Color.green(c0) + (Color.green(c1) - Color.green(c0)) * t),
                (int) (Color.blue(c0) + (Color.blue(c1) - Color.blue(c0)) * t));
    }


    // [overlaps = เช็ก View สองตัวทับกันไหน] เทียบสี่เหลี่ยมจากตำแหน่ง x,y และขนาด (ถ้าขนาดเป็น 0 ใช้ 80 px แทน)
    //   ต่างจาก Swordfish.isColliding ที่ใช้พื้นที่ที่มองเห็นบนจอ
    private static boolean overlaps(View v1, View v2) {
        float x1 = v1.getX(), y1 = v1.getY();
        float w1 = v1.getWidth() > 0 ? v1.getWidth() : 80f;
        float h1 = v1.getHeight() > 0 ? v1.getHeight() : 80f;
        float x2 = v2.getX(), y2 = v2.getY();
        float w2 = v2.getWidth() > 0 ? v2.getWidth() : 80f;
        float h2 = v2.getHeight() > 0 ? v2.getHeight() : 80f;
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    // [WaveView] วาดหน้าคลื่นเป็นเสี้ยวพระจันทร์ (ส่วนโค้งของวงรี) สองชั้น: เรืองแสงฟ้ากับแกนขาว ไม่ใช้ไฟล์รูป
    //   หมุนด้วย rotation ของ View ให้หันไปทางที่ยิง (rotation 0 = ไปทางขวา)
    /** หน้าคลื่นโค้งเป็นเสี้ยวพระจันทร์ เรืองแสงฟ้า-ขาว หันเข้าทิศที่ยิง (หมุนด้วย rotation ของ View) */
    private static class WaveView extends View {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();

        WaveView(Context context) {
            super(context);
            for (Paint p : new Paint[]{glow, core}) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
            }
            glow.setColor(Color.parseColor("#8800E5FF"));
            glow.setStrokeWidth(18f);
            core.setColor(Color.parseColor("#FFE0F7FA"));
            core.setStrokeWidth(6f);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float w = getWidth(), h = getHeight();
            // วงรีที่โค้งเข้าหาขอบขวา (ทิศที่คลื่นพุ่งไป เพราะ rotation 0 = ไปทางขวา)
            oval.set(-w * 1.6f, 4f, w - 4f, h - 4f);
            canvas.drawArc(oval, -70f, 140f, false, glow);
            canvas.drawArc(oval, -70f, 140f, false, core);
        }
    }

    // [FrenzyView] ภาพโหมดคลั่ง: ขอบจอสีแดงเต้นตุบ (ถี่ขึ้นเมื่อเหลือน้อยกว่า 25%), ข้อความ "BLOOD FRENZY +สแตก (เวลา)" พร้อมเงาดำ,
    //   และหลอดเวลาด้านล่างข้อความ
    //   ข้อความสร้างใหม่เฉพาะตอนค่าเปลี่ยน (เศษวินาที/สแตก) ประหยัดเครื่อง
    //   [แก้ยังไง] ข้อความ: แก้ label.append(...) | สีขอบ: แก้ "#FF1744" | ขนาดตัวอักษร: setTextSize(46f)
    /** ขอบจอแดงเต้นตุบ + ข้อความ "BLOOD FRENZY +สแตก" + เวลาที่เหลือ */
    private static class FrenzyView extends View {
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barBack = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float fraction = 1f;
        private long remainingMs = FRENZY_MS;
        private int stack = 0;
        private final StringBuilder label = new StringBuilder(48);
        private int lastTenths = -1;
        private int lastStack = -1;

        FrenzyView(Context context) {
            super(context);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(26f);
            border.setColor(Color.parseColor("#FF1744"));
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(46f);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            barBack.setColor(Color.parseColor("#55000000"));
            barFill.setColor(Color.parseColor("#FFFF1744"));
        }

        void update(float fraction, long remainingMs) {
            this.fraction = fraction;
            this.remainingMs = remainingMs;
            invalidate();
        }

        void setStack(int stack) {
            this.stack = stack;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float w = getWidth(), h = getHeight();

            // ขอบแดงเต้นตุบ ยิ่งใกล้หมดเวลายิ่งเต้นถี่
            double beat = System.nanoTime() / (fraction < 0.25f ? 90e6 : 180e6);
            border.setAlpha((int) (70 + 60 * Math.sin(beat)));
            rect.set(0, 0, w, h);
            canvas.drawRect(rect, border);

            // ข้อความสร้างใหม่เฉพาะตอนค่าเปลี่ยน (เศษวินาทีหรือสแตก) ไม่ format ทุกเฟรม
            int tenths = (int) (remainingMs / 100);
            if (tenths != lastTenths || stack != lastStack) {
                lastTenths = tenths;
                lastStack = stack;
                label.setLength(0);
                label.append("🩸 BLOOD FRENZY  +").append(stack).append("  (")
                        .append(tenths / 10).append('.').append(tenths % 10).append("s)");
            }
            // เงาข้อความ: วาดสีดำเยื้อง 2 px ก่อน แล้วค่อยวาดตัวหนังสือสีขาวทับ (แทน shadow layer ที่ต้องใช้ software layer)
            text.setColor(Color.BLACK);
            canvas.drawText(label, 0, label.length(), w / 2f + 2f, 72f, text);
            text.setColor(Color.WHITE);
            canvas.drawText(label, 0, label.length(), w / 2f, 70f, text);

            float barW = w * 0.4f;
            rect.set(w / 2f - barW / 2f, 90f, w / 2f + barW / 2f, 106f);
            canvas.drawRoundRect(rect, 8f, 8f, barBack);
            rect.right = rect.left + barW * fraction;
            canvas.drawRoundRect(rect, 8f, 8f, barFill);
        }
    }
}
