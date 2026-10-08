// =====================================================================================
// [คนที่ 4 - บอท เสียง และเอฟเฟกต์]  ไฟล์: KrakenBoss.java  (476 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/KrakenBoss.java
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   บอสด่าน 5 "Kraken Boss" (extends SeaEnemy) ตัวใหญ่ 8 เท่า มี 2 ชีวิต:
//     ชีวิตที่ 1 (เฟส 1) เลือด 100 : ยืนนิ่งกลางจอด้านขวา ยิงคลื่นพลังเป็นชุด (5 เส้น 2 ชุด) ทุก 5 วินาที
//     ชีวิตที่ 2 (เฟส 2) เลือด 30  : ตัวใหญ่ขึ้น 2 เท่า โกรธ ยิงถี่ขึ้น 1.5 เท่า คลื่น 7 เส้น 3 ชุด เร็วขึ้น และคืบเข้าหาผู้เล่นช้า ๆ
//                                   + หางฟาดระยะประชิด (ดาเมจ 15) มีวงเตือนก่อน
//   ลูกน้อง (BossMinion) 5 ตัวช่วยเฟส 1: ลูกน้องตาย 1 ตัว บอสเสียเลือด 10
//   บอส "ไม่โดนสตัน สโลว์ กลืน ผลักถอย" (override เป็นเมธอดว่าง)
//   หลอดเลือดบอสอยู่บน HUD กลางบนของจอ (layoutBossBar ใน activity_battle.xml) ไม่ใช่ติดตัวบอส
//   ภาพบอสเป็นแอนิเมชัน 47 เฟรม octo_idle_00..46 (res/drawable-nodpi/) เล่น 24 เฟรม/วินาที
//
// [ผังไฟล์] ค่าคงที่ -> ตัวแปร -> constructor -> override ต่าง ๆ (ไม่โดนสตัน ฯลฯ) -> reviveOnDepleted (เสียชีวิตแรก)
//           -> applyBossSize/enterPhase2/loadFrames/ชื่อ -> HUD (setupBossHud/syncHud) -> takeDamage
//           -> computeHome/placeAtHome -> updateAI (สมองบอส) -> หางฟาด -> เส้นเตือน -> animate
//
// [จะแก้ความยากบอสยังไง]
//   เลือด: BOSS_HP, PHASE2_HP | จำนวนชีวิต: MAX_LIVES (ต้องแก้ตรรกะเฟส 2 ด้วยถ้าเพิ่ม)
//   คลื่นพลัง: WAVE_DAMAGE, WAVE_SPEED, WAVE_COOLDOWN_MS, WAVE_VOLLEYS และชุดค่า PHASE2_*
//   หางฟาด: TAIL_DAMAGE, TAIL_COOLDOWN_MS, TAIL_WINDUP_MS (ยิ่งนานยิ่งหลบง่าย), TAIL_EXTRA_RADIUS
//   ลูกน้องกระทบเลือดบอสแค่ไหน: BOSS_HP_LOSS ใน BossMinion.java
// =====================================================================================
package com.example.finfury;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;

// [คอมเมนต์คลาส - โดยเจ้าของไฟล์เดิม]
/**
 * Mega Boss ด่าน 5: Kraken ตัวใหญ่ 4 เท่า 2 ชีวิต (ชีวิตแรกเลือด 100, เฟส 2 เลือด 30)
 * - ปกติยืนนิ่งอยู่กลางจอด้านขวา: ปล่อยคลื่นพลังเป็นชุด (ไม่มีการโจมตีระยะประชิดแล้ว)
 * - บอสไม่เคลื่อนที่ ยืนอยู่กับที่ตลอด แต่โจมตีถี่
 * - ไม่โดนสตัน/สโลว์/กลืน/ผลักถอย
 */
public class KrakenBoss extends SeaEnemy {

    // [ค่าคงที่เลือด] BOSS_HP = เลือดชีวิตแรก | MAX_LIVES = จำนวนชีวิต | PHASE2_HP = เลือดชีวิตที่สอง
    private static final int BOSS_HP = 100;
    private static final int MAX_LIVES = 2;
    private static final int PHASE2_HP = 30;

    // [ค่าคงที่คลื่นพลัง] WAVE_WINDUP_MS = เวลาเตือนก่อนยิงแต่ละชุด | WAVE_COOLDOWN_MS = พักระหว่างรอบ | WAVE_DAMAGE = ดาเมจต่อเส้น
    //   WAVE_SPREAD_DEG = มุมห่างระหว่างเส้น | WAVE_SPEED = ความเร็วคลื่น (px/วินาที)
    private static final long WAVE_WINDUP_MS = 500;
    private static final long WAVE_COOLDOWN_MS = 5000;

