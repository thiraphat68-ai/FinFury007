// =====================================================================================
// [คนที่ 3 - View หน้าจอและการเปลี่ยนหน้า]  ไฟล์: SelectStageActivity.java  (168 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/SelectStageActivity.java
// หน้าตา (layout): app/src/main/res/layout/activity_select_stage.xml
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   หน้าเลือกด่าน "แบบแยก Activity" (เวอร์ชันเก่า) มีปุ่มด่าน 5 ปุ่ม และมีความสามารถพิเศษ: "ลากไอคอนด่านไปวางที่ไหนก็ได้"
//   ตำแหน่งที่วางจำไว้ข้ามการเปิดแอป (SharedPreferences ชื่อ StageIconPositions) แตะเฉย ๆ = เข้าด่าน
//
// [สำคัญ - ข้อควรระวัง]
//   ไฟล์นี้ลงทะเบียนใน AndroidManifest.xml แล้ว แต่ "ไม่มีโค้ดส่วนไหนเปิดหน้านี้เลย"
//   (ค้นหา startActivity ... SelectStageActivity ไม่เจอ) เพราะหน้าเลือกด่านที่ใช้จริงถูกรวมไว้ใน MainActivity (layoutLevelSelect) แล้ว
//   จึงเป็นโค้ดสำรอง/ของเก่า ถ้าจะใช้: เปิดด้วย startActivity(new Intent(this, SelectStageActivity.class)) และส่ง HERO_ID มาด้วย
//
// [ผังไฟล์] ตัวแปรปุ่มด่าน -> onCreate -> onNewIntent -> onResume/onPause -> updateStageUnlocks -> setupStageButton
//           -> makeDraggable (ลากวาง) -> applyOffset -> startBattleStage
// =====================================================================================
package com.example.finfury;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull; // 🟢 Import สำหรับแก้ Warning line 38

// [ส่วนหัวคลาส] extends BaseActivity = โหมดเต็มจอ
public class SelectStageActivity extends BaseActivity {

    // [ปุ่มด่าน 5 ปุ่ม] ผูกจาก id ใน activity_select_stage.xml ที่ onCreate
    private ImageButton btnStage1;
    private ImageButton btnStage2;
    private ImageButton btnStage3;
    private ImageButton btnStage4;
    private ImageButton btnStage5;

    // [ฮีโร่ที่เลือกมา] อ่านจาก Intent (HERO_ID) ค่าเริ่มต้น 1 แล้วส่งต่อไป BattleActivity ตอนเข้าด่าน
    private int currentHeroId = 1;

