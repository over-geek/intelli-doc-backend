FROM maven:3.9.11-eclipse-temurin-21 AS build
ARG APPLICATIONINSIGHTS_AGENT_VERSION=3.7.8
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src src
RUN ./mvnw --batch-mode clean package -DskipTests
RUN curl -fsSL -o /workspace/applicationinsights-agent.jar "https://repo1.maven.org/maven2/com/microsoft/azure/applicationinsights-agent/${APPLICATIONINSIGHTS_AGENT_VERSION}/applicationinsights-agent-${APPLICATIONINSIGHTS_AGENT_VERSION}.jar"

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system intellidoc && useradd --system --gid intellidoc --home-dir /app intellidoc

WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar
COPY --from=build /workspace/applicationinsights-agent.jar /opt/applicationinsights/applicationinsights-agent.jar
COPY src/main/resources/applicationinsights.json /opt/applicationinsights/applicationinsights.json
COPY docker/entrypoint.sh /opt/applicationinsights/entrypoint.sh
RUN chmod +x /opt/applicationinsights/entrypoint.sh

USER intellidoc
EXPOSE 8080

ENV APPLICATIONINSIGHTS_ENABLED=true
ENV APPLICATIONINSIGHTS_ROLE_NAME=intellidoc-backend
ENV INTELLIDOC_ENVIRONMENT=container
ENV APPLICATION_VERSION=0.0.1-SNAPSHOT

ENTRYPOINT ["/opt/applicationinsights/entrypoint.sh"]