    private static final int WAVE_DAMAGE = 5;

    private static final float WAVE_SPREAD_DEG = 14f;
    private static final float WAVE_SPEED = 1700f;
    // [ค่าคงที่ผลโดน] โดนบอสตี = ผู้เล่นเหลือ 50% ความเร็ว นาน 3 วินาที | PHASE2_SIZE_MUL ขยายเฟส 2 | PHASE2_MOVE_SPEED ความเร็วเดินเฟส 2
    private static final float HIT_SLOW_FACTOR = 0.5f;   // โดนบอสตี = เคลื่อนที่เหลือ 50%
    private static final long HIT_SLOW_MS = 3000;
    private static final float PHASE2_SIZE_MUL = 2f;     // เฟส 2 ตัวใหญ่ขึ้น 2 เท่า
    private static final float PHASE2_MOVE_SPEED = 90f;  // เฟส 2 เคลื่อนที่เข้าหาผู้เล่นช้าๆ (px/s)
    // [ค่าคงที่เฟส 2] PHASE2_WAVE_LINES จำนวนเส้น , PHASE2_WAVE_SPREAD_DEG มุมห่าง (น้อยลง = แน่นขึ้น) , PHASE2_WAVE_VOLLEYS จำนวนชุด , PHASE2_WAVE_SPEED ความเร็ว
    // เฟส 2: ยิงคลื่นหลบยากขึ้น (เส้นมากขึ้น/แน่นขึ้น/ชุดมากขึ้น/เร็วขึ้น)
    private static final int PHASE2_WAVE_LINES = 7;
    private static final float PHASE2_WAVE_SPREAD_DEG = 10f;
    private static final int PHASE2_WAVE_VOLLEYS = 3;
    private static final float PHASE2_WAVE_SPEED = 2200f;
    // [ค่าคงที่หางฟาด (เฟส 2)] ดาเมจ 15 , คูลดาวน์ 5 วินาที , เวลาเตือน 0.6 วินาที , วงฟาดกว้างกว่าตัวบอสอีก 120 px , PHASE2_RATE_MUL ความถี่โจมตีเฟส 2
    // หางฟาด (เฟส 2): ระยะประชิด ดาเมจ 15 คูลดาวน์ 5 วินาที มีวงเตือนก่อนฟาด
    private static final int TAIL_DAMAGE = 15;
    private static final long TAIL_COOLDOWN_MS = 5000;
    private static final long TAIL_WINDUP_MS = 600;
    private static final float TAIL_EXTRA_RADIUS = 120f;   // วงฟาดกว้างกว่าตัวบอสออกไปเท่านี้
    private static final float PHASE2_RATE_MUL = 1.5f;   // เฟส 2 โจมตีถี่ขึ้น 1.5 เท่า
    // [BOSS_SCALE] ขนาดภาพบอสเทียบมาตรฐาน (8 เท่า) | FRAME_ASPECT สัดส่วนกว้างยาวของเฟรมแอนิเมชัน (170x256) | WAVE_VOLLEYS ชุดต่อรอบเฟส 1
    private static final float BOSS_SCALE = 8f;          // ลดลงครึ่งหนึ่งจาก 16 (เฟส 2 ยังขยาย 1.8 เท่าจากค่านี้)
    private static final float FRAME_ASPECT = 170f / 256f;
    private static final int WAVE_VOLLEYS = 2;          // ปล่อยคลื่นพลัง 2 ชุดต่อรอบ ห่างกัน WAVE_WINDUP_MS

    // [Phase] สถานะสมองบอส: IDLE (ยืนรอ) , WAVE_WINDUP (เตรียมยิงคลื่น) , TAIL_WINDUP (เตรียมหางฟาด)
    private enum Phase { IDLE, WAVE_WINDUP, TAIL_WINDUP }

    // [ตัวแปรสถานะ] lives = ชีวิตที่เหลือ | phase = สถานะตอนนี้ | phaseMs = เวลาที่อยู่ในสถานะ | waveCooldownMs = ตัวนับถอยหลังก่อนยิงคลื่นรอบถัดไป
    private int lives = MAX_LIVES;
    private Phase phase = Phase.IDLE;
    private long lastUpdateMs = 0;
    private long phaseMs = 0;
    private long waveCooldownMs = 2000;

    // [จุดพัก] homeX/homeY = ตำแหน่งที่บอสยืนรอ (72% ของความกว้างจอ กึ่งกลางแนวตั้ง) | homeSet = วางตำแหน่งแล้วหรือยัง
    private float homeX, homeY;
    private boolean homeSet = false;

