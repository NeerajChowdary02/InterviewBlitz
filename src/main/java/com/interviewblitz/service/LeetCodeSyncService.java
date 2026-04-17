package com.interviewblitz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewblitz.model.Problem;
import com.interviewblitz.repository.ProblemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches a user's solved LeetCode problems from the LeetCode GraphQL API and
 * stores new ones in the local database. Existing problems (matched by leetcodeId)
 * are skipped to avoid duplicate rows. A short delay between detail fetches prevents
 * the LeetCode API from rate-limiting us.
 */
@Service
public class LeetCodeSyncService {

    private static final Logger log = LoggerFactory.getLogger(LeetCodeSyncService.class);

    private static final String LEETCODE_GRAPHQL_URL = "https://leetcode.com/graphql";

    // GraphQL query to get the list of problems the user has solved recently
    private static final String RECENT_AC_QUERY =
            "{ \"query\": \"query { recentAcSubmissionList(username: \\\"%s\\\", limit: 500) { title titleSlug } }\" }";

    // GraphQL query to get detail (difficulty, topic tags, content) for a single problem
    private static final String PROBLEM_DETAIL_QUERY =
            "{ \"query\": \"query { question(titleSlug: \\\"%s\\\") { questionId title difficulty topicTags { name } content } }\" }";

    private final WebClient webClient;
    private final ProblemRepository problemRepository;
    private final ObjectMapper objectMapper;

