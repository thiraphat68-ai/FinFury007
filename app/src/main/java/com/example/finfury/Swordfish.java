package com.example.finfury;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swordfish (วิชาวงจรไฟฟ้า) ชุดสกิลไฟฟ้า:
 *  - Skill 1 Charge Bite  : พุ่งกัด 350 px ดาเมจ 3 ศัตรูที่โดนติด "ประจุ" 4 วินาที
 *  - Skill 2 Discharge    : ยิงคลื่นไฟฟ้าเส้นตรง ดาเมจ 2 (ติดประจุได้เหมือนกัน)
 *  - ประจุจากสกิลหนึ่ง + โดนอีกสกิลหนึ่ง = ระเบิดประจุ ดาเมจ 5 แล้วไฟฟ้าสาดไปหาศัตรูที่ใกล้ที่สุด 1 ตัว
 *  - Ultimate Circuit Link: ต่อศัตรูทั้งหมดเป็นวงจรเดียว 5 วินาที โดนตัวไหนตัวอื่นโดนด้วย
 */
public class Swordfish extends Hero {

    // ---------- Skill 1: Charge Bite ----------
    private static final float DASH_DISTANCE = 350f;   // px
    private static final long DASH_DURATION_MS = 150;
    private static final int DASH_DAMAGE = 3;

    // ---------- Skill 2: Discharge ----------
    private static final int BOLT_LENGTH = 46;           // px ความยาวคลื่น
    private static final int BOLT_THICKNESS = 16;        // px ความหนาคลื่น
    private static final long PROJECTILE_DURATION_MS = 550;
    private static final float PROJECTILE_DISTANCE = 1100f;
    private static final int PROJECTILE_DAMAGE = 2;

    // ---------- ประจุ ----------
    private static final long CHARGE_MS = 4000;          // อายุของประจุที่ติดบนศัตรู
    private static final int DETONATE_DAMAGE = 5;        // ดาเมจเมื่อประจุระเบิด
    private static final int ARC_DAMAGE = 3;             // ดาเมจไฟฟ้าที่สาดไปหาศัตรูที่ใกล้ที่สุด

    // ---------- Ultimate: Circuit Link ----------
    private static final long LINK_MS = 5000;

    // ตำแหน่งปากบนรูป hero_1 แบบยังไม่กลับด้าน (รูปต้นฉบับหันซ้าย)
    // 0 = ขอบซ้ายของรูป, 1 = ขอบขวา / 0 = ขอบบน, 1 = ขอบล่าง
    private static final float MOUTH_X_RATIO = 0.38f;
    private static final float MOUTH_Y_RATIO = 0.55f;

    private static final Rect tmpRect1 = new Rect();
    private static final Rect tmpRect2 = new Rect();

    /** ประจุที่ติดอยู่บนศัตรู: มาจากสกิลไหน + ประกายที่ตามตัวศัตรู */
    private static class Mark {
        final int skill;
        final SparkView view;
        final ValueAnimator anim;

        Mark(int skill, SparkView view, ValueAnimator anim) {
            this.skill = skill;
            this.view = view;
            this.anim = anim;
        }
    }

    private final Map<SeaEnemy, Mark> marks = new HashMap<>();

    // สถานะวงจร (Ultimate)
    private boolean linkActive = false;
    private final List<SeaEnemy> linked = new ArrayList<>();
    private CircuitView circuit;
    private ValueAnimator linkLoop;

    public Swordfish() {
        super("Swordfish", "Circuits");
    }

    /** Swordfish ใช้ปุ่ม Ultimate แยก (ไม่ปล่อยอัตโนมัติหลังตอบถูก) */
    @Override
    public boolean usesUltimateButton() {
        return true;
    }

    @Override public String getSkill1Name() { return "Charge Bite"; }
    @Override public String getSkill1Icon() { return "⚡"; }
    @Override public String getSkill1Description() { return "พุ่งกัด สร้างดาเมจ 3 หน่วย ศัตรูที่โดนจะติดประจุ 4 วินาที"; }

    @Override public String getSkill2Name() { return "Discharge"; }
    @Override public String getSkill2Icon() { return "🔋"; }
    @Override public String getSkill2Description() {
        return "ยิงคลื่นไฟฟ้า ดาเมจ 2 หน่วย ถ้าโดนศัตรูที่ติดประจุจากอีกสกิล ประจุระเบิด 5 ดาเมจและกระโดดไปตัวใกล้สุด"; }

