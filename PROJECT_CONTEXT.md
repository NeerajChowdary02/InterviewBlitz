# InterviewBlitz — Project Context

## Author
- **Name:** Neeraj Chowdary Mamillapalli
- **Email:** neerajpurav123@gmail.com
- **GitHub:** github.com/NeerajChowdary02
- **LinkedIn:** linkedin.com/in/neerajchowdarymamillapalli

## What Is This?
A spaced-repetition quiz tool that helps software engineers retain LeetCode problem-solving knowledge. It syncs your solved LeetCode problems, uses AI to auto-generate multiple-choice questions, and schedules reviews based on your weak areas.

## Why It Exists
Built to solve a personal problem: after solving 250+ LeetCode problems across 300+ hours of interview prep, patterns kept slipping. This tool turns solved problems into quick daily quizzes to maintain retention without re-solving.

## Resume Bullets (what MUST work for the project to be valid)
1. "Built a spaced repetition study app that syncs solved LeetCode problems via LeetCode's GraphQL API and uses OpenAI to auto-generate multiple choice questions per problem, targeting pattern recognition, time complexity, and edge cases."
2. "Tracks retention rate per topic (trees, DP, graphs, etc.) and schedules review sessions based on which areas need reinforcement, deployed on AWS using Docker."

## Tech Stack
| Technology | Version | Purpose |
|---|---|---|
| Java | 17 | Primary language |
| Spring Boot | 3.2.x | Backend framework |
| PostgreSQL | 16 | Relational database |
| OpenAI API | GPT-4o-mini | Quiz question generation |
| Docker | Latest | Containerization |
| AWS EC2 | t2.micro (free tier) | Deployment |
| HTML/CSS/JS | Vanilla | PWA frontend |
| Maven | 3.9.x | Build tool |
| JUnit 5 | Latest | Testing |

## LeetCode Username
codingknight2625

## Project Structure
```
InterviewBlitz/
├── PROJECT_CONTEXT.md
├── README.md
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .gitignore
├── src/
│   ├── main/
│   │   ├── java/com/interviewblitz/
│   │   │   ├── InterviewBlitzApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── ProblemController.java
│   │   │   │   ├── QuizController.java
│   │   │   │   └── StatsController.java
│   │   │   ├── service/
│   │   │   │   ├── LeetCodeSyncService.java
│   │   │   │   ├── QuizGenerationService.java
│   │   │   │   ├── SpacedRepetitionService.java
│   │   │   │   └── StatsService.java
│   │   │   ├── repository/
│   │   │   │   ├── ProblemRepository.java
│   │   │   │   ├── QuestionRepository.java
│   │   │   │   ├── QuizAttemptRepository.java
│   │   │   │   └── UserProgressRepository.java
│   │   │   ├── model/
│   │   │   │   ├── Problem.java
│   │   │   │   ├── Question.java
│   │   │   │   ├── QuizAttempt.java
│   │   │   │   └── UserProgress.java
│   │   │   ├── dto/
│   │   │   │   ├── LeetCodeProblemDto.java
│   │   │   │   ├── QuizRequestDto.java
│   │   │   │   ├── QuizResponseDto.java
│   │   │   │   └── StatsResponseDto.java
│   │   │   ├── config/
│   │   │   │   ├── OpenAiConfig.java
│   │   │   │   └── WebConfig.java
│   │   │   └── scheduler/
│   │   │       └── ReviewScheduler.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-prod.properties
│   │       └── static/
│   │           ├── index.html
│   │           ├── app.js
│   │           ├── styles.css
│   │           ├── manifest.json
│   │           └── sw.js
│   └── test/
│       └── java/com/interviewblitz/
│           ├── service/
│           │   ├── LeetCodeSyncServiceTest.java
│           │   ├── QuizGenerationServiceTest.java
│           │   ├── SpacedRepetitionServiceTest.java
│           │   └── StatsServiceTest.java
│           └── controller/
│               ├── ProblemControllerTest.java
│               └── QuizControllerTest.java
```

## Database Schema
```sql
-- Stores synced LeetCode problems
CREATE TABLE problems (
    id BIGSERIAL PRIMARY KEY,
    leetcode_id INTEGER UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    difficulty VARCHAR(10) NOT NULL,        -- Easy, Medium, Hard
    topic VARCHAR(50) NOT NULL,             -- trees, dp, graphs, etc.
    description TEXT,
    solution_approach TEXT,
    synced_at TIMESTAMP DEFAULT NOW()
);

-- Stores AI-generated quiz questions
CREATE TABLE questions (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT REFERENCES problems(id),
    question_text TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    option_c TEXT NOT NULL,
    option_d TEXT NOT NULL,
    correct_option CHAR(1) NOT NULL,        -- A, B, C, or D
    explanation TEXT NOT NULL,
    question_type VARCHAR(30) NOT NULL,     -- PATTERN, COMPLEXITY, EDGE_CASE, APPROACH
    created_at TIMESTAMP DEFAULT NOW()
);

-- Tracks each quiz attempt
CREATE TABLE quiz_attempts (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT REFERENCES questions(id),
    selected_option CHAR(1) NOT NULL,
    is_correct BOOLEAN NOT NULL,
    attempted_at TIMESTAMP DEFAULT NOW()
);

-- Tracks per-problem spaced repetition state
CREATE TABLE user_progress (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT UNIQUE REFERENCES problems(id),
    ease_factor DOUBLE PRECISION DEFAULT 2.5,
    interval_days INTEGER DEFAULT 1,
    repetitions INTEGER DEFAULT 0,
    next_review_date DATE DEFAULT CURRENT_DATE,
    last_reviewed_at TIMESTAMP,
    correct_streak INTEGER DEFAULT 0,
    total_attempts INTEGER DEFAULT 0,
    total_correct INTEGER DEFAULT 0
);
```

