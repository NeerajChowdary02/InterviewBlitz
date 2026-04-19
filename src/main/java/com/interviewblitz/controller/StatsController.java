package com.interviewblitz.controller;

import com.interviewblitz.dto.TopicStatsDto;
import com.interviewblitz.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for retention statistics.
 * All endpoints are read-only — they aggregate data from problems and user_progress.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * Returns a dashboard summary: total problems, how many have been reviewed,
     * how many are due today, overall accuracy, and current correct streak.
     * Example: GET /api/stats/overview
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        return ResponseEntity.ok(statsService.getOverview());
    }

    /**
     * Returns accuracy and review counts broken down by topic (trees, dp, graphs, etc.).
     * Example: GET /api/stats/topics
     */
    @GetMapping("/topics")
    public ResponseEntity<List<TopicStatsDto>> getTopicStats() {
        return ResponseEntity.ok(statsService.getTopicStats());
    }

    /**
     * Returns the 5 topics with the lowest accuracy, worst-first.
     * Only includes topics where at least one question has been attempted.
     * Example: GET /api/stats/weak-areas
     */
    @GetMapping("/weak-areas")
    public ResponseEntity<List<TopicStatsDto>> getWeakAreas() {
        return ResponseEntity.ok(statsService.getWeakAreas());
    }
}
