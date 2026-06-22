# AGENTS.md - Auto Test 项目 AI Agent 提示词

## 项目简介

本项目是一个 API 自动化测试平台，包含前后端分离架构，支持测试链路管理、节点配置、流程执行引擎、AI 智能服务、浏览器插件对接、WebSocket 实时推送等能力。

---

## 技术栈

### 后端 (backend/)
- **语言**: Java 8 (1.8)
- **框架**: Spring Boot 2.7.7
- **ORM**: MyBatis (mybatis-spring-boot-starter 2.3.1)
- **数据库**: MySQL 8.0 + Druid 连接池
- **AI**: LangChain4j 0.35.0 (OpenAI 兼容接口)
- **构建工具**: Maven
- **其他**: FastJSON, JsonPath, Apache HttpClient, Lombok, Guava

### 前端 (frontend/)
- **框架**: Vue 3.4 + Vite 5
- **UI**: Element Plus 2.5
- **路由**: Vue Router 4.2
- **流程编排**: LogicFlow (@logicflow/core + @logicflow/extension)
- **代码编辑**: Monaco Editor
- **HTTP**: Axios

### 数据库
- MySQL 8.0，通过 Docker Compose 部署
- 字符集: utf8mb4
- 表名大小写不敏感 (lower-case-table-names=1)

### 浏览器插件 (plugin-test/)
- Chrome Extension Manifest V3

---

## 环境要求

| 依赖       | 版本要求        | 说明                        |
|------------|----------------|----------------------------|
| JDK        | 1.8            | 后端编译运行                  |
| Maven      | 3.6+           | 后端构建                     |
| Node.js    | 18+            | 前端构建运行                 |
| MySQL      | 8.0            | 数据库（推荐 Docker 部署）     |
| Docker     | 20+            | 数据库容器化部署               |

---

## 启动指南

### 1. 启动数据库（Docker）

```bash
cd docker
docker-compose up -d
```

数据库连接信息：
- Host: `localhost:3306`
- Database: `auto_test`
- Username: `root`
- Password: `autotest123`

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

- 端口: `8080`
- 上下文路径: `/`

或者使用批处理脚本：
```bash
start-backend.bat
```

### 3. 启动前端

```bash
cd frontend
npm install   # 首次需要安装依赖
npm run dev
```

- 端口: `3001`
- API 代理: `/api` -> `http://localhost:8080`
- WebSocket 代理: `/ws` -> `ws://localhost:8080`

或者使用批处理脚本：
```bash
start-frontend.bat
```

### 4. 一键启动全部服务

```bash
start.bat
```

此脚本会同时启动后端和前端，并在新窗口中运行。

---

## 关闭指南

### 一键关闭全部服务

```bash
stop.bat
```

此脚本会终止占用 8080（后端）和 3001（前端）端口的进程。

### 手动关闭

- 关闭后端：终止占用 8080 端口的 Java 进程
- 关闭前端：在终端按 `Ctrl+C` 或终止占用 3001 端口的进程
- 关闭数据库：`cd docker && docker-compose down`

---

## 重启指南

```bash
restart.bat
```

等价于先执行 `stop.bat`，等待端口释放，再执行 `start.bat`。

---

## 关键配置

### 后端配置 (backend/src/main/resources/application.yml)

```yaml
# 数据库
spring.datasource.url: jdbc:mysql://localhost:3306/auto_test
spring.datasource.username: root
spring.datasource.password: autotest123

# AI 服务（OpenAI 兼容）
ai.base-url: http://localhost:11434/v1
ai.api-key: sk-placeholder
ai.model: qwen-max
ai.timeout-seconds: 120

# AES 加密密钥（测试账号密码加密，必须16字节）
account.aes-key: autotest_key_16!
```

### 前端配置 (frontend/vite.config.js)

```javascript
server.port: 3001
server.proxy./api -> http://localhost:8080
server.proxy./ws  -> ws://localhost:8080
```

---

## 项目结构

```
auto-test/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/autotest/
│   │   ├── config/            # 配置类
│   │   ├── controller/        # REST 控制器
│   │   ├── engine/            # 流程执行引擎
│   │   ├── exception/         # 异常处理
│   │   ├── filter/            # 过滤器
│   │   ├── mapper/            # MyBatis Mapper 接口
│   │   ├── model/             # 数据模型（entity/dto/vo）
│   │   ├── service/           # 业务服务层
│   │   ├── util/              # 工具类
│   │   └── websocket/         # WebSocket 推送
│   ├── src/main/resources/
│   │   ├── mapper/            # MyBatis XML 映射文件
│   │   └── application.yml    # 应用配置
│   └── pom.xml
├── frontend/                   # Vue 3 前端
│   ├── src/
│   │   ├── api/               # API 请求封装
│   │   ├── components/        # 公共组件
│   │   ├── router/            # 路由配置
│   │   ├── views/             # 页面视图
│   │   ├── App.vue
│   │   └── main.js
│   ├── vite.config.js
│   └── package.json
├── plugin-test/                # Chrome 浏览器插件
├── docker/                     # Docker 数据库部署
│   ├── docker-compose.yml
│   └── init.sql               # 数据库初始化脚本
├── doc/                        # 设计文档
├── agent-rules/                # AI Agent 编码规范
├── start.bat                   # 一键启动
├── stop.bat                    # 一键关闭
├── restart.bat                 # 一键重启
├── start-backend.bat           # 单独启动后端
└── start-frontend.bat          # 单独启动前端
```

---

## 编码规范

1. **行数限制**: 类和文档不超过 400 行，上限 500 行，超过则拆分
2. **模块隔离**: 代码修改模块/文件相互隔离，禁止范围重叠
3. **任务拆分**: 按「整体需求 → 主任务 → 原子任务」拆分，单会话执行单个原子任务
4. **命名规范**: Java 使用驼峰命名，MyBatis 开启下划线转驼峰 (`map-underscore-to-camel-case: true`)
5. **异常处理**: 所有 Controller 层接口必须有异常兜底处理
6. **编码格式**: 所有文件使用 UTF-8 编码
