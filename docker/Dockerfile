# One Dockerfile, three stages:
#   build - compiles everything once (dependencies cached in this layer)
#   app   - the application under test as a small JRE image
#   tests - the framework and tests; runs a suite against the app and a Selenium Grid
#
# Build context is the repository root:
#   docker build -f docker/Dockerfile --target app   -t shop-app .
#   docker build -f docker/Dockerfile --target tests -t shop-tests .

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
# POMs first: dependency downloads are cached until a POM changes
COPY pom.xml ./
COPY demo-app/pom.xml demo-app/
COPY automation/pom.xml automation/
# Checkstyle rules: the code-quality gate runs in every Maven build, also in the container
COPY config config
COPY demo-app/src demo-app/src
COPY automation/src automation/src
RUN mvn -B -q -DskipTests package \
    && mvn -B -q -DskipTests test-compile

FROM eclipse-temurin:17-jre AS app
WORKDIR /app
COPY --from=build /workspace/demo-app/target/demo-app.jar ./demo-app.jar
# Inside containers: qa-style ports, database reachable from the test container
ENV PORT=8081 \
    DB_TCP_PORT=9093 \
    DB_ALLOW_REMOTE=true
EXPOSE 8081 9093
ENTRYPOINT ["java", "-jar", "/app/demo-app.jar"]

FROM build AS tests
# Defaults for a containerised run; docker-compose and CI override them
ENV ENV=qa \
    HEADLESS=true \
    EXECUTION=remote
ENTRYPOINT ["mvn", "-B", "test"]
CMD ["-Dsuite=smoke"]
