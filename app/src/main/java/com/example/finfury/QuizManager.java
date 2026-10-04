package com.example.finfury;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * จัดการโจทย์ที่ขึ้นตอนสแตกเต็ม: คลังคำถาม, สุ่มคำถาม (ไม่ซ้ำข้อเดิมติดกัน),
 * แสดงโจทย์บน overlay ใน activity_battle.xml (overlayQuiz), จับเวลา และแจ้งผลกลับผ่าน Listener
 * หลังตอบ/หมดเวลา จะโชว์ผลถูก-ผิดพร้อมไฮไลต์คำตอบที่ถูก ~2 วินาที แล้วค่อยปิด overlay และเรียก Listener
 * BattleActivity ไม่ต้องรู้รายละเอียดของ quiz อีกต่อไป
 */
public class QuizManager {

    public static class QuizQuestion {
        public final String question;
        public final String[] options;
        public final int correctIndex;

        public QuizQuestion(String question, String[] options, int correctIndex) {
            this.question = question;
            this.options = options;
            this.correctIndex = correctIndex;
        }
    }

    public interface Listener {
        /** โจทย์ขึ้นแล้ว (ควรหยุดเกม) */
        void onQuizShown();

        /** ตอบถูก (เรียกหลังโชว์ผลครบ ~2 วินาทีและปิด overlay แล้ว) */
        void onCorrect();

        /** ตอบผิด (เรียกหลังโชว์ผลครบ ~2 วินาทีและปิด overlay แล้ว) */
        void onWrong();

        /** หมดเวลา (เรียกหลังโชว์ผลครบ ~2 วินาทีและปิด overlay แล้ว) */
        void onTimeout();
    }

    private static final long QUIZ_TIME_MS = 30000; // 30 วินาที
    private static final long FEEDBACK_MS = 2000;
    private static final int BAR_MAX = 1000;

    private static final int COLOR_OPTION = Color.parseColor("#2C3E50");
    private static final int COLOR_CORRECT = Color.parseColor("#27AE60");
    private static final int COLOR_WRONG = Color.parseColor("#C0392B");

    private final String title;
    private final List<QuizQuestion> bank;
    private final Listener listener;

    private final View overlay;
    private final TextView txtTitle;
    private final TextView txtQuestion;
    private final TextView txtTime;
    private final TextView txtFeedback;
    private final ProgressBar barTime;
    private final Button[] optionButtons = new Button[4];

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Integer> deck = new ArrayList<>();   // ข้อที่ยังไม่ถูกหยิบในรอบนี้
    private int lastQuestionIndex = -1;
    private final TextView txtFormula;
    private CountDownTimer timer;
    private QuizQuestion current;
    private boolean answered = false;     // ตอบ/หมดเวลาแล้ว กำลังโชว์ผลอยู่ (กดซ้ำไม่ได้)
    private Runnable pendingClose;
    private Runnable countdownRunnable;
    private int lastTickSecond = -1;

    private static final int COUNTDOWN_FROM = 3;
    private static final long COUNTDOWN_STEP_MS = 1000;

    private final View layoutContent;
    private final TextView txtCountdown;

    public QuizManager(Context context, View overlay, String title, List<QuizQuestion> bank, Listener listener) {
        this.overlay = overlay;
        this.title = title;
        this.bank = bank;
        this.listener = listener;

        layoutContent = overlay.findViewById(R.id.layoutQuizContent);
        txtCountdown = overlay.findViewById(R.id.txtQuizCountdown);
        txtTitle = overlay.findViewById(R.id.txtQuizTitle);
        txtQuestion = overlay.findViewById(R.id.txtQuizQuestion);
        txtFormula = overlay.findViewById(R.id.txtQuizFormula);
        txtTime = overlay.findViewById(R.id.txtQuizTime);
        txtFeedback = overlay.findViewById(R.id.txtQuizFeedback);
        barTime = overlay.findViewById(R.id.barQuizTime);
        optionButtons[0] = overlay.findViewById(R.id.btnQuizOpt1);
        optionButtons[1] = overlay.findViewById(R.id.btnQuizOpt2);
        optionButtons[2] = overlay.findViewById(R.id.btnQuizOpt3);
        optionButtons[3] = overlay.findViewById(R.id.btnQuizOpt4);

        for (int i = 0; i < optionButtons.length; i++) {
            final int index = i;
            optionButtons[i].setOnClickListener(v -> resolve(index));
        }
        keepContentOutOfCutout(context);
        barTime.setMax(BAR_MAX);
    }

