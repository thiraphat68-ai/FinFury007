// =====================================================================================
// [คนที่ 2 - Model ฮีโร่และสกิล]  ไฟล์: Pufferfish.java  (315 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/Pufferfish.java
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   ฮีโร่ตัวที่ 2 "Pufferfish" (ปักเป้า) วิชา "เคมี" (Chemistry) สายถึกและช้า
//     - Skill 1 Inflate      พองตัว 1 วินาที ศัตรูที่ชนโดน 2 ดาเมจ  (เรียกฟังก์ชันกลางใน SkillEffects)
//     - Skill 2 Venom Spray  พ่นพิษเป็นกรวยจากปาก ดาเมจ 2          (เรียกฟังก์ชันกลางใน SkillEffects)
//     - Ultimate Toxic Gulp  โชว์วงเล็ง ผู้เล่นขยับให้ศัตรูเข้าวง -> พองตัวดูดทุกตัวเข้าปาก
//                            บีบ 6 ครั้ง ครั้งละ 2 ดาเมจ แล้วคายออก
//   ใช้ปุ่ม ULT แยก (usesUltimateButton = true)
//
// [ผังไฟล์] สเตตัส -> ค่าคงที่ Ultimate -> constructor/ข้อความสกิล -> useSkill1/2 (สั้น ๆ)
//           -> executeUltimateSkill (ช่วงเล็ง) -> findInRange -> suckIn (ดูด) -> chew (เคี้ยว) -> spitOut (คาย)
//           -> AimView (วาดวงเล็ง) -> spawnSuctionRing (วงแรงดูด)
//
// [จะเพิ่ม/แก้อะไรบ่อย ๆ]
//   - ปรับความยาก Ultimate: แก้ค่าคงที่ใต้หัวข้อ "Ultimate" (ระยะวง เวลาเล็ง จำนวนครั้งเคี้ยว ดาเมจ)
//   - ปรับสกิล 1/2: แก้ตัวเลขที่ส่งให้ SkillEffects.inflate / venomSpray (บรรทัด useSkill1/useSkill2)
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
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class Pufferfish extends Hero {
    // [สเตตัสของ Pufferfish] เลือด 150 (ถึกที่สุด) / ว่าย 0.8 เท่า (ช้า) / ต้องสะสม 13 ฮิต
    //   คูลดาวน์ Skill 1 = 3 วินาที , Skill 2 = 6 วินาที  [แก้ได้โดยเปลี่ยนตัวเลข]
    // ---- สเตตัสพื้นฐาน (สมดุล) ----
    @Override public int getMaxHp() { return 150; }
    @Override public float getBaseSpeedMultiplier() { return 0.8f; }
    @Override public int getStackNeeded() { return 13; }
    @Override public long getSkill1CooldownMs() { return 3000; }
    @Override public long getSkill2CooldownMs() { return 6000; }


    // [ค่าคงที่ Ultimate: Toxic Gulp]
    //   SUCK_RANGE      รัศมีวงเล็งรอบตัว (px) ศัตรูในวงเท่านั้นถูกดูด
    //   AIM_MIN_MS      ต้องเล็งอย่างน้อยกี่ ms ก่อนดูด (วงในค่อย ๆ ขยายเต็มตามเวลานี้)
    //   AIM_MAX_MS      ถ้าไม่มีศัตรูเข้าวงจนถึงเวลานี้ สกิลหมดอายุ (ไม่เสียแต้มสแตก เพราะ onUltimateFinished ถูกเรียก)
    //   INFLATE_SCALE   ขยายตัวกี่เท่าตอนดูด
    //   SUCK_MS         เวลาที่ศัตรูถูกดูดเข้าปาก
    //   CHEW_TICKS      จำนวนครั้งที่บีบ (ทำดาเมจ) | CHEW_INTERVAL_MS ช่วงห่างระหว่างครั้ง | CHEW_DAMAGE ดาเมจต่อครั้งต่อตัว
    //   SPIT_DISTANCE   ระยะที่คายศัตรูออกจากจุดเดิม
    //   [คำนวณดาเมจรวมสูงสุดต่อศัตรู 1 ตัว = CHEW_TICKS x CHEW_DAMAGE = 12]
    // ---------- Ultimate: พองตัวดูดศัตรูทุกตัวที่อยู่ในระยะเข้าปาก ----------
    private static final float SUCK_RANGE = 380f;          // px รัศมีวงเล็ง
    private static final long AIM_MIN_MS = 2000;           // เวลาเล็งขั้นต่ำก่อนดูด
    private static final long AIM_MAX_MS = 6000;           // ไม่มีศัตรูเข้าวงเลยถึงเวลานี้ = สกิลหมดอายุ
    private static final float INFLATE_SCALE = 1.9f;
    private static final long SUCK_MS = 450;               // เวลาที่ศัตรูถูกดูดเข้าปาก
    private static final int CHEW_TICKS = 6;               // จำนวนครั้งที่ทำดาเมจขณะอยู่ในปาก
    private static final long CHEW_INTERVAL_MS = 500;
    private static final int CHEW_DAMAGE = 2;              // ต่อศัตรูหนึ่งตัวต่อหนึ่งครั้ง
    private static final float SPIT_DISTANCE = 240f;

    // [handler] ตัวหน่วงเวลาบนเธรดหลัก (postDelayed) ใช้ต่อคิวขั้นตอน: ดูดเสร็จ -> เคี้ยวทีละครั้ง -> คาย
    private final Handler handler = new Handler(Looper.getMainLooper());

    // [constructor] ชื่อ "Pufferfish" วิชา "Chemistry" (QuestionBank.forSubject ตรวจคำว่า chem เพื่อเลือกโจทย์เคมี)
    public Pufferfish() {
        super("Pufferfish", "Chemistry");
    }

    // [ปุ่ม ULT แยก] true = ตอบ quiz ถูกแล้วผู้เล่นกดปุ่ม ULT เอง
    @Override
    public boolean usesUltimateButton() {
        return true;
    }

    // [ข้อความสกิล] ชื่อ ไอคอน คำอธิบาย แสดงบนปุ่มและหน้าเลือกฮีโร่ (เปลี่ยนข้อความได้โดยไม่กระทบการทำงาน)
    //   [ระวัง] ถ้าแก้ตัวเลขดาเมจ/เวลาในสกิล ให้แก้ข้อความอธิบายให้ตรงกัน
    @Override public String getSkill1Name() { return "Inflate"; }
    @Override public String getSkill1Icon() { return "🐡"; }
    @Override public String getSkill1Description() { return "พองตัว นาน 1 วินาที ศัตรูที่ชนโดน 2 ดาเมจ"; }

    @Override public String getSkill2Name() { return "Venom Spray"; }
    @Override public String getSkill2Icon() { return "☠️"; }
    @Override public String getSkill2Description() { return "พ่นพิษสีม่วงเป็นกรวยจากปาก ดาเมจ 2"; }

    @Override public String getUltimateName() { return "Toxic Gulp"; }
    @Override public String getUltimateIcon() { return "🌀"; }
    @Override public String getUltimateDescription() { return "พองตัวดูดศัตรูในวงเข้าปาก บีบ 6 ครั้ง ครั้งละ 2 ดาเมจ แล้วคายออก"; }

    // [Skill 1: Inflate] ไม่เขียนเอง แต่เรียกฟังก์ชันกลาง SkillEffects.inflate(ctx, ขยายกี่เท่า, เวลาค้างมิลลิวินาที, ดาเมจ)
    //   ที่นี่คือ 2.2 เท่า ค้าง 1000 ms ดาเมจ 2   [แก้ยังไง] เปลี่ยน 3 ตัวเลขนี้ได้เลย รายละเอียดดู SkillEffects.java
    @Override
    public void useSkill1(BattleContext ctx) {
        // พองตัวขยาย 2.2 เท่า ค้าง 1 วินาที ศัตรูที่โดนตัวที่พองได้ดาเมจ 2
        SkillEffects.inflate(ctx, 2.2f, 1000, 2);
    }

    // [Skill 2: Venom Spray] เรียก SkillEffects.venomSpray(ctx, ระยะ px, มุมกรวย องศา, เวลา ms, ดาเมจ)
    //   ที่นี่คือ 600 px, กรวย 50 องศา, 0.6 วินาที, ดาเมจ 2
    @Override
    public void useSkill2(BattleContext ctx) {
        // พ่นพิษออกจากปาก ระยะ 600 px กรวย 50° นาน 0.6 วินาที ดาเมจ 2
        SkillEffects.venomSpray(ctx, 600f, 50f, 600, 2);
    }

    // =========================================================
    // Ultimate: โชว์วงระยะ ผู้เล่นขยับตัวเล็งเอง -> พองตัวดูดศัตรูทุกตัวที่อยู่ในวงเข้าปาก
    // ทำดาเมจต่อเนื่อง แล้วคายออกมา
    // =========================================================
    // [Ultimate ช่วงที่ 1: เล็ง] (ผู้เล่นยังเดินได้ ต้องขยับให้ศัตรูเข้าวง)
    //   1) สร้าง AimView (วงเล็ง) 2) aimLoop ไล่เวลาสูงสุด AIM_MAX_MS ทุกเฟรม:
    //      คำนวณศัตรูในวง (findInRange) -> อัปเดตภาพวงเล็ง
    //      ถ้ามีศัตรูในวง และเล็งนานเกิน AIM_MIN_MS และยังไม่ยิง -> fired=true , ลบวงเล็ง , เรียก suckIn
    //   3) ถ้าหมดเวลาโดยไม่มีศัตรูเข้าวง -> ลบวงเล็ง และ ctx.onUltimateFinished() (คืนสถานะ)
    //   [ระวัง] ทุกทางออกของสกิลต้องจบด้วย ctx.onUltimateFinished() ไม่งั้นปุ่มสกิลกดไม่ได้ค้าง
    @Override
    public void executeUltimateSkill(BattleContext ctx) {
        FrameLayout area = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (area == null || player == null) {
            ctx.onUltimateFinished();
            return;
        }

        final AimView aim = new AimView(ctx.getContext(), SUCK_RANGE);
        aim.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        area.addView(aim);

        // ช่วงเล็ง: ไม่ล็อกการเคลื่อนที่ ผู้เล่นขยับปลาให้ศัตรูเข้าวงได้เอง
        ValueAnimator aimLoop = ValueAnimator.ofFloat(0f, 1f);
        aimLoop.setDuration(AIM_MAX_MS);
        aimLoop.setInterpolator(new LinearInterpolator());
        final boolean[] fired = {false};
        final List<SeaEnemy> rangeBuf = new ArrayList<>();

        aimLoop.addUpdateListener(animation -> {
            if (!ctx.isGameRunning()) {
                animation.cancel();
                return;
            }
            float cx = player.getX() + player.getWidth() / 2f;
            float cy = player.getY() + player.getHeight() / 2f;
            List<SeaEnemy> inRange = findInRange(ctx, cx, cy, rangeBuf);

            long elapsed = (long) (animation.getAnimatedFraction() * AIM_MAX_MS);
            aim.update(cx, cy, inRange, Math.min(1f, elapsed / (float) AIM_MIN_MS));

            if (!inRange.isEmpty() && elapsed >= AIM_MIN_MS && !fired[0]) {
                fired[0] = true;
                animation.cancel();
                area.removeView(aim);
                suckIn(ctx, player, new ArrayList<>(inRange));   // สำเนา: rangeBuf ถูกใช้ซ้ำทุกเฟรม
            }
        });
        aimLoop.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (fired[0]) return;
                // หมดเวลาเล็ง (หรือเกมจบ) โดยไม่มีศัตรูเข้าวง
                area.removeView(aim);
                if (ctx.isGameRunning()) ctx.onUltimateFinished();
            }
        });
        aimLoop.start();
    }

    // [findInRange] คืนรายชื่อศัตรูที่ยังมีชีวิต ไม่ได้ถูกดูดอยู่ และระยะจากผู้เล่นไม่เกิน SUCK_RANGE
    //   ใช้ลิสต์ result เดิมซ้ำทุกเฟรม (ล้างแล้วเติมใหม่) ไม่สร้างลิสต์ใหม่ จึงต้อง "ทำสำเนา" ก่อนนำไปใช้ต่อ (ดูตอนเรียก suckIn)
    /** ศัตรูที่ยังมีชีวิตทุกตัวที่อยู่ในวงระยะ */
    private static List<SeaEnemy> findInRange(BattleContext ctx, float cx, float cy, List<SeaEnemy> result) {
        result.clear();   // ลิสต์เดิมใช้ซ้ำทุกเฟรม
        for (int eIdx = 0; eIdx < ctx.getEnemies().size(); eIdx++) {
            SeaEnemy e = ctx.getEnemies().get(eIdx);
            if (!e.isAlive || e.isSwallowed() || e.containerView == null) continue;
            float ex = e.containerView.getX() + e.containerView.getWidth() / 2f;
            float ey = e.containerView.getY() + e.containerView.getHeight() / 2f;
            if (Math.hypot(ex - cx, ey - cy) <= SUCK_RANGE) result.add(e);
        }
        return result;
    }

    // [Ultimate ช่วงที่ 2: ดูด] ล็อกการเดิน พองตัว (INFLATE_SCALE) เสกวงแรงดู 3 วง
    //   ศัตรูแต่ละตัว: setSwallowed(true) (บอกศัตรูว่าถูกกลืน ให้ AI หยุดทำงาน) แล้วเลื่อนเข้าหากลางปากและหดเหลือ 0.35 เท่า
    //   จากนั้นรอ SUCK_MS แล้วเข้าช่วงเคี้ยว chew(tick=0)
    private void suckIn(BattleContext ctx, View player, List<SeaEnemy> victims) {
        FrameLayout area = ctx.getGameArea();
        ctx.setSkillLock(true);

        float cx = player.getX() + player.getWidth() / 2f;
        float cy = player.getY() + player.getHeight() / 2f;

        // พองตัว + วงแรงดูดหดเข้าหาปาก
        player.animate().scaleX(INFLATE_SCALE).scaleY(INFLATE_SCALE).setDuration(300)
                .setInterpolator(new DecelerateInterpolator()).start();
        for (int i = 0; i < 3; i++) spawnSuctionRing(area, cx, cy, i * 140L);

        // ศัตรูทุกตัวถูกดูดเข้าไปรวมกันตรงกลางปาก (กระจายเล็กน้อยไม่ให้ซ้อนทับสนิท)
        for (SeaEnemy e : victims) {
            e.setSwallowed(true);
            View ev = e.containerView;
            float mouthX = cx - ev.getWidth() / 2f + (float) (Math.random() * 60 - 30);
            float mouthY = cy - ev.getHeight() / 2f + (float) (Math.random() * 60 - 30);
            ev.animate().x(mouthX).y(mouthY).scaleX(0.35f).scaleY(0.35f).alpha(0.75f)
                    .setDuration(SUCK_MS).setInterpolator(new AccelerateInterpolator()).start();
        }

        handler.postDelayed(() -> chew(ctx, player, victims, 0), SUCK_MS);
    }

    // [Ultimate ช่วงที่ 3: เคี้ยว] เรียกตัวเองซ้ำทุก CHEW_INTERVAL_MS
    //   ทุกครั้ง: ตัวปลาเต้นตุบ + ศัตรูทุกตัวที่ยังมีชีวิตในปากโดน CHEW_DAMAGE
    //   จบเมื่อครบ CHEW_TICKS หรือศัตรูในปากตายหมด แล้วเรียก spitOut
    //   เกมจบกลางทาง (isGameRunning = false) = หยุดต่อคิวทันที
    private void chew(BattleContext ctx, View player, List<SeaEnemy> victims, int tick) {
        if (!ctx.isGameRunning()) return;

        boolean anyAlive = false;
        for (SeaEnemy e : victims) {
            if (e.isAlive) {
                anyAlive = true;
                break;
            }
        }
        if (tick >= CHEW_TICKS || !anyAlive) {
            spitOut(ctx, player, victims);
            return;
        }

        // ตัวปลาเต้นตุบตามจังหวะเคี้ยว แล้วทุกตัวในปากโดนดาเมจ
        player.animate().scaleX(INFLATE_SCALE * 1.12f).scaleY(INFLATE_SCALE * 1.12f).setDuration(120)
                .withEndAction(() -> player.animate()
                        .scaleX(INFLATE_SCALE).scaleY(INFLATE_SCALE).setDuration(120).start())
                .start();
        for (SeaEnemy e : victims) {
            if (e.isAlive) e.takeDamage(CHEW_DAMAGE);
        }

        handler.postDelayed(() -> chew(ctx, player, victims, tick + 1), CHEW_INTERVAL_MS);
    }

    // [Ultimate ช่วงที่ 4: คาย] ยุบตัวกลับ 1 เท่าใน 350 ms แล้วปลดล็อก + ctx.onUltimateFinished()
    //   ศัตรูที่ยังรอด: setSwallowed(false) กระจายคายออกรอบตัวเป็นวงกลม (มุมเท่ากัน) ระยะ SPIT_DISTANCE บีบไม่ให้ออกนอกจอ
    private void spitOut(BattleContext ctx, View player, List<SeaEnemy> victims) {
        // ยุบตัวกลับ แล้วคายตัวที่ยังรอดออกไปคนละทิศรอบตัว
        player.animate().scaleX(1f).scaleY(1f).setDuration(350)
                .withEndAction(() -> {
                    ctx.setSkillLock(false);
                    ctx.onUltimateFinished();
                }).start();

        int alive = 0;
        for (SeaEnemy e : victims) {
            if (e.isAlive) alive++;
        }

        int i = 0;
        for (SeaEnemy e : victims) {
            e.setSwallowed(false);
            View ev = e.containerView;
            if (!e.isAlive || ev == null) continue;

            double ang = Math.PI * 2 * i / Math.max(alive, 1);
            i++;
            float outX = ev.getX() + (float) Math.cos(ang) * SPIT_DISTANCE;
            float outY = ev.getY() + (float) Math.sin(ang) * SPIT_DISTANCE;
            View area = (View) ev.getParent();
            if (area != null && area.getWidth() > 0) {
                outX = Math.max(0f, Math.min(area.getWidth() - ev.getWidth(), outX));
                outY = Math.max(0f, Math.min(area.getHeight() - ev.getHeight(), outY));
            }
            ev.animate().x(outX).y(outY).scaleX(1f).scaleY(1f).alpha(1f).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    // [AimView] วาดวงเล็ง: วงเต็มโปร่งใสสีม่วง + วงเส้นประหมุนช้า ๆ (phase) + วงในขยายตามเวลาชาร์จ (charge 0..1)
    //   และวาดกากบาทแดงล็อกทับศัตรูทุกตัวที่อยู่ในวง
    //   dashes = สร้างลายเส้นประไว้ล่วงหน้า 58 แบบ แล้วเลือกตามมุมหมุน (ไม่สร้างใหม่ทุกเฟรม ประหยัดเครื่อง)
    //   [แก้ยังไง] สี: แก้ Color.parseColor | ความหนาเส้น: setStrokeWidth
    /** วงระยะดูด: วงเส้นประหมุนรอบตัวปลา + กากบาทแดงล็อกบนศัตรูทุกตัวที่อยู่ในวง */
    private static class AimView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lock = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float range;
        private float cx, cy, charge, phase;
        private List<SeaEnemy> targets = new ArrayList<>();
        private final DashPathEffect[] dashes = new DashPathEffect[58];   // 36 + 22 = 1 รอบของลายเส้นประ สร้างครั้งเดียว

        AimView(Context context, float range) {
            super(context);
            this.range = range;
            for (int i = 0; i < dashes.length; i++) dashes[i] = new DashPathEffect(new float[]{36f, 22f}, i);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(Color.parseColor("#229C27B0"));
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(6f);
            ring.setColor(Color.parseColor("#CCCE93D8"));
            lock.setStyle(Paint.Style.STROKE);
            lock.setStrokeWidth(7f);
            lock.setStrokeCap(Paint.Cap.ROUND);
            lock.setColor(Color.parseColor("#FF5252"));
        }

        void update(float cx, float cy, List<SeaEnemy> targets, float charge) {
            this.cx = cx;
            this.cy = cy;
            this.targets = targets;
            this.charge = charge;
            phase += 6f;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            canvas.drawCircle(cx, cy, range, fill);

            // เส้นประวงหมุนช้าๆ ให้รู้ว่าเป็นวงเล็ง
            ring.setPathEffect(dashes[(int) phase % dashes.length]);
            canvas.drawCircle(cx, cy, range, ring);

            // วงใน ค่อยๆ ขยายเต็มวงตามเวลาที่ชาร์จ
            canvas.drawCircle(cx, cy, range * charge, ring);

            for (SeaEnemy t : targets) {
                if (t.containerView == null) continue;
                View ev = t.containerView;
                float tx = ev.getX() + ev.getWidth() / 2f;
                float ty = ev.getY() + ev.getHeight() / 2f;
                float r = Math.max(ev.getWidth(), ev.getHeight()) * 0.6f;

                canvas.drawCircle(tx, ty, r, lock);
                float g = r * 0.45f;
                canvas.drawLine(tx - r - g, ty, tx - r + g, ty, lock);
                canvas.drawLine(tx + r - g, ty, tx + r + g, ty, lock);
                canvas.drawLine(tx, ty - r - g, tx, ty - r + g, lock);
                canvas.drawLine(tx, ty + r - g, tx, ty + r + g, lock);
            }
        }
    }

    // [spawnSuctionRing] วงกลมเส้นสีม่วงขนาด 700 px โผล่หลังหน่วงเวลา delayMs แล้วหดเหลือ 0.1 เท่าพร้อมจางในเวลา SUCK_MS
    //   = ภาพแรงดูดเข้าหาปาก  เรียก 3 ครั้งเหลื่อมกัน 140 ms
    /** วงสีม่วงใหญ่ๆ หดเข้าหาปาก ให้ดูเหมือนแรงดูด (เหลื่อมเวลากันหลายวง) */
    private static void spawnSuctionRing(FrameLayout area, float cx, float cy, long delayMs) {
        View ring = new View(area.getContext());
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.TRANSPARENT);
        bg.setStroke(8, Color.parseColor("#CCAB47BC"));
        ring.setBackground(bg);
        int d = 700;
        ring.setLayoutParams(new FrameLayout.LayoutParams(d, d));
        ring.setX(cx - d / 2f);
        ring.setY(cy - d / 2f);
        ring.setAlpha(0f);
        area.addView(ring);
        ring.animate().alpha(1f).setDuration(1).setStartDelay(delayMs).withEndAction(() ->
                ring.animate().scaleX(0.1f).scaleY(0.1f).alpha(0f).setDuration(SUCK_MS)
                        .setStartDelay(0).setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> area.removeView(ring)).start()).start();
    }
}
