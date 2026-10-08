// =====================================================================================
// [คนที่ 5 - ข้อมูล ควิซ ไอเทม และสมดุลเกม]  ไฟล์: ItemManager.java  (360 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/ItemManager.java
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   ระบบ "ไอเทมบนแมพ" : ศัตรูตายมีโอกาส 25% ดรอป + สุ่มเกิดเองทุก 12-15 วินาที (สูงสุด 2 ชิ้นจากการสุ่ม รวมทั้งแมพไม่เกิน 5 ชิ้น)
//   ผู้เล่นว่ายไปชนเพื่อเก็บ ไอเทมลอยค้าง 8 วินาที (กะพริบ 2 วินาทีสุดท้าย) เกมหยุด/ขึ้นควิซ เวลาหยุดด้วย (เพราะ update ถูกเรียกเฉพาะตอนเกมเดิน)
//   7 ชนิด (enum Type) : ❤️ หัวใจ ฟื้นเลือด 15% | ⚡ พลังงาน สแตก +2 | 🛡️ โล่ กันดาเมจ 1 ครั้ง 10 วินาที | 💨 เร็ว x1.3 6 วินาที
//                        ⏱️ คูลดาวน์ลดครึ่ง 8 วินาที | 🔥 ดาเมจ +1 ต่อฮิต 8 วินาที | 🧊 แช่แข็งศัตรู ช้าลง 50% 4 วินาที
//   ผลที่ต้องไปทำกับตัวผู้เล่น ถูกสั่งผ่าน interface Host (BattleActivity implement) ส่วนบัฟที่มีเวลาเก็บสถานะไว้ในคลาสนี้ (shieldMs, speedMs ...)
//
// [ใครเรียก] BattleActivity: สร้างหลัง layout เสร็จ , update(dt) ทุกเฟรม , onEnemyDefeated (ผ่าน dropItemAt) ,
//            อ่าน speedFactor/cooldownFactor/damageBonus/absorbHit/buffText , clear() ตอนด่านจบ
//
// [จะเพิ่มไอเทมชนิดใหม่ยังไง] (เช็กลิสต์)
//   1) เพิ่มค่าใน enum Type : NAME("อีโมจิ", "#สี", น้ำหนักความหายาก, "ชื่อไทย", "คำอธิบาย")  (น้ำหนักมาก = ออกบ่อย)
//   2) เพิ่มค่าคงที่ของผล เช่น MAGNET_MS
//   3) เพิ่ม case ใน apply(): ทำผล + popup(...)  (ถ้าเป็นบัฟมีเวลา เพิ่มตัวแปร xxxMs ใน tickBuffs/clear ด้วย)
//   4) คู่มือในเมนูหยุดจะแสดงอัตโนมัติ (BattleActivity.buildItemGuide วนจาก Type.values())
// [จะปรับสมดุล] DROP_CHANCE โอกาสดรอป | น้ำหนักใน enum | ระยะเวลา/ค่า *_MS *_FACTOR | LIFETIME_MS อายุไอเทม
// =====================================================================================
package com.example.finfury;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

// [คอมเมนต์คลาส - โดยเจ้าของไฟล์เดิม]
/**
 * ไอเทมบนแมพ: ศัตรูตายมีโอกาสดรอป + สุ่มเกิดเองทุก 12-15 วินาที (สูงสุด 2 ชิ้น)
 * ว่ายไปชนเพื่อเก็บ ลอยค้าง 8 วินาที (กะพริบ 2 วินาทีสุดท้าย) ถ้าเกมหยุด/ขึ้นควิซ เวลาหยุดด้วย
 * ผลของไอเทมถูกสั่งผ่าน {@link Host} ส่วนบัฟที่มีเวลาเก็บสถานะไว้ที่นี่
 */
// [ส่วนหัวคลาส]
public class ItemManager {