    // [ตัวแปรคลื่น] waveVolley = ชุดที่ยิงไปแล้ว | attackRate = ตัวคูณความถี่ (เฟส 2 = 1.5) | sizeMul = ตัวคูณขนาด (เฟส 2 = 2)
    private int waveVolley = 0;
    private float attackRate = 1f;
    private float sizeMul = 1f;

    // [โบนัสดาเมจที่ผู้เล่นทำใส่บอส +15%] damageCarry = เศษดาเมจสะสม (ดาเมจเป็นจำนวนเต็มเล็ก ถ้าปัดทิ้งโบนัสจะหาย)
    //   ตัวแปรเฟส 2: phase2, waveLines, waveSpreadDeg, waveVolleys, waveSpeed (ค่าเริ่มต้นเฟส 1 แล้วเปลี่ยนตอน enterPhase2) , tailCooldownMs, tailTelegraph
    private static final float PLAYER_DAMAGE_MUL = 1.15f;   // ดาเมจที่ผู้เล่นทำใส่บอส +15%
    private float damageCarry = 0f;
    private boolean phase2 = false;
    private int waveLines = 5;
    private float waveSpreadDeg = WAVE_SPREAD_DEG;
    private int waveVolleys = WAVE_VOLLEYS;
    private float waveSpeed = WAVE_SPEED;
    private long tailCooldownMs = 0;
    private View tailTelegraph;

    // [เส้นเตือนคลื่น] อาร์เรย์เก็บเส้นเตือน + ทิศ (x, y) ของแต่ละเส้น ขนาดเท่าเส้นสูงสุด (เฟส 2 = 7)
    private final View[] waveTelegraphs = new View[PHASE2_WAVE_LINES];
    private final float[] waveDirX = new float[PHASE2_WAVE_LINES];
    private final float[] waveDirY = new float[PHASE2_WAVE_LINES];

    // [ตัวแปรแอนิเมชัน] animTime , damageMul (ตัวคูณดาเมจตามด่านจาก StageConfig)
    private float animTime = 0f;
    private final float damageMul;

    // [เฟรมแอนิเมชัน] FRAME_COUNT เฟรม โหลดครั้งเดียวเก็บในอาร์เรย์ frames ไม่ decode ซ้ำทุกเฟรม (ประหยัดเครื่อง) , FRAME_FPS ความเร็วเล่น
    // เฟรมแอนิเมชัน octo_idle_00..46 โหลดครั้งเดียวไว้ในหน่วยความจำ (ไม่ decode ซ้ำทุกเฟรม)
    private static final int FRAME_COUNT = 47;
    private static final float FRAME_FPS = 24f;
    private final android.graphics.drawable.Drawable[] frames = new android.graphics.drawable.Drawable[FRAME_COUNT];
    private int frameCount = 0;
    private float frameTime = 0f;
    private int shownFrame = -1;

    // [constructor] เรียก super (สร้างศัตรูพื้นฐาน) แล้วตั้ง HUD หลอดเลือดบอส โหลดเฟรม ตั้งชื่อ
    //   ขนาดจริงของจอรู้ตอน layout เสร็จ จึงใช้ containerView.post สองชั้น: ปรับขนาดภาพ แล้วค่อยวางที่จุดพัก
    public KrakenBoss(BattleContext ctx, float posX, float posY, StageConfig cfg) {
        super(ctx, "Kraken Boss", "🦑", posX, posY, cfg);
        damageMul = cfg.damageMul;
        setupBossHud();
        loadFrames();
        refreshNameLabel();
        // ขนาดจริงรู้ตอน layout เสร็จ แล้วค่อยวางบอสกลางด้านขวาของจอ
        containerView.post(() -> {
            applyBossSize();                        // ตอนนี้รู้ขนาดจอแล้ว: ย่อไม่ให้สูงเกินจอ
            containerView.post(this::placeAtHome);  // รอ layout ใหม่เสร็จก่อนวาง
        });
    }

    // [ข้อควรระวัง] configureEnemyStats ถูกเรียกจาก constructor ของ SeaEnemy ก่อนที่ฟิลด์ของ KrakenBoss ถูกกำหนดค่า จึงแตะได้เฉพาะฟิลด์ของ SeaEnemy (maxHp, hp)
    // ---------------------------------------------------------
    // ตั้งค่าเฉพาะบอส (ถูกเรียกจาก constructor ของ SeaEnemy: แตะได้เฉพาะฟิลด์ของ SeaEnemy)
    // ---------------------------------------------------------
    @Override
    protected void configureEnemyStats() {
        maxHp = BOSS_HP;
        hp = BOSS_HP;
    }

    // [override ของบอส] visualScale = ขยาย 8 เท่า | getDrawableResId = ใช้ภาพ boss_image แทนอีโมจิ | allowKnockback = ไม่ถูกผลักถอย | livesLeft = จำนวนชีวิต
    @Override
    protected float visualScale() { return BOSS_SCALE; }

