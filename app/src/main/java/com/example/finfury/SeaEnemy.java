package com.example.finfury;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class SeaEnemy {
    private static int activeAttackerId = 0;
    private static long lastAttackEndTime = 0;

    /** เรียกตอนเริ่มด่านใหม่ ตัวแปร static ค้างข้ามเกมได้ ถ้าเกมก่อนจบตอนศัตรูถือสิทธิ์โจมตีอยู่ ศัตรูชุดใหม่จะไม่ได้โจมตีเลย */
    public static void resetAttackQueue() {
        activeAttackerId = 0;
        lastAttackEndTime = 0;
    }

    /** DASH = พุ่งทะลุเข้าหา, LINE_SHOT = ยิงลำพลังเส้นตรง (แมงกะพรุน), INK_CONE = พ่นหมึกเป็นกรวย (หมึกยักษ์) */
    private enum AttackType { DASH, LINE_SHOT, INK_CONE }

    private enum State {
        ORBIT_AND_WAIT,
        WINDUP,
        ATTACKING
    }

    private final BattleContext ctx;
    private final int enemyId;

    public final String name;
    public final String emoji;
    public int hp = 10;
    public int maxHp = 10;
    public boolean isAlive = true;
    public View containerView;

    /** true = ถูกกลืนอยู่ในปากผู้เล่น: AI หยุด ไม่โดนผลักถอย ตำแหน่งถูกควบคุมโดยสกิล */
    private boolean swallowed = false;

    public boolean isSwallowed() { return swallowed; }

    // เงาตามเส้นทางพุ่ง
    private static final int TRAIL_COLOR = 0x44FF1744;
    private final long[] lastTrailMs = {0};
    private boolean trailStarted = false;
    private float trailFromX, trailFromY;

    private float slowFactor = 1f;
    private long slowUntilMs = 0;

    /** ทำให้เคลื่อนที่ช้าลง (factor 0.5 = เหลือครึ่งหนึ่ง) นาน durationMs ถ้าเรียกซ้ำจะต่อเวลา */
    public void applySlow(float factor, long durationMs) {
        slowFactor = factor;
        slowUntilMs = System.currentTimeMillis() + durationMs;
        setSlowed(true);
    }

    public void setSwallowed(boolean value) {
        swallowed = value;
        if (value) {
            cancelAttack();
        } else {
            lastStateUpdateTime = System.currentTimeMillis();
        }
    }

    private long stunUntilMs = 0;

    public boolean isStunned() { return System.currentTimeMillis() < stunUntilMs; }

    /** สตันนานตามเวลา: ขยับ/โจมตีไม่ได้ (ต่างจาก swallowed ตรงที่โดนดาเมจ/ผลักถอยได้ตามปกติ) */
    public void stun(long durationMs) {
        stunUntilMs = Math.max(stunUntilMs, System.currentTimeMillis() + durationMs);
        setStunned(true);
        cancelAttack();
    }

    /** ยกเลิกการโจมตีที่ค้างอยู่ ไม่งั้นตัวอื่นรอคิวโจมตีไม่ได้ */
    private void cancelAttack() {
        clearAttackPath();
        if (activeAttackerId == enemyId) {
            activeAttackerId = 0;
            lastAttackEndTime = System.currentTimeMillis();
        }
        timeInOrbitMs = 0;
        currentState = State.ORBIT_AND_WAIT;
    }

    private TextView imgAvatar;
    private ProgressBar barHp;
    private TextView txtHp;

    private State currentState = State.ORBIT_AND_WAIT;
    private long lastStateUpdateTime = System.currentTimeMillis();
    private long timeInOrbitMs = (long) (Math.random() * 800);

    private float dashTargetX = 0f;
    private float dashTargetY = 0f;
    private boolean hasHitPlayerThisDash = false;
    private long windupStartMs = 0;
    private View attackPathView;

    // สเตตัสและลักษณะเฉพาะของศัตรูแต่ละประเภท
    private float speedMultiplier = 1.0f;
    private float holdMinDistance = 200f;
    private float holdMaxDistance = 320f;
    private int attackDamage = 5;
    private AttackType attackType = AttackType.DASH;
    private float aimDirX = 1f;
    private float aimDirY = 0f;

    private static final float BASE_SPEED = BattleActivity.MAX_SPEED * 0.75f;
    private static final float ORBIT_SPEED_FACTOR = 0.20f;                      // ความเร็วตอนวนรอบ = 20% ของความเร็วเดิน
    private static final float DASH_SPEED = BattleActivity.MAX_SPEED * 10f;     // ความเร็วพุ่ง = 1000% ของผู้เล่น
    private static final float ORBIT_LOOKAHEAD_RAD = 0.4f;
    private static final float SEPARATION_SPEED = 400f;
    private static final float ATTACK_TRIGGER_RANGE = 45f;
    private static final float ENEMY_SPACING = 140f;
    private static final float PASS_THROUGH_DISTANCE = 160f; // ระยะพุ่งทะลุผ่านตัวผู้เล่นออกไปด้านหลัง
    private static final long ATTACK_INTERVAL_MS = 3000;     // ศัตรูแต่ละตัวโจมตีทุก 3 วินาที (นับรวมช่วงเตือน)
    private static final float LINE_SHOT_SPEED_FACTOR = 3f;  // ลำพลังแมงกะพรุนเร็ว 300% ของความเร็วเดินของมัน
    private static final long WINDUP_MS = 800;               // ช่วงหยุดเล็งและโชว์เส้นทางก่อนพุ่ง

    private float animTime = (float) (Math.random() * 10);
    private final float orbitDir = Math.random() < 0.5 ? 1f : -1f;

    public SeaEnemy(BattleContext ctx, String name, String emoji, float posX, float posY) {
        this.ctx = ctx;
        this.name = name;
        this.emoji = emoji;
        this.enemyId = System.identityHashCode(this);

        configureEnemyStats();
        createView(posX, posY);
    }

    private void configureEnemyStats() {
        if (emoji.contains("🦀") || name.contains("ปู")) {
            // ปูซ่า: เลือดเยอะ ช้า
            maxHp = 14;
            hp = 14;
            speedMultiplier = 0.85f;
            holdMinDistance = 160f;
            holdMaxDistance = 240f;
            attackDamage = 6;
        } else if (emoji.contains("🦑") || name.contains("หมึก")) {
            // หมึกยักษ์: วิ่งเร็ว วนใกล้ ดุดัน
            maxHp = 11;
            hp = 11;
            speedMultiplier = 1.25f;
            holdMinDistance = 280f;
            holdMaxDistance = 380f;  // ต้องน้อยกว่าระยะกรวยหมึก (RangedAttacks.INK_RANGE) เพื่อให้กรวยถึงตัวผู้เล่น
            attackDamage = 5;
            attackType = AttackType.INK_CONE;
        } else if (emoji.contains("🐢") || name.contains("เต่า")) {
            // เต่าทะเล: เลือดเยอะที่สุด วนไกล เดินช้า
            maxHp = 16;
            hp = 16;
            speedMultiplier = 0.85f;
            holdMinDistance = 240f;
            holdMaxDistance = 340f;
            attackDamage = 5;
        } else if (emoji.contains("🪼") || name.contains("กะพรุน")) {
            // แมงกะพรุน: ความเร็วปกติ ลอยวนระยะกว้าง
            maxHp = 10;
            hp = 10;
            speedMultiplier = 1.0f;
            holdMinDistance = 320f;
            holdMaxDistance = 420f;
            attackDamage = 4;
            attackType = AttackType.LINE_SHOT;
        } else {
            // ดาวทะเล หรืออื่นๆ: สเตตัสสมดุล
            maxHp = 10;
            hp = 10;
            speedMultiplier = 1.0f;
            holdMinDistance = 200f;
            holdMaxDistance = 320f;
            attackDamage = 5;
        }
    }

    private void createView(float posX, float posY) {
        LinearLayout layout = new LinearLayout(ctx.getContext());
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);

        TextView txtName = new TextView(ctx.getContext());
        txtName.setText(name);
        txtName.setTextColor(Color.WHITE);
        txtName.setTextSize(11f);
        txtName.setGravity(Gravity.CENTER);

        barHp = new ProgressBar(ctx.getContext(), null, android.R.attr.progressBarStyleHorizontal);
        barHp.setMax(maxHp);
        barHp.setProgress(hp);
        barHp.setProgressTintList(ColorStateList.valueOf(Color.RED));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(90, 14);
        barParams.bottomMargin = 2;
        barHp.setLayoutParams(barParams);

        txtHp = new TextView(ctx.getContext());
        txtHp.setText(String.format(Locale.US, "%d/%d", hp, maxHp));
        txtHp.setTextColor(Color.YELLOW);
        txtHp.setTextSize(9f);
        txtHp.setGravity(Gravity.CENTER);

        imgAvatar = new TextView(ctx.getContext());
        imgAvatar.setText(emoji);
        imgAvatar.setTextSize(36f);
        imgAvatar.setGravity(Gravity.CENTER);

        // แถวไอคอนสถานะเหนือศัตรู: แถวสูง 0 และไอคอนวาดล้นขึ้นด้านบน
        // เพื่อไม่ให้กรอบตัวศัตรู (ที่ใช้เช็กชน) ใหญ่ขึ้นจากเดิม
        layout.setClipChildren(false);
        LinearLayout statusRow = new LinearLayout(ctx.getContext());
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER);
        statusRow.setClipChildren(false);
        statusRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 0));
        iconCharged = makeStatusIcon("⚡");
        iconSlowed = makeStatusIcon("🐌");
        iconStunned = makeStatusIcon("💫");
        statusRow.addView(iconCharged);
        statusRow.addView(iconSlowed);
        statusRow.addView(iconStunned);

        layout.addView(statusRow);
        layout.addView(txtName);
        layout.addView(barHp);
        layout.addView(txtHp);
        layout.addView(imgAvatar);

        layout.setX(posX);
        layout.setY(posY);

        containerView = layout;
        if (ctx.getGameArea() != null) {
            ctx.getGameArea().addView(containerView);
        }
    }

    // ---------------------------------------------------------
    // ไอคอนสถานะเหนือศัตรู: ติดประจุ / ช้าลง / สตัน
    // ---------------------------------------------------------
    private static final int STATUS_ICON_PX = 36;
    private TextView iconCharged;
    private TextView iconSlowed;
    private TextView iconStunned;

    private TextView makeStatusIcon(String emojiText) {
        TextView tv = new TextView(ctx.getContext());
        tv.setText(emojiText);
        tv.setTextSize(13f);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(STATUS_ICON_PX, STATUS_ICON_PX));
        tv.setTranslationY(-STATUS_ICON_PX);
        tv.setVisibility(View.INVISIBLE);
        return tv;
    }

    private static void setIcon(TextView icon, boolean visible) {
        if (icon != null) icon.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    /** ติดประจุ (ผู้เรียก = สกิลที่ทำให้เกิดสถานะนี้ เช่น Swordfish) */
    public void setCharged(boolean value) { setIcon(iconCharged, value); }

    /** ช้าลง (ปกติไม่ต้องเรียกเอง applySlow จะโชว์ให้ และซ่อนเมื่อหมดเวลา) */
    public void setSlowed(boolean value) { setIcon(iconSlowed, value); }

    /** สตัน (ปกติไม่ต้องเรียกเอง stun() จะโชว์ให้ และซ่อนเมื่อหมดเวลา) */
    public void setStunned(boolean value) { setIcon(iconStunned, value); }

    private void refreshStatusIcons(long now) {
        setSlowed(now < slowUntilMs);
        setStunned(now < stunUntilMs);
    }

    public void updateAI(float targetX, float targetY) {
        if (!isAlive || swallowed || containerView == null || !ctx.isGameRunning() || ctx.isGamePaused()) return;

        long now = System.currentTimeMillis();
        refreshStatusIcons(now);
        if (now < stunUntilMs) {
            // สตัน: ไม่ขยับ ไม่โจมตี (แต่ยังรับดาเมจได้) เก็บเวลาไว้ไม่ให้เฟรมแรกหลังหายสตันกระโดด
            lastStateUpdateTime = now;
            return;
        }
        long dtMs = Math.min(now - lastStateUpdateTime, 100);
        lastStateUpdateTime = now;
        // ติดสโลว์: ทุกการเคลื่อนที่ (เดิน/วนรอบ/พุ่ง) คิดจากเวลาที่ช้าลง
        float dt = dtMs / 1000f * (now < slowUntilMs ? slowFactor : 1f);

        float moveSpeed = BASE_SPEED * speedMultiplier;

        float currentX = containerView.getX();
        float currentY = containerView.getY();

        float dx = targetX - currentX;
        float dy = targetY - currentY;
        float distance = (float) Math.hypot(dx, dy);

        float nextX = currentX;
        float nextY = currentY;

        switch (currentState) {
            case ORBIT_AND_WAIT: {
                timeInOrbitMs += dtMs;

                // 1. วนรอบผู้เล่นในระยะปลอดภัยด้วยความเร็วช้าลง (20%)
                // อยู่ไกลเกินวงโคจรให้เดินเข้าหาด้วยความเร็วปกติ ไม่งั้นต้องใช้เวลาหลายวินาทีกว่าจะเข้าระยะโจมตี
                float orbitSpeed = (distance > holdMaxDistance + 60f) ? moveSpeed : moveSpeed * ORBIT_SPEED_FACTOR;
                double angle = Math.atan2(-dy, -dx) + orbitDir * ORBIT_LOOKAHEAD_RAD;
                float holdDist = Math.max(holdMinDistance, Math.min(holdMaxDistance, distance));
                float holdX = targetX + (float) Math.cos(angle) * holdDist;
                float holdY = targetY + (float) Math.sin(angle) * holdDist;

                float hdx = holdX - currentX;
                float hdy = holdY - currentY;
                float hdist = (float) Math.hypot(hdx, hdy);
                if (hdist > 0.001f) {
                    float step = Math.min(orbitSpeed * dt, hdist);
                    nextX += (hdx / hdist) * step;
                    nextY += (hdy / hdist) * step;
                }

                // 2. ตรวจสอบคิวขอสิทธิ์พุ่งทะลุผ่านตัวผู้เล่น (Pass-Through Dash)
                boolean isAttackerSlotFree = (activeAttackerId == 0 || activeAttackerId == enemyId);
                // ให้สิทธิ์กับตัวที่รอนานที่สุด ไม่งั้นตัวท้ายลิสต์ (เช่นหมึก) แพ้คิวซ้ำๆ เพราะสิทธิ์ถูกใช้ตลอด
                boolean outranked = false;
                for (int oIdx = 0; oIdx < ctx.getEnemies().size(); oIdx++) {
                    SeaEnemy o = ctx.getEnemies().get(oIdx);
                    if (o != this && o.isReadyToAttack(targetX, targetY, now) && o.timeInOrbitMs > timeInOrbitMs) {
                        outranked = true;
                        break;
                    }
                }
                if (isReadyToAttack(targetX, targetY, now) && isAttackerSlotFree && !outranked) {
                    activeAttackerId = enemyId;
                    currentState = State.WINDUP;
                    windupStartMs = now;
                    startWindup(targetX, targetY);
                }
                break;
            }

            case WINDUP: {
                // หยุดนิ่งเล็งเป้า โชว์เส้นทางพุ่งที่ล็อกไว้แล้ว ให้ผู้เล่นหลบได้
                if (now - windupStartMs >= WINDUP_MS) {
                    clearAttackPath();
                    if (attackType == AttackType.DASH) {
                        hasHitPlayerThisDash = false;
                        currentState = State.ATTACKING;
                        trailStarted = false;
                    } else {
                        // โจมตีระยะไกล: ปล่อยแล้วจบเลย ไม่ต้องพุ่ง กลับไปวนรอบรอรอบถัดไป
                        fireRangedAttack();
                        if (activeAttackerId == enemyId) {
                            activeAttackerId = 0;
                            lastAttackEndTime = now;
                        }
                        timeInOrbitMs = 0;
                        currentState = State.ORBIT_AND_WAIT;
                    }
                }
                break;
            }

            case ATTACKING: {
                // พุ่งทะลุผ่านไปยังจุดหมายด้านหลังผู้เล่นด้วยความเร็ว 1000% ของผู้เล่น
                float ddx = dashTargetX - currentX;
                float ddy = dashTargetY - currentY;
                float ddist = (float) Math.hypot(ddx, ddy);

                if (ddist > 0.001f) {
                    float step = Math.min(DASH_SPEED * dt, ddist);
                    nextX += (ddx / ddist) * step;
                    nextY += (ddy / ddist) * step;
                }

                // ก้าวละหลายร้อย px จึงเช็กทั้งเส้นทางของเฟรมนี้ ไม่งั้นพุ่งข้ามผู้เล่นโดยไม่โดน
                // เงาตามเส้นทางพุ่ง: เสกไม่ถี่กว่า 40 ms แต่ลากเงาครอบคลุมทางที่เพิ่งผ่านมาตั้งแต่ครั้งก่อน
                if (!trailStarted) {
                    trailStarted = true;
                    trailFromX = currentX;
                    trailFromY = currentY;
                }
                FrameLayout fxArea = ctx.getGameArea();
                if (fxArea != null && GhostPool.due(lastTrailMs)) {
                    HitEffects.trail(fxArea, trailFromX, trailFromY, nextX, nextY,
                            containerView.getWidth(), containerView.getHeight(), TRAIL_COLOR);
                    trailFromX = nextX;
                    trailFromY = nextY;
                }

                // เช็กด้วยจุดกึ่งกลางของทั้งคู่ ให้ตรงกับแถบเส้นทางที่โชว์ตอนเตือน
                View pv = ctx.getPlayerContainer();
                float pcx = targetX + (pv != null ? pv.getWidth() / 2f : 0f);
                float pcy = targetY + (pv != null ? pv.getHeight() / 2f : 0f);
                float ehw = containerView.getWidth() / 2f, ehh = containerView.getHeight() / 2f;
                if (!hasHitPlayerThisDash
                        && distanceToSegment(pcx, pcy, currentX + ehw, currentY + ehh, nextX + ehw, nextY + ehh) <= ATTACK_TRIGGER_RANGE) {
                    ctx.damagePlayer(attackDamage);
                    HitEffects.playerHit(ctx, ctx.getPlayerContainer(), attackDamage);
                    hasHitPlayerThisDash = true;
                }

                // เมื่อพุ่งถึงจุดหมายด้านหลังผู้เล่นเรียบร้อย ให้กลับเข้าสู่สถานะวนรอบรอ
                if (ddist <= 15f) {
                    if (activeAttackerId == enemyId) {
                        activeAttackerId = 0;
                        lastAttackEndTime = now;
                    }
                    timeInOrbitMs = 0;
                    currentState = State.ORBIT_AND_WAIT;
                }
                break;
            }
        }

        // 3. เว้นระยะห่างระหว่างศัตรูด้วยกันเอง (ตัวที่กำลังพุ่งทะลุจะไม่โดนผลัก)
        for (int otherIdx = 0; otherIdx < ctx.getEnemies().size(); otherIdx++) {
            SeaEnemy other = ctx.getEnemies().get(otherIdx);
            if (currentState == State.ORBIT_AND_WAIT && other != this && other.isAlive && other.containerView != null) {
                float ox = other.containerView.getX();
                float oy = other.containerView.getY();
                float distToOther = (float) Math.hypot(nextX - ox, nextY - oy);

                if (distToOther > 0.001f && distToOther < ENEMY_SPACING) {
                    float pushX = (nextX - ox) / distToOther;
                    float pushY = (nextY - oy) / distToOther;
                    float pushForce = (ENEMY_SPACING - distToOther) / ENEMY_SPACING * SEPARATION_SPEED * dt;
                    nextX += pushX * pushForce;
                    nextY += pushY * pushForce;
                }
            }
        }

        View area = (View) containerView.getParent();
        if (area != null && area.getWidth() > 0) {
            nextX = Math.max(0f, Math.min(area.getWidth() - containerView.getWidth(), nextX));
            nextY = Math.max(0f, Math.min(area.getHeight() - containerView.getHeight(), nextY));
        }
        containerView.setX(nextX);
        containerView.setY(nextY);

        // 4. แอนิเมชันรูปศัตรู (พลิกตามทิศทาง + ส่าย)
        animTime += 0.08f;
        float facing = (dx < 0) ? 1f : -1f;
        float wave = (float) Math.sin(animTime * 2.5f);
        float tilt = (float) Math.sin(animTime * 1.5f) * 7f;

        imgAvatar.setRotation(tilt);
        imgAvatar.setScaleX(facing * (1.0f + wave * 0.06f));
        imgAvatar.setScaleY(1.0f - wave * 0.06f);
        imgAvatar.setAlpha(1.0f);
    }

    /** พร้อมขอสิทธิ์โจมตีหรือยัง: ครบเวลารอ + พ้นช่วงพักหลังโจมตีล่าสุด + (หมึก) ผู้เล่นอยู่ในระยะกรวย */
    private boolean isReadyToAttack(float targetX, float targetY, long now) {
        if (!isAlive || containerView == null || currentState != State.ORBIT_AND_WAIT) return false;
        if (timeInOrbitMs < ATTACK_INTERVAL_MS - WINDUP_MS) return false;
        if (now - lastAttackEndTime < 300) return false;
        if (attackType == AttackType.INK_CONE) {
            // หมึกพ่นได้เฉพาะตอนผู้เล่นอยู่ในระยะกรวย ไม่งั้นพ่นแล้วไม่ถึงตัว
            float d = (float) Math.hypot(targetX - containerView.getX(), targetY - containerView.getY());
            return d <= RangedAttacks.INK_RANGE * 0.9f;
        }
        return true;
    }

    private void fireRangedAttack() {
        float ecx = containerView.getX() + containerView.getWidth() / 2f;
        float ecy = containerView.getY() + containerView.getHeight() / 2f;
        if (attackType == AttackType.LINE_SHOT) {
            float speed = BASE_SPEED * speedMultiplier * LINE_SHOT_SPEED_FACTOR;
            RangedAttacks.fireLine(ctx, ecx, ecy, aimDirX, aimDirY, speed, attackDamage);
        } else if (attackType == AttackType.INK_CONE) {
            RangedAttacks.inkCone(ctx, ecx, ecy, aimDirX, aimDirY, attackDamage);
        }
    }

    /** ล็อกทิศ/จุดหมายของการโจมตี แล้วโชว์เส้นทางหรือพื้นที่เตือน */
    private void startWindup(float targetX, float targetY) {
        SoundManager.play(SoundManager.Sfx.ENEMY_WARN);
        View pv = ctx.getPlayerContainer();
        float ew = containerView.getWidth(), eh = containerView.getHeight();
        float ecx = containerView.getX() + ew / 2f;
        float ecy = containerView.getY() + eh / 2f;
        float pcx = targetX + (pv != null ? pv.getWidth() / 2f : 0f);
        float pcy = targetY + (pv != null ? pv.getHeight() / 2f : 0f);

        if (attackType != AttackType.DASH) {
            float adx = pcx - ecx, ady = pcy - ecy;
            float alen = Math.max((float) Math.hypot(adx, ady), 1f);
            aimDirX = adx / alen;
            aimDirY = ady / alen;

            clearAttackPath();
            FrameLayout fxr = ctx.getGameArea();
            if (fxr != null) {
                if (attackType == AttackType.LINE_SHOT) {
                    float reach = (float) Math.hypot(Math.max(fxr.getWidth(), 1), Math.max(fxr.getHeight(), 1));
                    attackPathView = HitEffects.showAttackPath(fxr, ecx, ecy,
                            ecx + aimDirX * reach, ecy + aimDirY * reach, RangedAttacks.LINE_BAND_WIDTH, WINDUP_MS);
                } else {
                    attackPathView = RangedAttacks.showInkTelegraph(fxr, ecx, ecy, aimDirX, aimDirY, WINDUP_MS);
                }
            }
            return;
        }

        float ddx = pcx - ecx, ddy = pcy - ecy;
        float len = Math.max((float) Math.hypot(ddx, ddy), 1f);
        float tx = pcx + ddx / len * PASS_THROUGH_DISTANCE;
        float ty = pcy + ddy / len * PASS_THROUGH_DISTANCE;

        // จุดหมายต้องอยู่ในจอ ไม่งั้นตำแหน่งถูกจำกัดที่ขอบแล้วไปไม่ถึงจุดหมายสักที
        // ศัตรูค้างในสถานะพุ่งและยึดสิทธิ์โจมตีไว้ ตัวอื่นเลยไม่ได้โจมตีอีก
        dashTargetX = tx - ew / 2f;
        dashTargetY = ty - eh / 2f;
        View area = (View) containerView.getParent();
        if (area != null && area.getWidth() > 0) {
            dashTargetX = Math.max(0f, Math.min(area.getWidth() - ew, dashTargetX));
            dashTargetY = Math.max(0f, Math.min(area.getHeight() - eh, dashTargetY));
        }

        clearAttackPath();
        FrameLayout fx = ctx.getGameArea();
        if (fx != null) {
            attackPathView = HitEffects.showAttackPath(fx, ecx, ecy,
                    dashTargetX + ew / 2f, dashTargetY + eh / 2f, ATTACK_TRIGGER_RANGE * 2f, WINDUP_MS);
        }
    }

    private void clearAttackPath() {
        if (attackPathView != null) {
            // เส้นทางพุ่งมาจากกองที่ใช้ซ้ำ: คืนเข้ากอง (ซ่อน) ส่วนวิวอื่น (เช่น กรวยหมึก) ลบออกตามเดิม
            if (!HitEffects.releaseAttackPath(attackPathView)) {
                attackPathView.animate().cancel();
                if (attackPathView.getParent() instanceof FrameLayout) {
                    ((FrameLayout) attackPathView.getParent()).removeView(attackPathView);
                }
            }
            attackPathView = null;
        }
    }

    private static float distanceToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax, aby = by - ay;
        float len2 = abx * abx + aby * aby;
        float t = len2 < 0.0001f ? 0f : Math.max(0f, Math.min(1f, ((px - ax) * abx + (py - ay) * aby) / len2));
        return (float) Math.hypot(px - (ax + abx * t), py - (ay + aby * t));
    }

    public void takeDamage(int damage) {
        takeDamage(damage, true);
    }

    /** knockback = false สำหรับสกิลที่ต้องการให้ศัตรูอยู่กับที่ (เช่น วงหวดหนวดที่โดนซ้ำหลายรอบ) */
    public void takeDamage(int damage, boolean knockback) {
        if (!isAlive || !ctx.isGameRunning() || containerView == null) return;

        hp = Math.max(0, hp - damage);
        SoundManager.play(hp <= 0 ? SoundManager.Sfx.ENEMY_DIE : SoundManager.Sfx.HIT_ENEMY);
        if (barHp != null) barHp.setProgress(hp);
        if (txtHp != null) txtHp.setText(String.format(Locale.US, "%d/%d", hp, maxHp));

        HitEffects.impact(ctx, containerView, damage);

        // 1. แรงดันผลักถอยหลัง (Knockback Effect) เมื่อโดนสกิล
        View player = ctx.getPlayerContainer();
        if (knockback && !swallowed && player != null && containerView != null) {
            float px = player.getX();
            float py = player.getY();
            float ex = containerView.getX();
            float ey = containerView.getY();

            float kdx = ex - px;
            float kdy = ey - py;
            float kdist = (float) Math.hypot(kdx, kdy);
            if (kdist > 0.001f) {
                float knockbackDist = 55f;
                float kbX = ex + (kdx / kdist) * knockbackDist;
                float kbY = ey + (kdy / kdist) * knockbackDist;

                View area = (View) containerView.getParent();
                if (area != null && area.getWidth() > 0) {
                    kbX = Math.max(0f, Math.min(area.getWidth() - containerView.getWidth(), kbX));
                    kbY = Math.max(0f, Math.min(area.getHeight() - containerView.getHeight(), kbY));
                }

                containerView.animate().x(kbX).y(kbY).setDuration(120).start();
            }
        }

        // 2. ขัดจังหวะการพุ่งหากโดนสกิล
        if (currentState == State.ATTACKING || currentState == State.WINDUP) {
            clearAttackPath();
            if (activeAttackerId == enemyId) {
                activeAttackerId = 0;
                lastAttackEndTime = System.currentTimeMillis();
            }
            timeInOrbitMs = 0;
            currentState = State.ORBIT_AND_WAIT;
        }

        // 3. แฟลชความกะพริบแจ้งเตือนเมื่อโดนความเสียหาย
        // (ตอนถูกกลืน ไม่กะพริบ เพราะจะรีเซ็ตความโปร่งใสที่สกิลตั้งไว้)
        if (!swallowed) {
            containerView.animate().alpha(0.3f).setDuration(80)
                    .withEndAction(() -> {
                        if (containerView != null) containerView.animate().alpha(1.0f).setDuration(80).start();
                    }).start();
        }

        if (hp <= 0) {
            isAlive = false;
            clearAttackPath();
            containerView.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(250)
                    .withEndAction(() -> {
                        if (containerView != null) containerView.setVisibility(View.GONE);
                    }).start();

            ctx.onEnemyDefeated();
        }
    }
}