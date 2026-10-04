package com.example.finfury;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Mega Boss ด่าน 5: Kraken ตัวใหญ่ 4 เท่า 2 ชีวิต (ชีวิตแรกเลือด 80, เฟส 2 เลือด 30)
 * - ปกติยืนนิ่งอยู่กลางจอด้านขวา: ปล่อยคลื่นพลังเป็นชุด (ไม่มีการโจมตีระยะประชิดแล้ว)
 * - บอสไม่เคลื่อนที่ ยืนอยู่กับที่ตลอด แต่โจมตีถี่
 * - ไม่โดนสตัน/สโลว์/กลืน/ผลักถอย
 */
public class KrakenBoss extends SeaEnemy {

    private static final int BOSS_HP = 100;
    private static final int MAX_LIVES = 2;
    private static final int PHASE2_HP = 30;

    private static final long WAVE_WINDUP_MS = 500;
    private static final long WAVE_COOLDOWN_MS = 5000;

    private static final int WAVE_DAMAGE = 5;

    private static final float WAVE_SPREAD_DEG = 14f;
    private static final float WAVE_SPEED = 1700f;
    private static final float HIT_SLOW_FACTOR = 0.5f;   // โดนบอสตี = เคลื่อนที่เหลือ 50%
    private static final long HIT_SLOW_MS = 3000;
    private static final float PHASE2_SIZE_MUL = 2f;     // เฟส 2 ตัวใหญ่ขึ้น 2 เท่า
    private static final float PHASE2_MOVE_SPEED = 90f;  // เฟส 2 เคลื่อนที่เข้าหาผู้เล่นช้าๆ (px/s)
    // เฟส 2: ยิงคลื่นหลบยากขึ้น (เส้นมากขึ้น/แน่นขึ้น/ชุดมากขึ้น/เร็วขึ้น)
    private static final int PHASE2_WAVE_LINES = 7;
    private static final float PHASE2_WAVE_SPREAD_DEG = 10f;
    private static final int PHASE2_WAVE_VOLLEYS = 3;
    private static final float PHASE2_WAVE_SPEED = 2200f;
    // หางฟาด (เฟส 2): ระยะประชิด ดาเมจ 15 คูลดาวน์ 5 วินาที มีวงเตือนก่อนฟาด
    private static final int TAIL_DAMAGE = 15;
    private static final long TAIL_COOLDOWN_MS = 5000;
    private static final long TAIL_WINDUP_MS = 600;
    private static final float TAIL_EXTRA_RADIUS = 120f;   // วงฟาดกว้างกว่าตัวบอสออกไปเท่านี้
    private static final float PHASE2_RATE_MUL = 1.5f;   // เฟส 2 โจมตีถี่ขึ้น 1.5 เท่า
    private static final float BOSS_SCALE = 8f;          // ลดลงครึ่งหนึ่งจาก 16 (เฟส 2 ยังขยาย 1.8 เท่าจากค่านี้)
    private static final float FRAME_ASPECT = 170f / 256f;
    private static final int WAVE_VOLLEYS = 2;          // ปล่อยคลื่นพลัง 2 ชุดต่อรอบ ห่างกัน WAVE_WINDUP_MS

    private enum Phase { IDLE, WAVE_WINDUP, TAIL_WINDUP }

    private int lives = MAX_LIVES;
    private Phase phase = Phase.IDLE;
    private long lastUpdateMs = 0;
    private long phaseMs = 0;
    private long waveCooldownMs = 2000;

    private float homeX, homeY;
    private boolean homeSet = false;

    private int waveVolley = 0;
    private float attackRate = 1f;
    private float sizeMul = 1f;

    private static final float PLAYER_DAMAGE_MUL = 1.15f;   // ดาเมจที่ผู้เล่นทำใส่บอส +15%
    private float damageCarry = 0f;
    private boolean phase2 = false;
    private int waveLines = 5;
    private float waveSpreadDeg = WAVE_SPREAD_DEG;
    private int waveVolleys = WAVE_VOLLEYS;
    private float waveSpeed = WAVE_SPEED;
    private long tailCooldownMs = 0;
    private View tailTelegraph;

    private final View[] waveTelegraphs = new View[PHASE2_WAVE_LINES];
    private final float[] waveDirX = new float[PHASE2_WAVE_LINES];
    private final float[] waveDirY = new float[PHASE2_WAVE_LINES];

    private float animTime = 0f;
    private final float damageMul;

    // เฟรมแอนิเมชัน octo_idle_00..46 โหลดครั้งเดียวไว้ในหน่วยความจำ (ไม่ decode ซ้ำทุกเฟรม)
    private static final int FRAME_COUNT = 47;
    private static final float FRAME_FPS = 24f;
    private final android.graphics.drawable.Drawable[] frames = new android.graphics.drawable.Drawable[FRAME_COUNT];
    private int frameCount = 0;
    private float frameTime = 0f;
    private int shownFrame = -1;

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

    // ---------------------------------------------------------
    // ตั้งค่าเฉพาะบอส (ถูกเรียกจาก constructor ของ SeaEnemy: แตะได้เฉพาะฟิลด์ของ SeaEnemy)
    // ---------------------------------------------------------
    @Override
    protected void configureEnemyStats() {
        maxHp = BOSS_HP;
        hp = BOSS_HP;
    }

    @Override
    protected float visualScale() { return BOSS_SCALE; }

    @Override
    protected int getDrawableResId() { return R.drawable.boss_image; }

    @Override
    protected boolean allowKnockback() { return false; }

    @Override
    public int livesLeft() { return lives; }

    @Override
    public void stun(long durationMs) { /* บอสไม่โดนสตัน */ }

    @Override
    public void applySlow(float factor, long durationMs) { /* บอสไม่โดนสโลว์ */ }

    @Override
    public void setSwallowed(boolean value) { /* บอสตัวใหญ่เกินกว่าจะกลืน */ }

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

    @Override
    public void takeDamage(int damage, boolean knockback) {
        // ผู้เล่นตีบอสแรงขึ้น +15% (ดาเมจเป็นจำนวนเต็มเล็กๆ จึงสะสมเศษไว้ ไม่งั้นปัดแล้วโบนัสหายไป)
        float total = damage * PLAYER_DAMAGE_MUL + damageCarry;
        int dealt = (int) total;
        damageCarry = total - dealt;
        super.takeDamage(dealt, knockback);
        syncHud();   // ครอบคลุมทั้งโดนดาเมจปกติและตอนฟื้นเลือดเต็มหลังเสียชีวิตแรก
    }

    /** ลดเลือดบอสตามจำนวนที่กำหนดตรงๆ ไม่คิดโบนัส (ใช้ตอนลูกน้องตาย: ลด 10 พอดี) */
    public void takeFixedDamage(int damage) {
        super.takeDamage(damage, false);
        syncHud();
    }

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

    private float tailRadius() {
        return Math.max(containerView.getWidth(), containerView.getHeight()) / 2f + TAIL_EXTRA_RADIUS;
    }

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

    private void removeTailTelegraph() {
        if (tailTelegraph == null) return;
        tailTelegraph.animate().cancel();
        if (tailTelegraph.getParent() instanceof FrameLayout) {
            ((FrameLayout) tailTelegraph.getParent()).removeView(tailTelegraph);
        }
        tailTelegraph = null;
    }

    private int scaled(int base) { return Math.max(1, Math.round(base * damageMul)); }

    private void enterPhase(Phase next) {
        phase = next;
        phaseMs = 0;
    }

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
