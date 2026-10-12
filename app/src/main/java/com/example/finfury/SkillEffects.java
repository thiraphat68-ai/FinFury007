package com.example.finfury;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;


import java.util.ArrayList;
import java.util.List;

/**
 * ชิ้นส่วนสกิลที่ใช้ซ้ำได้ ฮีโร่แต่ละตัวเรียกใช้พร้อมกำหนดค่าของตัวเอง
 * หรือจะเขียนสกิลใหม่ทั้งหมดในไฟล์ฮีโร่โดยไม่ใช้คลาสนี้ก็ได้
 */
public final class SkillEffects {

    private SkillEffects() {}

    // ---------------------------------------------------------
    // พองตัวขึ้น ศัตรูที่ชนตัวที่พองจะโดนดาเมจ (ตัวละ 1 ครั้ง) ค้างไว้ครู่หนึ่งแล้วยุบกลับ
    // ---------------------------------------------------------
    public static void inflate(BattleContext ctx, float scale, long holdMs, int damage) {
        FrameLayout gameArea = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (gameArea == null || player == null) return;

        ctx.setSkillLock(true); //ล็อกสกิลอื่นไม่ให้ใช้ได้ระหว่างสกิลนี้ทำงาน

        final List<SeaEnemy> hitEnemies = new ArrayList<>();  // รายชื่อศัตรูเอาไว้เก็บตอนที่โดนโจมตี
                                            //1f ขนาดเริ่มต้นก่อนขยายตัว scale ขนาดที่ขยายตัวเสร็จแล้ว
        ValueAnimator puff = ValueAnimator.ofFloat(1f, scale);
        puff.setDuration(250); // ระยะเวลา เริ่ม-ใช้สกิลเสร็จ
        puff.setInterpolator(new DecelerateInterpolator()); // ขยายตัวไวแล้วช้าลง
        puff.addUpdateListener(animation -> {
            float s = (float) animation.getAnimatedValue(); //ค่าขนาดปัจจุบัน
            player.setScaleX(s);
            player.setScaleY(s);

            // ศูนย์กลางตัวละคร
            float cx = player.getX() + player.getWidth() / 2f; // ศูนย์กลางแนวนอน
            float cy = player.getY() + player.getHeight() / 2f; // ศูนย์กลางแนวตั้ง
            float halfW = player.getWidth() * s / 2f; // ครึ่งความกว้างที่พองแล้ว
            float halfH = player.getHeight() * s / 2f; //ครึ่งความสูงที่พองแล้ว

            for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
                SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);  //วนลูปดูศัตรูแต่ละตัว
                if (enemy.isAlive && enemy.containerView != null //เช็คศัตรู มีชีวิตอยู่ อยู่ในจอ
                        && !hitEnemies.contains(enemy)) {   //ยังไม่เคยโดนโจมตี
                    View ev = enemy.containerView; //หยิบ view ศัตรู
                    float ex = ev.getX() + ev.getWidth() / 2f; //ศูนย์กลางศัตรู แกน x
                    float ey = ev.getY() + ev.getHeight() / 2f; //ศูนย์กลางศัตรู แกน y
                    //เช็คว่ากรอบทับกันไหม หรือโจมตีโดนไหม
                    if (Math.abs(ex - cx) < halfW + ev.getWidth() / 2f
                            && Math.abs(ey - cy) < halfH + ev.getHeight() / 2f) {
                        hitEnemies.add(enemy); // จดว่าโดนตีแล้ว กันโดนดาเมจซ้ำในการโจมตีเดียว
                        enemy.takeDamage(damage); // ลดเลือดตามดาเมจที่สร้าง
                        ctx.onHitEnemySuccess(); // บอกระบบว่าตีโดน ให้เพิ่มสแตก
                    }
                }
            }
        });
        //ทำงานหลังพองเสร็จ
        puff.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> //สร้างตัวหน่วงเวลา ให้ยุบกลับก่อนค่อยทำงานต่อ
                        player.animate().scaleX(1f).scaleY(1f).setDuration(300)
                                .withEndAction(() -> ctx.setSkillLock(false)).start(),
                        holdMs); // อนิเมชันตอนกำลังยุบตัว ให้ขนากลับไปที่เดิม และปลดล็อกสกิลให้ใช้งานได้
            }
        });
        puff.start();
    }

    // ---------------------------------------------------------
    // พ่นพิษออกจากปากเป็นลำฟุ้งไปตามทิศของจอยสติ๊ก ศัตรูที่อยู่ในรูปกรวยโดนดาเมจ (ตัวละ 1 ครั้ง)
    // ---------------------------------------------------------
    public static void venomSpray(BattleContext ctx, float range, float coneDeg, long durationMs, int damage) {
        FrameLayout gameArea = ctx.getGameArea();
        View player = ctx.getPlayerContainer();
        if (gameArea == null || player == null) return;

        final float angle = ctx.getPlayerAngle(); // มุมองศาที่ตัวละครหัน
        final float rad = (float) Math.toRadians(angle);
        final float dirX = (float) Math.cos(rad); //ทิศทาง
        final float dirY = (float) Math.sin(rad);
        final float halfCone = coneDeg / 2f; //ครึ่งของมุมกรวย

        // หาตำแหน่งปาก
        final float mouthReach = player.getWidth() * player.getScaleX() / 2f; //ระยะเอื้อมถึงปาก = ความกว้างเดิม * ขนาดปัจจุบันหารสอง
        final float originX = player.getX() + player.getWidth() / 2f + dirX * mouthReach;
        final float originY = player.getY() + player.getHeight() / 2f + dirY * mouthReach;
        //ตำแหน่งปาก = ศูนย์กลาง + ทิศทาง * ระยะ

        // ม่วงล้วน หลายเฉดเพื่อให้ละอองดูมีมิติ
        final int[] colors = {0xAA9C27B0, 0xAA7B1FA2, 0xAAAB47BC, 0xAA6A1B9A};
        final java.util.Random rnd = new java.util.Random();
        final List<SeaEnemy> hitEnemies = new ArrayList<>();
        final GhostPool puffPool = GhostPool.puffs(gameArea);

        //อนิเมชันการพ่นละอองพิษ
        ValueAnimator spray = ValueAnimator.ofFloat(0f, 1f);
        spray.setDuration(durationMs);
        spray.setInterpolator(new LinearInterpolator()); //ความเร็วการพ่นคงที่
        spray.addUpdateListener(animation -> {
            // พ่นละอองหลายเม็ดต่อเฟรม แต่ละเม็ดกระจายในมุมกรวยแล้วลอยออกไปจางหาย
            for (int i = 0; i < 3; i++) {
                float a = (float) Math.toRadians(angle + (rnd.nextFloat() - 0.5f) * coneDeg); // ทิศทางของพิษที่พ่น
                float dist = range * (0.5f + rnd.nextFloat() * 0.5f); // สุ่มระยะที่พิษแต่ละเม็ดลอยไป
                int size = 30 + rnd.nextInt(40); //ขนาดของพิษ  ตอนกำลังพ่น

                // ละอองมาจากกองที่ใช้ซ้ำ: เริ่มที่ 0.4 เท่า ขยายเป็น 2 เท่า ลอยออกไปแล้วจาง (เหมือนเดิม)
                puffPool.spawn(originX, originY, size, size, colors[rnd.nextInt(colors.length)],
                        0.4f, 2f, (float) Math.cos(a) * dist,
                        (float) Math.sin(a) * dist, 450 + rnd.nextInt(200));
            }

            // ลำพิษยาวขึ้นตามเวลา เช็กศัตรูในกรวยเท่าความยาวปัจจุบัน
            float reach = range * Math.min(1f, animation.getAnimatedFraction() * 2.5f); //ความยาวที่พิษโจมตีถึง
            for (int enemyIdx = 0; enemyIdx < ctx.getEnemies().size(); enemyIdx++) {
                SeaEnemy enemy = ctx.getEnemies().get(enemyIdx);// วนลูปดูศัตรู
                if (!enemy.isAlive || enemy.containerView == null || hitEnemies.contains(enemy)) {
                    continue; // เชคศัตรุ ตายแล้ว ไม่อยู่ในจอ โดนพ่นใส่แล้ว เป็นจริงอันใดอันหนึ่ง ทำงานต่อไป
                }
                // ระยะจากปากถึงศัตรู
                View ev = enemy.containerView;
                float dx = ev.getX() + ev.getWidth() / 2f - originX;
                float dy = ev.getY() + ev.getHeight() / 2f - originY;
                float dist = (float) Math.hypot(dx, dy);
                if (dist > reach + ev.getWidth() / 2f) continue;
                // ศัตรูอยู่ไกลกว่าที่พิษไปถึง + รัศมีตัวศัตรู ให้ข้าม

                float diff = (float) Math.toDegrees(Math.atan2(dy, dx)) - angle; //มุมจากปากไปถึงศัตรู จากทิศที่หัน
                while (diff > 180f) diff -= 360f; //ปรับมุมให้อยู่ในช่วง -180 ถึง 180
                while (diff < -180f) diff += 360f;
                // ศัตรูตัวใหญ่ ขยายมุมที่ยอมรับตามระยะ (ใกล้ๆ ก็ไม่หลุดกรวย)
                float slack = (float)
                        Math.toDegrees(Math.atan2(ev.getWidth() / 2f,
                        Math.max(dist, 1f))); // เช็คศัตรูตามขนาดจริงของศัตรู โดยการประมาณ
                if (Math.abs(diff) <= halfCone + slack) { // มุมเบี่ยงจากแนวแกนกลางพิษ
                    hitEnemies.add(enemy); // เพิ่มศัตรูที่โดนโจมตีแล้ว ไปในลิส ไม่ให้โดนซ้ำจากการโจมตีเดียว
                    enemy.takeDamage(damage);
                    ctx.onHitEnemySuccess(); // แจ้งระแบบว่าตีโดน แล้วให้เพิ่มสแตก
                }
            }
        });
        spray.start();
    }

}