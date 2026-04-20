package com.interviewblitz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interviewblitz.model.Problem;
import com.interviewblitz.repository.ProblemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fetches a user's solved LeetCode problems from the LeetCode GraphQL API and
 * stores new ones in the local database.
 *
 * Uses two queries:
 *   1. matchedUser — get the total accepted-problem count to know how many pages to request.
 *   2. problemsetQuestionList (paginated, filter status=AC) — returns questionId, title,
 *      titleSlug, difficulty, and topicTags all in one shot, so no second detail-fetch
 *      round-trip is needed.
 *
 * Problems already present in the database (matched by leetcodeId) are skipped.
 */
@Service
public class LeetCodeSyncService {

    private static final Logger log = LoggerFactory.getLogger(LeetCodeSyncService.class);

    private static final String LEETCODE_GRAPHQL_URL = "https://leetcode.com/graphql";
    private static final int PAGE_SIZE = 50;

    // Query 1: total accepted-problem count, used to determine how many pages to fetch
    private static final String TOTAL_SOLVED_QUERY =
            "{ \"query\": \"query { matchedUser(username: \\\"%s\\\") { submitStatsGlobal { acSubmissionNum { difficulty count } } } }\" }";

    // Query 2: paginated solved problem list — returns full metadata so no detail fetch is needed
    private static final String QUESTION_TAGS_QUERY =
            "query questionData($titleSlug: String!) { question(titleSlug: $titleSlug) { topicTags { name } } }";

    private static final String PROBLEMSET_QUERY =
            "query problemsetQuestionList($categorySlug: String, $limit: Int, $skip: Int, $filters: QuestionListFilterInput) { " +
            "problemsetQuestionList: questionList(categorySlug: $categorySlug, limit: $limit, skip: $skip, filters: $filters) { " +
            "total: totalNum questions: data { questionId title titleSlug difficulty topicTags { name } status } } }";

    private final WebClient webClient;
    private final ProblemRepository problemRepository;
    private final ObjectMapper objectMapper;

    /** LeetCode session cookie value — injected from LEETCODE_SESSION env var. */
    @Value("${leetcode.session:}")
    private String leetcodeSession;

    /** LeetCode CSRF token — injected from LEETCODE_CSRF env var. */
    @Value("${leetcode.csrftoken:}")
    private String leetcodeCsrfToken;

    public LeetCodeSyncService(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
        this.objectMapper = new ObjectMapper();
        // LeetCode blocks requests that don't look like they come from a real browser.
        // Base headers go here; auth headers are added per-request so @Value fields
        // are guaranteed to be populated before any request is sent.
        this.webClient = WebClient.builder()
                .baseUrl(LEETCODE_GRAPHQL_URL)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Referer", "https://leetcode.com")
                .build();
    }

    /**
     * Main entry point: syncs all solved problems for the given LeetCode username.
     * Returns the number of new problems added to the database.
     */
    public int syncProblemsFromLeetCode(String username) {
        log.info("Starting LeetCode sync for user: {}", username);

        int totalSolved = fetchTotalSolvedCount(username);
        if (totalSolved == 0) {
            log.warn("No solved problems found (or could not reach LeetCode API) for user: {}", username);
            return 0;
        }
        log.info("Total accepted problems for {}: {}", username, totalSolved);

        int newProblemsAdded = fetchAndSaveAllSolvedProblems(totalSolved);

        log.info("Sync complete for {}. New problems added: {}", username, newProblemsAdded);
        return newProblemsAdded;
    }

    /**
     * Paginates through problemsetQuestionList (AC filter) until all solved problems are
     * saved. Returns the total count of new rows inserted across all pages.
     */
    int fetchAndSaveAllSolvedProblems(int totalSolved) {
        int newProblemsAdded = 0;
        int skip = 0;

        while (skip < totalSolved) {
            int savedInBatch = fetchAndSaveProblemBatch(skip, PAGE_SIZE);
            newProblemsAdded += savedInBatch;

            log.info("Batch skip={}: saved {} new problems (running total: {})",
                    skip, savedInBatch, newProblemsAdded);

            skip += PAGE_SIZE;
        }

        return newProblemsAdded;
    }

