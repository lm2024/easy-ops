# EasyOps 项目长期记忆

## 项目基本信息
- 项目名称：EasyOps 分布式运维部署管理平台
- 架构：Server-Agent 分布式架构
- 后端：Java 8 + Spring Boot 2.7.18 + MyBatis + H2 嵌入式数据库
- 前端：Vue 3 + Vite 5 + TypeScript + Ant Design Vue
- Shell：Git Bash (Windows)，不要用 PowerShell

## 启动要点
- 后端 Server 端口 8081，前端端口 3000
- 必须设置 JWT_SECRET 环境变量，否则 Server 启动失败
- 启动命令示例（Git Bash）：`JWT_SECRET=easyops-jwt-secret-key-2026-opsplatform java -jar backend/server/target/ops-platform-server-1.0.0-SNAPSHOT.jar`
- 前端启动：`cd frontend && npm run dev`
- 默认管理员：admin / admin123
- H2 数据库文件在 backend/server/data/ 目录，启动失败时可删除重建

## Docker Agent 部署
- 3个Agent容器: agent-1(2123), agent-2(2124), agent-3(2125)
- Demo app端口映射: agent-1→8080, agent-2→9080, agent-3→10080
- 关键修复：心跳上报需使用AGENT_HOST_IP和AGENT_HOST_PORT环境变量，让Server知道外部可达的IP和端口
- 之前Bug：所有Agent上报IP=127.0.0.1 Port=2123(容器内部)，Server把所有请求都发到了agent-1
- docker-compose.yml中每个Agent需配置AGENT_HOST_IP和AGENT_HOST_PORT

## 用户偏好
- 本地 JDK 是 17，项目配置 source/target=1.8，JDK 17 可以编译运行
- 用户已安装 Docker
- 执行命令优先使用 Git Bash 语法，不要使用 PowerShell
