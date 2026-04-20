package com.interviewblitz.controller;

import com.interviewblitz.model.Problem;
import com.interviewblitz.repository.ProblemRepository;
import com.interviewblitz.service.LeetCodeSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for problem management.
 * Handles syncing problems from LeetCode and querying them by topic or difficulty.
 */
@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final LeetCodeSyncService leetCodeSyncService;
    private final ProblemRepository problemRepository;

    public ProblemController(LeetCodeSyncService leetCodeSyncService,
                             ProblemRepository problemRepository) {
        this.leetCodeSyncService = leetCodeSyncService;
        this.problemRepository = problemRepository;
    }

    /**
     * Triggers a sync of all solved problems for the given LeetCode username.
     * Returns how many new problems were added to the database.
     * Example: GET /api/problems/sync/codingknight2625
     */
    @GetMapping("/sync/{username}")
    public ResponseEntity<Map<String, Object>> syncProblems(@PathVariable String username) {
        try {
            int newCount = leetCodeSyncService.syncProblemsFromLeetCode(username);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "username", username,
                    "newProblemsSynced", newCount
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Returns all problems currently stored in the database.
     * Example: GET /api/problems
     */
    @GetMapping
    public ResponseEntity<List<Problem>> getAllProblems() {
        return ResponseEntity.ok(problemRepository.findAll());
    }

    /**
     * Returns problems grouped by topic as a map of topic → list of problems.
     * Useful for the stats dashboard to show counts per topic.
     * Example: GET /api/problems/topics
     */
    @GetMapping("/topics")
    public ResponseEntity<Map<String, List<Problem>>> getProblemsByTopic() {
        List<Problem> allProblems = problemRepository.findAll();
        Map<String, List<Problem>> groupedByTopic = allProblems.stream()
                .collect(Collectors.groupingBy(Problem::getTopic));
        return ResponseEntity.ok(groupedByTopic);
    }

    /**
     * Re-fetches tag data from LeetCode for the given username and re-applies the
     * current priority-ordered topic mapping to all existing DB rows. No new problems
     * are inserted — only the topic field of already-synced problems is updated.
     * Use this whenever the mapping logic changes to backfill existing rows correctly.
     * Example: PUT /api/problems/sync/topics/codingknight2625
     */
    @PutMapping("/sync/topics/{username}")
    public ResponseEntity<Map<String, Object>> remapTopics(@PathVariable String username) {
        try {
            int updated = leetCodeSyncService.remapTopicsFromLeetCode(username);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "username", username,
                    "problemsUpdated", updated
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }
}
