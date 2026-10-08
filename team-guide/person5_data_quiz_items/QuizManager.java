// =====================================================================================
// [คนที่ 5 - ข้อมูล ควิซ ไอเทม และสมดุลเกม]  ไฟล์: QuizManager.java  (353 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/QuizManager.java
// หน้าตา (overlay) อยู่ใน: app/src/main/res/layout/activity_battle.xml (id overlayQuiz และลูก ๆ txtQuiz*, btnQuizOpt1..4, barQuizTime)
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   "ตัวจัดการโจทย์ quiz" ที่ขึ้นเมื่อสแตกพลังเต็ม ทำครบวงจรในคลาสเดียว:
//     ขึ้นหน้าต่างโจทย์ -> นับ 3-2-1 -> เฉลยโจทย์ + เริ่มจับเวลา 30 วินาที -> ผู้เล่นเลือกตอบ (หรือหมดเวลา)
//     -> โชว์ผลถูก/ผิด + ไฮไลต์ข้อที่ถูกเป็นสีเขียว ~2 วินาที -> ปิดหน้าต่าง -> แจ้งผลกลับผ่าน Listener
//   BattleActivity (คนที่ 1) ไม่ต้องรู้รายละเอียดของ quiz เลย แค่เรียก show() แล้วรับผลทาง Listener 4 จังหวะ :
//     onQuizShown (ควรหยุดเกม) , onCorrect , onWrong , onTimeout   (ดู BattleActivity.setupQuiz)
//   คลังโจทย์ไม่อยู่ที่นี่ แต่อยู่ใน QuestionBank.java (QuizManager รับ List<QuizQuestion> มาตอนสร้าง)
//
// [ลำดับการทำงาน (ผังสั้น)]
//   show() --gate.canShow()?--> listener.onQuizShown --> สุ่มโจทย์ (nextQuestionIndex) --> เติมข้อความ/ปุ่ม
//         --> นับ 3-2-1 (runCountdown) --> beginQuizTimer (30 วิ) --> ผู้เล่นกด --> resolve(choice)
//         --> หน่วง 2 วินาที (pendingClose) --> ปิด --> listener.onCorrect/onWrong/onTimeout
//
// [จะแก้ยังไง]
//   เวลาตอบ: QUIZ_TIME_MS | เวลาโชว์ผล: FEEDBACK_MS | เลขนับถอยหลัง: COUNTDOWN_FROM, COUNTDOWN_STEP_MS | สีปุ่ม: COLOR_*
//   ข้อความถูก/ผิด: แก้ txtFeedback.setText ใน resolve | รูปแบบเวลา: updateTime
//   เพิ่มโจทย์: แก้ QuestionBank.java ไม่ต้องแก้ไฟล์นี้
// [ข้อควรระวัง] ห้ามเปลี่ยน id ใน overlayQuiz ของ activity_battle.xml โดยไม่แก้ constructor ด้านล่างให้ตรงกัน
// =====================================================================================
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

// [คอมเมนต์คลาส - โดยเจ้าของไฟล์เดิม]
/**
 * จัดการโจทย์ที่ขึ้นตอนสแตกเต็ม: คลังคำถาม, สุ่มคำถาม (ไม่ซ้ำข้อเดิมติดกัน),
 * แสดงโจทย์บน overlay ใน activity_battle.xml (overlayQuiz), จับเวลา และแจ้งผลกลับผ่าน Listener
 * หลังตอบ/หมดเวลา จะโชว์ผลถูก-ผิดพร้อมไฮไลต์คำตอบที่ถูก ~2 วินาที แล้วค่อยปิด overlay และเรียก Listener
 * BattleActivity ไม่ต้องรู้รายละเอียดของ quiz อีกต่อไป
 */
// [ส่วนหัวคลาส]
public class QuizManager {

    // [QuizQuestion = โจทย์ 1 ข้อ] question = ข้อความโจทย์ (บรรทัดที่ขึ้นต้นด้วย "สูตร:" จะถูกแยกไปแสดงสีเหลืองใต้โจทย์ ดู showQuestionText)
    //   options = ตัวเลือก (สูงสุด 4) | correctIndex = ลำดับข้อที่ถูก (เริ่มที่ 0) ทุกฟิลด์ final (สร้างแล้วแก้ไม่ได้)
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

    // [Listener = ช่องทางแจ้งผลกลับ] BattleActivity implement 4 เมธอดนี้ ทุกผลถูกเรียก "หลังโชว์ผลครบ 2 วินาทีและปิดหน้าต่างแล้ว"
    //   [เพิ่มจังหวะใหม่] เพิ่มเมธอดใน interface แล้ว implement ที่ BattleActivity.setupQuiz และเรียกที่ใน resolve/show
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

