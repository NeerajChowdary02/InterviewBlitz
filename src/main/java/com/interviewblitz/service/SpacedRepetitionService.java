package com.interviewblitz.service;

import com.interviewblitz.dto.SubmitAnswerResponse;
import com.interviewblitz.model.Problem;
import com.interviewblitz.model.QuizAttempt;
import com.interviewblitz.model.UserProgress;
import com.interviewblitz.repository.ProblemRepository;
import com.interviewblitz.repository.QuizAttemptRepository;
import com.interviewblitz.repository.QuestionRepository;
import com.interviewblitz.repository.UserProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements the SM-2 spaced-repetition algorithm.
 *
 * When a user answers a question, this service:
 *   1. Checks if the selected option is correct.
 *   2. Derives a quality score (4 = correct, 1 = wrong).
 *   3. Applies the SM-2 formulas to update interval and ease factor.
 *   4. Sets the next review date to today + new interval.
 *   5. Persists a QuizAttempt record and the updated UserProgress.
 *
 * SM-2 interval rules:
 *   - First correct answer  (repetitions == 0): interval = 1 day
 *   - Second correct answer (repetitions == 1): interval = 6 days
 *   - Subsequent correct answers: interval = round(previous_interval × ease_factor)
 *   - Wrong answer: reset interval to 1, reset repetitions to 0, keep ease factor
 *
 * SM-2 ease factor formula (applied only on correct answers):
 *   EF' = max(1.3, EF + 0.1 − (5 − quality) × (0.08 + (5 − quality) × 0.02))
 */
@Service
public class SpacedRepetitionService {

    private static final Logger log = LoggerFactory.getLogger(SpacedRepetitionService.class);

    // SM-2 minimum ease factor — the algorithm breaks below this value
    private static final double MIN_EASE_FACTOR = 1.3;

    // Quality scores: multiple-choice only captures right/wrong, no partial credit
    private static final int QUALITY_CORRECT = 4;
    private static final int QUALITY_WRONG   = 1;

    private final QuestionRepository questionRepository;
    private final UserProgressRepository userProgressRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final ProblemRepository problemRepository;

    public SpacedRepetitionService(QuestionRepository questionRepository,
                                   UserProgressRepository userProgressRepository,
                                   QuizAttemptRepository quizAttemptRepository,
                                   ProblemRepository problemRepository) {
        this.questionRepository = questionRepository;
        this.userProgressRepository = userProgressRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.problemRepository = problemRepository;
    }

    /**
     * Processes a single answer submission.
     * Updates the UserProgress for the question's problem using SM-2 and
     * writes a QuizAttempt record. Returns the result shown to the user.
     */
    @Transactional
    public SubmitAnswerResponse processAnswer(Long questionId, String selectedOption) {
        var question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

        boolean correct = selectedOption.trim().equalsIgnoreCase(question.getCorrectOption());
        int quality = correct ? QUALITY_CORRECT : QUALITY_WRONG;

        // Get or create the UserProgress record for this question's problem
        Long problemId = question.getProblem().getId();
        UserProgress progress = userProgressRepository.findByProblemId(problemId)
                .orElseGet(() -> {
                    UserProgress fresh = new UserProgress();
                    fresh.setProblem(question.getProblem());
                    return fresh;
                });

        applySmTwo(progress, quality, correct);
        userProgressRepository.save(progress);

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuestion(question);
        attempt.setSelectedOption(selectedOption.trim().toUpperCase());
        attempt.setCorrect(correct);
        quizAttemptRepository.save(attempt);

        log.info("Processed answer for question {} (problem {}): correct={}, nextReview={}",
                questionId, problemId, correct, progress.getNextReviewDate());

        return new SubmitAnswerResponse(
                correct,
                question.getCorrectOption(),
                question.getExplanation(),
                progress.getNextReviewDate()
        );
    }

    /**
     * Applies the SM-2 algorithm to the given progress record in place.
     * The caller is responsible for saving the modified record.
     * Package-visible so unit tests can call it directly without mocking repositories.
     */
    void applySmTwo(UserProgress progress, int quality, boolean correct) {
        progress.setTotalAttempts(progress.getTotalAttempts() + 1);

        if (correct) {
            progress.setTotalCorrect(progress.getTotalCorrect() + 1);
            progress.setCorrectStreak(progress.getCorrectStreak() + 1);

            int newInterval = computeNewInterval(progress);
            double newEaseFactor = computeNewEaseFactor(progress.getEaseFactor(), quality);

            progress.setIntervalDays(newInterval);
            progress.setEaseFactor(newEaseFactor);
            progress.setRepetitions(progress.getRepetitions() + 1);

        } else {
            // Wrong answer: reset scheduling state but leave ease factor unchanged (per spec)
            progress.setCorrectStreak(0);
            progress.setRepetitions(0);
            progress.setIntervalDays(1);
        }

        progress.setNextReviewDate(LocalDate.now().plusDays(progress.getIntervalDays()));
        progress.setLastReviewedAt(LocalDateTime.now());
    }

    /**
     * Computes the next interval in days based on the SM-2 repetition schedule.
     * Uses the current intervalDays and repetitions count from progress.
     */
    int computeNewInterval(UserProgress progress) {
        return switch (progress.getRepetitions()) {
            case 0 -> 1;
            case 1 -> 6;
            default -> (int) Math.round(progress.getIntervalDays() * progress.getEaseFactor());
        };
    }

    /**
     * Computes the updated ease factor using the SM-2 formula.
     * The result is clamped to a minimum of 1.3 to keep intervals meaningful.
     */
    double computeNewEaseFactor(double currentEf, int quality) {
        double delta = 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02);
        return Math.max(MIN_EASE_FACTOR, currentEf + delta);
    }

    /**
     * Ensures every synced problem has a UserProgress record.
     * Called lazily on the first /api/quiz/next request so the review queue
     * is immediately populated without a manual setup step.
     */
    @Transactional
    public void ensureAllProblemsHaveProgress() {
        long progressCount = userProgressRepository.count();
        long problemCount = problemRepository.count();

        if (progressCount >= problemCount) {
            return; // Already initialised — nothing to do
        }

        // Find which problem IDs already have a progress record
        Set<Long> alreadyTracked = userProgressRepository.findAll().stream()
                .map(up -> up.getProblem().getId())
                .collect(Collectors.toSet());

        List<UserProgress> newRecords = problemRepository.findAll().stream()
                .filter(p -> !alreadyTracked.contains(p.getId()))
                .map(this::freshProgressFor)
                .collect(Collectors.toList());

        if (!newRecords.isEmpty()) {
            userProgressRepository.saveAll(newRecords);
            log.info("Initialised UserProgress for {} problems (total synced: {})",
                    newRecords.size(), problemCount);
        }
    }

    /** Creates a brand-new UserProgress for a problem — defaults set by the entity @PrePersist. */
    private UserProgress freshProgressFor(Problem problem) {
        UserProgress up = new UserProgress();
        up.setProblem(problem);
        return up;
    }
}