## API Endpoints
```
GET    /api/problems/sync/{username}     → Sync solved problems from LeetCode
GET    /api/problems                      → List all synced problems
GET    /api/problems/topics               → List problems grouped by topic

POST   /api/quiz/generate/{problemId}     → Generate quiz for a specific problem
GET    /api/quiz/next                     → Get next problem due for review (spaced repetition)
POST   /api/quiz/submit                   → Submit quiz answers, update progress

GET    /api/stats/overview                → Overall retention stats
GET    /api/stats/topics                  → Retention breakdown by topic
GET    /api/stats/weak-areas              → Topics needing most review
```

## Spaced Repetition Algorithm (SM-2 Variant)
After each quiz attempt:
- If correct: increase interval (1 → 3 → 7 → 14 → 30 days), increase ease factor
- If wrong: reset interval to 1 day, decrease ease factor (min 1.3)
- Next review date = today + interval
- Problems with lower ease factors appear more frequently
- `/api/quiz/next` returns the problem with the earliest next_review_date

## OpenAI Prompt Strategy
For each problem, send to GPT-4o-mini:
```
You are a coding interview coach. Given this LeetCode problem and its solution approach, generate exactly 4 multiple-choice questions that test understanding WITHOUT requiring the person to write code.

Problem: {title} ({difficulty})
Topic: {topic}
Solution Approach: {approach}

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
]
```

## Code Style Rules
1. Clean method names that read like English — syncProblemsFromLeetCode() not syncLC()
2. Short methods that do ONE thing — no 100-line god methods
3. Proper Spring Boot layering — Controller → Service → Repository
4. No clever one-liners — explicit loops where clearer
5. Every class gets a top comment explaining what it does in plain English
6. Every public method gets a one-line comment explaining purpose
7. Complex logic gets inline comments explaining WHY not WHAT
8. JUnit tests for every service method
9. Test names read like sentences — shouldGenerateQuestionsForValidProblem()
10. No AI attribution ANYWHERE — no "generated by", "AI-assisted", "Co-authored-by" in code, comments, commits, or docs

## Build Phases
### Phase 1: Project Setup + LeetCode API Integration
- Initialize Spring Boot project with Maven
- Configure PostgreSQL connection
- Create Problem entity and repository
- Build LeetCodeSyncService that calls LeetCode GraphQL API
- Create ProblemController with /api/problems/sync/{username}
- Write JUnit tests for sync service
- Commit: "Initial project setup with LeetCode problem sync"

### Phase 2: Database Design + Entities
- Create all JPA entities (Problem, Question, QuizAttempt, UserProgress)
- Create all repositories
- Configure Hibernate auto-DDL for development
- Seed database with synced problems
- Commit: "Add database entities and repositories"

### Phase 3: OpenAI Quiz Generation
- Create OpenAiConfig with API key from environment variable
- Build QuizGenerationService that sends problem data to GPT-4o-mini
- Parse JSON response into Question entities
- Store generated questions in database
- Create QuizController with /api/quiz/generate/{problemId}
- Write JUnit tests with mocked OpenAI responses
- Commit: "Add AI-powered quiz question generation"

### Phase 4: Spaced Repetition Logic
- Implement SM-2 variant in SpacedRepetitionService
- Update UserProgress after each quiz attempt
- Build /api/quiz/next endpoint that returns due problems
- Write JUnit tests for interval calculations
- Commit: "Add spaced repetition scheduling"

### Phase 5: REST API Endpoints + Stats
- Build StatsService for retention calculations
- Create StatsController with overview/topics/weak-areas endpoints
- Wire up /api/quiz/submit to update progress
- Write integration tests
- Commit: "Add stats endpoints and quiz submission flow"

### Phase 6: PWA Frontend
- Build mobile-first quiz UI (HTML/CSS/JS)
- Problem sync page, quiz page, stats dashboard
- Add manifest.json and service worker for PWA
- Installable on iPhone home screen
- Commit: "Add PWA frontend"

### Phase 7: Docker Containerization
- Create Dockerfile for Spring Boot app
- Create docker-compose.yml with app + PostgreSQL
- Test full stack runs with docker-compose up
- Commit: "Add Docker containerization"

### Phase 8: AWS Deployment
- Launch EC2 t2.micro instance
- Install Docker on EC2
- Deploy with docker-compose
- Configure security groups (ports 80, 443, 8080)
- Commit: "Add production configuration"

### Phase 9: README + Polish
- Professional README with architecture diagram
- Setup instructions, screenshots, tech stack table
- Clean up any TODO comments
- Final commit: "Add documentation and polish"

## Git Commit Rules
- Author: Neeraj Chowdary Mamillapalli <neerajpurav123@gmail.com>
- No "Co-authored-by" trailers
- No AI tool references in commit messages
- Commit messages are descriptive: "Add spaced repetition scheduling" not "update files"
- One commit per phase minimum, can have sub-commits for large phases

## Environment Variables (never commit these)
```
OPENAI_API_KEY=sk-xxxxx
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/interviewblitz
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
```
