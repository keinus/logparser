-- V2__Add_history_and_versions.sql
-- 이력 및 버전 테이블 생성: config_history, configuration_versions

-- 설정 이력 테이블
CREATE TABLE IF NOT EXISTS config_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entity_type VARCHAR(100) NOT NULL,
    entity_id INTEGER NOT NULL,
    action VARCHAR(50) NOT NULL,
    old_values TEXT,
    new_values TEXT,
    changed_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 설정 버전/스냅샷 테이블
CREATE TABLE IF NOT EXISTS configuration_versions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    version_name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    input_adapters TEXT,
    parsers TEXT,
    transforms TEXT,
    output_adapters TEXT,
    common_settings TEXT,
    status VARCHAR(50) DEFAULT 'DRAFT',
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP
);
