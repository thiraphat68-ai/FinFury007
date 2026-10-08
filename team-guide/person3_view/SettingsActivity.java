// =====================================================================================
// [คนที่ 3 - View หน้าจอและการเปลี่ยนหน้า]  ไฟล์: SettingsActivity.java  (40 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/SettingsActivity.java
// หน้าตา (layout): app/src/main/res/layout/activity_settings.xml
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   หน้าตั้งค่า (เปิดจากปุ่ม "ตั้งค่า" ในเมนูหลัก) มี 3 อย่าง:
//     1) สวิตช์เปิด/ปิดเสียง (บันทึกผ่าน SoundManager ของคนที่ 4 ลง SharedPreferences ชื่อ GamePrefs)
//     2) ปุ่ม "รีเซ็ตความคืบหน้า" (ล้างดาวและการปลดล็อกด่าน ผ่าน GameProgress.reset ของคนที่ 5) มีกล่องถามยืนยันก่อน
//     3) ปุ่มย้อนกลับ ปิดหน้านี้ด้วย finish()
//
// [จะเพิ่มตัวเลือกใหม่ยังไง] เช่น สวิตช์ "สั่น"
//   1) เพิ่ม SwitchCompat ใน activity_settings.xml (id ใหม่)  2) findViewById + setOnCheckedChangeListener ที่นี่
//   3) เก็บค่าด้วย SharedPreferences (ดูตัวอย่างใน SoundManager.setSoundEnabled)
// =====================================================================================
package com.example.finfury;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

// [คอมเมนต์คลาส - โดยเจ้าของไฟล์เดิม]
/** หน้าตั้งค่า (เปิดจากเมนูหลัก): สวิตช์เปิด/ปิดเสียง และปุ่มรีเซ็ตความคืบหน้า (ล้างดา/การปลดล็อกด่าน) บันทึกลง GamePrefs */
public class SettingsActivity extends AppCompatActivity {

    // [onCreate] เริ่มต้นเสียง (SoundManager.init ปลอดภัยแม้เรียกซ้ำ) ตั้ง layout แล้วผูกปุ่ม
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SoundManager.init(this); // ไม่มีผลถ้าเริ่มไว้แล้ว กันกรณีโปรเซสถูกฆ่าแล้วกลับมาเปิดหน้านี้ก่อน
        setContentView(R.layout.activity_settings);

        // [สวิตช์เสียง] แสดงสถานะปัจจุบันจาก SoundManager.isSoundEnabled() และบันทึกทุกครั้งที่ผู้ใช้สลับด้วย setSoundEnabled
        SwitchCompat switchSound = findViewById(R.id.switchSound);
        switchSound.setChecked(SoundManager.isSoundEnabled());
        switchSound.setOnCheckedChangeListener((button, checked) ->
                SoundManager.setSoundEnabled(this, checked));

        // [ปุ่มรีเซ็ต] เปิด AlertDialog ถามยืนยัน (ปุ่ม "ยกเลิก" ไม่ทำอะไร, ปุ่ม "รีเซ็ต" เรียก GameProgress.reset แล้วขึ้น Toast)
        //   [แก้ข้อความ] แก้สตริงภาษาไทยใน setTitle / setMessage
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

        // [ปุ่มย้อนกลับ] finish() = ปิดหน้านี้ กลับไปหน้าเมนูที่เปิดมา
        Button btnBack = findViewById(R.id.btnSettingsBack);
        btnBack.setOnClickListener(v -> finish());
    }
}
