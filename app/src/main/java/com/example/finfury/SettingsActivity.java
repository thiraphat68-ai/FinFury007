package com.example.finfury;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

/** หน้าตั้งค่า (เปิดจากเมนูหลัก) ตอนนี้มีสวิตช์เปิด/ปิดเสียงอย่างเดียว บันทึกลง GamePrefs */
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

        Button btnBack = findViewById(R.id.btnSettingsBack);
        btnBack.setOnClickListener(v -> finish());
    }
}
