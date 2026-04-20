package com.interviewblitz.service;

import com.interviewblitz.dto.TopicStatsDto;
import com.interviewblitz.model.Problem;
import com.interviewblitz.model.UserProgress;
import com.interviewblitz.repository.ProblemRepository;
import com.interviewblitz.repository.UserProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes retention statistics across all synced problems.
 *
 * All public methods load exactly two result sets — one for problems and one
 * for user_progress — then join them in memory. With 241 problems this avoids
 * N+1 queries without requiring JOIN FETCH annotations everywhere.
 *
 * Note: accessing up.getProblem().getId() when UserProgress.problem is LAZY
 * is safe inside a @Transactional method because Hibernate can return the FK
 * value from the proxy without issuing a separate SELECT.
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private final ProblemRepository problemRepository;
    private final UserProgressRepository userProgressRepository;

    public StatsService(ProblemRepository problemRepository,
                        UserProgressRepository userProgressRepository) {
        this.problemRepository = problemRepository;
        this.userProgressRepository = userProgressRepository;
    }

    /**
     * Returns a snapshot of overall retention health:
     * total problems, how many have been reviewed at least once, how many
     * are due today, overall accuracy, and the highest current correct streak.
     */
    public Map<String, Object> getOverview() {
        List<UserProgress> allProgress = userProgressRepository.findAll();
        LocalDate today = LocalDate.now();

        long totalProblems = problemRepository.count();

        long totalReviewed = allProgress.stream()
                .filter(up -> up.getTotalAttempts() > 0)
                .count();

        long totalDueToday = allProgress.stream()
                .filter(up -> !up.getNextReviewDate().isAfter(today))
                .count();

        long sumCorrect  = allProgress.stream().mapToLong(UserProgress::getTotalCorrect).sum();
        long sumAttempts = allProgress.stream().mapToLong(UserProgress::getTotalAttempts).sum();
        double overallAccuracy = sumAttempts > 0 ? (double) sumCorrect / sumAttempts : 0.0;

        // currentStreak = best active correct streak across all problems
        int currentStreak = allProgress.stream()
                .mapToInt(UserProgress::getCorrectStreak)
                .max()
                .orElse(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalProblems",   totalProblems);
        result.put("totalReviewed",   totalReviewed);
        result.put("totalDueToday",   totalDueToday);
        result.put("overallAccuracy", Math.round(overallAccuracy * 1000.0) / 1000.0);
        result.put("currentStreak",   currentStreak);
        return result;
    }

    /**
     * Returns per-topic stats: total problems, reviewed count, accuracy, and due today.
     * Topics are sorted alphabetically so the response is stable.
     */
    public List<TopicStatsDto> getTopicStats() {
        // Load both tables once — join in memory
        Map<Long, Problem> problemsById = problemRepository.findAll().stream()
                .collect(Collectors.toMap(Problem::getId, p -> p));

        Map<Long, UserProgress> progressByProblemId = userProgressRepository.findAll().stream()
                .collect(Collectors.toMap(up -> up.getProblem().getId(), up -> up));

        // Group problem IDs by topic
        Map<String, List<Long>> idsByTopic = new LinkedHashMap<>();
        for (Problem p : problemsById.values()) {
            idsByTopic.computeIfAbsent(p.getTopic(), k -> new ArrayList<>()).add(p.getId());
        }

        LocalDate today = LocalDate.now();
        List<TopicStatsDto> result = new ArrayList<>();

        for (Map.Entry<String, List<Long>> entry : idsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<Long> ids = entry.getValue();

            int totalProblems = ids.size();
            int reviewed = 0;
            long sumCorrect = 0;
            long sumAttempts = 0;
            int dueToday = 0;

            for (Long id : ids) {
                UserProgress up = progressByProblemId.get(id);
                if (up != null) {
                    if (up.getTotalAttempts() > 0) reviewed++;
                    sumCorrect  += up.getTotalCorrect();
                    sumAttempts += up.getTotalAttempts();
                    if (!up.getNextReviewDate().isAfter(today)) dueToday++;
                }
            }

            double accuracy = sumAttempts > 0 ? (double) sumCorrect / sumAttempts : 0.0;
            result.add(new TopicStatsDto(topic, totalProblems, reviewed,
                    Math.round(accuracy * 1000.0) / 1000.0, dueToday));
        }

        result.sort(Comparator.comparing(TopicStatsDto::getTopic));
        return result;
    }

    /**
     * Returns up to 5 topics that the user is genuinely struggling with, ordered
     * worst-first. A topic qualifies as weak only when it has been reviewed at least
     * 3 times (enough signal to be meaningful) AND its accuracy is below 70%.
     * Topics with 100% accuracy — or any accuracy ≥ 70% — are never shown as weak,
     * regardless of how many problems remain unreviewed in that topic.
     */
    public List<TopicStatsDto> getWeakAreas() {
        return getTopicStats().stream()
                .filter(dto -> dto.getReviewed() >= 3 && dto.getAccuracy() < 0.70)
                .sorted(Comparator.comparingDouble(TopicStatsDto::getAccuracy))
                .limit(5)
                .collect(Collectors.toList());
    }
}
