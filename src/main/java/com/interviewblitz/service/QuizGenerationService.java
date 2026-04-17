package com.interviewblitz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interviewblitz.model.Problem;
import com.interviewblitz.model.Question;
import com.interviewblitz.repository.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates multiple-choice quiz questions for a LeetCode problem using GPT-4o-mini.
 * Each generation call produces exactly four questions — one per category:
 * PATTERN, COMPLEXITY, EDGE_CASE, and APPROACH. The questions are saved to the database
 * and returned so the caller can immediately serve them.
 *
 * If questions already exist for a problem they are returned as-is without calling
 * the API again, so each problem is charged to the OpenAI API exactly once.
 */
@Service
public class QuizGenerationService {

    private static final Logger log = LoggerFactory.getLogger(QuizGenerationService.class);

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final WebClient openAiWebClient;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    // Field default ensures the correct value is used even when @Value is not injected (e.g. unit tests)
    @Value("${openai.model:gpt-4o-mini}")
    private String model = "gpt-4o-mini";

    public QuizGenerationService(@Qualifier("openAiWebClient") WebClient openAiWebClient,
                                 QuestionRepository questionRepository) {
        this.openAiWebClient = openAiWebClient;
        this.questionRepository = questionRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Main entry point: returns four questions for the given problem.
     * Calls OpenAI only if no questions have been generated before; otherwise
     * returns the cached questions from the database.
     */
    public List<Question> generateQuestionsForProblem(Problem problem) {
        // Return existing questions without hitting the API again
        if (questionRepository.existsByProblemId(problem.getId())) {
            log.info("Questions already exist for problem [{}] — returning cached", problem.getId());
            return questionRepository.findByProblemId(problem.getId());
        }

        log.info("Generating questions for problem [{}] {}", problem.getId(), problem.getTitle());

        String prompt = buildPrompt(problem);
        String openAiResponseBody = callOpenAiApi(prompt);
        String questionsJson = extractContentFromResponse(openAiResponseBody);

        List<Question> questions = parseQuestionsFromContent(questionsJson, problem);
        if (questions.isEmpty()) {
            log.warn("OpenAI returned no parseable questions for problem [{}]", problem.getId());
            return questions;
        }

        List<Question> saved = questionRepository.saveAll(questions);
        log.info("Saved {} questions for problem [{}] {}", saved.size(), problem.getId(), problem.getTitle());
        return saved;
    }

    /**
     * Builds the prompt sent to GPT-4o-mini using the strategy defined in the project spec.
     * Includes the problem title, difficulty, topic, and solution approach as context.
     */
    String buildPrompt(Problem problem) {
        String approach = problem.getSolutionApproach() != null
                ? problem.getSolutionApproach()
                : "Standard algorithm approach for " + problem.getTopic() + " problems";

        return """
                You are a coding interview coach. Given this LeetCode problem and its solution approach, \
                generate exactly 4 multiple-choice questions that test understanding WITHOUT requiring \
                the person to write code.

                Problem: %s (%s)
                Topic: %s
                Solution Approach: %s

                Generate questions in these categories:
                1. PATTERN - "Which algorithm pattern does this problem use?"
                2. COMPLEXITY - "What is the time/space complexity and why?"
                3. EDGE_CASE - "What edge case would break a naive solution?"
                4. APPROACH - "Why does this approach work better than alternatives?"

                Return ONLY valid JSON array, no markdown, no backticks:
                [
                  {
                    "questionText": "...",
                    "optionA": "...",
                    "optionB": "...",
                    "optionC": "...",
                    "optionD": "...",
                    "correctOption": "A",
                    "explanation": "...",
                    "questionType": "PATTERN"
                  }
                ]"""
                .formatted(problem.getTitle(), problem.getDifficulty(),
                           problem.getTopic(), approach);
    }

    /**
     * Sends the prompt to the OpenAI chat completions endpoint and returns the raw response body.
     * Package-visible so tests can stub this method without mocking the WebClient chain.
     */
    String callOpenAiApi(String prompt) {
        try {
            String requestBody = buildOpenAiRequestBody(prompt);
            return openAiWebClient.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("OpenAI API returned HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("OpenAI API error: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Failed to reach OpenAI API: {}", e.getMessage());
            throw new RuntimeException("Could not connect to OpenAI API", e);
        }
    }

    /**
     * Builds the JSON request body for the OpenAI chat completions endpoint.
     * Uses Jackson so the prompt text is safely escaped regardless of content.
     */
    String buildOpenAiRequestBody(String prompt) throws RuntimeException {
        try {
            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);

            ArrayNode messages = objectMapper.createArrayNode();
            messages.add(message);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.set("messages", messages);
            body.put("temperature", 0.7);

            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build OpenAI request body", e);
        }
    }

    /**
     * Pulls the assistant's text reply out of the OpenAI response envelope.
     * The actual question JSON lives at choices[0].message.content.
     */
    String extractContentFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();

            if (content.isEmpty()) {
                log.warn("OpenAI response had no content in choices[0].message.content");
            }
            return content;
        } catch (Exception e) {
            log.error("Failed to extract content from OpenAI response: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Parses the JSON array string returned by GPT into Question entities linked to the problem.
     * Returns an empty list (not an exception) on any parse failure so the caller can handle gracefully.
     */
    List<Question> parseQuestionsFromContent(String jsonContent, Problem problem) {
        List<Question> questions = new ArrayList<>();
        if (jsonContent == null || jsonContent.isBlank()) {
            log.warn("Received empty content string — no questions to parse");
            return questions;
        }

        try {
            // GPT sometimes wraps its answer in markdown code fences despite the prompt — strip them
            String cleaned = jsonContent.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }

            JsonNode array = objectMapper.readTree(cleaned);
            if (!array.isArray()) {
                log.warn("Expected JSON array from OpenAI but got: {}", array.getNodeType());
                return questions;
            }

            for (JsonNode node : array) {
                Question question = mapNodeToQuestion(node, problem);
                if (question != null) {
                    questions.add(question);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse questions JSON from OpenAI content: {}", e.getMessage());
        }

        return questions;
    }

    /**
     * Maps one JSON object from the GPT response array into a Question entity.
     * Returns null if any required field is missing so the caller can skip that entry.
     */
    private Question mapNodeToQuestion(JsonNode node, Problem problem) {
        String questionText = node.path("questionText").asText();
        String correctOption = node.path("correctOption").asText();
        String questionType = node.path("questionType").asText();

        if (questionText.isEmpty() || correctOption.isEmpty() || questionType.isEmpty()) {
            log.warn("Skipping question with missing required fields: {}", node);
            return null;
        }

        Question question = new Question();
        question.setProblem(problem);
        question.setQuestionText(questionText);
        question.setOptionA(node.path("optionA").asText());
        question.setOptionB(node.path("optionB").asText());
        question.setOptionC(node.path("optionC").asText());
        question.setOptionD(node.path("optionD").asText());
        question.setCorrectOption(correctOption.substring(0, 1).toUpperCase());
        question.setExplanation(node.path("explanation").asText());
        question.setQuestionType(questionType.toUpperCase());
        return question;
    }
}
