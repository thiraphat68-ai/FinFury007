// =====================================================================================
// [คนที่ 4 - บอท เสียง และเอฟเฟกต์]  ไฟล์: HitEffects.java  (481 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/HitEffects.java
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   ศูนย์รวม "เอฟเฟกต์ภาพตอนโจมตีโดน" (final class + static): 
//     impact(ctx, target, damage)        ศัตรูโดนสกิล : วงแหวนกระแทกสีส้ม + ตัวเลขดาเมจลอยขึ้น (-3) + จอสั่น
//     playerHit(ctx, player, damage)     ผู้เล่นโดนตี : วงแหวนสีแดง + ตัวเลขแดง + จอสั่นแรง + จอวาบแดง
//     trail(...)                         เงาตามเส้นทางที่ศัตรูพุ่ง (ใช้ GhostPool)
//     showAttackPath / releaseAttackPath แถบเตือนเส้นทางพุ่ง/ยิงของศัตรู (แถบแดงโปร่ง + เส้นประ + หัวลูกศร)
//   หลักสำคัญ: ทุก View "สร้างครั้งเดียวแล้วใช้ซ้ำ" (Object Pool) ไม่ new ทุกครั้งที่โดน เพื่อให้เกมไม่กระตุก
//      วงแหวน 6 ตัว , ตัวเลขดาเมจ 6 ตัว , แถบเส้นทาง 8 ตัว , แฟลชแดง 1 ตัว , animator จอสั่น 1 ตัว
//   แต่ละ View มีขนาดพอดีกับเอฟเฟกต์ของมัน ไม่ใช้ MATCH_PARENT ทั้งฉาก ยกเว้นแฟลชแดงที่คลุมทั้งพื้นที่เล่น
//
// [ใครเรียก] SeaEnemy.takeDamage (impact) , SeaEnemy ตอนพุ่งโดนผู้เล่น/RangedAttacks/KrakenBoss (playerHit) , SeaEnemy/KrakenBoss (showAttackPath)
//            BattleActivity.onDestroy เรียก release()
//
// [จะแก้ยังไง]
//   สีเอฟเฟกต์: แก้ค่าสีด้านบน (รูปแบบ 0xAARRGGBB) | ความแรงจอสั่น: ตัวเลขใน shake(area, ...) ที่ impact/playerHit
//   ขนาดวงแหวน: dp(c, 70) / dp(c, 55) ใน impact → burst | ดาเมจ "ใหญ่": เงื่อนไข big = damage >= 3 ใน impact
//   ความเร็วตัวเลขลอย: .setDuration(550) ใน damagePopup
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
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;

// [คอมเมนต์คลาส - โดยเจ้าของไฟล์เดิม]
/**
 * เอฟเฟกต์ตอนโจมตีโดนศัตรู: วงคลื่นกระแทก + ตัวเลขดาเมจลอยขึ้น + จอสั่น
 *
 * ทุก View เอฟเฟกต์ "สร้างครั้งเดียวแล้วใช้ซ้ำ" (วงกระแทก/ตัวเลขดาเมจอย่างละ 6 ตัวสูงสุด ถ้าเต็มจะแทนตัวที่เก่าที่สุด)
 * และแต่ละตัวมีขนาดแค่พอครอบเอฟเฟกต์ของมันเอง ไม่ใช้ MATCH_PARENT ทั้งฉาก
 * ยกเว้นแฟลชแดงตอนผู้เล่นโดนตี ซึ่งตัวเอฟเฟกต์เองคือการวาบทั้งจอ จึงยังคลุมทั้งพื้นที่ แต่ใช้ View เดียวซ้ำ
 */
public final class HitEffects {

    private HitEffects() {}

