package com.interviewblitz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewblitz.model.Problem;
import com.interviewblitz.repository.ProblemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LeetCodeSyncService.
 * Covers topic-tag mapping, total-count parsing, batch save logic,
 * request body construction, and error handling.
 * The WebClient is not exercised — all tests work against pure Java methods.
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
    // Total solved count parsing (matchedUser query)
    // -----------------------------------------------------------------------

    @Test
    void shouldParseTotalSolvedCountFromAllDifficultyBucket() {
        String response = """
                {
                  "data": {
                    "matchedUser": {
                      "submitStatsGlobal": {
                        "acSubmissionNum": [
                          {"difficulty": "All",    "count": 241},
                          {"difficulty": "Easy",   "count": 70},
                          {"difficulty": "Medium", "count": 145},
                          {"difficulty": "Hard",   "count": 26}
                        ]
                      }
                    }
                  }
                }
                """;

        assertThat(syncService.parseTotalSolvedCount(response)).isEqualTo(241);
    }

    @Test
    void shouldSumDifficultiesWhenNoAllBucketPresent() {
        String response = """
                {
                  "data": {
                    "matchedUser": {
                      "submitStatsGlobal": {
                        "acSubmissionNum": [
                          {"difficulty": "Easy",   "count": 70},
                          {"difficulty": "Medium", "count": 145},
                          {"difficulty": "Hard",   "count": 26}
                        ]
                      }
                    }
                  }
                }
                """;

        assertThat(syncService.parseTotalSolvedCount(response)).isEqualTo(241);
    }

    @Test
    void shouldReturnZeroWhenTotalCountResponseIsMalformed() {
        assertThat(syncService.parseTotalSolvedCount("not valid json")).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroWhenMatchedUserIsNull() {
        assertThat(syncService.parseTotalSolvedCount(
                "{ \"data\": { \"matchedUser\": null } }")).isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // Request body construction (problemsetQuestionList)
    // -----------------------------------------------------------------------

    @Test
    void shouldBuildProblemsetRequestBodyWithCorrectVariables() throws Exception {
        String body = syncService.buildProblemsetRequestBody(50, 50);

        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.readTree(body);

        // The query field must be present
        assertThat(root.path("query").asText()).isNotEmpty();

        // Variables must carry the correct skip, limit, and AC filter
        var vars = root.path("variables");
        assertThat(vars.path("skip").asInt()).isEqualTo(50);
        assertThat(vars.path("limit").asInt()).isEqualTo(50);
        assertThat(vars.path("categorySlug").asText()).isEqualTo("");
        assertThat(vars.path("filters").path("status").asText()).isEqualTo("AC");
    }

    @Test
    void shouldBuildProblemsetRequestBodyWithZeroSkipOnFirstPage() throws Exception {
        String body = syncService.buildProblemsetRequestBody(0, 50);

        var root = new ObjectMapper().readTree(body);
        assertThat(root.path("variables").path("skip").asInt()).isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // Batch parse-and-save (problemsetQuestionList response)
    // -----------------------------------------------------------------------

    @Test
    void shouldSaveNewProblemsFromBatchResponse() {
        when(problemRepository.findByLeetcodeId(anyInt())).thenReturn(Optional.empty());
        when(problemRepository.save(any(Problem.class))).thenAnswer(inv -> inv.getArgument(0));

        String response = """
                {
                  "data": {
                    "problemsetQuestionList": {
                      "total": 241,
                      "questions": [
                        {
                          "questionId": "1",
                          "title": "Two Sum",
                          "titleSlug": "two-sum",
                          "difficulty": "Easy",
                          "topicTags": [{"name": "Array"}],
                          "status": "ac"
                        },
                        {
                          "questionId": "2",
                          "title": "Add Two Numbers",
                          "titleSlug": "add-two-numbers",
                          "difficulty": "Medium",
                          "topicTags": [{"name": "Linked List"}],
                          "status": "ac"
                        }
                      ]
                    }
                  }
                }
                """;

        int saved = syncService.parseBatchAndSave(response);

        assertThat(saved).isEqualTo(2);
        verify(problemRepository, times(2)).save(any(Problem.class));
    }

    @Test
    void shouldSkipAlreadySyncedProblemsInBatch() {
        // Problem 1 already exists; problem 2 is new
        Problem existing = new Problem();
        existing.setLeetcodeId(1);
        when(problemRepository.findByLeetcodeId(1)).thenReturn(Optional.of(existing));
        when(problemRepository.findByLeetcodeId(2)).thenReturn(Optional.empty());
        when(problemRepository.save(any(Problem.class))).thenAnswer(inv -> inv.getArgument(0));

        String response = """
                {
                  "data": {
                    "problemsetQuestionList": {
                      "total": 2,
                      "questions": [
                        {
                          "questionId": "1",
                          "title": "Two Sum",
                          "titleSlug": "two-sum",
                          "difficulty": "Easy",
                          "topicTags": [{"name": "Array"}],
                          "status": "ac"
                        },
                        {
                          "questionId": "2",
                          "title": "Add Two Numbers",
                          "titleSlug": "add-two-numbers",
                          "difficulty": "Medium",
                          "topicTags": [{"name": "Linked List"}],
                          "status": "ac"
                        }
                      ]
                    }
                  }
                }
                """;

        int saved = syncService.parseBatchAndSave(response);

        assertThat(saved).isEqualTo(1);
        // Only problem 2 should have been saved
        verify(problemRepository, times(1)).save(any(Problem.class));
    }

    @Test
    void shouldReturnZeroWhenAllProblemsInBatchAlreadySynced() {
        Problem existing = new Problem();
        existing.setLeetcodeId(1);
        when(problemRepository.findByLeetcodeId(1)).thenReturn(Optional.of(existing));

        String response = """
                {
                  "data": {
                    "problemsetQuestionList": {
                      "total": 1,
                      "questions": [
                        {
                          "questionId": "1",
                          "title": "Two Sum",
                          "titleSlug": "two-sum",
                          "difficulty": "Easy",
                          "topicTags": [{"name": "Array"}],
                          "status": "ac"
                        }
                      ]
                    }
                  }
                }
                """;

        assertThat(syncService.parseBatchAndSave(response)).isEqualTo(0);
        verify(problemRepository, never()).save(any(Problem.class));
    }

    @Test
    void shouldReturnZeroWhenBatchResponseHasNoQuestions() {
        String response = """
                {
                  "data": {
                    "problemsetQuestionList": {
                      "total": 0,
                      "questions": []
                    }
                  }
                }
                """;

        assertThat(syncService.parseBatchAndSave(response)).isEqualTo(0);
        verify(problemRepository, never()).save(any(Problem.class));
    }

    @Test
    void shouldReturnZeroWhenBatchResponseIsMalformedJson() {
        assertThat(syncService.parseBatchAndSave("not valid json{{")).isEqualTo(0);
        verify(problemRepository, never()).save(any(Problem.class));
    }

    @Test
    void shouldSaveCorrectFieldsFromBatchResponse() {
        when(problemRepository.findByLeetcodeId(anyInt())).thenReturn(Optional.empty());
        when(problemRepository.save(any(Problem.class))).thenAnswer(inv -> inv.getArgument(0));

        String response = """
                {
                  "data": {
                    "problemsetQuestionList": {
                      "total": 1,
                      "questions": [
                        {
                          "questionId": "200",
                          "title": "Number of Islands",
                          "titleSlug": "number-of-islands",
                          "difficulty": "Medium",
                          "topicTags": [
                            {"name": "Breadth-First Search"},
                            {"name": "Dynamic Programming"}
                          ],
                          "status": "ac"
                        }
                      ]
                    }
                  }
                }
                """;

        syncService.parseBatchAndSave(response);

        verify(problemRepository).save(argThat(problem ->
                problem.getLeetcodeId() == 200
                && "Number of Islands".equals(problem.getTitle())
                && "Medium".equals(problem.getDifficulty())
                && "graphs".equals(problem.getTopic())   // BFS maps to graphs
        ));
    }

    @Test
    void shouldSkipProblemsWithMissingQuestionIdInBatch() {
        when(problemRepository.findByLeetcodeId(anyInt())).thenReturn(Optional.empty());
        when(problemRepository.save(any(Problem.class))).thenAnswer(inv -> inv.getArgument(0));

        String response = """
                {
                  "data": {
                    "problemsetQuestionList": {
                      "total": 2,
                      "questions": [
                        {
                          "questionId": "",
                          "title": "Bad Problem",
                          "titleSlug": "bad-problem",
                          "difficulty": "Easy",
                          "topicTags": [],
                          "status": "ac"
                        },
                        {
                          "questionId": "1",
                          "title": "Two Sum",
                          "titleSlug": "two-sum",
                          "difficulty": "Easy",
                          "topicTags": [{"name": "Array"}],
                          "status": "ac"
                        }
                      ]
                    }
                  }
                }
                """;

        int saved = syncService.parseBatchAndSave(response);

        assertThat(saved).isEqualTo(1);
    }
}
