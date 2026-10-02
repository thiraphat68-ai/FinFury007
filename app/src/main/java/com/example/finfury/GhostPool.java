package com.example.finfury;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

/**
 * กองวงกลมจางหายที่ "สร้างครั้งเดียว ใช้ซ้ำ" แทนการ new View + addView/removeView ทุกเฟรม
 * (เงาตามเส้นทางพุ่ง ประกายหาง ละอองพิษ) แต่ละช่องมี View ซ่อน/แสดงสลับกัน
 * ถ้าช่องที่ถึงคิวยังเล่นอยู่ จะเริ่มช่องนั้นใหม่ (ตัวที่เก่าที่สุดถูกแทน) จำนวน View จึงคงที่เสมอ
 *
 * ทุกวงเป็นวงกลมขนาดฐาน BASE px แล้วย่อ/ขยายด้วย scale เพื่อให้ได้ขนาดตามต้องการ
 */
public final class GhostPool {

    /** เว้นช่วงอย่างน้อยเท่านี้ระหว่างการเสกเงา/ประกายของผู้เสกแต่ละราย */
    public static final long MIN_SPAWN_INTERVAL_MS = 40;

    private static final int BASE = 100;
    private static final Interpolator DECELERATE = new DecelerateInterpolator();

    private static GhostPool ghosts;
    private static GhostPool puffs;

    /** กองสำหรับเงา/ประกายจางหายอยู่กับที่ (12 ช่อง) */
    public static GhostPool ghosts(FrameLayout area) {
        if (ghosts == null || ghosts.area != area) ghosts = new GhostPool(area, 12, null);
        return ghosts;
    }

    /** กองสำหรับละอองที่ลอยออกไปแล้วจาง (เช่น พ่นพิษ ~180 เม็ดต่อวินาที อายุ ~0.6 วินาที จึงต้องมีหลายช่อง) */
    public static GhostPool puffs(FrameLayout area) {
        if (puffs == null || puffs.area != area) puffs = new GhostPool(area, 96, DECELERATE);
        return puffs;
    }

    /** เรียกตอนปิดด่าน: เอา View ในกองออกจากฉาก */
    public static void release() {
        if (ghosts != null) ghosts.removeAll();
        if (puffs != null) puffs.removeAll();
        ghosts = null;
        puffs = null;
    }

    /** true = ถึงเวลาเสกได้แล้ว (และจดเวลาไว้) lastMs คือตัวจับเวลาของผู้เสกรายนั้น สร้างครั้งเดียวต่อหนึ่งสกิล */
    public static boolean due(long[] lastMs) {
        long now = SystemClock.uptimeMillis();
        if (now - lastMs[0] < MIN_SPAWN_INTERVAL_MS) return false;
        lastMs[0] = now;
        return true;
    }

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

    private final FrameLayout area;
    private final Item[] items;
    private final Interpolator interpolator;
    private int next = 0;

    private GhostPool(FrameLayout area, int count, Interpolator interpolator) {
        this.area = area;
        this.interpolator = interpolator;
        items = new Item[count];
        for (int i = 0; i < count; i++) items[i] = new Item(area);
    }

    private void removeAll() {
        for (Item it : items) {
            it.view.animate().cancel();
            area.removeView(it.view);
        }
    }

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

    /** เงาตามตัวที่พุ่ง/กระสุน: ย่อเหลือ 0.3 เท่าแล้วจางใน 250 ms (ค่าเดิมของ spawnGhost) */
    public void ghost(float x, float y, float w, float h, int color) {
        spawn(x + w / 2f, y + h / 2f, w, h, color, 1f, 0.3f, 0f, 0f, 250);
    }

    /** ประกายกลมเล็กๆ ย่อเหลือ 0.2 เท่าแล้วจางใน durationMs */
    public void spark(float cx, float cy, float size, int color, long durationMs) {
        spawn(cx, cy, size, size, color, 1f, 0.2f, 0f, 0f, durationMs);
    }
}
