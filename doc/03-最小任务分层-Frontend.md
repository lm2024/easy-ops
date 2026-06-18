# 一体云运维平台 - 最小任务分层文档 (前端篇) v1.0

## 使用说明

本文档将前端开发拆解为最小原子任务。每个原子任务对应一个独立编码会话。

---

## 第一阶段: 前端基础建设 (T-02-01 ~ T-02-10)

### T-02-01 初始化前端Vite+Vue3+TypeScript项目

依赖: 无
完成标准: frontend/ 目录下存在完整前端项目，npm install + npm run dev 成功运行。

执行步骤:
1. 在 frontend/ 目录下使用 npm 创建 Vite + Vue3 + TypeScript 项目；
2. 安装依赖: ant-design-vue, vue-router, pinia, axios, echarts, xterm, codemirror, dayjs, highlight.js, jszip；
3. 配置 vite.config.ts (别名设置 @ -> src, proxy 配置)；
4. 创建 tsconfig.json 配置路径别名 @ 指向 src；
5. 验证: npm run dev 能正常运行，浏览器访问 http://localhost:5173 显示默认页面。

### T-02-02 创建项目目录骨架

依赖: T-02-01
完成标准: 按详细设计文档的目录结构创建所有文件夹。

执行步骤:
1. 创建目录: src/api/、src/components/、src/views/、src/stores/、src/router/、src/types/、src/utils/、src/assets/；
2. 各目录内创建对应的空文件或占位文件；
3. 确保目录结构与详细设计文档前端篇要求一致。

### T-02-03 Axios 封装 (request.ts)

依赖: T-02-01
完成标准: 所有 HTTP 请求经过统一拦截器处理。

执行步骤:
1. 在 utils/request.ts 中封装 Axios 实例；
2. 基础URL 配置为 /api (Nginx 反向代理)；
3. 请求拦截器: 从内存读取 Token 并注入 Authorization 请求头；
4. 响应拦截器: 统一处理错误码 (401 跳转登录, 403 无权限, 500 系统错误)；
5. 文件传输接口 (upload/download) 设置超时时间为 5 分钟，其他接口 30 秒；
6. 提供 get/post/upload/download 四个快捷方法。

### T-02-04 统一响应类型定义 (types/api.ts)

依赖: T-02-01
完成标准: TypeScript 类型定义覆盖所有接口响应。

执行步骤:
1. 定义统一响应类型: Result<T> (code: number, message: string, data: T)；
2. 定义分页类型: PageResult<T> (list: T[], total: number)；
3. 定义错误码枚举: ErrorCode (SUCCESS, UNAUTHORIZED, FORBIDDEN, SERVER_ERROR, PARAM_ERROR)。

### T-02-05 Pinia 状态管理 (auth store)

依赖: T-02-03
完成标准: 用户登录状态能通过 Pinia 管理。

执行步骤:
1. 创建 stores/auth.ts；
2. 管理字段: token, userId, username, role (admin/operator)；
3. 提供 setAuth、clearAuth 方法；
4. Token 存储于内存 (不持久化)，刷新页面后需重新登录；
5. 提供 isLogin 计算属性 (是否有有效 Token)。

### T-02-06 路由配置与权限守卫 (router/index.ts)

依赖: T-02-05
完成标准: 未登录自动跳转登录页，无权限跳转 403 页。

执行步骤:
1. 创建路由表，定义所有路由及嵌套关系；
2. 路由使用 Vue Router 4 历史模式；
3. 为每个路由配置 meta.roles 字段 (admin 或 admin/operator)；
4. 路由守卫逻辑:
   - 已登录且 Token 有效: 放行或跳转到仪表盘；
   - 未登录: 重定向到 /login (白名单路由除外)；
   - 已登录但角色不匹配: 跳转 403 页面。

### T-02-07 登录页面 (LoginPage.vue)

依赖: T-02-03, T-02-05
完成标准: 用户能正常登录并跳转至仪表盘。