    /**
     * Fetches one page of solved problems from problemsetQuestionList and saves any
     * that are not already in the database. Returns the count of new rows saved.
     */
    int fetchAndSaveProblemBatch(int skip, int limit) {
        try {
            String requestBody = buildProblemsetRequestBody(skip, limit);
            String response = postGraphQL(requestBody);
            log.debug("problemsetQuestionList raw response (skip={}): {}", skip, response);
            return parseBatchAndSave(response);
        } catch (WebClientResponseException e) {
            log.error("LeetCode API returned HTTP {} fetching batch skip={}: {}",
                    e.getStatusCode(), skip, e.getMessage());
            return 0;
        } catch (Exception e) {
            log.error("Failed to fetch/save batch at skip={}: {}", skip, e.getMessage());
            return 0;
        }
    }

    /**
     * Builds the JSON request body for problemsetQuestionList.
     * Uses Jackson to construct the object so the variables sub-object is serialised
     * correctly — string formatting is not reliable for nested JSON.
     */
    String buildProblemsetRequestBody(int skip, int limit) throws Exception {
        ObjectNode filters = objectMapper.createObjectNode();
        filters.put("status", "AC");

        ObjectNode variables = objectMapper.createObjectNode();
        variables.put("categorySlug", "");
        variables.put("skip", skip);
        variables.put("limit", limit);
        variables.set("filters", filters);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("query", PROBLEMSET_QUERY);
        body.set("variables", variables);

        return objectMapper.writeValueAsString(body);
    }

