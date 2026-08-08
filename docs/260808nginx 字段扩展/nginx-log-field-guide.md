# Nginx 访问日志字段扩展指南（生产环境）

> 适用对象：EasyOps Nginx 流量监控的生产接入方。
> 目的：说明当前日志规范到底打了哪些字段、EasyOps 实际接住了哪些、生产环境还应补充哪些字段，以及扩展字段时需要注意的解析兼容性。
> 配套阅读：EasyOps 源码 `backend/agent/src/main/java/com/ops/agent/traffic/NginxLogParser.java`、`backend/common/.../model/NginxMinuteStatModel.java`。

---

## 1. 现状：你打的 13 个字段，EasyOps 到底用了哪些

你（之前）拟定的 `log_format main` 实际输出 **13 个引号字段**，但 EasyOps agent 的解析器只抽取了其中一部分。

| # | Nginx 变量 | 含义 | EasyOps 是否采集 | 当前用途 |
|---|---|---|---|---|
| 0 | `$time_iso8601` | 请求时间（ISO8601） | ✅ | 分钟分桶 |
| 1 | `$remote_addr` | 直连客户端 IP | ⚠️ 仅备用 | 当 XFF 为空时作为 clientIp |
| 2 | `$http_x_forwarded_for` | 真实客户端 IP（代理链） | ⚠️ 仅用于解析 IP | 解析出 clientIp（取第一跳），**未单独存储** |
| 3 | `$remote_user` | HTTP 基础认证用户 | ❌ 丢弃 | — |
| 4 | `$request` | 请求行（METHOD URI PROTO） | ✅ | 拆出 method + uri |
| 5 | `$status` | 客户端收到的状态码 | ✅ | 2xx/4xx/5xx 计数 |
| 6 | `$body_bytes_sent` | 响应体字节数 | ❌ 丢弃 | 可算带宽 |
| 7 | `$http_referer` | 来源页 | ❌ 丢弃 | 流量来源分析 |
| 8 | `$http_user_agent` | 客户端 UA | ❌ 丢弃 | 设备/爬虫识别（UA 维度） |
| 9 | `$upstream_addr` | 后端地址 | ❌ 丢弃 | 定位坏节点 |
| 10 | `$request_time` | 总耗时（秒） | ✅ | 平均/慢请求判定 |
| 11 | `$upstream_response_time` | 后端耗时（秒） | ✅ | 后端耗时 |
| 12 | `$http_host` | 访问的 Host | ❌ 丢弃 | 虚拟主机区分 |

**结论**：页面"只显示几个字段"，不是 nginx 只能打这几个，而是 EasyOps 解析器（`NginxLogParser.parse`）只抽取了 6 类（时间、IP、方法+URI、状态码、request_time、upstream_time），其余 7 个字段虽然在日志里存在，但被解析器直接丢弃、也未进入聚合表。UA / Referer / 字节数 / upstream_addr / 真实 XFF 这些**你现在就能在生产日志里看到，但 EasyOps 目前不存**。

---

## 2. 为什么显示维度这么少（架构约束）

EasyOps 采用**分钟级预聚合**存储（`nginx_minute_stat`），唯一键为 `(source_id, bucket_time, client_ip, uri, method)`。所有统计都在这张表上 `GROUP BY`，因此：

- 维度只能落在 `client_ip / uri / method / 状态码 / 时间` 这几列 —— 这就是页面只有 IP / 接口 / 交叉 / 方法 四个维度的根本原因。
- UA、Referer、Host 等若要变成可统计维度，必须把它们**加进唯一键或单独维度列**，会显著放大行数（基数爆炸），需谨慎（见第 5 节）。
- 耗时只存了 `sum` 和 `max`，未存原始样本，**无法反推瞬时值或百分位**（p50/p95/p99）。

---

## 3. 建议在生产环境新增的字段

以下 nginx 变量目前日志里**没有**，但对运维排查价值高，建议补上：