    // [enum Type = รายการชนิดไอเทม] แต่ละค่ามี: icon (อีโมจิ) , color (สีวงกลมขอบ) , weight (น้ำหนักความน่าจะเป็นตอนสุ่ม) , nameTh/descTh (ข้อความไทย)
    //   คำอธิบายสร้างจากค่าคงที่สมดุลด้านล่าง ตัวเลขในคำอธิบายจึงตรงกับค่าจริงเสมอ (แก้ค่าที่เดียว)
    //   น้ำหนักรวม 100 : หัวใจ 25 พลังงาน 25 โล่ 15 เร็ว 12 ลดคูลดาวน์ 12 ดาเมจ 6 แช่แข็ง 5
    public enum Type {
        HEART("❤️", "#E53935", 25, "หัวใจ",
                "ฟื้นเลือด " + Math.round(HEAL_PERCENT * 100) + "%"),
        ENERGY("⚡", "#FBC02D", 25, "พลังงาน",
                "เพิ่มสแตก +" + ENERGY_AMOUNT),
        SHIELD("🛡️", "#1E88E5", 15, "โล่",
                "กันดาเมจ 1 ครั้ง (นาน " + secText(SHIELD_MS) + " วินาที)"),
        SPEED("💨", "#26C6DA", 12, "ว่ายเร็ว",
                "ความเร็ว x" + String.format(Locale.US, "%.1f", SPEED_FACTOR) + " นาน " + secText(SPEED_MS) + " วินาที"),
        COOLDOWN("⏱️", "#8E24AA", 12, "ลดคูลดาวน์",
                "สกิลฟื้นเร็วขึ้น " + String.format(Locale.US, "%.0f", 1f / COOLDOWN_FACTOR) + " เท่า นาน " + secText(COOLDOWN_MS) + " วินาที"),
        DAMAGE("🔥", "#FB8C00", 6, "ดาเมจเพิ่ม",
                "+" + DAMAGE_BONUS + " ดาเมจต่อฮิต นาน " + secText(DAMAGE_MS) + " วินาที"),
        FREEZE("🧊", "#4FC3F7", 5, "แช่แข็ง",
                "ศัตรูช้าลง " + Math.round((1f - FREEZE_FACTOR) * 100) + "% นาน " + secText(FREEZE_MS) + " วินาที");

        final String icon;
        final int color;
        final int weight;
        final String nameTh;
        final String descTh;
        Type(String icon, String colorHex, int weight, String nameTh, String descTh) {
            this.icon = icon;
            this.color = Color.parseColor(colorHex);
            this.weight = weight;
            this.nameTh = nameTh;
            this.descTh = descTh;
        }

        // [label] ข้อความรวมสำหรับแสดง เช่น "🛡️ โล่: กันดาเมจ 1 ครั้ง (นาน 10 วินาที)" (ใช้ในคู่มือและ popup)
        /** เช่น "🛡️ โล่: กันดาเมจ 1 ครั้ง (นาน 10 วินาที)" */
        public String label() {
            return icon + " " + nameTh + ": " + descTh;
        }
    }

    // [secText] แปลงมิลลิวินาทีเป็นข้อความวินาที
    private static String secText(long ms) {
        return String.valueOf(ms / 1000);
    }

    // [interface Host] สัญญาที่ BattleActivity ต้องทำให้ : ถามเลือด (getPlayerHp/MaxHp) , ฟื้นเลือด (healPlayer) , เพิ่มสแตก (addStack) , แช่แข็งศัตรู (freezeEnemies)
    //   [เพิ่มผลไอเทมที่ต้องแตะระบบอื่น] เพิ่มเมธอดที่นี่ แล้ว implement ใน BattleActivity
    /** สิ่งที่ไอเทมสั่งให้ฉากต่อสู้ทำ */
    public interface Host {
        int getPlayerHp();
        int getPlayerMaxHp();
        void healPlayer(int amount);
        void addStack(int amount);
        void freezeEnemies(float factor, long durationMs);
    }

