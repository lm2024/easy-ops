# 脚本文件管理功能设计文档

## 1. 需求概述

### 1.1 业务需求
- 管理 Agent 节点的 `start.sh` / `stop.sh` 等运维脚本
- 对服务器任意目录的文件进行维护分发（如 Nginx 配置、定时任务、系统配置等）
- 支持脚本文件的版本管理和一致性检查

### 1.2 使用场景
| 场景 | 文件类型 | 目录位置 | 说明 |
|------|----------|----------|------|
| 应用启停脚本 | `.sh` | 项目部署目录 | `start.sh`, `stop.sh`, `restart.sh` |
| Nginx 配置 | `.conf` | `/etc/nginx/` | 站点配置、反向代理配置 |
| 定时任务 | `.cron` | `/etc/cron.d/` | Crontab 配置文件 |
| 系统服务 | `.service` | `/etc/systemd/system/` | Systemd 服务配置 |
| 日志轮转 | `.logrotate` | `/etc/logrotate.d/` | 日志轮转配置 |
| 自定义脚本 | 任意 | 任意 | 运维自定义脚本 |

---

## 2. 整体架构设计

### 2.1 功能定位
```
配置管理页面
├── Tab 1: 应用配置 (现有功能)
│   └── 管理项目部署目录下的 yml/yaml 配置文件
│
└── Tab 2: 脚本文件 (新增功能)
    └── 管理任意目录下的脚本/配置文件
```

### 2.2 核心区别

| 维度 | 应用配置 (现有) | 脚本文件 (新增) |
|------|-----------------|-----------------|
| **目录位置** | 固定为 `{deployDir}/config/` | 任意目录 |
| **文件类型** | `.yml`, `.yaml`, `.properties`, `.conf` | 任意类型 |
| **路径模式** | 相对路径 | 绝对路径或相对路径 |
| **扫描方式** | 自动扫描 `config/` 目录 | 手动添加或指定目录扫描 |
| **特殊属性** | 无 | 可执行权限、文件所有者等 |

---

## 3. 数据库设计

### 3.1 方案对比

| 方案 | 优点 | 缺点 | 推荐度 |
|------|------|------|--------|
| **A: 复用现有表** | 无需新建表，代码改动小 | 字段冗余，逻辑耦合 | ⭐⭐ |
| **B: 新建独立表** | 结构清晰，扩展性强 | 需要新建表和 Mapper | ⭐⭐⭐⭐⭐ |
| **C: 扩展现有表** | 兼顾复用和扩展 | 需要修改现有表结构 | ⭐⭐⭐ |

### 3.2 推荐方案：新建独立表

```sql
-- 项目脚本文件定义表
CREATE TABLE project_script_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL COMMENT '项目ID',
    file_name VARCHAR(200) NOT NULL COMMENT '文件名',
    file_path VARCHAR(500) NOT NULL COMMENT '文件路径（绝对路径或相对路径）',
    file_type VARCHAR(50) COMMENT '文件类型：sh/conf/cron/service/other',
    description VARCHAR(500) COMMENT '文件描述',
    is_executable INT DEFAULT 0 COMMENT '是否需要可执行权限：0-否 1-是',
    auto_backup INT DEFAULT 1 COMMENT '分发前是否自动备份：0-否 1-是',
    create_time BIGINT COMMENT '创建时间',
    update_time BIGINT COMMENT '更新时间',
    UNIQUE KEY uk_project_path (project_id, file_path)
) COMMENT '项目脚本文件定义表';
```

### 3.3 脚本快照表

```sql
-- 节点脚本快照表
CREATE TABLE node_script_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL COMMENT '项目ID',
    node_id BIGINT NOT NULL COMMENT '节点ID',
    script_file_id BIGINT NOT NULL COMMENT '脚本文件ID',
    content_hash VARCHAR(64) COMMENT '文件内容SHA256哈希',
    content_size BIGINT COMMENT '文件大小（字节）',
    file_mode INT COMMENT '文件权限（八进制）',
    sync_status INT DEFAULT 0 COMMENT '同步状态：0-未知 1-一致 2-差异 3-定制',
    last_sync_time BIGINT COMMENT '最后同步时间',
    update_time BIGINT COMMENT '更新时间',
    UNIQUE KEY uk_node_file (node_id, script_file_id)
) COMMENT '节点脚本快照表';
```

---

## 4. 后端设计

### 4.1 新增 Model

