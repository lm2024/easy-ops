CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100),
    password VARCHAR(200),
    role VARCHAR(50),
    status INT DEFAULT 1,
    create_time BIGINT,
    update_time BIGINT
);

CREATE TABLE IF NOT EXISTS node_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    ip VARCHAR(50),
    port INT,
    token VARCHAR(100),
    status INT DEFAULT 0,
    os_info VARCHAR(200),
    java_version VARCHAR(50),
    last_heartbeat BIGINT,
    tags VARCHAR(500),
    cpu_cores INT,
    total_memory_mb INT,
    total_disk_mb BIGINT,
    os_arch VARCHAR(20),
    agent_version VARCHAR(50),
    create_time BIGINT,
    update_time BIGINT
);

CREATE TABLE IF NOT EXISTS project_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    node_ids VARCHAR(500),
    start_script VARCHAR(500),
    stop_script VARCHAR(500),
    restart_script VARCHAR(500),
    jvm_opts VARCHAR(500),
    env_vars VARCHAR(500),
    status INT DEFAULT 1,
    jar_name VARCHAR(200),
    deploy_dir VARCHAR(500),
    frontend_deploy_dir VARCHAR(500),
    config_dir VARCHAR(500),
    monitor_interval_sec INT DEFAULT 60,
    health_check_enabled TINYINT DEFAULT 1,
    health_check_port INT DEFAULT 8080,
    health_check_path VARCHAR(200) DEFAULT '/hello',
    health_check_keyword VARCHAR(200) DEFAULT 'Hello,DEPLOYED',
    create_time BIGINT,
    update_time BIGINT
);

CREATE TABLE IF NOT EXISTS version_package (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT,
    jar_name VARCHAR(200),
    file_path VARCHAR(500),
    file_size BIGINT,
    version VARCHAR(100),
    sha256 VARCHAR(100),
    remark VARCHAR(500),
    package_type VARCHAR(20) DEFAULT 'jar',
    create_time BIGINT
);

CREATE TABLE IF NOT EXISTS deploy_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT,
    version_id BIGINT,
    node_id BIGINT,
    status INT DEFAULT 0,
    jar_name VARCHAR(200),
    log TEXT,
    start_time BIGINT,
    end_time BIGINT,
    create_time BIGINT,
    schedule_time BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    module VARCHAR(50),
    action VARCHAR(50),
    content VARCHAR(1000),
    ip VARCHAR(50),
    create_time BIGINT
);

CREATE TABLE IF NOT EXISTS alarm_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT,
    node_id BIGINT,
    type VARCHAR(50),
    content VARCHAR(1000),
    send_result INT,
    send_time BIGINT,
    create_time BIGINT
);

CREATE TABLE IF NOT EXISTS file_access_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    node_id BIGINT,
    file_type VARCHAR(50),
    file_path VARCHAR(500),
    action VARCHAR(50),
    content_summary VARCHAR(1000),
    ip VARCHAR(50),
    create_time BIGINT
);

CREATE TABLE IF NOT EXISTS user_project_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    create_time BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_project ON user_project_relation(user_id, project_id);
CREATE INDEX IF NOT EXISTS idx_project_id ON user_project_relation(project_id);

CREATE TABLE IF NOT EXISTS sys_config (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(500),
    description VARCHAR(500),
    update_time BIGINT
);

-- 以下表与主 schema.sql 保持同步（测试可能用到）

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

CREATE TABLE IF NOT EXISTS scheduler_lock (
    lock_name VARCHAR(100) PRIMARY KEY,
    instance_id VARCHAR(200) NOT NULL,
    locked_at BIGINT NOT NULL,
    expire_at BIGINT NOT NULL
);

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

CREATE TABLE IF NOT EXISTS user_notification_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_status TINYINT DEFAULT 0,
    ack_status TINYINT DEFAULT 0,
    ack_time BIGINT
);

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