    // [ค่าสมดุล - แก้ได้เลย]
    //   DROP_CHANCE โอกาสดรอปจากศัตรู (0.25 = 25%) | LIFETIME_MS อายุไอเทม | BLINK_MS ช่วงกะพริบก่อนหาย
    //   SPAWN_MIN_MS / SPAWN_MAX_MS ช่วงเวลาสุ่มเกิดเอง | MAX_RANDOM_ON_MAP, MAX_TOTAL_ON_MAP จำนวนสูงสุด | PICKUP_RADIUS_DP ระยะเก็บ
    //   HEAL_PERCENT, ENERGY_AMOUNT, SHIELD_MS, SPEED_MS/FACTOR, COOLDOWN_MS/FACTOR, DAMAGE_MS/BONUS, FREEZE_MS/FACTOR = ผลและเวลาของแต่ละชนิด
    // ค่าสมดุล
    private static final float DROP_CHANCE = 0.25f;
    private static final long LIFETIME_MS = 8000;
    private static final long BLINK_MS = 2000;
    private static final long SPAWN_MIN_MS = 12000;
    private static final long SPAWN_MAX_MS = 15000;
    private static final int MAX_RANDOM_ON_MAP = 2;
    private static final int MAX_TOTAL_ON_MAP = 5;
    private static final float PICKUP_RADIUS_DP = 36f;
    private static final float HEAL_PERCENT = 0.15f;
    private static final int ENERGY_AMOUNT = 2;
    private static final long SHIELD_MS = 10000;
    private static final long SPEED_MS = 6000;
    public static final float SPEED_FACTOR = 1.3f;
    private static final long COOLDOWN_MS = 8000;
    public static final float COOLDOWN_FACTOR = 0.5f;
    private static final long DAMAGE_MS = 8000;
    public static final int DAMAGE_BONUS = 1;
    private static final long FREEZE_MS = 4000;
    private static final float FREEZE_FACTOR = 0.5f;

    // [Item = ไอเทม 1 ชิ้นที่อยู่บนแมพ] ชนิด , View (วงกลมอีโมจิ) , มาจากการสุ่มเกิดเองไหม (fromRandomSpawn) , เฟสการลอยขึ้นลง , อายุ
    private static final class Item {
        final Type type;
        final TextView view;
        final boolean fromRandomSpawn;
        final float bobPhase;
        float ageMs = 0f;
        Item(Type type, TextView view, boolean fromRandomSpawn, float bobPhase) {
            this.type = type;
            this.view = view;
            this.fromRandomSpawn = fromRandomSpawn;
            this.bobPhase = bobPhase;
        }
    }

    // [ตัวแปร] context, area (พื้นที่เล่น), player (ตัวผู้เล่น), host (ตัวสั่งผล) , randomSpawnEnabled , rng , items = ไอเทมที่อยู่บนแมพ ,
    //   nextSpawnMs = ตัวนับถอยหลังจนสุ่มเกิดครั้งถัดไป , timeMs = เวลารวม (ไว้ทำลอยขึ้นลง)
    private final Context context;
    private final FrameLayout area;
    private final View player;
    private final Host host;
    private final boolean randomSpawnEnabled;
    private final Random rng = new Random();
    private final List<Item> items = new ArrayList<>();

    private float nextSpawnMs;
    private float timeMs = 0f;

    // [สถานะบัฟ] shieldMs, speedMs, cooldownMs, damageMs = เวลาที่เหลือ (มิลลิวินาที) 0 = ไม่มีบัฟ | lastBuffKey = ไว้เช็กว่าข้อความ HUD ต้องเปลี่ยนไหม
    // บัฟที่มีเวลา (มิลลิวินาทีที่เหลือ)
    private float shieldMs, speedMs, cooldownMs, damageMs;
    private int lastBuffKey = -1;

    // [constructor] รับของที่ต้องใช้ แล้วสุ่มเวลาเกิดเองครั้งแรก 12-15 วินาที
    public ItemManager(Context context, FrameLayout area, View player, Host host, boolean randomSpawnEnabled) {
        this.context = context;
        this.area = area;
        this.player = player;
        this.host = host;
        this.randomSpawnEnabled = randomSpawnEnabled;
        this.nextSpawnMs = SPAWN_MIN_MS + rng.nextInt((int) (SPAWN_MAX_MS - SPAWN_MIN_MS));
    }

    // [getter ตัวคูณ] speedFactor() = 1.3 ถ้ามีบัฟเร็ว ไม่ใช่ 1 | cooldownFactor() = 0.5 หรือ 1 | damageBonus() = +1 หรือ 0 (BattleActivity อ่านทุกเฟรมแล้วเอาไปใช้)
    // ---------- สถานะบัฟที่ฉากต่อสู้อ่านไปใช้ ----------
    public float speedFactor() { return speedMs > 0f ? SPEED_FACTOR : 1f; }
    public float cooldownFactor() { return cooldownMs > 0f ? COOLDOWN_FACTOR : 1f; }
    public int damageBonus() { return damageMs > 0f ? DAMAGE_BONUS : 0; }

