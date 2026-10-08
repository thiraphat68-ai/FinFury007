package com.example.finfury;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * โหมดทดสอบตอบคำถาม: 1 รอบ = 10 ข้อสุ่มจาก PracticeQuestionBank ของวิชาที่เลือก แสดงทีละข้อ ไม่จับเวลา
 * ตอบแล้วล็อกตัวเลือก ไฮไลต์เขียว/แดง โชว์ข้อความผล แล้วรอกด "ข้อต่อไป" / "ดูผลคะแนน"
 * ไม่แตะ GameProgress (ไม่มีรางวัล ไม่ปลดล็อกด่าน)
 */
public class PracticeQuizActivity extends BaseActivity {

    public static final String EXTRA_SUBJECT = "SUBJECT";
    public static final String EXTRA_SUBJECT_NAME = "SUBJECT_NAME";

    private static final int QUESTIONS_PER_ROUND = 10;

    private String subject;
    private String subjectName;
    private final List<QuizManager.QuizQuestion> round = new ArrayList<>();
    private int index = 0;
    private int score = 0;
    private boolean answered = false;
    private boolean finished = false;

    private View layoutQuestion;
    private View layoutResult;
    private TextView txtCounter;
    private TextView txtQuestion;
    private TextView txtFeedback;
    private ProgressBar barProgress;
    private Button btnNext;
    private final Button[] optionButtons = new Button[4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SoundManager.init(this); // ไม่มีผลถ้าเริ่มไว้แล้ว
        setContentView(R.layout.activity_practice_quiz);

        subject = getIntent().getStringExtra(EXTRA_SUBJECT);
        subjectName = getIntent().getStringExtra(EXTRA_SUBJECT_NAME);
        if (subjectName == null) subjectName = "";

        layoutQuestion = findViewById(R.id.layoutPracticeQuestion);
        layoutResult = findViewById(R.id.layoutPracticeResult);
        txtCounter = findViewById(R.id.txtPracticeCounter);
        txtQuestion = findViewById(R.id.txtPracticeQuestion);
        txtFeedback = findViewById(R.id.txtPracticeFeedback);
        barProgress = findViewById(R.id.barPracticeProgress);
        btnNext = findViewById(R.id.btnPracticeNext);
        ((TextView) findViewById(R.id.txtPracticeSubject)).setText(subjectName);
        ((TextView) findViewById(R.id.txtResultSubject)).setText(subjectName);
        barProgress.setMax(QUESTIONS_PER_ROUND);

        optionButtons[0] = findViewById(R.id.btnPracticeOpt1);
        optionButtons[1] = findViewById(R.id.btnPracticeOpt2);
        optionButtons[2] = findViewById(R.id.btnPracticeOpt3);
        optionButtons[3] = findViewById(R.id.btnPracticeOpt4);
        for (int i = 0; i < optionButtons.length; i++) {
            final int choice = i;
            optionButtons[i].setOnClickListener(v -> answer(choice));
        }

        btnNext.setOnClickListener(v -> {
            if (!answered) return;
            index++;
            if (index >= round.size()) showResult();
            else showQuestion();
        });

        findViewById(R.id.btnPracticeRetry).setOnClickListener(v -> startRound());
        findViewById(R.id.btnPracticeOtherSubject).setOnClickListener(v -> finish());   // กลับไปหน้าเลือกวิชา
        findViewById(R.id.btnPracticeHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Back กลางรอบ: ถามยืนยันก่อนออก (ออกแล้วคะแนนรอบนี้ไม่เก็บ) หน้าสรุปผลออกได้เลย
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (finished) {
                    finish();
                    return;
                }
                new AlertDialog.Builder(PracticeQuizActivity.this)
                        .setTitle("ออกจากการทดสอบ?")
                        .setMessage("คะแนนรอบนี้จะไม่ถูกบันทึก")
                        .setNegativeButton("ทำต่อ", null)
                        .setPositiveButton("ออก", (dialog, which) -> finish())
                        .show();
            }
        });

        startRound();
    }

    /** สุ่ม 10 ข้อไม่ซ้ำ และสลับตัวเลือกของแต่ละข้อ (คำนวณข้อที่ถูกใหม่ตามตำแหน่งหลังสลับ) */
    private void startRound() {
        List<QuizManager.QuizQuestion> pool = new ArrayList<>(PracticeQuestionBank.forSubject(subject));
        Collections.shuffle(pool);
        round.clear();
        for (int i = 0; i < Math.min(QUESTIONS_PER_ROUND, pool.size()); i++) {
            round.add(shuffleOptions(pool.get(i)));
        }
        index = 0;
        score = 0;
        finished = false;
        layoutResult.setVisibility(View.GONE);
        layoutQuestion.setVisibility(View.VISIBLE);
        showQuestion();
    }

    private static QuizManager.QuizQuestion shuffleOptions(QuizManager.QuizQuestion q) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < q.options.length; i++) order.add(i);
        Collections.shuffle(order);
        String[] options = new String[q.options.length];
        int correct = 0;
        for (int i = 0; i < order.size(); i++) {
            options[i] = q.options[order.get(i)];
            if (order.get(i) == q.correctIndex) correct = i;
        }
        return new QuizManager.QuizQuestion(q.question, options, correct);
    }

    private void showQuestion() {
        QuizManager.QuizQuestion q = round.get(index);
        answered = false;
        txtCounter.setText(String.format(Locale.US, "ข้อ %d/%d", index + 1, round.size()));
        barProgress.setProgress(index);
        txtQuestion.setText(q.question);
        txtFeedback.setVisibility(View.INVISIBLE);
        btnNext.setVisibility(View.INVISIBLE);
        for (int i = 0; i < optionButtons.length; i++) {
            Button b = optionButtons[i];
            b.setEnabled(true);
            b.setBackgroundTintList(ColorStateList.valueOf(QuizManager.COLOR_OPTION));
            if (i < q.options.length) {
                b.setVisibility(View.VISIBLE);
                b.setText(String.format(Locale.US, "%c.  %s", 'A' + i, q.options[i]));
            } else {
                b.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void answer(int choice) {
        if (answered || finished) return;
        answered = true;
        QuizManager.QuizQuestion q = round.get(index);
        boolean correct = choice == q.correctIndex;

        // ล็อกตัวเลือก: ข้อที่ถูกเป็นสีเขียวเสมอ ข้อที่เลือกผิดเป็นสีแดง (ปุ่มที่ปิดไว้ยังคงสีไฮไลต์)
        for (Button b : optionButtons) b.setEnabled(false);
        optionButtons[q.correctIndex].setBackgroundTintList(ColorStateList.valueOf(QuizManager.COLOR_CORRECT));
        if (!correct) optionButtons[choice].setBackgroundTintList(ColorStateList.valueOf(QuizManager.COLOR_WRONG));

        txtFeedback.setVisibility(View.VISIBLE);
        if (correct) {
            score++;
            SoundManager.play(SoundManager.Sfx.QUIZ_CORRECT);
            txtFeedback.setTextColor(QuizManager.COLOR_CORRECT);
            txtFeedback.setText("✅ ถูกต้อง!");
        } else {
            SoundManager.play(SoundManager.Sfx.QUIZ_WRONG);
            txtFeedback.setTextColor(QuizManager.COLOR_WRONG);
            txtFeedback.setText("❌ ยังไม่ถูก คำตอบที่ถูกคือข้อสีเขียว");
        }

        boolean last = index == round.size() - 1;
        btnNext.setText(last ? "ดูผลคะแนน" : "ข้อต่อไป");
        btnNext.setVisibility(View.VISIBLE);
    }

    private void showResult() {
        finished = true;
        barProgress.setProgress(round.size());
        ((TextView) findViewById(R.id.txtPracticeScore))
                .setText(String.format(Locale.US, "%d/%d", score, round.size()));
        ((TextView) findViewById(R.id.txtPracticeMessage)).setText(encouragement());
        layoutQuestion.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);
    }

    private String encouragement() {
        float ratio = score / (float) Math.max(1, round.size());
        if (ratio >= 1f) return "เต็มสิบ! สุดยอดไปเลย 🎉";
        if (ratio >= 0.8f) return "เก่งมาก! เกือบเต็มแล้ว 👏";
        if (ratio >= 0.5f) return "ทำได้ดี! ลองอีกรอบจะยิ่งแม่นขึ้น 💪";
        return "ไม่เป็นไร ลองใหม่อีกครั้ง เดี๋ยวก็เก่งขึ้น 😊";
    }
}
