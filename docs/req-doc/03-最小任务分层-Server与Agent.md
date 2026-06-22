# 一体云运维平台 - 最小任务分层文档 (Server与Agent篇) v1.0

## 使用说明

本文档将整体需求拆解为最小原子任务。每个原子任务对应一个独立编码会话，任务之间通过依赖编号约束执行顺序。

- 任务命名规则：T-需求号-主任务号-原子任务号-描述
- 示例：T-01-01-01 初始化Maven多模块项目结构
- 依赖任务编号格式：依赖 T-xx-xx-xx

---

## 第一阶段：基础能力建设 (T-01-01 ~ T-01-08)

### T-01-01 初始化Maven多模块项目结构

依赖: 无
完成标准: 根目录存在 pom.xml，包含 server/agent/common 三个子模块，Maven clean/package 成功。

执行步骤:
1. 在 backend/ 目录下创建父 pom.xml，groupId=com.ops，artifactId=ops-platform-parent，packaging=pom；
2. 创建 common/pom.xml，artifactId=ops-platform-common，打包方式为 jar；
3. 创建 server/pom.xml，artifactId=ops-platform-server，打包方式为 jar，依赖 ops-platform-common；
4. 创建 agent/pom.xml，artifactId=ops-platform-agent，打包方式为 jar，依赖 ops-platform-common；
5. 父 pom.xml 中声明所有子模块，统一管理 Spring Boot 2.7.18、MyBatis 3.5.x、Hutool 5.8.x、Jackson 2.15.x、OSHI 6.x、Quartz 2.3.x、Spring Security Crypto 等依赖版本；
6. 执行 mvn clean package 验证项目结构正确。

### T-01-02 创建Spring Boot Server启动类与基础配置

依赖: T-01-01
完成标准: Server 启动类存在，启动成功无报错，默认端口 8081。

执行步骤:
1. 在 server/src/main/java/com/ops/server/ 下创建 ServerApplication.java，添加 @SpringBootApplication 注解；
2. 创建 resources/application.yml，配置 server.port=8081、spring.application.name=ops-server、logging.level.root=INFO、spring.profiles.active=dev；
3. 配置 server.tomcat.accesslog.enabled=true 开启访问日志；
4. 添加 Hibernate Validator 依赖 (spring-boot-starter-validation)；
5. 启动验证: java -jar server/target/ops-platform-server-1.0.0.jar 能正常启动。

### T-01-03 创建Spring Boot Agent启动类与基础配置

依赖: T-01-01
完成标准: Agent 启动类存在，启动成功无报错，默认端口 2123。

执行步骤:
1. 在 agent/src/main/java/com/ops/agent/ 下创建 AgentApplication.java，添加 @SpringBootApplication 注解；
2. 创建 resources/application.yml，配置 server.port=2123、spring.application.name=ops-agent、logging.level.root=INFO、server.servlet.context-path=/api；
3. 添加 Apache HttpClient 5.x 依赖 (用于 Server 向 Agent 发起 HTTP 请求)；
4. 启动验证: java -jar agent/target/ops-platform-agent-1.0.0.jar 能正常启动。

### T-01-04 创建common共享模块 (模型与枚举)

依赖: T-01-01
完成标准: common 模块编译通过，包含所有数据模型和枚举类。

执行步骤:
1. 在 common/src/main/java/com/ops/common/model/ 下创建所有数据模型类: NodeModel、ProjectModel、VersionModel、DeployModel、AlarmModel、UserModel、OperationLogModel、FileAccessLogModel，每个类包含所有字段及 getter/setter (使用 Lombok @Data)；
2. 在 common/src/main/java/com/ops/common/enums/ 下创建枚举类: NodeStatus (OFFLINE=0, ONLINE=1)、DeployStatus (PROCESSING=0, SUCCESS=1, FAILED=2, ROLLBACK=3)、FileType (YML, LOG, JAR)、FileAction (VIEW, EDIT, DOWNLOAD)、UserRole (ADMIN, OPERATOR)；
3. 在 common/src/main/java/com/ops/common/constant/ 下创建常量类: ErrorCode (统一错误码)、SystemConstant (系统常量)；
4. 在 common/src/main/java/com/ops/common/response/ 下创建统一响应类 Result<T> (code, message, data)；
5. 编译验证: mvn clean package -pl common。

### T-01-05 集成MyBatis + H2数据库

依赖: T-01-02
完成标准: Server 启动时自动创建 H2 数据库表，MyBatis 可正常执行 CRUD 操作。

执行步骤:
1. 在 server/pom.xml 添加 mybatis-spring-boot-starter、h2 依赖；
2. 在 application.yml 配置数据源: spring.datasource.url=jdbc:h2:./data/ops;AUTO_SERVER=TRUE;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE，driver-class-name=org.h2.Driver；
3. 创建 resources/db/schema.sql 包含所有建表语句 (7张核心表 + 1张文件访问审计表)；
4. 创建 resources/mapper/ 目录，创建所有 Mapper XML 文件 (NodeMapper.xml、ProjectMapper.xml、VersionMapper.xml、DeployMapper.xml、AlarmMapper.xml、UserMapper.xml、OperationLogMapper.xml、FileAccessLogMapper.xml)；
5. 在 ServerApplication 添加 @MapperScan("com.ops.server.mapper") 注解；
6. 启动验证: Server 启动后检查 data 目录下生成 H2 数据库文件，访问 H2 Web Console 确认表结构。

