package com.interviewblitz.controller;

import com.interviewblitz.dto.SubmitAnswerRequest;
import com.interviewblitz.dto.SubmitAnswerResponse;
import com.interviewblitz.model.Problem;
import com.interviewblitz.model.UserProgress;
import com.interviewblitz.repository.UserProgressRepository;
import com.interviewblitz.service.SpacedRepetitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the live quiz session — submitting answers, fetching the
 * next due problem, and checking how many problems are due today.
 */
@RestController
@RequestMapping("/api/quiz")
public class QuizSessionController {

    private final SpacedRepetitionService spacedRepetitionService;
    private final UserProgressRepository userProgressRepository;

    public QuizSessionController(SpacedRepetitionService spacedRepetitionService,
                                 UserProgressRepository userProgressRepository) {
        this.spacedRepetitionService = spacedRepetitionService;
        this.userProgressRepository = userProgressRepository;
    }

    /**
     * Accepts a question answer, runs the SM-2 algorithm, and returns the result.
     * Response includes whether the answer was correct, the correct option,
     * the explanation, and when the problem will next appear for review.
     * Example: POST /api/quiz/submit  body: {"questionId": 1, "selectedOption": "B"}
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitAnswer(@RequestBody SubmitAnswerRequest request) {
        if (request.getQuestionId() == null || request.getSelectedOption() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "questionId and selectedOption are required"
            ));
        }
        try {
            SubmitAnswerResponse response = spacedRepetitionService
                    .processAnswer(request.getQuestionId(), request.getSelectedOption());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Returns the next problem due for review — the one with the earliest nextReviewDate
     * that is today or before. On the first call, initialises UserProgress for all
     * synced problems so the full queue is available immediately.
     * Example: GET /api/quiz/next
     */
    @GetMapping("/next")
    public ResponseEntity<?> getNextDueProblem() {
        // Lazy initialisation — runs only when counts differ (i.e. the first time)
        spacedRepetitionService.ensureAllProblemsHaveProgress();

        List<UserProgress> due = userProgressRepository
                .findAllByNextReviewDateLessThanEqualOrderByNextReviewDateAsc(LocalDate.now());

        if (due.isEmpty()) {
            // Nothing is due — tell the user when the next problem comes up
            return userProgressRepository.findAll().stream()
                    .map(UserProgress::getNextReviewDate)
                    .min(Comparator.naturalOrder())
                    .map(nextDate -> ResponseEntity.ok(Map.of(
                            "message", "All caught up! No problems due today.",
                            "nextReviewDate", nextDate.toString()
                    )))
                    .orElse(ResponseEntity.ok(Map.of("message", "No problems synced yet.")));
        }

        Problem next = due.get(0).getProblem();
        return ResponseEntity.ok(next);
    }

    /**
     * Returns a count of how many problems are due for review today or are overdue.
     * Example: GET /api/quiz/due-count
     */
    @GetMapping("/due-count")
    public ResponseEntity<Map<String, Long>> getDueCount() {
        long count = userProgressRepository.countByNextReviewDateLessThanEqual(LocalDate.now());
        return ResponseEntity.ok(Map.of("dueToday", count));
    }
}
