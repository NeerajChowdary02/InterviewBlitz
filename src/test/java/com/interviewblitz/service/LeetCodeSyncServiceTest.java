package com.interviewblitz.service;

import com.interviewblitz.model.Problem;
import com.interviewblitz.repository.ProblemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LeetCodeSyncService.
 * Tests topic-tag mapping, deduplication logic, and response parsing.
 * The WebClient is not called here — these tests only exercise pure Java logic.
 */
@ExtendWith(MockitoExtension.class)
class LeetCodeSyncServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    private LeetCodeSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new LeetCodeSyncService(problemRepository);
    }

    // -----------------------------------------------------------------------
    // Topic mapping tests
    // -----------------------------------------------------------------------

    @Test
    void shouldMapTreeTagToTreesTopic() {
        assertThat(syncService.mapTopicTag("Tree")).isEqualTo("trees");
    }

    @Test
    void shouldMapBinaryTreeTagToTreesTopic() {
        assertThat(syncService.mapTopicTag("Binary Tree")).isEqualTo("trees");
    }

    @Test
    void shouldMapBinarySearchTreeTagToTreesTopic() {
        assertThat(syncService.mapTopicTag("Binary Search Tree")).isEqualTo("trees");
    }

    @Test
    void shouldMapDynamicProgrammingTagToDpTopic() {
        assertThat(syncService.mapTopicTag("Dynamic Programming")).isEqualTo("dp");
    }

    @Test
    void shouldMapGraphTagToGraphsTopic() {
        assertThat(syncService.mapTopicTag("Graph")).isEqualTo("graphs");
    }

    @Test
    void shouldMapBreadthFirstSearchTagToGraphsTopic() {
        assertThat(syncService.mapTopicTag("Breadth-First Search")).isEqualTo("graphs");
    }

    @Test
    void shouldMapDepthFirstSearchTagToGraphsTopic() {
        assertThat(syncService.mapTopicTag("Depth-First Search")).isEqualTo("graphs");
    }

    @Test
    void shouldMapArrayTagToArraysTopic() {
        assertThat(syncService.mapTopicTag("Array")).isEqualTo("arrays");
    }

    @Test
    void shouldMapHashTableTagToArraysTopic() {
        assertThat(syncService.mapTopicTag("Hash Table")).isEqualTo("arrays");
    }

    @Test
    void shouldMapTwoPointersTagToTwoPointersTopic() {
        assertThat(syncService.mapTopicTag("Two Pointers")).isEqualTo("two-pointers");
    }

    @Test
    void shouldMapSlidingWindowTagToSlidingWindowTopic() {
        assertThat(syncService.mapTopicTag("Sliding Window")).isEqualTo("sliding-window");
    }

    @Test
    void shouldMapStackTagToStacksTopic() {
        assertThat(syncService.mapTopicTag("Stack")).isEqualTo("stacks");
    }

    @Test
    void shouldMapMonotonicStackTagToStacksTopic() {
        assertThat(syncService.mapTopicTag("Monotonic Stack")).isEqualTo("stacks");
    }

    @Test
    void shouldMapLinkedListTagToLinkedListsTopic() {
        assertThat(syncService.mapTopicTag("Linked List")).isEqualTo("linked-lists");
    }

    @Test
    void shouldMapBinarySearchTagToBinarySearchTopic() {
        assertThat(syncService.mapTopicTag("Binary Search")).isEqualTo("binary-search");
    }

    @Test
    void shouldMapHeapTagToHeapsTopic() {
        assertThat(syncService.mapTopicTag("Heap (Priority Queue)")).isEqualTo("heaps");
    }

    @Test
    void shouldMapBacktrackingTagToBacktrackingTopic() {
        assertThat(syncService.mapTopicTag("Backtracking")).isEqualTo("backtracking");
    }

    @Test
    void shouldMapGreedyTagToGreedyTopic() {
        assertThat(syncService.mapTopicTag("Greedy")).isEqualTo("greedy");
    }

    @Test
    void shouldMapTrieTagToTriesTopic() {
        assertThat(syncService.mapTopicTag("Trie")).isEqualTo("tries");
    }

    @Test
    void shouldMapUnionFindTagToUnionFindTopic() {
        assertThat(syncService.mapTopicTag("Union Find")).isEqualTo("union-find");
    }

    @Test
    void shouldMapSortingTagToSortingTopic() {
        assertThat(syncService.mapTopicTag("Sorting")).isEqualTo("sorting");
    }

    @Test
    void shouldMapUnknownTagToOtherTopic() {
        assertThat(syncService.mapTopicTag("Geometry")).isEqualTo("other");
        assertThat(syncService.mapTopicTag("Number Theory")).isEqualTo("other");
        assertThat(syncService.mapTopicTag("")).isEqualTo("other");
    }

    // -----------------------------------------------------------------------
    // Duplicate detection tests
    // -----------------------------------------------------------------------

    @Test
    void shouldSkipProblemThatAlreadyExistsInDatabase() {
        // Simulate a problem already stored by returning a non-empty Optional
        Problem existing = new Problem();
        existing.setLeetcodeId(1);
        when(problemRepository.findByLeetcodeId(1)).thenReturn(Optional.of(existing));

        String responseWithExistingProblem = """
                {
                  "data": {
                    "question": {
                      "questionId": "1",
                      "title": "Two Sum",
                      "difficulty": "Easy",
                      "topicTags": [{"name": "Array"}],
                      "content": "Given an array..."
                    }
                  }
                }
                """;

        boolean saved = syncService.parseProblemAndSave(responseWithExistingProblem);

        assertThat(saved).isFalse();
        // The repository save method should never be called for a duplicate
        verify(problemRepository, never()).save(any(Problem.class));
    }

    @Test
    void shouldSaveNewProblemThatIsNotInDatabase() {
        // Simulate no existing problem found
        when(problemRepository.findByLeetcodeId(anyInt())).thenReturn(Optional.empty());
        when(problemRepository.save(any(Problem.class))).thenAnswer(inv -> inv.getArgument(0));

        String responseWithNewProblem = """
                {
                  "data": {
                    "question": {
                      "questionId": "1",
                      "title": "Two Sum",
                      "difficulty": "Easy",
                      "topicTags": [{"name": "Array"}, {"name": "Hash Table"}],
                      "content": "Given an array..."
                    }
                  }
                }
                """;

        boolean saved = syncService.parseProblemAndSave(responseWithNewProblem);

        assertThat(saved).isTrue();
        verify(problemRepository, times(1)).save(any(Problem.class));
    }

    // -----------------------------------------------------------------------
    // Error handling / bad data tests
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnFalseWhenResponseHasNoQuestionData() {
        String emptyResponse = "{ \"data\": { \"question\": null } }";

        boolean saved = syncService.parseProblemAndSave(emptyResponse);

        assertThat(saved).isFalse();
        verify(problemRepository, never()).save(any(Problem.class));
    }

    @Test
    void shouldReturnFalseWhenResponseIsMalformedJson() {
        String malformedJson = "not-valid-json{{{{";

        boolean saved = syncService.parseProblemAndSave(malformedJson);

        assertThat(saved).isFalse();
        verify(problemRepository, never()).save(any(Problem.class));
    }

    @Test
    void shouldReturnFalseWhenQuestionIdIsMissing() {
        String responseWithoutId = """
                {
                  "data": {
                    "question": {
                      "questionId": "",
                      "title": "Two Sum",
                      "difficulty": "Easy",
                      "topicTags": [],
                      "content": "..."
                    }
                  }
                }
                """;

        boolean saved = syncService.parseProblemAndSave(responseWithoutId);

        assertThat(saved).isFalse();
    }

    // -----------------------------------------------------------------------
    // Slug parsing tests
    // -----------------------------------------------------------------------

    @Test
    void shouldParseTitleSlugsFromValidSubmissionResponse() {
        String response = """
                {
                  "data": {
                    "recentAcSubmissionList": [
                      {"title": "Two Sum", "titleSlug": "two-sum"},
                      {"title": "Reverse Linked List", "titleSlug": "reverse-linked-list"}
                    ]
                  }
                }
                """;

        List<String> slugs = syncService.parseTitleSlugsFromResponse(response);

        assertThat(slugs).containsExactly("two-sum", "reverse-linked-list");
    }

    @Test
    void shouldDeduplicateSlugsFromSubmissionResponse() {
        // The same problem can appear multiple times in submission history
        String response = """
                {
                  "data": {
                    "recentAcSubmissionList": [
                      {"title": "Two Sum", "titleSlug": "two-sum"},
                      {"title": "Two Sum", "titleSlug": "two-sum"},
                      {"title": "Reverse Linked List", "titleSlug": "reverse-linked-list"}
                    ]
                  }
                }
                """;

        List<String> slugs = syncService.parseTitleSlugsFromResponse(response);

        assertThat(slugs).hasSize(2);
        assertThat(slugs).containsExactly("two-sum", "reverse-linked-list");
    }

    @Test
    void shouldReturnEmptyListWhenSubmissionResponseIsMalformed() {
        String malformedResponse = "{ bad json }";

        List<String> slugs = syncService.parseTitleSlugsFromResponse(malformedResponse);

        assertThat(slugs).isEmpty();
    }

    @Test
    void shouldUsePrimaryTopicFromFirstKnownTag() {
        // Problem has Array first but also BFS — should pick "arrays" since it comes first
        when(problemRepository.findByLeetcodeId(anyInt())).thenReturn(Optional.empty());
        when(problemRepository.save(any(Problem.class))).thenAnswer(inv -> inv.getArgument(0));

        String response = """
                {
                  "data": {
                    "question": {
                      "questionId": "200",
                      "title": "Number of Islands",
                      "difficulty": "Medium",
                      "topicTags": [
                        {"name": "Breadth-First Search"},
                        {"name": "Dynamic Programming"}
                      ],
                      "content": "..."
                    }
                  }
                }
                """;

        syncService.parseProblemAndSave(response);

        // Verify the saved problem uses the first resolvable tag
        verify(problemRepository).save(argThat(problem ->
                "graphs".equals(problem.getTopic())
        ));
    }
}
