# EasyOps 多租户能力实施版架构设计

> 版本：v2.0（以当前代码为准）
> 日期：2026-08-08
> 目标：在不破坏现有单租户部署的前提下，引入租户隔离、用户管理和项目级授权。

## 1. 实施结论

EasyOps 采用共享数据库、共享 Server、行级租户隔离的方案：

```text
平台管理员 SUPER_ADMIN
  -> 租户 TENANT
      -> 节点 NODE（一个 Agent 只能归属一个租户）
      -> 项目 PROJECT
          -> 版本 / 部署 / 监控 / 配置 / 日志 / 告警 / 自愈 / Nginx
```

用户不直接拥有多个 `tenant_id` 字段，而通过成员关系加入租户：

```text
sys_user 1:N tenant_user N:1 tenant
tenant_user + user_project_relation
```

权限从高到低分为三层：

| 层级 | 角色/关系 | 能力 |
|---|---|---|
| 平台 | `SUPER_ADMIN` | 管理所有租户、平台配置、全局脚本和数据库维护 |
| 租户 | `TENANT_ADMIN` | 管理本租户用户、节点、项目和本租户配置 |
| 项目 | `user_project_relation` | 在授权项目内查看或操作资源 |
| 普通 | `OPERATOR` / `VIEWER` | 按项目授权执行操作或只读 |

兼容现有部署：历史数据全部迁移到 `default` 租户，历史 `admin` 作为 `SUPER_ADMIN`，其他用户加入 `default`；旧 API 不要求前端传租户参数，由登录态确定当前租户。

## 2. 当前代码事实与必须修正项

1. 当前用户管理后端已经有基础权限校验，不能再按“完全无校验”实施。
2. 后端角色值是小写 `admin/operator`，前端判断是大写 `ADMIN/OPERATOR`，必须统一为大小写不敏感的权限工具或统一存储大写。
3. `project_info.node_ids` 是逗号字符串，节点没有 `project_id`。本版本不把节点租户从项目绑定推断，而是给节点明确设置租户。
4. `/nodes`、`/projects`、Agent 升级节点列表、Nginx source 列表及部分详情接口仍有全局查询，必须在 Controller、Service、Mapper 三层同时收口。
5. Monitor、Notification WebSocket 当前存在跨用户广播；Deploy、Console、知识库协作还需要在握手和入房间时校验资源归属。
6. 后台调度器没有 HTTP 请求上下文，必须使用“按租户/资源显式传参”，不能读取线程请求上下文。

## 3. 数据模型

### 3.1 新增表

```sql
tenant(id, code, name, status, create_time, update_time)
tenant_user(id, tenant_id, user_id, role, status, create_time, update_time)
```

约束：

- `tenant.code` 全局唯一；`tenant_user(tenant_id,user_id)` 唯一。
- 每个启用用户至少有一个启用租户成员关系。
- `SUPER_ADMIN` 可以跨租户；其他角色必须有当前租户成员关系。
- 默认租户固定 code 为 `default`，只用于兼容存量数据。

### 3.2 资源表

第一阶段直接增加 `tenant_id` 的表：

```text
node_info, project_info, version_package, deploy_record, monitor_snapshot,
alarm_record, self_heal_policy, self_heal_event, notification_record,
agent_upgrade_record, nginx_access_source, nginx_source_whitelist,
nginx_traffic_alarm_rule
```

项目子表通过 `project_id` 继承租户；高频 Nginx 聚合表先通过 `source_id` 反查已授权 source，避免 ingest 端伪造租户。后续如数据量证明需要，再物化 `tenant_id`。

必须同步处理 `user_project_relation`：项目授权写入时校验项目与当前租户一致，查询时通过项目租户过滤。

## 4. 请求与 Agent 鉴权

### 4.1 HTTP

`AuthInterceptor` 从用户 token 解析 `userId、role、currentTenantId`，写入请求属性。普通接口禁止从 query/body/header 接收任意租户作为权限依据。

