// =====================================================================================
// [คนที่ 3 - View หน้าจอและการเปลี่ยนหน้า]  ไฟล์: BaseActivity.java  (27 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/BaseActivity.java
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   Activity "แม่" (abstract) ที่ทำให้ทุกหน้าเป็นโหมดเต็มจอ โดยซ่อนแถบสถานะ (ด้านบน) และแถบนำทาง (ด้านล่าง) ของระบบ
//   ใครสืบทอด: MainActivity, SelectStageActivity, BattleActivity  (extends BaseActivity)
//   [SettingsActivity ไม่ได้สืบทอดจากตัวนี้ จึงยังเห็นแถบระบบ]
//
// [จะใช้กับหน้าใหม่ยังไง] เขียน public class หน้าใหม่ extends BaseActivity แทน AppCompatActivity
// [จะแก้ยังไง]
//   - อยากให้แถบระบบแสดงตลอด: ลบการเรียก hideSystemBars()
//   - พฤติกรรมปัดขอบจอ: เปลี่ยนค่า BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE (ปัดแล้วแถบโผล่ชั่วคราวและหายเอง)
// =====================================================================================
package com.example.finfury;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

// [คอมเมนต์คลาส - โดยเจ้าของไฟล์เดิม]
/**
 * Activity พื้นฐานของทุกหน้าเกม: ซ่อนแถบสถานะและแถบนำทางของระบบ (โหมดเต็มจอ)
 * ปัดจากขอบจอจะโผล่ชั่วคราวแล้วซ่อนกลับเอง
 */
public abstract class BaseActivity extends AppCompatActivity {

    // [onWindowFocusChanged] ถูกเรียกเมื่อหน้าต่างได้/เสียโฟกัส ถ้าได้โฟกัสคืน (hasFocus) ซ่อนแถบระบบซ้ำ
    //   เพราะบางเหตุการณ์ (เช่น ปิด dialog) ทำให้แถบระบบกลับมา
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // ซ่อนซ้ำทุกครั้งที่ได้โฟกัสคืน (เช่น หลังปิด dialog ที่ทำให้แถบระบบกลับมา)
        if (hasFocus) hideSystemBars();
    }

    // [hideSystemBars] ขอ controller ของหน้าต่าง ตั้งโหมดให้ปัดแล้วโผล่ชั่วคราว แล้วสั่งซ่อน systemBars (สถานะ+นำทาง)
    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }
}
