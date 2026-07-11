FROM eclipse-temurin:8-jre

WORKDIR /app

COPY easy-ops-agent-*.jar agent.jar

ENV AGENT_TOKEN=agent-token-1
ENV AGENT_NODE_NAME=agent-1
ENV AGENT_SERVER_URL=http://host.docker.internal:8081/api
ENV JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true

EXPOSE 2123

ENTRYPOINT ["java", "-jar", "agent.jar"]