### T-01-06 实现Token认证拦截器

依赖: T-01-04
完成标准: 所有需要认证的接口拦截通过，Token 不合法时返回 401。

执行步骤:
1. 在 server/src/main/java/com/ops/server/interceptor/ 下创建 AuthInterceptor.java，实现 HandlerInterceptor 接口；
2. 从请求头读取 X-Token (Agent 接口) 和 Authorization (用户接口)；
3. Agent 接口校验: 根据请求节点IP查询 node_info 表获取 Token 进行比对；
4. 用户接口校验: 解析 JWT Token 验证用户身份 (使用 Nimbus JOSE+JWT 库)；
5. 在 server 配置类中注册拦截器，配置排除路径 (登录接口、心跳接口不需认证)；
6. 测试验证: 使用错误 Token 请求受保护接口返回 401。

### T-01-07 实现WebSocket核心配置

依赖: T-01-02
完成标准: Server 启动后可建立 WebSocket 连接，能收发消息。

执行步骤:
1. 在 server/src/main/java/com/ops/server/websocket/ 下创建 WebSocketConfig.java，实现 WebSocketConfigurer 接口；
2. 注册三个 WebSocket 端点: /ws/console、/ws/deploy、/ws/monitor；
3. 为每个端点添加 WebSocketInterceptor 进行 Token 校验；
4. 在 agent/src/main/java/com/ops/agent/socket/ 下创建 WebSocketClientManager.java，使用 StandardWebSocketClient 建立与服务端的 WebSocket 连接；
5. 实现消息路由机制，根据 projectId 和 nodeId 分发消息；
6. 测试验证: 使用 wscat 工具连接 Server WebSocket 端点，发送/接收消息。

### T-01-08 实现统一异常处理与日志框架

依赖: T-01-06
完成标准: 运行时异常被统一捕获并返回标准化错误信息，不暴露敏感堆栈。

执行步骤:
1. 在 server/src/main/java/com/ops/server/exception/ 下创建自定义异常类 BusinessException (code, message) 和系统异常 SystemException；
2. 创建 GlobalExceptionHandler.java，使用 @RestControllerAdvice 注解，捕获所有异常；
3. 处理场景: 业务异常返回业务错误码，参数校验异常返回参数错误，其他异常返回系统错误；
4. 在 agent 端也创建类似的异常处理；
5. 配置 Logback 日志输出到 /logs 目录，按天分割，保留30天。

---

## 第二阶段：系统管理与节点管理 (T-01-09 ~ T-01-18)

### T-01-09 用户登录接口

依赖: T-01-05, T-01-06, T-01-08
完成标准: 提供正确的用户名密码能成功登录并获取 Token。

执行步骤:
1. 在 server/src/main/java/com/ops/server/controller/ 下创建 SystemController.java，添加 login 接口 (POST /api/auth/login)；
2. 根据 username 查询 sys_user 表，比对 BCrypt 加密后的密码；
3. 密码正确后使用 Nimbus JOSE+JWT 生成 JWT Token (包含 userId, username, role)；
4. 将 Token 缓存到内存 Map 中，设置24小时过期时间；
5. 返回 { code, message, data: { token, username, role } }。

### T-01-10 节点注册接口 (Server 侧)

依赖: T-01-05, T-01-06
完成标准: Agent 首次启动调用注册接口后，Server 数据库中存储节点信息。

执行步骤:
1. 在 server/src/main/java/com/ops/server/controller/NodeController.java 中添加 nodeAdd 接口 (POST /api/nodes)；
2. 接收参数: name, ip, port, token, osInfo, javaVersion；
3. 校验 token 是否在数据库中已存在 (处理重复注册)；
4. 插入 node_info 表，初始状态为 ONLINE (status=1)；
5. 记录操作审计日志 (operation_log 表)。

### T-01-11 节点列表与详情接口

依赖: T-01-05, T-01-09
完成标准: 前端能正常查询节点列表和单个节点详情。

执行步骤:
1. 在 NodeController.java 中实现 getNodes 接口 (GET /api/nodes)，支持分页和状态筛选；
2. 使用 MyBatis 动态 SQL 根据筛选条件拼接查询；
3. 实现 getNode 接口 (GET /api/nodes/{id}) 返回单个节点详情；
4. 实现 updateNode 接口 (PUT /api/nodes/{id}) 和 deleteNode 接口 (DELETE /api/nodes/{id})；
5. 删除节点时检查是否有项目绑定该节点，有则拒绝删除。

### T-01-12 心跳保活机制 (Server 侧)

依赖: T-01-05, T-01-06
完成标准: Agent 心跳被 Server 接收并更新最后心跳时间，超时节点标记为离线。