    @Override
    protected int getDrawableResId() { return R.drawable.boss_image; }

    @Override
    protected boolean allowKnockback() { return false; }

    @Override
    public int livesLeft() { return lives; }

    // [บอสไม่โดนสถานะ] stun / applySlow / setSwallowed เป็นเมธอดว่าง (ตั้งใจ) บอสตัวใหญ่เกินกว่าจะโดน
    @Override
    public void stun(long durationMs) { /* บอสไม่โดนสตัน */ }

    @Override
    public void applySlow(float factor, long durationMs) { /* บอสไม่โดนสโลว์ */ }

    @Override
    public void setSwallowed(boolean value) { /* บอสตัวใหญ่เกินกว่าจะกลืน */ }

    // [reviveOnDepleted = ตอน HP หมด] SeaEnemy.takeDamage เรียกเมธอดนี้: คืน true = ไม่ตาย / false = ตายจริง
    //   ลดชีวิต 1 + ยกเลิกเส้นเตือน ถ้าไม่เหลือชีวิต = ตายจริง
    //   ถ้ายังเหลือ: เข้าเฟส 2 : เลือดเหลือ PHASE2_HP , อัปเดตหลอด , เสียงเตือน , ctx.onEnemyLifeLost() (BattleActivity เก็บลูกน้อง + ดรอปหัวใจ) , enterPhase2()
    @Override
    protected boolean reviveOnDepleted() {
        lives--;
        cancelTelegraphs();
        if (lives <= 0) {
            refreshNameLabel();
            return false;   // หมดทุกชีวิต: ตายตามปกติ
        }
        maxHp = PHASE2_HP;   // เฟส 2 เลือดเหลือแค่ 30 ให้ลูกน้องช่วยลด (ตัวละ 10)
        hp = maxHp;
        if (hudBar != null) hudBar.setMax(maxHp);
        if (barHp != null) { barHp.setMax(maxHp); barHp.setProgress(hp); }
        if (txtHp != null) txtHp.setText(hp + "/" + maxHp);
        refreshNameLabel();
        SoundManager.play(SoundManager.Sfx.ENEMY_WARN);
        // เสียชีวิตแรก: โกรธ ยิงคลื่นชุดต่อไปเร็วขึ้น
        phase = Phase.IDLE;
        phaseMs = 0;
        waveCooldownMs = 1000;
        ctx.onEnemyLifeLost();
        enterPhase2();
        return true;
    }

    // [applyBossSize] ปรับกรอบภาพให้ตรงสัดส่วนเฟรม (hitbox จะพอดีตัว) กว้าง = 65 x BOSS_SCALE x sizeMul และบีบไม่ให้สูงเกิน 95% ของจอ
    /** ปรับกรอบภาพบอสให้ตรงสัดส่วนเฟรม (กว้างกว่าสูง) เพื่อให้ hitbox พอดีตัว; sizeMul ใช้ขยายตอนเฟส 2 */
    private void applyBossSize() {
        android.view.ViewGroup.LayoutParams lp = imgAvatar.getLayoutParams();
        if (lp == null) return;
        float w = 65f * BOSS_SCALE * sizeMul;
        float h = w * FRAME_ASPECT;
        View area = containerView.getParent() instanceof View ? (View) containerView.getParent() : null;
        if (area != null && area.getHeight() > 0 && h > area.getHeight() * 0.95f) {   // ห้ามสูงเกินจอ
            h = area.getHeight() * 0.95f;
            w = h / FRAME_ASPECT;
        }
        lp.width = (int) w;
        lp.height = (int) h;
        imgAvatar.setLayoutParams(lp);
    }

    // [enterPhase2] เปลี่ยนค่าทั้งหมดเป็นของเฟส 2 (ขนาด ความถี่ จำนวนเส้น/ชุด/ความเร็วคลื่น ตั้งคูลดาวน์หางฟาดครั้งแรก 2 วินาที)
    //   แล้วหลัง layout ใหม่ ขยายออกรอบจุดกลางเดิม และคำนวณจุดพักใหม่ให้พอดีขนาดใหม่
    private void enterPhase2() {
        float cx = containerView.getX() + containerView.getWidth() / 2f;
        float cy = containerView.getY() + containerView.getHeight() / 2f;
        sizeMul = PHASE2_SIZE_MUL;
        attackRate = PHASE2_RATE_MUL;
        phase2 = true;
        waveLines = PHASE2_WAVE_LINES;
        waveSpreadDeg = PHASE2_WAVE_SPREAD_DEG;
        waveVolleys = PHASE2_WAVE_VOLLEYS;
        waveSpeed = PHASE2_WAVE_SPEED;
        tailCooldownMs = 2000;   // หางฟาดครั้งแรกช้าหน่อยหลังเข้าเฟส 2
        applyBossSize();
        // หลัง layout ใหม่ ขยายออกรอบจุดกึ่งกลางเดิม แล้วคำนวณจุดพักใหม่ให้พอดีขนาดใหม่
        containerView.post(() -> {
            View area = (View) containerView.getParent();
            if (area == null) return;
            float w = containerView.getWidth(), h = containerView.getHeight();
            containerView.setX(Math.max(0f, Math.min(cx - w / 2f, area.getWidth() - w)));
            containerView.setY(Math.max(0f, Math.min(cy - h / 2f, area.getHeight() - h)));
            computeHome();
        });
    }

