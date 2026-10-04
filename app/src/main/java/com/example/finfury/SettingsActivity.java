package com.example.finfury;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

/** หน้าตั้งค่า (เปิดจากเมนูหลัก): สวิตช์เปิด/ปิดเสียง และปุ่มรีเซ็ตความคืบหน้า (ล้างดา/การปลดล็อกด่าน) บันทึกลง GamePrefs */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SoundManager.init(this); // ไม่มีผลถ้าเริ่มไว้แล้ว กันกรณีโปรเซสถูกฆ่าแล้วกลับมาเปิดหน้านี้ก่อน
        setContentView(R.layout.activity_settings);

        SwitchCompat switchSound = findViewById(R.id.switchSound);
        switchSound.setChecked(SoundManager.isSoundEnabled());
        switchSound.setOnCheckedChangeListener((button, checked) ->
                SoundManager.setSoundEnabled(this, checked));

        // รีเซ็ตความคืบหน้า: ถามยืนยันก่อน เพราะกู้คืนไม่ได้
        findViewById(R.id.btnResetProgress).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("รีเซ็ตความคืบหน้า?")
                        .setMessage("ดาทั้งหมดจะหาย และกลับไปเหลือแค่ด่าน 1 (ค่าตั้งเสียงไม่เปลี่ยน) กู้คืนไม่ได้")
                        .setNegativeButton("ยกเลิก", null)
                        .setPositiveButton("รีเซ็ต", (dialog, which) -> {
                            GameProgress.reset(this);
                            Toast.makeText(this, "รีเซ็ตความคืบหน้าแล้ว", Toast.LENGTH_SHORT).show();
                        })
                        .show());

        Button btnBack = findViewById(R.id.btnSettingsBack);
        btnBack.setOnClickListener(v -> finish());
    }
}