执行步骤:
1. 在 NodeController.java 中添加 heartbeat 接口 (GET /api/nodes/heartbeat)；
2. 从请求头 X-Token 获取节点身份，更新 node_info 表的 last_heartbeat 字段和 ip/port/osInfo/javaVersion；
3. 创建 HeartbeatChecker.java 定时任务 (使用 Spring @Scheduled)，每秒执行一次；
4. 扫描 node_info 表，查询 last_heartbeat 距离当前时间超过 90 秒的节点，将 status 更新为 OFFLINE；
5. 节点离线时，向 AlarmController 触发告警事件。

### T-01-13 节点管理前端页面

依赖: T-01-10, T-01-11
完成标准: 前端能正常展示节点列表、新增节点、编辑节点、删除节点。

执行步骤:
1. 在 api/node.ts 中封装 node 相关接口函数；
2. 创建 views/node/NodeList.vue 页面，使用 Ant Design Table 展示节点列表；
3. 列: 节点名称、IP、端口、状态 (在线绿色/离线红色)、JDK版本、最后心跳、操作；
4. 创建 views/node/NodeCreate.vue 页面，使用 Ant Design Form 表单；
5. 字段: 节点名称 (必填)、IP地址 (必填)、端口 (默认2123)、Token (必填)；
6. 状态列使用 StatusBadge 组件，每30秒自动刷新列表。

### T-01-14 用户列表与管理前端页面

依赖: T-01-09
完成标准: 前端能正常展示用户列表、新增用户、编辑用户、删除用户。

执行步骤:
1. 在 api/system.ts 中封装用户管理接口函数；
2. 创建 views/system/UserList.vue 页面，表格展示用户名、角色、状态；
3. 创建 views/system/UserCreate.vue 页面，表单包含用户名、密码、角色选择 (admin/operator)；
4. 操作列包含编辑和删除按钮；
5. 前端侧用户列表页需要通过系统管理权限校验。

### T-01-15 操作审计日志接口

依赖: T-01-05
完成标准: 所有关键操作自动写入 operation_log 表。

执行步骤:
1. 在 SystemController.java 中实现 getOperations 接口 (GET /api/operations?page=1&pageSize=20&module=xxx)；
2. 在 common 模块创建 OperationLog 工具类，提供 @OperationLog 注解；
3. 在 AOP 切面中解析注解，记录操作人、操作时间、模块名、操作内容；
4. 在节点注册、删除、用户增删改、版本上传、部署操作等处添加 @OperationLog 注解；
5. 前端页面创建 views/system/LogList.vue 展示审计日志。

### T-01-16 登录前端页面

依赖: T-01-09
完成标准: 用户能正常登录并跳转至仪表盘。

执行步骤:
1. 创建 views/login/LoginPage.vue 页面；
2. 包含用户名输入框、密码输入框 (密码类型)、登录按钮；
3. 登录成功后调用 POST /api/auth/login 接口；
4. 将返回的 Token 存储到内存 (JavaScript 变量，刷新失效)；
5. 存储用户名和角色到 Pinia store，重定向到 /dashboard。

### T-01-17 前端布局框架

依赖: T-01-16
完成标准: 登录后看到完整框架，包含侧边菜单、顶栏、内容区。

执行步骤:
1. 创建 views/layout/MainLayout.vue 作为所有受保护页面的父组件；
2. 使用 Ant Design Layout 组件，侧边固定菜单 (AppAside)、顶栏 (AppHeader)、内容区 (router-view)；
3. 侧边菜单根据用户角色动态渲染 (管理员显示系统管理，操作员不显示)；
4. 顶栏显示用户名和退出按钮，退出时清除 Token 并跳转登录页；
5. 创建 router/index.ts，配置路由守卫 (未登录跳登录页)。

### T-01-18 Agent心跳保活实现 (Agent 侧)

依赖: T-01-12
完成标准: Agent 启动后自动向 Server 发送心跳，Server 能正确记录节点状态。

执行步骤:
1. 在 agent/src/main/java/com/ops/agent/daemon/ 下创建 HeartbeatScheduler.java；
2. 读取 application.yml 中的 server.url 和 token；
3. 启动后每30秒向 Server 的 /api/nodes/heartbeat 接口发送 GET 请求；
4. 请求头携带 X-Token 进行身份校验；
5. 上报当前节点信息 (OS版本、JDK版本、CPU、内存使用率、磁盘使用率)；
6. 使用 OSHI 库采集系统信息；
7. 网络异常时自动重试，最多重试3次。

---

## 第三阶段：版本管理与项目管理 (T-01-19 ~ T-01-28)

### T-01-19 版本包上传接口 (Server 侧)

依赖: T-01-05, T-01-06
完成标准: 前端上传Jar包后，Server 端保存到指定目录并生成版本记录。

