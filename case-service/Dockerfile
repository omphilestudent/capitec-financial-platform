# syntax=docker/dockerfile:1.7
#
# Build stage: Maven compiles and packages the Spring Boot jar with tests skipped
# (tests run in CI and locally). The optional BuildKit secret "truststore" lets builds
# behind a TLS-intercepting proxy trust its root CA without baking it into the image:
#   docker build --secret id=truststore,src=/path/to/truststore.jks .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=truststore,required=false \
    sh -c 'if [ -f /run/secrets/truststore ]; then export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/run/secrets/truststore -Djavax.net.ssl.trustStorePassword=${TRUSTSTORE_PASSWORD:-changeit}"; fi && mvn -B -q package -DskipTests'

# Runtime stage: JRE only, non-root user, no build tooling or sources.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/*-SNAPSHOT.jar /app/app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8084
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
  CMD wget -q -O /dev/null http://127.0.0.1:8084/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
