package com.interviewblitz.service;

import com.interviewblitz.dto.TopicStatsDto;
import com.interviewblitz.model.Problem;
import com.interviewblitz.model.UserProgress;
import com.interviewblitz.repository.ProblemRepository;
import com.interviewblitz.repository.UserProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StatsService.
 * Verifies overview aggregation, per-topic accuracy, weak-area ranking,
 * and edge cases such as zero attempts.
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock private ProblemRepository problemRepository;
    @Mock private UserProgressRepository userProgressRepository;

    private StatsService statsService;

    @BeforeEach
    void setUp() {
        statsService = new StatsService(problemRepository, userProgressRepository);
    }

    // -----------------------------------------------------------------------
    // Overview: total counts
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnCorrectTotalProblemCount() {
        when(problemRepository.count()).thenReturn(241L);
        when(userProgressRepository.findAll()).thenReturn(List.of());

        Map<String, Object> overview = statsService.getOverview();

        assertThat(overview.get("totalProblems")).isEqualTo(241L);
    }

    @Test
    void shouldCountOnlyProblemsWithAttemptsAsReviewed() {
        UserProgress reviewed   = progressWith(10, 7, 2.5, 0);  // 10 attempts
        UserProgress unreviewed = progressWith(0,  0, 2.5, 0);  // 0 attempts

        when(problemRepository.count()).thenReturn(2L);
        when(userProgressRepository.findAll()).thenReturn(List.of(reviewed, unreviewed));

        Map<String, Object> overview = statsService.getOverview();

        assertThat(overview.get("totalReviewed")).isEqualTo(1L);
    }

    @Test
    void shouldCountProblemsDueTodayOrOverdue() {
        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow  = today.plusDays(1);

        UserProgress dueToday    = progressWith(5, 3, 2.5, 0);
        dueToday.setNextReviewDate(today);
        UserProgress overdue     = progressWith(5, 3, 2.5, 0);
        overdue.setNextReviewDate(yesterday);
        UserProgress notDueYet   = progressWith(5, 3, 2.5, 0);
        notDueYet.setNextReviewDate(tomorrow);

        when(problemRepository.count()).thenReturn(3L);
        when(userProgressRepository.findAll()).thenReturn(List.of(dueToday, overdue, notDueYet));

        Map<String, Object> overview = statsService.getOverview();

        assertThat(overview.get("totalDueToday")).isEqualTo(2L);
    }

    // -----------------------------------------------------------------------
    // Overview: accuracy
    // -----------------------------------------------------------------------

    @Test
    void shouldCalculateOverallAccuracyAsTotalCorrectDividedByTotalAttempts() {
        UserProgress p1 = progressWith(10, 8, 2.5, 0);  // 80%
        UserProgress p2 = progressWith(10, 6, 2.5, 0);  // 60%
        // Combined: 14 correct / 20 attempts = 70%

        when(problemRepository.count()).thenReturn(2L);
        when(userProgressRepository.findAll()).thenReturn(List.of(p1, p2));

        Map<String, Object> overview = statsService.getOverview();

        assertThat((double) overview.get("overallAccuracy")).isCloseTo(0.7, within(0.001));
    }

    @Test
    void shouldReturnZeroAccuracyWhenNoAttemptsHaveBeenMade() {
        when(problemRepository.count()).thenReturn(1L);
        when(userProgressRepository.findAll()).thenReturn(List.of(progressWith(0, 0, 2.5, 0)));

        Map<String, Object> overview = statsService.getOverview();

        assertThat(overview.get("overallAccuracy")).isEqualTo(0.0);
    }

    // -----------------------------------------------------------------------
    // Overview: current streak
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnHighestCorrectStreakAsCurrentStreak() {
        UserProgress p1 = progressWith(10, 8, 2.5, 3);
        UserProgress p2 = progressWith(10, 6, 2.5, 7);
        UserProgress p3 = progressWith(10, 5, 2.5, 1);

        when(problemRepository.count()).thenReturn(3L);
        when(userProgressRepository.findAll()).thenReturn(List.of(p1, p2, p3));

        Map<String, Object> overview = statsService.getOverview();

        assertThat(overview.get("currentStreak")).isEqualTo(7);
    }

    // -----------------------------------------------------------------------
    // Topic stats
    // -----------------------------------------------------------------------

    @Test
    void shouldGroupProblemsByTopicCorrectly() {
        Problem treesProblem1 = problem(1L, "trees");
        Problem treesProblem2 = problem(2L, "trees");
        Problem dpProblem     = problem(3L, "dp");

        when(problemRepository.findAll()).thenReturn(List.of(treesProblem1, treesProblem2, dpProblem));
        when(userProgressRepository.findAll()).thenReturn(List.of());

        List<TopicStatsDto> stats = statsService.getTopicStats();

        assertThat(stats).hasSize(2);
        TopicStatsDto treesDto = stats.stream()
                .filter(d -> "trees".equals(d.getTopic())).findFirst().orElseThrow();
        assertThat(treesDto.getTotalProblems()).isEqualTo(2);

        TopicStatsDto dpDto = stats.stream()
                .filter(d -> "dp".equals(d.getTopic())).findFirst().orElseThrow();
        assertThat(dpDto.getTotalProblems()).isEqualTo(1);
    }

    @Test
    void shouldCalculateAccuracyPerTopicCorrectly() {
        Problem p1 = problem(1L, "trees");
        Problem p2 = problem(2L, "trees");

        UserProgress up1 = progressForProblem(p1, 10, 9, 2.5, 0); // 90%
        UserProgress up2 = progressForProblem(p2, 10, 5, 2.5, 0); // 50%
        // Trees combined: 14/20 = 70%

        when(problemRepository.findAll()).thenReturn(List.of(p1, p2));
        when(userProgressRepository.findAll()).thenReturn(List.of(up1, up2));

        List<TopicStatsDto> stats = statsService.getTopicStats();

        TopicStatsDto trees = stats.get(0);
        assertThat(trees.getAccuracy()).isCloseTo(0.7, within(0.001));
    }

    @Test
    void shouldCountReviewedProblemsPerTopicCorrectly() {
        Problem p1 = problem(1L, "arrays");
        Problem p2 = problem(2L, "arrays");

        UserProgress reviewed   = progressForProblem(p1, 5, 3, 2.5, 0);
        UserProgress unreviewed = progressForProblem(p2, 0, 0, 2.5, 0);

        when(problemRepository.findAll()).thenReturn(List.of(p1, p2));
        when(userProgressRepository.findAll()).thenReturn(List.of(reviewed, unreviewed));

        List<TopicStatsDto> stats = statsService.getTopicStats();

        assertThat(stats.get(0).getReviewed()).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroAccuracyForTopicWithNoAttempts() {
        Problem p = problem(1L, "greedy");
        when(problemRepository.findAll()).thenReturn(List.of(p));
        when(userProgressRepository.findAll()).thenReturn(List.of());

        List<TopicStatsDto> stats = statsService.getTopicStats();

        assertThat(stats.get(0).getAccuracy()).isZero();
    }

    // -----------------------------------------------------------------------
    // Weak areas
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnWeakAreasOrderedByLowestAccuracyFirst() {
        // 3 problems per topic so reviewed == 3 (meets the minimum-sample threshold)
        Problem t1 = problem(1L, "trees"); Problem t2 = problem(2L, "trees"); Problem t3 = problem(3L, "trees");
        Problem g1 = problem(4L, "graphs"); Problem g2 = problem(5L, "graphs"); Problem g3 = problem(6L, "graphs");

        // trees ≈ 30%, graphs ≈ 50% — both below 70% threshold
        List<UserProgress> progress = List.of(
                progressForProblem(t1, 10, 3, 2.5, 0),
                progressForProblem(t2, 10, 3, 2.5, 0),
                progressForProblem(t3, 10, 3, 2.5, 0),
                progressForProblem(g1, 10, 5, 2.5, 0),
                progressForProblem(g2, 10, 5, 2.5, 0),
                progressForProblem(g3, 10, 5, 2.5, 0));

        when(problemRepository.findAll()).thenReturn(List.of(t1, t2, t3, g1, g2, g3));
        when(userProgressRepository.findAll()).thenReturn(progress);

        List<TopicStatsDto> weakAreas = statsService.getWeakAreas();

        assertThat(weakAreas).hasSize(2);
        assertThat(weakAreas.get(0).getTopic()).isEqualTo("trees");   // 30% — worst
        assertThat(weakAreas.get(1).getTopic()).isEqualTo("graphs");  // 50%
    }

    @Test
    void shouldExcludeTopicsWithHighAccuracyFromWeakAreas() {
        // Two topics, both with 3 reviewed problems — only the one below 70% should appear
        Problem lo1 = problem(1L, "trees"); Problem lo2 = problem(2L, "trees"); Problem lo3 = problem(3L, "trees");
        Problem hi1 = problem(4L, "dp");   Problem hi2 = problem(5L, "dp");   Problem hi3 = problem(6L, "dp");

        List<UserProgress> progress = List.of(
                progressForProblem(lo1, 10, 4, 2.5, 0),   // trees: 40%
                progressForProblem(lo2, 10, 4, 2.5, 0),
                progressForProblem(lo3, 10, 4, 2.5, 0),
                progressForProblem(hi1, 10, 8, 2.5, 0),   // dp: 80% — not weak
                progressForProblem(hi2, 10, 8, 2.5, 0),
                progressForProblem(hi3, 10, 8, 2.5, 0));

        when(problemRepository.findAll()).thenReturn(List.of(lo1, lo2, lo3, hi1, hi2, hi3));
        when(userProgressRepository.findAll()).thenReturn(progress);

        List<TopicStatsDto> weakAreas = statsService.getWeakAreas();

        assertThat(weakAreas).hasSize(1);
        assertThat(weakAreas.get(0).getTopic()).isEqualTo("trees");
    }

    @Test
    void shouldExcludeTopicsWithFewerThanThreeReviewedProblems() {
        // Only 2 problems reviewed — not enough signal, should not appear as weak
        Problem p1 = problem(1L, "trees");
        Problem p2 = problem(2L, "trees");

        List<UserProgress> progress = List.of(
                progressForProblem(p1, 10, 1, 2.5, 0),   // 10% accuracy — would qualify if reviewed >= 3
                progressForProblem(p2, 10, 1, 2.5, 0));

        when(problemRepository.findAll()).thenReturn(List.of(p1, p2));
        when(userProgressRepository.findAll()).thenReturn(progress);

        assertThat(statsService.getWeakAreas()).isEmpty();
    }

    @Test
    void shouldExcludeTopicsWithNoAttemptsFromWeakAreas() {
        // 3 problems in trees (all reviewed, low accuracy) and 3 in dp (none attempted)
        Problem t1 = problem(1L, "trees"); Problem t2 = problem(2L, "trees"); Problem t3 = problem(3L, "trees");
        Problem d1 = problem(4L, "dp");    Problem d2 = problem(5L, "dp");    Problem d3 = problem(6L, "dp");

        List<UserProgress> progress = List.of(
                progressForProblem(t1, 5, 2, 2.5, 0),
                progressForProblem(t2, 5, 2, 2.5, 0),
                progressForProblem(t3, 5, 2, 2.5, 0),
                progressForProblem(d1, 0, 0, 2.5, 0),
                progressForProblem(d2, 0, 0, 2.5, 0),
                progressForProblem(d3, 0, 0, 2.5, 0));

        when(problemRepository.findAll()).thenReturn(List.of(t1, t2, t3, d1, d2, d3));
        when(userProgressRepository.findAll()).thenReturn(progress);

        List<TopicStatsDto> weakAreas = statsService.getWeakAreas();

        assertThat(weakAreas).hasSize(1);
        assertThat(weakAreas.get(0).getTopic()).isEqualTo("trees");
    }

    @Test
    void shouldLimitWeakAreasToFiveTopics() {
        // 7 topics each with 3 reviewed problems at 50% accuracy (all qualify as weak)
        String[] topics = {"trees", "dp", "graphs", "arrays", "stacks", "heaps", "greedy"};
        List<Problem> problems = new java.util.ArrayList<>();
        List<UserProgress> progress = new java.util.ArrayList<>();
        long id = 1L;
        for (String topic : topics) {
            for (int i = 0; i < 3; i++) {
                Problem p = problem(id++, topic);
                problems.add(p);
                progress.add(progressForProblem(p, 10, 5, 2.5, 0)); // 50% < 70%
            }
        }

        when(problemRepository.findAll()).thenReturn(problems);
        when(userProgressRepository.findAll()).thenReturn(progress);

        List<TopicStatsDto> weakAreas = statsService.getWeakAreas();

        assertThat(weakAreas).hasSize(5);
    }

    @Test
    void shouldReturnEmptyWeakAreasWhenNoProblemHasBeenAttempted() {
        Problem p = problem(1L, "trees");
        when(problemRepository.findAll()).thenReturn(List.of(p));
        when(userProgressRepository.findAll()).thenReturn(List.of());

        assertThat(statsService.getWeakAreas()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** UserProgress with a detached (no real DB id) Problem proxy — suitable for stats tests. */
    private UserProgress progressWith(int totalAttempts, int totalCorrect,
                                      double easeFactor, int correctStreak) {
        Problem p = new Problem();
        p.setId((long) (Math.random() * 10000));
        return progressForProblem(p, totalAttempts, totalCorrect, easeFactor, correctStreak);
    }

    private UserProgress progressForProblem(Problem problem, int totalAttempts, int totalCorrect,
                                             double easeFactor, int correctStreak) {
        UserProgress up = new UserProgress();
        up.setProblem(problem);
        up.setTotalAttempts(totalAttempts);
        up.setTotalCorrect(totalCorrect);
        up.setEaseFactor(easeFactor);
        up.setCorrectStreak(correctStreak);
        up.setNextReviewDate(LocalDate.now());
        return up;
    }

    private Problem problem(Long id, String topic) {
        Problem p = new Problem();
        p.setId(id);
        p.setTitle("Problem " + id);
        p.setTopic(topic);
        p.setDifficulty("Medium");
        return p;
    }
}
