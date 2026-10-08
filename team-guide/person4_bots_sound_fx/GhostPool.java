// =====================================================================================
// [คนที่ 4 - บอท เสียง และเอฟเฟกต์]  ไฟล์: GhostPool.java  (133 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/GhostPool.java
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   "กองวงกลมจางหาย" ที่สร้างไว้ครั้งเดียวแล้วใช้ซ้ำ (Object Pool) ใช้ทำเอฟเฟกต์เล็ก ๆ ที่เสกถี่ ๆ:
//     เงาของปลาตอนพุ่ง (ghost) , ประกายหางกระสุน/ฟ้า (spark) , ละอองพิษ (spawn ผ่านกอง puffs)
//   ปัญหาที่แก้: ถ้า new View + addView/removeView ทุกเฟรม เครื่องจะกระตุก (GC) จึงสร้าง View คงที่ล่วงหน้า (ghosts 12 ช่อง, puffs 96 ช่อง)
//   วนใช้ทีละช่อง: ช่องที่ถึงคิวถ้ายังเล่นอยู่ก็เริ่มใหม่ (เก่าสุดถูกแทน) จำนวน View จึงคงที่เสมอ
//   ทุกวงเป็นวงกลมขนาดฐาน BASE px แล้วย่อ/ขยายด้วย scale
//
// [ใครเรียก] Swordfish, Shark, SkillEffects (เงา/ประกาย) , SkillEffects.venomSpray (ละออง) , BattleActivity.onDestroy เรียก release()
// [วิธีใช้]
//   long[] timer = {0};  if (GhostPool.due(timer)) GhostPool.ghosts(gameArea).ghost(x, y, w, h, สี);
//   GhostPool.puffs(gameArea).spawn(...) สำหรับละอองที่ลอยไปแล้วจาง
// [จะแก้ยังไง] เสกถี่/ห่างขึ้น: MIN_SPAWN_INTERVAL_MS | กองใหญ่ขึ้น (กันของหายตอนเสกเยอะ): เลขช่อง 12 / 96 ในเมธอด ghosts() / puffs()
// =====================================================================================
package com.example.finfury;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

// [คอมเมนต์คลาส - โดยเจ้าของไฟล์เดิม]
/**
 * กองวงกลมจางหายที่ "สร้างครั้งเดียว ใช้ซ้ำ" แทนการ new View + addView/removeView ทุกเฟรม
 * (เงาตามเส้นทางพุ่ง ประกายหาง ละอองพิษ) แต่ละช่องมี View ซ่อน/แสดงสลับกัน
 * ถ้าช่องที่ถึงคิวยังเล่นอยู่ จะเริ่มช่องนั้นใหม่ (ตัวที่เก่าที่สุดถูกแทน) จำนวน View จึงคงที่เสมอ
 *
 * ทุกวงเป็นวงกลมขนาดฐาน BASE px แล้วย่อ/ขยายด้วย scale เพื่อให้ได้ขนาดตามต้องการ
 */
// [คลาส final] ห้ามสืบทอด
public final class GhostPool {

    /** เว้นช่วงอย่างน้อยเท่านี้ระหว่างการเสกเงา/ประกายของผู้เสกแต่ละราย */
    // [MIN_SPAWN_INTERVAL_MS] เว้นช่วงอย่างน้อยเท่านี้ (40 ms) ระหว่างการเสกของผู้เสกแต่ละราย
    public static final long MIN_SPAWN_INTERVAL_MS = 40;

    // [BASE] ขนาดฐานของวงกลมทุกวง (100 px) | DECELERATE = ตัวปรับความเร็วแอนิเมชันแบบช้าลงตอนท้าย (ใช้กับละออง)
    private static final int BASE = 100;
    private static final Interpolator DECELERATE = new DecelerateInterpolator();

    // [กองที่ใช้งานอยู่ 2 กอง - static] ghosts (เงา/ประกาย) กับ puffs (ละออง)
    private static GhostPool ghosts;
    private static GhostPool puffs;

    // [ghosts(area)] ขอกองเงา 12 ช่อง ถ้ายังไม่มีหรือเปลี่ยนด่าน/พื้นที่เล่น (area ไม่ตรง) สร้างกองใหม่
    /** กองสำหรับเงา/ประกายจางหายอยู่กับที่ (12 ช่อง) */
    public static GhostPool ghosts(FrameLayout area) {
        if (ghosts == null || ghosts.area != area) ghosts = new GhostPool(area, 12, null);
        return ghosts;
    }

    // [puffs(area)] ขอกองละออง 96 ช่อง (พ่นพิษ ~180 เม็ดต่อวินาที อายุ ~0.6 วินาที จึงต้องมีหลายช่อง) ใช้ตัวปรับ DECELERATE
    /** กองสำหรับละอองที่ลอยออกไปแล้วจาง (เช่น พ่นพิษ ~180 เม็ดต่อวินาที อายุ ~0.6 วินาที จึงต้องมีหลายช่อง) */
    public static GhostPool puffs(FrameLayout area) {
        if (puffs == null || puffs.area != area) puffs = new GhostPool(area, 96, DECELERATE);
        return puffs;
    }

    // [release] ถอด View ในกองออกจากฉากทั้งหมดแล้วลืมกอง (เรียกตอน BattleActivity.onDestroy กัน View ค้างผูกกับ Activity เก่า)
    /** เรียกตอนปิดด่าน: เอา View ในกองออกจากฉาก */
    public static void release() {
        if (ghosts != null) ghosts.removeAll();
        if (puffs != null) puffs.removeAll();
        ghosts = null;
        puffs = null;
    }