执行步骤:
1. 在 server/src/main/java/com/ops/server/controller/VersionController.java 中实现 upload 接口 (POST /api/versions/upload)；
2. 使用 Spring Multipart 接收文件；
3. 校验文件类型 (仅允许 .jar 后缀)；
4. 根据 projectId 查找项目名，生成版本号 (项目名-v{自增序号})；
5. 计算文件 SHA-256 校验值；
6. 将文件保存到 {server.path}/versions/{projectId}/{version}/xxx.jar 目录；
7. 插入 version_package 表，记录 jarName、filePath、fileSize、version、sha256、remark；
8. 返回 { code, message, data: { version, filePath } }。

### T-01-20 版本包管理前端页面

依赖: T-01-19
完成标准: 前端能展示版本列表、上传Jar包、查看详情、下载、删除。

执行步骤:
1. 在 api/version.ts 中封装版本相关接口函数；
2. 创建 views/version/VersionList.vue 页面，表格展示版本信息；
3. 列: 项目名、Jar包名、版本号、文件大小、备注、上传时间；
4. 创建 views/version/VersionUpload.vue 页面，使用 Ant Design Upload 组件；
5. 支持选择项目、选择本地Jar文件 (仅支持.jar)、填写备注；
6. 上传后自动刷新列表显示新版本。

### T-01-21 项目管理接口 (Server 侧)

依赖: T-01-05
完成标准: 能创建、查询、修改、删除项目，支持绑定多个节点。

执行步骤:
1. 在 server/src/main/java/com/ops/server/controller/ProjectController.java 中实现 CRUD 接口；
2. create 接口接收: name, nodeIds (逗号分隔的节点ID列表), startScript, stopScript, jvmOpts, envVars；
3. startScript 和 stopScript 为 Shell 脚本字符串 (支持模板变量)；
4. jvmOpts 存储 JVM 启动参数 (如 -Xms512m -Xmx1g)；
5. envVars 存储 JSON 格式的环境变量；
6. 实现 getProjects 接口支持按节点筛选、按状态筛选；
7. 实现 getById 接口获取单个项目详情。

### T-01-22 项目管理前端页面

依赖: T-01-21
完成标准: 前端能创建、编辑、查看项目，支持选择绑定节点。

执行步骤:
1. 在 api/project.ts 中封装项目相关接口函数；
2. 创建 views/project/ProjectList.vue 页面，使用卡片式展示项目；
3. 每个卡片显示: 项目名称、绑定节点数 (标签形式)、运行状态、最后部署时间；
4. 创建 views/project/ProjectCreate.vue 页面，表单包含:
   - 项目名称 (必填)
   - 节点选择 (多选下拉框，从节点列表获取选项)
   - 启动脚本 (代码编辑器，支持模板变量提示)
   - 停止脚本 (代码编辑器)
   - JVM参数 (文本框)
   - 环境变量 (Key-Value 编辑)
5. 创建 views/project/ProjectDetail.vue 页面展示项目详情和部署记录。

### T-01-23 版本发布接口 (Server 侧)

依赖: T-01-05, T-01-19
完成标准: 选择版本发布后，Jar包被下发到所有绑定节点。

执行步骤:
1. 在 server/src/main/java/com/ops/server/controller/DeployController.java 中实现 publish 接口 (POST /api/deploy)；
2. 接收参数: projectId, versionId；
3. 校验项目存在性、版本属于该项目、项目绑定了至少一个节点；
4. 在 deploy_record 表中创建一条记录 (status=PROCESSING)；
5. 遍历项目绑定的所有节点，对每个在线节点:
   a. 调用 Agent 的 /api/files/receive 接口上传 Jar 包；
   b. 调用 Agent 的停止脚本接口；
   c. 等待进程完全退出 (轮询检测进程状态)；
   d. 调用 Agent 的启动脚本接口启动新版；
   e. 等待启动完成，检测进程状态；
   f. 更新部署记录的状态为 SUCCESS 或 FAILED；
6. 任一节点失败时记录失败日志，支持后续重试单个节点。

### T-01-24 Agent文件接收接口

依赖: T-01-23
完成标准: Agent 能接收 Server 下发的 Jar 包并保存到项目目录。

执行步骤:
1. 在 agent/src/main/java/com/ops/agent/controller/FileController.java 中添加 receive 接口 (POST /api/files/receive)；
2. 校验请求头 X-Token 合法性；
3. 使用 Spring Multipart 接收文件；
4. 根据项目配置中的 startScript 中的项目名，确定存储目录；
5. 将文件保存到 {agent.path}/projects/{projectName}/releases/{version}/xxx.jar 目录；
6. 记录文件大小和 SHA-256 校验值 (用于后续校验)；
7. 返回 { code, message, filePath }。

### T-01-25 部署记录与回滚接口

依赖: T-01-23
完成标准: 前端能查看部署记录，支持一键回滚。

执行步骤:
1. 在 DeployController.java 中实现 getDeployRecords 接口 (GET /api/deploy?projectId=xxx)；
2. 返回列表包含: 版本名、节点、状态、部署时间、日志；
3. 实现 rollback 接口 (POST /api/deploy/{id}/rollback)；
4. 回滚逻辑: 根据部署记录找到回滚目标版本 → 按 T-01-23 的流程执行；
5. 创建 views/deploy/DeployList.vue 和 views/deploy/DeployDetail.vue 前端页面。