    // [loadFrames] วนโหลดรูป octo_idle_00 ถึง octo_idle_46 ด้วยชื่อ (getIdentifier) หยุดเมื่อหาไม่เจอ
    //   [เปลี่ยนแอนิเมชันบอส] ใส่รูปชื่อเดียวกันใน res/drawable-nodpi/ และปรับ FRAME_COUNT
    private void loadFrames() {
        applyBossSize();
        if (!(imgAvatar instanceof android.widget.ImageView)) return;
        android.content.Context c = ctx.getContext();
        for (int i = 0; i < FRAME_COUNT; i++) {
            int id = c.getResources().getIdentifier(
                    String.format(java.util.Locale.US, "octo_idle_%02d", i), "drawable", c.getPackageName());
            if (id == 0) break;
            frames[frameCount++] = androidx.core.content.ContextCompat.getDrawable(c, id);
        }
    }

    // [ชื่อบอส] "Kraken Boss ❤❤" จำนวนหัวใจ = ชีวิตที่เหลือ แสดงบน HUD
    private void refreshNameLabel() {
        if (hudName != null) hudName.setText(bossTitle());
        if (txtName == null) return;
        txtName.setText(bossTitle());
    }

    private String bossTitle() {
        StringBuilder sb = new StringBuilder("Kraken Boss ");
        for (int i = 0; i < lives; i++) sb.append("❤");
        return sb.toString();
    }

    // [HUD หลอดเลือดบอส] หา View จาก Activity (layoutBossBar, txtBossName, txtBossHp, barBossHp ใน activity_battle.xml)
    //   ซ่อนหลอด/ชื่อที่ติดตัวบอส แล้วโชว์ HUD บนจอแทน | syncHud อัปเดตหลอดและตัวเลข
    // ---------------------------------------------------------
    // หลอดเลือดบอสตรงกลางบนของจอ (อยู่ใน activity_battle.xml) แทนหลอดที่ติดตัวบอส
    // ---------------------------------------------------------
    private View hudRoot;
    private android.widget.TextView hudName;
    private android.widget.TextView hudHp;
    private android.widget.ProgressBar hudBar;

    private void setupBossHud() {
        // หลอด/ชื่อ/ตัวเลขที่ติดตัวบอสซ่อนไว้ (บอสตัวใหญ่ ข้อมูลไปอยู่บน HUD แทน)
        txtName.setVisibility(View.GONE);
        txtHp.setVisibility(View.GONE);
        barHp.setVisibility(View.GONE);

        if (!(ctx.getContext() instanceof android.app.Activity)) return;
        android.app.Activity a = (android.app.Activity) ctx.getContext();
        hudRoot = a.findViewById(R.id.layoutBossBar);
        hudName = a.findViewById(R.id.txtBossName);
        hudHp = a.findViewById(R.id.txtBossHp);
        hudBar = a.findViewById(R.id.barBossHp);
        if (hudRoot == null || hudBar == null) { hudRoot = null; return; }
        hudBar.setMax(maxHp);
        hudRoot.setVisibility(View.VISIBLE);
        syncHud();
    }

    private void syncHud() {
        if (hudRoot == null) return;
        hudBar.setProgress(hp);
        hudHp.setText(hp + "/" + maxHp);
    }

    // [takeDamage ของบอส] คูณดาเมจผู้เล่น 1.15 แล้วเก็บเศษ (damageCarry) ก่อนส่งให้ super.takeDamage แล้ว syncHud
    @Override
    public void takeDamage(int damage, boolean knockback) {
        // ผู้เล่นตีบอสแรงขึ้น +15% (ดาเมจเป็นจำนวนเต็มเล็กๆ จึงสะสมเศษไว้ ไม่งั้นปัดแล้วโบนัสหายไป)
        float total = damage * PLAYER_DAMAGE_MUL + damageCarry;
        int dealt = (int) total;
        damageCarry = total - dealt;
        super.takeDamage(dealt, knockback);
        syncHud();   // ครอบคลุมทั้งโดนดาเมจปกติและตอนฟื้นเลือดเต็มหลังเสียชีวิตแรก
    }