    // [ค่าคงที่เวลา] QUIZ_TIME_MS = เวลาตอบ 30 วินาที | FEEDBACK_MS = เวลาโชว์ผลถูก/ผิด | BAR_MAX = ความละเอียดของหลอดเวลา (0-1000)
    private static final long QUIZ_TIME_MS = 30000; // 30 วินาที
    private static final long FEEDBACK_MS = 2000;
    private static final int BAR_MAX = 1000;

    // [สีปุ่มตัวเลือก] ปกติ (น้ำเงินเข้ม) / ถูก (เขียว) / ผิด (แดง)
    private static final int COLOR_OPTION = Color.parseColor("#2C3E50");
    private static final int COLOR_CORRECT = Color.parseColor("#27AE60");
    private static final int COLOR_WRONG = Color.parseColor("#C0392B");

    // [ข้อมูลที่รับมาตอนสร้าง] title = ชื่อวิชาที่แสดงเป็น "ชื่อวิชา Quiz" , bank = คลังโจทย์ของฮีโร่ , listener = ตัวรับผล
    private final String title;
    private final List<QuizQuestion> bank;
    private final Listener listener;

    // [View ที่ใช้] overlay = หน้าต่างโจทย์ทั้งก้อน | txtTitle, txtQuestion, txtTime, txtFeedback, barTime , optionButtons[4] = ปุ่มตัวเลือก 4 ปุ่ม
    private final View overlay;
    private final TextView txtTitle;
    private final TextView txtQuestion;
    private final TextView txtTime;
    private final TextView txtFeedback;
    private final ProgressBar barTime;
    private final Button[] optionButtons = new Button[4];

    // [handler] ตัวหน่วงเวลาบนเธรดหลัก (นับ 3-2-1 และหน่วงปิด) | deck = "กองไพ่" ของโจทย์ที่ยังไม่ถูกหยิบ | lastQuestionIndex = ข้อล่าสุด (กันซ้ำติดกัน)
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Integer> deck = new ArrayList<>();   // ข้อที่ยังไม่ถูกหยิบในรอบนี้
    private int lastQuestionIndex = -1;
    // [ตัวแปรสถานะ] timer = ตัวนับถอยหลัง | current = โจทย์ปัจจุบัน | answered = ตอบแล้ว/หมดเวลาแล้ว (กดซ้ำไม่ได้)
    //   pendingClose, countdownRunnable = งานที่รอเวลา (ต้องยกเลิกได้) | lastTickSecond = วินาทีล่าสุดที่เล่นเสียงติ๊ก
    private final TextView txtFormula;
    private CountDownTimer timer;
    private QuizQuestion current;
    private boolean answered = false;     // ตอบ/หมดเวลาแล้ว กำลังโชว์ผลอยู่ (กดซ้ำไม่ได้)
    private Runnable pendingClose;
    private Runnable countdownRunnable;
    private int lastTickSecond = -1;

    // [ค่านับถอยหลังก่อนโจทย์ขึ้น] เริ่มที่ 3 ทุก 1 วินาที | layoutContent = เนื้อหา quiz ที่ซ่อนไว้ระหว่างนับ | txtCountdown = เลขใหญ่กลางจอ
    private static final int COUNTDOWN_FROM = 3;
    private static final long COUNTDOWN_STEP_MS = 1000;

    private final View layoutContent;
    private final TextView txtCountdown;

    // [constructor] รับ Context, overlay, ชื่อวิชา, คลังโจทย์, listener แล้วผูก View ทั้งหมดด้วย id (ดูรายการใน activity_battle.xml)
    //   ผูก onClick ของปุ่มตัวเลือกทุกปุ่มให้เรียก resolve(ลำดับปุ่ม) , เรียก keepContentOutOfCutout , ตั้ง max ของหลอดเวลา
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

    // [keepContentOutOfCutout] กันกล้องหน้า/รอยบากบังโจทย์ : เพิ่ม padding ซ้ายขวาเท่ากันตามฝั่งที่ cutout ใหญ่กว่า (ทำงานได้ทั้งตอนกล้องอยู่ซ้ายหรือขวา)
    //   เครื่องที่ไม่รายงาน cutout ใช้ padding ขั้นต่ำ 48dp
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

    // [Gate = ประตูกันโจทย์] ผู้เรียก (BattleActivity.canOpenQuiz) บอกว่าตอนนี้เปิดโจทย์ได้ไหม (เช่น ด่านจบแล้ว หรือมีเมนูอื่นเปิดอยู่) setGate ผูกตัวตรวจ
    //   isShowing() = โจทย์กำลังแสดงอยู่ไหม
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

