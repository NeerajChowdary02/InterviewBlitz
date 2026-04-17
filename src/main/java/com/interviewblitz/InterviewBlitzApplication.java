package com.interviewblitz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the InterviewBlitz application.
 * Starts the Spring Boot server that powers the spaced-repetition quiz API.
 */
@SpringBootApplication
public class InterviewBlitzApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewBlitzApplication.class, args);
    }
}
