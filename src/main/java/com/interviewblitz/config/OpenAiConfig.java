package com.interviewblitz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures a WebClient bean pre-loaded with the OpenAI base URL and
 * Authorization header. The API key is read from the OPENAI_API_KEY
 * environment variable — it is never hardcoded.
 */
@Configuration
public class OpenAiConfig {

    @Value("${openai.api.key:}")
    private String apiKey;

    /** WebClient wired for all OpenAI API calls — inject with @Qualifier("openAiWebClient"). */
    @Bean("openAiWebClient")
    public WebClient openAiWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                // 4 MB buffer — OpenAI responses with long explanations can exceed the 256 KB default
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }
}
