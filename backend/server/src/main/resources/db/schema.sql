-- 节点信息表
CREATE TABLE node_info (
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
CREATE INDEX idx_node_status ON node_info(status);
CREATE INDEX idx_node_ip ON node_info(ip);

-- 项目管理表
CREATE TABLE project_info (
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
CREATE INDEX idx_project_status ON project_info(status);

-- 版本包表
CREATE TABLE version_package (
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
CREATE INDEX idx_version_project ON version_package(project_id);
CREATE INDEX idx_version_project_ver ON version_package(project_id, version);

-- 部署记录表
CREATE TABLE deploy_record (
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
CREATE INDEX idx_deploy_project ON deploy_record(project_id);
CREATE INDEX idx_deploy_status ON deploy_record(status);

-- 告警记录表
CREATE TABLE alarm_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT,
    node_id BIGINT,
    type VARCHAR(50),
    content TEXT,
    send_result TINYINT DEFAULT 0,
    send_time BIGINT,
    create_time BIGINT
);
CREATE INDEX idx_alarm_type ON alarm_record(type);
CREATE INDEX idx_alarm_create ON alarm_record(create_time);

-- 系统用户表
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(128) NOT NULL,
    role VARCHAR(20) DEFAULT 'operator',
    status TINYINT DEFAULT 1,
    create_time BIGINT,
    update_time BIGINT
);
CREATE UNIQUE INDEX uk_user_name ON sys_user(username);

-- 操作审计表
CREATE TABLE operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    module VARCHAR(50),
    action VARCHAR(100),
    content TEXT,
    ip VARCHAR(50),
    create_time BIGINT
);
CREATE INDEX idx_log_user ON operation_log(user_id);
CREATE INDEX idx_log_module ON operation_log(module);
CREATE INDEX idx_log_create ON operation_log(create_time);

-- 文件访问审计表
CREATE TABLE file_access_log (
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
CREATE INDEX idx_file_node ON file_access_log(node_id);
CREATE INDEX idx_file_type ON file_access_log(file_type);
CREATE INDEX idx_file_create ON file_access_log(create_time);

-- 告警配置表
CREATE TABLE alarm_config (
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
CREATE TABLE sys_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT,
    description VARCHAR(500),
    update_time BIGINT
);

-- 初始化默认管理员 (密码: admin123)
INSERT INTO sys_user (id, username, password, role, status, create_time, update_time)
VALUES (1, 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin', 1, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);

-- 初始化默认告警配置
INSERT INTO alarm_config (id, enabled, smtp_host, smtp_port, smtp_ssl, receivers, update_time)
VALUES (1, 0, '', 465, 0, '', UNIX_TIMESTAMP() * 1000);
