package com.example.finfury;

import android.content.Intent;
import android.os.Bundle;

/** เลือกวิชาที่จะทดสอบตอบคำถาม (เปิดจากปุ่ม "การทดสอบตอบคำถาม" ในเมนูหลัก) ไม่เกี่ยวกับเกมและ GameProgress */
public class QuizSubjectActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SoundManager.init(this); // ไม่มีผลถ้าเริ่มไว้แล้ว
        setContentView(R.layout.activity_quiz_subject);

        // คีย์วิชา (ภาษาอังกฤษ ตรงกับที่ PracticeQuestionBank.forSubject รับ) + ชื่อที่โชว์
        bindSubject(R.id.btnSubjectCalculus, "Calculus", "แคลคูลัส");
        bindSubject(R.id.btnSubjectCircuits, "Circuits", "วงจรไฟฟ้า");
        bindSubject(R.id.btnSubjectChemistry, "Chemistry", "เคมี");
        bindSubject(R.id.btnSubjectProgramming, "Programming", "การเขียนโปรแกรม");
        bindSubject(R.id.btnSubjectPhysics, "Physics", "ฟิสิกส์");

        findViewById(R.id.btnSubjectBack).setOnClickListener(v -> finish());
    }

    private void bindSubject(int buttonId, String subjectKey, String subjectName) {
        findViewById(buttonId).setOnClickListener(v -> {
            SoundManager.play(SoundManager.Sfx.BUTTON);
            Intent intent = new Intent(this, PracticeQuizActivity.class);
            intent.putExtra(PracticeQuizActivity.EXTRA_SUBJECT, subjectKey);
            intent.putExtra(PracticeQuizActivity.EXTRA_SUBJECT_NAME, subjectName);
            startActivity(intent);
        });
    }
}
