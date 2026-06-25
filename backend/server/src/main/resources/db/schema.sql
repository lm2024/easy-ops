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
