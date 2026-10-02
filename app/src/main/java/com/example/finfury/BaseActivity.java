package com.example.finfury;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Activity พื้นฐานของทุกหน้าเกม: ซ่อนแถบสถานะและแถบนำทางของระบบ (โหมดเต็มจอ)
 * ปัดจากขอบจอจะโผล่ชั่วคราวแล้วซ่อนกลับเอง
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // ซ่อนซ้ำทุกครั้งที่ได้โฟกัสคืน (เช่น หลังปิด dialog ที่ทำให้แถบระบบกลับมา)
        if (hasFocus) hideSystemBars();
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }
}
