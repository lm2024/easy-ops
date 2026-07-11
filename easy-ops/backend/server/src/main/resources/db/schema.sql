-- 注册 MySQL 兼容函数 FIND_IN_SET (H2 不原生支持)
CREATE ALIAS IF NOT EXISTS FIND_IN_SET FOR "com.ops.server.util.H2CompatFunctions.findInSet";

-- 节点信息表
CREATE TABLE IF NOT EXISTS node_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) UNIQUE NOT NULL,
    ip VARCHAR(50) NOT NULL,
    port INT DEFAULT 2123,
    token VARCHAR(64) NOT NULL,
    status TINYINT DEFAULT 0,
    os_info VARCHAR(200),
    java_version VARCHAR(50),
    last_heartbeat BIGINT,
    create_time BIGINT,
    update_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_node_status ON node_info(status);
CREATE INDEX IF NOT EXISTS idx_node_ip ON node_info(ip);

-- 新增列: 标签、系统硬件信息 (兼容已有数据库)
ALTER TABLE node_info ADD COLUMN IF NOT EXISTS tags VARCHAR(500) DEFAULT '';
ALTER TABLE node_info ADD COLUMN IF NOT EXISTS cpu_cores INT DEFAULT 0;
ALTER TABLE node_info ADD COLUMN IF NOT EXISTS total_memory_mb INT DEFAULT 0;
ALTER TABLE node_info ADD COLUMN IF NOT EXISTS total_disk_mb BIGINT DEFAULT 0;
ALTER TABLE node_info ADD COLUMN IF NOT EXISTS os_arch VARCHAR(50) DEFAULT '';
ALTER TABLE node_info ADD COLUMN IF NOT EXISTS agent_version VARCHAR(50) DEFAULT '';

-- 项目管理表
CREATE TABLE IF NOT EXISTS project_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) UNIQUE NOT NULL,
    node_ids VARCHAR(1000),
    start_script TEXT,
    stop_script TEXT,
    restart_script TEXT,
    jvm_opts TEXT,
    env_vars TEXT,
    status TINYINT DEFAULT 1,
    create_time BIGINT,
    update_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_project_status ON project_info(status);
ALTER TABLE project_info ADD COLUMN IF NOT EXISTS jar_name VARCHAR(200) DEFAULT '';
ALTER TABLE project_info ADD COLUMN IF NOT EXISTS deploy_dir VARCHAR(500) DEFAULT '';
ALTER TABLE project_info ADD COLUMN IF NOT EXISTS frontend_deploy_dir VARCHAR(500) DEFAULT '';

-- 版本包类型（jar / frontend）
ALTER TABLE version_package ADD COLUMN IF NOT EXISTS package_type VARCHAR(20) DEFAULT 'jar';

-- 版本包表
CREATE TABLE IF NOT EXISTS version_package (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    jar_name VARCHAR(200) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT,
    version VARCHAR(50) NOT NULL,
    sha256 VARCHAR(64),
    remark VARCHAR(500),
    create_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_version_project ON version_package(project_id);
CREATE INDEX IF NOT EXISTS idx_version_project_ver ON version_package(project_id, version);

-- 部署记录表
CREATE TABLE IF NOT EXISTS deploy_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    status TINYINT DEFAULT 0,
    jar_name VARCHAR(200),
    log TEXT,
    start_time BIGINT,
    end_time BIGINT,
    create_time BIGINT
);
ALTER TABLE deploy_record ADD COLUMN IF NOT EXISTS schedule_time BIGINT DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_deploy_project ON deploy_record(project_id);
CREATE INDEX IF NOT EXISTS idx_deploy_status ON deploy_record(status);
CREATE INDEX IF NOT EXISTS idx_deploy_schedule ON deploy_record(status, schedule_time);

-- 告警记录表
CREATE TABLE IF NOT EXISTS alarm_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT,
    node_id BIGINT,
    type VARCHAR(50),
    content TEXT,
    send_result TINYINT DEFAULT 0,
    send_time BIGINT,
    create_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_alarm_type ON alarm_record(type);
CREATE INDEX IF NOT EXISTS idx_alarm_create ON alarm_record(create_time);

-- 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(128) NOT NULL,
    role VARCHAR(20) DEFAULT 'operator',
    status TINYINT DEFAULT 1,
    create_time BIGINT,
    update_time BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_name ON sys_user(username);

-- 操作审计表
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    module VARCHAR(50),
    action VARCHAR(100),
    content TEXT,
    ip VARCHAR(50),
    create_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_log_user ON operation_log(user_id);