    // [absorbHit] ผู้เล่นโดนตี: ถ้ามีโล่ ใช้โล่ทิ้ง (shieldMs = 0) โชว์ "กันได้!" และคืน true (BattleActivity.damagePlayer จะไม่หักเลือด)
    /** โล่กันดาเมจ: คืน true ถ้ากันได้ (ใช้โล่หมดไป 1 ชิ้น) */
    public boolean absorbHit() {
        if (shieldMs <= 0f) return false;
        shieldMs = 0f;
        popup("🛡️ กันได้!", Color.parseColor("#64B5F6"));
        return true;
    }

    // [ส่วนดรอป/สุ่ม]
    // ---------- ดรอป / สุ่มเกิด ----------
    // [onEnemyDefeated] ศัตรูตาย: ถ้า forceHeart (บอสเสียชีวิต) ดรอปหัวใจแน่นอน ไม่งั้นสุ่ม 25% แล้วสุ่มชนิดดรอป
    /** ศัตรูตาย: โอกาส 25% (forceHeart = ดรอปหัวใจแน่นอน เช่น บอสเสียชีวิต) */
    public void onEnemyDefeated(float cx, float cy, boolean forceHeart) {
        if (forceHeart) {
            spawn(Type.HEART, cx, cy, false);
        } else if (rng.nextFloat() < DROP_CHANCE) {
            spawn(randomType(), cx, cy, false);
        }
    }

    // [randomType] สุ่มแบบถ่วงน้ำหนัก : รวมน้ำหนักทั้งหมด สุ่มเลข 0 ถึงน้ำหนักรวม ลบน้ำหนักทีละชนิดจนติดลบ ชนิดนั้นคือผลลัพธ์
    private Type randomType() {
        int total = 0;
        for (Type t : Type.values()) total += t.weight;
        int roll = rng.nextInt(total);
        for (Type t : Type.values()) {
            roll -= t.weight;
            if (roll < 0) return t;
        }
        return Type.HEART;
    }

    // [spawn] สร้างไอเทมที่จุด (cx, cy): ไม่สร้างถ้าเต็ม MAX_TOTAL_ON_MAP, วงกลมโปร่งสีดำขอบสีตามชนิด ใส่อีโมจิ, บีบให้อยู่ในจอ, ขยายเข้ามาจาก 0 ใน 200 ms
    private void spawn(Type type, float cx, float cy, boolean fromRandomSpawn) {
        if (area == null || items.size() >= MAX_TOTAL_ON_MAP) return;
        float size = dp(46);

        TextView tv = new TextView(context);
        tv.setText(type.icon);
        tv.setTextSize(24f);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(150, 0, 0, 0));
        bg.setStroke((int) dp(3), type.color);
        tv.setBackground(bg);
        tv.setLayoutParams(new FrameLayout.LayoutParams((int) size, (int) size));

        float x = clamp(cx - size / 2f, 0f, Math.max(0f, area.getWidth() - size));
        float y = clamp(cy - size / 2f, 0f, Math.max(0f, area.getHeight() - size));
        tv.setX(x);
        tv.setY(y);
        tv.setScaleX(0f);
        tv.setScaleY(0f);
        area.addView(tv);
        tv.animate().scaleX(1f).scaleY(1f).setDuration(200).start();