执行步骤:
1. 创建 views/login/LoginPage.vue；
2. 页面中心放置登录卡片 (Ant Design Card 组件)；
3. 表单字段: 用户名输入框、密码输入框 (密码类型)、登录按钮；
4. 调用 POST /api/auth/login 接口；
5. 登录成功后将 Token 存储到 Pinia store (auth store)；
6. 存储用户名和角色到 Pinia store，重定向到 /dashboard。

### T-02-08 整体布局框架 (MainLayout.vue)

依赖: T-02-07, T-02-06
完成标准: 登录后看到完整框架，包含侧边菜单、顶栏、内容区。

执行步骤:
1. 创建 views/layout/MainLayout.vue 作为所有受保护页面的父组件；
2. 使用 Ant Design Layout 组件:
   - 左侧: 固定宽度 220px 侧边菜单 (深色背景)；
   - 中间: 白色内容区 (router-view 渲染页面)；
   - 顶部: 固定高度 56px 顶栏；
3. 侧边菜单根据用户角色动态渲染 (管理员显示系统管理菜单，操作员隐藏)；
4. 顶栏显示用户名和退出按钮，退出时清除 Token 并跳转登录页。

### T-02-09 前端公共组件库 (1/2)

依赖: T-02-08
完成标准: 基础 UI 组件库编译通过。

执行步骤:
1. 创建 components/AppTable.vue - 封装表格组件:
   - 属性: columns, dataSource, loading, pagination, scroll, rowKey；
   - 默认支持分页、排序、筛选；
   - 插槽: custom 操作列；
2. 创建 components/AppModal.vue - 封装弹窗组件:
   - 属性: title, visible, width, confirmLoading, okText, cancelText；
   - 插槽: default 内容区；
3. 创建 components/AppForm.vue - 封装表单组件:
   - 属性: fields (字段配置), model, rules；
   - 插槽: 自定义表单项。

### T-02-10 前端公共组件库 (2/2)

依赖: T-02-09
完成标准: 所有公共组件编译通过。

执行步骤:
1. 创建 components/CodeEditor.vue - 封装 CodeMirror 编辑器:
   - 属性: value, readOnly, mode (yaml/java/log)；
   - 支持语法高亮、行号显示、代码折叠；
2. 创建 components/LogViewer.vue - 日志查看器:
   - 属性: lines, loading, autoScroll, keyword；
   - 带行号的文本展示，支持关键字高亮，支持自动滚动；
3. 创建 components/Terminal.vue - 封装 xterm.js 终端:
   - 属性: connected, command；
   - 支持 WebSocket 实时输出；
4. 创建 components/StatusBadge.vue - 状态标签组件；
5. 创建 components/StatsPanel.vue - 统计面板组件。

---

## 第二阶段: 功能页面开发 (T-02-11 ~ T-02-38)

### T-02-11 类型定义 (types/node.ts / types/project.ts 等)

依赖: T-02-04
完成标准: 所有数据模型有对应的 TypeScript 类型。

执行步骤:
1. 创建 types/node.ts: NodeModel (id, name, ip, port, token, status, osInfo, javaVersion)；
2. 创建 types/project.ts: ProjectModel (id, name, nodeIds, startScript, stopScript, jvmOpts, envVars)；
3. 创建 types/version.ts: VersionModel (id, projectId, jarName, filePath, version, sha256, remark)；
4. 创建 types/deploy.ts: DeployModel (id, projectId, versionId, nodeId, status, log)；
5. 创建 types/alarm.ts: AlarmModel (id, projectId, nodeId, type, content, sendResult)；
6. 创建 types/user.ts: UserModel (id, username, password, role, status)；
7. 创建 types/file.ts: FileItemModel (nodeId, path, type, content)。

### T-02-12 API 接口封装 (api/ 目录)

依赖: T-02-03, T-02-11
完成标准: 所有后端接口都有对应的前端 API 封装函数。

