package com.interviewblitz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewblitz.model.Problem;
import com.interviewblitz.model.Question;
import com.interviewblitz.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QuizGenerationService.
 * Tests prompt building, OpenAI response parsing, error handling, and caching logic.
 * The WebClient is not exercised — callOpenAiApi is stubbed via Mockito spy so tests
 * focus on the pure logic methods.
 */
@ExtendWith(MockitoExtension.class)
class QuizGenerationServiceTest {

    @Mock
    private WebClient mockWebClient;

    @Mock
    private QuestionRepository questionRepository;

    private QuizGenerationService service;

    // A well-formed JSON array that GPT-4o-mini would return
    private static final String VALID_QUESTIONS_JSON = """
            [
              {
                "questionText": "Which pattern does Two Sum use?",
                "optionA": "Sliding Window",
                "optionB": "Hash Map lookup",
                "optionC": "Binary Search",
                "optionD": "Dynamic Programming",
                "correctOption": "B",
                "explanation": "A hash map stores each number so its complement can be found in O(1).",
                "questionType": "PATTERN"
              },
              {
                "questionText": "What is the time complexity of the optimal Two Sum solution?",
                "optionA": "O(n^2)",
                "optionB": "O(n log n)",
                "optionC": "O(n)",
                "optionD": "O(1)",
                "correctOption": "C",
                "explanation": "A single pass through the array with a hash map gives O(n).",
                "questionType": "COMPLEXITY"
              },
              {
                "questionText": "Which edge case breaks the naive O(n^2) solution?",
                "optionA": "Array with all zeros",
                "optionB": "Using the same element twice",
                "optionC": "Negative numbers",
                "optionD": "Empty array",
                "correctOption": "B",
                "explanation": "The problem guarantees exactly one solution, but a naive loop may reuse index i.",
                "questionType": "EDGE_CASE"
              },
              {
                "questionText": "Why is the hash map approach better than nested loops?",
                "optionA": "Uses less memory",
                "optionB": "Easier to implement",
                "optionC": "Reduces time from O(n^2) to O(n)",
                "optionD": "Works for sorted arrays only",
                "correctOption": "C",
                "explanation": "Trading O(n) space for O(n) time avoids the O(n^2) nested loop.",
                "questionType": "APPROACH"
              }
            ]
            """;

