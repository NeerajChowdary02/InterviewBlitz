package com.interviewblitz.controller;

import com.interviewblitz.model.Problem;
import com.interviewblitz.model.Question;
import com.interviewblitz.repository.ProblemRepository;
import com.interviewblitz.repository.QuestionRepository;
import com.interviewblitz.service.QuizGenerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for quiz question management.
 * Handles generating questions for a problem and fetching stored questions.
 * Submit and next-due endpoints will be added in Phase 4/5.
 */
@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizGenerationService quizGenerationService;
    private final QuestionRepository questionRepository;
    private final ProblemRepository problemRepository;

    public QuizController(QuizGenerationService quizGenerationService,
                          QuestionRepository questionRepository,
                          ProblemRepository problemRepository) {
        this.quizGenerationService = quizGenerationService;
        this.questionRepository = questionRepository;
        this.problemRepository = problemRepository;
    }

    /**
     * Generates four quiz questions for the given problem using GPT-4o-mini.
     * If questions already exist for this problem they are returned without
     * calling the OpenAI API again.
     * Example: POST /api/quiz/generate/1
     */
    @PostMapping("/generate/{problemId}")
    public ResponseEntity<?> generateQuestions(@PathVariable Long problemId) {
        return problemRepository.findById(problemId)
                .map(problem -> {
                    try {
                        List<Question> questions = quizGenerationService.generateQuestionsForProblem(problem);
                        return ResponseEntity.ok(questions);
                    } catch (RuntimeException e) {
                        return ResponseEntity.internalServerError().body(Map.of(
                                "status", "error",
                                "message", e.getMessage()
                        ));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns all stored questions for a problem without triggering generation.
     * Useful for reviewing questions that were generated in a previous session.
     * Example: GET /api/quiz/questions/1
     */
    @GetMapping("/questions/{problemId}")
    public ResponseEntity<?> getQuestionsForProblem(@PathVariable Long problemId) {
        if (!problemRepository.existsById(problemId)) {
            return ResponseEntity.notFound().build();
        }
        List<Question> questions = questionRepository.findByProblemId(problemId);
        if (questions.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "message", "No questions generated yet for this problem. Use POST /api/quiz/generate/" + problemId,
                    "questions", List.of()
            ));
        }
        return ResponseEntity.ok(questions);
    }
}
