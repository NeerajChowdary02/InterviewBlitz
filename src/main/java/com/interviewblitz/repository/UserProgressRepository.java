package com.interviewblitz.repository;

import com.interviewblitz.model.UserProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data access layer for UserProgress entities.
 * Provides the spaced-repetition queue query and weak-area lookups.
 */
@Repository
public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {

    /** Fetch the progress record for one specific problem — used when updating after a quiz. */
    Optional<UserProgress> findByProblemId(Long problemId);

    /**
     * Returns all problems due for review on or before the given date, sorted by
     * oldest due date first — this is the daily review queue order.
     */
    List<UserProgress> findAllByNextReviewDateLessThanEqualOrderByNextReviewDateAsc(LocalDate date);

    /**
     * Returns the 10 problems with the lowest ease factor — i.e. the ones the user
     * struggles with most. Used by the weak-areas stats endpoint.
     */
    List<UserProgress> findTop10ByOrderByEaseFactorAsc();
}