| Nginx 变量 | 含义 | 能解决的痛点 |
|---|---|---|
| `$upstream_status` | 后端真实状态码 | 识别"后端正常但 nginx 返回 502/504"的代理错误 |
| `$upstream_connect_time` | 与后端建连耗时 | 定位"慢在 TCP 建连" |
| `$upstream_header_time` | 后端首字节时间(TTFB) | 区分"慢在 nginx 还是后端处理" |
| `$request_length` | 请求总大小（含头） | 入流量 / 大请求体排查 |
| `$upstream_cache_status` | 缓存命中状态 HIT/MISS/EXPIRED/BYPASS | 缓存命中率分析 |
| `$scheme` | http / https | 协议分布 |
| `$ssl_protocol` `$ssl_cipher` | TLS 协议/套件（仅 HTTPS 站点） | 不安全协议排查 |
| `$msec` | 毫秒级时间戳 | 比 `$time_iso8601` 更利于排序/对齐 |

---

## 4. 推荐的完整 log_format（生产可直接贴）

### 4.1 方案 A：沿用引号格式（兼容现有解析器，新字段必须追加在末尾）

> ⚠️ 现有解析器按**字段位置**取值。新增字段只能**追加在最后（第 13 位之后）**，绝不能插在中间或调整顺序，否则位置错位、解析全乱。

```nginx
log_format easyops_ext
    '"$time_iso8601"'      # 0  时间
    '"$remote_addr"'       # 1  直连IP
    '"$http_x_forwarded_for"' # 2  XFF
    '"$remote_user"'       # 3  认证用户
    '"$request"'           # 4  请求行
    '"$status"'            # 5  状态码
    '"$body_bytes_sent"'   # 6  响应字节
    '"$http_referer"'      # 7  来源
    '"$http_user_agent"'   # 8  UA
    '"$upstream_addr"'     # 9  后端地址
    '"$request_time"'      # 10 总耗时
    '"$upstream_response_time"' # 11 后端耗时
    '"$http_host"'         # 12 Host
    # ===== 以下为新增（追加在末尾，顺序与解析器约定一致）=====
    '"$upstream_status"'        # 13 后端状态码
    '"$upstream_connect_time"'  # 14 建连耗时
    '"$upstream_header_time"'   # 15 首字节耗时
    '"$request_length"'         # 16 请求大小
    '"$upstream_cache_status"'  # 17 缓存状态
    '"$scheme"'                 # 18 协议
    '"$ssl_protocol"'           # 19 TLS协议(HTTPS)
    '"$ssl_cipher"'             # 20 TLS套件(HTTPS)
    '"$msec"'                   # 21 毫秒时间戳
;

access_log /var/log/nginx/access.log easyops_ext;
```

### 4.2 方案 B（强烈推荐）：改用 JSON 格式

字段带名字，新增/调整字段**不会破坏解析**，也自解释、便于对接其它系统。代价是 EasyOps 解析器需从"引号位置解析"升级为"JSON 解析"（见第 5 节）。

```nginx
log_format easyops_json escape=json
    '{'
    '"time":"$time_iso8601",'
    '"remote_addr":"$remote_addr",'
    '"xff":"$http_x_forwarded_for",'
    '"remote_user":"$remote_user",'
    '"method":"$request_method",'
    '"uri":"$request_uri",'
    '"args":"$args",'
    '"status":$status,'
    '"body_bytes":$body_bytes_sent,'
    '"referer":"$http_referer",'
    '"user_agent":"$http_user_agent",'
    '"upstream_addr":"$upstream_addr",'
    '"request_time":$request_time,'
    '"upstream_time":"$upstream_response_time",'
    '"host":"$http_host",'
    '"upstream_status":"$upstream_status",'
    '"upstream_connect_time":$upstream_connect_time,'
    '"upstream_header_time":$upstream_header_time,'
    '"request_length":$request_length,'
    '"cache_status":"$upstream_cache_status",'
    '"scheme":"$scheme",'
    '"ssl_protocol":"$ssl_protocol",'
    '"ssl_cipher":"$ssl_cipher",'
    '"msec":$msec'
    '}';

access_log /var/log/nginx/access.log easyops_json;
```

