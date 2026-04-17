Read PROJECT_CONTEXT.md in this directory first. That is the master blueprint for this entire project.

We are starting Phase 1: Project Setup + LeetCode API Integration.

Do the following in order:

1. Initialize a Spring Boot 3.2.x project using Maven with these dependencies:
   - spring-boot-starter-web
   - spring-boot-starter-data-jpa
   - postgresql driver
   - spring-boot-starter-test
   - jackson-databind (for JSON parsing)
   - spring-boot-starter-webflux (for WebClient to call LeetCode API)

2. Create the pom.xml with groupId "com.interviewblitz" and artifactId "interviewblitz". Set Java version to 17. Author in pom.xml should be "Neeraj Chowdary Mamillapalli".

3. Create a .gitignore for Java/Maven/Spring Boot projects. Include: target/, .idea/, *.iml, .DS_Store, .env, application-local.properties.

4. Create application.properties with PostgreSQL config:
   - Database name: interviewblitz
   - spring.datasource.url=jdbc:postgresql://localhost:5432/interviewblitz
   - spring.datasource.username=postgres
   - spring.datasource.password=postgres
   - spring.jpa.hibernate.ddl-auto=update
   - spring.jpa.show-sql=true
   - server.port=8080

5. Create the main application class: InterviewBlitzApplication.java

6. Create the Problem entity (model/Problem.java) matching the schema in PROJECT_CONTEXT.md:
   - Fields: id, leetcodeId, title, difficulty, topic, description, solutionApproach, syncedAt
   - Use JPA annotations (@Entity, @Table, @Id, @GeneratedValue, @Column)
   - Add a clear class-level comment explaining what this entity represents

7. Create ProblemRepository (repository/ProblemRepository.java):
   - Extend JpaRepository<Problem, Long>
   - Add: Optional<Problem> findByLeetcodeId(Integer leetcodeId)
   - Add: List<Problem> findByTopic(String topic)
   - Add: List<Problem> findByDifficulty(String difficulty)

8. Create LeetCodeSyncService (service/LeetCodeSyncService.java):
   - Use WebClient to call LeetCode's GraphQL API at https://leetcode.com/graphql
   - GraphQL query to get solved problems for a username:
     ```
     query { matchedUser(username: "%s") { submitStats: submitStatsGlobal { acSubmissionNum { difficulty count } } } }
     ```
   - Also fetch the full problem list the user has solved:
     ```
     query { matchedUser(username: "%s") { submitStats: submitStatsGlobal { acSubmissionNum { difficulty count } } } recentAcSubmissionList(username: "%s", limit: 500) { title titleSlug } }
     ```
   - For each solved problem, also fetch its details (difficulty, topic tags) using:
     ```
     query { question(titleSlug: "%s") { questionId title difficulty topicTags { name } content } }
     ```
   - Map LeetCode topic tags to simplified categories: 
     - "Tree", "Binary Tree", "Binary Search Tree" → "trees"
     - "Dynamic Programming" → "dp"  
     - "Graph", "Breadth-First Search", "Depth-First Search" → "graphs"
     - "Array", "Hash Table" → "arrays"
     - "Two Pointers" → "two-pointers"
     - "Sliding Window" → "sliding-window"
     - "Stack", "Monotonic Stack" → "stacks"
     - "Linked List" → "linked-lists"
     - "Binary Search" → "binary-search"
     - "Heap (Priority Queue)" → "heaps"
     - "Backtracking" → "backtracking"
     - "Greedy" → "greedy"
     - "Trie" → "tries"
     - "Union Find" → "union-find"
     - "Sorting" → "sorting"
     - Everything else → "other"
   - Use the FIRST matching topic tag as the primary topic
   - Skip problems that are already in the database (check by leetcodeId)
   - Add clear comments on every method explaining what it does
   - Handle errors gracefully — if LeetCode API is down, return a clear error message
   - Add a 1-second delay between individual problem detail fetches to avoid rate limiting

9. Create ProblemController (controller/ProblemController.java):
   - GET /api/problems/sync/{username} → calls LeetCodeSyncService, returns count of new problems synced
   - GET /api/problems → returns all problems
   - GET /api/problems/topics → returns problems grouped by topic as a Map<String, List<Problem>>
   - Add clear comments on each endpoint

10. Create LeetCodeSyncServiceTest (test/.../service/LeetCodeSyncServiceTest.java):
    - Test topic mapping logic
    - Test that duplicate problems are skipped
    - Test error handling when API returns bad data
    - Use descriptive test names like shouldMapTreeTagToTreesTopic()

11. Create the PostgreSQL database. Add a note in the README that the user needs to run:
    ```
    createdb interviewblitz
    ```

12. Make sure the application compiles and starts with: mvn spring-boot:run

Follow ALL code style rules from PROJECT_CONTEXT.md. No AI attribution anywhere. Author is Neeraj Chowdary Mamillapalli.