平台管理员查看租户时只能通过服务端显式的“切换当前租户”动作取得受控上下文；不能让普通用户伪造 `X-Tenant-Id`。

### 4.2 Agent

- Agent token 只绑定一个 node；node 只属于一个 tenant。
- Server 接收心跳、日志、Nginx ingest、配置回传时，从 token 得到 node，再从 node 得到 tenant。
- 所有 `projectId/nodeId/sourceId` 组合必须校验同租户和资源关系。
- Agent 端不接收租户参数，不相信客户端传入的租户字段。

## 5. 接口隔离清单

必须覆盖列表、下拉、详情、写入、导出和批量接口：

| 范围 | 接口 |
|---|---|
| 核心下拉 | `/nodes`、`/projects`、版本项目下拉、Agent 升级节点下拉 |
| 核心操作 | 节点详情/修改/删除、项目详情/修改/删除、版本、部署、配置、日志 |
| 监控 | 应用监控、Agent 状态、监控仪表盘 |
| 告警 | 告警记录、告警规则、自愈策略、通知 |
| Nginx | source 列表、source 配置、白名单、告警规则、overview/rank/trend |
| 管理 | 用户、租户、项目授权、操作审计 |
| 实时 | monitor、notification、deploy、console、kb-collab |

任何带 `id` 的接口都必须先做资源归属校验，再调用 Mapper。只过滤列表不算完成隔离。

## 6. 后台任务

后台任务统一采用以下方式：

- 平台任务：查询所有启用租户，逐租户执行；
- 资源任务：Mapper 查询返回资源自身租户，后续调用显式携带 tenant/node/project；
- 分布式锁：锁名增加 tenant 或资源维度，避免不同租户相互阻塞；
- 清理任务：按表的时间字段清理，不依赖用户请求上下文。

## 7. WebSocket

握手成功后把 `userId、role、tenantId` 写入 session attributes。

- monitor：只向同租户 session 推送指标；
- notification：只向通知所属租户和目标用户推送；
- deploy/console：入房间时按 deploy/project/node 校验；
- kb-collab：按 docId 校验文档租户和知识库权限；
- 不接受前端传来的 tenantId 作为授权依据。

## 8. 迁移与兼容

迁移顺序必须是：

1. 建 tenant、tenant_user；
2. 建 default 租户；
3. 给用户建立成员关系；
4. 给资源增加 tenant_id 并回填；
5. 新代码先只读 default，验证后启用强制隔离；
6. 验证完成后再增加非空约束和租户范围唯一索引。

不能使用“super_admin 永久绕过所有条件”作为普通业务实现方式；平台查询必须显式使用 platform scope，避免误把跨租户查询带入普通 Service。

## 9. 验收标准

至少准备两个租户 A/B，分别拥有节点、项目、版本、部署、监控、Nginx source、告警和用户：

- A 用户所有列表、下拉、详情、导出、批量接口都看不到 B；
- A 的 WebSocket 不收到 B 的监控、通知、部署和协作消息；
- A 不能使用 B 的 projectId/nodeId/sourceId/versionId 进行写操作；
- Agent A 的 token 不能 ingest B 的 source，也不能操作 B 的项目；
- 后台监控、告警、自愈任务不会跨租户关联资源；
- 租户管理员不能管理其他租户用户；
- 平台管理员可审计跨租户操作，但必须有明确 platform scope；
- 原有单租户部署、默认 admin 登录、Docker Agent 心跳和前端下拉保持可用。

## 10. 实施阶段

- Phase 0：角色统一、租户基础表、成员关系、默认租户迁移、上下文和公共授权服务。
- Phase 1：节点/项目/版本/部署/监控及所有核心下拉和详情接口。
- Phase 2：WebSocket、后台调度、Agent ingest/升级/文件操作边界。
- Phase 3：Nginx、告警、自愈、通知、配置、脚本、日志。
- Phase 4：知识库、平台管理员租户切换、审计和多实例 Token 存储。

本次实施先完成 Phase 0 和 Phase 1，并为后续阶段保留明确的授权入口和测试边界。