    // [takeFixedDamage] ลดเลือดตรง ๆ ไม่คิดโบนัส ใช้ตอนลูกน้องตาย (BossMinion.onDefeated เรียก)
    /** ลดเลือดบอสตามจำนวนที่กำหนดตรงๆ ไม่คิดโบนัส (ใช้ตอนลูกน้องตาย: ลด 10 พอดี) */
    public void takeFixedDamage(int damage) {
        super.takeDamage(damage, false);
        syncHud();
    }

    // [computeHome / placeAtHome] คำนวณและวางตำแหน่งจุดพัก (72% ของความกว้าง กลางแนวตั้ง) ต้องรอจอรู้ขนาดก่อน
    private void computeHome() {
        View area = (View) containerView.getParent();
        if (area == null || area.getWidth() <= 0) return;
        float w = containerView.getWidth(), h = containerView.getHeight();
        homeX = Math.max(0f, area.getWidth() * 0.72f - w / 2f);
        homeX = Math.min(homeX, Math.max(0f, area.getWidth() - w));
        homeY = Math.max(0f, (area.getHeight() - h) / 2f);
    }

    private void placeAtHome() {
        View area = (View) containerView.getParent();
        if (area == null || area.getWidth() <= 0) return;
        computeHome();
        containerView.setX(homeX);
        containerView.setY(homeY);
        homeSet = true;
    }

    // [updateAI = สมองบอส (override)] ต่างจากศัตรูทั่วไป: ไม่เดิน ไม่โดนสตัน
    //   ทุกเฟรม: คิด dt , นับคูลดาวน์ (เฟส 2 นับเร็วขึ้นตาม attackRate; หางฟาดนับปกติ) แล้วเลือกตามสถานะ:
    //    IDLE : ถ้าเฟส 2 และผู้เล่นอยู่ในรัศมีหางฟาดและหางพร้อม -> เตือนหางฟาด
    //           ไม่ใช่ ถ้าคลื่นพร้อม -> เตือนคลื่น (ล็อกทิศหาผู้เล่น)
    //           ไม่ใช่ ถ้าเฟส 2 และผู้เล่นไกล -> คืบเข้าหาช้า ๆ
    //    TAIL_WINDUP : ครบ 0.6 วินาที -> ถ้าผู้เล่นยังอยู่ในวงโดนดาเมจ+สโลว์ (หลบออกทัน = ไม่โดน) แล้วรอคูลดาวน์
    //    WAVE_WINDUP : ครบ 0.5 วินาที -> ยิงคลื่นทุกเส้นด้วย RangedAttacks.fireLine ถ้ายังไม่ครบจำนวนชุด เล็งใหม่ยิงอีก ไม่งั้นเริ่มคูลดาวน์
    //   แล้วเล่นแอนิเมชัน
    // ---------------------------------------------------------
    // AI
    // ---------------------------------------------------------
    @Override
    public void updateAI(float targetX, float targetY) {
        if (!isAlive || containerView == null || !ctx.isGameRunning() || ctx.isGamePaused()) return;
        View area = (View) containerView.getParent();
        if (area == null || area.getWidth() <= 0) return;

        long now = System.currentTimeMillis();
        if (lastUpdateMs == 0) lastUpdateMs = now;
        long dtMs = Math.min(now - lastUpdateMs, 100);
        lastUpdateMs = now;
        float dt = dtMs / 1000f;

        if (!homeSet) {
            placeAtHome();
            if (!homeSet) return;
        }

        phaseMs += dtMs;
        long atkMs = Math.round(dtMs * attackRate);   // เฟส 2 คูลดาวน์ลดเร็วขึ้น = โจมตีถี่ขึ้น
        waveCooldownMs -= atkMs;
        tailCooldownMs -= dtMs;   // หางฟาด: คูลดาวน์ 5 วินาทีตายตัว ไม่เร่งตามเฟส

        View pv = ctx.getPlayerContainer();
        float pcx = targetX + (pv != null ? pv.getWidth() / 2f : 0f);
        float pcy = targetY + (pv != null ? pv.getHeight() / 2f : 0f);
        float bw = containerView.getWidth(), bh = containerView.getHeight();
        float bcx = containerView.getX() + bw / 2f;
        float bcy = containerView.getY() + bh / 2f;

        switch (phase) {
            case IDLE: {
                float tailRadius = tailRadius();
                float dist = (float) Math.hypot(pcx - bcx, pcy - bcy);
                if (phase2 && tailCooldownMs <= 0 && dist <= tailRadius) {
                    enterPhase(Phase.TAIL_WINDUP);
                    showTailTelegraph(bcx, bcy, tailRadius);
                } else if (waveCooldownMs <= 0) {
                    enterPhase(Phase.WAVE_WINDUP);
                    waveVolley = 0;
                    showWaveTelegraphs(bcx, bcy, pcx, pcy, area);
                } else if (phase2 && dist > tailRadius * 0.8f) {
                    // เฟส 2: คืบเข้าหาผู้เล่นช้าๆ (เฉพาะตอนไม่ได้เตรียมโจมตี)
                    moveToward(pcx - bw / 2f, pcy - bh / 2f, PHASE2_MOVE_SPEED * dt, area);
                }
                break;
            }
            case TAIL_WINDUP: {
                if (phaseMs >= TAIL_WINDUP_MS) {
                    removeTailTelegraph();
                    if (Math.hypot(pcx - bcx, pcy - bcy) <= tailRadius()) {   // หลบออกนอกวงทันก็ไม่โดน
                        ctx.damagePlayer(TAIL_DAMAGE);
                        ctx.slowPlayer(HIT_SLOW_FACTOR, HIT_SLOW_MS);
                        HitEffects.playerHit(ctx, ctx.getPlayerContainer(), TAIL_DAMAGE);
                    }
                    tailCooldownMs = TAIL_COOLDOWN_MS;
                    enterPhase(Phase.IDLE);
                }
                break;
            }
            case WAVE_WINDUP: {
                if (phaseMs >= WAVE_WINDUP_MS) {
                    releaseWaveTelegraphs();
                    for (int i = 0; i < waveLines; i++) {
                        RangedAttacks.fireLine(ctx, bcx, bcy, waveDirX[i], waveDirY[i], waveSpeed, scaled(WAVE_DAMAGE),
                                () -> ctx.slowPlayer(HIT_SLOW_FACTOR, HIT_SLOW_MS));
                    }
                    if (++waveVolley < waveVolleys) {
                        // ชุดถัดไป: เล็งผู้เล่นใหม่ เตือนอีกครั้ง แล้วยิงอีกใน 0.5 วิ
                        phaseMs = 0;
                        showWaveTelegraphs(bcx, bcy, pcx, pcy, area);
                    } else {
                        waveCooldownMs = WAVE_COOLDOWN_MS;
                        enterPhase(Phase.IDLE);
                    }
                }
                break;
            }
        }

        animate(dt);
    }