执行步骤:
1. 创建 api/auth.ts: 封装登录接口；
2. 创建 api/node.ts: 封装节点列表、详情、创建、编辑、删除接口；
3. 创建 api/version.ts: 封装版本上传、列表、详情、删除、下载接口；
4. 创建 api/project.ts: 封装项目 CRUD 接口；
5. 创建 api/deploy.ts: 封装部署发布、记录列表、回滚接口；
6. 创建 api/process.ts: 封装进程启停接口；
7. 创建 api/log.ts: 封装日志查看接口 (控制台 + 文件日志)；
8. 创建 api/monitor.ts: 封装进程监控接口；
9. 创建 api/alarm.ts: 封装告警查询、配置接口；
10. 创建 api/system.ts: 封装用户管理、操作审计接口；
11. 创建 api/file.ts: 封装文件管理接口 (日志查看、YML读取/保存、批量下载)。

### T-02-13 节点列表页 (NodeList.vue)

依赖: T-02-10, T-02-12
完成标准: 能正常展示节点列表、新增节点、编辑节点、删除节点。

执行步骤:
1. 使用 Ant Design Table 展示节点列表；
2. 列: 节点名称、IP、端口、状态 (在线绿色/离线红色)、JDK版本、最后心跳、操作；
3. 支持按状态筛选，顶部搜索框按名称/IP搜索；
4. 操作列: 查看、编辑、删除 (删除前检查是否有项目绑定)；
5. 每 30 秒自动刷新列表，状态列使用 StatusBadge 组件。

### T-02-14 新增/编辑节点页 (NodeCreate.vue)

依赖: T-02-13
完成标准: 能正常新增和编辑节点。

执行步骤:
1. 使用 Ant Design Form 创建表单；
2. 字段: 节点名称 (必填)、IP地址 (必填)、端口 (默认2123)、Token (必填)；
3. 编辑模式时回显已有数据；
4. 提交后刷新节点列表。

### T-02-15 版本列表页 (VersionList.vue)

依赖: T-02-12
完成标准: 能展示版本列表、上传Jar包、查看详情、下载、删除。

执行步骤:
1. 表格展示: 项目名、Jar包名、版本号、文件大小、备注、上传时间；
2. 支持按项目筛选 (下拉框选择项目)；
3. 操作列: 查看详情、下载Jar包、删除。

### T-02-16 上传Jar包页 (VersionUpload.vue)

依赖: T-02-15
完成标准: 能正常上传Jar包。

执行步骤:
1. 使用 Ant Design Upload 组件；
2. 选择项目 (下拉框)、选择本地Jar文件 (仅允许.jar)、填写备注；
3. 上传后自动刷新列表显示新版本。

### T-02-17 项目列表页 (ProjectList.vue)

依赖: T-02-12
完成标准: 能展示项目列表、创建项目、编辑项目、删除项目。

执行步骤:
1. 使用卡片式布局展示项目；
2. 每个卡片显示: 项目名称、绑定节点数 (标签形式)、运行状态、最后部署时间；
3. 点击卡片进入项目详情页；
4. 支持创建新项目、编辑项目、删除项目。

### T-02-18 创建/编辑项目页 (ProjectCreate.vue)

依赖: T-02-17
完成标准: 能正常创建和编辑项目。

执行步骤:
1. 表单字段:
   - 项目名称 (必填，唯一校验)
   - 节点选择 (多选下拉框，从节点列表获取选项)
   - 启动脚本 (CodeEditor，支持模板变量提示)
   - 停止脚本 (CodeEditor)
   - JVM参数 (文本框)
   - 环境变量 (Key-Value 编辑)
2. 编辑模式时回显已有数据。

### T-02-19 项目详情页 (ProjectDetail.vue)

依赖: T-02-17
完成标准: 能查看项目详情、部署记录、执行部署操作。

执行步骤:
1. 信息卡片: 项目名称、JVM参数、启停脚本、环境变量；
2. 部署记录列表 (调用部署接口)；
3. 操作区: 选择版本发布、节点启停按钮；
4. 文件管理入口 (跳转至文件管理页，传入项目ID)。

### T-02-20 部署记录页 (DeployList.vue)

依赖: T-02-12
完成标准: 能查看部署记录列表。

