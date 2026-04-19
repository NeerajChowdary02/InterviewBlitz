package com.interviewblitz.service;

import com.interviewblitz.dto.SubmitAnswerResponse;
import com.interviewblitz.model.Problem;
import com.interviewblitz.model.Question;
import com.interviewblitz.model.QuizAttempt;
import com.interviewblitz.model.UserProgress;
import com.interviewblitz.repository.ProblemRepository;
import com.interviewblitz.repository.QuestionRepository;
import com.interviewblitz.repository.QuizAttemptRepository;
import com.interviewblitz.repository.UserProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SpacedRepetitionService.
 * Exercises the SM-2 algorithm, correct/wrong detection, interval calculation,
 * ease factor clamping, streak tracking, and QuizAttempt persistence.
 */
@ExtendWith(MockitoExtension.class)
class SpacedRepetitionServiceTest {

    @Mock private QuestionRepository questionRepository;
    @Mock private UserProgressRepository userProgressRepository;
    @Mock private QuizAttemptRepository quizAttemptRepository;
    @Mock private ProblemRepository problemRepository;

    private SpacedRepetitionService service;

    @BeforeEach
    void setUp() {
        service = new SpacedRepetitionService(
                questionRepository, userProgressRepository,
                quizAttemptRepository, problemRepository);
    }

    // -----------------------------------------------------------------------
    // Correct / wrong detection
    // -----------------------------------------------------------------------

