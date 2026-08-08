FROM eclipse-temurin:8-jdk

# 安装 procps（提供 ps/top 命令，进程检测依赖 ps 查找应用 PID）
# 安装 tini：作为 PID 1 的 init 进程，负责回收 Agent 的子进程（僵尸），
# 避免 Agent 自身当 PID 1 时无法回收被停掉的应用进程导致僵尸堆积。
RUN apt-get update && apt-get install -y --no-install-recommends procps tini && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 创建数据目录
RUN mkdir -p /app/data

COPY easy-ops-agent-*.jar agent.jar
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENV AGENT_TOKEN=agent-token-1
ENV AGENT_NODE_NAME=agent-1
ENV AGENT_SERVER_URL=http://host.docker.internal:8081/api
ENV JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true

EXPOSE 2123

# tini 作为 PID 1 负责信号转发与僵尸回收；agent 在 entrypoint.sh 中以循环方式运行，
# 这样 Agent 升级/崩溃重启时，被 detach 的应用进程会被 tini 接管而非随容器一起被杀。
ENTRYPOINT ["/usr/bin/tini", "--", "/entrypoint.sh"]
