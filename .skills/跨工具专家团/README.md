# 跨工具专家团 · 完整目录索引

> 本文档是整个"跨工具专家团"文档体系的导航图。

---

## 文档结构

```
跨工具专家团/
├── 00-快速入门指南.md          ← 【新手必读】5 分钟上手
├── 跨工具协作教程.md           ← 【实战教程】手把手教你玩
├── 如何用模型编写Skill.md      ← 【进阶教程】学习自定义 Skill
├── README.md                   ← 本文档（目录索引）
│
├── shared-skill/               ← 【最小配置】3 个专家（推荐新手）
│   ├── expert-architect/
│   │   └── SKILL.md           ← 架构师专家
│   ├── expert-backend/
│   │   └── SKILL.md           ← 后端专家
│   └── expert-frontend/
│       └── SKILL.md           ← 前端专家
│
├── full-skill/                 ← 【完整配置】5 个专家
│   ├── expert-architect/
│   │   └── SKILL.md
│   ├── expert-backend/
│   │   └── SKILL.md
│   ├── expert-frontend/
│   │   └── SKILL.md
│   ├── expert-tester/
│   │   └── SKILL.md           ← 测试专家
│   └── expert-reviewer/
│       └── SKILL.md           ← 审查专家
│
├── claudecode-skill/           ← 【Claude Code】专用配置
│   └── README.md
├── opencode-skill/             ← 【Opencode】专用配置
│   └── README.md
├── cline-skill/                ← 【Cline】专用配置
│   └── README.md
├── continue-skill/             ← 【Continue】专用配置
│   └── README.md
├── kilo-skill/                 ← 【Kilo】专用配置
│   └── README.md
├── hermes-skill/               ← 【Hermes】专用配置
│   └── README.md
├── openclaw-skill/             ← 【OpenClaw】专用配置
│   └── README.md
└── codex-skill/                ← 【Codex】专用配置
    └── README.md
```

---

## 使用路径

### 路径 1：新手快速上手（推荐）

```
第 1 天：阅读 00-快速入门指南.md
  → 了解什么是专家团模式
  → 了解支持的 8 种工具
  → 了解核心概念

第 2 天：安装 Skill 文件
  → 复制 shared-skill/ 到你的工具目录
  → 尝试"专家团模式"

第 3 天：实战练习
  → 阅读 跨工具协作教程.md
  → 尝试"双工具接力"模式"
```

### 路径 2：进阶定制（有经验用户）

```
第 1 天：阅读 如何用模型编写Skill.md
  → 学习 Skill 文件标准结构
  → 学习 5 步编写法

第 2 天：向模型提问
  → 使用提示词模板生成 Skill 文件
  → 审查模型输出

第 3 天：测试验证
  → 将 Skill 文件复制到工具目录
  → 实际使用，验证效果"
```

### 路径 3：跨工具协作（高级用户）

```
第 1 天：阅读 跨工具协作教程.md
  → 了解 4 种协作模式
  → 了解核心原理

第 2 天：实战演练
  → 尝试"多工具接力"模式
  → 尝试"全工具竞赛"模式"
```

---

## 快速查找

### 我想了解基本概念

```
→ 阅读 00-快速入门指南.md
  → 第 1 节：什么是专家团模式
  → 第 2 节：支持的 8 种工具清单
  → 第 3 节：核心概念"
```

### 我想安装 Skill 文件

```
→ 阅读 00-快速入门指南.md
  → 第 4 节：快速上手（5 分钟）"
```

### 我想学习跨工具协作

```
→ 阅读 跨工具协作教程.md
  → 第 3 节：实战案例 1：Hermes 当总指挥
  → 第 4 节：实战案例 2：Codex 当架构师
  → 第 5 节：实战案例 3：全工具接力赛"
```

### 我想自定义 Skill 文件

```
→ 阅读 如何用模型编写Skill.md
  → 第 3 节：编写流程（5 步法）
  → 第 4 节：实战案例 1：编写架构师 Skill
  → 第 5 节：实战案例 2：编写后端专家 Skill"
```

### 我想为特定工具配置

```
→ 查看对应工具的 README.md：
  → 跨工具专家团/claudecode-skill/README.md
  → 跨工具专家团/opencode-skill/README.md
  → 跨工具专家团/cline-skill/README.md
  → 跨工具专家团/continue-skill/README.md
  → 跨工具专家团/kilo-skill/README.md
  → 跨工具专家团/hermes-skill/README.md
  → 跨工具专家团/openclaw-skill/README.md
  → 跨工具专家团/codex-skill/README.md"
```

---

## Skill 文件说明

### shared-skill/（最小配置）

```
适合场景：快速上手，覆盖基本需求
包含专家：架构师 + 后端 + 前端
使用方法：复制到任何工具的 skills/ 目录"
```

### full-skill/（完整配置）

```
适合场景：完整开发流程，包含测试和审查
包含专家：架构师 + 后端 + 前端 + 测试 + 审查
使用方法：复制到任何工具的 skills/ 目录"
```

### 每个工具的 README.md

```
适合场景：特定工具的配置指南
包含内容：安装步骤、使用方法、常见问题
使用方法：按工具名称查找对应 README"
```

---

## 常见问题速查

### Q1：Skill 文件没有被加载？

```
→ 检查目录是否正确（.{工具名}/skills/）
→ 检查文件名是否正确（SKILL.md）
→ 检查 YAML 元数据是否正确（name: expert-xxx）"
```

### Q2：AI 没有按角色输出？

```
→ 在提示词中强调角色（"请以后端专家身份"）
→ 检查 Skill 文件中的"角色定义"部分
→ 提供具体示例"
```

### Q3：如何自定义 Skill 文件？

```
→ 阅读 如何用模型编写Skill.md
→ 使用 5 步编写法
→ 向模型提问，生成 Skill 文件"
```

---

## 下一步行动

```
1. 选择一个工具
2. 复制 shared-skill/ 到你的工具目录
3. 尝试"专家团模式"
4. 逐步过渡到跨工具协作
5. 学习如何自定义 Skill"
```

---

## 总结

```
新手：
  → 从 00-快速入门指南.md 开始
  → 安装 shared-skill/
  → 尝试"专家团模式"

进阶：
  → 阅读 如何用模型编写Skill.md
  → 向模型提问，生成 Skill 文件
  → 测试验证，迭代优化

高级：
  → 阅读 跨工具协作教程.md
  → 尝试"多工具接力"模式
  → 尝试"全工具竞赛"模式"
```
