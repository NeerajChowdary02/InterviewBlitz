# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy dependency descriptors first for layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build the JAR
COPY src ./src
RUN mvn clean package -DskipTests -q

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the fat JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Default environment variables — all overridable at runtime
ENV DB_HOST=localhost \
    DB_PORT=5432 \
    DB_NAME=interviewblitz \
    DB_USERNAME=postgres \
    DB_PASSWORD=postgres \
    OPENAI_API_KEY= \
    LEETCODE_SESSION= \
    LEETCODE_CSRF= \
    APP_USERNAME=admin \
    APP_PASSWORD=interviewblitz

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
