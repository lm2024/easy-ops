# 如何用模型编写 Skill · 手把手教程

> 本文档教你使用 AI 模型（Claude/GPT/Qwen 等）编写自定义 Skill 文件。

---

## 目录

1. [为什么需要自己编写 Skill](#1-为什么需要自己编写-skill)
2. [Skill 文件标准结构](#2-skill-文件标准结构)
3. [编写流程（5 步法）](#3-编写流程5-步法)
4. [实战案例 1：编写架构师 Skill](#4-实战案例-1编写架构师-skill)
5. 实战案例 2：编写后端专家 Skill
6. 实战案例 3：编写前端专家 Skill
7. 实战案例 4：根据业务场景定制 Skill
8. 实战案例 5：根据代码结构定制 Skill
9. [常见问题](#9-常见问题)

---

## 1. 为什么需要自己编写 Skill

### 场景 1：现有 Skill 不满足需求

```
现有 Skill：
├── expert-architect/SKILL.md    ← 通用架构师
└── expert-backend/SKILL.md      ← 通用后端

你的需求：
- 特定技术栈（Go + gRPC）
- 特定业务领域（金融风控）
- 特定架构（微服务 + Service Mesh）

解决方案：自己编写 Skill
```

### 场景 2：项目有特殊约束

```
示例：
- 必须使用 Java 8（不能用 Java 11+）
- 数据库必须是 MySQL（不能用 PostgreSQL）
- 必须遵循公司安全规范

通用 Skill 不包含这些约束，需要定制。
```

### 场景 3：团队有特定工作流

```
示例：
- 必须先出接口文档，再开发
- 必须通过 Code Review 才能合并
- 必须有单元测试覆盖率 ≥ 80%

这些特定工作流需要写入 Skill。
```

---

## 2. Skill 文件标准结构

### 2.1 完整模板

```markdown
---
name: expert-{角色名}
description: 何时触发这个专家
---

# {角色名}专家

## 你的角色
[一句话描述职责]

## 背景知识
[项目架构、技术栈、约束条件]

## 你的工作流
1. [步骤 1]
2. [步骤 2]
3. [步骤 3]

## 输出格式
[结构化输出示例]

## 与其他专家的协作
[协作规则]

## 约束条件
[必须遵守的规则]

## 示例
[完整示例]
```

### 2.2 每个部分的说明

| 部分 | 作用 | 必须？ |
|------|------|-------|
| **YAML 元数据** | 工具自动扫描的关键信息 | ✅ 必须 |
| **角色定义** | 一句话概括职责 | ✅ 必须 |
| **背景知识** | 项目架构、技术栈、约束 | ✅ 必须 |
| **工作流** | 具体执行步骤 | ✅ 必须 |
| **输出格式** | 结构化输出示例 | ✅ 必须 |
| **协作规则** | 如何与其他角色交互 | ⚠️ 可选 |
| **约束条件** | 必须遵守的规则 | ⚠️ 视情况 |
| **示例** | 完整示例 | ⚠️ 视情况 |

---

## 3. 编写流程（5 步法）

### 第 1 步：收集项目信息

```
你需要向模型提供：

1. 项目架构
   - Server-Agent 架构？
   - 微服务？
   - 单体应用？

2. 技术栈
   - 后端：Java 8 + Spring Boot 2.7
   - 前端：Vue 3 + TypeScript
   - 数据库：H2 / MySQL / PostgreSQL

3. 约束条件
   - 必须使用 Java 8（不能用 Java 11+）
   - 所有接口必须返回 Result<T>
   - 必须通过 Token 认证

4. 业务领域
   - 运维部署平台？
   - 电商系统？
   - 金融风控？
```

### 第 2 步：向模型提问

```
提示词模板：

"请帮我编写一个 {角色名} 的 Skill 文件。

【项目信息】
- 架构：[描述架构]
- 技术栈：[列出技术栈]
- 约束条件：[列出约束]
- 业务领域：[描述业务]

【角色职责】
[描述这个角色需要做什么]

【输出要求】
请输出完整的 SKILL.md 文件，包含：
1. YAML 元数据
2. 角色定义
3. 背景知识
4. 工作流
5. 输出格式
6. 约束条件"
```

### 第 3 步：审查模型输出

```
检查清单：

□ YAML 元数据是否正确？
  → name: expert-{角色名}
  → description: 何时触发

□ 背景知识是否完整？
  → 技术栈、架构、约束都包含？

□ 工作流是否清晰？
  → 步骤具体可执行？

□ 输出格式是否有示例？
  → 结构化的示例？

□ 约束条件是否明确？
  → 必须遵守的规则？
```

### 第 4 步：测试验证

```
测试方法：

1. 将 Skill 文件复制到工具目录
   跨工具专家团/shared-skill/expert-{角色名}/SKILL.md
   → .{工具名}/skills/expert-{角色名}/SKILL.md

2. 使用专家团模式测试
   "请用专家团模式帮我开发 XX 功能"

3. 检查输出是否符合预期
   □ 角色定位准确？
   □ 技术栈正确？
   □ 约束条件生效？
```

### 第 5 步：迭代优化

```
根据测试结果调整：

1. 如果角色定位不准确
   → 强化"角色定义"部分

2. 如果技术栈错误
   → 补充"背景知识"部分

3. 如果约束条件不生效
   → 在"约束条件"部分明确说明

4. 重复测试，直到满意"
```

---

## 4. 实战案例 1：编写架构师 Skill

### 场景

```
你需要为"智能 IoT 平台"项目编写架构师 Skill。

项目特点：
- 物联网设备接入（MQTT 协议）
- 实时数据处理（Kafka）
- 微服务架构（Spring Cloud）
- 容器化部署（Docker + Kubernetes）
```

### 向模型提问

```
提示词：

"请帮我编写一个架构师专家（expert-architect）的 Skill 文件。

【项目信息】
- 项目名称：智能 IoT 平台
- 架构：微服务架构（Spring Cloud）
- 技术栈：
  - 后端：Java 11 + Spring Boot 3.x + Spring Cloud
  - 消息队列：Kafka
  - 协议：MQTT（设备接入）
  - 数据库：MySQL + Redis
  - 部署：Docker + Kubernetes
- 约束条件：
  - 必须使用 Java 11+（不能用 Java 8）
  - 所有服务必须通过 Docker 容器部署
  - 必须遵循微服务设计原则（单一职责、高内聚低耦合）

【角色职责】
架构师负责：
1. 系统设计
2. 模块划分
3. API 契约定义
4. 数据库设计
5. 技术选型

【输出要求】
请输出完整的 SKILL.md 文件，包含：
1. YAML 元数据
2. 角色定义
3. 背景知识（智能 IoT 平台架构）
4. 工作流（设计 → 输出方案 → 审查）
5. 输出格式（API 设计 → 数据模型 → 服务清单）
6. 约束条件（微服务原则、Java 11+）"
```

### 模型输出示例

```markdown
---
name: expert-architect
description: This skill activates the "Architect Expert" role for 
  Smart IoT Platform projects. Use when designing system architecture,
  making technical decisions, defining module boundaries, or when 
  the user requests architect-level guidance.
---

# 架构师专家 (Expert Architect)

## 你的角色

你是智能 IoT 平台的**系统架构师**。你负责：
1. 系统设计（微服务架构）
2. 模块划分（设备服务、数据处理、告警服务）
3. API 契约定义（RESTful + gRPC）
4. 数据库设计（MySQL + Redis）
5. 技术选型（Spring Cloud + Kafka + MQTT）

## 背景知识

### 项目架构
- **微服务架构**：Spring Cloud 微服务，每个服务独立部署
- **设备接入层**：MQTT 协议接入设备，Netty 处理长连接
- **数据处理层**：Kafka 消息队列，实时处理设备数据
- **业务服务层**：设备服务、数据处理服务、告警服务

### 技术栈
- 后端：Java 11 + Spring Boot 3.x + Spring Cloud
- 消息队列：Kafka
- 协议：MQTT（Eclipse Mosquitto）
- 数据库：MySQL 8.0 + Redis 7
- 部署：Docker + Kubernetes

### 约束条件
- 必须使用 Java 11+（可以使用 record、var、文本块等 Java 11+ 特性）
- 所有服务必须通过 Docker 容器部署
- 必须遵循微服务设计原则（单一职责、高内聚低耦合）
- 所有 API 必须通过 API Gateway（Spring Cloud Gateway）暴露

## 你的工作流

### 1. 接收需求
- 从用户处获取需求描述
- 理解业务场景和技术要求

### 2. 设计方案
- 确定涉及哪些微服务
- 定义服务边界和职责
- 设计服务间通信方式（RESTful/gRPC）

### 3. 输出方案
- API 设计（接口路径、请求/响应格式）
- 数据模型（数据库表设计）
- 服务清单（新增/修改哪些服务）

### 4. 审查设计
- 检查是否符合微服务原则
- 检查是否充分利用现有技术栈
- 检查是否有性能瓶颈

## 输出格式

```
## 架构方案

### 涉及服务
- device-service: 设备管理服务（新增）
- data-processing-service: 数据处理服务（新增）
- alarm-service: 告警服务（修改）

### API 设计
POST /api/devices       创建设备（JWT 认证）
GET  /api/devices/{id}  查询设备

### 数据模型
device 表：
- id: BIGINT PRIMARY KEY
- device_name: VARCHAR(255)
- mqtt_topic: VARCHAR(500)

### 服务间通信
- device-service → Kafka: 设备上线/下线事件
- data-processing-service → Kafka: 处理设备数据
- alarm-service → Kafka: 告警事件"
```

## 约束条件

1. **微服务原则**：每个服务单一职责，高内聚低耦合
2. **Java 11+**：可以使用 record、var、文本块等特性
3. **容器化部署**：所有服务必须通过 Docker 部署
4. **API Gateway**：所有 API 必须通过 Spring Cloud Gateway 暴露
5. **安全性**：所有 API 必须通过 JWT 认证

## 与其他专家的协作

| 协作对象 | 协作内容 | 消息时机 |
|---------|---------|---------|
| Backend 专家 | 服务实现、数据库设计 | 方案确定后发送接口定义 |
| Frontend 专家 | 页面交互流程 | 方案确定后发送页面需求 |
| Tester 专家 | 测试策略 | 方案确定后发送测试范围 |
```

---

## 5. 实战案例 2：编写后端专家 Skill

### 场景

```
你需要为"电商平台"项目编写后端专家 Skill。

项目特点：
- 电商核心业务（商品、订单、支付）
- 高并发场景（秒杀活动）
- 分布式事务（Seata）
- 缓存策略（Redis 多级缓存）
```

### 向模型提问

```
提示词：

"请帮我编写一个后端专家（expert-backend）的 Skill 文件。

【项目信息】
- 项目名称：电商平台
- 架构：Spring Cloud 微服务
- 技术栈：
  - 后端：Java 11 + Spring Boot 3.x + Spring Cloud Alibaba
  - 数据库：MySQL 8.0 + Redis 7
  - 缓存：Redis 多级缓存（本地 Caffeine + 分布式 Redis）
  - 分布式事务：Seata
  - 消息队列：RabbitMQ
- 约束条件：
  - 必须使用 Java 11+
  - 所有接口返回 Result<T>
  - 高并发场景必须使用缓存 + 限流

【角色职责】
后端专家负责：
1. Controller 层（RESTful API）
2. Service 层（业务逻辑）
3. Mapper 层（数据访问）
4. 缓存策略（Redis）
5. 分布式事务（Seata）

【输出要求】
请输出完整的 SKILL.md 文件，重点包含：
1. Spring Boot 代码规范
2. 缓存策略（Redis 多级缓存）
3. 分布式事务处理
4. 高并发优化（限流、降级）"
```

### 关键部分示例

```markdown
## 你的工作流

### 1. 接收架构方案
- 从架构师处获取 API 契约
- 理解业务逻辑

### 2. 实现 Controller 层
- RESTful 接口设计
- 参数校验（@Valid）
- 统一响应（Result<T>）

### 3. 实现 Service 层
- 业务逻辑实现
- 缓存策略（Redis）
- 分布式事务（@GlobalTransactional）

### 4. 实现 Mapper 层
- MyBatis XML 映射
- 分页查询（PageHelper）

### 5. 高并发优化
- 缓存策略（本地 Caffeine + 分布式 Redis）
- 限流（Sentinel）
- 降级策略

## 约束条件

1. **Java 11+**：可以使用 record、var、文本块
2. **Result<T> 封装**：所有接口必须返回 Result<T>
3. **缓存策略**：热点数据必须使用多级缓存
4. **分布式事务**：跨服务操作必须使用 @GlobalTransactional
5. **限流降级**：高并发接口必须配置 Sentinel 限流"
```

---

## 6. 实战案例 3：编写前端专家 Skill

### 场景

```
你需要为"数据可视化平台"项目编写前端专家 Skill。

项目特点：
- ECharts 复杂图表
- WebSocket 实时数据推送
- 拖拽式仪表盘
- 大屏展示
```

### 向模型提问

```
提示词：

"请帮我编写一个前端专家（expert-frontend）的 Skill 文件。

【项目信息】
- 项目名称：数据可视化平台
- 技术栈：
  - 前端：Vue 3 + TypeScript + Vite
  - UI 框架：Ant Design Vue 4.x
  - 图表：ECharts 5.x
  - 状态管理：Pinia
  - 路由：Vue Router 4
  - WebSocket：原生 WebSocket API
- 约束条件：
  - 必须使用 TypeScript（不能用 JavaScript）
  - 所有组件必须使用 Vue 3 Composition API
  - 实时数据必须通过 WebSocket 推送

【角色职责】
前端专家负责：
1. Vue 3 页面实现
2. ECharts 图表封装
3. WebSocket 实时数据接收
4. 拖拽式仪表盘
5. 大屏展示

【输出要求】
请输出完整的 SKILL.md 文件，重点包含：
1. Vue 3 Composition API 规范
2. ECharts 封装规范
3. WebSocket 处理规范
4. 响应式设计"
```

---

## 7. 实战案例 4：根据业务场景定制 Skill

### 场景

```
你需要为"金融风控系统"编写测试专家 Skill。

业务特点：
- 实时风控决策（毫秒级响应）
- 规则引擎（Drools）
- 机器学习模型（Python 服务）
- 合规要求（审计日志）
```

### 向模型提问

```
提示词：

"请帮我编写一个测试专家（expert-tester）的 Skill 文件。

【项目信息】
- 项目名称：金融风控系统
- 技术栈：
  - 后端：Java 11 + Spring Boot 3.x
  - 规则引擎：Drools
  - 机器学习：Python（Flask）
  - 数据库：MySQL 8.0
- 约束条件：
  - 风控决策响应时间 < 100ms
  - 所有操作必须记录审计日志
  - 必须符合金融合规要求

【角色职责】
测试专家负责：
1. 单元测试（JUnit 5 + Mockito）
2. 集成测试（Spring Boot Test）
3. 性能测试（JMeter）
4. 合规审计（审计日志验证）

【输出要求】
请输出完整的 SKILL.md 文件，重点包含：
1. 单元测试规范（覆盖率 ≥ 80%）
2. 性能测试标准（响应时间 < 100ms）
3. 合规审计要求
4. 规则引擎测试（Drools）"
```

---

## 8. 实战案例 5：根据代码结构定制 Skill

### 场景

```
你的项目有特殊的代码结构：

backend/
├── services/
│   ├── device-service/
│   ├── data-service/
│   └── alarm-service/
├── common/
│   ├── core/
│   └── utils/
└── api-gateway/

你需要为这种结构编写后端专家 Skill。
```

### 向模型提问

```
提示词：

"请帮我编写一个后端专家（expert-backend）的 Skill 文件。

【项目代码结构】
backend/
├── services/
│   ├── device-service/      ← 设备服务
│   │   ├── controller/
│   │   ├── service/
│   │   └── mapper/
│   ├── data-service/        ← 数据服务
│   └── alarm-service/       ← 告警服务
├── common/
│   ├── core/                ← 核心工具类
│   └── utils/               ← 通用工具
└── api-gateway/             ← API 网关

【角色职责】
后端专家需要：
1. 按照上述代码结构编写代码
2. 每个服务独立目录（controller/service/mapper）
3. 公共代码放在 common/ 目录
4. 所有 API 通过 api-gateway 暴露

【输出要求】
请输出完整的 SKILL.md 文件，重点包含：
1. 代码目录规范
2. 服务间调用规范（Feign）
3. 公共代码使用规范
4. API Gateway 配置"
```

### 关键部分示例

```markdown
## 背景知识

### 项目代码结构
backend/
├── services/
│   ├── device-service/      ← 设备服务
│   │   ├── controller/      ← REST 控制器
│   │   ├── service/         ← 业务逻辑
│   │   └── mapper/          ← 数据访问
│   ├── data-service/        ← 数据服务
│   └── alarm-service/       ← 告警服务
├── common/
│   ├── core/                ← 核心工具类
│   └── utils/               ← 通用工具
└── api-gateway/             ← API 网关

### 代码规范
- 每个服务独立目录（controller/service/mapper）
- 公共代码放在 common/ 目录
- 所有 API 通过 api-gateway 暴露
- 服务间调用使用 OpenFeign

## 约束条件

1. **目录规范**：严格按照 services/{service-name}/ 结构
2. **公共代码**：通用工具放在 common/utils/
3. **API 网关**：所有 API 必须通过 api-gateway 暴露
4. **服务调用**：使用 OpenFeign 进行服务间调用"
```

---

## 9. 常见问题

### Q1：如何判断 Skill 文件是否写得好？

```
检查清单：

□ YAML 元数据正确？
  → name: expert-{角色名}
  → description: 清晰描述触发条件

□ 背景知识完整？
  → 技术栈、架构、约束都包含？

□ 工作流清晰？
  → 步骤具体可执行？

□ 输出格式有示例？
  → 结构化的示例？

□ 约束条件明确？
  → 必须遵守的规则？

□ 经过测试验证？
  → 实际使用中符合预期？"
```

### Q2：如何调试 Skill 文件？

```
调试步骤：

1. 检查文件路径
   ✅ 正确：.{工具名}/skills/expert-{角色名}/SKILL.md

2. 检查文件名
   ✅ 正确：SKILL.md（大写）

3. 检查 YAML 元数据
   ✅ 正确：name: expert-{角色名}

4. 测试输出
   "请用专家团模式帮我开发 XX 功能"
   → 检查输出是否符合预期

5. 迭代优化
   根据测试结果调整内容"
```

### Q3：如何更新 Skill 文件？

```
更新步骤：

1. 修改 Skill 文件内容
   跨工具专家团/{角色名}/SKILL.md

2. 同步到所有工具目录
   .{工具名}/skills/expert-{角色名}/SKILL.md

3. 重启工具（如果需要）
   退出工具，重新打开

4. 测试验证
   "请用专家团模式帮我开发 XX 功能"

5. 迭代优化
   根据测试结果继续调整"
```

---

## 总结

### 编写 Skill 的核心公式

```
好的 Skill = 清晰的背景知识 + 具体的工作流 + 明确的约束条件
```

### 5 步法回顾

```
1. 收集项目信息（架构、技术栈、约束）
2. 向模型提问（使用提示词模板）
3. 审查模型输出（检查清单）
4. 测试验证（实际使用）
5. 迭代优化（根据结果调整）
```

### 下一步

- 📖 [快速入门指南](./00-快速入门指南.md) → 学习基础概念
- 📖 [跨工具协作教程](./跨工具协作教程.md) → 学习实战技巧
- 📁 每个工具的详细配置 → 查看 `跨工具专家团/{工具名}/` 目录
