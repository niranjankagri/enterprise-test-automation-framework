# One Dockerfile, three stages:
#   build - compiles everything once (dependencies cached in this layer)
#   app   - the application under test as a small JRE image
#   tests - the framework and tests; runs a suite against the app and a Selenium Grid
#
# Build context is the repository root:
#   docker build -f docker/Dockerfile --target app   -t shop-app .
#   docker build -f docker/Dockerfile --target tests -t shop-tests .

FROM maven:3.9.16-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584 AS build
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

FROM eclipse-temurin:17.0.20.1_1-jre-noble@sha256:07fa0356cf354c5252947575f42ed42aa6fd921f36438b7bc77ad81c31c537dd AS app
WORKDIR /app
COPY --from=build /workspace/demo-app/target/demo-app.jar ./demo-app.jar
# Keep this runtime agent version aligned with jacoco-maven-plugin.version in the parent POM.
COPY --from=build /root/.m2/repository/org/jacoco/org.jacoco.agent/0.8.15/org.jacoco.agent-0.8.15-runtime.jar ./jacocoagent.jar
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
