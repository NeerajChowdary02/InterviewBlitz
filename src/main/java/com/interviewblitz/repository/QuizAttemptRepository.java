package com.interviewblitz.repository;

import com.interviewblitz.model.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access layer for QuizAttempt entities.
 * Provides attempt history lookups and correct-answer counts used by the stats service.
 */
@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    /** All attempts for a question, newest first — used to show attempt history. */
    List<QuizAttempt> findByQuestionIdOrderByAttemptedAtDesc(Long questionId);

    /** Total number of attempts ever recorded for a question. */
    long countByQuestionId(Long questionId);

    /** Number of correct attempts for a question — used to compute per-question accuracy. */
    long countByQuestionIdAndCorrectTrue(Long questionId);
}
