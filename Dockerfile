# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./

RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true

COPY src ./src
COPY application.yml ./
COPY logback-spring.xml ./

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN groupadd --system app && useradd --system --gid app --create-home app

RUN mkdir -p /opt/files /app/logs && chown -R app:app /app /opt/files

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
COPY --chown=app:app application.yml /app/application.yml
COPY --chown=app:app logback-spring.xml /app/logback-spring.xml

USER app

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