    public LeetCodeSyncService(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
        this.objectMapper = new ObjectMapper();
        // LeetCode requires a realistic User-Agent header, otherwise it blocks requests
        this.webClient = WebClient.builder()
                .baseUrl(LEETCODE_GRAPHQL_URL)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; InterviewBlitz/1.0)")
                .defaultHeader("Referer", "https://leetcode.com")
                .build();
    }

    /**
     * Main entry point: syncs all solved problems for the given LeetCode username.
     * Returns the number of new problems added to the database.
     */
    public int syncProblemsFromLeetCode(String username) {
        log.info("Starting LeetCode sync for user: {}", username);

        List<String> titleSlugs = fetchSolvedProblemSlugs(username);
        if (titleSlugs.isEmpty()) {
            log.warn("No solved problems found for user: {}", username);
            return 0;
        }

        int newProblemsAdded = 0;
        for (String titleSlug : titleSlugs) {
            try {
                boolean saved = fetchAndSaveProblemDetails(titleSlug);
                if (saved) {
                    newProblemsAdded++;
                }
                // Pause between requests to avoid triggering LeetCode's rate limiter
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Sync interrupted while processing: {}", titleSlug);
                break;
            } catch (Exception e) {
                log.error("Failed to process problem: {} — skipping. Error: {}", titleSlug, e.getMessage());
            }
        }

        log.info("Sync complete for {}. New problems added: {}", username, newProblemsAdded);
        return newProblemsAdded;
    }

    /**
     * Calls the LeetCode GraphQL API to get the list of title slugs for all
     * recently accepted submissions by this user (up to 500).
     */
    List<String> fetchSolvedProblemSlugs(String username) {
        String query = String.format(RECENT_AC_QUERY, username);

        try {
            String response = webClient.post()
                    .bodyValue(query)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseTitleSlugsFromResponse(response);
        } catch (WebClientResponseException e) {
            log.error("LeetCode API returned HTTP {}: {}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("LeetCode API error: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Failed to reach LeetCode API: {}", e.getMessage());
            throw new RuntimeException("Could not connect to LeetCode API", e);
        }
    }

    /**
     * Parses the list of title slugs out of the recentAcSubmissionList GraphQL response.
     */
    List<String> parseTitleSlugsFromResponse(String responseBody) {
        List<String> slugs = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode submissions = root.path("data").path("recentAcSubmissionList");

            // Deduplicate: the same problem can appear multiple times in submission history
            List<String> seen = new ArrayList<>();
            for (JsonNode submission : submissions) {
                String slug = submission.path("titleSlug").asText();
                if (!slug.isEmpty() && !seen.contains(slug)) {
                    seen.add(slug);
                    slugs.add(slug);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse submission list response: {}", e.getMessage());
        }
        return slugs;
    }

    /**
     * Fetches full details for one problem by its title slug, then saves it to the
     * database if it hasn't been synced before. Returns true if a new row was inserted.
     */
    boolean fetchAndSaveProblemDetails(String titleSlug) {
        String query = String.format(PROBLEM_DETAIL_QUERY, titleSlug);

        try {
            String response = webClient.post()
                    .bodyValue(query)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseProblemAndSave(response);
        } catch (WebClientResponseException e) {
            log.error("Failed to fetch details for {}: HTTP {}", titleSlug, e.getStatusCode());
            return false;
        }
    }

    /**
     * Parses the problem detail GraphQL response and saves to DB.
     * Skips if the problem already exists (by leetcodeId).
     */
    boolean parseProblemAndSave(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode question = root.path("data").path("question");

            if (question.isMissingNode() || question.isNull()) {
                log.warn("GraphQL response contained no question data");
                return false;
            }

            String rawId = question.path("questionId").asText();
            if (rawId.isEmpty() || rawId.equals("null")) {
                log.warn("Problem has no questionId — skipping");
                return false;
            }

            int leetcodeId = Integer.parseInt(rawId);

            // Skip if already in the database
            if (problemRepository.findByLeetcodeId(leetcodeId).isPresent()) {
                log.debug("Problem {} already synced — skipping", leetcodeId);
                return false;
            }

            Problem problem = new Problem();
            problem.setLeetcodeId(leetcodeId);
            problem.setTitle(question.path("title").asText());
            problem.setDifficulty(question.path("difficulty").asText());
            problem.setDescription(question.path("content").asText());
            problem.setTopic(extractPrimaryTopic(question.path("topicTags")));
            problem.setSyncedAt(LocalDateTime.now());

            problemRepository.save(problem);
            log.info("Saved new problem: [{}] {}", leetcodeId, problem.getTitle());
            return true;

        } catch (NumberFormatException e) {
            log.error("Could not parse questionId as integer: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Error parsing problem detail response: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Iterates through a problem's topic tags and returns the first one that matches
     * a known simplified category. Falls back to "other" if nothing matches.
     */
    String extractPrimaryTopic(JsonNode topicTagsNode) {
        if (topicTagsNode.isMissingNode() || !topicTagsNode.isArray()) {
            return "other";
        }

        for (JsonNode tag : topicTagsNode) {
            String tagName = tag.path("name").asText();
            String mapped = mapTopicTag(tagName);
            // Use the first tag that resolves to a known category
            if (!mapped.equals("other")) {
                return mapped;
            }
        }

        // No known tag found — fall back to the first tag's mapped value (which is "other")
        if (topicTagsNode.size() > 0) {
            return "other";
        }
        return "other";
    }

    /**
     * Maps a raw LeetCode topic tag name to our simplified internal category.
     * The mapping groups related tags into one bucket (e.g. BFS + DFS → "graphs").
     */
    String mapTopicTag(String rawTag) {
        return switch (rawTag) {
            case "Tree", "Binary Tree", "Binary Search Tree", "N-ary Tree",
                 "Segment Tree", "Binary Indexed Tree" -> "trees";
            case "Dynamic Programming" -> "dp";
            case "Graph", "Breadth-First Search", "Depth-First Search",
                 "Topological Sort", "Shortest Path", "Biconnected Component",
                 "Strongly Connected Component", "Eulerian Circuit" -> "graphs";
            case "Array", "Hash Table", "Matrix" -> "arrays";
            case "Two Pointers" -> "two-pointers";
            case "Sliding Window" -> "sliding-window";
            case "Stack", "Monotonic Stack" -> "stacks";
            case "Linked List", "Doubly-Linked List" -> "linked-lists";
            case "Binary Search" -> "binary-search";
            case "Heap (Priority Queue)" -> "heaps";
            case "Backtracking" -> "backtracking";
            case "Greedy" -> "greedy";
            case "Trie" -> "tries";
            case "Union Find" -> "union-find";
            case "Sorting", "Merge Sort", "Counting Sort", "Radix Sort",
                 "Bucket Sort" -> "sorting";
            default -> "other";
        };
    }
}