    // [ค่าสี (0xAARRGGBB: AA = ความทึบ)] เก็บเป็นตัวเลขไว้ ไม่เรียก Color.parseColor ซ้ำ
    //   FLASH_RED = สีแฟลชแดงตอนผู้เล่นโดน | PATH_BAND = สีแถบเตือน | PATH_DASH = สีเส้นประและหัวลูกศร
    // ---------- สีที่ใช้บ่อย (ไม่ parse ใหม่ทุกครั้ง) ----------
    private static final int ORANGE = 0xFFFF9800;
    private static final int YELLOW = 0xFFFFEB3B;
    private static final int RED = 0xFFFF1744;
    private static final int RED_ACCENT = 0xFFFF5252;
    private static final int PINK_LIGHT = 0xFFFFCDD2;
    private static final int FLASH_RED = 0x26FF0000;
    private static final int PATH_BAND = 0x33FF1744;
    private static final int PATH_DASH = 0xCCFF5252;

    // [ขนาดกอง] MAX_BURSTS วงแหวนกระแทก , MAX_POPUPS ตัวเลขดาเมจ , MAX_PATHS แถบเส้นทาง (ถ้าอยากเห็นเอฟเฟกต์ซ้อนเยอะขึ้น เพิ่มตัวเลข)
    private static final int MAX_BURSTS = 6;
    private static final int MAX_POPUPS = 6;
    private static final int MAX_PATHS = 8;

    // [DAMAGE_TEXT] สร้างข้อความ "-0".."-39" ล่วงหน้า ตอนเล่นแค่หยิบมา ไม่สร้าง String ใหม่ทุกครั้ง (ดาเมจเกิน 39 ใช้ String.valueOf)
    // ข้อความดาเมจสร้างไว้ก่อน ("-1", "-2", ...) กันสร้าง String ใหม่ทุกครั้งที่โดน
    private static final String[] DAMAGE_TEXT = new String[40];

    static {
        for (int i = 0; i < DAMAGE_TEXT.length; i++) DAMAGE_TEXT[i] = "-" + i;
    }

    // [ตัวแปรกอง - static] poolArea = พื้นที่เล่นที่กองผูกอยู่ | bursts/burstNext = กองวงแหวนกับดัชนีวน | popups/popupBig/popupHide/popupNext = กองตัวเลข
    //   flashView = แฟลชแดง | paths = กองแถบเส้นทาง | shake* = ตัวสั่นจอ
    // ---------- กองเอฟเฟกต์ที่ใช้ซ้ำ (ผูกกับพื้นที่เกมของด่านปัจจุบัน) ----------
    private static FrameLayout poolArea;
    private static final BurstView[] bursts = new BurstView[MAX_BURSTS];
    private static int burstNext = 0;
    private static final TextView[] popups = new TextView[MAX_POPUPS];
    private static final boolean[] popupBig = new boolean[MAX_POPUPS];
    private static final Runnable[] popupHide = new Runnable[MAX_POPUPS];
    private static int popupNext = 0;
    private static View flashView;
    private static final ArrayList<PathView> paths = new ArrayList<>();

    // จอสั่น: animator ตัวเดียวใช้ซ้ำ
    private static ValueAnimator shakeAnim;
    private static View shakeTarget;
    private static float shakeAmp;

    // [bind] ผูกกองกับพื้นที่เล่นนี้ ถ้าเป็นพื้นที่ใหม่ (เริ่มด่านใหม่) ล้างกองเก่าก่อน เพราะ View เก่าผูกกับ Activity เก่า
    /** ผูกกองเอฟเฟกต์กับพื้นที่เกมนี้ (ถ้าเป็นพื้นที่ใหม่ เช่น เริ่มด่านใหม่ ทิ้งกองเก่า) */
    private static void bind(FrameLayout area) {
        if (poolArea == area) return;
        clearPools();
        poolArea = area;
    }

    // [clearPools] ลืม View ในกองทั้งหมด (ไม่ได้ถอดออกจากจอ) และยกเลิกจอสั่น
    private static void clearPools() {
        for (int i = 0; i < bursts.length; i++) bursts[i] = null;
        for (int i = 0; i < popups.length; i++) {
            popups[i] = null;
            popupHide[i] = null;
        }
        flashView = null;
        paths.clear();
        if (shakeAnim != null) shakeAnim.cancel();
        shakeAnim = null;
        shakeTarget = null;
        burstNext = 0;
        popupNext = 0;
    }