CREATE INDEX IF NOT EXISTS idx_log_module ON operation_log(module);
CREATE INDEX IF NOT EXISTS idx_log_create ON operation_log(create_time);

-- 文件访问审计表
CREATE TABLE IF NOT EXISTS file_access_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    file_type VARCHAR(20),
    file_path VARCHAR(500),
    action VARCHAR(20),
    content_summary VARCHAR(500),
    ip VARCHAR(50),
    create_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_file_node ON file_access_log(node_id);
CREATE INDEX IF NOT EXISTS idx_file_type ON file_access_log(file_type);
CREATE INDEX IF NOT EXISTS idx_file_create ON file_access_log(create_time);

-- 告警配置表
CREATE TABLE IF NOT EXISTS alarm_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    enabled TINYINT DEFAULT 1,
    smtp_host VARCHAR(200),
    smtp_port INT,
    smtp_ssl TINYINT DEFAULT 0,
    sender_email VARCHAR(200),
    sender_password VARCHAR(200),
    receivers VARCHAR(1000),
    update_time BIGINT
);

-- 系统配置表
CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT,
    description VARCHAR(500),
    update_time BIGINT
);

-- 初始化默认管理员 (密码: admin123) — 使用 MERGE INTO 避免重复
MERGE INTO sys_user (id, username, password, role, status, create_time, update_time)
KEY (id)
VALUES (1, 'admin', '$2a$10$NatUJE6J35F/fGMeUFFQ/Op7rJeZsK3c9kUD4AwoWXBDphKFHXxri', 'admin', 1, 1781833996000, 1781833996000);

-- 初始化默认告警配置 — 使用 MERGE INTO 避免重复
MERGE INTO alarm_config (id, enabled, smtp_host, smtp_port, smtp_ssl, receivers, update_time)
KEY (id)
VALUES (1, 0, '', 465, 0, '', 1781833996000);

-- 分布式调度锁表 (SEC-001)
CREATE TABLE IF NOT EXISTS scheduler_lock (
    lock_name VARCHAR(100) PRIMARY KEY,
    instance_id VARCHAR(200) NOT NULL,
    locked_at BIGINT NOT NULL,
    expire_at BIGINT NOT NULL
);

-- 用户-项目关系表 (SEC-003/SEC-004)
CREATE TABLE IF NOT EXISTS user_project_relation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    create_time BIGINT,
    UNIQUE INDEX uk_user_project (user_id, project_id),
    INDEX idx_project_id (project_id)
);

-- ========== 运维能力提升 2025-06-25 ==========

ALTER TABLE project_info ADD COLUMN IF NOT EXISTS config_dir VARCHAR(500) DEFAULT '';
ALTER TABLE project_info ADD COLUMN IF NOT EXISTS monitor_interval_sec INT DEFAULT 60;
ALTER TABLE project_info ADD COLUMN IF NOT EXISTS health_check_enabled TINYINT DEFAULT 1;
ALTER TABLE project_info ADD COLUMN IF NOT EXISTS health_check_port INT DEFAULT 8080;
ALTER TABLE project_info ADD COLUMN IF NOT EXISTS health_check_path VARCHAR(200) DEFAULT '/hello';
ALTER TABLE project_info ADD COLUMN IF NOT EXISTS health_check_keyword VARCHAR(200) DEFAULT 'Hello,DEPLOYED';

-- M1 配置文件管理
CREATE TABLE IF NOT EXISTS project_config_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    relative_path VARCHAR(500) NOT NULL,
    is_primary TINYINT DEFAULT 0,
    remark VARCHAR(500) DEFAULT '',
    create_time BIGINT,
    update_time BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cfg_project_path ON project_config_file(project_id, relative_path);
CREATE INDEX IF NOT EXISTS idx_cfg_project ON project_config_file(project_id);

CREATE TABLE IF NOT EXISTS node_config_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    config_file_id BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    content_size INT DEFAULT 0,
    sync_status TINYINT DEFAULT 0,
    last_sync_time BIGINT,
    update_time BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_node_cfg_file ON node_config_snapshot(node_id, config_file_id);
CREATE INDEX IF NOT EXISTS idx_snap_project ON node_config_snapshot(project_id, sync_status);