```java
// ProjectScriptFileModel.java
@Data
public class ProjectScriptFileModel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Long id;
    private Long projectId;
    private String fileName;
    private String filePath;      // 绝对路径或相对路径
    private String fileType;      // sh/conf/cron/service/other
    private String description;
    private Integer isExecutable; // 是否需要可执行权限
    private Integer autoBackup;   // 分发前是否自动备份
    private Long createTime;
    private Long updateTime;
}
```

### 4.2 新增 Mapper

```java
@Mapper
public interface ProjectScriptFileMapper {
    ProjectScriptFileModel findById(@Param("id") Long id);
    List<ProjectScriptFileModel> findByProjectId(@Param("projectId") Long projectId);
    int insert(ProjectScriptFileModel model);
    int update(ProjectScriptFileModel model);
    int deleteById(@Param("id") Long id);
}
```

### 4.3 新增 Controller

```java
@RestController
@RequestMapping("/script")
public class ScriptMgmtController {
    
    @Autowired
    private ScriptMgmtService scriptMgmtService;
    
    // 查询项目脚本文件列表
    @GetMapping("/files")
    public Result<?> listFiles(@RequestParam Long projectId) { ... }
    
    // 新增脚本文件定义
    @PostMapping("/files")
    public Result<?> createFile(@RequestBody ProjectScriptFileModel model) { ... }
    
    // 更新脚本文件定义
    @PutMapping("/files/{id}")
    public Result<?> updateFile(@PathVariable Long id, @RequestBody ProjectScriptFileModel model) { ... }
    
    // 删除脚本文件定义
    @DeleteMapping("/files/{id}")
    public Result<?> deleteFile(@PathVariable Long id, @RequestParam Long projectId) { ... }
    
    // 扫描指定目录下的脚本文件
    @PostMapping("/scan")
    public Result<?> scanScriptFiles(@RequestParam Long projectId, @RequestParam String scanDir) { ... }
    
    // 读取脚本文件内容
    @GetMapping("/content")
    public Result<?> getContent(@RequestParam Long projectId, @RequestParam Long nodeId, 
                                @RequestParam Long scriptFileId) { ... }
    
    // 自动选在线节点读取脚本内容
    @GetMapping("/content/auto")
    public Result<?> getContentAuto(@RequestParam Long projectId, @RequestParam Long scriptFileId) { ... }
    
    // 分发脚本文件到指定节点
    @PostMapping("/distribute")
    public Result<?> distribute(@RequestBody Map<String, Object> body) { ... }
    
    // 获取各节点脚本快照
    @GetMapping("/snapshot")
    public Result<?> getSnapshot(@RequestParam Long projectId, @RequestParam Long scriptFileId) { ... }
    
    // 刷新所有节点快照哈希
    @PostMapping("/refresh")
    public Result<?> refresh(@RequestBody Map<String, Object> body) { ... }
}
```

### 4.4 新增 Service

```java
@Service
public class ScriptMgmtService {
    
    // 扫描指定目录下的脚本文件
    public List<ProjectScriptFileModel> scanAndImport(Long projectId, String scanDir) {
        // 1. 遍历项目在线节点
        // 2. 调用 Agent 扫描指定目录
        // 3. 发现未注册的脚本文件并自动导入
    }
    
    // 分发脚本文件
    public Map<String, Object> distribute(Long projectId, Long scriptFileId, String content,
                                          List<Long> targetNodeIds, boolean setExecutable,
                                          boolean autoBackup, Long operatorId) {
        // 1. 计算内容哈希
        // 2. 遍历目标节点
        // 3. 调用 Agent 写入文件
        // 4. 设置可执行权限（如果需要）
        // 5. 更新快照
    }
}
```

### 4.5 Agent 端新增接口

```java
@RestController
@RequestMapping("/file")
public class FileController {
    
    // 读取任意文件内容
    @GetMapping("/script")
    public Result<String> getScriptContent(@RequestParam String filePath) { ... }
    
    // 写入任意文件内容
    @PostMapping("/script")
    public Result<Map<String, Object>> writeScript(@RequestBody Map<String, Object> body) {
        // body: { filePath, content, backup, setExecutable }
    }
    
    // 扫描指定目录下的脚本文件
    @GetMapping("/script/discover")
    public Result<List<Map<String, Object>>> discoverScripts(@RequestParam String scanDir) { ... }
    
    // 获取文件状态（权限、大小、修改时间等）
    @GetMapping("/script/status")
    public Result<Map<String, Object>> getScriptStatus(@RequestParam String filePath) { ... }
}
```

---

## 5. 前端设计

### 5.1 页面结构

