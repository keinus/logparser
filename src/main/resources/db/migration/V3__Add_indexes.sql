-- V3__Add_indexes.sql
-- 성능 최적화를 위한 인덱스 생성

-- config_settings 인덱스
CREATE INDEX IF NOT EXISTS idx_config_settings_key ON config_settings(config_key);

-- input_adapters 인덱스
CREATE INDEX IF NOT EXISTS idx_input_adapters_type ON input_adapters(type);
CREATE INDEX IF NOT EXISTS idx_input_adapters_messagetype ON input_adapters(messagetype);
CREATE INDEX IF NOT EXISTS idx_input_adapters_enabled ON input_adapters(enabled);

-- parsers 인덱스
CREATE INDEX IF NOT EXISTS idx_parsers_type ON parsers(type);
CREATE INDEX IF NOT EXISTS idx_parsers_messagetype ON parsers(messagetype);
CREATE INDEX IF NOT EXISTS idx_parsers_enabled ON parsers(enabled);
CREATE INDEX IF NOT EXISTS idx_parsers_priority ON parsers(priority);
CREATE INDEX IF NOT EXISTS idx_parsers_messagetype_priority ON parsers(messagetype, priority);

-- transforms 인덱스
CREATE INDEX IF NOT EXISTS idx_transforms_type ON transforms(type);
CREATE INDEX IF NOT EXISTS idx_transforms_messagetype ON transforms(messagetype);
CREATE INDEX IF NOT EXISTS idx_transforms_enabled ON transforms(enabled);
CREATE INDEX IF NOT EXISTS idx_transforms_priority ON transforms(priority);
CREATE INDEX IF NOT EXISTS idx_transforms_messagetype_priority ON transforms(messagetype, priority);

-- output_adapters 인덱스
CREATE INDEX IF NOT EXISTS idx_output_adapters_type ON output_adapters(type);
CREATE INDEX IF NOT EXISTS idx_output_adapters_messagetype ON output_adapters(messagetype);
CREATE INDEX IF NOT EXISTS idx_output_adapters_enabled ON output_adapters(enabled);

-- config_history 인덱스
CREATE INDEX IF NOT EXISTS idx_config_history_entity ON config_history(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_config_history_created_at ON config_history(created_at);
CREATE INDEX IF NOT EXISTS idx_config_history_action ON config_history(action);

-- configuration_versions 인덱스
CREATE INDEX IF NOT EXISTS idx_configuration_versions_status ON configuration_versions(status);
CREATE INDEX IF NOT EXISTS idx_configuration_versions_created_at ON configuration_versions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_configuration_versions_name ON configuration_versions(version_name);
