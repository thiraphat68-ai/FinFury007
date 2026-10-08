package com.example.finfury;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** ตรวจคลังคำถามโหมดทดสอบ: ต้องไม่ซ้ำกับคำถามในเกม (QuestionBank) และโครงสร้างถูกต้อง */
public class PracticeQuestionBankTest {

    private static final String[] SUBJECTS = {"Calculus", "Circuits", "Chemistry", "Programming", "Physics"};

    /** ตัดบรรทัดสูตรท้ายโจทย์และช่องว่างส่วนเกินออก เพื่อเทียบเฉพาะตัวคำถาม */
    private static String normalize(String question) {
        int cut = question.indexOf("\nสูตร:");
        String q = cut < 0 ? question : question.substring(0, cut);
        return q.replaceAll("\\s+", " ").trim();
    }

    @Test
    public void practiceQuestionsAreNotInGameQuestions() {
        for (String subject : SUBJECTS) {
            Set<String> inGame = new HashSet<>();
            for (QuizManager.QuizQuestion q : QuestionBank.forSubject(subject)) inGame.add(normalize(q.question));
            for (QuizManager.QuizQuestion q : PracticeQuestionBank.forSubject(subject)) {
                assertFalse(subject + ": ซ้ำกับคำถามในเกม: " + q.question, inGame.contains(normalize(q.question)));
            }
        }
    }

    @Test
    public void eachSubjectHasTwelveWellFormedQuestions() {
        for (String subject : SUBJECTS) {
            List<QuizManager.QuizQuestion> list = PracticeQuestionBank.forSubject(subject);
            assertEquals(subject, 12, list.size());
            Set<String> seen = new HashSet<>();
            for (QuizManager.QuizQuestion q : list) {
                assertTrue("โจทย์ซ้ำกันเอง: " + q.question, seen.add(normalize(q.question)));
                assertEquals(q.question, 4, q.options.length);
                assertTrue(q.question, q.correctIndex >= 0 && q.correctIndex < 4);
            }
        }
    }

    @Test
    public void forSubjectMapsEachSubjectToDifferentBank() {
        Set<String> firstQuestions = new HashSet<>();
        for (String subject : SUBJECTS) firstQuestions.add(PracticeQuestionBank.forSubject(subject).get(0).question);
        assertEquals(SUBJECTS.length, firstQuestions.size());
    }
}