### T-01-26 项目启停接口 (Server 侧)

依赖: T-01-05
完成标准: 前端能指定项目+节点执行启动、停止、重启操作。

执行步骤:
1. 在 server/src/main/java/com/ops/server/controller/ProcessController.java 中实现启停接口；
2. start 接口 (POST /api/process/{projectId}/{nodeId}/start):
   - 查询项目的 startScript；
   - 通过 HTTP 调用 Agent 的脚本执行接口；
   - 等待启动结果并返回前端。
3. stop 接口 (POST /api/process/{projectId}/{nodeId}/stop) 和 restart 接口类似实现；
4. 脚本执行使用后台线程异步执行，WebSocket 推送进度到前端。

### T-01-27 Agent进程管理接口

依赖: T-01-26
完成标准: Agent 能接收启动/停止/重启指令并执行。

执行步骤:
1. 在 agent/src/main/java/com/ops/agent/controller/ProcessController.java 中添加进程相关接口；
2. start 接口: 读取项目配置中的 startScript，通过 ProcessBuilder 启动进程；
3. stop 接口: 读取项目配置中的 stopScript 或直接通过 kill 命令停止进程；
4. 查询进程状态接口: 通过进程名或端口查找 PID，返回运行状态、PID、端口、CPU、内存；
5. 使用 OSHI 库采集 CPU 和内存使用率。

### T-01-28 Agent脚本执行接口

依赖: T-01-27
完成标准: Agent 能根据 Server 下发的脚本指令执行操作。

执行步骤:
1. 在 agent/src/main/java/com/ops/agent/controller/ 下创建 ScriptController.java；
2. 实现脚本执行接口 (POST /api/scripts/run)；
3. 接收参数: scriptType (start/stop), script (脚本内容)，projectName (项目名)；
4. 将脚本中的模板变量 (如 ${projectName}, ${jarPath}, ${JAVA_HOME}) 替换为实际值；
5. 通过 ProcessBuilder 执行脚本，记录输出日志；
6. 返回执行结果 (成功/失败 + 输出信息)。

---

## 第四阶段：文件管理增强 (T-01-29 ~ T-01-38)

### T-01-29 日志查看接口 (Server 侧)

依赖: T-01-05
完成标准: 能分页读取指定节点指定项目的运行日志文件内容。

执行步骤:
1. 在 server/src/main/java/com/ops/server/controller/LogController.java 中实现 getLogFile 接口；
2. 参数: nodeId, projectId, page, pageSize；
3. 通过 Agent 的 /api/log/tail 接口读取日志文件内容；
4. 按分页参数处理日志行，返回 { code, message, data: { lines, total } }；
5. 支持关键字搜索功能 (过滤出包含关键字的行)；
6. 前端创建 views/process/ProcessLog.vue 页面展示日志。

### T-01-30 实时控制台 (WebSocket)

依赖: T-01-07
完成标准: Server 能通过 WebSocket 向浏览器实时推送进程控制台输出。

执行步骤:
1. 在 server 端创建 WebSocketHandler 处理 /ws/console/{projectId}/{nodeId} 连接；
2. Agent 侧建立到 Server 的 WebSocket 连接，推送实时控制台输出；
3. Server 收到 Agent 的控制台输出后，通过 WebSocket 转发给前端；
4. 实现消息路由: Server 将所有连接到同一个 projectId/nodeId 的 WebSocket 会话加入一个订阅组；
5. 当 Agent 推送控制台输出时，遍历订阅组转发给所有前端会话；
6. 前端 views/process/ProcessConsole.vue 使用 xterm.js 终端组件展示输出。

### T-01-31 日志文件查看接口 (M10 - Server 侧)

依赖: T-01-05
完成标准: 能查看服务器上任意路径下的日志文件内容，支持分页和关键字搜索。

执行步骤:
1. 在 server/src/main/java/com/ops/server/controller/FileController.java 中实现 viewLog 接口 (GET /api/files/log?nodeId=xxx&path=xxx&page=1&pageSize=100)；
2. 校验 nodeId 对应的节点存在且在线；
3. 通过 Agent 的 /api/files/read?path=xxx 接口读取指定路径的日志文件内容；
4. Agent 读取文件时，按行读取，返回指定分页的数据；
5. 支持关键字搜索: 后端过滤包含关键字的行，前端高亮显示；
6. 支持自动刷新: 前端定时轮询或 WebSocket 推送新日志行。

### T-01-32 YML配置读取接口 (M10 - Server 侧)

依赖: T-01-05
完成标准: 能读取服务器上任意YML配置文件内容，前端使用代码编辑器展示。

执行步骤:
1. 在 FileController.java 中实现 viewConfig 接口 (GET /api/files/config?nodeId=xxx&path=xxx)；
2. 校验 nodeId 和 path 合法性；
3. 路径安全检查: 白名单校验 (使用 Agent 配置中的 allow.path.prefix 限制访问路径)；
4. 通过 Agent 的 /api/files/read?path=xxx 接口读取YML文件内容；
5. 返回文件内容字符串，前端使用 CodeEditor 组件以 YAML 语法高亮展示。

