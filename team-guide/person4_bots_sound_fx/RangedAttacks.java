// =====================================================================================
// [คนที่ 4 - บอท เสียง และเอฟเฟกต์]  ไฟล์: RangedAttacks.java  (275 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/RangedAttacks.java
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   "การโจมตีระยะไกลของศัตรู" 2 แบบ เป็นฟังก์ชัน static (final class + constructor private = ห้ามสร้าง object)
//     fireLine   ลำพลังเส้นตรง (ใช้โดยแมงกะพรุน และบอสยิงคลื่นพลัง) ซิกแซกเป็นคลื่นไซน์ได้
//     inkCone    พ่นหมึกเป็นกรวยขยายออก (ใช้โดยหมึก)
//     showInkTelegraph  แถบเตือนรูปกรวยก่อนพ่น
//   ทำงานอิสระหลังปล่อย (ศัตรูกลับไปเดินวนได้ทันที) ชนผู้เล่นแล้วเรียก ctx.damagePlayer + onHit (ผลพิเศษ เช่น สโลว์/สตัน)
//
// [ใครเรียกไฟล์นี้] SeaEnemy.fireRangedAttack / startWindup , KrakenBoss.updateAI (ยิงคลื่น)
//
// [จะแก้/เพิ่มยังไง]
//   ลำพลังใหญ่/เล็ก: BOLT_LENGTH, BOLT_THICKNESS | ชนง่าย/ยาก: BOLT_HIT_RADIUS | กรวยหมึกไกล/กว้าง: INK_RANGE, INK_HALF_ANGLE_DEG
//   เพิ่มการโจมตีแบบใหม่: เพิ่มฟังก์ชัน static ในไฟล์นี้ (ตามแบบ fireLine) แล้วเพิ่มค่าใน enum AttackType ของ SeaEnemy + เรียกใน fireRangedAttack
// =====================================================================================
package com.example.finfury;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

// [คอมเมนต์คลาส - โดยเจ้าของไฟล์เดิม]
/**
 * การโจมตีระยะไกลของศัตรู
 * - แมงกะพรุน: ยิงลำพลังเป็นเส้นตรง
 * - หมึกยักษ์: พ่นหมึกเป็นรูปกรวยที่ขยายกว้างออกไปข้างหน้า
 * ทั้งสองแบบทำงานอิสระหลังปล่อย (ศัตรูกลับไปวนรอบได้ทันที)
 */
public final class RangedAttacks {

    private RangedAttacks() {}

    // [ค่าคงที่ลำพลัง] BOLT_HIT_RADIUS ระยะจากกลางลำพลังถึงกลางผู้เล่นที่นับว่าโดน | BOLT_LENGTH, BOLT_THICKNESS ขนาดที่วาด
    //   LINE_BAND_WIDTH ความกว้างแถบเตือนของลำพลัง (public ให้ SeaEnemy และ KrakenBoss ใช้) | INK_RANGE ระยะกรวยหมึก | INK_HALF_ANGLE_DEG ครึ่งมุมกรวย
    private static final float BOLT_HIT_RADIUS = 45f;   // ระยะจากกึ่งกลางลำพลังถึงกึ่งกลางผู้เล่นที่นับว่าโดน
    private static final float BOLT_LENGTH = 70f;
    private static final float BOLT_THICKNESS = 26f;

    public static final float LINE_BAND_WIDTH = 60f;    // ความกว้างแถบเตือนของลำพลัง
    public static final float INK_RANGE = 640f;        // ระยะไกลสุดของกรวยหมึก
    public static final float INK_HALF_ANGLE_DEG = 32f; // ครึ่งมุมของกรวย (กว้างรวม 64°)

    // [playerCenter] จุดกึ่งกลางผู้เล่นปัจจุบัน (null ถ้ายังไม่มี) ไว้เช็กโดน
    private static float[] playerCenter(BattleContext ctx) {
        View p = ctx.getPlayerContainer();
        if (p == null) return null;
        return new float[]{p.getX() + p.getWidth() / 2f, p.getY() + p.getHeight() / 2f};
    }

    // [fireLine - 3 รูปแบบ overload ซ้อนกัน] เรียกแบบสั้นแล้วส่งต่อไปแบบเต็ม:
    //   (ctx, จุดเริ่ม x0,y0, ทิศ dirX,dirY, ความเร็ว, ดาเมจ) -> + onHit (Runnable ผลพิเศษหลังโดน ส่ง null ได้) -> + waveAmplitude/waveLength (ซิกแซก)
    // ---------------------------------------------------------
    // แมงกะพรุน: ลำพลังเส้นตรง ความเร็ว speed (px/s) บินจนพ้นจอหรือโดนผู้เล่น
    // ---------------------------------------------------------
    public static void fireLine(BattleContext ctx, float x0, float y0, float dirX, float dirY,
                                float speed, int damage) {
        fireLine(ctx, x0, y0, dirX, dirY, speed, damage, null);
    }

