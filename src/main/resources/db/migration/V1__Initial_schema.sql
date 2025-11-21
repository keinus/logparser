-- V1__Initial_schema.sql
-- 기본 테이블 생성: config_settings, input_adapters, parsers, transforms, output_adapters

-- 공통 설정 테이블
CREATE TABLE IF NOT EXISTS config_settings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    config_key VARCHAR(255) NOT NULL UNIQUE,
    config_value TEXT,
    data_type VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

-- 입력 어댑터 설정 테이블
CREATE TABLE IF NOT EXISTS input_adapters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type VARCHAR(100) NOT NULL,
    messagetype VARCHAR(100) NOT NULL UNIQUE,
    host VARCHAR(255),
    port INTEGER,
    path VARCHAR(500),
    topicid VARCHAR(255),
    bootstrapservers VARCHAR(500),
    group_id VARCHAR(255),
    codec VARCHAR(50),
    path_pattern VARCHAR(500),
    buffer_size INTEGER,
    timeout_ms INTEGER,
    enabled BOOLEAN DEFAULT 1,
    worker_threads INTEGER,
    queue_size INTEGER,
    config_params TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

-- 파서 설정 테이블
CREATE TABLE IF NOT EXISTS parsers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type VARCHAR(100) NOT NULL,
    messagetype VARCHAR(100) NOT NULL,
    param TEXT,
    priority INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT 1,
    continue_on_failure BOOLEAN DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

-- 변환 설정 테이블
CREATE TABLE IF NOT EXISTS transforms (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type VARCHAR(100) NOT NULL,
    messagetype VARCHAR(100) NOT NULL,
    priority INTEGER DEFAULT 0,
    filter_pass TEXT,
    filter_drop TEXT,
    add_properties TEXT,
    remove_properties TEXT,
    config_params TEXT,
    enabled BOOLEAN DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

-- 출력 어댑터 설정 테이블
CREATE TABLE IF NOT EXISTS output_adapters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type VARCHAR(100) NOT NULL,
    messagetype VARCHAR(100) NOT NULL,
    host VARCHAR(255),
    port INTEGER,
    url VARCHAR(1000),
    method VARCHAR(20),
    headers TEXT,
    topicid VARCHAR(255),
    bootstrapservers VARCHAR(500),
    key VARCHAR(255),
    index_template VARCHAR(255),
    os_username VARCHAR(255),
    os_password VARCHAR(500),
    action VARCHAR(50),
    routingkey VARCHAR(255),
    exchange VARCHAR(255),
    rmq_username VARCHAR(255),
    rmq_password VARCHAR(500),
    rmq_port INTEGER,
    tagpass TEXT,
    batch_size INTEGER,
    flush_interval_ms INTEGER,
    retry_count INTEGER,
    retry_delay_ms INTEGER,
    add_origin_text BOOLEAN DEFAULT 0,
    enabled BOOLEAN DEFAULT 1,
    timeout_ms INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);