    // [release] ถอด View เอฟเฟกต์ทุกตัวออกจากจอ แล้วล้างกอง (เรียกตอน BattleActivity.onDestroy)
    /** เรียกตอนปิดด่าน: เอา View เอฟเฟกต์ออกจากฉากและล้างกอง */
    public static void release() {
        if (poolArea != null) {
            for (BurstView b : bursts) if (b != null) poolArea.removeView(b);
            for (TextView t : popups) if (t != null) poolArea.removeView(t);
            if (flashView != null) poolArea.removeView(flashView);
            for (PathView p : paths) poolArea.removeView(p);
        }
        clearPools();
        poolArea = null;
    }

    // [impact] ศัตรูโดนสกิล: คำนวณจุดกึ่งกลางศัตรู ดาเมจ >= 3 นับเป็น "ใหญ่" (วงแหวนและตัวเลขใหญ่กว่า จอสั่นแรงกว่า 10 เทียบ 5)
    public static void impact(BattleContext ctx, View target, int damage) {
        FrameLayout area = ctx.getGameArea();
        if (area == null || target == null) return;
        bind(area);

        float cx = target.getX() + target.getWidth() / 2f;
        float cy = target.getY() + target.getHeight() / 2f;

        boolean big = damage >= 3;
        burst(ctx.getContext(), area, cx, cy, big, ORANGE, YELLOW);
        damagePopup(ctx.getContext(), area, cx, cy, damage, big, YELLOW);
        shake(area, big ? 10f : 5f);
    }

    // [playerHit] ผู้เล่นโดนตี: วงแหวนแดง ตัวเลขแดง จอสั่น 9 และแฟลชแดงทั้งจอ
    /** ศัตรูพุ่งโดนผู้เล่น: เอฟเฟกต์สีแดง + จอวาบแดง + จอสั่นแรง */
    public static void playerHit(BattleContext ctx, View player, int damage) {
        FrameLayout area = ctx.getGameArea();
        if (area == null || player == null) return;
        bind(area);

        float cx = player.getX() + player.getWidth() / 2f;
        float cy = player.getY() + player.getHeight() / 2f;

        burst(ctx.getContext(), area, cx, cy, false, RED, PINK_LIGHT);
        damagePopup(ctx.getContext(), area, cx, cy, damage, false, RED_ACCENT);
        shake(area, 9f);
        flashRed(ctx.getContext(), area);
    }

    // [flashRed] View เดียวใช้ซ้ำ ปรับขนาดให้เท่าพื้นที่เล่นพอดี แสดงเต็มความทึบแล้วจางใน 200 ms แล้วซ่อน (HIDE_FLASH)
    /** แฟลชแดงเต็มพื้นที่ View เดียวใช้ซ้ำ ขนาดเท่าพื้นที่เกมพอดี */
    private static void flashRed(Context c, FrameLayout area) {
        if (flashView == null) {
            flashView = new View(c);
            flashView.setBackgroundColor(FLASH_RED);
            flashView.setLayoutParams(new FrameLayout.LayoutParams(1, 1));
            flashView.setVisibility(View.GONE);
            area.addView(flashView);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) flashView.getLayoutParams();
        if (lp.width != area.getWidth() || lp.height != area.getHeight()) {
            lp.width = area.getWidth();
            lp.height = area.getHeight();
            flashView.setLayoutParams(lp);
        }
        flashView.animate().cancel();
        flashView.setAlpha(1f);
        flashView.setVisibility(View.VISIBLE);
        flashView.animate().alpha(0f).setDuration(200).withEndAction(HIDE_FLASH);
    }

    // [HIDE_FLASH] คำสั่งซ่อนแฟลช สร้างไว้ครั้งเดียว (ไม่สร้างแลมบ์ดาใหม่ทุกครั้ง)
    private static final Runnable HIDE_FLASH = new Runnable() {
        @Override
        public void run() {
            if (flashView != null) flashView.setVisibility(View.GONE);
        }
    };