```vue
<template>
  <a-card>
    <a-tabs v-model:activeKey="activeTab">
      <!-- Tab 1: 应用配置 -->
      <a-tab-pane key="config" tab="应用配置">
        <!-- 现有配置管理内容 -->
      </a-tab-pane>
      
      <!-- Tab 2: 脚本文件 -->
      <a-tab-pane key="script" tab="脚本文件">
        <ScriptManagePanel :project-id="selectedProjectId" />
      </a-tab-pane>
    </a-tabs>
  </a-card>
</template>
```

### 5.2 脚本管理面板

```vue
<template>
  <div>
    <!-- 顶部操作栏 -->
    <div class="script-toolbar">
      <a-input v-model:value="scanDir" placeholder="扫描目录" style="width: 300px" />
      <a-button @click="handleScan">扫描</a-button>
      <a-button type="primary" @click="showAddModal">添加脚本</a-button>
    </div>
    
    <!-- 脚本列表 -->
    <a-table :dataSource="scriptFiles" :columns="columns">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="selectFile(record)">编辑</a>
            <a @click="handleDelete(record)">删除</a>
          </a-space>
        </template>
      </template>
    </a-table>
    
    <!-- 编辑器区域 -->
    <div v-if="selectedFile" class="script-editor">
      <div class="editor-header">
        <span>{{ selectedFile.fileName }}</span>
        <span class="file-path">{{ selectedFile.filePath }}</span>
      </div>
      
      <!-- 节点状态 -->
      <div class="node-status">
        <NodeStatusChip v-for="node in nodes" :key="node.id" :node="node" />
      </div>
      
      <!-- 代码编辑器 -->
      <MonacoEditor v-model:value="editContent" :language="editorLanguage" />
      
      <!-- 分发面板 -->
      <div class="distribute-panel">
        <a-checkbox-group v-model:value="distributeNodeIds">
          <a-checkbox v-for="node in nodes" :key="node.id" :value="node.id">
            {{ node.name }}
          </a-checkbox>
        </a-checkbox-group>
        <a-checkbox v-model:checked="setExecutable">设置可执行权限</a-checkbox>
        <a-checkbox v-model:checked="autoBackup">分发前备份</a-checkbox>
        <a-button type="primary" @click="handleDistribute">分发</a-button>
      </div>
    </div>
  </div>
</template>
```

### 5.3 新增 API

```typescript
// api/scriptMgmt.ts

/** 查询项目脚本文件列表 */
export function listScriptFiles(projectId: number) {
  return request.get<any, Result<ProjectScriptFileModel[]>>('/script/files', {
    params: { projectId }
  })
}

/** 新增脚本文件定义 */
export function createScriptFile(model: ProjectScriptFileModel) {
  return request.post<any, Result<ProjectScriptFileModel>>('/script/files', model)
}

/** 更新脚本文件定义 */
export function updateScriptFile(id: number, model: ProjectScriptFileModel) {
  return request.put<any, Result<ProjectScriptFileModel>>(`/script/files/${id}`, model)
}

/** 删除脚本文件定义 */
export function deleteScriptFile(id: number, projectId: number) {
  return request.delete<any, Result>(`/script/files/${id}`, { params: { projectId } })
}

/** 扫描指定目录下的脚本文件 */
export function scanScriptFiles(projectId: number, scanDir: string) {
  return request.post<any, Result<ProjectScriptFileModel[]>>('/script/scan', null, {
    params: { projectId, scanDir }
  })
}

/** 读取脚本文件内容 */
export function getScriptContent(projectId: number, nodeId: number, scriptFileId: number) {
  return request.get<any, Result<string>>('/script/content', {
    params: { projectId, nodeId, scriptFileId }
  })
}

/** 自动选在线节点读取脚本内容 */
export function getScriptContentAuto(projectId: number, scriptFileId: number) {
  return request.get<any, Result<{ content: string; nodeId: number; nodeName: string }>>('/script/content/auto', {
    params: { projectId, scriptFileId }
  })
}

/** 分发脚本文件 */
export function distributeScript(params: {
  projectId: number
  scriptFileId: number
  content: string
  targetNodeIds: number[]
  setExecutable?: boolean
  autoBackup?: boolean
}) {
  return request.post<any, Result>('/script/distribute', params)
}

/** 获取各节点脚本快照 */
export function getScriptSnapshot(projectId: number, scriptFileId: number) {
  return request.get<any, Result<ScriptSnapshotResult>>('/script/snapshot', {
    params: { projectId, scriptFileId }
  })
}

/** 刷新所有节点快照哈希 */
export function refreshScriptSnapshots(projectId: number, scriptFileId: number) {
  return request.post<any, Result>('/script/refresh', { projectId, scriptFileId })
}
```

### 5.4 新增类型定义