执行步骤:
1. 表格展示: 项目名、Jar包、节点、状态 (成功/失败/进行中)、部署时间；
2. 支持按项目、状态筛选；
3. 操作列: 查看详情、回滚 (仅支持已完成的部署)。

### T-02-21 部署详情与回滚页 (DeployDetail.vue)

依赖: T-02-20
完成标准: 能查看部署详情、执行回滚。

执行步骤:
1. 展示部署详细信息: 项目、版本、节点、状态、日志；
2. 使用日志查看组件展示部署日志；
3. 提供一键回滚按钮 (调用回滚接口)。

### T-02-22 控制台页面 (ProcessConsole.vue)

依赖: T-02-10, T-02-12
完成标准: 能查看指定项目指定节点的实时控制台输出。

执行步骤:
1. 顶部选择项目、选择节点；
2. 使用 xterm.js 终端组件展示实时控制台输出；
3. 通过 WebSocket 连接 Server 的 /ws/console/{projectId}/{nodeId}；
4. 支持查看多个项目的控制台输出切换。

### T-02-23 日志查看页 (ProcessLog.vue)

依赖: T-02-10, T-02-12
完成标准: 能查看指定项目指定节点的运行日志文件。

执行步骤:
1. 选择项目、节点后，分页读取服务器日志文件；
2. 使用 LogViewer 组件，带行号显示；
3. 支持日志关键字搜索和高亮；
4. 支持日志文件选择 (如 application.log、error.log)。

### T-02-24 监控面板页 (ProcessMonitor.vue)

依赖: T-02-10, T-02-12
完成标准: 能查看指定项目指定节点的 CPU/内存使用趋势图。

执行步骤:
1. 使用 ECharts 展示 CPU、内存使用趋势图；
2. 通过 WebSocket 实时获取数据 (每 10 秒更新)；
3. 支持选择不同项目/节点切换查看。

### T-02-25 告警历史页 (AlarmList.vue)

依赖: T-02-12
完成标准: 能查看告警历史记录。

执行步骤:
1. 表格展示: 时间、项目/节点、告警类型、发送结果；
2. 支持按项目、类型、时间筛选。

### T-02-26 告警配置页 (AlarmConfig.vue)

依赖: T-02-12
完成标准: 能配置 SMTP 服务器信息和告警接收人。

执行步骤:
1. 表单字段:
   - SMTP 服务器地址、端口、是否SSL
   - 发件邮箱、密码
   - 告警接收人 (多邮箱，逗号分隔)
   - 启用/禁用开关
2. 保存后立即发送测试邮件验证配置。

### T-02-27 用户列表页 (UserList.vue)

依赖: T-02-12
完成标准: 能查看用户列表。

执行步骤:
1. 表格展示: 用户名、角色、状态、操作；
2. 操作列: 编辑、删除。

### T-02-28 新增/编辑用户页 (UserCreate.vue)

依赖: T-02-27
完成标准: 能正常新增和编辑用户。

执行步骤:
1. 表单字段: 用户名、密码、角色选择 (admin/operator)；
2. 编辑模式时不显示密码输入 (可选修改)。

### T-02-29 操作审计页 (LogList.vue)

依赖: T-02-12
完成标准: 能查看操作审计日志。

执行步骤:
1. 表格展示: 操作时间、模块、操作人、操作内容、来源IP；
2. 支持按模块、时间范围筛选。

### T-02-30 仪表盘页面 (Dashboard.vue)

依赖: T-02-12
完成标准: 首页展示关键运维数据概览。

执行步骤:
1. 展示: 节点总数/在线数、项目总数/运行数、今日部署次数、未处理告警数；
2. 使用 ECharts 展示最近7天部署趋势图和节点在线趋势图；
3. 快速操作入口 (新建项目、上传版本、查看告警)。

### T-02-31 文件日志查看页 (FileLogView.vue) (M10)

依赖: T-02-10, T-02-12
完成标准: 能查看服务器上任意路径下的日志文件内容。