    // [trail] วาดเงาตามเส้นทางพุ่ง ทุก ~80 px (ไม่เกิน 6 จุด) จากกอง GhostPool.ghosts ผู้เรียกควรเรียกไม่ถี่กว่า GhostPool.MIN_SPAWN_INTERVAL_MS
    /**
     * เงาจางๆ ตามเส้นทางพุ่ง (ทุก ~80px ไม่เกิน 6 จุด) ให้เห็นแนวพุ่งแม้ความเร็วสูงมาก
     * ใช้เงาจากกองที่ใช้ซ้ำ ผู้เรียกควรเรียกไม่ถี่กว่า GhostPool.MIN_SPAWN_INTERVAL_MS
     */
    public static void trail(FrameLayout area, float x0, float y0, float x1, float y1,
                             int w, int h, int color) {
        float dist = (float) Math.hypot(x1 - x0, y1 - y0);
        int n = Math.min(6, Math.max(1, (int) (dist / 80f)));
        GhostPool pool = GhostPool.ghosts(area);
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            pool.ghost(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, w, h, color);
        }
    }

    // [ส่วนแถบเตือนเส้นทาง] ช่วงเตือนก่อนศัตรูโจมตี ให้ผู้เล่นหลบได้
    // ---------------------------------------------------------
    // เส้นทางพุ่ง/พื้นที่เตือนของศัตรู
    // ---------------------------------------------------------

    // [showAttackPath] ขอแถบเตือนจากกอง ตั้งตำแหน่งตามเส้น (x0,y0)->(x1,y1) ความกว้างแถบ = ระยะโดนจริง เริ่มโปร่ง 25% แล้วค่อยทึบเต็มภายใน durationMs
    //   คืน View ให้ผู้เรียกเก็บไว้ แล้วส่งกลับด้วย releaseAttackPath เมื่อพุ่งหรือถูกขัดจังหวะ
    /**
     * โชว์เส้นทางพุ่งของศัตรูก่อนโจมตี: แถบแดงโปร่งใสกว้างเท่าระยะโดนจริง + เส้นประกลาง + หัวลูกศร
     * ค่อยๆ ชัดขึ้นตลอดช่วงเตือน View มีขนาดแค่กรอบของเส้นทางและใช้ซ้ำจากกอง
     * คืน View ให้ผู้เรียกส่งกลับด้วย releaseAttackPath() เมื่อพุ่งหรือถูกขัดจังหวะ
     */
    public static View showAttackPath(FrameLayout area, float x0, float y0, float x1, float y1,
                                      float bandWidth, long durationMs) {
        bind(area);
        PathView view = acquirePath(area);
        view.configure(x0, y0, x1, y1, bandWidth);
        view.animate().cancel();
        view.setAlpha(0.25f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).setDuration(durationMs);
        return view;
    }

    // [releaseAttackPath] คืนแถบเข้ากอง (ซ่อน) คืน true ถ้าเป็นของกองนี้ , false = ผู้เรียกต้องลบเอง (เช่น กรวยหมึกไม่ได้มาจากกอง)
    /** คืนเส้นทางเตือนเข้ากอง (ซ่อน) คืน true ถ้า view นี้เป็นของกองนี้ false = ผู้เรียกต้องลบเอง */
    public static boolean releaseAttackPath(View view) {
        if (!(view instanceof PathView)) return false;
        view.animate().cancel();
        view.setVisibility(View.GONE);
        ((PathView) view).inUse = false;
        return true;
    }

    // [acquirePath] หาแถบที่ว่าง ถ้าไม่มีและยังไม่เต็มกอง สร้างใหม่ ถ้าเต็มแล้ว แทนตัวแรก (ปกติไม่เกิดเพราะศัตรูมี 5 ตัว)
    private static PathView acquirePath(FrameLayout area) {
        for (PathView p : paths) {
            if (!p.inUse) {
                p.inUse = true;
                return p;
            }
        }
        if (paths.size() < MAX_PATHS) {
            PathView p = new PathView(area.getContext());
            p.setLayoutParams(new FrameLayout.LayoutParams(1, 1));
            p.setVisibility(View.GONE);
            area.addView(p);
            paths.add(p);
            p.inUse = true;
            return p;
        }
        PathView oldest = paths.get(0);   // เต็มกอง: แทนตัวแรก (ไม่น่าเกิด เพราะมีศัตรูแค่ 5 ตัว)
        oldest.inUse = true;
        return oldest;
    }

    // [PathView] View วาดแถบเตือน: เส้นหนา (band) ความกว้าง = ระยะโดน + เส้นประกลาง + สามเหลี่ยมหัวลูกศรที่ปลาย
    //   configure() ตั้งขนาด View ให้พอดีกรอบเส้นบวกส่วนยื่น แล้วแปลงพิกัดเป็นสัมพัทธ์กับมุม View ของมันเอง
    //   inUse = ช่องนี้ถูกใช้อยู่ไหม
    private static class PathView extends View {
        private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path head = new Path();
        private float x0, y0, x1, y1;     // พิกัดเทียบมุมซ้ายบนของ View นี้
        boolean inUse = false;

        PathView(Context c) {
            super(c);
            band.setStyle(Paint.Style.STROKE);
            band.setStrokeCap(Paint.Cap.BUTT);
            band.setColor(PATH_BAND);
            dash.setStyle(Paint.Style.STROKE);
            dash.setStrokeWidth(6f);
            dash.setColor(PATH_DASH);
            dash.setPathEffect(new DashPathEffect(new float[]{26f, 18f}, 0f));
            arrow.setStyle(Paint.Style.FILL);
            arrow.setColor(PATH_DASH);
        }

        /** ตั้งตำแหน่ง/ขนาด View ให้พอดีกรอบของเส้น + ส่วนยื่นของแถบและหัวลูกศร */
        void configure(float ax, float ay, float bx, float by, float bandWidth) {
            float pad = bandWidth / 2f + 70f;
            float left = Math.min(ax, bx) - pad;
            float top = Math.min(ay, by) - pad;
            int w = (int) Math.ceil(Math.abs(bx - ax) + 2 * pad);
            int h = (int) Math.ceil(Math.abs(by - ay) + 2 * pad);

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
            if (lp.width != w || lp.height != h) {
                lp.width = w;
                lp.height = h;
                setLayoutParams(lp);
            }
            setX(left);
            setY(top);
            x0 = ax - left;
            y0 = ay - top;
            x1 = bx - left;
            y1 = by - top;
            band.setStrokeWidth(bandWidth);
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            canvas.drawLine(x0, y0, x1, y1, band);
            canvas.drawLine(x0, y0, x1, y1, dash);

            float len = (float) Math.hypot(x1 - x0, y1 - y0);
            if (len < 1f) return;
            float ux = (x1 - x0) / len, uy = (y1 - y0) / len;
            float size = 34f;
            head.reset();
            head.moveTo(x1 + ux * size, y1 + uy * size);
            head.lineTo(x1 - uy * size * 0.8f, y1 + ux * size * 0.8f);
            head.lineTo(x1 + uy * size * 0.8f, y1 - ux * size * 0.8f);
            head.close();
            canvas.drawPath(head, arrow);
        }
    }

    // [dp] แปลงหน่วย dp เป็นพิกเซลตามความละเอียดจอ
    private static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    // [burst] ขอวงแหวนจากกอง (สร้างตอนแรกที่ต้องใช้) วนใช้ทีละตัว แล้วสั่ง play ที่จุด (cx, cy)
    // ---------------------------------------------------------
    // วงแหวนกระแทก + เส้นประกายพุ่งออกรอบตัว (กองสูงสุด 6 ตัว)
    // ---------------------------------------------------------
    private static void burst(Context c, FrameLayout area, float cx, float cy, boolean big, int glow, int spikeColor) {
        BurstView view = bursts[burstNext];
        if (view == null) {
            view = new BurstView(c, dp(c, 70));
            area.addView(view);
            bursts[burstNext] = view;
        }
        burstNext = (burstNext + 1) % MAX_BURSTS;
        view.play(cx, cy, dp(c, big ? 70 : 55), glow, spikeColor);
    }

    // [damagePopup] ขอ TextView จากกอง ตั้งข้อความ "-N" สี ตำแหน่งเหนือเป้า ขยาย 0.7 เป็น 1.15 เท่า ลอยขึ้น 36 dp และจางใน 550 ms แล้วซ่อน
    //   ขนาดตัวอักษรเปลี่ยนเฉพาะเมื่อชนิด (ใหญ่/เล็ก) ต่างจากครั้งก่อนของช่องนั้น
    //   [แก้ยังไง] เปลี่ยนขนาดตัวเลข: setTextSize 26 (ใหญ่) / 20 (เล็ก)
    // ---------------------------------------------------------
    // ตัวเลขดาเมจลอยขึ้นแล้วจางหาย (กองสูงสุด 6 ตัว กล่องขนาดคงที่ ชิดล่าง)
    // ---------------------------------------------------------
    private static void damagePopup(Context c, FrameLayout area, float cx, float cy, int damage, boolean big, int color) {
        int slot = popupNext;
        popupNext = (popupNext + 1) % MAX_POPUPS;

        TextView tv = popups[slot];
        if (tv == null) {
            tv = new TextView(c);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setShadowLayer(8f, 0f, 0f, Color.BLACK);
            tv.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            tv.setLayoutParams(new FrameLayout.LayoutParams((int) dp(c, 120), (int) dp(c, 44)));
            tv.setVisibility(View.GONE);
            area.addView(tv);
            popups[slot] = tv;
            popupBig[slot] = !big;   // บังคับให้ตั้งขนาดตัวอักษรครั้งแรก
            popupHide[slot] = new HideRunnable(tv);
        }

        tv.animate().cancel();
        if (popupBig[slot] != big) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, big ? 26 : 20);
            popupBig[slot] = big;
        }
        tv.setText(damage >= 0 && damage < DAMAGE_TEXT.length ? DAMAGE_TEXT[damage] : String.valueOf(-damage));
        tv.setTextColor(color);
        tv.setX(cx - dp(c, 60));
        tv.setY(cy - dp(c, 44) - dp(c, 20));
        tv.setScaleX(0.7f);
        tv.setScaleY(0.7f);
        tv.setAlpha(1f);
        tv.setVisibility(View.VISIBLE);

        tv.animate()
                .scaleX(1.15f).scaleY(1.15f)
                .translationYBy(-dp(c, 36))
                .alpha(0f)
                .setDuration(550)
                .withEndAction(popupHide[slot]);
    }

    // [HideRunnable] คลาสเล็กสำหรับสั่งซ่อน View ตอนจบแอนิเมชัน สร้างครั้งเดียวต่อช่อง
    /** ซ่อน View เมื่อแอนิเมชันจบ (สร้างตอนสร้าง popup ไม่ใช่ทุกครั้งที่โดน) */
    private static final class HideRunnable implements Runnable {
        private final View v;

        HideRunnable(View v) {
            this.v = v;
        }

        @Override
        public void run() {
            v.setVisibility(View.GONE);
        }
    }

    // [shake] ทำให้พื้นที่เล่นสั่นสั้น ๆ 160 ms ใช้ animator ตัวเดียวซ้ำ : ตำแหน่ง = sin/cos ของเวลา x แอมพลิจูด x ตัวลด (ค่อยเบาลง)
    //   ถ้าสั่นอยู่แล้ว ยกเลิกแล้วเริ่มรอบใหม่ (ค่าสั่นเก่าถูกรีเซ็ต)
    //   [แก้ยังไง] ปิดจอสั่น: ลบการเรียก shake(...) ใน impact/playerHit
    // ---------------------------------------------------------
    // จอสั่นสั้นๆ แอมพลิจูดลดลงเรื่อยๆ (animator ตัวเดียวใช้ซ้ำ)
    // ---------------------------------------------------------
    private static void shake(View area, float amplitudePx) {
        if (shakeAnim == null) {
            shakeAnim = ValueAnimator.ofFloat(0f, 1f);
            shakeAnim.setDuration(160);
            shakeAnim.addUpdateListener(a -> {
                if (shakeTarget == null) return;
                float t = (float) a.getAnimatedValue();
                float decay = 1f - t;
                shakeTarget.setTranslationX((float) Math.sin(t * Math.PI * 8) * shakeAmp * decay);
                shakeTarget.setTranslationY((float) Math.cos(t * Math.PI * 6) * shakeAmp * 0.5f * decay);
            });
            shakeAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    resetShake();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    resetShake();
                }
            });
        }
        shakeAnim.cancel();   // ถ้ากำลังสั่นอยู่ เริ่มรอบใหม่ (ค่าสั่นรอบเก่าถูกรีเซ็ตก่อน)
        shakeTarget = area;
        shakeAmp = amplitudePx;
        shakeAnim.start();
    }

    // [resetShake] คืนตำแหน่งพื้นที่เล่นกลับเป็น 0 (ไม่ให้จอเอียงค้าง)
    private static void resetShake() {
        if (shakeTarget != null) {
            shakeTarget.setTranslationX(0f);
            shakeTarget.setTranslationY(0f);
        }
    }

    // [BurstView] วงกระแทก 1 ตัว: ขนาดคงที่พอดีรัศมีสูงสุด วาดที่กึ่งกลาง View ใช้ software layer เพื่อให้เงาเรืองแสงทำงาน
    //   แอนิเมชัน 300 ms : แกนสว่างวาบตอนเริ่ม + วงแหวนขยายแบบชะลอ (ease) บางลงและจาง + เส้นประกาย 8 เส้นพุ่งออก
    //   play() รีเซ็ตแล้วเล่นใหม่ที่ตำแหน่งใหม่ (ถ้าเล่นอยู่ ตัวเก่าถูกแทน)
    // ---------------------------------------------------------
    // วงกระแทก: ขนาดคงที่พอดีรัศมีสูงสุด วาดที่กึ่งกลาง View
    // ---------------------------------------------------------
    private static class BurstView extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint spike = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ValueAnimator anim;
        private final int half;
        private float maxRadius;
        private float progress;

        BurstView(Context c, float maxPossibleRadius) {
            super(c);
            // รัศมีสูงสุดที่วาดถึง = maxRadius * 1.05 + ขอบเส้น/เงาเรืองแสง
            half = (int) Math.ceil(maxPossibleRadius * 1.05f + 24f);
            setLayoutParams(new FrameLayout.LayoutParams(half * 2, half * 2));
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(Color.WHITE);
            spike.setStyle(Paint.Style.STROKE);
            spike.setStrokeCap(Paint.Cap.ROUND);
            core.setColor(Color.WHITE);
            setLayerType(LAYER_TYPE_SOFTWARE, null); // ให้ shadow ทำงาน (View เล็กลงแล้ว จึงเบาลงมาก)
            setVisibility(GONE);

            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(300);
            anim.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(GONE);
                }
            });
        }

        void play(float cx, float cy, float maxRadius, int glow, int spikeColor) {
            anim.cancel();   // ถ้ากำลังเล่นอยู่ เริ่มรอบใหม่ (onAnimationEnd ซ่อนไว้ก่อน แล้วเปิดใหม่ด้านล่าง)
            this.maxRadius = maxRadius;
            this.progress = 0f;
            ring.setShadowLayer(14f, 0f, 0f, glow);
            spike.setColor(spikeColor);
            setX(cx - half);
            setY(cy - half);
            setVisibility(VISIBLE);
            anim.start();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float cx = half, cy = half;
            float ease = 1f - (1f - progress) * (1f - progress);
            int alpha = (int) (255 * (1f - progress));

            // แกนแสงสว่างวาบตอนเริ่ม
            core.setAlpha((int) (255 * Math.max(0f, 1f - progress * 2.5f)));
            canvas.drawCircle(cx, cy, maxRadius * 0.3f * (1f - progress), core);

            ring.setAlpha(alpha);
            ring.setStrokeWidth(9f * (1f - progress) + 1f);
            canvas.drawCircle(cx, cy, maxRadius * ease, ring);

            spike.setAlpha(alpha);
            spike.setStrokeWidth(5f * (1f - progress) + 1f);
            for (int i = 0; i < 8; i++) {
                double ang = Math.PI * 2 * i / 8 + 0.3;
                float r0 = maxRadius * ease * 0.55f;
                float r1 = maxRadius * ease * 1.05f;
                canvas.drawLine(cx + (float) Math.cos(ang) * r0, cy + (float) Math.sin(ang) * r0,
                        cx + (float) Math.cos(ang) * r1, cy + (float) Math.sin(ang) * r1, spike);
            }
        }
    }
}