    // [show] เปิดโจทย์ (ไม่ทำถ้าคลังว่าง กำลังแสดงอยู่ หรือประตูไม่ยอม) :
    //   แจ้ง onQuizShown (ให้หยุดเกม) -> สุ่มโจทย์ -> ล้างข้อความผล -> ใส่ข้อความปุ่ม "A.  ตัวเลือก" (ปุ่มที่ไม่ใช้ = ล่องหน เก็บที่ไว้) -> ตั้งเวลาเต็ม
    //   -> แสดง overlay -> ซ่อนเนื้อหา (ผู้เล่นยังไม่เห็นโจทย์) แล้วนับ 3-2-1
    //   [เพิ่มตัวเลือกเป็น 5 ข้อ] ต้องเพิ่มปุ่มใน XML + ในอาร์เรย์ optionButtons ขนาด 4 + constructor
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

    // [runCountdown] นับถอยหลังแบบเรียกตัวเองซ้ำ : โชว์เลข (เด้งจาก 1.6 เท่าแล้วหดจางใน 1 วินาที) หน่วง 1 วินาทีแล้วเรียกตัวเองด้วย number-1
    //   ถึง 0 = ซ่อนเลข โชว์เนื้อหาโจทย์ และเริ่มจับเวลา (beginQuizTimer)
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

    // [beginQuizTimer] CountDownTimer ติ๊กทุก 100 ms : อัปเดตหลอดเวลาและตัวเลข , 10 วินาทีสุดท้ายเล่นเสียงติ๊กวินาทีละครั้ง (จำวินาทีล่าสุดกันซ้ำ)
    //   หมดเวลา -> updateTime(0) แล้ว resolve(-1) (-1 = หมดเวลา)
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

    // [nextQuestionIndex] สุ่มแบบ "สับไพ่" : ใส่ลำดับโจทย์ทั้งหมดลงกอง สับ แล้วหยิบทีละใบจนหมดจึงสับใหม่ (ไม่ซ้ำจนกว่าจะครบทุกข้อ)
    //   ข้อแรกของกองใหม่จะไม่ซ้ำกับข้อสุดท้ายของกองเก่า (สลับตำแหน่งถ้าชน)
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

    // [showQuestionText] ถ้าโจทย์มี "\nสูตร:" แยกส่วนสูตรไปแสดงเป็นบรรทัดสีเหลืองใต้โจทย์ ถ้าไม่มีซ่อนช่องสูตร
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

    // [resolve(choice)] ตัดสินคำตอบ (choice = ลำดับปุ่มที่เลือก , -1 = หมดเวลา) : กันกดซ้ำด้วย answered , หยุดจับเวลา
    //   ไฮไลต์ข้อที่ถูกสีเขียวเสมอ , ข้อที่เลือกผิดสีแดง , ข้อความ "✅ ถูกต้อง!" / "⏰ หมดเวลา!" / "❌ ผิด!" (บอกให้ดูข้อสีเขียว เพื่อให้ผู้เล่นได้เรียนรู้)
    //   แล้วหน่วง FEEDBACK_MS ค่อยปิด overlay และเรียก listener ที่ตรงกับผลลัพธ์
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

    // [dismiss] ปิดโจทย์ทันทีโดย "ไม่เรียก" onCorrect/onWrong/onTimeout : ยกเลิกตัวนับเวลา งานที่รอเวลา และการนับ 3-2-1 แล้วซ่อน overlay
    //   ใช้ตอนด่านจบขณะโจทย์เปิดอยู่ (BattleActivity.cancelEverythingForStageEnd) answered = true ทำให้กดตอบต่อไม่ได้
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

    // [destroy] เรียก dismiss() (เรียกตอน BattleActivity.onDestroy)
    /** เรียกจาก onDestroy ของ Activity */
    public void destroy() {
        dismiss();
    }

    // [cancelTimer] ยกเลิกและลืมตัวนับถอยหลัง
    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    // [ตัวแปรแสดงเวลา] จำวินาทีและความคืบหน้าหลอดล่าสุด + StringBuilder ใช้ซ้ำ กันสร้างข้อความใหม่ทุก 100 ms
    private int lastShownSecond = -1;
    private int lastBarProgress = -1;
    private final StringBuilder timeText = new StringBuilder(8);

    // [updateTime] อัปเดตหลอด (เมื่อค่าเปลี่ยนจริง) และข้อความ "นาที:วินาที" (เมื่อเปลี่ยนวินาที) เช่น 00:30
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

    // [tint] เปลี่ยนสีพื้นหลังปุ่ม
    private static void tint(Button b, int color) {
        b.setBackgroundTintList(ColorStateList.valueOf(color));
    }
}
