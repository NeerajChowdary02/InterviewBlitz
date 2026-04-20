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
        Problem p1 = problem(1L, "trees");
        Problem p2 = problem(2L, "dp");
        Problem p3 = problem(3L, "graphs");

        // trees=30%, graphs=50% — both weak; dp=80% — not weak
        UserProgress up1 = progressForProblem(p1, 10, 3, 2.5, 0);
        UserProgress up2 = progressForProblem(p2, 10, 8, 2.5, 0);
        UserProgress up3 = progressForProblem(p3, 10, 5, 2.5, 0);

        when(problemRepository.findAll()).thenReturn(List.of(p1, p2, p3));
        when(userProgressRepository.findAll()).thenReturn(List.of(up1, up2, up3));

        List<TopicStatsDto> weakAreas = statsService.getWeakAreas();

        assertThat(weakAreas).hasSize(2);
        assertThat(weakAreas.get(0).getTopic()).isEqualTo("trees");   // 30% — worst
        assertThat(weakAreas.get(1).getTopic()).isEqualTo("graphs");  // 50%
    }

    @Test
    void shouldExcludeTopicsWithHighAccuracyFromWeakAreas() {
        Problem lo = problem(1L, "trees");
        Problem hi = problem(2L, "dp");

        when(problemRepository.findAll()).thenReturn(List.of(lo, hi));
        when(userProgressRepository.findAll()).thenReturn(List.of(
                progressForProblem(lo, 10, 4, 2.5, 0),   // 40% — weak
                progressForProblem(hi, 10, 8, 2.5, 0))); // 80% — not weak

        List<TopicStatsDto> weakAreas = statsService.getWeakAreas();

        assertThat(weakAreas).hasSize(1);
        assertThat(weakAreas.get(0).getTopic()).isEqualTo("trees");
    }

    @Test
    void shouldFlagTopicAsWeakAfterASingleReview() {
        // 1 reviewed problem with 0% accuracy — enough to be flagged as weak
        Problem p = problem(1L, "backtracking");
        when(problemRepository.findAll()).thenReturn(List.of(p));
        when(userProgressRepository.findAll()).thenReturn(List.of(
                progressForProblem(p, 5, 0, 2.5, 0))); // 0%

        List<TopicStatsDto> weakAreas = statsService.getWeakAreas();

        assertThat(weakAreas).hasSize(1);
        assertThat(weakAreas.get(0).getTopic()).isEqualTo("backtracking");
    }

    @Test
    void shouldExcludeTopicsWithNoAttemptsFromWeakAreas() {
        Problem p1 = problem(1L, "trees");
        Problem p2 = problem(2L, "dp");

        UserProgress attempted   = progressForProblem(p1, 5, 2, 2.5, 0);  // 40% — weak
        UserProgress unattempted = progressForProblem(p2, 0, 0, 2.5, 0);  // 0 attempts

        when(problemRepository.findAll()).thenReturn(List.of(p1, p2));
        when(userProgressRepository.findAll()).thenReturn(List.of(attempted, unattempted));

        List<TopicStatsDto> weakAreas = statsService.getWeakAreas();

        assertThat(weakAreas).hasSize(1);
        assertThat(weakAreas.get(0).getTopic()).isEqualTo("trees");
    }

    @Test
    void shouldLimitWeakAreasToFiveTopics() {
        // 7 topics each with one reviewed problem at 50% accuracy (all qualify as weak)
        List<Problem> problems = List.of(
                problem(1L, "trees"), problem(2L, "dp"), problem(3L, "graphs"),
                problem(4L, "arrays"), problem(5L, "stacks"), problem(6L, "heaps"),
                problem(7L, "greedy"));

        List<UserProgress> progress = problems.stream()
                .map(p -> progressForProblem(p, 10, 5, 2.5, 0))
                .toList();

        when(problemRepository.findAll()).thenReturn(problems);
        when(userProgressRepository.findAll()).thenReturn(progress);

        assertThat(statsService.getWeakAreas()).hasSize(5);
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
