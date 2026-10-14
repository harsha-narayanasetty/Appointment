# Build stage: compile the application into a single runnable jar
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# Run stage: small JRE image, non-root user
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home appuser
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