```typescript
// types/index.ts

/** 项目脚本文件定义 */
export interface ProjectScriptFileModel {
  id?: number
  projectId: number
  fileName: string           // 文件名，如 "start.sh"
  filePath: string           // 文件路径，如 "/app/scripts/start.sh"
  fileType?: string          // 文件类型：sh/conf/cron/service/other
  description?: string       // 文件描述
  isExecutable?: number      // 是否需要可执行权限：0-否 1-是
  autoBackup?: number        // 分发前是否自动备份：0-否 1-是
  createTime?: number
  updateTime?: number
}

/** 脚本快照查询结果 */
export interface ScriptSnapshotResult {
  scriptFile: ProjectScriptFileModel
  nodes: NodeScriptSnapshotModel[]
  allSame: boolean
}

/** 节点脚本快照 */
export interface NodeScriptSnapshotModel {
  id?: number
  projectId: number
  nodeId: number
  scriptFileId: number
  contentHash?: string
  contentSize?: number
  fileMode?: number          // 文件权限（八进制）
  syncStatus?: number        // 0-未知 1-一致 2-差异 3-定制
  lastSyncTime?: number
  updateTime?: number
  nodeName?: string
}
```

---

## 6. 复用与扩展策略

### 6.1 复用现有组件

| 组件 | 复用方式 | 说明 |
|------|----------|------|
| `AgentClient` | 直接复用 | 调用 Agent 接口 |
| `ConfigDiffService` | 直接复用 | 计算 SHA256 哈希 |
| `NodeConfigSnapshotMapper` | 参考实现 | 创建类似的 `NodeScriptSnapshotMapper` |
| `ConfigDistributeService` | 参考实现 | 创建类似的 `ScriptDistributeService` |
| 前端编辑器组件 | 直接复用 | Monaco Editor |
| 前端节点状态组件 | 直接复用 | NodeStatusChip |

### 6.2 扩展点

| 扩展点 | 说明 |
|--------|------|
| 文件类型支持 | 支持任意文件类型，不只是 yml/yaml |
| 路径模式 | 支持绝对路径和相对路径 |
| 权限管理 | 支持设置文件可执行权限 |
| 备份策略 | 支持自定义备份策略 |
| 扫描目录 | 支持扫描任意目录 |

---

## 7. 实施计划

### 7.1 第一阶段：基础功能（2-3天）

1. **数据库**
   - 创建 `project_script_file` 表
   - 创建 `node_script_snapshot` 表

2. **后端**
   - 创建 `ProjectScriptFileModel`
   - 创建 `ProjectScriptFileMapper`
   - 创建 `NodeScriptSnapshotMapper`
   - 创建 `ScriptMgmtService`（基础 CRUD）
   - 创建 `ScriptMgmtController`

3. **Agent 端**
   - 扩展 `FileController`，添加脚本文件读写接口
   - 创建 `ScriptFileService`（支持任意文件类型）

4. **前端**
   - 创建 `api/scriptMgmt.ts`
   - 创建 `ScriptManagePanel.vue`
   - 修改 `ConfigManageView.vue`，添加 Tab 切换

### 7.2 第二阶段：高级功能（2-3天）

1. **扫描功能**
   - 实现指定目录扫描
   - 支持文件类型过滤
   - 支持递归扫描

2. **分发功能**
   - 实现脚本文件分发
   - 支持设置可执行权限
   - 支持自动备份

3. **快照功能**
   - 实现脚本快照管理
   - 支持一致性检查
   - 支持差异对比

### 7.3 第三阶段：优化完善（1-2天）

1. **性能优化**
   - 并行扫描优化
   - 批量分发优化

2. **用户体验**
   - 文件类型图标
   - 语法高亮
   - 快捷操作

3. **文档完善**
   - API 文档
   - 使用说明

---

## 8. 风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| 文件权限问题 | 无法写入系统目录 | Agent 以 root 运行或配置 sudo |
| 文件类型多样 | 语法高亮困难 | 使用通用文本编辑器 |
| 路径冲突 | 多项目同路径文件冲突 | 使用 project_id + file_path 联合唯一键 |
| 大文件传输 | 网络超时 | 分块传输或流式传输 |

---

## 9. 总结

本设计方案通过以下方式实现脚本文件管理功能：

1. **独立模块**：新建独立的数据库表和服务，避免与现有配置管理耦合
2. **复用架构**：复用现有的 Agent 通信、差异对比、快照管理等基础设施
3. **灵活扩展**：支持任意文件类型和目录位置
4. **用户友好**：复用现有的编辑器和分发组件，保持一致的用户体验

预计总工期：5-8 天