CREATE TABLE IF NOT EXISTS config_distribute_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    config_file_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    target_node_ids VARCHAR(2000) NOT NULL,
    distribute_type VARCHAR(20) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    restart_after TINYINT DEFAULT 0,
    status TINYINT DEFAULT 0,
    result_detail TEXT,
    create_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_dist_project_time ON config_distribute_record(project_id, create_time);

-- M2 日志管理
CREATE TABLE IF NOT EXISTS project_log_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    log_dir VARCHAR(500) NOT NULL,
    main_log_file VARCHAR(500) NOT NULL,
    rolling_pattern VARCHAR(200) DEFAULT '',
    timestamp_regex VARCHAR(500) NOT NULL,
    timestamp_format VARCHAR(100) NOT NULL,
    max_line_length INT DEFAULT 4096,
    create_time BIGINT,
    update_time BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_log_project ON project_log_profile(project_id);

-- M3 监控与 AI
CREATE TABLE IF NOT EXISTS project_health_probe (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    enabled TINYINT DEFAULT 1,
    method VARCHAR(10) DEFAULT 'GET',
    url VARCHAR(500) NOT NULL,
    headers TEXT,
    body TEXT,
    expected_status INT DEFAULT 200,
    body_contains VARCHAR(500) DEFAULT '',
    timeout_ms INT DEFAULT 3000,
    create_time BIGINT,
    update_time BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_probe_project ON project_health_probe(project_id);

CREATE TABLE IF NOT EXISTS monitor_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    health_status VARCHAR(20) NOT NULL,
    health_detail VARCHAR(500) DEFAULT '',
    process_status VARCHAR(20) NOT NULL,
    process_pid INT,
    cpu_percent DECIMAL(5,2),
    memory_mb INT,
    heap_used_mb INT,
    heap_max_mb INT,
    gc_count INT,
    gc_time_ms INT,
    response_ms INT,
    host_cpu_percent DECIMAL(5,2),
    host_memory_percent INT,
    disk_usage_percent INT,
    extra_json TEXT,
    collect_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_mon_proj_node_time ON monitor_snapshot(project_id, node_id, collect_time);
CREATE INDEX IF NOT EXISTS idx_mon_collect ON monitor_snapshot(collect_time);

CREATE TABLE IF NOT EXISTS ai_diagnosis_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    node_id BIGINT,
    trigger_type VARCHAR(30) NOT NULL,
    question TEXT,
    context_summary TEXT,
    diagnosis TEXT NOT NULL,
    severity VARCHAR(20) DEFAULT 'INFO',
    saved_to_kb TINYINT DEFAULT 0,
    kb_document_id BIGINT,
    operator_id BIGINT,
    token_used INT,
    create_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_ai_proj_time ON ai_diagnosis_record(project_id, create_time);

-- M4 知识库
CREATE TABLE IF NOT EXISTS kb_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT DEFAULT 0,
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(50) DEFAULT '',
    sort_order INT DEFAULT 0,
    project_id BIGINT,
    create_time BIGINT,
    update_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_kb_cat_parent ON kb_category(parent_id);

CREATE TABLE IF NOT EXISTS kb_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    title VARCHAR(300) NOT NULL,
    summary VARCHAR(500) DEFAULT '',
    content LONGTEXT NOT NULL,
    content_size INT DEFAULT 0,
    source_type VARCHAR(30) DEFAULT 'MANUAL',
    source_id BIGINT,
    project_id BIGINT,
    author_id BIGINT NOT NULL,
    last_editor_id BIGINT,
    version_no INT DEFAULT 1,
    status TINYINT DEFAULT 1,
    view_count INT DEFAULT 0,
    create_time BIGINT,
    update_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_kb_doc_cat ON kb_document(category_id);
CREATE INDEX IF NOT EXISTS idx_kb_doc_update ON kb_document(update_time);

CREATE TABLE IF NOT EXISTS kb_document_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    title VARCHAR(300) NOT NULL,
    content LONGTEXT NOT NULL,
    editor_id BIGINT NOT NULL,
    change_note VARCHAR(500) DEFAULT '',
    create_time BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_doc_ver ON kb_document_version(document_id, version_no);

CREATE TABLE IF NOT EXISTS kb_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT 0,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    rating TINYINT,
    create_time BIGINT,
    update_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_kb_cmt_doc ON kb_comment(document_id, create_time);

CREATE TABLE IF NOT EXISTS kb_document_lock (
    document_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(50) NOT NULL,
    lock_time BIGINT NOT NULL,
    expire_time BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_kb_lock_expire ON kb_document_lock(expire_time);

CREATE TABLE IF NOT EXISTS kb_image (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size INT NOT NULL,
    mime_type VARCHAR(50) NOT NULL,
    uploader_id BIGINT NOT NULL,
    create_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_kb_img_doc ON kb_image(document_id);

-- M5 自愈与通知
CREATE TABLE IF NOT EXISTS self_heal_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    enabled TINYINT DEFAULT 1,
    max_retries INT DEFAULT 3,
    retry_interval_sec INT DEFAULT 30,
    check_interval_sec INT DEFAULT 30,
    circuit_breaker TINYINT DEFAULT 0,
    circuit_break_time BIGINT,
    notify_email TINYINT DEFAULT 1,
    notify_popup TINYINT DEFAULT 1,
    auto_ai_diagnose TINYINT DEFAULT 0,
    create_time BIGINT,
    update_time BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_heal_project ON self_heal_policy(project_id);

CREATE TABLE IF NOT EXISTS self_heal_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    retry_count INT,
    max_retries INT,
    detail TEXT,
    process_pid INT,
    create_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_heal_evt ON self_heal_event(project_id, node_id, create_time);

CREATE TABLE IF NOT EXISTS notification_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(30) NOT NULL,
    level VARCHAR(20) NOT NULL,
    title VARCHAR(300) NOT NULL,
    content TEXT NOT NULL,
    project_id BIGINT,
    node_id BIGINT,
    source_type VARCHAR(30),
    source_id BIGINT,
    require_ack TINYINT DEFAULT 0,
    broadcast TINYINT DEFAULT 1,
    create_time BIGINT,
    expire_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_notify_type_time ON notification_record(type, create_time);
CREATE INDEX IF NOT EXISTS idx_notify_expire ON notification_record(expire_time);

CREATE TABLE IF NOT EXISTS user_notification_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_status TINYINT DEFAULT 0,
    ack_status TINYINT DEFAULT 0,
    ack_time BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_notify ON user_notification_state(notification_id, user_id);
CREATE INDEX IF NOT EXISTS idx_user_notify_unread ON user_notification_state(user_id, read_status);

-- 初始化知识库默认分类
MERGE INTO kb_category (id, parent_id, name, icon, sort_order, create_time, update_time)
KEY (id)
VALUES (1, 0, '故障案例', 'alert', 1, 1781833996000, 1781833996000);
MERGE INTO kb_category (id, parent_id, name, icon, sort_order, create_time, update_time)
KEY (id)
VALUES (2, 0, '部署手册', 'rocket', 2, 1781833996000, 1781833996000);
MERGE INTO kb_category (id, parent_id, name, icon, sort_order, create_time, update_time)
KEY (id)
VALUES (3, 0, '配置说明', 'setting', 3, 1781833996000, 1781833996000);
MERGE INTO kb_category (id, parent_id, name, icon, sort_order, create_time, update_time)
KEY (id)
VALUES (4, 0, '常见问题 FAQ', 'question', 4, 1781833996000, 1781833996000);
MERGE INTO kb_category (id, parent_id, name, icon, sort_order, create_time, update_time)
KEY (id)
VALUES (5, 0, '开发交接', 'code', 5, 1781833996000, 1781833996000);

-- ========== 知识管理重新设计 2026-06-27 ==========

-- ALTER 旧表新增字段
ALTER TABLE kb_category ADD COLUMN IF NOT EXISTS color VARCHAR(20) DEFAULT '';
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS yjs_state BLOB DEFAULT NULL;
ALTER TABLE kb_comment ADD COLUMN IF NOT EXISTS reply_to_id BIGINT DEFAULT 0;
ALTER TABLE kb_comment ADD COLUMN IF NOT EXISTS mention_user_ids VARCHAR(500) DEFAULT '';
ALTER TABLE kb_comment ADD COLUMN IF NOT EXISTS likes INT DEFAULT 0;
ALTER TABLE kb_comment ADD COLUMN IF NOT EXISTS type VARCHAR(20) DEFAULT 'COMMENT';
ALTER TABLE kb_comment ADD COLUMN IF NOT EXISTS annotation_id VARCHAR(50) DEFAULT '';

-- CREATE 新表
CREATE TABLE IF NOT EXISTS kb_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) UNIQUE NOT NULL,
    color VARCHAR(20) DEFAULT '#722ED1',
    create_time BIGINT
);

CREATE TABLE IF NOT EXISTS kb_document_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    create_time BIGINT,
    UNIQUE INDEX uk_doc_tag (document_id, tag_id),
    INDEX idx_tag_id (tag_id)
);

