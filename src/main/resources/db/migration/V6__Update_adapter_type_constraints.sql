-- V6__Update_adapter_type_constraints.sql
-- 어댑터 타입 제약조건 업데이트 (Short name -> Class name)

-- 1. 기존 트리거 삭제
DROP TRIGGER IF EXISTS validate_input_adapter_type;
DROP TRIGGER IF EXISTS validate_parser_type;
DROP TRIGGER IF EXISTS validate_transform_type;
DROP TRIGGER IF EXISTS validate_output_adapter_type;

-- 2. 새로운 트리거 생성 (클래스명 기준)

-- input_adapters: type 검증
CREATE TRIGGER validate_input_adapter_type
BEFORE INSERT ON input_adapters
FOR EACH ROW
WHEN NEW.type NOT IN ('TcpInputAdapter', 'UdpInputAdapter', 'HttpInputAdapter', 'KafkaInputAdapter', 'FileInputAdapter', 'FakeInputAdapter')
BEGIN
    SELECT RAISE(ABORT, 'Invalid input adapter type');
END;

-- parsers: type 검증
CREATE TRIGGER validate_parser_type
BEFORE INSERT ON parsers
FOR EACH ROW
WHEN NEW.type NOT IN ('JsonParser', 'GrokParser', 'RegexParser', 'RFC3164SyslogParser', 'RFC5424SyslogParser', 'HttpParser')
BEGIN
    SELECT RAISE(ABORT, 'Invalid parser type');
END;

-- transforms: type 검증
CREATE TRIGGER validate_transform_type
BEFORE INSERT ON transforms
FOR EACH ROW
WHEN NEW.type NOT IN ('Filter', 'AddProperty', 'RemoveProperty')
BEGIN
    SELECT RAISE(ABORT, 'Invalid transform type');
END;

-- output_adapters: type 검증
CREATE TRIGGER validate_output_adapter_type
BEFORE INSERT ON output_adapters
FOR EACH ROW
WHEN NEW.type NOT IN ('ConsoleOutputAdapter', 'TcpOutputAdapter', 'HttpOutputAdapter', 'KafkaOutputAdapter', 'OpenSearchOutputAdapter', 'RabbitMQAdapter', 'BenchmarkAdapter')
BEGIN
    SELECT RAISE(ABORT, 'Invalid output adapter type');
END;