    /**
     * Parses a problemsetQuestionList response, saves each problem that isn't already
     * in the database, and returns the count of new rows inserted.
     */
    int parseBatchAndSave(String responseBody) {
        int saved = 0;
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode questions = root.path("data")
                    .path("problemsetQuestionList")
                    .path("questions");

            if (questions.isMissingNode() || !questions.isArray()) {
                log.warn("problemsetQuestionList response contained no questions array");
                return 0;
            }

            for (JsonNode q : questions) {
                String rawId = q.path("questionId").asText();
                if (rawId.isEmpty() || rawId.equals("null")) {
                    log.warn("Skipping problem with missing questionId");
                    continue;
                }

                int leetcodeId;
                try {
                    leetcodeId = Integer.parseInt(rawId);
                } catch (NumberFormatException e) {
                    log.warn("Could not parse questionId '{}' as integer — skipping", rawId);
                    continue;
                }

                // Skip problems already stored
                if (problemRepository.findByLeetcodeId(leetcodeId).isPresent()) {
                    log.debug("Problem {} already synced — skipping", leetcodeId);
                    continue;
                }

                Problem problem = new Problem();
                problem.setLeetcodeId(leetcodeId);
                problem.setTitle(q.path("title").asText());
                problem.setDifficulty(q.path("difficulty").asText());
                problem.setTopic(extractPrimaryTopic(q.path("topicTags")));
                problem.setSyncedAt(LocalDateTime.now());
                // titleSlug stored in description until a dedicated column is added in Phase 2
                problem.setDescription(q.path("titleSlug").asText());

                problemRepository.save(problem);
                log.info("Saved new problem: [{}] {}", leetcodeId, problem.getTitle());
                saved++;
            }

        } catch (Exception e) {
            log.error("Failed to parse batch response: {}", e.getMessage());
        }
        return saved;
    }

    /**
     * Calls matchedUser to get the total count of accepted (AC) problems for this user.
     * Returns 0 if the query fails or the user doesn't exist.
     */
    int fetchTotalSolvedCount(String username) {
        String query = String.format(TOTAL_SOLVED_QUERY, username);
        try {
            String response = postGraphQL(query);
            log.debug("Total solved count raw response: {}", response);
            return parseTotalSolvedCount(response);
        } catch (Exception e) {
            log.error("Failed to fetch total solved count for {}: {}", username, e.getMessage());
            return 0;
        }
    }

    /**
     * Parses the total number of accepted problems out of the matchedUser response.
     * Uses the "All" difficulty bucket directly; sums Easy + Medium + Hard as a fallback.
     */
    int parseTotalSolvedCount(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode acNums = root.path("data")
                    .path("matchedUser")
                    .path("submitStatsGlobal")
                    .path("acSubmissionNum");

            if (acNums.isMissingNode() || !acNums.isArray()) {
                log.warn("Could not find acSubmissionNum in response");
                return 0;
            }

            for (JsonNode entry : acNums) {
                if ("All".equals(entry.path("difficulty").asText())) {
                    return entry.path("count").asInt(0);
                }
            }

            // No "All" bucket — sum the individual difficulty counts
            int total = 0;
            for (JsonNode entry : acNums) {
                total += entry.path("count").asInt(0);
            }
            return total;

        } catch (Exception e) {
            log.error("Failed to parse total solved count: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Sends a pre-serialised JSON body to the LeetCode GraphQL endpoint,
     * attaching the session cookie and CSRF token on every request.
     */
    private String postGraphQL(String requestBody) {
        return webClient.post()
                .header("Cookie", buildCookieHeader())
                .header("x-csrftoken", leetcodeCsrfToken)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * Builds the Cookie header value that authenticates requests to LeetCode.
     * LeetCode requires both the session token and the CSRF token in the cookie jar.
     */
    private String buildCookieHeader() {
        return "LEETCODE_SESSION=" + leetcodeSession + "; csrftoken=" + leetcodeCsrfToken;
    }

    /**
     * Collects all tag names from the topicTags array into a Set, then checks each
     * NeetCode category in priority order (specific → general) so that a problem
     * tagged [Array, Sliding Window] is categorised as "Sliding Window", not
     * "Arrays & Hashing".
     */
    String extractPrimaryTopic(JsonNode topicTagsNode) {
        if (topicTagsNode.isMissingNode() || !topicTagsNode.isArray()) {
            return "Other";
        }

        Set<String> tags = new HashSet<>();
        for (JsonNode tag : topicTagsNode) {
            tags.add(tag.path("name").asText());
        }

        return resolveNeetCodeCategory(tags);
    }

    /**
     * Applies NeetCode-style category rules against a set of raw LeetCode tag names.
     * Rules are evaluated in priority order: specific/rare patterns first, broad tags
     * (Array, Hash Table) last so they don't swallow everything.
     *
     * Key constraint: DFS/BFS are NOT used to match Graphs because tree problems also
     * carry those tags. Only explicit "Graph", "Union Find", or "Topological Sort" tags
     * signal a graph problem.
     */
    String resolveNeetCodeCategory(Set<String> tags) {
        // 1. Sliding Window
        if (tags.contains("Sliding Window"))
            return "Sliding Window";

        // 2. Two Pointers
        if (tags.contains("Two Pointers"))
            return "Two Pointers";

        // 3. Heap / Priority Queue
        if (tags.contains("Heap (Priority Queue)"))
            return "Heap / Priority Queue";

        // 4. Tries
        if (tags.contains("Trie"))
            return "Tries";

        // 5. Backtracking
        if (tags.contains("Backtracking"))
            return "Backtracking";

        // 6. Bit Manipulation
        if (tags.contains("Bit Manipulation"))
            return "Bit Manipulation";

        // 7. Intervals — tag name contains "Interval" or is "Line Sweep"
        if (tags.stream().anyMatch(t -> t.contains("Interval") || t.equals("Line Sweep")))
            return "Intervals";

        // 8. Greedy
        if (tags.contains("Greedy"))
            return "Greedy";

        // 9. Advanced Graphs — before general Graphs
        if (tags.contains("Shortest Path") || tags.contains("Minimum Spanning Tree"))
            return "Advanced Graphs";

        // 10. Graphs — only explicit graph tags; DFS/BFS excluded because trees use them too
        if (tags.contains("Graph") || tags.contains("Union Find") || tags.contains("Topological Sort"))
            return "Graphs";

        // 11. Trees
        if (tags.contains("Tree") || tags.contains("Binary Tree") || tags.contains("Binary Search Tree"))
            return "Trees";

        // 12. Linked List
        if (tags.contains("Linked List") || tags.contains("Doubly-Linked List"))
            return "Linked List";

        // 13. Binary Search
        if (tags.contains("Binary Search"))
            return "Binary Search";

        // 14. Stack
        if (tags.contains("Stack") || tags.contains("Monotonic Stack"))
            return "Stack";

        // 15. 1-D Dynamic Programming — check before 2-D so the Matrix guard is meaningful
        if (tags.contains("Dynamic Programming") && !tags.contains("Matrix"))
            return "1-D Dynamic Programming";

        // 16. 2-D Dynamic Programming
        if (tags.contains("Dynamic Programming") && tags.contains("Matrix"))
            return "2-D Dynamic Programming";

        // 17. Math & Geometry
        if (tags.contains("Math") || tags.contains("Geometry") || tags.contains("Number Theory"))
            return "Math & Geometry";

        // 18. Arrays & Hashing — broad fallback; must stay near the bottom
        if (tags.contains("Array") || tags.contains("Hash Table") || tags.contains("Matrix") ||
                tags.contains("Counting") || tags.contains("Sorting") || tags.contains("Simulation"))
            return "Arrays & Hashing";

        return "Other";
    }

    /**
     * Re-maps topics for all problems already in the database by fetching each problem's
     * topic tags from LeetCode's public GraphQL API (no session cookie required — individual
     * problem data is publicly accessible). Runs 10 requests concurrently via WebFlux to
     * finish in seconds rather than minutes.
     *
     * The username parameter is accepted for API consistency but is not used; every problem
     * in the local DB is re-mapped regardless of who solved it.
     *
     * Returns the number of problems whose topic field changed.
     */
    public int remapTopicsFromLeetCode(String username) {
        List<Problem> problems = problemRepository.findAll();
        log.info("Starting topic remap for {} DB problems via public LeetCode API", problems.size());

        // Fetch tags for all problems concurrently (concurrency=10 to stay within rate limits),
        // then collect into a list before touching JPA to avoid cross-thread session issues.
        List<Map.Entry<Problem, Set<String>>> fetched = Flux.fromIterable(problems)
                .flatMap(p -> {
                    String titleSlug = p.getDescription();
                    if (titleSlug == null || titleSlug.isBlank()) return Mono.empty();
                    return fetchPublicTagsMono(titleSlug).map(tags -> Map.entry(p, tags));
                }, 10)
                .collectList()
                .block(Duration.ofMinutes(5));

        if (fetched == null) {
            log.error("Tag fetch timed out after 5 minutes");
            return 0;
        }

        int updated = 0;
        for (Map.Entry<Problem, Set<String>> entry : fetched) {
            Problem p = entry.getKey();
            Set<String> tags = entry.getValue();
            if (tags.isEmpty()) continue;

            String newTopic = resolveNeetCodeCategory(tags);
            if (!newTopic.equals(p.getTopic())) {
                log.debug("Remapped [{}] {}: {} → {}", p.getLeetcodeId(), p.getTitle(), p.getTopic(), newTopic);
                p.setTopic(newTopic);
                problemRepository.save(p);
                updated++;
            }
        }

        log.info("Topic remap complete: {} of {} problems updated", updated, problems.size());
        return updated;
    }

    /**
     * Fetches the topic tags for a single LeetCode problem using the public GraphQL API.
     * No session cookie is needed because individual problem metadata is publicly accessible.
     * Returns an empty set on any failure so callers can safely skip the problem.
     */
    private Mono<Set<String>> fetchPublicTagsMono(String titleSlug) {
        try {
            ObjectNode variables = objectMapper.createObjectNode();
            variables.put("titleSlug", titleSlug);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("query", QUESTION_TAGS_QUERY);
            body.set("variables", variables);
            String requestBody = objectMapper.writeValueAsString(body);

            return webClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(response -> {
                        try {
                            JsonNode tagsNode = objectMapper.readTree(response)
                                    .path("data").path("question").path("topicTags");
                            Set<String> tags = new HashSet<>();
                            if (tagsNode.isArray()) {
                                for (JsonNode tag : tagsNode) {
                                    tags.add(tag.path("name").asText());
                                }
                            }
                            return tags;
                        } catch (Exception e) {
                            return Set.<String>of();
                        }
                    })
                    .onErrorReturn(Set.of());
        } catch (Exception e) {
            log.warn("Could not build request for '{}': {}", titleSlug, e.getMessage());
            return Mono.just(Set.of());
        }
    }
}
