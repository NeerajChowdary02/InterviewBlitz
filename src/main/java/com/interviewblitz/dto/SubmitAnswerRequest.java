package com.interviewblitz.dto;

/**
 * Request body for POST /api/quiz/submit.
 * The caller provides the question they are answering and the option they selected.
 */
public class SubmitAnswerRequest {

    private Long questionId;
    private String selectedOption;

    public SubmitAnswerRequest() {}

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getSelectedOption() { return selectedOption; }
    public void setSelectedOption(String selectedOption) { this.selectedOption = selectedOption; }
}
