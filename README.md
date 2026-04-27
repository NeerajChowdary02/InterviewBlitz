# InterviewBlitz

A spaced repetition quiz app that syncs your solved LeetCode problems and generates multiple-choice questions to reinforce pattern recognition, time complexity analysis, and edge case awareness.

---

## Features

- **LeetCode sync** — pulls your accepted submissions via the LeetCode GraphQL API and stores them locally
- **Quiz generation** — uses OpenAI GPT-4o-mini to generate four questions per problem covering pattern, complexity, approach, and edge cases
- **Spaced repetition** — SM-2 algorithm schedules each problem for review based on your answer history
- **Topic analytics** — tracks accuracy and review progress across 17 NeetCode-style categories (Sliding Window, Two Pointers, Trees, Graphs, etc.)
- **Mobile-first PWA** — dark-themed progressive web app installable on iOS and Android, works offline
- **Authentication** — HTTP Basic auth protects all API endpoints; credentials are environment-variable driven
- **Docker support** — multi-stage Dockerfile produces a minimal runtime image; Docker Compose orchestrates the app and Postgres together

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL 16 |
| AI Integration | OpenAI API (GPT-4o-mini) |
| Frontend | HTML / CSS / JavaScript (vanilla PWA) |
| Build | Maven |
| Containerization | Docker & Docker Compose |
| Testing | JUnit 5, Mockito, AssertJ |

---

## Architecture

The application follows a standard layered architecture:

```
Controller → Service → Repository → PostgreSQL
```

**Controllers** handle HTTP routing and request/response mapping. **Services** contain all business logic. **Repositories** use Spring Data JPA for database access.

Key services:

- **`LeetCodeSyncService`** — fetches accepted submissions and topic tags from the LeetCode GraphQL API, maps them to NeetCode categories, and persists new problems
- **`QuizGenerationService`** — calls the OpenAI chat completions API to generate structured multiple-choice questions for each problem
- **`SpacedRepetitionService`** — implements the SM-2 algorithm to compute next review dates and update ease factors after each quiz attempt
- **`StatsService`** — aggregates per-topic accuracy, review counts, and weak area identification across all attempts

---

## Getting Started

### Prerequisites

- Java 17
- Maven 3.9+
- PostgreSQL 16
- Docker & Docker Compose *(optional)*

### Option 1 — Local Development

```bash
git clone https://github.com/NeerajChowdary02/InterviewBlitz.git
cd InterviewBlitz

# Create your .env file (see Environment Variables section)
cp .env.example .env   # or create from scratch

# Create the database
psql -U postgres -c "CREATE DATABASE interviewblitz;"

# Run the app
./mvnw spring-boot:run
```

The app will be available at `http://localhost:8080`.

### Option 2 — Docker

```bash
git clone https://github.com/NeerajChowdary02/InterviewBlitz.git
cd InterviewBlitz

# Create your .env file (see Environment Variables section)
cp .env.example .env

docker compose up --build
```

Docker Compose starts a Postgres container and the application container. The app waits for Postgres to be healthy before starting.

---

## Environment Variables

Create a `.env` file in the project root. This file is gitignored and never committed.

| Variable | Description |
|---|---|
| `DB_PASSWORD` | PostgreSQL password |
| `DB_HOST` | Database host (default: `localhost`, Docker: `db`) |
| `DB_NAME` | Database name (default: `interviewblitz`) |
| `DB_USERNAME` | Database user (default: `postgres`) |
| `OPENAI_API_KEY` | OpenAI API key for question generation |
| `LEETCODE_SESSION` | LeetCode session cookie for syncing submissions |
| `LEETCODE_CSRF` | LeetCode CSRF token for syncing submissions |
| `APP_USERNAME` | Username for HTTP Basic auth login |
| `APP_PASSWORD` | Password for HTTP Basic auth login |

---

## API Endpoints

All endpoints under `/api/**` require HTTP Basic authentication.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/sync/{username}` | Sync accepted LeetCode submissions for a user |
| `PUT` | `/api/sync/topics/{username}` | Remap topic categories using the LeetCode public API |
| `POST` | `/api/quiz/generate/{problemId}` | Generate quiz questions for a problem |
| `GET` | `/api/quiz/questions/{problemId}` | Fetch stored questions for a problem |
| `GET` | `/api/quiz/next` | Get the next problem due for review |
| `POST` | `/api/quiz/submit` | Submit an answer and update spaced repetition state |
| `GET` | `/api/quiz/due-count` | Get the count of problems due for review today |
| `GET` | `/api/stats/overview` | Get overall solve count, accuracy, and review stats |
| `GET` | `/api/stats/topics` | Get per-topic accuracy and review progress |
| `GET` | `/api/stats/weak-areas` | Get topics where accuracy is below 70% |

---

## Spaced Repetition

InterviewBlitz uses the SM-2 algorithm to schedule problem reviews. After each quiz, a quality score (0–4) is computed from your answer. Correct answers progressively increase the review interval — from 1 day to 6 days to weeks — while incorrect answers reset the interval to 1 day. An ease factor per problem adjusts over time based on performance, so problems you consistently struggle with come back more frequently.

---

## Project Structure

```
src/main/java/com/interviewblitz/
├── InterviewBlitzApplication.java       # Spring Boot entry point
├── config/
│   ├── OpenAiConfig.java                # OpenAI WebClient bean
│   └── SecurityConfig.java             # HTTP Basic auth, CORS config
├── controller/
│   ├── ProblemController.java           # /api/sync endpoints
│   ├── QuizController.java             # /api/quiz endpoints
│   ├── QuizSessionController.java      # Quiz session lifecycle
│   └── StatsController.java            # /api/stats endpoints
├── dto/
│   ├── SubmitAnswerRequest.java
│   ├── SubmitAnswerResponse.java
│   └── TopicStatsDto.java
├── model/
│   ├── Problem.java                     # LeetCode problem entity
│   ├── Question.java                    # Multiple-choice question entity
│   ├── QuizAttempt.java                # Per-answer attempt record
│   └── UserProgress.java               # SM-2 state per problem
├── repository/
│   ├── ProblemRepository.java
│   ├── QuestionRepository.java
│   ├── QuizAttemptRepository.java
│   └── UserProgressRepository.java
└── service/
    ├── LeetCodeSyncService.java         # GraphQL sync + topic mapping
    ├── QuizGenerationService.java       # OpenAI question generation
    ├── SpacedRepetitionService.java     # SM-2 scheduling
    └── StatsService.java               # Topic analytics + weak areas
```
