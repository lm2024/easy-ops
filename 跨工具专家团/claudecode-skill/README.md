# Claude Code 专家团配置指南

> 本文档教你如何在 Claude Code 中使用"专家团模式"。

---

## 1. 安装 Skill 文件

### 第 1 步：进入项目目录

```bash
cd /Users/lm/Documents/GitHub/easy-ops
```

### 第 2 步：创建 Claude Code 的 Skill 目录

```bash
mkdir -p .claude-code/skills
```

### 第 3 步：复制 Skill 文件

```bash
# 复制最小配置（3 个专家）
cp 跨工具专家团/shared-skill/expert-*/SKILL.md .claude-code/skills/

# 或者复制完整配置（5 个专家）
cp 跨工具专家团/full-skill/expert-*/SKILL.md .claude-code/skills/
```

### 第 4 步：验证目录结构

```bash
ls .claude-code/skills/expert-*/SKILL.md
```

**预期输出**：
```
.claude-code/skills/expert-architect/SKILL.md
.claude-code/skills/expert-backend/SKILL.md
.claude-code/skills/expert-frontend/SKILL.md
.claude-code/skills/expert-tester/SKILL.md
.claude-code/skills/expert-reviewer/SKILL.md
```

---

## 2. 使用专家团模式

### 方法 1：自动模式（推荐新手）

```bash
# 在 Claude Code 中输入：

"请用专家团模式帮我开发批量节点操作功能"

# Claude Code 会自动：
# 1. 扫描 .claude-code/skills/ 目录
# 2. 加载 {expert-*}.SKILL.md 文件
# 3. 按角色分工工作（架构师 → 后端 → 前端 → 测试 → 审查）"
```

### 方法 2：手动指定角色

```bash
# 在 Claude Code 中输入：

"请按以下角色分工工作：

1. 架构师：先出设计方案，输出 API 契约
2. 后端：按方案实现 Java 代码
3. 前端：按方案实现 Vue 页面"
```

### 方法 3：完整流程

```bash
# 在 Claude Code 中输入：

"请用专家团模式帮我开发批量节点操作功能，包含完整流程：
 1. 架构师：设计系统架构 + API 契约
 2. 后端：实现 Java 代码（Controller/Service/Mapper）
 3. 前端：实现 Vue 页面
 4. 测试：编写 JUnit 测试用例
 5. 审查：检查代码质量和安全性"
```

---

## 3. 实战案例

### 案例：开发"批量节点操作"功能

#### 第 1 步：在 Claude Code 中输入

```
"请用专家团模式帮我开发'批量节点操作'功能。

需求：
- 用户可以选择多个节点
- 一键执行启动/停止/重启
- 实时显示操作进度
- 支持失败重试"
```

#### 第 2 步：Claude Code 输出

```
## 架构师方案

### 涉及模块
- Server: 新增 BatchOperateController、BatchOperateService
- Agent: 接收批量指令，并行执行
- Frontend: 新增 BatchOperateView.vue

### API 设计
POST /api/batch/operate    批量操作
GET  /api/batch/status/{id} 查询进度

### 数据模型
batch_task 表：
- id: BIGINT PRIMARY KEY
- node_ids: VARCHAR(500) 节点 ID 列表
- operation: VARCHAR(20) 操作类型
- status: VARCHAR(20) 状态
```

```java
// 后端代码

@RestController
@RequestMapping("/api/batch")
public class BatchOperateController {
    
    @Autowired
    private BatchOperateService batchOperateService;
    
    @PostMapping("/operate")
    public Result<BatchTaskModel> operate(@RequestBody OperateRequest request) {
        return Result.success(batchOperateService.batchOperate(request));
    }
}
```

```vue
<!-- 前端代码 -->

<template>
  <div>
    <a-table :data="nodes" @select="handleSelect">
      <a-column title="节点名称" data-index="name" />
    </a-table>
    
    <a-space>
      <a-button @click="handleStart">启动</a-button>
      <a-button @click="handleStop">停止</a-button>
    </a-space>
  </div>
</template>
```

```java
// 测试代码

@Test
void testBatchOperate() {
    OperateRequest request = new OperateRequest();
    request.setNodeIds(Arrays.asList(1L, 2L, 3L));
    request.setOperation("start");
    
    BatchTaskModel task = batchOperateService.batchOperate(request);
    assertNotNull(task);
    assertEquals("running", task.getStatus());
}
```

---

## 4. 常见问题

### Q1：Skill 文件没有被加载？

```
解决方案：

1. 检查目录是否正确
   ✅ 正确：.claude-code/skills/expert-architect/SKILL.md

2. 检查文件名是否正确
   ✅ 正确：SKILL.md（大写）

3. 检查 YAML 元数据
   ✅ 正确：name: expert-architect

4. 重启 Claude Code
   → 退出工具，重新打开"
```

### Q2：AI 没有按角色输出？

```
解决方案：

1. 在提示词中强调角色
   "请以后端专家身份，输出 Java 代码"

2. 检查 Skill 文件中的"角色定义"部分

3. 提供具体示例
   "请输出 Controller/Service/Mapper 三层代码"
```

---

## 5. 目录结构

```
.claude-code/skills/
├── expert-architect/
│   └── SKILL.md           ← 架构师专家
├── expert-backend/
│   └── SKILL.md           ← 后端专家
├── expert-frontend/
│   └── SKILL.md           ← 前端专家
├── expert-tester/
│   └── SKILL.md           ← 测试专家
└── expert-reviewer/
    └── SKILL.md           ← 审查专家
```

---

## 6. 下一步

- 📖 [快速入门指南](../00-快速入门指南.md)
- 📖 [跨工具协作教程](../跨工具协作教程.md)
- 📖 [如何用模型编写 Skill](../如何用模型编写Skill.md)
