package com.example.finfury;

import android.app.AlertDialog;
import android.content.Context;
import android.os.CountDownTimer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * จัดการโจทย์ที่ขึ้นตอนสแตกเต็ม: คลังคำถาม, สุ่มคำถาม (ไม่ซ้ำข้อเดิมติดกัน),
 * แสดง Dialog, จับเวลา และแจ้งผลกลับผ่าน Listener
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
        /** Dialog ขึ้นแล้ว (ควรหยุดเกม) */
        void onQuizShown();

        /** ตอบถูก */
        void onCorrect();

        /** ตอบผิด */
        void onWrong();

        /** หมดเวลา */
        void onTimeout();
    }

    private static final long QUIZ_TIME_MS = 300000; // 5 นาที

    private final Context context;
    private final String title;
    private final List<QuizQuestion> bank;
    private final Listener listener;

    private int lastQuestionIndex = -1;
    private CountDownTimer timer;
    private AlertDialog dialog;

    public QuizManager(Context context, String title, List<QuizQuestion> bank, Listener listener) {
        this.context = context;
        this.title = title;
        this.bank = bank;
        this.listener = listener;
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    public void show() {
        if (bank.isEmpty() || isShowing()) return;

        listener.onQuizShown();

        int randomIndex;
        do {
            randomIndex = (int) (Math.random() * bank.size());
        } while (bank.size() > 1 && randomIndex == lastQuestionIndex);
        lastQuestionIndex = randomIndex;

        final QuizQuestion q = bank.get(randomIndex);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(formatTitle(QUIZ_TIME_MS, q));
        builder.setCancelable(false);
        builder.setItems(q.options, (d, which) -> {
            cancelTimer();
            if (which == q.correctIndex) {
                listener.onCorrect();
            } else {
                listener.onWrong();
            }
        });

        dialog = builder.create();
        dialog.show();

        cancelTimer();
        timer = new CountDownTimer(QUIZ_TIME_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (dialog != null && dialog.isShowing()) {
                    dialog.setTitle(formatTitle(millisUntilFinished, q));
                }
            }

            @Override
            public void onFinish() {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
                listener.onTimeout();
            }
        }.start();
    }

    /** เรียกจาก onDestroy ของ Activity */
    public void destroy() {
        cancelTimer();
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        dialog = null;
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private String formatTitle(long millisLeft, QuizQuestion q) {
        long minutes = (millisLeft / 1000) / 60;
        long seconds = (millisLeft / 1000) % 60;
        return String.format(Locale.US, "%s Quiz (%02d:%02d)\n%s", title, minutes, seconds, q.question);
    }

    // =========================================================
    // คลังคำถาม (ตอนนี้มีชุดแคลคูลัสชุดเดียว เพิ่มชุดวิชาอื่นได้ที่นี่)
    // =========================================================
    public static List<QuizQuestion> calculusQuestions() {
        List<QuizQuestion> list = new ArrayList<>();
        list.add(new QuizQuestion("ดิฟเฟอเรนเชียลพื้นฐาน: อนุพันธ์ของ x (d/dx x) มีค่าเท่ากับข้อใด?", new String[]{"0", "1", "x", "2x"}, 1));
        list.add(new QuizQuestion("อนุพันธ์ของค่าคงที่ c (d/dx c) มีค่าเท่ากับข้อใด?", new String[]{"0", "1", "c", "x"}, 0));
        list.add(new QuizQuestion("อนุพันธ์ของ x² (d/dx x²) มีค่าเท่ากับข้อใด?", new String[]{"x", "2x", "x²", "2"}, 1));
        list.add(new QuizQuestion("อนุพันธ์ของ x³ (d/dx x³) มีค่าเท่ากับข้อใด?", new String[]{"3x", "3x²", "x²", "3"}, 1));
        list.add(new QuizQuestion("อนุพันธ์ของ sin(x) (d/dx sin(x)) คือข้อใด?", new String[]{"cos(x)", "-cos(x)", "tan(x)", "-sin(x)"}, 0));
        list.add(new QuizQuestion("อนุพันธ์ของ cos(x) (d/dx cos(x)) คือข้อใด?", new String[]{"sin(x)", "-sin(x)", "-cos(x)", "sec(x)"}, 1));
        list.add(new QuizQuestion("อนุพันธ์ของ e^x (d/dx e^x) คือข้อใด?", new String[]{"e^x", "x e^(x-1)", "1", "ln(x)"}, 0));
        list.add(new QuizQuestion("อนุพันธ์ของ ln(x) (d/dx ln(x)) คือข้อใด?", new String[]{"1/x", "e^x", "1", "x"}, 0));
        return list;
    }
}