### T-01-33 YML配置保存接口 (M10 - Server 侧)

依赖: T-01-32
完成标准: 前端编辑后能保存YML配置到服务器指定路径。

执行步骤:
1. 在 FileController.java 中实现 saveConfig 接口 (POST /api/files/config?nodeId=xxx&path=xxx)；
2. 接收参数: node_id, path, content (YML内容)；
3. 路径安全检查: 白名单校验，仅允许修改 .yml 和 .yaml 后缀的文件；
4. 通过 Agent 的 /api/files/write?path=xxx 接口将新内容写入文件；
5. 写入前做差异对比，记录到 operation_log 表和 file_access_log 表；
6. 返回 { code, message }，前端显示保存成功或失败的提示信息。

### T-01-34 批量下载接口 (M10 - Server 侧)

依赖: T-01-31, T-01-32
完成标准: 前端勾选多个文件后，能将多个节点上的指定文件打包为 Zip 返回下载。

执行步骤:
1. 在 FileController.java 中实现 batchDownload 接口 (POST /api/files/batch-download)；
2. 接收参数: { items: [{ nodeId, path, type: yml|log|jar }] }；
3. 遍历 items 列表，对每个文件调用 Agent 的对应接口获取文件内容；
4. 使用 JSch 或 java.util.zip.ZipOutputStream 将文件打包为 Zip 流；
5. 设置 Content-Type: application/zip，Content-Disposition: attachment; filename=xxx.zip；
6. 使用流式输出，大文件分块输出，不占用过多内存。

### T-01-35 Agent文件读取接口

依赖: T-01-31, T-01-32, T-01-33, T-01-34
完成标准: Agent 能根据 Server 请求读取指定路径的文件内容 (用于日志查看和YML读取)。

执行步骤:
1. 在 agent/src/main/java/com/ops/agent/controller/FileController.java 中实现 read 接口 (GET /api/files/read?path=xxx)；
2. 校验 X-Token 合法性；
3. 路径安全检查: 校验 path 是否在允许访问的路径前缀列表内；
4. 按行读取文件内容，支持分页 (从第 N 行开始读取 M 行)；
5. 返回文件内容字符串和文件名；
6. 对于二进制文件 (Jar包)，返回文件字节流。

### T-01-36 Agent文件写入接口 (M10)

依赖: T-01-31, T-01-32, T-01-33
完成标准: Agent 能将指定内容写入服务器文件系统的指定路径。

执行步骤:
1. 在 agent/FileController.java 中实现 write 接口 (POST /api/files/write?path=xxx)；
2. 接收参数: path, content；
3. 路径安全检查: 白名单校验 (使用 Agent 配置中的 allow.path.prefix)；
4. 文件后缀校验: 仅允许 .yml 和 .yaml 后缀；
5. 使用 Java NIO Files.write 写入文件，先写入临时文件，原子替换；
6. 写入失败时抛出异常，返回错误信息。

### T-01-37 Agent文件下载接口 (M10)

依赖: T-01-31, T-01-32, T-01-33, T-01-34
完成标准: Agent 能返回服务器上指定路径的文件内容，用于前端下载。

执行步骤:
1. 在 agent/FileController.java 中实现 download 接口 (GET /api/files/download?path=xxx)；
2. 校验 X-Token 和路径白名单；
3. 读取文件字节内容，设置正确的 Content-Type 和 Content-Disposition；
4. 使用流式输出，支持大文件断点续传 (支持 Range 请求头)；
5. 计算文件的 ETag 值 (基于文件修改时间+大小)，用于浏览器缓存。

### T-01-38 前端文件管理页面 (M10)

依赖: T-01-31, T-01-32, T-01-33, T-01-34
完成标准: 前端能查看日志文件、编辑YML配置、批量下载文件。

执行步骤:
1. 在 api/file.ts 中封装文件管理相关接口函数；
2. 创建 views/file/FileLogView.vue 页面:
   - 选择节点、输入文件路径 (支持通配符)；
   - 使用 LogViewer 组件展示日志内容；
   - 支持关键字搜索、自动刷新；
3. 创建 views/file/FileConfigEdit.vue 页面:
   - 选择节点、输入YML文件路径；
   - 使用 CodeEditor 组件 (mode=yaml) 编辑YML；
   - 保存前显示变更预览 (Diff 对比)；
   - 保存后刷新显示新内容；
4. 创建 views/file/FileDownload.vue 页面:
   - 选择节点，勾选需要下载的文件 (日志、YML、Jar)；
   - 点击"批量下载"按钮，调用后端接口触发浏览器下载。

---

## 第五阶段：监控告警与调度 (T-01-39 ~ T-01-48)

### T-01-39 进程监控接口 (Server + Agent)

依赖: T-01-27
完成标准: 能获取指定节点指定项目的进程运行状态。