CREATE TABLE IF NOT EXISTS kb_document_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    target_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    user_id BIGINT NOT NULL,
    permission_level VARCHAR(20) NOT NULL DEFAULT 'VIEW',
    create_time BIGINT,
    UNIQUE INDEX uk_perm_target_user (target_id, target_type, user_id),
    INDEX idx_perm_target (target_id, target_type)
);

CREATE TABLE IF NOT EXISTS kb_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) DEFAULT '',
    content LONGTEXT NOT NULL,
    icon VARCHAR(50) DEFAULT '',
    category VARCHAR(50) DEFAULT '',
    user_id BIGINT,
    is_system TINYINT DEFAULT 0,
    create_time BIGINT,
    update_time BIGINT
);

CREATE TABLE IF NOT EXISTS kb_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time BIGINT,
    UNIQUE INDEX uk_fav_doc_user (document_id, user_id)
);

CREATE TABLE IF NOT EXISTS kb_recent_access (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    access_type VARCHAR(20) NOT NULL DEFAULT 'VIEW',
    create_time BIGINT,
    INDEX idx_recent_user_time (user_id, create_time)
);

CREATE TABLE IF NOT EXISTS kb_share_link (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    token VARCHAR(64) UNIQUE NOT NULL,
    password VARCHAR(100) DEFAULT '',
    expire_time BIGINT DEFAULT 0,
    create_user_id BIGINT NOT NULL,
    create_time BIGINT,
    INDEX idx_share_token (token)
);