    @Override public String getUltimateName() { return "Circuit Link"; }
    @Override public String getUltimateIcon() { return "🔗"; }
    @Override public String getUltimateDescription() { return "ต่อศัตรูทั้งหมดเป็นวงจร 5 วินาที โจมตีโดนตัวไหนตัวอื่นโดนด้วย"; }

    // =========================================================
    // Skill 1: Charge Bite พุ่งกัดไปตามทิศจอยสติ๊ก ดาเมจ 3 + ติดประจุ
    // =========================================================
    @Override
    public void useSkill1(BattleContext ctx) {
        FrameLayout gameArea = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (gameArea == null || player == null) return;

        ctx.setSkillLock(true);

        float rad = (float) Math.toRadians(ctx.getPlayerAngle());

        // ทำงานกับ translation ตรงๆ อย่างเดียว (getX() รวม translation อยู่แล้ว ห้ามบวกซ้ำ)
        final float startTx = player.getTranslationX();
        final float startTy = player.getTranslationY();

        float endTx = startTx + (float) Math.cos(rad) * DASH_DISTANCE;
        float endTy = startTy + (float) Math.sin(rad) * DASH_DISTANCE;

        // กันพุ่งออกนอกจอ (ขอบเดียวกับที่ BattleActivity.updateFish ใช้)
        if (gameArea.getWidth() > 0 && gameArea.getHeight() > 0) {
            float minTx = -player.getLeft();
            float maxTx = gameArea.getWidth() - player.getRight();
            float minTy = -player.getTop();
            float maxTy = gameArea.getHeight() - player.getBottom();
            endTx = Math.max(minTx, Math.min(maxTx, endTx));
            endTy = Math.max(minTy, Math.min(maxTy, endTy));
        }

        final float finalEndTx = endTx;
        final float finalEndTy = endTy;
        final List<SeaEnemy> hitEnemies = new ArrayList<>();

        // เอฟเฟกต์พุ่ง: คลื่นไฟฟ้าที่จุดออกตัว + แถบแสงยาวตามเส้นทาง
        final float startCx = player.getX() + player.getWidth() / 2f;
        final float startCy = player.getY() + player.getHeight() / 2f;
        spawnRing(gameArea, startCx, startCy, player.getWidth() * 1.4f, Color.parseColor("#FFEB3B"));
        final StreakView streak = new StreakView(ctx.getContext(), startCx, startCy);
        streak.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        gameArea.addView(streak);

        final long[] sparkTimer = {0};   // เสกประกายไม่ถี่กว่า 40 ms
        ValueAnimator dash = ValueAnimator.ofFloat(0f, 1f);
        dash.setDuration(DASH_DURATION_MS);
        dash.setInterpolator(new DecelerateInterpolator());

        dash.addUpdateListener(animation -> {
            float p = (float) animation.getAnimatedValue();
            player.setTranslationX(startTx + (finalEndTx - startTx) * p);
            player.setTranslationY(startTy + (finalEndTy - startTy) * p);

            float cx = player.getX() + player.getWidth() / 2f;
            float cy = player.getY() + player.getHeight() / 2f;
            streak.setHead(cx, cy);
            if (GhostPool.due(sparkTimer)) spawnSpark(gameArea, cx, cy, player.getHeight() * 0.5f, 0xCCFFF59D);

            // ชนศัตรูตัวไหนก็ทำดาเมจ ตัวละ 1 ครั้ง แล้วพุ่งต่อทะลุไปเลย
            for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
                SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);
                if (enemy.isAlive && enemy.containerView != null
                        && !hitEnemies.contains(enemy)
                        && isColliding(player, enemy.containerView)) {
                    hitEnemies.add(enemy);
                    hitEnemy(ctx, enemy, DASH_DAMAGE, 1);
                }
            }
        });

        dash.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ctx.setSkillLock(false);
                // แถบแสงค่อยๆ จางหลังหยุดพุ่ง
                streak.animate().alpha(0f).setDuration(280)
                        .withEndAction(() -> gameArea.removeView(streak)).start();
            }
        });

        dash.start();
    }

    // =========================================================
    // Skill 2: Discharge ยิงคลื่นไฟฟ้าออกจากปากตรงๆ ไปตามแนวที่ปากชี้
    // =========================================================
    @Override
    public void useSkill2(BattleContext ctx) {
        FrameLayout gameArea = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (gameArea == null || player == null) return;

        // [0,1] = จุดปาก, [2,3] = ทิศที่ปากชี้ (เวกเตอร์ยาว 1)
        float[] aim = getMouthAim(player, ctx.getPlayerAngle());
        float mouthX = aim[0], mouthY = aim[1];
        float dirX = aim[2], dirY = aim[3];

        BoltView projectile = new BoltView(ctx.getContext());
        projectile.setLayoutParams(new FrameLayout.LayoutParams(BOLT_LENGTH, BOLT_THICKNESS));
        projectile.setRotation((float) Math.toDegrees(Math.atan2(dirY, dirX)));

        // วางให้ "ท้าย" คลื่นอยู่ที่ปากพอดี แล้วพุ่งออกไปข้างหน้า
        float centerX = mouthX + dirX * (BOLT_LENGTH / 2f);
        float centerY = mouthY + dirY * (BOLT_LENGTH / 2f);
        final float startX = centerX - BOLT_LENGTH / 2f;
        final float startY = centerY - BOLT_THICKNESS / 2f;
        final float targetX = startX + dirX * PROJECTILE_DISTANCE;
        final float targetY = startY + dirY * PROJECTILE_DISTANCE;

        projectile.setX(startX);
        projectile.setY(startY);
        gameArea.addView(projectile);
        spawnRing(gameArea, mouthX, mouthY, 90f, Color.parseColor("#FFF9C4"));

        final long[] sparkTimer = {0};   // เสกประกายไม่ถี่กว่า 40 ms
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(PROJECTILE_DURATION_MS);
        animator.setInterpolator(new LinearInterpolator());

        final boolean[] hasHit = {false};

        animator.addUpdateListener(animation -> {
            if (hasHit[0]) return;

            float f = animation.getAnimatedFraction();
            projectile.setX(startX + (targetX - startX) * f);
            projectile.setY(startY + (targetY - startY) * f);

            // หางไฟฟ้า: ประกายหลุดจากท้ายคลื่น
            float tailX = projectile.getX() + BOLT_LENGTH / 2f - dirX * BOLT_LENGTH * 0.4f;
            float tailY = projectile.getY() + BOLT_THICKNESS / 2f - dirY * BOLT_LENGTH * 0.4f;
            if (GhostPool.due(sparkTimer)) spawnSpark(gameArea, tailX, tailY, BOLT_THICKNESS * 1.1f, 0x88FFEB3B);

            for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
                SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);
                if (enemy.isAlive && enemy.containerView != null
                        && isColliding(projectile, enemy.containerView)) {
                    hasHit[0] = true;
                    animator.cancel();
                    gameArea.removeView(projectile);
                    hitEnemy(ctx, enemy, PROJECTILE_DAMAGE, 2);
                    break;
                }
            }
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!hasHit[0]) gameArea.removeView(projectile);
            }
        });

        animator.start();
    }

    // =========================================================
    // ระบบประจุ: ทุกการโดนของ Skill 1 / Skill 2 ต้องผ่านตัวนี้
    // =========================================================
    private void hitEnemy(BattleContext ctx, SeaEnemy enemy, int baseDamage, int skill) {
        FrameLayout area = ctx.getGameArea();
        Mark mark = marks.get(enemy);

        int damage = baseDamage;
        boolean detonate = mark != null && mark.skill != skill;
        float ex = 0f, ey = 0f;

        if (detonate) {
            damage = DETONATE_DAMAGE;
            View ev = enemy.containerView;
            ex = ev.getX() + ev.getWidth() / 2f;
            ey = ev.getY() + ev.getHeight() / 2f;
            clearMark(enemy);
            if (area != null) spawnRing(area, ex, ey, Math.max(ev.getWidth(), 120) * 1.6f, Color.parseColor("#FFEB3B"));
        }

        dealDamage(ctx, enemy, damage);

        if (detonate) {
            arcToNearest(ctx, enemy, ex, ey);
        } else if (enemy.isAlive) {
            // ไม่ระเบิด: ติดประจุ (ถ้ามีประจุจากสกิลเดียวกันอยู่แล้วก็ต่ออายุใหม่)
            addMark(ctx, enemy, skill);
        }

        // ระหว่างวงจรทำงานไม่สะสมสแตก ไม่งั้นโจทย์ quiz เด้งขึ้นมาขัดกลางจังหวะ 5 วินาที
        if (!linkActive) ctx.onHitEnemySuccess();
    }

    /** ทำดาเมจ และถ้าวงจรทำงานอยู่ ศัตรูตัวอื่นในวงจรโดนดาเมจเท่ากันด้วย */
    private void dealDamage(BattleContext ctx, SeaEnemy enemy, int damage) {
        enemy.takeDamage(damage);
        if (!enemy.isAlive) clearMark(enemy);

        if (!linkActive || !linked.contains(enemy)) return;

        FrameLayout area = ctx.getGameArea();
        for (SeaEnemy other : new ArrayList<>(linked)) {
            if (other == enemy || !other.isAlive || other.containerView == null) continue;
            if (area != null) {
                View a = enemy.containerView, b = other.containerView;
                spawnArc(area, a.getX() + a.getWidth() / 2f, a.getY() + a.getHeight() / 2f,
                        b.getX() + b.getWidth() / 2f, b.getY() + b.getHeight() / 2f);
            }
            other.takeDamage(damage);
            if (!other.isAlive) clearMark(other);
        }
        if (circuit != null) circuit.pulse();
    }

    /** ไฟฟ้าจากประจุที่ระเบิดสาดไปหาศัตรูที่ใกล้ที่สุด 1 ตัว */
    private void arcToNearest(BattleContext ctx, SeaEnemy from, float fx, float fy) {
        SeaEnemy best = null;
        float bestDist = Float.MAX_VALUE;
        for (int eIdx = 0; eIdx < ctx.getEnemies().size(); eIdx++) {
            SeaEnemy e = ctx.getEnemies().get(eIdx);
            if (e == from || !e.isAlive || e.containerView == null) continue;
            float d = (float) Math.hypot(
                    e.containerView.getX() + e.containerView.getWidth() / 2f - fx,
                    e.containerView.getY() + e.containerView.getHeight() / 2f - fy);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        if (best == null) return;

        FrameLayout area = ctx.getGameArea();
        View bv = best.containerView;
        if (area != null) {
            spawnArc(area, fx, fy, bv.getX() + bv.getWidth() / 2f, bv.getY() + bv.getHeight() / 2f);
        }
        dealDamage(ctx, best, ARC_DAMAGE);
    }

    private void addMark(BattleContext ctx, SeaEnemy enemy, int skill) {
        FrameLayout area = ctx.getGameArea();
        if (area == null || enemy.containerView == null) return;
        clearMark(enemy);

        SparkView view = new SparkView(ctx.getContext(), enemy);
        view.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(view);

        // ประกายตามตัวศัตรูตลอดอายุประจุ แล้วหายไปเอง
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(CHARGE_MS);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            if (!enemy.isAlive || !ctx.isGameRunning()) {
                a.cancel();
                return;
            }
            view.invalidate();
        });
        final Mark mark = new Mark(skill, view, anim);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // เสร็จตามเวลา/ถูกยกเลิก: เอาประกายออก (ถ้าเป็นประจุอันปัจจุบันของศัตรูตัวนี้ ลบออกจากตาราง)
                removeSpark(view);
                if (marks.get(enemy) == mark) {
                    marks.remove(enemy);
                    enemy.setCharged(false);
                }
            }
        });
        marks.put(enemy, mark);
        enemy.setCharged(true);
        anim.start();
    }

    private void clearMark(SeaEnemy enemy) {
        Mark mark = marks.remove(enemy);
        if (mark == null) return;
        enemy.setCharged(false);
        mark.anim.cancel();     // onAnimationEnd จะลบ view ให้
        removeSpark(mark.view);
    }

    private static void removeSpark(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    // =========================================================
    // Ultimate: Circuit Link ต่อศัตรูทั้งหมดเป็นวงจร 5 วินาที
    // =========================================================
    @Override
    public void executeUltimateSkill(BattleContext ctx) {
        FrameLayout area = ctx.getGameArea();
        if (area == null) {
            ctx.onUltimateFinished();
            return;
        }

        // ใช้ซ้ำระหว่างวงจรเดิมทำงานอยู่ = เริ่มนับใหม่
        endLink();

        linked.clear();
        for (int eIdx = 0; eIdx < ctx.getEnemies().size(); eIdx++) {
            SeaEnemy e = ctx.getEnemies().get(eIdx);
            if (e.isAlive && e.containerView != null) linked.add(e);
        }
        if (linked.isEmpty()) {
            ctx.onUltimateFinished();
            return;
        }

        circuit = new CircuitView(ctx.getContext());
        circuit.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(circuit);
        linkActive = true;

        // ปลดล็อกปุ่มสกิลทันที: ผู้เล่นต้องกด Skill 1 / 2 ใส่ศัตรูเองระหว่าง 5 วินาทีนี้
        ctx.onUltimateFinished();
        ctx.showUltimateDuration(LINK_MS);

        final long[] remaining = {LINK_MS};
        final long[] lastNs = {System.nanoTime()};
        linkLoop = ValueAnimator.ofFloat(0f, 1f);
        linkLoop.setDuration(60_000);   // เพดานกันค้าง จบจริงด้วยตัวนับเวลาด้านล่าง
        linkLoop.setInterpolator(new LinearInterpolator());
        linkLoop.addUpdateListener(animation -> {
            long now = System.nanoTime();
            long dtMs = (now - lastNs[0]) / 1_000_000L;
            lastNs[0] = now;

            if (!ctx.isGameRunning()) {
                endLink();
                return;
            }
            if (ctx.isGamePaused()) return;   // quiz ขึ้นอยู่ เวลาวงจรหยุดนับ

            remaining[0] -= dtMs;
            if (remaining[0] <= 0) {
                endLink();
                return;
            }
            circuit.update(linked, remaining[0] / (float) LINK_MS);
        });
        linkLoop.start();
    }

    private void endLink() {
        linkActive = false;
        linked.clear();
        if (linkLoop != null) {
            ValueAnimator l = linkLoop;
            linkLoop = null;
            l.removeAllUpdateListeners();
            l.cancel();
        }
        if (circuit != null) {
            View c = circuit;
            circuit = null;
            c.animate().alpha(0f).setDuration(200).withEndAction(() -> removeSpark(c)).start();
        }
    }

    // =========================================================
    // เอฟเฟกต์ / ตัวช่วย
    // =========================================================

    /**
     * คืนค่า {ปากX, ปากY, ทิศX, ทิศY} ในพิกัดของ gameArea
     * ใช้ matrix จริงของรูปปลา จึงตามทั้งการกลับด้าน (scaleX), การเอียง (rotation)
     * และการลอยขึ้นลง ทิศคำนวณจาก "หลังปาก -> ปาก" = แนวที่ปาก/ดาบชี้อยู่จริง
     */
    private static float[] getMouthAim(View player, float fallbackAngleDeg) {
        View body = player.findViewById(R.id.imgPlayer);

        if (body == null || body.getWidth() == 0) {
            float rad = (float) Math.toRadians(fallbackAngleDeg);
            return new float[]{
                    player.getX() + player.getWidth() / 2f,
                    player.getY() + player.getHeight() / 2f,
                    (float) Math.cos(rad), (float) Math.sin(rad)
            };
        }

        float w = body.getWidth();
        float h = body.getHeight();

        // จุดปาก กับจุดที่อยู่หลังปากบนเส้นเดียวกัน (ไปทางหาง ซึ่งรูปต้นฉบับอยู่ฝั่งขวา)
        float[] pts = {
                w * MOUTH_X_RATIO, h * MOUTH_Y_RATIO,
                w * (MOUTH_X_RATIO + 0.3f), h * MOUTH_Y_RATIO
        };

        // รูปปลา -> playerContainer
        body.getMatrix().mapPoints(pts);
        for (int i = 0; i < pts.length; i += 2) {
            pts[i] += body.getLeft();
            pts[i + 1] += body.getTop();
        }

        // playerContainer -> gameArea
        player.getMatrix().mapPoints(pts);
        for (int i = 0; i < pts.length; i += 2) {
            pts[i] += player.getLeft();
            pts[i + 1] += player.getTop();
        }

        float dx = pts[0] - pts[2];
        float dy = pts[1] - pts[3];
        float len = (float) Math.hypot(dx, dy);

        float dirX, dirY;
        if (len < 1f) {
            // กำลังกลับตัวพอดี (scaleX ใกล้ 0) -> ใช้ทิศจอยสติ๊กแทน
            float rad = (float) Math.toRadians(fallbackAngleDeg);
            dirX = (float) Math.cos(rad);
            dirY = (float) Math.sin(rad);
        } else {
            dirX = dx / len;
            dirY = dy / len;
        }

        return new float[]{pts[0], pts[1], dirX, dirY};
    }

    /** วงคลื่นขยายออกแล้วจาง (ตอนออกตัว / ตอนยิง / ตอนประจุระเบิด) */
    private static void spawnRing(FrameLayout area, float cx, float cy, float size, int color) {
        View ring = new View(area.getContext());
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.TRANSPARENT);
        bg.setStroke(6, color);
        ring.setBackground(bg);
        int d = Math.max(20, (int) size);
        ring.setLayoutParams(new FrameLayout.LayoutParams(d, d));
        ring.setX(cx - d / 2f);
        ring.setY(cy - d / 2f);
        ring.setScaleX(0.3f);
        ring.setScaleY(0.3f);
        area.addView(ring);
        ring.animate().scaleX(1.4f).scaleY(1.4f).alpha(0f).setDuration(320)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> area.removeView(ring)).start();
    }

    /** ประกายกลมเล็กๆ หดและจางหายอยู่กับที่ ทิ้งไว้เป็นหางตามเส้นทาง */
    private static void spawnSpark(FrameLayout area, float cx, float cy, float size, int color) {
        // ใช้วงกลมจากกองที่ใช้ซ้ำ (ไม่สร้าง View ใหม่ทุกประกาย)
        int d = Math.max(8, (int) size);
        GhostPool.ghosts(area).spark(cx + (float) (Math.random() * 10 - 5),
                cy + (float) (Math.random() * 10 - 5), d, color, 300);
    }

    /** ฟ้าผ่าสั้นๆ ระหว่างสองจุด แสดงแวบเดียวแล้วจาง */
    private static void spawnArc(FrameLayout area, float x0, float y0, float x1, float y1) {
        ArcView arc = new ArcView(area.getContext(), x0, y0, x1, y1);
        arc.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(arc);
        arc.animate().alpha(0f).setDuration(350).withEndAction(() -> area.removeView(arc)).start();
    }

    /** เส้นซิกแซกแบบฟ้าผ่า วาดจากจุดหนึ่งไปอีกจุด (เส้นนอกเรืองแสง + แกนขาว) */
    private static void drawJagged(Canvas canvas, Paint glow, Paint core,
                                   float x0, float y0, float x1, float y1, float jag) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Math.max((float) Math.hypot(dx, dy), 1f);
        float nx = -dy / len, ny = dx / len;
        int steps = Math.max(2, (int) (len / 45f));

        Path path = JAGGED_PATH;   // Path เดียวใช้ซ้ำ (วาดบนเธรด UI เท่านั้น)
        path.reset();
        path.moveTo(x0, y0);
        for (int i = 1; i < steps; i++) {
            float t = i / (float) steps;
            float off = (float) (Math.random() * 2 - 1) * jag;
            path.lineTo(x0 + dx * t + nx * off, y0 + dy * t + ny * off);
        }
        path.lineTo(x1, y1);
        canvas.drawPath(path, glow);
        canvas.drawPath(path, core);
    }

    private static final Path JAGGED_PATH = new Path();

    private static Paint strokePaint(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        p.setStrokeWidth(width);
        return p;
    }

    private static class ArcView extends View {
        private final Paint glow = strokePaint(Color.parseColor("#88FFEB3B"), 16f);
        private final Paint core = strokePaint(Color.WHITE, 5f);
        private final float x0, y0, x1, y1;

        ArcView(Context c, float x0, float y0, float x1, float y1) {
            super(c);
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            drawJagged(canvas, glow, core, x0, y0, x1, y1, 30f);
        }
    }

    /** ประกายไฟฟ้าเล็กๆ กะพริบรอบตัวศัตรูที่ติดประจุ (ตามตัวศัตรูไปเอง) */
    private static class SparkView extends View {
        private final Paint glow = strokePaint(Color.parseColor("#99FFEB3B"), 9f);
        private final Paint core = strokePaint(Color.WHITE, 3f);
        private final SeaEnemy enemy;

        SparkView(Context c, SeaEnemy enemy) {
            super(c);
            this.enemy = enemy;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            View ev = enemy.containerView;
            if (ev == null || !enemy.isAlive) return;
            float cx = ev.getX() + ev.getWidth() / 2f;
            float cy = ev.getY() + ev.getHeight() / 2f;
            float r = Math.max(40f, Math.max(ev.getWidth(), ev.getHeight()) * 0.55f);

            for (int i = 0; i < 5; i++) {
                double a = Math.random() * Math.PI * 2;
                float r0 = r * 0.55f;
                float r1 = r * (0.9f + (float) Math.random() * 0.4f);
                float x0 = cx + (float) Math.cos(a) * r0, y0 = cy + (float) Math.sin(a) * r0;
                float x1 = cx + (float) Math.cos(a + 0.25) * r1, y1 = cy + (float) Math.sin(a + 0.25) * r1;
                drawJagged(canvas, glow, core, x0, y0, x1, y1, 8f);
            }
        }
    }

    /** สายไฟที่ต่อศัตรูทั้งหมดเป็นวง + วงนับเวลาถอยหลังรอบศัตรูแต่ละตัว */
    private static class CircuitView extends View {
        private final Paint glow = strokePaint(Color.parseColor("#6600E5FF"), 18f);
        private final Paint core = strokePaint(Color.parseColor("#FFFFF59D"), 5f);
        private final Paint timer = strokePaint(Color.parseColor("#FFFFEB3B"), 8f);
        private final RectF oval = new RectF();
        private final float[][] pts = new float[16][4];   // {x, y, รัศมี, มุม} ของศัตรูแต่ละตัวในวงจร
        private List<SeaEnemy> nodes = new ArrayList<>();
        private float fraction = 1f;
        private float pulse = 0f;

        CircuitView(Context c) {
            super(c);
        }

        void update(List<SeaEnemy> nodes, float fraction) {
            this.nodes = nodes;
            this.fraction = fraction;
            invalidate();
        }

        /** โดนศัตรูในวงจร: สายไฟสว่างและหนาขึ้นวูบหนึ่ง */
        void pulse() {
            pulse = 1f;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            // ใช้อาร์เรย์ที่สร้างไว้แล้วซ้ำทุกเฟรม (ไม่ new List/float[]/comparator ใน onDraw)
            int n = 0;
            float mx = 0f, my = 0f;
            for (int eIdx = 0; eIdx < nodes.size() && n < pts.length; eIdx++) {
                SeaEnemy e = nodes.get(eIdx);
                if (!e.isAlive || e.containerView == null) continue;
                View ev = e.containerView;
                float x = ev.getX() + ev.getWidth() / 2f;
                float y = ev.getY() + ev.getHeight() / 2f;
                float[] p = pts[n++];
                p[0] = x;
                p[1] = y;
                p[2] = Math.max(40f, Math.max(ev.getWidth(), ev.getHeight()) * 0.6f);
                p[3] = 0f;
                mx += x;
                my += y;
            }
            if (n == 0) return;
            mx /= n;
            my /= n;

            // เรียงตามมุมรอบจุดกลาง เพื่อต่อสายเป็นวงปิดไม่ไขว้กัน (insertion sort ในอาร์เรย์เดิม)
            for (int i = 0; i < n; i++) pts[i][3] = (float) Math.atan2(pts[i][1] - my, pts[i][0] - mx);
            for (int i = 1; i < n; i++) {
                float[] key = pts[i];
                int j = i - 1;
                while (j >= 0 && pts[j][3] > key[3]) {
                    pts[j + 1] = pts[j];
                    j--;
                }
                pts[j + 1] = key;
            }

            // ช่วงท้าย (เหลือน้อยกว่า 25%) สายกะพริบเตือนว่าใกล้หมดเวลา
            boolean blink = fraction < 0.25f && ((int) (System.nanoTime() / 120_000_000L) % 2 == 0);
            int alpha = blink ? 90 : 255;
            glow.setAlpha(alpha);
            core.setAlpha(alpha);
            glow.setStrokeWidth(18f + 14f * pulse);
            core.setStrokeWidth(5f + 4f * pulse);

            if (n >= 2) {
                int segments = n == 2 ? 1 : n;
                for (int i = 0; i < segments; i++) {
                    float[] a = pts[i];
                    float[] b = pts[(i + 1) % n];
                    drawJagged(canvas, glow, core, a[0], a[1], b[0], b[1], 16f);
                }
            }

            for (int i = 0; i < n; i++) {
                float[] p = pts[i];
                oval.set(p[0] - p[2], p[1] - p[2], p[0] + p[2], p[1] + p[2]);
                timer.setAlpha(alpha);
                canvas.drawArc(oval, -90f, 360f * fraction, false, timer);
            }

            pulse = Math.max(0f, pulse - 0.06f);
        }
    }

    /** คลื่นไฟฟ้าทรงแคปซูล มีแสงเรืองรอบ วาดเอง ไม่ต้องใช้รูป */
    private static class BoltView extends View {
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        BoltView(Context context) {
            super(context);
            glowPaint.setColor(Color.parseColor("#77FFEB3B"));
            corePaint.setColor(Color.parseColor("#FFFDE7"));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();

            // แสงเรืองรอบนอก
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, h / 2f, h / 2f, glowPaint);

            // แกนกลางสว่าง (ค่อนไปทางหัวคลื่น)
            float inset = h * 0.28f;
            rect.set(w * 0.25f, inset, w - inset, h - inset);
            float r = (h - inset * 2f) / 2f;
            canvas.drawRoundRect(rect, r, r, corePaint);
        }
    }

    /** แถบแสงไล่ระดับจากจุดออกตัวถึงหัวปลา (หางจาง หัวสว่าง) */
    private static class StreakView extends View {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private static final int GLOW_FROM = 0x00FFEB3B, GLOW_TO = 0x66FFEB3B;
        private static final int CORE_FROM = 0x00FFFFFF, CORE_TO = 0xF0FFFFFF;

        private final float x0, y0;
        private float x1, y1;
        private final LinearGradient glowShader =
                new LinearGradient(0f, 0f, 1f, 0f, GLOW_FROM, GLOW_TO, Shader.TileMode.CLAMP);
        private final LinearGradient coreShader =
                new LinearGradient(0f, 0f, 1f, 0f, CORE_FROM, CORE_TO, Shader.TileMode.CLAMP);
        private final Matrix gradientMatrix = new Matrix();

        StreakView(Context context, float x0, float y0) {
            super(context);
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x0;
            this.y1 = y0;
            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeCap(Paint.Cap.ROUND);
            core.setStyle(Paint.Style.STROKE);
            core.setStrokeCap(Paint.Cap.ROUND);
            glow.setStrokeWidth(44f);
            core.setStrokeWidth(14f);
            glow.setShader(glowShader);
            core.setShader(coreShader);
        }

        void setHead(float x, float y) {
            x1 = x;
            y1 = y;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float dx = x1 - x0, dy = y1 - y0;
            float len = (float) Math.hypot(dx, dy);
            if (len < 2f) return;
            // ไล่สีหน่วยยาว 1 แล้วย้ายด้วย matrix ให้พาดตามเส้น (ไม่สร้าง LinearGradient ใหม่ทุกเฟรม)
            gradientMatrix.setScale(len, 1f);
            gradientMatrix.postRotate((float) Math.toDegrees(Math.atan2(dy, dx)));
            gradientMatrix.postTranslate(x0, y0);
            glowShader.setLocalMatrix(gradientMatrix);
            coreShader.setLocalMatrix(gradientMatrix);
            canvas.drawLine(x0, y0, x1, y1, glow);
            canvas.drawLine(x0, y0, x1, y1, core);
        }
    }

    private static boolean isColliding(View v1, View v2) {
        if (v1 == null || v2 == null) return false;
        // getGlobalVisibleRect คืน false เมื่อ view ยังไม่ได้ layout (ขนาด 0) หรืออยู่นอกจอ
        // และกรณีขนาด 0 จะ "ไม่เขียนค่า" ลง rect เลย -> tmpRect (static) ค้างค่าของครั้งก่อน
        if (!v1.getGlobalVisibleRect(tmpRect1)) return false;
        if (!v2.getGlobalVisibleRect(tmpRect2)) return false;
        return Rect.intersects(tmpRect1, tmpRect2);
    }
}