执行步骤:
1. 在 server/src/main/java/com/ops/server/controller/MonitorController.java 中实现 processMonitor 接口；
2. 通过 Agent 的进程状态接口获取当前运行状态；
3. 返回: { status, pid, port, cpu, memory, disk }；
4. 前端 views/process/ProcessMonitor.vue 使用 ECharts 展示趋势图；
5. 通过 WebSocket 实现实时推送 (每10秒更新一次)。

### T-01-40 Agent系统信息采集上报

依赖: T-01-39
完成标准: Agent 能采集 CPU、内存、磁盘使用率并上报给 Server。

执行步骤:
1. 在 agent/src/main/java/com/ops/agent/daemon/ 下创建 SystemInfoCollector.java；
2. 使用 OSHI 库采集系统信息: CPU使用率、内存使用率、磁盘使用率、网络流量；
3. 每10秒向 Server 的监控接口上报一次；
4. Server 将数据保存到内存缓存 (使用 ConcurrentHashMap)，供前端查询。

### T-01-41 邮件告警配置接口 (Server 侧)

依赖: T-01-05
完成标准: 能配置 SMTP 服务器信息和告警接收人列表。

执行步骤:
1. 在 server/src/main/java/com/ops/server/controller/AlarmController.java 中实现配置接口；
2. 从 application.yml 或数据库读取 SMTP 配置 (host, port, username, password, sslEnabled)；
3. 读取告警接收人邮箱列表 (逗号分隔)；
4. 前端 views/alarm/AlarmConfig.vue 提供配置表单。

### T-01-42 邮件告警推送服务

依赖: T-01-41
完成标准: 节点离线或进程宕机时，能自动发送告警邮件。

执行步骤:
1. 在 AlarmController.java 中实现 sendAlarm 方法；
2. 使用 Java Mail Sender 发送邮件；
3. 邮件模板: 主题="[告警]{节点/项目}异常"，正文包含时间、节点、项目、异常详情；
4. 发送结果记录到 alarm_record 表；
5. 支持多接收人同时发送。

### T-01-43 告警历史查询接口

依赖: T-01-05, T-01-42
完成标准: 前端能查询告警历史记录。

执行步骤:
1. 在 AlarmController.java 中实现 getAlarms 接口 (GET /api/alarms?projectId=xxx&page=1)；
2. 从 alarm_record 表查询告警记录，支持按项目、类型、时间筛选；
3. 前端 views/alarm/AlarmList.vue 展示告警历史列表。

### T-01-44 定时重启任务调度

依赖: T-01-05, T-01-26
完成标准: 支持配置 Cron 表达式定时重启项目。

执行步骤:
1. 在 project_info 表添加 restart_cron 字段 (Cron 表达式，可选)；
2. 在 server 的 scheduler/ 包下创建 CronRestartScheduler.java；
3. 启动时扫描所有 cron 表达式不为空的项目；
4. 使用 Quartz 或 Spring Task Scheduler 调度定时任务；
5. 到调度时间执行项目的重启操作 (调用 ProcessController 的 restart 接口)。

### T-01-45 进程异常自动重启

依赖: T-01-27
完成标准: 进程意外退出后，Server 检测到并自动执行重启。

执行步骤:
1. 在 HeartbeatChecker.java 的心跳检测任务中，额外检查进程状态；
2. 对于 status=RUNNING 但进程实际已停止的项目，触发自动重启；
3. 调用 ProcessController 的 restart 接口；
4. 重启成功则更新状态，重启失败则记录失败日志并发送告警；
5. 自动重启失败后不进行多次重试，避免进程震荡。

### T-01-46 部署WebSocket进度推送

依赖: T-01-23
完成标准: 前端通过 WebSocket 接收部署进度信息。

执行步骤:
1. 在 Server 端创建 WebSocketSessionManager 管理部署 WebSocket 连接；
2. 发布接口返回部署 ID，前端通过 /ws/deploy/{deployId} 建立连接；
3. Server 在部署执行的每个阶段 (下发Jar、停止、启动、检测) 推送进度消息；
4. 前端 views/deploy/DeployDetail.vue 实时展示部署进度。

### T-01-47 前端告警中心页面

依赖: T-01-41, T-01-42, T-01-43
完成标准: 前端能查看告警配置和历史记录。

执行步骤:
1. 在 api/alarm.ts 中封装告警接口函数；
2. 创建 views/alarm/AlarmList.vue 页面，表格展示告警历史；
3. 列: 时间、项目/节点、告警类型、发送结果；
4. 创建 views/alarm/AlarmConfig.vue 页面，表单包含:
   - SMTP 服务器地址、端口、是否SSL
   - 发件邮箱、密码
   - 告警接收人 (多邮箱，逗号分隔)
   - 启用/禁用开关

### T-01-48 仪表盘 (Dashboard)

依赖: 以上各模块
完成标准: 首页展示关键运维数据概览。

执行步骤:
1. 创建 views/dashboard/Dashboard.vue 页面；
2. 展示: 节点总数/在线数、项目总数/运行数、今日部署次数、未处理告警数；
3. 使用 ECharts 展示最近7天部署趋势图和节点在线趋势图；
4. 快速操作入口 (新建项目、上传版本、查看告警)。

