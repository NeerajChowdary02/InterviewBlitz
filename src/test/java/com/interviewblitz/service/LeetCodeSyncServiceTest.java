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
import java.util.Set;

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
    // NeetCode category mapping — resolveNeetCodeCategory(Set<String>)
    // -----------------------------------------------------------------------

    @Test
    void shouldMapSlidingWindowTagToSlidingWindow() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Sliding Window", "Array")))
                .isEqualTo("Sliding Window");
    }

    @Test
    void shouldMapTwoPointersTagToTwoPointers() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Two Pointers", "Array")))
                .isEqualTo("Two Pointers");
    }

    @Test
    void shouldMapHeapTagToHeapPriorityQueue() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Heap (Priority Queue)", "Array")))
                .isEqualTo("Heap / Priority Queue");
    }

    @Test
    void shouldMapTrieTagToTries() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Trie")))
                .isEqualTo("Tries");
    }

    @Test
    void shouldMapBacktrackingTagToBacktracking() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Backtracking", "Array")))
                .isEqualTo("Backtracking");
    }

    @Test
    void shouldMapBitManipulationTagToBitManipulation() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Bit Manipulation")))
                .isEqualTo("Bit Manipulation");
    }

    @Test
    void shouldMapGreedyTagToGreedy() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Greedy", "Array")))
                .isEqualTo("Greedy");
    }

    @Test
    void shouldMapShortestPathTagToAdvancedGraphs() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Shortest Path", "Graph")))
                .isEqualTo("Advanced Graphs");
    }

    @Test
    void shouldMapGraphTagToGraphs() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Graph", "Depth-First Search")))
                .isEqualTo("Graphs");
    }

    @Test
    void shouldMapUnionFindTagToGraphs() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Union Find")))
                .isEqualTo("Graphs");
    }

    @Test
    void shouldMapTopologicalSortTagToGraphs() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Topological Sort")))
                .isEqualTo("Graphs");
    }

    @Test
    void shouldNotMapBfsAloneToGraphs() {
        // BFS by itself is not enough — tree problems also use BFS
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Breadth-First Search")))
                .isNotEqualTo("Graphs");
    }

    @Test
    void shouldNotMapDfsAloneToGraphs() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Depth-First Search")))
                .isNotEqualTo("Graphs");
    }

    @Test
    void shouldMapTreeTagToTrees() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Tree", "Depth-First Search")))
                .isEqualTo("Trees");
    }

    @Test
    void shouldMapBinarySearchTreeTagToTrees() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Binary Search Tree")))
                .isEqualTo("Trees");
    }

    @Test
    void shouldMapLinkedListTagToLinkedList() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Linked List")))
                .isEqualTo("Linked List");
    }

    @Test
    void shouldMapBinarySearchTagToBinarySearch() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Binary Search", "Array")))
                .isEqualTo("Binary Search");
    }

    @Test
    void shouldMapStackTagToStack() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Stack")))
                .isEqualTo("Stack");
    }

    @Test
    void shouldMapMonotonicStackTagToStack() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Monotonic Stack", "Array")))
                .isEqualTo("Stack");
    }

    @Test
    void shouldMapDpWithoutMatrixToOneDimensionalDp() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Dynamic Programming", "Array")))
                .isEqualTo("1-D Dynamic Programming");
    }

    @Test
    void shouldMapDpWithMatrixToTwoDimensionalDp() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Dynamic Programming", "Matrix")))
                .isEqualTo("2-D Dynamic Programming");
    }

    @Test
    void shouldMapMathTagToMathAndGeometry() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Math")))
                .isEqualTo("Math & Geometry");
    }

    @Test
    void shouldMapGeometryAndNumberTheoryTagsToMathAndGeometry() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Geometry")))
                .isEqualTo("Math & Geometry");
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Number Theory")))
                .isEqualTo("Math & Geometry");
    }

    @Test
    void shouldMapArrayTagToArraysAndHashing() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Array")))
                .isEqualTo("Arrays & Hashing");
    }

    @Test
    void shouldMapHashTableTagToArraysAndHashing() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Hash Table")))
                .isEqualTo("Arrays & Hashing");
    }

    @Test
    void shouldReturnOtherForEmptyTagSet() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of()))
                .isEqualTo("Other");
    }

    @Test
    void shouldReturnOtherForUnrecognisedTags() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Recursion", "Memoization")))
                .isEqualTo("Other");
    }

    @Test
    void shouldPreferSlidingWindowOverArraysAndHashing() {
        // A problem tagged [Array, Sliding Window] must land in Sliding Window, not Arrays & Hashing
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Array", "Sliding Window")))
                .isEqualTo("Sliding Window");
    }

    @Test
    void shouldPreferSpecificCategoryOverArraysAndHashingForHeap() {
        assertThat(syncService.resolveNeetCodeCategory(Set.of("Array", "Heap (Priority Queue)")))
                .isEqualTo("Heap / Priority Queue");
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
                            {"name": "Graph"},
                            {"name": "Depth-First Search"}
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
                && "Graphs".equals(problem.getTopic())   // Graph tag maps to Graphs
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