    // [onCreate] ตั้ง layout อ่าน HERO_ID และผูกปุ่มด่านทั้ง 5
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_stage);

        currentHeroId = getIntent().getIntExtra("HERO_ID", 1);

        // ดึง View ตาม ID ใน XML
        btnStage1 = findViewById(R.id.btnStage1);
        btnStage2 = findViewById(R.id.btnStage2);
        btnStage3 = findViewById(R.id.btnStage3);
        btnStage4 = findViewById(R.id.btnStage4);
        btnStage5 = findViewById(R.id.btnStage5);
    }

    // [onNewIntent] ถ้าหน้านี้ถูกเรียกซ้ำขณะเปิดอยู่ (Intent ใหม่) ให้อัปเดต Intent และค่า HERO_ID
    @Override
    protected void onNewIntent(@NonNull Intent intent) { // 🟢 ใส่ @NonNull แก้ Warning line 38
        super.onNewIntent(intent);
        setIntent(intent);
        currentHeroId = intent.getIntExtra("HERO_ID", currentHeroId); // 🟢 ตัด if ออก แก้ Warning line 41
    }

    // [onResume] เล่นเพลงเมนู แล้วอัปเดตกุญแจ/ดาว (เพราะอาจเพิ่งชนะด่านกลับมา)
    @Override
    protected void onResume() {
        super.onResume();
        SoundManager.playMusic(this, "bgm_menu");
        updateStageUnlocks();
    }

    // [onPause] หยุดเพลง
    @Override
    protected void onPause() {
        super.onPause();
        SoundManager.pauseMusic();
    }

    // [updateStageUnlocks] เขียนดาวที่ได้ใต้ปุ่มด่าน แล้วตั้งค่าปุ่มด่านทีละปุ่ม (ด่านที่ล็อก = ไอคอนกุญแจ)
    //   [ข้อสังเกต] ไอคอนด่าน 5 ที่นี่ใช้น้ำวนน้ำเงินเหมือนด่านอื่น (ต่างจาก MainActivity ที่ใช้ไอคอนบอส)
    private void updateStageUnlocks() {
        // ปลดล็อกทีละด่านตามความคืบหน้า (GameProgress.getUnlockedStage)

        // ดาวที่ดีที่สุดของแต่ละด่านใต้ปุ่มด่าน
        int[] starViews = {R.id.txtStars1, R.id.txtStars2, R.id.txtStars3, R.id.txtStars4, R.id.txtStars5};
        for (int i = 0; i < starViews.length; i++) {
            TextView tv = findViewById(starViews[i]);
            if (tv != null) tv.setText(GameProgress.starsText(GameProgress.getStars(this, i + 1)));
        }

        setupStageButton(btnStage1, 1, R.drawable.ic_whirlpool_green, R.id.txtStars1);
        setupStageButton(btnStage2, 2, R.drawable.ic_whirlpool_blue, R.id.txtStars2);
        setupStageButton(btnStage3, 3, R.drawable.ic_whirlpool_blue, R.id.txtStars3);
        setupStageButton(btnStage4, 4, R.drawable.ic_whirlpool_blue, R.id.txtStars4);
        setupStageButton(btnStage5, 5, R.drawable.ic_whirlpool_blue, R.id.txtStars5);
    }

    // [setupStageButton] ตั้งความโปร่งใส/ไอคอนตามว่าปลดล็อกหรือยัง และผูกการกด: ด่านที่เปิดอยู่เท่านั้นที่เข้าได้
    //   แล้วทำให้ปุ่มลากได้ (makeDraggable)
    private void setupStageButton(ImageButton button, int stageNumber, int drawableRes, int starsViewId) {
        if (button == null) return;

        final boolean open = stageNumber <= GameProgress.getUnlockedStage(this);
        button.setEnabled(true);
        button.setAlpha(open ? 1.0f : 0.6f);
        button.setImageResource(open ? drawableRes : R.drawable.ic_whirlpool_lock);
        button.setOnClickListener(v -> {
            if (open) startBattleStage(stageNumber);
        });
        makeDraggable(button, findViewById(starsViewId), stageNumber);
    }

    // [ลากไอคอนด่าน] ตำแหน่งที่วางเก็บเป็นหน่วย dp (ไม่ใช่พิกเซล) เพื่อให้ตรงแม้ความละเอียดจอเปลี่ยน
    // ---------------------------------------------------------
    // ลากไอคอนด่านไปวางที่ไหนก็ได้ (แตะเฉยๆ ยังเข้าด่านเหมือนเดิม) ตำแหน่งที่วางจำไว้ข้ามการเปิดแอป
    // ---------------------------------------------------------
    private static final String PREFS_STAGE_POS = "StageIconPositions";

    // [makeDraggable] ตั้ง touch listener บนไอคอนเพื่อแยก "แตะ" กับ "ลาก":
    //   DOWN : จดตำแหน่งนิ้วและ translation เริ่มต้น คืน false เพื่อให้ปุ่มรับการกดต่อ (ถ้าไม่ลากจะเป็นคลิก)
    //   MOVE : ถ้านิ้วขยับเกิน slop (ระยะที่ระบบถือว่าเป็นการลาก) จึงเริ่มลาก ยกเลิกสถานะกด และห้ามพ่อแย่งเหตุการณ์
    //          คำนวณตำแหน่งใหม่และบีบไม่ให้ออกนอกจอ แล้ว applyOffset (ย้ายทั้งไอคอนและป้ายดาวด้วย)
    //   UP/CANCEL : ถ้ากำลังลากอยู่ ให้บันทึกตำแหน่งลง SharedPreferences และคืน true (กลืนเหตุการณ์ ไม่ให้นับเป็นคลิกเข้าด่าน)
    //   [แก้ยังไง] อยากปิดการลาก: ลบบรรทัด makeDraggable(...) ใน setupStageButton
    @SuppressLint("ClickableViewAccessibility")
    private void makeDraggable(View icon, View starsLabel, int stageNumber) {
        SharedPreferences prefs = getSharedPreferences(PREFS_STAGE_POS, MODE_PRIVATE);
        float density = getResources().getDisplayMetrics().density;
        // เก็บเป็น dp เพื่อให้ตรงกันแม้ความละเอียดหน้าจอเปลี่ยน
        applyOffset(icon, starsLabel,
                prefs.getFloat("x" + stageNumber, 0f) * density,
                prefs.getFloat("y" + stageNumber, 0f) * density);

        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        final float[] down = new float[4];   // rawX, rawY, translationX, translationY ตอนนิ้วแตะ
        final boolean[] dragging = {false};

        icon.setOnTouchListener((v, e) -> {
            View parent = (View) v.getParent();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                    down[2] = v.getTranslationX();
                    down[3] = v.getTranslationY();
                    dragging[0] = false;
                    return false;   // ให้ปุ่มจัดการการกดตามปกติต่อ (ถ้าไม่ลากจะเป็นการคลิก)
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - down[0], dy = e.getRawY() - down[1];
                    if (!dragging[0] && Math.hypot(dx, dy) > slop) {
                        dragging[0] = true;
                        v.setPressed(false);
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (!dragging[0]) return false;
                    // จำกัดไม่ให้ลากออกนอกจอ (v.getLeft/Top คือตำแหน่งตั้งต้นของ layout)
                    float tx = down[2] + dx, ty = down[3] + dy;
                    if (parent != null) {
                        tx = Math.max(-v.getLeft(), Math.min(parent.getWidth() - v.getRight(), tx));
                        ty = Math.max(-v.getTop(), Math.min(parent.getHeight() - v.getBottom(), ty));
                    }
                    applyOffset(v, starsLabel, tx, ty);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        dragging[0] = false;
                        v.setPressed(false);
                        prefs.edit()
                                .putFloat("x" + stageNumber, v.getTranslationX() / density)
                                .putFloat("y" + stageNumber, v.getTranslationY() / density)
                                .apply();
                        return true;   // กลืนเหตุการณ์ ไม่ให้นับเป็นคลิกเข้าด่าน
                    }
                    return false;
                default:
                    return false;
            }
        });
    }

    // [applyOffset] ย้าย (translation) ทั้งไอคอนและป้ายดาวด้วยค่าเท่ากัน เพราะป้ายผูกกับไอคอนด้วย constraint แต่ translation ไม่ส่งต่ออัตโนมัติ
    /** ย้ายไอคอนพร้อมป้ายดาวใต้ไอคอน (ป้ายผูกกับไอคอนด้วย constraint แต่ translation ไม่ส่งต่อให้อัตโนมัติ) */
    private static void applyOffset(View icon, View starsLabel, float tx, float ty) {
        icon.setTranslationX(tx);
        icon.setTranslationY(ty);
        if (starsLabel != null) {
            starsLabel.setTranslationX(tx);
            starsLabel.setTranslationY(ty);
        }
    }

    // [startBattleStage] เล่นเสียงปุ่ม แล้วเปิด BattleActivity พร้อม STAGE_ID และ HERO_ID
    private void startBattleStage(int stageId) {
        SoundManager.play(SoundManager.Sfx.BUTTON);
        Intent intent = new Intent(SelectStageActivity.this, BattleActivity.class);
        intent.putExtra("STAGE_ID", stageId);
        intent.putExtra("HERO_ID", currentHeroId);
        startActivity(intent);
    }
}