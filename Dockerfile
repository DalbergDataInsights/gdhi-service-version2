FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./

RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY . .
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app

COPY --from=builder /workspace/build/libs/gdhi-service-*.jar /app/app.jar
COPY application.yml /app/application.yml
COPY logback-spring.xml /app/logback-spring.xml

RUN mkdir -p /opt/files /app/logs && chown -R app:app /app /opt/files

EXPOSE 8888

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