    /**
     * กันกล้องหน้า (display cutout) บังเนื้อหาโจทย์: เพิ่ม padding ซ้าย-ขวาของ "ทั้งบล็อก" เนื้อหา quiz
     * (ชื่อวิชา เวลา โจทย์ สูตร ปุ่มตอบ) ให้เท่ากันทั้งสองข้างตามฝั่งที่ cutout ใหญ่กว่า
     * จึงปลอดภัยทั้งตอนกล้องอยู่ซ้ายและตอนหมุนจอกลับด้านที่กล้องอยู่ขวา
     * ถ้าเครื่องไม่รายงาน cutout เลย ใช้ padding ขั้นต่ำ 48dp แทน
     */
    private void keepContentOutOfCutout(Context context) {
        final float density = context.getResources().getDisplayMetrics().density;
        final int basePad = layoutContent.getPaddingLeft();     // padding เดิมจาก layout (ซ้าย = ขวา)
        final int minPad = Math.round(48 * density);

        ViewCompat.setOnApplyWindowInsetsListener(layoutContent, (v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            int safe = Math.max(cutout.left, cutout.right);
            int pad = safe > 0 ? basePad + safe : minPad;
            v.setPadding(pad, v.getPaddingTop(), pad, v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(layoutContent);
    }

    /** ผู้เรียกใช้บอกว่าตอนนี้เปิดโจทย์ได้ไหม (เช่น ด่านยังไม่จบ) ตรวจทุกครั้งที่เรียก show() */
    public interface Gate {
        boolean canShow();
    }

    private Gate gate;

    public void setGate(Gate gate) {
        this.gate = gate;
    }

    public boolean isShowing() {
        return overlay.getVisibility() == View.VISIBLE;
    }

    public void show() {
        if (bank.isEmpty() || isShowing()) return;
        if (gate != null && !gate.canShow()) return;   // ด่านจบแล้ว หรือมี overlay อื่นเปิดอยู่: ไม่เปิดโจทย์

        listener.onQuizShown();

        current = bank.get(nextQuestionIndex());
        answered = false;

        txtTitle.setText(String.format(Locale.US, "%s Quiz", title));
        showQuestionText(current.question);
        txtFeedback.setVisibility(View.GONE);
        for (int i = 0; i < optionButtons.length; i++) {
            Button b = optionButtons[i];
            if (i < current.options.length) {
                b.setVisibility(View.VISIBLE);
                b.setText(String.format(Locale.US, "%c.  %s", 'A' + i, current.options[i]));
                tint(b, COLOR_OPTION);
            } else {
                b.setVisibility(View.INVISIBLE);   // เก็บที่ไว้ ไม่ให้ปุ่มที่เหลือขยับ
            }
        }
        updateTime(QUIZ_TIME_MS);
        overlay.setVisibility(View.VISIBLE);
        ViewCompat.requestApplyInsets(layoutContent);   // คำนวณ padding กันกล้องใหม่ทุกครั้งที่โจทย์ขึ้น

        // นับ 3-2-1 ก่อน ค่อยเฉลยโจทย์และเริ่มจับเวลา (ระหว่างนับยังไม่เห็นโจทย์และกดตอบไม่ได้)
        layoutContent.setVisibility(View.INVISIBLE);
        runCountdown(COUNTDOWN_FROM);
    }

    private void runCountdown(int number) {
        if (number <= 0) {
            txtCountdown.setVisibility(View.GONE);
            layoutContent.setVisibility(View.VISIBLE);
            beginQuizTimer();
            return;
        }
        txtCountdown.setText(String.valueOf(number));
        txtCountdown.setVisibility(View.VISIBLE);
        // เลขเด้งเข้ามาแล้วจางลงนิดหน่อยในแต่ละวินาที
        txtCountdown.setScaleX(1.6f);
        txtCountdown.setScaleY(1.6f);
        txtCountdown.setAlpha(1f);
        txtCountdown.animate().scaleX(1f).scaleY(1f).alpha(0.7f).setDuration(COUNTDOWN_STEP_MS).start();

        countdownRunnable = () -> runCountdown(number - 1);
        handler.postDelayed(countdownRunnable, COUNTDOWN_STEP_MS);
    }

    private void beginQuizTimer() {
        cancelTimer();
        lastTickSecond = -1;
        timer = new CountDownTimer(QUIZ_TIME_MS, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateTime(millisUntilFinished);

                // เสียงติ๊กวินาทีละครั้งเฉพาะ 10 วินาทีสุดท้าย (onTick ถี่ทุก 100 ms จึงจำวินาทีล่าสุดไว้กันเล่นซ้ำ)
                int secondsLeft = (int) ((millisUntilFinished + 999) / 1000);
                if (secondsLeft <= 10 && secondsLeft != lastTickSecond) {
                    lastTickSecond = secondsLeft;
                    SoundManager.play(SoundManager.Sfx.QUIZ_TICK);
                }
            }

            @Override
            public void onFinish() {
                updateTime(0);
                resolve(-1);
            }
        }.start();
    }

    /**
     * สุ่มแบบไม่ซ้ำจนครบทุกข้อ: เทข้อทั้งหมดลง "กอง" สับไพ่ แล้วหยิบทีละข้อ
     * พอหมดกองค่อยสับใหม่ (และข้อแรกของกองใหม่จะไม่ซ้ำกับข้อสุดท้ายของกองเก่า)
     */
    private int nextQuestionIndex() {
        if (deck.isEmpty()) {
            for (int i = 0; i < bank.size(); i++) deck.add(i);
            Collections.shuffle(deck);
            int top = deck.size() - 1;
            if (deck.size() > 1 && deck.get(top) == lastQuestionIndex) {
                Collections.swap(deck, top, 0);
            }
        }
        lastQuestionIndex = deck.remove(deck.size() - 1);
        return lastQuestionIndex;
    }

    /** แยกบรรทัดสูตร ("สูตร: ...") ออกมาแสดงเป็นบรรทัดของตัวเองใต้โจทย์ ข้อความทั้งหมดยังครบเหมือนเดิม */
    private void showQuestionText(String text) {
        int cut = text.indexOf("\nสูตร:");
        if (cut < 0) {
            txtQuestion.setText(text);
            txtFormula.setVisibility(View.GONE);
        } else {
            txtQuestion.setText(text.substring(0, cut));
            txtFormula.setText(text.substring(cut + 1));
            txtFormula.setVisibility(View.VISIBLE);
        }
    }

    /** choice = ข้อที่เลือก, -1 = หมดเวลา */
    private void resolve(int choice) {
        if (answered || current == null) return;
        answered = true;
        cancelTimer();

        final boolean correct = choice == current.correctIndex;
        final boolean timedOut = choice < 0;

        // ไฮไลต์: ข้อที่ถูกเป็นสีเขียวเสมอ ข้อที่เลือกผิดเป็นสีแดง
        tint(optionButtons[current.correctIndex], COLOR_CORRECT);
        if (!correct && !timedOut) tint(optionButtons[choice], COLOR_WRONG);

        txtFeedback.setVisibility(View.VISIBLE);
        if (correct) {
            txtFeedback.setTextColor(COLOR_CORRECT);
            txtFeedback.setText("✅ ถูกต้อง!");
        } else if (timedOut) {
            txtFeedback.setTextColor(COLOR_WRONG);
            txtFeedback.setText("⏰ หมดเวลา! คำตอบที่ถูกคือข้อสีเขียว");
        } else {
            txtFeedback.setTextColor(COLOR_WRONG);
            txtFeedback.setText("❌ ผิด! คำตอบที่ถูกคือข้อสีเขียว");
        }

        pendingClose = () -> {
            pendingClose = null;
            overlay.setVisibility(View.GONE);
            if (correct) listener.onCorrect();
            else if (timedOut) listener.onTimeout();
            else listener.onWrong();
        };
        handler.postDelayed(pendingClose, FEEDBACK_MS);
    }

    /** เรียกจาก onDestroy ของ Activity */
    /**
     * ปิดโจทย์ทันทีโดยไม่เรียก onCorrect/onWrong/onTimeout (ใช้ตอนด่านจบขณะโจทย์เปิดหรือกำลังนับ 3-2-1)
     * ยกเลิกตัวนับเวลา (เสียงติ๊กจึงหยุดด้วย) และงานที่รอเวลาทั้งหมด
     */
    public void dismiss() {
        cancelTimer();
        if (pendingClose != null) handler.removeCallbacks(pendingClose);
        pendingClose = null;
        if (countdownRunnable != null) handler.removeCallbacks(countdownRunnable);
        countdownRunnable = null;
        txtCountdown.animate().cancel();
        txtCountdown.setVisibility(View.GONE);
        overlay.setVisibility(View.GONE);
        answered = true;   // กดตอบ/หมดเวลาอีกไม่ได้ (resolve() จะไม่ทำอะไร)
        current = null;
    }

    /** เรียกจาก onDestroy ของ Activity */
    public void destroy() {
        dismiss();
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private int lastShownSecond = -1;
    private int lastBarProgress = -1;
    private final StringBuilder timeText = new StringBuilder(8);

    // onTick ถี่ทุก 100 ms แต่ข้อความเวลาเปลี่ยนวินาทีละครั้ง จึงสร้างข้อความใหม่เฉพาะตอนวินาทีเปลี่ยน
    private void updateTime(long millisLeft) {
        int progress = (int) (millisLeft * BAR_MAX / QUIZ_TIME_MS);
        if (progress != lastBarProgress) {
            lastBarProgress = progress;
            barTime.setProgress(progress);
        }
        int totalSec = (int) ((millisLeft + 999) / 1000);
        if (totalSec != lastShownSecond) {
            lastShownSecond = totalSec;
            timeText.setLength(0);
            int min = totalSec / 60, sec = totalSec % 60;
            if (min < 10) timeText.append('0');
            timeText.append(min).append(':');
            if (sec < 10) timeText.append('0');
            timeText.append(sec);
            txtTime.setText(timeText);
        }
    }

    private static void tint(Button b, int color) {
        b.setBackgroundTintList(ColorStateList.valueOf(color));
    }
}
