package com.interviewblitz.repository;

import com.interviewblitz.model.Problem;
import com.interviewblitz.model.Question;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for QuestionRepository custom query methods.
 * Verifies that each method returns the expected data shape and filters correctly
 * by problem ID and question type.
 */
@ExtendWith(MockitoExtension.class)
class QuestionRepositoryTest {

    @Mock
    private QuestionRepository questionRepository;

    @Test
    void shouldReturnAllQuestionsForAProblem() {
        Question q1 = questionWithType("PATTERN");
        Question q2 = questionWithType("COMPLEXITY");
        Question q3 = questionWithType("EDGE_CASE");
        Question q4 = questionWithType("APPROACH");

        when(questionRepository.findByProblemId(1L)).thenReturn(List.of(q1, q2, q3, q4));

        List<Question> result = questionRepository.findByProblemId(1L);

        assertThat(result).hasSize(4);
        assertThat(result).extracting(Question::getQuestionType)
                .containsExactlyInAnyOrder("PATTERN", "COMPLEXITY", "EDGE_CASE", "APPROACH");
    }

    @Test
    void shouldReturnEmptyListWhenNoProblemQuestionsExist() {
        when(questionRepository.findByProblemId(99L)).thenReturn(List.of());

        assertThat(questionRepository.findByProblemId(99L)).isEmpty();
    }

    @Test
    void shouldReturnOnlyQuestionsMatchingTheRequestedType() {
        Question patternQuestion = questionWithType("PATTERN");
        when(questionRepository.findByProblemIdAndQuestionType(1L, "PATTERN"))
                .thenReturn(List.of(patternQuestion));

        List<Question> result = questionRepository.findByProblemIdAndQuestionType(1L, "PATTERN");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuestionType()).isEqualTo("PATTERN");
    }

    @Test
    void shouldReturnEmptyListWhenNoQuestionOfRequestedTypeExists() {
        when(questionRepository.findByProblemIdAndQuestionType(1L, "COMPLEXITY"))
                .thenReturn(List.of());

        assertThat(questionRepository.findByProblemIdAndQuestionType(1L, "COMPLEXITY")).isEmpty();
    }

    @Test
    void shouldReturnTrueWhenQuestionsExistForProblem() {
        when(questionRepository.existsByProblemId(1L)).thenReturn(true);

        assertThat(questionRepository.existsByProblemId(1L)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNoQuestionsHaveBeenGeneratedForProblem() {
        when(questionRepository.existsByProblemId(42L)).thenReturn(false);

        assertThat(questionRepository.existsByProblemId(42L)).isFalse();
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Question questionWithType(String type) {
        Question q = new Question();
        q.setQuestionType(type);
        q.setQuestionText("Sample question?");
        q.setOptionA("A"); q.setOptionB("B"); q.setOptionC("C"); q.setOptionD("D");
        q.setCorrectOption("A");
        q.setExplanation("Because A is correct.");
        Problem p = new Problem();
        p.setId(1L);
        q.setProblem(p);
        return q;
    }
}
