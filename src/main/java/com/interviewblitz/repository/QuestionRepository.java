package com.interviewblitz.repository;

import com.interviewblitz.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access layer for Question entities.
 * Provides lookups by problem and by question category type.
 */
@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    /** Find all questions generated for a given problem. */
    List<Question> findByProblemId(Long problemId);

    /** Find all questions of a specific type (PATTERN, COMPLEXITY, EDGE_CASE, APPROACH) for a problem. */
    List<Question> findByProblemIdAndQuestionType(Long problemId, String questionType);

    /** True if at least one question has been generated for this problem — used to skip re-generation. */
    boolean existsByProblemId(Long problemId);
}
