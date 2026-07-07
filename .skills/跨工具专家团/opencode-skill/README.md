# Opencode 专家团配置指南

> 本文档教你如何在 Opencode 中使用"专家团模式"。

---

## 1. 安装 Skill 文件

### 第 1 步：进入项目目录

```bash
cd /Users/lm/Documents/GitHub/easy-ops
```

### 第 2 步：创建 Opencode 的 Skill 目录

```bash
mkdir -p .opencode/skills
```

### 第 3 步：复制 Skill 文件

```bash
# 复制最小配置（3 个专家）
cp 跨工具专家团/shared-skill/expert-*/SKILL.md .opencode/skills/

# 或者复制完整配置（5 个专家）
cp 跨工具专家团/full-skill/expert-*/SKILL.md .opencode/skills/
```

### 第 4 步：验证目录结构

```bash
ls .opencode/skills/expert-*/SKILL.md
```

---

## 2. 使用专家团模式

### 在 Opencode 中输入：

```
"请用专家团模式帮我开发批量节点操作功能"

# 或者手动指定角色：

"请按以下角色分工工作：
 1. 架构师：先出设计方案
 2. 后端：按方案实现 Java 代码
 3. 前端：按方案实现 Vue 页面"
```

---

## 3. 目录结构

```
.opencode/skills/
├── expert-architect/
│   └── SKILL.md
├── expert-backend/
│   └── SKILL.md
├── expert-frontend/
│   └── SKILL.md
├── expert-tester/
│   └── SKILL.md
└── expert-reviewer/
    └── SKILL.md
```

---

## 4. 常见问题

### Q1：Skill 文件没有被加载？

```
解决方案：

1. 检查目录是否正确
   ✅ 正确：.opencode/skills/expert-architect/SKILL.md

2. 检查文件名是否正确
   ✅ 正确：SKILL.md（大写）

3. 检查 YAML 元数据
   ✅ 正确：name: expert-architect

4. 重启 Opencode
   → 退出工具，重新打开"
```

---

## 5. 下一步

- 📖 [快速入门指南](../00-快速入门指南.md)
- 📖 [跨工具协作教程](../跨工具协作教程.md)
- 📖 [如何用模型编写 Skill](../如何用模型编写Skill.md)