    // [tailRadius] รัศมีหางฟาด = ครึ่งหนึ่งของด้านใหญ่สุดของบอส + TAIL_EXTRA_RADIUS
    private float tailRadius() {
        return Math.max(containerView.getWidth(), containerView.getHeight()) / 2f + TAIL_EXTRA_RADIUS;
    }

    // [moveToward] ขยับบอสเข้าหาจุดหมายไม่เกิน step px ต่อเฟรม และจำกัดไม่ให้หลุดจอ (ใช้ตอนคืบในเฟส 2)
    /** ขยับตำแหน่งมุมซ้ายบนของบอสไปหาจุดหมายไม่เกิน step px แล้วจำกัดไม่ให้หลุดจอ */
    private void moveToward(float tx, float ty, float step, View area) {
        float cx = containerView.getX(), cy = containerView.getY();
        float dx = tx - cx, dy = ty - cy;
        float d = (float) Math.hypot(dx, dy);
        if (d < 0.5f) return;
        float k = Math.min(step, d) / d;
        float nx = cx + dx * k, ny = cy + dy * k;
        containerView.setX(Math.max(0f, Math.min(area.getWidth() - containerView.getWidth(), nx)));
        containerView.setY(Math.max(0f, Math.min(area.getHeight() - containerView.getHeight(), ny)));
    }