    @Test
    void shouldMarkAnswerCorrectWhenSelectedOptionMatchesCorrectOption() {
        Question question = questionWithCorrectOption("B");
        UserProgress progress = freshProgress();

        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(userProgressRepository.findByProblemId(any())).thenReturn(Optional.of(progress));
        when(userProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubmitAnswerResponse response = service.processAnswer(1L, "B");

        assertThat(response.isCorrect()).isTrue();
    }

    @Test
    void shouldMarkAnswerWrongWhenSelectedOptionDoesNotMatchCorrectOption() {
        Question question = questionWithCorrectOption("B");
        UserProgress progress = freshProgress();

        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(userProgressRepository.findByProblemId(any())).thenReturn(Optional.of(progress));
        when(userProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubmitAnswerResponse response = service.processAnswer(1L, "A");

        assertThat(response.isCorrect()).isFalse();
    }

    @Test
    void shouldBeCaseInsensitiveWhenComparingSelectedOptionToCorrectOption() {
        Question question = questionWithCorrectOption("C");
        UserProgress progress = freshProgress();

        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(userProgressRepository.findByProblemId(any())).thenReturn(Optional.of(progress));
        when(userProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubmitAnswerResponse response = service.processAnswer(1L, "c");

        assertThat(response.isCorrect()).isTrue();
    }

    // -----------------------------------------------------------------------
    // SM-2 interval calculation
    // -----------------------------------------------------------------------

    @Test
    void shouldSetIntervalToOneDayOnFirstCorrectAnswer() {
        UserProgress progress = freshProgress(); // repetitions = 0

        service.applySmTwo(progress, 4, true);

        assertThat(progress.getIntervalDays()).isEqualTo(1);
    }

    @Test
    void shouldSetIntervalToSixDaysOnSecondCorrectAnswer() {
        UserProgress progress = freshProgress();
        progress.setRepetitions(1);
        progress.setIntervalDays(1);

        service.applySmTwo(progress, 4, true);

        assertThat(progress.getIntervalDays()).isEqualTo(6);
    }

    @Test
    void shouldMultiplyIntervalByEaseFactorOnThirdCorrectAnswer() {
        UserProgress progress = freshProgress();
        progress.setRepetitions(2);
        progress.setIntervalDays(6);
        progress.setEaseFactor(2.5);

        service.applySmTwo(progress, 4, true);

        // round(6 * 2.5) = 15
        assertThat(progress.getIntervalDays()).isEqualTo(15);
    }

    @Test
    void shouldMultiplyByUpdatedEaseFactorOnSubsequentCorrectAnswers() {
        UserProgress progress = freshProgress();
        progress.setRepetitions(3);
        progress.setIntervalDays(15);
        progress.setEaseFactor(2.5);

        service.applySmTwo(progress, 4, true);

        // round(15 * 2.5) = 38
        assertThat(progress.getIntervalDays()).isEqualTo(38);
    }

    @Test
    void shouldResetIntervalToOneDayOnWrongAnswer() {
        UserProgress progress = freshProgress();
        progress.setRepetitions(3);
        progress.setIntervalDays(15);

        service.applySmTwo(progress, 1, false);

        assertThat(progress.getIntervalDays()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // SM-2 repetitions counter
    // -----------------------------------------------------------------------

    @Test
    void shouldIncrementRepetitionsOnCorrectAnswer() {
        UserProgress progress = freshProgress(); // repetitions = 0

        service.applySmTwo(progress, 4, true);

        assertThat(progress.getRepetitions()).isEqualTo(1);
    }

    @Test
    void shouldResetRepetitionsToZeroOnWrongAnswer() {
        UserProgress progress = freshProgress();
        progress.setRepetitions(4);

        service.applySmTwo(progress, 1, false);

        assertThat(progress.getRepetitions()).isZero();
    }

    // -----------------------------------------------------------------------
    // SM-2 ease factor
    // -----------------------------------------------------------------------

    @Test
    void shouldNotChangeEaseFactorOnWrongAnswerPerSpec() {
        // Spec says "keep easeFactor" on wrong answers
        UserProgress progress = freshProgress();
        progress.setEaseFactor(2.5);

        service.applySmTwo(progress, 1, false);

        assertThat(progress.getEaseFactor()).isEqualTo(2.5);
    }

    @Test
    void shouldIncreaseEaseFactorWhenQualityIsFive() {
        double before = 2.5;
        double result = service.computeNewEaseFactor(before, 5);

        // quality=5: delta = 0.1 − 0 = +0.1
        assertThat(result).isCloseTo(2.6, within(0.0001));
    }

    @Test
    void shouldLeaveEaseFactorUnchangedWhenQualityIsFour() {
        double before = 2.5;
        double result = service.computeNewEaseFactor(before, 4);

        // quality=4: delta = 0.1 − 1*(0.08+0.02) = 0
        assertThat(result).isCloseTo(2.5, within(0.0001));
    }

    @Test
    void shouldNeverLetEaseFactorDropBelowOnePointThree() {
        // Start at minimum and apply a very low quality score
        double result = service.computeNewEaseFactor(1.3, 3);

        assertThat(result).isGreaterThanOrEqualTo(1.3);
    }

    @Test
    void shouldClampEaseFactorToOnePointThreeWhenFormulaWouldGoBelowMinimum() {
        // quality=3: delta = 0.1 − 2*(0.08+0.04) = 0.1 − 0.24 = −0.14
        // Starting from 1.35, result would be 1.21 → clamped to 1.3
        double result = service.computeNewEaseFactor(1.35, 3);

        assertThat(result).isEqualTo(1.3);
    }

    // -----------------------------------------------------------------------
    // computeNewInterval
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnOneForZeroRepetitions() {
        UserProgress progress = freshProgress(); // rep=0
        assertThat(service.computeNewInterval(progress)).isEqualTo(1);
    }

    @Test
    void shouldReturnSixForOneRepetition() {
        UserProgress progress = freshProgress();
        progress.setRepetitions(1);
        assertThat(service.computeNewInterval(progress)).isEqualTo(6);
    }

    @Test
    void shouldRoundIntervalWhenMultiplyingByEaseFactor() {
        UserProgress progress = freshProgress();
        progress.setRepetitions(2);
        progress.setIntervalDays(4);
        progress.setEaseFactor(2.5);

        // round(4 * 2.5) = 10
        assertThat(service.computeNewInterval(progress)).isEqualTo(10);
    }

    // -----------------------------------------------------------------------
    // Next review date
    // -----------------------------------------------------------------------

    @Test
    void shouldSetNextReviewDateToTodayPlusIntervalDays() {
        UserProgress progress = freshProgress(); // rep=0

        service.applySmTwo(progress, 4, true);

        // First correct → interval = 1 day
        assertThat(progress.getNextReviewDate()).isEqualTo(LocalDate.now().plusDays(1));
    }

    @Test
    void shouldSetNextReviewDateToTomorrowOnWrongAnswer() {
        UserProgress progress = freshProgress();

        service.applySmTwo(progress, 1, false);

        assertThat(progress.getNextReviewDate()).isEqualTo(LocalDate.now().plusDays(1));
    }

    // -----------------------------------------------------------------------
    // Streak and counters
    // -----------------------------------------------------------------------

    @Test
    void shouldIncrementCorrectStreakOnCorrectAnswer() {
        UserProgress progress = freshProgress();
        progress.setCorrectStreak(3);

        service.applySmTwo(progress, 4, true);

        assertThat(progress.getCorrectStreak()).isEqualTo(4);
    }

    @Test
    void shouldResetCorrectStreakToZeroOnWrongAnswer() {
        UserProgress progress = freshProgress();
        progress.setCorrectStreak(5);

        service.applySmTwo(progress, 1, false);

        assertThat(progress.getCorrectStreak()).isZero();
    }

    @Test
    void shouldIncrementTotalAttemptsOnEveryAnswer() {
        UserProgress progress = freshProgress();
        progress.setTotalAttempts(7);

        service.applySmTwo(progress, 4, true);
        assertThat(progress.getTotalAttempts()).isEqualTo(8);

        service.applySmTwo(progress, 1, false);
        assertThat(progress.getTotalAttempts()).isEqualTo(9);
    }

    @Test
    void shouldIncrementTotalCorrectOnlyOnCorrectAnswer() {
        UserProgress progress = freshProgress();
        progress.setTotalCorrect(3);

        service.applySmTwo(progress, 4, true);
        assertThat(progress.getTotalCorrect()).isEqualTo(4);

        service.applySmTwo(progress, 1, false);
        assertThat(progress.getTotalCorrect()).isEqualTo(4); // unchanged
    }

    // -----------------------------------------------------------------------
    // QuizAttempt persistence
    // -----------------------------------------------------------------------

    @Test
    void shouldSaveQuizAttemptAfterProcessingAnswer() {
        Question question = questionWithCorrectOption("A");
        UserProgress progress = freshProgress();

        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(userProgressRepository.findByProblemId(any())).thenReturn(Optional.of(progress));
        when(userProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processAnswer(1L, "A");

        ArgumentCaptor<QuizAttempt> captor = ArgumentCaptor.forClass(QuizAttempt.class);
        verify(quizAttemptRepository).save(captor.capture());

        QuizAttempt saved = captor.getValue();
        assertThat(saved.getSelectedOption()).isEqualTo("A");
        assertThat(saved.isCorrect()).isTrue();
    }

    @Test
    void shouldIncludeExplanationAndCorrectOptionInResponse() {
        Question question = questionWithCorrectOption("C");
        question.setExplanation("Because C is the optimal approach.");
        UserProgress progress = freshProgress();

        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(userProgressRepository.findByProblemId(any())).thenReturn(Optional.of(progress));
        when(userProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubmitAnswerResponse response = service.processAnswer(1L, "A");

        assertThat(response.getCorrectOption()).isEqualTo("C");
        assertThat(response.getExplanation()).isEqualTo("Because C is the optimal approach.");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private UserProgress freshProgress() {
        Problem problem = new Problem();
        problem.setId(1L);
        UserProgress up = new UserProgress();
        up.setProblem(problem);
        up.setNextReviewDate(LocalDate.now());
        return up;
    }

    private Question questionWithCorrectOption(String correctOption) {
        Problem problem = new Problem();
        problem.setId(1L);
        Question q = new Question();
        q.setId(1L);
        q.setProblem(problem);
        q.setCorrectOption(correctOption);
        q.setExplanation("Sample explanation.");
        q.setQuestionText("Sample question?");
        q.setOptionA("A"); q.setOptionB("B"); q.setOptionC("C"); q.setOptionD("D");
        return q;
    }
}