    // The full OpenAI API response envelope wrapping the JSON array above
    private static final String OPENAI_RESPONSE_ENVELOPE = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": %s
                  }
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        service = new QuizGenerationService(mockWebClient, questionRepository);
    }

    // -----------------------------------------------------------------------
    // Prompt building tests
    // -----------------------------------------------------------------------

    @Test
    void shouldIncludeProblemTitleInPrompt() {
        Problem problem = problemWithApproach("Hash map for O(1) lookup");

        String prompt = service.buildPrompt(problem);

        assertThat(prompt).contains("Two Sum");
    }

    @Test
    void shouldIncludeDifficultyInPrompt() {
        Problem problem = problemWithApproach("Hash map for O(1) lookup");

        String prompt = service.buildPrompt(problem);

        assertThat(prompt).contains("Easy");
    }

    @Test
    void shouldIncludeTopicInPrompt() {
        Problem problem = problemWithApproach("Hash map for O(1) lookup");

        String prompt = service.buildPrompt(problem);

        assertThat(prompt).contains("arrays");
    }

    @Test
    void shouldIncludeSolutionApproachInPrompt() {
        Problem problem = problemWithApproach("Use a hash map to store complements");

        String prompt = service.buildPrompt(problem);

        assertThat(prompt).contains("Use a hash map to store complements");
    }

    @Test
    void shouldUseDefaultApproachWhenSolutionApproachIsNull() {
        Problem problem = problemWithApproach(null);

        String prompt = service.buildPrompt(problem);

        // Should not throw, and should contain a fallback mention of the topic
        assertThat(prompt).contains("arrays");
    }

    @Test
    void shouldIncludeAllFourQuestionTypeLabelsInPrompt() {
        Problem problem = problemWithApproach("Standard approach");

        String prompt = service.buildPrompt(problem);

        assertThat(prompt).contains("PATTERN");
        assertThat(prompt).contains("COMPLEXITY");
        assertThat(prompt).contains("EDGE_CASE");
        assertThat(prompt).contains("APPROACH");
    }

    // -----------------------------------------------------------------------
    // OpenAI response parsing tests
    // -----------------------------------------------------------------------

    @Test
    void shouldParseAllFourQuestionsFromValidContent() {
        Problem problem = problemWithApproach("Hash map");

        List<Question> questions = service.parseQuestionsFromContent(VALID_QUESTIONS_JSON, problem);

        assertThat(questions).hasSize(4);
    }

    @Test
    void shouldMapAllFieldsCorrectlyFromParsedQuestion() {
        Problem problem = problemWithApproach("Hash map");

        List<Question> questions = service.parseQuestionsFromContent(VALID_QUESTIONS_JSON, problem);
        Question patternQuestion = questions.stream()
                .filter(q -> "PATTERN".equals(q.getQuestionType()))
                .findFirst()
                .orElseThrow();

        assertThat(patternQuestion.getQuestionText()).contains("Two Sum");
        assertThat(patternQuestion.getOptionA()).isEqualTo("Sliding Window");
        assertThat(patternQuestion.getOptionB()).isEqualTo("Hash Map lookup");
        assertThat(patternQuestion.getCorrectOption()).isEqualTo("B");
        assertThat(patternQuestion.getExplanation()).isNotBlank();
        assertThat(patternQuestion.getProblem()).isEqualTo(problem);
    }

    @Test
    void shouldNormaliseCorrectOptionToUpperCase() {
        String contentWithLowercaseOption = """
                [{"questionText":"Q?","optionA":"A","optionB":"B","optionC":"C","optionD":"D",
                  "correctOption":"b","explanation":"B is right.","questionType":"PATTERN"}]
                """;
        Problem problem = problemWithApproach("approach");

        List<Question> questions = service.parseQuestionsFromContent(contentWithLowercaseOption, problem);

        assertThat(questions).hasSize(1);
        assertThat(questions.get(0).getCorrectOption()).isEqualTo("B");
    }

    @Test
    void shouldReturnEmptyListForMalformedJson() {
        Problem problem = problemWithApproach("approach");

        List<Question> questions = service.parseQuestionsFromContent("not valid json{{", problem);

        assertThat(questions).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenContentIsBlank() {
        List<Question> questions = service.parseQuestionsFromContent("", problemWithApproach("x"));

        assertThat(questions).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenContentIsNull() {
        List<Question> questions = service.parseQuestionsFromContent(null, problemWithApproach("x"));

        assertThat(questions).isEmpty();
    }

    @Test
    void shouldStripMarkdownCodeFencesBeforeParsing() {
        String wrappedInFences = "```json\n" + VALID_QUESTIONS_JSON + "\n```";
        Problem problem = problemWithApproach("Hash map");

        List<Question> questions = service.parseQuestionsFromContent(wrappedInFences, problem);

        assertThat(questions).hasSize(4);
    }

    @Test
    void shouldSkipArrayEntriesWithMissingRequiredFields() {
        String partiallyBadJson = """
                [
                  {"questionText":"","optionA":"A","optionB":"B","optionC":"C","optionD":"D",
                   "correctOption":"A","explanation":"x","questionType":"PATTERN"},
                  {"questionText":"Valid?","optionA":"A","optionB":"B","optionC":"C","optionD":"D",
                   "correctOption":"A","explanation":"Because A.","questionType":"COMPLEXITY"}
                ]
                """;
        Problem problem = problemWithApproach("approach");

        List<Question> questions = service.parseQuestionsFromContent(partiallyBadJson, problem);

        // First entry has empty questionText — should be skipped; second should be saved
        assertThat(questions).hasSize(1);
        assertThat(questions.get(0).getQuestionType()).isEqualTo("COMPLEXITY");
    }

    // -----------------------------------------------------------------------
    // extractContentFromResponse tests
    // -----------------------------------------------------------------------

    @Test
    void shouldExtractContentStringFromOpenAiEnvelope() throws Exception {
        // Embed the JSON array as an escaped string inside the envelope
        String contentValue = VALID_QUESTIONS_JSON.replace("\"", "\\\"").replace("\n", "\\n");
        String envelope = OPENAI_RESPONSE_ENVELOPE.formatted("\"" + contentValue + "\"");

        String extracted = service.extractContentFromResponse(envelope);

        // The extracted string should be parseable as a JSON array
        assertThat(new ObjectMapper().readTree(extracted).isArray()).isTrue();
    }

    @Test
    void shouldReturnEmptyStringWhenEnvelopeIsMalformed() {
        assertThat(service.extractContentFromResponse("not json")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Caching / generate-once tests
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnCachedQuestionsWithoutCallingOpenAiWhenQuestionsAlreadyExist() {
        Problem problem = problemWithApproach("Hash map");
        Question existing = new Question();
        existing.setQuestionType("PATTERN");

        when(questionRepository.existsByProblemId(problem.getId())).thenReturn(true);
        when(questionRepository.findByProblemId(problem.getId())).thenReturn(List.of(existing));

        List<Question> result = service.generateQuestionsForProblem(problem);

        assertThat(result).hasSize(1);
        // OpenAI should not have been called
        verifyNoInteractions(mockWebClient);
    }

    @Test
    void shouldGenerateAndSaveQuestionsWhenNoneExistForProblem() {
        Problem problem = problemWithApproach("Hash map");
        QuizGenerationService spyService = spy(service);

        when(questionRepository.existsByProblemId(problem.getId())).thenReturn(false);
        // Stub the HTTP call so the test doesn't need a live OpenAI key
        doReturn(buildEnvelopeWithContent(VALID_QUESTIONS_JSON))
                .when(spyService).callOpenAiApi(anyString());
        when(questionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Question> result = spyService.generateQuestionsForProblem(problem);

        assertThat(result).hasSize(4);
        verify(questionRepository).saveAll(anyList());
    }

    @Test
    void shouldReturnEmptyListWhenOpenAiReturnsUnparseableContent() {
        Problem problem = problemWithApproach("Hash map");
        QuizGenerationService spyService = spy(service);

        when(questionRepository.existsByProblemId(problem.getId())).thenReturn(false);
        doReturn(buildEnvelopeWithContent("this is not json"))
                .when(spyService).callOpenAiApi(anyString());

        List<Question> result = spyService.generateQuestionsForProblem(problem);

        assertThat(result).isEmpty();
        verify(questionRepository, never()).saveAll(anyList());
    }

    // -----------------------------------------------------------------------
    // buildOpenAiRequestBody tests
    // -----------------------------------------------------------------------

    @Test
    void shouldBuildRequestBodyWithCorrectModelAndUserRole() throws Exception {
        String body = service.buildOpenAiRequestBody("test prompt");

        var root = new ObjectMapper().readTree(body);
        assertThat(root.path("model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(root.path("messages").path(0).path("role").asText()).isEqualTo("user");
        assertThat(root.path("messages").path(0).path("content").asText()).isEqualTo("test prompt");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Problem problemWithApproach(String approach) {
        Problem p = new Problem();
        p.setId(1L);
        p.setTitle("Two Sum");
        p.setDifficulty("Easy");
        p.setTopic("arrays");
        p.setSolutionApproach(approach);
        return p;
    }

    /** Wraps a JSON array string in an OpenAI chat completion envelope. */
    private String buildEnvelopeWithContent(String content) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            // Serialize the content string as a proper JSON string value (handles quotes/newlines)
            String escapedContent = mapper.writeValueAsString(content);
            return """
                    {"choices":[{"message":{"role":"assistant","content":%s}}]}
                    """.formatted(escapedContent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
