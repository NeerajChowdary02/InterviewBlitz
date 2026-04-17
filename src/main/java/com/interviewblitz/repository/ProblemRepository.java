package com.interviewblitz.repository;

import com.interviewblitz.model.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for Problem entities.
 * Spring Data JPA generates all query implementations at runtime.
 */
@Repository
public interface ProblemRepository extends JpaRepository<Problem, Long> {

    /** Find a problem by its LeetCode ID — used to skip already-synced problems. */
    Optional<Problem> findByLeetcodeId(Integer leetcodeId);

    /** Find all problems that belong to a given topic (e.g. "trees", "dp"). */
    List<Problem> findByTopic(String topic);

    /** Find all problems of a given difficulty: Easy, Medium, or Hard. */
    List<Problem> findByDifficulty(String difficulty);
}