---

## 第六阶段：前端基础设施 (T-01-49 ~ T-01-55)

### T-01-49 前端路由与权限系统

依赖: T-01-17
完成标准: 路由守卫正确拦截未登录和无权限访问。

执行步骤:
1. 在 router/index.ts 中配置所有路由 (含懒加载)；
2. 为每个路由添加 meta.roles 字段 (admin/operator)；
3. 路由守卫: 未登录重定向到 /login，有 Token 但无权限跳转 403；
4. 根据用户角色动态生成侧边菜单 (管理员显示所有菜单，操作员隐藏系统管理)。

### T-01-50 Axios 封装 (request.ts)

依赖: T-01-09
完成标准: 所有 HTTP 请求经过统一拦截器处理。

执行步骤:
1. 在 utils/request.ts 中封装 Axios 实例；
2. 请求拦截器: 从内存读取 Token 并注入 Authorization 头；
3. 响应拦截器: 统一处理错误码 (401/403/500 等)，显示错误提示；
4. 文件传输接口设置特殊超时时间 (5分钟)；
5. 提供 get/post/upload/download 四个快捷方法。

### T-01-51 前端公共组件库

依赖: 无 (可并行开发)
完成标准: 所有公共组件编译通过，可被页面正常引用。

执行步骤:
1. AppLayout - 整体布局组件 (侧边菜单 + 顶栏 + 内容区)；
2. AppTable - 封装表格 (支持分页/排序/筛选/自定义列)；
3. AppModal - 封装弹窗 (支持确定/取消/加载状态)；
4. CodeEditor - 封装 CodeMirror (支持 yaml/java/log 模式)；
5. LogViewer - 日志查看器 (带行号、关键字高亮、自动滚动)；
6. Terminal - 封装 xterm.js 终端组件；
7. StatusBadge - 状态标签组件；
8. StatsPanel - 统计面板组件。

### T-01-52 Pinia 状态管理

依赖: T-01-09
完成标准: 用户状态、节点状态等全局数据通过 Pinia 管理。

执行步骤:
1. 创建 stores/auth.ts: 管理当前登录用户信息 (userId, username, role, token)；
2. 创建 stores/node.ts: 管理在线节点列表缓存；
3. 创建 stores/project.ts: 管理项目列表缓存；
4. 创建 stores/file.ts: 管理文件管理的临时状态 (当前选中节点、路径等)；
5. Token 仅存于内存，刷新页面后需要重新登录。

### T-01-53 前端构建与Nginx配置

依赖: T-01-49, T-01-50, T-01-51
完成标准: 前端构建为静态文件，可通过 Nginx 反向代理访问。

执行步骤:
1. 配置 vite.config.ts，设置 build.outDir 为 dist，配置 proxy 到 /api；
2. 创建 frontend/nginx.conf，配置反向代理: location /api 代理到 Server 8081 端口；
3. 配置静态资源缓存策略 (CSS/JS 带 hash，图片不缓存)；
4. 执行 npm run build 验证构建成功，输出 dist 目录包含完整静态资源。

### T-01-54 前后端联调 (节点管理 + 版本管理)

依赖: T-01-13, T-01-20
完成标准: 能完整走完节点创建、版本上传、项目创建流程。

执行步骤:
1. 依次启动 Server 和 Agent；
2. 手动创建节点 → 手动上传版本包 → 手动创建项目 (绑定节点)；
3. 验证前后端接口联调结果；
4. 修复发现的问题。

### T-01-55 前后端联调 (部署发布 + 文件管理)

依赖: T-01-25, T-01-38
完成标准: 能完整走完版本发布 → 查看日志 → 查看YML → 编辑配置 → 批量下载流程。

执行步骤:
1. 上传版本包 → 发布到项目 → 查看部署记录 → 回滚；
2. 查看日志文件 → 编辑YML配置 → 保存配置 → 批量下载文件；
3. 验证前后端接口联调结果；
4. 修复发现的问题。

---

## 任务依赖关系汇总

T-01-01 (初始化项目) 是所有任务的起点。
T-01-02 ~ T-01-04 (Server/Agent/Model) 并行依赖于 T-01-01。
T-01-05 (数据库) 和 T-01-06 (认证) 依赖于 T-01-02 和 T-01-04。
T-01-07 (WebSocket) 和 T-01-08 (异常处理) 并行。
第二阶段 (T-01-09 ~ T-01-18) 依赖于第一阶段。
第三阶段 (T-01-19 ~ T-01-28) 依赖于第二阶段。
第四阶段 (T-01-29 ~ T-01-38) 依赖于第三阶段。
第五阶段 (T-01-39 ~ T-01-48) 依赖于第四阶段和第三阶段部分任务。
第六阶段 (T-01-49 ~ T-01-55) 依赖以上所有相关任务。

---

*本文档为第三层最小任务分层 (Server与Agent篇)，审核通过后开始逐个执行编码任务。*