> 注意 `$request_method` / `$request_uri` 是 nginx 原生变量，比从 `$request` 字符串里拆 method+uri 更可靠（尤其带空格的 URI）。

---

## 5. EasyOps 侧要改什么，才能真的用上这些字段

只改 nginx 不够——如果 EasyOps 不解析/不存储，新增字段依旧看不到。按价值排序：

| 字段 | 价值 | 需要的代码改动 | 工作量 |
|---|---|---|---|
| `user_agent` | 高（UA 维度） | parser 抽取 + `nginx_minute_stat` 加 `user_agent` 列或独立 UA 维度表 + mapper/聚合/前端 | 中（UA 基数大，建议独立维度表 + 采样） |
| `upstream_status` | 高 | parser 抽取 + 聚合表加 `upstream_5xx` 等列 | 小 |
| `upstream_connect_time` / `header_time` | 高 | parser 抽取 + 聚合表加 `sum_upstream_connect_ms` 等 | 小 |
| `body_bytes_sent` | 中 | parser 抽取 + 聚合表加 `sum_body_bytes` | 小 |
| `referer` | 中 | 同 UA，独立维度表 + 采样 | 中 |
| `upstream_addr` | 中 | parser 抽取 + 维度列 | 中（基数随后端数） |
| `request_length` | 低 | parser 抽取 + 聚合表加 `sum_request_length` | 小 |
| `cache_status` | 中 | parser 抽取 + 维度列（HIT/MISS…） | 小 |
| `scheme` / `ssl_*` | 低 | parser 抽取 + 维度列 | 小 |

**通用改动清单**（以新增一列为例）：
1. `NginxLogParser.ParsedLine` 增加字段；`parse()` 增加取值（引号格式按新下标，JSON 格式按 key）。
2. `NginxMinuteStatModel` / 聚合表 DDL 增加列；agent 端 `MinuteBucketAggregator` 累加；`NginxMinuteStatMapper.xml` 的 `insertStat`/`incrementStat` 同步。
3. 维度字段（UA/Referer）不要直接塞进现有唯一键（行数爆炸），应建独立维度表并按需采样（如只保留 TOP-N 或慢请求样本）。
4. 前端 `NginxTrafficView.vue` 增加对应维度 Tab / 列。
5. 若改用 JSON 格式：解析器整体改为 JSON，并兼容老引号格式（自动探测首字符 `{`）。

---

## 6. 耗时维度的扩展路线（对应"为什么只有平均耗时"）

当前聚合表只有 `sum_request_time_ms` 和 `max_request_time_ms`，因此：

- **某时间段耗时（如 1 分钟）**：数据已在库，只需在 `trendByMinute` 增加 `avgMs`/`maxMs` 列并在趋势图叠加耗时线，**不改表即可**。
- **按耗时排名**：rank 查询增加排序参数（按平均耗时↓ / 按最慢单次↓），数据已具备，**不改表即可**。
- **瞬时耗时 / 百分位(p95/p99)**：分钟聚合已抹平单请求细节，**无法实现**。必须新增一张**原始请求采样表**（例如仅保留 > 阈值 或 按比率采样，配合 TTL 清理），存储 `client_ip, uri, method, request_time, upstream_time, timestamp`，才能支撑真瞬时值与百分位。这是架构级改动，需单独排期。

---

## 7. 生产落地 Checklist

- [ ] 选方案 A（引号，新字段追加末尾）或方案 B（JSON，推荐）。
- [ ] 在灰度一台 nginx 上先切换 `log_format` + `access_log`，确认日志行格式正确。
- [ ] 若选 JSON：同步升级 EasyOps agent 解析器（JSON + 兼容引号）。
- [ ] 评估需要哪些字段真的进 EasyOps 统计（避免基数爆炸），排期 parser/model/mapper/前端改动。
- [ ] 验证：制造覆盖各场景的访问，确认新字段在 EasyOps 页面/接口可见。
- [ ] 全量推广前确认日志轮转（logrotate）与磁盘占用（字段越多行越大）。