执行步骤:
1. 选择节点、输入文件路径 (支持通配符如 /data/logs/*.log)；
2. 使用 LogViewer 组件展示日志内容 (带行号)；
3. 支持关键字搜索、高亮显示；
4. 支持自动刷新 (每5秒自动轮询新日志)。

### T-02-32 YML配置编辑页 (FileConfigEdit.vue) (M10)

依赖: T-02-10, T-02-12
完成标准: 能查看并编辑服务器上YML配置文件。

执行步骤:
1. 选择节点、输入YML文件路径 (如 /data/app/application.yml)；
2. 使用 CodeEditor 组件 (mode=yaml) 加载并展示YML内容；
3. 编辑后点击保存，后端写入服务器；
4. 保存前显示变更预览 (Diff 视图，使用 react-diff-viewer 或类似库)；
5. 保存失败时显示错误信息。

### T-02-33 批量下载页 (FileDownload.vue) (M10)

依赖: T-02-10, T-02-12
完成标准: 能勾选多个文件并批量下载为 Zip。

执行步骤:
1. 选择节点，勾选需要下载的文件 (日志、YML、Jar)；
2. 支持跨节点批量选择 (Table 多选)；
3. 点击"批量下载"按钮，调用后端接口；
4. 后端返回 Zip 流，前端触发浏览器下载；
5. 显示下载进度提示。

### T-02-34 403 页面

依赖: T-02-06
完成标准: 访问无权限路由时显示 403 页面。

执行步骤:
1. 创建 views/error/403.vue；
2. 显示"没有权限访问此页面"；
3. 提供返回首页按钮。

### T-02-35 全局样式 (assets/styles/)

依赖: 无
完成标准: 全局 CSS 样式生效。

执行步骤:
1. 创建全局 CSS 变量 (颜色、间距、圆角)；
2. 使用 CSS 变量实现主题切换 (亮色/暗色)；
3. 统一按钮、输入框、表格等基础样式。

---

## 第三阶段: 前后端联调与优化 (T-02-36 ~ T-02-38)

### T-02-36 前后端联调 (基础模块)

依赖: T-02-13, T-02-14, T-02-16, T-02-17, T-02-18, T-02-07, T-02-08
完成标准: 能完整走完节点创建、版本上传、项目创建全流程。

执行步骤:
1. 依次启动 Server 和 Agent；
2. 手动创建节点 → 手动上传版本包 → 手动创建项目 (绑定节点)；
3. 验证前后端接口联调结果；
4. 修复发现的问题。

### T-02-37 前后端联调 (核心部署与文件管理)

依赖: T-02-19, T-02-20, T-02-21, T-02-22, T-02-23, T-02-31, T-02-32, T-02-33
完成标准: 能完整走完版本发布 → 查看日志 → 编辑YML → 批量下载全流程。

执行步骤:
1. 上传版本包 → 发布到项目 → 查看部署记录 → 回滚；
2. 查看日志文件 → 编辑YML配置 → 保存配置 → 批量下载文件；
3. 验证前后端接口联调结果；
4. 修复发现的问题。

### T-02-38 Nginx 配置与部署验证

依赖: T-02-36, T-02-37
完成标准: 前端构建为静态文件，通过 Nginx 反向代理访问，前后端联调通过。

执行步骤:
1. 执行 npm run build 验证构建成功；
2. 配置 Nginx 反向代理: location /api 代理到 Server 8081 端口，其他请求返回静态文件；
3. 验证: 通过 Nginx 访问前端页面，能正常调用后端接口。

---

## 任务依赖关系汇总

T-02-01 (初始化项目) 是所有任务的起点。
T-02-02 ~ T-02-06 并行依赖于 T-02-01。
T-02-07 ~ T-02-10 并行依赖于 T-02-03/T-02-05/T-02-06。
T-02-11 ~ T-02-12 并行依赖于 T-02-01/T-02-03/T-02-04。
T-02-13 ~ T-02-35 并行或按页面依赖顺序执行。
T-02-36 ~ T-02-38 串联依赖于以上各功能页面。

---

*本文档为第三层最小任务分层 (前端篇)，与Server与Agent篇配合构成完整开发计划。审核通过后开始逐个执行编码任务。*
