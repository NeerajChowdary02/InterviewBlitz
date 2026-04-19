package com.interviewblitz.dto;

import java.time.LocalDate;

/**
 * Response body for POST /api/quiz/submit.
 * Tells the client whether they were right, reveals the correct answer,
 * shows the explanation, and tells them when this problem is due again.
 */
public class SubmitAnswerResponse {

    private boolean correct;
    private String correctOption;
    private String explanation;
    private LocalDate nextReviewDate;

    public SubmitAnswerResponse() {}

    public SubmitAnswerResponse(boolean correct, String correctOption,
                                String explanation, LocalDate nextReviewDate) {
        this.correct = correct;
        this.correctOption = correctOption;
        this.explanation = explanation;
        this.nextReviewDate = nextReviewDate;
    }

    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }

    public String getCorrectOption() { return correctOption; }
    public void setCorrectOption(String correctOption) { this.correctOption = correctOption; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public LocalDate getNextReviewDate() { return nextReviewDate; }
    public void setNextReviewDate(LocalDate nextReviewDate) { this.nextReviewDate = nextReviewDate; }
}
