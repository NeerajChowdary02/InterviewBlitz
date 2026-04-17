package com.interviewblitz.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Records a single attempt at answering a quiz question.
 * Every time the user selects an answer, a row is written here.
 * The spaced-repetition algorithm reads these rows to decide how well
 * the user knows each problem and when it should appear again.
 */
@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The question that was answered in this attempt. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /** The option the user chose: A, B, C, or D. */
    @Column(name = "selected_option", nullable = false, length = 1)
    private String selectedOption;

    /** True when selectedOption matches the question's correctOption. */
    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;

    @PrePersist
    void onInsert() {
        attemptedAt = LocalDateTime.now();
    }

    // Default constructor required by JPA
    public QuizAttempt() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public String getSelectedOption() { return selectedOption; }
    public void setSelectedOption(String selectedOption) { this.selectedOption = selectedOption; }

    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }

    public LocalDateTime getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(LocalDateTime attemptedAt) { this.attemptedAt = attemptedAt; }
}
