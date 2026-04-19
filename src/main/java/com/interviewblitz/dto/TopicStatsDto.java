package com.interviewblitz.dto;

/**
 * Aggregated statistics for one topic (trees, dp, graphs, etc.).
 * Used by the /api/stats/topics and /api/stats/weak-areas endpoints.
 */
public class TopicStatsDto {

    private String topic;
    private int totalProblems;
    private int reviewed;
    private double accuracy;
    private int dueToday;

    public TopicStatsDto() {}

    public TopicStatsDto(String topic, int totalProblems, int reviewed,
                         double accuracy, int dueToday) {
        this.topic = topic;
        this.totalProblems = totalProblems;
        this.reviewed = reviewed;
        this.accuracy = accuracy;
        this.dueToday = dueToday;
    }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public int getTotalProblems() { return totalProblems; }
    public void setTotalProblems(int totalProblems) { this.totalProblems = totalProblems; }

    public int getReviewed() { return reviewed; }
    public void setReviewed(int reviewed) { this.reviewed = reviewed; }

    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { this.accuracy = accuracy; }

    public int getDueToday() { return dueToday; }
    public void setDueToday(int dueToday) { this.dueToday = dueToday; }
}
