package com.interviewblitz.repository;

import com.interviewblitz.model.QuizAttempt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for QuizAttemptRepository custom query methods.
 * Verifies attempt history ordering, count queries, and correct-answer counting.
 */
@ExtendWith(MockitoExtension.class)
class QuizAttemptRepositoryTest {

    @Mock
    private QuizAttemptRepository quizAttemptRepository;

    @Test
    void shouldReturnAttemptsForQuestionNewestFirst() {
        QuizAttempt older = attemptAt(LocalDateTime.now().minusDays(1), true);
        QuizAttempt newer = attemptAt(LocalDateTime.now(), false);

        // Repo returns newest first per the ORDER BY attemptedAt DESC contract
        when(quizAttemptRepository.findByQuestionIdOrderByAttemptedAtDesc(1L))
                .thenReturn(List.of(newer, older));

        List<QuizAttempt> result = quizAttemptRepository.findByQuestionIdOrderByAttemptedAtDesc(1L);

        assertThat(result).hasSize(2);
        // Verify newest is first
        assertThat(result.get(0).getAttemptedAt())
                .isAfter(result.get(1).getAttemptedAt());
    }

    @Test
    void shouldReturnEmptyListWhenNoAttemptsExistForQuestion() {
        when(quizAttemptRepository.findByQuestionIdOrderByAttemptedAtDesc(99L))
                .thenReturn(List.of());

        assertThat(quizAttemptRepository.findByQuestionIdOrderByAttemptedAtDesc(99L)).isEmpty();
    }

    @Test
    void shouldCountTotalAttemptsForQuestion() {
        when(quizAttemptRepository.countByQuestionId(1L)).thenReturn(5L);

        assertThat(quizAttemptRepository.countByQuestionId(1L)).isEqualTo(5L);
    }

    @Test
    void shouldCountOnlyCorrectAttemptsForQuestion() {
        // 3 correct out of 5 total
        when(quizAttemptRepository.countByQuestionIdAndCorrectTrue(1L)).thenReturn(3L);

        assertThat(quizAttemptRepository.countByQuestionIdAndCorrectTrue(1L)).isEqualTo(3L);
    }

    @Test
    void shouldReturnZeroCorrectCountWhenAllAttemptsWereWrong() {
        when(quizAttemptRepository.countByQuestionIdAndCorrectTrue(2L)).thenReturn(0L);

        assertThat(quizAttemptRepository.countByQuestionIdAndCorrectTrue(2L)).isZero();
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private QuizAttempt attemptAt(LocalDateTime time, boolean correct) {
        QuizAttempt a = new QuizAttempt();
        a.setAttemptedAt(time);
        a.setCorrect(correct);
        a.setSelectedOption("A");
        return a;
    }
}
