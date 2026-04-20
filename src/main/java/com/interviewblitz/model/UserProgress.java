package com.interviewblitz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tracks the spaced-repetition state for one problem per user.
 * The SM-2 algorithm updates easeFactor and intervalDays after each quiz session
 * to determine when the problem should appear in the review queue again.
 * One row exists per problem; it is created the first time the user answers
 * a question for that problem.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "user_progress")
public class UserProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The problem this progress record belongs to — one-to-one, no two rows share a problem. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", unique = true, nullable = false)
    private Problem problem;

    /**
     * SM-2 ease factor: starts at 2.5, increases on correct answers, decreases on wrong ones.
     * Minimum allowed value is 1.3 — below that the interval would become too short to matter.
     */
    @Column(name = "ease_factor", nullable = false)
    private double easeFactor = 2.5;

    /** Days until the next review — grows exponentially with correct answers: 1 → 3 → 7 → 14 → 30. */
    @Column(name = "interval_days", nullable = false)
    private int intervalDays = 1;

    /** How many consecutive times the user has answered correctly (used by the SM-2 formula). */
    @Column(name = "repetitions", nullable = false)
    private int repetitions = 0;

    /** The date when this problem should next appear in the review queue. */
    @Column(name = "next_review_date", nullable = false)
    private LocalDate nextReviewDate;

    /** Timestamp of the last quiz session for this problem — null until first attempt. */
    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    /** Current run of consecutive correct answers — resets to 0 on any wrong answer. */
    @Column(name = "correct_streak", nullable = false)
    private int correctStreak = 0;

    /** All-time count of quiz attempts for this problem across every session. */
    @Column(name = "total_attempts", nullable = false)
    private int totalAttempts = 0;

    /** All-time count of correct answers — used to compute the per-problem retention rate. */
    @Column(name = "total_correct", nullable = false)
    private int totalCorrect = 0;

    @PrePersist
    void onInsert() {
        // Default review date is today so a new problem appears in the queue immediately
        if (nextReviewDate == null) {
            nextReviewDate = LocalDate.now();
        }
    }

    // Default constructor required by JPA
    public UserProgress() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Problem getProblem() { return problem; }
    public void setProblem(Problem problem) { this.problem = problem; }

    public double getEaseFactor() { return easeFactor; }
    public void setEaseFactor(double easeFactor) { this.easeFactor = easeFactor; }

    public int getIntervalDays() { return intervalDays; }
    public void setIntervalDays(int intervalDays) { this.intervalDays = intervalDays; }

    public int getRepetitions() { return repetitions; }
    public void setRepetitions(int repetitions) { this.repetitions = repetitions; }

    public LocalDate getNextReviewDate() { return nextReviewDate; }
    public void setNextReviewDate(LocalDate nextReviewDate) { this.nextReviewDate = nextReviewDate; }

    public LocalDateTime getLastReviewedAt() { return lastReviewedAt; }
    public void setLastReviewedAt(LocalDateTime lastReviewedAt) { this.lastReviewedAt = lastReviewedAt; }

    public int getCorrectStreak() { return correctStreak; }
    public void setCorrectStreak(int correctStreak) { this.correctStreak = correctStreak; }

    public int getTotalAttempts() { return totalAttempts; }
    public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }

    public int getTotalCorrect() { return totalCorrect; }
    public void setTotalCorrect(int totalCorrect) { this.totalCorrect = totalCorrect; }
}