-- 预置模板初始数据
MERGE INTO kb_template (id, name, description, content, icon, category, user_id, is_system, create_time, update_time)
KEY (id)
VALUES (1, '故障报告', '记录故障处理全过程', '# 故障概述\n\n## 影响范围\n\n## 根因分析\n\n## 解决方案\n\n## 验证结果\n\n## 预防措施\n', 'alert', '故障案例', 1, 1, 1781833996000, 1781833996000);

MERGE INTO kb_template (id, name, description, content, icon, category, user_id, is_system, create_time, update_time)
KEY (id)
VALUES (2, '部署检查清单', '规范化部署流程', '# 部署检查清单\n\n## 部署前检查\n\n| 项目 | 状态 | 备注 |\n|------|------|------|\n| 服务运行 | ☐ | |\n| 端口可用 | ☐ | |\n| 配置文件 | ☐ | |\n\n## 部署步骤\n\n1. ...\n2. ...\n\n## 部署后验证\n\n| 项目 | 预期 | 实际 |\n|------|------|------|\n| 服务启动 | ✅ | |\n| 日志无异常 | ✅ | |\n\n## 回滚方案\n\n', 'rocket', '部署手册', 1, 1, 1781833996000, 1781833996000);

MERGE INTO kb_template (id, name, description, content, icon, category, user_id, is_system, create_time, update_time)
KEY (id)
VALUES (3, '交接文档', '开发→运维交接', '# 交接文档\n\n## 系统概述\n\n## 架构说明\n\n## 关键配置\n\n| 配置项 | 值 | 说明 |\n|--------|----|------|\n| JVM 参数 | -Xmx4g | |\n| 日志路径 | /var/log/app/ | |\n\n## 常见问题\n\n## 联系方式\n\n', 'code', '开发交接', 1, 1, 1781833996000, 1781833996000);

MERGE INTO kb_template (id, name, description, content, icon, category, user_id, is_system, create_time, update_time)
KEY (id)
VALUES (4, '配置变更记录', '配置变更审批记录', '# 配置变更记录\n\n## 变更原因\n\n## 变更内容\n\n| 配置项 | 变更前 | 变更后 |\n|--------|--------|--------|\n| Xmx | 2g | 4g |\n\n## 影响评估\n\n## 执行步骤\n\n## 验证结果\n\n', 'setting', '配置说明', 1, 1, 1781833996000, 1781833996000);

MERGE INTO kb_template (id, name, description, content, icon, category, user_id, is_system, create_time, update_time)
KEY (id)
VALUES (5, 'FAQ', '运维常见问题汇总', '# FAQ\n\n## Q1: 微服务启动失败？\n\n**A:** 常见原因：\n- OOM\n- 端口占用\n- 配置错误\n\n## Q2: 如何查看日志？\n\n**A:** ...\n\n> 相关文档：[链接]\n\n', 'question', '常见问题', 1, 1, 1781833996000, 1781833996000);
