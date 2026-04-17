package com.interviewblitz.repository;

import com.interviewblitz.model.Problem;
import com.interviewblitz.model.UserProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for UserProgressRepository custom query methods.
 * Verifies the review-queue query, problem lookup by ID, and weak-area ranking.
 */
@ExtendWith(MockitoExtension.class)
class UserProgressRepositoryTest {

    @Mock
    private UserProgressRepository userProgressRepository;

    @Test
    void shouldFindProgressByProblemId() {
        UserProgress progress = progressWithEaseFactor(2.5, LocalDate.now());
        when(userProgressRepository.findByProblemId(1L)).thenReturn(Optional.of(progress));

        Optional<UserProgress> result = userProgressRepository.findByProblemId(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getEaseFactor()).isEqualTo(2.5);
    }

    @Test
    void shouldReturnEmptyOptionalWhenNoProblemProgressExists() {
        when(userProgressRepository.findByProblemId(99L)).thenReturn(Optional.empty());

        assertThat(userProgressRepository.findByProblemId(99L)).isEmpty();
    }

    @Test
    void shouldReturnProblemsWhoseReviewDateIsToday() {
        LocalDate today = LocalDate.now();
        UserProgress dueToday = progressWithEaseFactor(2.5, today);

        when(userProgressRepository.findAllByNextReviewDateLessThanEqualOrderByNextReviewDateAsc(today))
                .thenReturn(List.of(dueToday));

        List<UserProgress> queue = userProgressRepository
                .findAllByNextReviewDateLessThanEqualOrderByNextReviewDateAsc(today);

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).getNextReviewDate()).isEqualTo(today);
    }

    @Test
    void shouldReturnProblemsOverdueAsWellAsDueToday() {
        LocalDate today = LocalDate.now();
        UserProgress overdueProgress = progressWithEaseFactor(1.8, today.minusDays(3));
        UserProgress dueTodayProgress = progressWithEaseFactor(2.5, today);

        // Overdue problem comes first because it has an earlier review date
        when(userProgressRepository.findAllByNextReviewDateLessThanEqualOrderByNextReviewDateAsc(today))
                .thenReturn(List.of(overdueProgress, dueTodayProgress));

        List<UserProgress> queue = userProgressRepository
                .findAllByNextReviewDateLessThanEqualOrderByNextReviewDateAsc(today);

        assertThat(queue).hasSize(2);
        assertThat(queue.get(0).getNextReviewDate()).isBefore(queue.get(1).getNextReviewDate());
    }

    @Test
    void shouldReturnEmptyQueueWhenNothingIsDueToday() {
        LocalDate today = LocalDate.now();
        when(userProgressRepository.findAllByNextReviewDateLessThanEqualOrderByNextReviewDateAsc(today))
                .thenReturn(List.of());

        assertThat(userProgressRepository
                .findAllByNextReviewDateLessThanEqualOrderByNextReviewDateAsc(today)).isEmpty();
    }

    @Test
    void shouldReturnTop10WeakProblemsOrderedByLowestEaseFactor() {
        UserProgress weakest = progressWithEaseFactor(1.3, LocalDate.now());
        UserProgress medium = progressWithEaseFactor(2.0, LocalDate.now());
        UserProgress strong = progressWithEaseFactor(2.8, LocalDate.now());

        // Weakest (lowest ease factor) should come first
        when(userProgressRepository.findTop10ByOrderByEaseFactorAsc())
                .thenReturn(List.of(weakest, medium, strong));

        List<UserProgress> weakAreas = userProgressRepository.findTop10ByOrderByEaseFactorAsc();

        assertThat(weakAreas).hasSize(3);
        assertThat(weakAreas.get(0).getEaseFactor())
                .isLessThan(weakAreas.get(1).getEaseFactor());
    }

    @Test
    void shouldReturnEmptyListWhenNoProgressRecordsExist() {
        when(userProgressRepository.findTop10ByOrderByEaseFactorAsc()).thenReturn(List.of());

        assertThat(userProgressRepository.findTop10ByOrderByEaseFactorAsc()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private UserProgress progressWithEaseFactor(double easeFactor, LocalDate nextReviewDate) {
        Problem p = new Problem();
        p.setId(1L);
        UserProgress up = new UserProgress();
        up.setProblem(p);
        up.setEaseFactor(easeFactor);
        up.setNextReviewDate(nextReviewDate);
        return up;
    }
}