    /** เหมือนกันแต่เมื่อโดนผู้เล่นจะเรียก onHit ด้วย (เช่น สโลว์ / สตัน) ส่ง null ได้ */
    public static void fireLine(BattleContext ctx, float x0, float y0, float dirX, float dirY,
                                float speed, int damage, Runnable onHit) {
        fireLine(ctx, x0, y0, dirX, dirY, speed, damage, onHit, 0f, 1f);
    }

    // [fireLine ตัวเต็ม] ขั้นตอน:
    //   1) สร้างลำพลัง = View สี่เหลี่ยมมนสีชมพู ขอบขาว หมุนตามทิศ
    //   2) ระยะบินสูงสุด = เส้นทแยงมุมของพื้นที่เล่น (บินจนพ้นจอ) เวลา = ระยะ / ความเร็ว
    //   3) ValueAnimator ไล่ระยะ d จาก 0 ถึงระยะสูงสุด ทุกเฟรมคำนวณตำแหน่ง = จุดเริ่ม + ทิศ x d + ตั้งฉาก x ออฟเซ็ตไซน์
    //      (ถ้า waveAmplitude > 0 ลำพลังโยกซ้ายขวาเป็นคลื่น และหมุนหัวตามความชันของเส้นโค้ง)
    //   4) เช็กโดนผู้เล่นด้วย "ระยะจากผู้เล่นถึงเส้นทางที่เพิ่งบินผ่านในเฟรมนี้" (distanceToSegment) เพราะลำพลังเร็ว ก้าวต่อเฟรมยาว
    //      โดนครั้งเดียว: ctx.damagePlayer + onHit + เอฟเฟกต์ + หยุด
    //   5) จบ (ชน/พ้นจอ/เกมจบ) ลบลำพลังออกจากจอ
    //   [แก้ยังไง] เปลี่ยนสี: "#F48FB1" | ซิกแซกมาก/น้อย: waveAmplitude/waveLength ที่ผู้เรียกส่งมา (SeaEnemy ใช้ 110 และ 480)
    /**
     * waveAmplitude > 0 = ลำพลังซิกแซกเป็นคลื่นไซน์: แกว่งข้างละ waveAmplitude px รอบละ waveLength px (0 = เส้นตรง)
     */
    public static void fireLine(BattleContext ctx, float x0, float y0, float dirX0, float dirY0,
                                float speed, int damage, Runnable onHit,
                                float waveAmplitude, float waveLength) {
        FrameLayout area = ctx.getGameArea();
        if (area == null) return;

        View bolt = new View(ctx.getContext());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(BOLT_THICKNESS / 2f);
        bg.setColor(Color.parseColor("#F48FB1"));
        bg.setStroke(4, Color.WHITE);
        bolt.setBackground(bg);
        bolt.setLayoutParams(new FrameLayout.LayoutParams((int) BOLT_LENGTH, (int) BOLT_THICKNESS));
        bolt.setRotation((float) Math.toDegrees(Math.atan2(dirY0, dirX0)));
        area.addView(bolt);

        // บินให้พ้นขอบจอ (ใช้เส้นทแยงมุมของพื้นที่เล่นเป็นระยะสูงสุด)
        final float maxDist = (float) Math.hypot(Math.max(area.getWidth(), 1), Math.max(area.getHeight(), 1));
        final long durationMs = (long) (maxDist / speed * 1000f);

        final boolean[] hit = {false};
        final float[] prevDist = {0f};
        final float[] pos = {x0, y0};                 // ตำแหน่งในเฟรมก่อน ใช้เช็กชนตลอดเส้นทางที่เพิ่งบินผ่าน
        final float[] dir = {dirX0, dirY0};

        ValueAnimator anim = ValueAnimator.ofFloat(0f, maxDist);
        anim.setDuration(durationMs);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            if (!ctx.isGameRunning()) {
                a.cancel();
                return;
            }
            float d = (float) a.getAnimatedValue();
            float[] pc = playerCenter(ctx);

            // วิถีคลื่นไซน์: บินตรงตามทิศที่ล็อกไว้ แล้วแกว่งซ้าย-ขวาตั้งฉากกับทิศนั้น (ซิกแซก)
            float phase = (float) (2 * Math.PI * d / waveLength);
            float offset = waveAmplitude * (float) Math.sin(phase);
            float ax = pos[0], ay = pos[1];
            float bx = x0 + dir[0] * d - dir[1] * offset;
            float by = y0 + dir[1] * d + dir[0] * offset;
            pos[0] = bx;
            pos[1] = by;
            if (waveAmplitude > 0f) {   // หันหัวลำพลังตามแนวเส้นโค้ง
                float slope = waveAmplitude * (float) (2 * Math.PI / waveLength) * (float) Math.cos(phase);
                bolt.setRotation((float) Math.toDegrees(Math.atan2(dir[1], dir[0]) + Math.atan(slope)));
            }
            bolt.setX(bx - BOLT_LENGTH / 2f);
            bolt.setY(by - BOLT_THICKNESS / 2f);

            if (!hit[0] && pc != null && !ctx.isGamePaused()) {
                // ลำพลังเร็วมาก ก้าวต่อเฟรมยาว จึงเช็กทั้งช่วงที่บินผ่านในเฟรมนี้
                if (distanceToSegment(pc[0], pc[1], ax, ay, bx, by) <= BOLT_HIT_RADIUS) {
                    hit[0] = true;
                    ctx.damagePlayer(damage);
                    if (onHit != null) onHit.run();
                    HitEffects.playerHit(ctx, ctx.getPlayerContainer(), damage);
                    a.cancel();
                }
            }
            prevDist[0] = d;
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                area.removeView(bolt);
            }
        });
        anim.start();
    }

    // [inkCone] พ่นหมึกเป็นกรวย:
    //   สร้าง ConeView ขยายรัศมีจาก 0 ถึง INK_RANGE ใน 380 ms (ชะลอ) และมุมเริ่มแคบ (35%) แล้วบานออกถึงครึ่งมุมเต็ม
    //   ทุกเฟรมเช็กว่าผู้เล่นอยู่ในกรวยหรือยัง (inCone) ถ้าอยู่และยังไม่เคยโดน = ดาเมจ + onHit + เอฟเฟกต์ (โดนครั้งเดียว)
    //   จบแล้วหมึกค้างครู่และจางหายใน 350 ms | ถูกยกเลิก (เกมจบ) ลบทันที
    // ---------------------------------------------------------
    // หมึกยักษ์: พ่นหมึกเป็นกรวย ขยายทั้งระยะและมุมจากตัวหมึกออกไปทางผู้เล่น
    // ---------------------------------------------------------
    public static void inkCone(BattleContext ctx, float cx, float cy, float dirX, float dirY, int damage) {
        inkCone(ctx, cx, cy, dirX, dirY, damage, null);
    }

    public static void inkCone(BattleContext ctx, float cx, float cy, float dirX, float dirY, int damage,
                               Runnable onHit) {
        FrameLayout area = ctx.getGameArea();
        if (area == null) return;

        final float dirDeg = (float) Math.toDegrees(Math.atan2(dirY, dirX));
        ConeView cone = new ConeView(ctx.getContext(), cx, cy, dirDeg, false);
        cone.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(cone);

        final boolean[] hit = {false};

        ValueAnimator expand = ValueAnimator.ofFloat(0f, 1f);
        expand.setDuration(380);
        expand.setInterpolator(new DecelerateInterpolator());
        expand.addUpdateListener(a -> {
            if (!ctx.isGameRunning()) {
                a.cancel();
                return;
            }
            float p = (float) a.getAnimatedValue();
            float radius = INK_RANGE * p;
            float half = INK_HALF_ANGLE_DEG * (0.35f + 0.65f * p); // เริ่มแคบแล้วบานออกเป็นกรวย
            cone.setShape(radius, half);

            float[] pc = playerCenter(ctx);
            if (!hit[0] && pc != null && !ctx.isGamePaused() && inCone(pc[0], pc[1], cx, cy, dirDeg, radius, half)) {
                hit[0] = true;
                ctx.damagePlayer(damage);
                if (onHit != null) onHit.run();
                HitEffects.playerHit(ctx, ctx.getPlayerContainer(), damage);
            }
        });
        expand.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // ปล่อยให้หมึกค้างครู่หนึ่งแล้วจางหาย
                cone.animate().alpha(0f).setDuration(350).withEndAction(() -> area.removeView(cone)).start();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                area.removeView(cone);
            }
        });
        expand.start();
    }

    // [showInkTelegraph] แถบเตือนรูปกรวยเต็มขนาดสุดท้ายตั้งแต่เริ่ม โปร่งใส 25% ค่อยทึบถึง 100% ตลอดช่วงเตือน (ผู้เล่นรู้ว่าต้องหนีออกจากกรวย)
    //   คืน View ให้ผู้เรียก (SeaEnemy) เป็นคนลบ
    /** แถบเตือนรูปกรวย (โชว์ก่อนพ่นหมึก) ค่อยๆ ชัดขึ้นตลอดช่วงเตือน คืน View ให้ผู้เรียกลบเอง */
    public static View showInkTelegraph(FrameLayout area, float cx, float cy, float dirX, float dirY,
                                        long durationMs) {
        ConeView cone = new ConeView(area.getContext(), cx, cy,
                (float) Math.toDegrees(Math.atan2(dirY, dirX)), true);
        cone.setShape(INK_RANGE, INK_HALF_ANGLE_DEG);
        cone.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        cone.setAlpha(0.25f);
        area.addView(cone);
        cone.animate().alpha(1f).setDuration(durationMs).start();
        return cone;
    }

    // [inCone] เช็กจุดอยู่ในกรวยไหม: ระยะต้องไม่เกินรัศมี และมุมจากทิศกรวยต่างกันไม่เกินครึ่งมุม
    //   (สูตร ((ang - dirDeg + 540) % 360) - 180 ทำให้มุมวนรอบ 360 องศาได้ถูก เช่น 350 กับ 10 ต่างกัน 20)
    // ---------------------------------------------------------
    // ตัวช่วย
    // ---------------------------------------------------------
    private static boolean inCone(float px, float py, float cx, float cy, float dirDeg,
                                  float radius, float halfDeg) {
        float dx = px - cx, dy = py - cy;
        if (Math.hypot(dx, dy) > radius) return false;
        float ang = (float) Math.toDegrees(Math.atan2(dy, dx));
        float diff = Math.abs(((ang - dirDeg + 540f) % 360f) - 180f);
        return diff <= halfDeg;
    }

    // [distanceToSegment] ระยะจากจุดถึงเส้นตรงช่วงหนึ่ง
    private static float distanceToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax, aby = by - ay;
        float len2 = abx * abx + aby * aby;
        float t = len2 < 0.0001f ? 0f : Math.max(0f, Math.min(1f, ((px - ax) * abx + (py - ay) * aby) / len2));
        return (float) Math.hypot(px - (ax + abx * t), py - (ay + aby * t));
    }

    // [ConeView] วาดกรวยด้วย drawArc (ส่วนโค้งแบบ useCenter=true = รูปพัด) โหมด telegraph: ม่วงโปร่งใสขอบเส้นประ
    //   โหมดจริง: ม่วงเข้มทึบ ขอบสว่าง มีวงในเข้มกว่าเป็นเงาให้ดูเป็นหมึก (รัศมี 62%)
    //   setShape(radius, halfDeg) ถูกเรียกทุกเฟรมที่ขยาย
    /** กรวยหมึก: telegraph = แถบม่วงโปร่งใสมีเส้นประขอบ, ไม่ใช่ telegraph = หมึกดำทึบพร้อมขอบม่วง */
    private static class ConeView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final float cx, cy, dirDeg;
        private final boolean telegraph;
        private float radius, halfDeg;

        ConeView(Context c, float cx, float cy, float dirDeg, boolean telegraph) {
            super(c);
            this.cx = cx;
            this.cy = cy;
            this.dirDeg = dirDeg;
            this.telegraph = telegraph;

            fill.setStyle(Paint.Style.FILL);
            edge.setStyle(Paint.Style.STROKE);
            inner.setStyle(Paint.Style.FILL);
            if (telegraph) {
                fill.setColor(Color.parseColor("#55AA00FF"));
                edge.setColor(Color.parseColor("#CCCE93D8"));
                edge.setStrokeWidth(5f);
                edge.setPathEffect(new DashPathEffect(new float[]{26f, 18f}, 0f));
            } else {
                fill.setColor(Color.parseColor("#CC6A1B9A"));
                edge.setColor(Color.parseColor("#FFE1BEE7"));
                edge.setStrokeWidth(4f);
                inner.setColor(Color.parseColor("#992A0845"));
            }
        }

        void setShape(float radius, float halfDeg) {
            this.radius = radius;
            this.halfDeg = halfDeg;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (radius < 1f) return;
            oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(oval, dirDeg - halfDeg, halfDeg * 2f, true, fill);
            if (!telegraph) {
                float r2 = radius * 0.62f;
                oval.set(cx - r2, cy - r2, cx + r2, cy + r2);
                canvas.drawArc(oval, dirDeg - halfDeg * 0.8f, halfDeg * 1.6f, true, inner);
            }
            oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(oval, dirDeg - halfDeg, halfDeg * 2f, true, edge);
        }
    }
}
