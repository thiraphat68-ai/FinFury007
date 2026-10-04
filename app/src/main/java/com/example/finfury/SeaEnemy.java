package com.example.finfury;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class SeaEnemy {
    // สิทธิ์โจมตี: ปกติมี 1 ช่อง (ทีละตัว) ด่านที่เปิด Dual Attacker มี 2 ช่อง
    private static int activeAttackerId = 0;
    private static int activeAttackerId2 = 0;
    private static boolean dualAttackEnabled = false;
    private static long lastAttackEndTime = 0;

    /** เรียกตอนเริ่มด่านใหม่ ตัวแปร static ค้างข้ามเกมได้ ถ้าเกมก่อนจบตอนศัตรูถือสิทธิ์โจมตีอยู่ ศัตรูชุดใหม่จะไม่ได้โจมตีเลย */
    public static void resetAttackQueue() {
        activeAttackerId = 0;
        activeAttackerId2 = 0;
        dualAttackEnabled = false;
        lastAttackEndTime = 0;
    }

    /** เปิด/ปิดโหมดโจมตีพร้อมกัน 2 ตัว (เรียกหลัง resetAttackQueue ตอนเริ่มด่าน) */
    public static void setDualAttack(boolean enabled) { dualAttackEnabled = enabled; }

    private boolean holdsAttackSlot() {
        return activeAttackerId == enemyId || activeAttackerId2 == enemyId;
    }

    private boolean attackSlotAvailable() {
        return holdsAttackSlot() || activeAttackerId == 0 || (dualAttackEnabled && activeAttackerId2 == 0);
    }

    private void claimAttackSlot() {
        if (holdsAttackSlot()) return;
        if (activeAttackerId == 0) activeAttackerId = enemyId;
        else activeAttackerId2 = enemyId;
    }

    /** คืนสิทธิ์โจมตี (ถ้าถืออยู่) พร้อมเริ่มนับช่วงพัก */
    protected void releaseAttackSlot(long now) {
        if (activeAttackerId == enemyId) activeAttackerId = 0;
        else if (activeAttackerId2 == enemyId) activeAttackerId2 = 0;
        else return;
        lastAttackEndTime = now;
    }

    /** DASH = พุ่งทะลุเข้าหา, LINE_SHOT = ยิงลำพลังเส้นตรง (แมงกะพรุน), INK_CONE = พ่นหมึกเป็นกรวย (หมึกยักษ์) */
    private enum AttackType { DASH, LINE_SHOT, INK_CONE }

    private enum State {
        ORBIT_AND_WAIT,
        WINDUP,
        ATTACKING
    }

    protected final BattleContext ctx;
    private final int enemyId;
    private final StageConfig cfg;

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

    /** สตันนานตามเวลา: ขยับ/โจมตีไม่ได้ (ต่างจาก swallowed ตรงที่โดนดาเมจ/ผลักถอยได้ตามปกติ) */
    public void stun(long durationMs) {
        stunUntilMs = Math.max(stunUntilMs, System.currentTimeMillis() + durationMs);
        setStunned(true);
        cancelAttack();
    }

    /** ยกเลิกการโจมตีที่ค้างอยู่ ไม่งั้นตัวอื่นรอคิวโจมตีไม่ได้ */
    private void cancelAttack() {
        clearAttackPath();
        releaseAttackSlot(System.currentTimeMillis());
        timeInOrbitMs = 0;
        currentState = State.ORBIT_AND_WAIT;
    }

    protected View imgAvatar;
    protected ProgressBar barHp;
    protected TextView txtHp;
    protected TextView txtName;

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

    /** สไตล์การเดินตอนรอโจมตี (ต่างกันตามชนิดศัตรู) */
    private enum MoveStyle { ORBIT, SIDESTEP, DRIFT, PATROL, KITE, ERRATIC }
    private MoveStyle moveStyle = MoveStyle.ORBIT;
    private float wanderX = 0f, wanderY = 0f;
    private long wanderUntilMs = 0;
    private static final long APPROACH_BEFORE_ATTACK_MS = 1200;   // ก่อนโจมตีเท่านี้ กลับเข้าประชิดผู้เล่น

    private static final float BASE_SPEED = BattleActivity.MAX_SPEED * 0.75f;
    private static final float ORBIT_SPEED_FACTOR = 0.60f;                      // ความเร็วตอนล้อมผู้เล่น = 60% ของความเร็วเดิน (ต้องเร็วพอตามช่องที่หมุน+ซิกแซกทัน)
    private static final float BASE_DASH_SPEED = BattleActivity.MAX_SPEED * 10f; // ความเร็วพุ่ง = 1000% ของผู้เล่น
    private static final float ORBIT_ROTATE_RAD_PER_S = 0.45f;   // วงล้อมหมุนรอบผู้เล่น
    private static final float ZIGZAG_AMPLITUDE = 90f;           // แกว่งเข้า-ออกจากรัศมีกลางข้างละกี่ px
    private static final float ZIGZAG_FREQ = 3.2f;               // ความถี่ซิกแซก (rad/s ของคลื่น)
    private static final float SEPARATION_SPEED = 400f;
    private static final float ATTACK_TRIGGER_RANGE = 45f;
    private static final float ENEMY_SPACING = 210f;   // เว้นระยะระหว่างศัตรูกว้างขึ้น 1.5 เท่า (เดิม 140)
    private static final float PASS_THROUGH_DISTANCE = 160f; // ระยะพุ่งทะลุผ่านตัวผู้เล่นออกไปด้านหลัง
    private static final long DEFAULT_ATTACK_INTERVAL_MS = 3000; // ศัตรูแต่ละตัวโจมตีทุก 3 วินาที (นับรวมช่วงเตือน) ปรับตามด่านได้
    private long attackIntervalMs = DEFAULT_ATTACK_INTERVAL_MS;
    private float dashSpeed = BASE_DASH_SPEED;
    private static final float LINE_SHOT_SPEED_FACTOR = 3f;  // ลำพลังแมงกะพรุนเร็ว 300% ของความเร็วเดินของมัน
    private static final float RECOIL_SPEED = 1400f;   // ถอยหลังยิง: ระยะรวมประมาณ RECOIL_SPEED / RECOIL_DECAY = 200 px
    private static final float RECOIL_DECAY = 7f;
    private float recoilVx = 0f, recoilVy = 0f;

    private static final float WAVE_AMPLITUDE_PX = 110f;   // กระสุนแมงกะพรุนซิกแซกเป็นคลื่น: แกว่งข้างละ 110 px
    private static final float WAVE_LENGTH_PX = 480f;      // หนึ่งรอบคลื่นยาว 480 px
    private static final float SHOCKWAVE_SPEED = 5000f;   // แรงผลัก: ไกลประมาณ 600 px (จอจำกัดไว้ที่ขอบ)
    private static final int BARRAGE_SHOTS = 15;
    private static final long BARRAGE_GAP_MS = 120;   // เดิม 80 ms ห่างขึ้น 1.5 เท่า
    private static final long JELLY_STUN_MS = 500;
    private static final float TRAVEL_CUT_PER_STAGE_S = 0.2f;
    private static final float MIN_TRAVEL_S = 0.25f;
    private static final long WINDUP_MS = 500;              // ช่วงหยุดเล็งและโชว์เส้นทางก่อนพุ่ง

    private float animTime = (float) (Math.random() * 10);
    private final float zigPhase = (float) (Math.random() * Math.PI * 2);   // แต่ละตัวซิกแซกไม่พร้อมกัน
    private final float slotOffset = (float) (Math.random() * 0.4 - 0.2);   // สุ่มมุมเล็กน้อย ไม่ให้ดูเป็นแถวเป๊ะ

    public SeaEnemy(BattleContext ctx, String name, String emoji, float posX, float posY) {
        this(ctx, name, emoji, posX, posY, StageConfig.forStage(1));
    }

    public SeaEnemy(BattleContext ctx, String name, String emoji, float posX, float posY, StageConfig cfg) {
        this.ctx = ctx;
        this.name = name;
        this.emoji = emoji;
        this.cfg = cfg;
        this.enemyId = System.identityHashCode(this);

        configureEnemyStats();
        applyStageModifiers();
        createView(posX, posY);
    }

    /** เต่า (ด่าน 2-4): โดนแล้วสโลว์ 50% นาน 2 วิ / ดาวทะเล (ด่าน 2-4): โดนแล้วผลักผู้เล่นไปตามทิศพุ่ง */
    protected void onPlayerHit(float dirX, float dirY) {
        if ("Jellyfish".equals(name)) {   // ทุกด่าน: กระสุนแมงกะพรุนทำให้สตัน 0.5 วินาที
            ctx.stunPlayer(JELLY_STUN_MS);
            return;
        }
        if (cfg.stage < 2 || cfg.stage > 4) return;
        if ("Turtle".equals(name)) {
            ctx.slowPlayer(0.5f, 2000);
        } else if ("Starfish".equals(name)) {
            ctx.knockbackPlayer(dirX, dirY, 2000f);
        }
    }

    /** เอาศัตรูออกจากฉากเงียบๆ (ไม่นับว่าถูกกำจัด: ไม่มีคลื่นกระแทก ไม่ลดเลือดบอส ไม่เช็กชนะ) */
    public void removeSilently() {
        if (!isAlive) return;
        isAlive = false;
        clearAttackPath();
        if (currentState != State.ORBIT_AND_WAIT) releaseAttackSlot(System.currentTimeMillis());
        if (containerView != null) {
            containerView.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(300)
                    .withEndAction(() -> {
                        if (containerView != null) containerView.setVisibility(View.GONE);
                    }).start();
        }
    }

    /** มุมช่องของตัวนี้บนวงล้อมผู้เล่น: แบ่งวง 360° เท่าๆ กันตามจำนวนศัตรูที่ยังมีชีวิต (ไม่นับบอส) */
    private double slotAngle() {
        int idx = 0, n = 0;
        List<SeaEnemy> all = ctx.getEnemies();
        for (int i = 0; i < all.size(); i++) {
            SeaEnemy e = all.get(i);
            if (e == this) idx = n;
            if (e.isAlive && !(e instanceof KrakenBoss) && (e == this || !e.swallowed)) n++;
        }
        return Math.PI * 2 * idx / Math.max(1, n) + slotOffset;
    }

    /** จุดที่ศัตรูจะเดินไปหาตอนรอโจมตี ตามสไตล์การเดิน */
    private float[] computeHoldPoint(float targetX, float targetY, float curX, float curY,
                                     float distance, float tSec, long now) {
        float mid = (holdMinDistance + holdMaxDistance) / 2f;
        float baseAngle = (float) (slotAngle() + ORBIT_ROTATE_RAD_PER_S * tSec);
        boolean approaching = timeInOrbitMs >= attackIntervalMs - WINDUP_MS - APPROACH_BEFORE_ATTACK_MS;

        float angle = baseAngle;
        float dist = mid;
        switch (moveStyle) {
            case SIDESTEP:   // ปู: ก้าวข้างไปมาแทนการวนรอบ
                angle = (float) slotAngle() + (float) Math.sin(tSec * 1.1f + zigPhase) * 0.9f;
                dist = mid + triangleWave(tSec * 1.6f + zigPhase) * 25f;
                break;
            case DRIFT: {    // แมงกะพรุน: ลอยขึ้นลงเป็นคลื่นกว้าง ระยะเข้า-ออกช้าๆ
                dist = mid + (float) Math.sin(tSec * 0.9f + zigPhase) * (holdMaxDistance - holdMinDistance + 60f);
                float bob = (float) Math.sin(tSec * 2.2f + zigPhase) * 70f;
                float hx = targetX + (float) Math.cos(baseAngle) * dist;
                float hy = targetY + (float) Math.sin(baseAngle) * dist + bob;
                return new float[]{hx, hy};
            }
            case KITE: {     // หมึก: ผู้เล่นเข้าใกล้เกิน ถอยตรงข้าม ไม่งั้นลอยวนช้าๆ ที่ระยะไกล
                if (distance < holdMinDistance && distance > 0.001f) {
                    float fx = curX + (curX - targetX) / distance * 260f;
                    float fy = curY + (curY - targetY) / distance * 260f;
                    return new float[]{fx, fy};
                }
                dist = mid + triangleWave(tSec * ZIGZAG_FREQ + zigPhase) * 25f;
                break;
            }
            case PATROL:     // เต่า: ตระเวนไปจุดสุ่มบนแมพ แล้วค่อยกลับมาหาผู้เล่นตอนใกล้โจมตี
            case ERRATIC: {  // ดาวทะเล: เปลี่ยนจุดสุ่มรอบผู้เล่นถี่ๆ ทิศไม่แน่นอน
                if (approaching) break;
                if (now >= wanderUntilMs || Math.hypot(wanderX - curX, wanderY - curY) < 40f) {
                    pickWanderPoint(targetX, targetY, now);
                }
                return new float[]{wanderX, wanderY};
            }
            case ORBIT:
            default: {
                float zig = triangleWave(tSec * ZIGZAG_FREQ + zigPhase);
                dist = mid + zig * ZIGZAG_AMPLITUDE;
                break;
            }
        }
        dist = Math.max(holdMinDistance, Math.min(holdMaxDistance, dist));
        return new float[]{targetX + (float) Math.cos(angle) * dist, targetY + (float) Math.sin(angle) * dist};
    }

    private void pickWanderPoint(float targetX, float targetY, long now) {
        View area = containerView != null ? (View) containerView.getParent() : null;
        float aw = area != null && area.getWidth() > 0 ? area.getWidth() : 1000f;
        float ah = area != null && area.getHeight() > 0 ? area.getHeight() : 500f;
        float maxX = Math.max(0f, aw - (containerView != null ? containerView.getWidth() : 0));
        float maxY = Math.max(0f, ah - (containerView != null ? containerView.getHeight() : 0));
        if (moveStyle == MoveStyle.ERRATIC) {
            double a = Math.random() * Math.PI * 2;
            float r = holdMinDistance + (float) Math.random() * (holdMaxDistance - holdMinDistance + 150f);
            wanderX = targetX + (float) Math.cos(a) * r;
            wanderY = targetY + (float) Math.sin(a) * r;
            wanderUntilMs = now + 900 + (long) (Math.random() * 1200);
        } else {
            wanderX = (float) Math.random() * maxX;
            wanderY = (float) Math.random() * maxY;
            wanderUntilMs = now + 2500 + (long) (Math.random() * 2500);
        }
        wanderX = Math.max(0f, Math.min(maxX, wanderX));
        wanderY = Math.max(0f, Math.min(maxY, wanderY));
    }

    /** คลื่นสามเหลี่ยม -1..1 (เส้นตรงสลับขึ้น-ลง = ซิกแซกคม ต่างจากไซน์ที่โค้งนุ่ม) */
    private static float triangleWave(float x) {
        return (float) (2.0 / Math.PI * Math.asin(Math.sin(x)));
    }

    /** ตายแล้วปล่อยคลื่นกระแทก: ผลักผู้เล่นออกจากจุดที่ตายไปทางขอบจอ ไม่มีดาเมจ */
    private void deathShockwave() {
        View pv = ctx.getPlayerContainer();
        FrameLayout fx = ctx.getGameArea();
        if (pv == null || fx == null || !ctx.isGameRunning()) return;

        float ecx = containerView.getX() + containerView.getWidth() / 2f;
        float ecy = containerView.getY() + containerView.getHeight() / 2f;
        float dx = pv.getX() + pv.getWidth() / 2f - ecx;
        float dy = pv.getY() + pv.getHeight() / 2f - ecy;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) {   // ทับกันพอดี: สุ่มทิศ
            double a = Math.random() * Math.PI * 2;
            dx = (float) Math.cos(a);
            dy = (float) Math.sin(a);
            len = 1f;
        }
        ctx.knockbackPlayer(dx / len, dy / len, SHOCKWAVE_SPEED);

        // วงคลื่นขยายออกจากจุดที่ตาย
        View ring = new View(ctx.getContext());
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(Color.TRANSPARENT);
        bg.setStroke(10, Color.parseColor("#B3FFFFFF"));
        ring.setBackground(bg);
        final int size = 200;
        ring.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        ring.setX(ecx - size / 2f);
        ring.setY(ecy - size / 2f);
        fx.addView(ring);
        ring.animate().scaleX(6f).scaleY(6f).alpha(0f).setDuration(450)
                .withEndAction(() -> fx.removeView(ring)).start();
    }

    /** ตัวคูณดาเมจ/เลือดเพิ่มเติมจากค่าของด่าน (ลูกน้องบอสใช้) */
    protected float damageScale() { return 1f; }

    protected float hpScale() { return 1f; }

    /** เรียกตอนศัตรูตาย ก่อนแจ้ง ctx (ลูกน้องบอสใช้ลดเลือดบอส) */
    protected void onDefeated() {}

    /** ปรับสเตตัสพื้นฐานตามความยากของด่าน (เลือด / ความเร็ว / คูลดาวน์โจมตี / ความเร็วพุ่ง) */
    private void applyStageModifiers() {
        maxHp = Math.max(1, Math.round(maxHp * cfg.hpMul * hpScale()));
        hp = maxHp;
        speedMultiplier *= cfg.speedMul;
        attackIntervalMs = cfg.attackIntervalMs;
        dashSpeed = BASE_DASH_SPEED * cfg.dashSpeedMul;
        attackDamage = Math.max(1, Math.round(attackDamage * cfg.damageMul * damageScale()));
    }

    /** ขนาดภาพศัตรูเทียบปกติ (บอสใหญ่ 4 เท่า) */
    protected float visualScale() { return 1f; }

    /** Resource ID ของรูปภาพศัตรู (0 = ใช้ Emoji แทน) */
    protected int getDrawableResId() { return 0; }

    /** ศัตรูโดนผลักถอยตอนโดนสกิลได้ไหม */
    protected boolean allowKnockback() { return true; }

    /** HP หมดแล้ว มีชีวิตเหลือให้ฟื้นไหม (true = ไม่ตาย) บอสหลายชีวิต override */
    protected boolean reviveOnDepleted() { return false; }

    /** จำนวนชีวิตที่เหลือ (ศัตรูปกติมี 1) */
    public int livesLeft() { return 1; }

    protected void configureEnemyStats() {
        if (emoji.contains("🦀") || name.contains("ปู")) {
            // ปูซ่า: เลือดเยอะ ช้า
            maxHp = 16;
            hp = 16;
            speedMultiplier = 0.85f;
            holdMinDistance = 160f;
            holdMaxDistance = 240f;
            attackDamage = 6;
            moveStyle = MoveStyle.SIDESTEP;
        } else if (emoji.contains("🦑") || name.contains("หมึก")) {
            // หมึกยักษ์: วิ่งเร็ว วนใกล้ ดุดัน
            maxHp = 12;
            hp = 12;
            speedMultiplier = 1.25f;
            holdMinDistance = 420f;
            holdMaxDistance = 500f;  // ต้องน้อยกว่าระยะกรวยหมึก x0.9 (RangedAttacks.INK_RANGE) เพื่อให้กรวยถึงตัวผู้เล่น
            attackDamage = 5;
            attackType = AttackType.INK_CONE;
            moveStyle = MoveStyle.KITE;
        } else if (emoji.contains("🐢") || name.contains("เต่า")) {
            // เต่าทะเล: เลือดเยอะที่สุด วนไกล เดินช้า
            maxHp = 18;
            hp = 18;
            speedMultiplier = 0.85f;
            holdMinDistance = 240f;
            holdMaxDistance = 340f;
            attackDamage = 5;
            moveStyle = MoveStyle.PATROL;
        } else if (emoji.contains("🪼") || name.toLowerCase(Locale.US).contains("jellyfish") || name.contains("กะพรุน")) {
            // แมงกะพรุน: ความเร็วปกติ รักษาระยะห่าง 240px
            maxHp = 9;
            hp = 9;
            speedMultiplier = 1.0f;
            holdMinDistance = 180f;   // เข้าหาผู้เล่นใกล้กว่าเดิม (เดิม 320-360)
            holdMaxDistance = 220f;
            attackDamage = 4;
            attackType = AttackType.LINE_SHOT;
            moveStyle = MoveStyle.DRIFT;
        } else {
            // ดาวทะเล หรืออื่นๆ: สเตตัสสมดุล
            maxHp = 11;
            hp = 11;
            speedMultiplier = 1.0f;
            holdMinDistance = 200f;
            holdMaxDistance = 320f;
            attackDamage = 5;
            moveStyle = MoveStyle.ERRATIC;
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

        final float vs = visualScale();
        final float uiScale = Math.min(vs, 2f);   // ชื่อ/หลอดเลือดโตสูงสุด 2 เท่า ตัวภาพโตเต็ม vs
        txtName = new TextView(ctx.getContext());
        txtName.setText(name);
        txtName.setTextColor(0xFFFF5252);   // ฝั่งศัตรู = สีแดง
        txtName.setTextSize(11f * uiScale);
        txtName.setGravity(Gravity.CENTER);

        barHp = new ProgressBar(ctx.getContext(), null, android.R.attr.progressBarStyleHorizontal);
        barHp.setMax(maxHp);
        barHp.setProgress(hp);
        barHp.setProgressTintList(ColorStateList.valueOf(Color.RED));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams((int) (90 * vs), (int) (14 * uiScale));
        barParams.bottomMargin = 2;
        barHp.setLayoutParams(barParams);

        txtHp = new TextView(ctx.getContext());
        txtHp.setText(String.format(Locale.US, "%d/%d", hp, maxHp));
        txtHp.setTextColor(Color.YELLOW);
        txtHp.setTextSize(9f * uiScale);
        txtHp.setGravity(Gravity.CENTER);

        int drawableRes = getDrawableResId();
        if (drawableRes != 0) {
            ImageView imgView = new ImageView(ctx.getContext());
            imgView.setImageResource(drawableRes);
            imgView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int sizePx = (int) (65 * vs);
            imgView.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
            imgAvatar = imgView;
        } else {
            TextView txtAvatar = new TextView(ctx.getContext());
            txtAvatar.setText(emoji);
            txtAvatar.setTextSize(36f * vs);
            txtAvatar.setGravity(Gravity.CENTER);
            imgAvatar = txtAvatar;
        }

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

                // 1. เดินตามสไตล์ของแต่ละชนิด (ปู=ก้าวข้าง, แมงกะพรุน=ลอยเป็นคลื่น, เต่า=ตระเวนสุ่ม,
                // หมึก=ถอยเว้นระยะ, ดาวทะเล=สุ่มเปลี่ยนจุดไม่แน่นอน) แล้วค่อยกลับมาประชิดตอนใกล้ถึงเวลาโจมตี
                float tSec = now / 1000f;
                float[] hold = computeHoldPoint(targetX, targetY, currentX, currentY, distance, tSec, now);
                float orbitSpeed = (distance > holdMaxDistance + 60f && moveStyle != MoveStyle.PATROL
                        && moveStyle != MoveStyle.ERRATIC) ? moveSpeed : moveSpeed * ORBIT_SPEED_FACTOR;
                if (moveStyle == MoveStyle.KITE && distance < holdMinDistance) orbitSpeed = moveSpeed;   // ถอยหนีเต็มสปีด

                float hdx = hold[0] - currentX;
                float hdy = hold[1] - currentY;
                float hdist = (float) Math.hypot(hdx, hdy);
                if (hdist > 0.001f) {
                    float step = Math.min(orbitSpeed * dt, hdist);
                    nextX += (hdx / hdist) * step;
                    nextY += (hdy / hdist) * step;
                }

                // 2. ตรวจสอบคิวขอสิทธิ์พุ่งทะลุผ่านตัวผู้เล่น (Pass-Through Dash)
                boolean isAttackerSlotFree = attackSlotAvailable();
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
                    claimAttackSlot();
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
                        releaseAttackSlot(now);
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
                    float step = Math.min(dashSpeed * dt, ddist);
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
                    if (ddist > 0.001f) onPlayerHit(ddx / ddist, ddy / ddist);
                }

                // เมื่อพุ่งถึงจุดหมายด้านหลังผู้เล่นเรียบร้อย ให้กลับเข้าสู่สถานะวนรอบรอ
                if (ddist <= 15f) {
                    releaseAttackSlot(now);
                    timeInOrbitMs = 0;
                    currentState = State.ORBIT_AND_WAIT;
                }
                break;
            }
        }

        // 3. เว้นระยะห่างระหว่างศัตรูด้วยกันเอง (ตัวที่กำลังพุ่งทะลุจะไม่โดนผลัก)
        for (int otherIdx = 0; otherIdx < ctx.getEnemies().size(); otherIdx++) {
            SeaEnemy other = ctx.getEnemies().get(otherIdx);
            if (currentState == State.ORBIT_AND_WAIT && other != this && other.isAlive && other.containerView != null
                    && !(other instanceof KrakenBoss)) {
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

        // แรงสะท้อนหลังยิง (แมงกะพรุน): ถูกผลักถอยตรงข้ามทิศยิง แล้วค่อยๆ หมดแรง
        if (recoilVx != 0f || recoilVy != 0f) {
            nextX += recoilVx * dt;
            nextY += recoilVy * dt;
            float decay = (float) Math.exp(-RECOIL_DECAY * dt);
            recoilVx *= decay;
            recoilVy *= decay;
            if (Math.abs(recoilVx) + Math.abs(recoilVy) < 5f) recoilVx = recoilVy = 0f;
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
        if (timeInOrbitMs < attackIntervalMs - WINDUP_MS) return false;
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
        final float ax = aimDirX, ay = aimDirY;
        Runnable onHit = () -> onPlayerHit(ax, ay);
        if (attackType == AttackType.LINE_SHOT) {
            float speed = BASE_SPEED * speedMultiplier * LINE_SHOT_SPEED_FACTOR;
            FrameLayout area = ctx.getGameArea();
            if (area == null) return;
            // ทุกด่านที่ผ่านมา เวลาที่ลำพลังบินข้ามจอสั้นลง 0.2 วินาที (บินเร็วขึ้น)
            if (area.getWidth() > 0) {
                float maxDist = (float) Math.hypot(area.getWidth(), area.getHeight());
                float travelS = Math.max(MIN_TRAVEL_S, maxDist / speed - TRAVEL_CUT_PER_STAGE_S * (cfg.stage - 1));
                speed = maxDist / travelS;
            }
            // กระสุนรัว 15 นัดตามทิศที่ล็อกไว้ตอนเตือน นัดละ 120 ms ดาเมจต่อนัดลดเหลือ 1/3 กันโดนรัวจนตาย
            recoilVx = -ax * RECOIL_SPEED;
            recoilVy = -ay * RECOIL_SPEED;
            final float fspeed = speed;
            final int shotDamage = Math.max(1, Math.round(attackDamage / 3f));
            for (int i = 0; i < BARRAGE_SHOTS; i++) {
                final Runnable shot = () -> {
                    if (isAlive && ctx.isGameRunning() && !ctx.isGamePaused()) {
                        RangedAttacks.fireLine(ctx, ecx, ecy, ax, ay, fspeed, shotDamage, onHit,
                                WAVE_AMPLITUDE_PX, WAVE_LENGTH_PX);
                    }
                };
                if (i == 0) shot.run(); else area.postDelayed(shot, i * BARRAGE_GAP_MS);
            }
        } else if (attackType == AttackType.INK_CONE) {
            RangedAttacks.inkCone(ctx, ecx, ecy, aimDirX, aimDirY, attackDamage, onHit);
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

        if (attackType == AttackType.LINE_SHOT) {
            // เล็งนำไปตามทิศที่ผู้เล่นกำลังเคลื่อนที่ (ผู้เล่นจะเดินต่อไปอีกช่วงเตือน WINDUP_MS ก่อนยิง)
            float ratio = ctx.getPlayerSpeedRatio();
            if (ratio > 0.1f) {
                double rad = Math.toRadians(ctx.getPlayerAngle());
                float lead = BattleActivity.MAX_SPEED * ratio * WINDUP_MS / 1000f;
                pcx += (float) Math.cos(rad) * lead;
                pcy += (float) Math.sin(rad) * lead;
            }
        }

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

    /** โบนัสดาเมจจากไอเทม (BattleActivity เซ็ตทุกเฟรม) บวกเฉพาะการโดนครั้งเดียว ไม่บวกดาเมจต่อเนื่องที่ knockback=false */
    public static int playerDamageBonus = 0;

    public void takeDamage(int damage) {
        takeDamage(damage + playerDamageBonus, true);
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
        if (knockback && allowKnockback() && !swallowed && player != null && containerView != null) {
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
            releaseAttackSlot(System.currentTimeMillis());
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

        if (hp <= 0 && reviveOnDepleted()) return;

        if (hp <= 0) {
            isAlive = false;
            clearAttackPath();
            containerView.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(250)
                    .withEndAction(() -> {
                        if (containerView != null) containerView.setVisibility(View.GONE);
                    }).start();

            if (!(this instanceof KrakenBoss)) {
                deathShockwave();
                ctx.dropItemAt(containerView.getX() + containerView.getWidth() / 2f,
                        containerView.getY() + containerView.getHeight() / 2f, false);
            }
            onDefeated();
            ctx.onEnemyDefeated();
        }
    }
}
