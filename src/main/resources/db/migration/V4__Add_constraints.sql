-- V4__Add_constraints.sql
-- 제약조건 추가 (FOREIGN KEY, UNIQUE 등)
-- SQLite는 FOREIGN KEY를 기본적으로 지원하지만 runtime에 활성화 필요

-- SQLite에서는 이미 생성된 테이블에 제약조건을 추가하는 것이 제한적입니다.
-- 따라서 주요 제약조건은 V1에 포함되어 있고, 여기서는 추가적인 검증 규칙을 정의합니다.

-- 데이터 무결성 검증을 위한 트리거 생성

-- input_adapters: messagetype은 UNIQUE해야 함 (이미 V1에서 정의됨)
CREATE TRIGGER IF NOT EXISTS validate_input_adapter_type
BEFORE INSERT ON input_adapters
FOR EACH ROW
WHEN NEW.type NOT IN ('tcp', 'udp', 'http', 'kafka', 'file', 'fake')
BEGIN
    SELECT RAISE(ABORT, 'Invalid input adapter type');
END;

-- parsers: type 검증
CREATE TRIGGER IF NOT EXISTS validate_parser_type
BEFORE INSERT ON parsers
FOR EACH ROW
WHEN NEW.type NOT IN ('json', 'grok', 'regex', 'rfc3164', 'rfc5424', 'http')
BEGIN
    SELECT RAISE(ABORT, 'Invalid parser type');
END;

-- transforms: type 검증
CREATE TRIGGER IF NOT EXISTS validate_transform_type
BEFORE INSERT ON transforms
FOR EACH ROW
WHEN NEW.type NOT IN ('filter', 'add_property', 'remove_property')
BEGIN
    SELECT RAISE(ABORT, 'Invalid transform type');
END;

-- output_adapters: type 검증
CREATE TRIGGER IF NOT EXISTS validate_output_adapter_type
BEFORE INSERT ON output_adapters
FOR EACH ROW
WHEN NEW.type NOT IN ('console', 'tcp', 'http', 'kafka', 'opensearch', 'rabbitmq', 'benchmark')
BEGIN
    SELECT RAISE(ABORT, 'Invalid output adapter type');
END;

-- configuration_versions: status 검증
CREATE TRIGGER IF NOT EXISTS validate_version_status
BEFORE INSERT ON configuration_versions
FOR EACH ROW
WHEN NEW.status NOT IN ('DRAFT', 'ACTIVE', 'ARCHIVED')
BEGIN
    SELECT RAISE(ABORT, 'Invalid version status');
END;