    // [วงเตือนหางฟาด] วงกลมสีแดงโปร่งใสรอบบอส ค่อยทึบขึ้นตามเวลาเตือน เล่นเสียงเตือน (ผู้เล่นต้องวิ่งออกนอกวง)
    private void showTailTelegraph(float cx, float cy, float r) {
        FrameLayout fx = ctx.getGameArea();
        if (fx == null) return;
        removeTailTelegraph();
        View v = new View(ctx.getContext());
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x55FF1744);
        bg.setStroke(6, Color.parseColor("#FFFF8A80"));
        v.setBackground(bg);
        v.setLayoutParams(new FrameLayout.LayoutParams((int) (r * 2), (int) (r * 2)));
        v.setX(cx - r);
        v.setY(cy - r);
        v.setAlpha(0.25f);
        fx.addView(v);
        v.animate().alpha(1f).setDuration(TAIL_WINDUP_MS).start();
        tailTelegraph = v;
        SoundManager.play(SoundManager.Sfx.ENEMY_WARN);
    }

    // [ลบวงเตือน] ยกเลิกแอนิเมชันแล้วลบวงออกจากจอ
    private void removeTailTelegraph() {
        if (tailTelegraph == null) return;
        tailTelegraph.animate().cancel();
        if (tailTelegraph.getParent() instanceof FrameLayout) {
            ((FrameLayout) tailTelegraph.getParent()).removeView(tailTelegraph);
        }
        tailTelegraph = null;
    }

    // [scaled] ดาเมจ x damageMul ของด่าน อย่างน้อย 1
    private int scaled(int base) { return Math.max(1, Math.round(base * damageMul)); }

    // [enterPhase] เปลี่ยนสถานะและรีเซ็ตตัวนับเวลา
    private void enterPhase(Phase next) {
        phase = next;
        phaseMs = 0;
    }

    // [เส้นเตือนคลื่นพลัง] showWaveTelegraphs : คำนวณมุมกลางไปทางผู้เล่น แล้วแผ่เป็นพัด (waveLines เส้น ห่างกัน waveSpreadDeg) เก็บทิศเพื่อยิงจริงด้วยทิศเดียวกัน
    //   โชว์แถบเตือนแต่ละเส้นจากกองที่ใช้ซ้ำ (HitEffects.showAttackPath) | releaseWaveTelegraphs คืนกอง | cancelTelegraphs ล้างทั้งหมด
    // ---------------------------------------------------------
    // เส้นเตือน
    // ---------------------------------------------------------
    private void showWaveTelegraphs(float bcx, float bcy, float pcx, float pcy, View area) {
        FrameLayout fx = ctx.getGameArea();
        float base = (float) Math.atan2(pcy - bcy, pcx - bcx);
        float reach = (float) Math.hypot(Math.max(area.getWidth(), 1), Math.max(area.getHeight(), 1));
        for (int i = 0; i < waveLines; i++) {
            float a = base + (float) Math.toRadians((i - waveLines / 2) * waveSpreadDeg);
            waveDirX[i] = (float) Math.cos(a);
            waveDirY[i] = (float) Math.sin(a);
            if (fx != null) {
                waveTelegraphs[i] = HitEffects.showAttackPath(fx, bcx, bcy,
                        bcx + waveDirX[i] * reach, bcy + waveDirY[i] * reach,
                        RangedAttacks.LINE_BAND_WIDTH, WAVE_WINDUP_MS);
            }
        }
        SoundManager.play(SoundManager.Sfx.ENEMY_WARN);
    }

    private void releaseWaveTelegraphs() {
        for (int i = 0; i < waveTelegraphs.length; i++) {
            if (waveTelegraphs[i] != null) {
                HitEffects.releaseAttackPath(waveTelegraphs[i]);
                waveTelegraphs[i] = null;
            }
        }
    }

    private void cancelTelegraphs() {
        removeTailTelegraph();
        releaseWaveTelegraphs();
    }

    // [animate] เปลี่ยนเฟรมภาพตามเวลา (เร็วขึ้น 1.6 เท่าตอนเตรียมยิง) + ส่าย/บีบตัวเบา ๆ (หายใจ) และสั่นแรงตอนเตรียมยิงคลื่น
    // ---------------------------------------------------------
    // แอนิเมชัน: หายใจตอนนิ่ง / สั่นตอนเตรียมพุ่ง / พองตอนเตรียมฟาด
    // ---------------------------------------------------------
    private void animate(float dt) {
        animTime += dt;
        if (frameCount > 0) {
            // ตอนเตรียมยิงคลื่นเล่นเฟรมเร็วขึ้น ให้ดูดุดัน
            float fps = phase == Phase.WAVE_WINDUP ? FRAME_FPS * 1.6f : FRAME_FPS;
            frameTime += dt * fps;
            int idx = (int) frameTime % frameCount;
            if (idx != shownFrame) {
                shownFrame = idx;
                ((android.widget.ImageView) imgAvatar).setImageDrawable(frames[idx]);
            }
        }
        float wave = (float) Math.sin(animTime * 2.5f);
        float rot = (float) Math.sin(animTime * 1.5f) * 4f;
        float sx = 1f + wave * 0.03f, sy = 1f - wave * 0.03f;
        if (phase == Phase.WAVE_WINDUP) rot = (float) Math.sin(animTime * 40f) * 4f;   // สั่นตอนเตรียมยิง
        imgAvatar.setRotation(rot);
        imgAvatar.setScaleX(sx);
        imgAvatar.setScaleY(sy);
    }
}