        items.add(new Item(type, tv, fromRandomSpawn, rng.nextFloat() * 6.28f));
    }

    // [randomOnMap] นับไอเทมที่เกิดจากการสุ่มเอง (ใช้จำกัดไม่เกิน 2 ชิ้น)
    private int randomOnMap() {
        int n = 0;
        for (int i = 0; i < items.size(); i++) if (items.get(i).fromRandomSpawn) n++;
        return n;
    }

    // [update(dtSec)] เรียกทุกเฟรมเฉพาะตอนเกมเดิน (ถ้าเกมหยุด ไม่เรียก เวลาจึงหยุด) :
    //   1) นับเวลาบัฟลง 2) สุ่มเกิดเองเมื่อถึงเวลา (ตำแหน่งสุ่มเว้นขอบ 60dp) 3) วนทุกไอเทม: เพิ่มอายุ หมดอายุ = ลบ
    //   4) ลอยขึ้นลงด้วยไซน์ + กะพริบตอนใกล้หมดเวลา 5) ถ้าผู้เล่นอยู่ในระยะเก็บ และเก็บได้ -> apply แล้วลบ
    // ---------- เรียกทุกเฟรมตอนเกมเดินอยู่ (dtSec = วินาที) ----------
    public void update(float dtSec) {
        float dtMs = dtSec * 1000f;
        timeMs += dtMs;
        tickBuffs(dtMs);

        if (randomSpawnEnabled && area != null && area.getWidth() > 0) {
            nextSpawnMs -= dtMs;
            if (nextSpawnMs <= 0f) {
                nextSpawnMs = SPAWN_MIN_MS + rng.nextInt((int) (SPAWN_MAX_MS - SPAWN_MIN_MS));
                if (randomOnMap() < MAX_RANDOM_ON_MAP) {
                    float margin = dp(60);
                    float x = margin + rng.nextFloat() * Math.max(1f, area.getWidth() - margin * 2);
                    float y = margin + rng.nextFloat() * Math.max(1f, area.getHeight() - margin * 2);
                    spawn(randomType(), x, y, true);
                }
            }
        }

        float pcx = player.getX() + player.getWidth() / 2f;
        float pcy = player.getY() + player.getHeight() / 2f;
        float pickup = dp(PICKUP_RADIUS_DP) + Math.min(player.getWidth(), player.getHeight()) / 2f;

        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            it.ageMs += dtMs;
            if (it.ageMs >= LIFETIME_MS) {
                remove(i);
                continue;
            }
            // ลอยขึ้นลงเบาๆ + กะพริบตอนใกล้หมดเวลา
            it.view.setTranslationY((float) Math.sin(timeMs / 350f + it.bobPhase) * dp(4));
            long left = (long) (LIFETIME_MS - it.ageMs);
            it.view.setAlpha(left < BLINK_MS && (left / 150) % 2 == 0 ? 0.3f : 1f);

            float icx = it.view.getX() + it.view.getWidth() / 2f;
            float icy = it.view.getY() + it.view.getHeight() / 2f;
            if (Math.hypot(pcx - icx, pcy - icy) <= pickup && canPickup(it.type)) {
                apply(it.type);
                remove(i);
            }
        }
    }

    // [canPickup] หัวใจตอนเลือดเต็มไม่เก็บ (ปล่อยไว้ให้เก็บทีหลัง) ชนิดอื่นเก็บได้เสมอ
    private boolean canPickup(Type type) {
        // หัวใจตอนเลือดเต็มไม่เก็บ ปล่อยไว้ให้เก็บทีหลัง
        return type != Type.HEART || host.getPlayerHp() < host.getPlayerMaxHp();
    }

    // [apply] เล่นเสียง ULT_READY แล้วทำผลตามชนิด + popup ข้อความสีเฉพาะ:
    //   HEART ฟื้นเลือด 15% ของสูงสุด (อย่างน้อย 1) | ENERGY addStack | SHIELD/SPEED/COOLDOWN/DAMAGE ตั้งเวลาบัฟ | FREEZE host.freezeEnemies
    private void apply(Type type) {
        SoundManager.play(SoundManager.Sfx.ULT_READY);
        switch (type) {
            case HEART:
                int heal = Math.max(1, Math.round(host.getPlayerMaxHp() * HEAL_PERCENT));
                host.healPlayer(heal);
                popup(type.label(), Color.parseColor("#EF5350"));
                break;
            case ENERGY:
                host.addStack(ENERGY_AMOUNT);
                popup(type.label(), Color.parseColor("#FDD835"));
                break;
            case SHIELD:
                shieldMs = SHIELD_MS;
                popup(type.label(), Color.parseColor("#64B5F6"));
                break;
            case SPEED:
                speedMs = SPEED_MS;
                popup(type.label(), Color.parseColor("#4DD0E1"));
                break;
            case COOLDOWN:
                cooldownMs = COOLDOWN_MS;
                popup(type.label(), Color.parseColor("#BA68C8"));
                break;
            case DAMAGE:
                damageMs = DAMAGE_MS;
                popup(type.label(), Color.parseColor("#FFA726"));
                break;
            case FREEZE:
                host.freezeEnemies(FREEZE_FACTOR, FREEZE_MS);
                popup(type.label(), Color.parseColor("#81D4FA"));
                break;
        }
    }

    // [tickBuffs] ลดเวลาบัฟทุกตัวตามเวลาที่ผ่านไป ไม่ต่ำกว่า 0
    private void tickBuffs(float dtMs) {
        shieldMs = Math.max(0f, shieldMs - dtMs);
        speedMs = Math.max(0f, speedMs - dtMs);
        cooldownMs = Math.max(0f, cooldownMs - dtMs);
        damageMs = Math.max(0f, damageMs - dtMs);
    }

    // [buffText] ข้อความบัฟบน HUD เช่น "🛡️ 8s  💨 4s" (ว่าง = ไม่มีบัฟ)
    /** ข้อความสถานะบัฟสำหรับ HUD เช่น "🛡️ 8s  💨 4s" (ว่าง = ไม่มีบัฟ) */
    public String buffText() {
        StringBuilder sb = new StringBuilder();
        append(sb, "🛡️", shieldMs);
        append(sb, "💨", speedMs);
        append(sb, "⏱️", cooldownMs);
        append(sb, "🔥", damageMs);
        return sb.toString();
    }

    // [buffTextChanged] บีบเวลาบัฟแต่ละตัวเป็นวินาทีเต็ม (ตัวละ 6 บิต) รวมเป็นตัวเลขเดียว ถ้าไม่เปลี่ยนจากครั้งก่อนไม่ต้อง setText (กันสร้าง String ทุกเฟรม)
    /** เปลี่ยนเมื่อข้อความ HUD ควรอัปเดต (นับเป็นวินาทีเต็ม) กันไม่ให้ setText ทุกเฟรม */
    public boolean buffTextChanged() {
        int key = secs(shieldMs) | (secs(speedMs) << 6) | (secs(cooldownMs) << 12) | (secs(damageMs) << 18);
        if (key == lastBuffKey) return false;
        lastBuffKey = key;
        return true;
    }

    // [secs / append] ตัวช่วยแปลงมิลลิวินาทีเป็นวินาทีปัดขึ้น (สูงสุด 63) และต่อข้อความบัฟ
    private static int secs(float ms) { return ms <= 0f ? 0 : Math.min(63, (int) Math.ceil(ms / 1000f)); }

    private static void append(StringBuilder sb, String icon, float ms) {
        if (ms <= 0f) return;
        if (sb.length() > 0) sb.append("  ");
        sb.append(icon).append(' ').append(String.format(Locale.US, "%ds", secs(ms)));
    }

    // [popup] ข้อความลอยเหนือหัวผู้เล่น ลอยขึ้น 50dp และจางใน 1.5 วินาที แล้วลบ ข้อความยาวตัดบรรทัดและบีบให้อยู่ในจอ (วัดขนาดก่อนวาง)
    private void popup(String text, int color) {
        if (area == null) return;
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(16f);
        tv.setShadowLayer(4f, 0f, 0f, Color.BLACK);
        tv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        // ข้อความยาว: ตัดบรรทัดไม่ให้กว้างเกินพื้นที่เกม แล้วดันตำแหน่ง X ให้อยู่ในกรอบ
        float margin = dp(8);
        float maxW = Math.max(dp(120), area.getWidth() - margin * 2);
        tv.setMaxWidth((int) maxW);
        tv.setGravity(Gravity.CENTER);
        tv.measure(View.MeasureSpec.makeMeasureSpec((int) maxW, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED);
        float w = tv.getMeasuredWidth();
        float cx = player.getX() + player.getWidth() / 2f;
        tv.setX(clamp(cx - w / 2f, margin, Math.max(margin, area.getWidth() - w - margin)));
        tv.setY(Math.max(margin, player.getY() - dp(24)));
        area.addView(tv);
        tv.animate().translationYBy(-dp(50)).alpha(0f).setDuration(1500)
                .withEndAction(() -> {
                    if (tv.getParent() == area) area.removeView(tv);
                }).start();
    }

    // [remove] ลบไอเทมออกจากลิสต์และจอ
    private void remove(int index) {
        Item it = items.remove(index);
        it.view.animate().cancel();
        if (it.view.getParent() == area && area != null) area.removeView(it.view);
    }

    // [clear] ล้างไอเทมและบัฟทั้งหมด เรียกตอนด่านจบ
    /** ล้างไอเทมบนแมพทั้งหมด (จบด่าน/ปิดหน้าจอ) */
    public void clear() {
        for (int i = items.size() - 1; i >= 0; i--) remove(i);
        shieldMs = speedMs = cooldownMs = damageMs = 0f;
    }

    // [dp / clamp] ตัวช่วย: แปลง dp เป็นพิกเซล และบีบค่าให้อยู่ในช่วง
    private float dp(float v) {
        return v * context.getResources().getDisplayMetrics().density;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
