# EasyOps 后端「内网离线部署」说明

目标环境：**JDK 8**（JRE 8 亦可运行，但重新打包需 JDK 8）。无需联网。

## 一、依赖如何「工程内私有化」

Maven 没有前端那种扁平的 `node_modules` 文件夹，它的依赖库是「本地仓库」
（按 `groupId/artifactId/version` 分层、带 POM 元数据）。本工程已把完整构建
依赖导出到 **`backend/local-repo/`**（~149MB），等价于前端的 node_modules。

> 运行后端本身**不依赖 Maven、不依赖网络**——Spring Boot 的 fat jar 已把全部
> 依赖打进 `BOOT-INF/lib`。`local-repo` 只在你「改代码后重新打包」时才需要。

## 二、拷贝到内网的内容

### 方案 A：只运行（最小拷贝）
- `backend/server/target/ops-platform-server-1.0.0-SNAPSHOT.jar`（已构建好，Java 8 字节码）
- `backend/server/data/`（**必须带**，H2 数据库，否则数据丢失/重新初始化）
- `backend/start-prod.sh`
- 内网需装 **JDK 8**

启动：`cd backend && ./start-prod.sh`

### 方案 B：运行 + 可改码重打包（完整拷贝）
把整个 `backend/` 目录拷过去（含 `local-repo/`、`common/`、`server/`、`agent/`、源码、脚本）。
- 内网需装 **JDK 8** 与 **Maven 3.9+**
- 改完代码重新打包：`cd backend && ./build-offline.sh`（断网，`-o` 模式，只用 `local-repo`）
- 再启动：`./start-prod.sh`

## 三、已验证（本机，仅用 local-repo + `-o` 离线模式）

```
mvn -o -Dmaven.repo.local=backend/local-repo clean package -DskipTests
→ Reactor SUCCESS: common / server / agent
→ server jar 字节码 major version = 52 (Java 8) ✅
```

## 四、本次为适配 JDK 8 修复的测试代码 bug（不影响运行，但会阻塞 JDK 8 下整包构建）

测试代码误用了 Java 9/11 API，在 JDK 8 下 `mvn package` 会编译失败：
- `server/.../AIAnalyzeControllerTest.java`：`Map.of` → `Collections.singletonMap`
- `server/.../FileControllerTest.java`：`Map.of(...)` → `new HashMap` + `put`
- `agent/.../FileCommanderTest.java`、`LogCommanderTest.java`：`Path.of` → `Paths.get`

> 注意：`start.sh`（原开发脚本）写死 `$HOME/.jdk8/Contents/Home`（macOS 路径），
> 内网 Linux 请用 `start-prod.sh`（使用系统 `java`）。

## 五、Agent 部署提示

- Agent 在开发环境以 Docker 容器运行；内网若沿用 Docker，需把 agent 镜像导入。
- 也可直接用 `agent/target/easy-ops-agent-1.0.0-SNAPSHOT.jar` 以 `java -jar` 方式在目标主机运行
  （已构建为 Spring Boot fat jar，同样 Java 8 字节码）。

## 六、部署健康检查开关（重要）

部署完成后，Server 默认会对每个目标节点做一次「健康检查」判断应用是否真起来。
此前该检查是**硬编码**的：必须应用监听 `8080` 端口、有 `/hello` 接口、且返回内容包含
`Hello` 或 `DEPLOYED`，否则判为失败（5 次重试后仍失败 → 部署失败）。这意味着**你的工程
若没有健康地址，部署会一直失败**。

现已改为「**项目级可配置 + 可关闭**」：

- 在「项目管理」表单里新增「部署后健康检查」开关（默认开启）。
- 开启时可配置：端口 / 路径 / 关键字（逗号分隔，响应含任一关键字即视为健康）。默认值即
  原来的 `8080` / `/hello` / `Hello,DEPLOYED`，对 `demo-test-app` 等原样兼容。
- **关闭后：跳过健康检查，部署直接判定成功** —— 适用于你的工程当前没有健康地址的场景。

字段存储在 `project_info` 表，迁移脚本 `schema.sql` 已用 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`
自动加列（`health_check_enabled` / `health_check_port` / `health_check_path` /
`health_check_keyword`，带默认值），对已有数据零破坏，重启即生效。

前端表单位置：`frontend/src/views/ProjectFormView.vue`（开关 + 端口/路径/关键字三个输入）。

## 七、Agent 启动逻辑 bug 修复（与你之前「健康检查未通过」强相关）

**根因**：Agent 执行部署时生成的 `start.sh` 含 `> logs/startup.log` 重定向，但部署目录下
**没有 `logs/` 子目录**，导致重定向失败、`java` 进程根本没启动 → 8080 无响应 → 健康检查
5 次全失败 → 部署失败（表面现象就是「启动应用 200 OK，但健康检查 ❌」）。

**修复**：`backend/agent/.../ProcessController.start()` 在启动前先 `mkdir -p logs`，并用 `setsid`
让应用彻底脱离 Agent 进程组、稳定运行：

```bash
cd "deployDir" && mkdir -p logs && setsid sh start.sh > /dev/null 2>&1 < /dev/null &
```

`backend/agent/target/easy-ops-agent-1.0.0-SNAPSHOT.jar` 已含此修复（JDK 8）。
内网部署时**请务必使用含此修复的 agent jar / 镜像**，否则老版本仍有此 bug。

> 验证：本机复现「先杀掉应用 → 关闭项目健康检查 → 立即部署」→ 部署日志显示
> `⏭️ 健康检查已关闭，跳过检查，直接判定成功`，且 `start.sh` 能正常拉起应用。

