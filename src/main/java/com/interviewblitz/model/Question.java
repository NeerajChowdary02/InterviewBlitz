package com.interviewblitz.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a single AI-generated multiple-choice question tied to a LeetCode problem.
 * Each problem gets four questions — one per category: PATTERN, COMPLEXITY, EDGE_CASE, APPROACH.
 * Questions are generated once and reused across quiz sessions.
 */
@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The problem this question was generated for. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "option_a", nullable = false, columnDefinition = "TEXT")
    private String optionA;

    @Column(name = "option_b", nullable = false, columnDefinition = "TEXT")
    private String optionB;

    @Column(name = "option_c", nullable = false, columnDefinition = "TEXT")
    private String optionC;

    @Column(name = "option_d", nullable = false, columnDefinition = "TEXT")
    private String optionD;

    /** The letter of the correct answer: A, B, C, or D. */
    @Column(name = "correct_option", nullable = false, length = 1)
    private String correctOption;

    /** Explains why the correct answer is right — shown after the user answers. */
    @Column(name = "explanation", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    /** Which category this question tests: PATTERN, COMPLEXITY, EDGE_CASE, or APPROACH. */
    @Column(name = "question_type", nullable = false, length = 30)
    private String questionType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onInsert() {
        createdAt = LocalDateTime.now();
    }

    // Default constructor required by JPA
    public Question() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Problem getProblem() { return problem; }
    public void setProblem(Problem problem) { this.problem = problem; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getOptionA() { return optionA; }
    public void setOptionA(String optionA) { this.optionA = optionA; }

    public String getOptionB() { return optionB; }
    public void setOptionB(String optionB) { this.optionB = optionB; }

    public String getOptionC() { return optionC; }
    public void setOptionC(String optionC) { this.optionC = optionC; }

    public String getOptionD() { return optionD; }
    public void setOptionD(String optionD) { this.optionD = optionD; }

    public String getCorrectOption() { return correctOption; }
    public void setCorrectOption(String correctOption) { this.correctOption = correctOption; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
