package com.interviewblitz.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a LeetCode problem that has been synced from the user's solved list.
 * Each row in this table is one distinct problem the user has previously solved.
 * The topic field stores a simplified category (e.g. "trees", "dp") derived from
 * LeetCode's raw topic tags.
 */
@Entity
@Table(name = "problems")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LeetCode's own numeric ID for this problem — used as the deduplication key. */
    @Column(name = "leetcode_id", unique = true, nullable = false)
    private Integer leetcodeId;

    @Column(name = "title", nullable = false)
    private String title;

    /** Easy, Medium, or Hard — as returned by the LeetCode API. */
    @Column(name = "difficulty", nullable = false, length = 10)
    private String difficulty;

    /** Simplified topic category: trees, dp, graphs, arrays, etc. */
    @Column(name = "topic", nullable = false, length = 50)
    private String topic;

    /** Full problem description/statement from LeetCode. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** High-level approach to solving this problem — used as context for quiz generation. */
    @Column(name = "solution_approach", columnDefinition = "TEXT")
    private String solutionApproach;

    /** Timestamp when this problem was pulled from the LeetCode API. */
    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    // Default constructor required by JPA
    public Problem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getLeetcodeId() { return leetcodeId; }
    public void setLeetcodeId(Integer leetcodeId) { this.leetcodeId = leetcodeId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSolutionApproach() { return solutionApproach; }
    public void setSolutionApproach(String solutionApproach) { this.solutionApproach = solutionApproach; }

    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }
}
