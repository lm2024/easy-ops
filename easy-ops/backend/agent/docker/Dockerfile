FROM eclipse-temurin:8-jdk

# 安装 procps（提供 ps/top 命令），Agent 进程检测依赖 ps 查找应用 PID
RUN apt-get update && apt-get install -y --no-install-recommends procps && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY easy-ops-agent-*.jar agent.jar
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENV AGENT_TOKEN=agent-token-1
ENV AGENT_NODE_NAME=agent-1
ENV AGENT_SERVER_URL=http://host.docker.internal:8081/api
ENV JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true

EXPOSE 2123

ENTRYPOINT ["/entrypoint.sh"]
