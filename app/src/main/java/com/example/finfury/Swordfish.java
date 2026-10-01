package com.example.finfury;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Swordfish มีสกิล 1 / สกิล 2 ของตัวเอง (ไม่ใช้ SkillEffects.dash / projectile)
 * ฮีโร่ตัวอื่นยังใช้ SkillEffects แบบเดิม ไม่ได้รับผลกระทบ
 */
public class Swordfish extends Hero {

    // ---------- Skill 1: Dash ----------
    private static final float DASH_DISTANCE = 300f;   // px
    private static final long DASH_DURATION_MS = 150;
    private static final int DASH_DAMAGE = 2;

    // ---------- Skill 2: Projectile (ลำพลังน้ำยิงออกจากปาก) ----------
    private static final int BOLT_LENGTH = 46;           // px ความยาวลูกกระสุน
    private static final int BOLT_THICKNESS = 16;        // px ความหนาลูกกระสุน
    private static final long PROJECTILE_DURATION_MS = 550;
    private static final float PROJECTILE_DISTANCE = 1100f;
    private static final int PROJECTILE_DAMAGE = 1;

    // ตำแหน่งปากบนรูป hero_1 แบบยังไม่กลับด้าน (รูปต้นฉบับหันซ้าย)
    // 0 = ขอบซ้ายของรูป, 1 = ขอบขวา / 0 = ขอบบน, 1 = ขอบล่าง
    // ถ้ากระสุนยังไม่ตรงปากเป๊ะ ปรับ 2 ค่านี้ได้เลย
    //   - ออกเลยไปทางปลายดาบ  -> เพิ่ม MOUTH_X_RATIO
    //   - ออกค่อนไปทางตัว/หาง   -> ลด MOUTH_X_RATIO
    //   - สูง/ต่ำไป             -> ปรับ MOUTH_Y_RATIO
    private static final float MOUTH_X_RATIO = 0.38f;
    private static final float MOUTH_Y_RATIO = 0.55f;

    // ---------- Ultimate ----------
    private static final int ULTIMATE_DAMAGE = 5;

    private static final Rect tmpRect1 = new Rect();
    private static final Rect tmpRect2 = new Rect();

    public Swordfish() {
        super("Swordfish", "Calculus");
    }

    /** Swordfish ใช้ปุ่ม Ultimate แยก (ไม่ปล่อยอัตโนมัติหลังตอบถูก) */
    @Override
    public boolean usesUltimateButton() {
        return true;
    }

    // =========================================================
    // Skill 1: พุ่งชนทะลุไปตามทิศจอยสติ๊ก แล้วหยุดอยู่ตรงนั้น (ไม่เด้งกลับ)
    // =========================================================
    @Override
    public void useSkill1(BattleContext ctx) {
        FrameLayout gameArea = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (gameArea == null || player == null) return;

        ctx.setSkillLock(true);

        float rad = (float) Math.toRadians(ctx.getPlayerAngle());

        // ทำงานกับ translation ตรงๆ อย่างเดียว
        // (ของเดิมเอา getX() ซึ่งรวม translation อยู่แล้ว ไปบวก translation ซ้ำ
        //  ทำให้ตำแหน่งแกว่งไปมาระหว่างพุ่ง และจบไม่ถึงเป้า ดูเหมือนเด้งกลับ)
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

        ValueAnimator dash = ValueAnimator.ofFloat(0f, 1f);
        dash.setDuration(DASH_DURATION_MS);
        dash.setInterpolator(new DecelerateInterpolator());

        dash.addUpdateListener(animation -> {
            float p = (float) animation.getAnimatedValue();
            player.setTranslationX(startTx + (finalEndTx - startTx) * p);
            player.setTranslationY(startTy + (finalEndTy - startTy) * p);

            // ชนศัตรูตัวไหนก็ทำดาเมจ ตัวละ 1 ครั้ง แล้วพุ่งต่อทะลุไปเลย
            for (SeaEnemy enemy : ctx.getEnemies()) {
                if (enemy.isAlive && enemy.containerView != null
                        && !hitEnemies.contains(enemy)
                        && isColliding(player, enemy.containerView)) {
                    hitEnemies.add(enemy);
                    enemy.takeDamage(DASH_DAMAGE);
                    ctx.onHitEnemySuccess();
                }
            }
        });

        dash.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ctx.setSkillLock(false);
            }
        });

        dash.start();
    }

    // =========================================================
    // Skill 2: ยิงลำพลังออกจากปากตรงๆ ไปตามแนวที่ปากชี้ (แนวเดียวกับดาบ)
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

        // วางให้ "ท้าย" ลูกกระสุนอยู่ที่ปากพอดี แล้วพุ่งออกไปข้างหน้า
        float centerX = mouthX + dirX * (BOLT_LENGTH / 2f);
        float centerY = mouthY + dirY * (BOLT_LENGTH / 2f);
        final float startX = centerX - BOLT_LENGTH / 2f;
        final float startY = centerY - BOLT_THICKNESS / 2f;
        final float targetX = startX + dirX * PROJECTILE_DISTANCE;
        final float targetY = startY + dirY * PROJECTILE_DISTANCE;

        projectile.setX(startX);
        projectile.setY(startY);
        gameArea.addView(projectile);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(PROJECTILE_DURATION_MS);
        animator.setInterpolator(new LinearInterpolator());

        final boolean[] hasHit = {false};

        animator.addUpdateListener(animation -> {
            if (hasHit[0]) return;

            float f = animation.getAnimatedFraction();
            projectile.setX(startX + (targetX - startX) * f);
            projectile.setY(startY + (targetY - startY) * f);

            for (SeaEnemy enemy : ctx.getEnemies()) {
                if (enemy.isAlive && enemy.containerView != null
                        && isColliding(projectile, enemy.containerView)) {
                    hasHit[0] = true;
                    animator.cancel();
                    gameArea.removeView(projectile);
                    enemy.takeDamage(PROJECTILE_DAMAGE);
                    ctx.onHitEnemySuccess();
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
    // Ultimate: กดจากปุ่ม ULT (ปลดล็อกหลังสแตกเต็ม + ตอบ quiz ถูก)
    // =========================================================
    @Override
    public void executeUltimateSkill(BattleContext ctx) {
        SkillEffects.thunderChain(ctx, ULTIMATE_DAMAGE);
    }

    // =========================================================
    // ตัวช่วย
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

    /** ลำพลังน้ำทรงแคปซูล มีแสงเรืองรอบ วาดเอง ไม่ต้องใช้รูป */
    private static class BoltView extends View {
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        BoltView(Context context) {
            super(context);
            glowPaint.setColor(Color.parseColor("#6600E5FF"));
            corePaint.setColor(Color.parseColor("#E0FFFF"));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();

            // แสงเรืองรอบนอก
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, h / 2f, h / 2f, glowPaint);

            // แกนกลางสว่าง (ค่อนไปทางหัวกระสุน)
            float inset = h * 0.28f;
            rect.set(w * 0.25f, inset, w - inset, h - inset);
            float r = (h - inset * 2f) / 2f;
            canvas.drawRoundRect(rect, r, r, corePaint);
        }
    }

    private static boolean isColliding(View v1, View v2) {
        if (v1 == null || v2 == null) return false;
        v1.getGlobalVisibleRect(tmpRect1);
        v2.getGlobalVisibleRect(tmpRect2);
        return Rect.intersects(tmpRect1, tmpRect2);
    }
}