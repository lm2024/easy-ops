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