    // [due(lastMs)] ตัวคุมความถี่: คืน true ถ้าห่างจากครั้งก่อนเกิน 40 ms แล้วจดเวลาใหม่ lastMs เป็นอาร์เรย์ 1 ช่อง (เพื่อแก้ค่าได้ในแลมบ์ดา) สร้างหนึ่งตัวต่อหนึ่งสกิล
    /** true = ถึงเวลาเสกได้แล้ว (และจดเวลาไว้) lastMs คือตัวจับเวลาของผู้เสกรายนั้น สร้างครั้งเดียวต่อหนึ่งสกิล */
    public static boolean due(long[] lastMs) {
        long now = SystemClock.uptimeMillis();
        if (now - lastMs[0] < MIN_SPAWN_INTERVAL_MS) return false;
        lastMs[0] = now;
        return true;
    }

    // [Item = ช่อง 1 ช่องในกอง] ประกอบด้วย View วงกลมใส (GradientDrawable รูปวงรี) ซ่อนไว้ (GONE) ถูก addView ลงจอไว้ล่วงหน้า และ hide = คำสั่งซ่อนเมื่อจบแอนิเมชัน
    private static final class Item {
        final View view;
        final GradientDrawable bg;
        final Runnable hide;

        Item(FrameLayout area) {
            view = new View(area.getContext());
            bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.TRANSPARENT);
            view.setBackground(bg);
            view.setLayoutParams(new FrameLayout.LayoutParams(BASE, BASE));
            view.setVisibility(View.GONE);
            area.addView(view);
            hide = () -> view.setVisibility(View.GONE);
        }
    }

    // [ตัวแปรกอง] area = พื้นที่เล่นที่ติดอยู่ | items = ช่องทั้งหมด | interpolator | next = ช่องถัดไปที่จะใช้ (วนรอบ)
    private final FrameLayout area;
    private final Item[] items;
    private final Interpolator interpolator;
    private int next = 0;

    // [constructor private] สร้างช่องทั้งหมดล่วงหน้า (ห้ามสร้างเองจากภายนอก ใช้ ghosts()/puffs())
    private GhostPool(FrameLayout area, int count, Interpolator interpolator) {
        this.area = area;
        this.interpolator = interpolator;
        items = new Item[count];
        for (int i = 0; i < count; i++) items[i] = new Item(area);
    }

    // [removeAll] ยกเลิกแอนิเมชันและลบทุกช่องออกจากจอ
    private void removeAll() {
        for (Item it : items) {
            it.view.animate().cancel();
            area.removeView(it.view);
        }
    }

    // [spawn] เสกวงกลม 1 วง: ใช้ช่องถัดไป (ถ้ายังเล่นอยู่ ยกเลิกและเริ่มใหม่) ตั้งสี ตำแหน่ง (กึ่งกลาง cx,cy) ขนาดเริ่ม แล้วเล่นแอนิเมชัน
    //   จางเป็นโปร่งใส + ขยาย/ย่อถึง endScale + เลื่อนตาม (dx, dy) ถ้ามี ภายใน durationMs แล้วซ่อนตัวเอง (hide)
    /**
     * โชว์วงกลมสี color ที่ศูนย์กลาง (cx, cy) ขนาดเริ่ม w x h (px) แล้วค่อยๆ จาง
     * พร้อมย่อ/ขยายเป็น endScale เท่าของขนาดเริ่ม และเลื่อนไป (dx, dy) ถ้ามี
     */
    public void spawn(float cx, float cy, float w, float h, int color,
                      float startScale, float endScale, float dx, float dy, long durationMs) {
        Item it = items[next];
        next = (next + 1) % items.length;

        View v = it.view;
        v.animate().cancel();
        it.bg.setColor(color);
        v.setX(cx - BASE / 2f);
        v.setY(cy - BASE / 2f);
        v.setScaleX(w / BASE * startScale);
        v.setScaleY(h / BASE * startScale);
        v.setAlpha(1f);
        v.setVisibility(View.VISIBLE);

        ViewPropertyAnimator a = v.animate()
                .alpha(0f)
                .scaleX(w / BASE * endScale)
                .scaleY(h / BASE * endScale)
                .setDuration(durationMs)
                .withEndAction(it.hide);
        if (interpolator != null) a.setInterpolator(interpolator);
        if (dx != 0f || dy != 0f) a.translationXBy(dx).translationYBy(dy);
    }

    // [ghost] เงาปลา/กระสุน : ย่อเหลือ 0.3 เท่าแล้วจางใน 250 ms ที่ตำแหน่งมุมซ้ายบน (x, y) ขนาด w x h
    /** เงาตามตัวที่พุ่ง/กระสุน: ย่อเหลือ 0.3 เท่าแล้วจางใน 250 ms (ค่าเดิมของ spawnGhost) */
    public void ghost(float x, float y, float w, float h, int color) {
        spawn(x + w / 2f, y + h / 2f, w, h, color, 1f, 0.3f, 0f, 0f, 250);
    }

    // [spark] ประกายวงกลม ย่อเหลือ 0.2 เท่าแล้วจางใน durationMs ที่จุดกึ่งกลาง (cx, cy)
    /** ประกายกลมเล็กๆ ย่อเหลือ 0.2 เท่าแล้วจางใน durationMs */
    public void spark(float cx, float cy, float size, int color, long durationMs) {
        spawn(cx, cy, size, size, color, 1f, 0.2f, 0f, 0f, durationMs);
    }
}
