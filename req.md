# LogParser - 데이터베이스 기반 설정 관리 시스템 요구사항

## 1. 프로젝트 개요

### 1.1 현재 상태
- **설정 방식**: YAML 파일(`config/config.yaml`) 기반의 정적 설정
- **설정 관리**: `ConfigService`를 통한 기본적인 CRUD API 구현 (`/api/config/**`)
- **구조**: Input → Parser → Transform → Output 파이프라인 구조
- **기술 스택**: Spring Boot 3.5.8, Java 21

### 1.2 현재 구현 상황
#### 1.2.1 Config 관련 클래스 분석
- **ApplicationProperties**: YAML 파일에서 타입 안전한 설정 로드
  - `@ConfigurationProperties(prefix = "logparser")`로 YAML의 `logparser` 섹션 자동 바인딩
  - Input, Output, Parser, Transform 리스트 정의
  - `@PostConstruct`를 통한 설정 검증
  - 주요 공통 설정: `parserThreads`, `flushInterval`, `queueSize`

- **Config 클래스들** (타입 안전한 설정)
  - `InputAdapterConfig`: 다양한 입력 소스 설정 (File, TCP, UDP, HTTP, Kafka, Fake)
  - `OutputAdapterConfig`: 다양한 출력 목적지 설정 (Console, Kafka, HTTP, TCP, OpenSearch, RabbitMQ, Benchmark)
  - `ParserAdapterConfig`: 파서 타입 및 파라미터 설정 (JSON, Grok, Regex, RFC3164, RFC5424, HTTP)
  - `TransformConfig`: 변환 규칙 설정 (Filter, AddProperty, RemoveProperty 등)
  - 각 설정 클래스는 메타데이터 어노테이션으로 검증 규칙 정의

- **ConfigService**: 현재 구현 기능
  - `getConfig()`: YAML 파일 읽기
  - `addConfig()`: 특정 섹션에 설정 추가
  - `updateConfig()`: 인덱스 기반 설정 수정
  - `deleteConfig()`: 인덱스 기반 설정 삭제
  - `updateCommonConfig()`: 공통 설정 업데이트
  - 제약사항: YAML 파일 직접 조작, 이력 관리 없음, 설정 충돌 감지 없음

- **ConfigController**: 현재 구현 API
  - `GET /api/config`: 전체 설정 조회
  - `POST /api/config/{section}`: 설정 추가
  - `PUT /api/config/{section}/{index}`: 설정 수정
  - `DELETE /api/config/{section}/{index}`: 설정 삭제
  - `PUT /api/config/common`: 공통 설정 수정

#### 1.2.2 제약사항 및 개선 필요 사항
1. **정적 설정 관리**: YAML 파일 기반이므로 실시간 설정 변경 어려움
2. **검증 부족**: 기본 타입 검증만 수행, 파이프라인 무결성 검증 없음
3. **이력 관리 없음**: 누가, 언제, 무엇을 변경했는지 추적 불가능
4. **버전 관리 없음**: 이전 설정으로 돌아갈 수 없음
5. **동시성 제어 없음**: 동시 설정 변경 시 충돌 발생 가능
6. **파이프라인 무결성**: Input의 messagetype에 해당하는 Parser가 없으면 데이터 손실
7. **설정 연관성**: 변경된 설정이 파이프라인 전체에 미치는 영향 추적 불가능
8. **데이터 복원**: 설정 삭제 후 복원 불가능

### 1.3 목표
YAML 파일 기반의 설정 관리 방식을 **SQLite 데이터베이스로 전환**하고, Spring Data JPA를 활용한 완전한 설정 관리 시스템 구축:

1. **데이터베이스 기반 설정 저장 및 관리**
   - 영구적인 설정 저장
   - 관계형 데이터 모델링

2. **RESTful API를 통한 동적 설정 변경**
   - 실시간 설정 조회/수정/삭제
   - 단순한 인덱스 기반이 아닌 ID 기반 관리
   - 타입별/메시지 타입별 상세 조회 기능

3. **설정 검증 및 파이프라인 무결성 보장**
   - Input → Parser → Transform → Output 연결성 검증
   - 필수 필드 검증
   - 어댑터 타입별 특정 필드 검증
   - 데이터 손실 방지

4. **설정 이력 관리 및 감시**
   - 모든 변경 사항 기록 (who, when, what)
   - 이전 설정으로 빠르게 복원
   - 감시 로그

5. **버전 관리 및 스냅샷**
   - 안정적인 설정 버전 저장
   - 언제든지 이전 버전 활성화 가능

6. **파이프라인 동적 재로드**
   - 애플리케이션 재시작 없이 설정 변경 반영
   - 안전한 재로드 (검증 후 적용)
   - 재로드 실패 시 자동 복원

---

## 2. 데이터베이스 설계

### 2.1 필수 테이블 구조

#### 2.1.1 공통 설정 테이블 (config_settings)
```sql
CREATE TABLE config_settings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key VARCHAR(255) UNIQUE NOT NULL,
  config_value TEXT NOT NULL,
  data_type VARCHAR(50),  -- INT, LONG, STRING, BOOLEAN
  description TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Example records:
-- (1, 'parser_threads', '4', 'INT', 'Number of parser threads')
-- (2, 'queue_size', '20000', 'INT', 'Internal queue size')
-- (3, 'flush_interval', '5000', 'LONG', 'Flush interval in milliseconds')
```

#### 2.1.2 입력 어댑터 설정 테이블 (input_adapters)
```sql
CREATE TABLE input_adapters (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(100) NOT NULL,  -- FileInputAdapter, TcpInputAdapter, ...
  messagetype VARCHAR(100) NOT NULL,
  host VARCHAR(255),
  port INT,
  path VARCHAR(500),
  topicid VARCHAR(255),
  bootstrapservers VARCHAR(255),
  group_id VARCHAR(255),
  codec VARCHAR(50),  -- json, plain, multipart
  path_pattern VARCHAR(500),
  buffer_size INT DEFAULT 8192,
  timeout_ms INT DEFAULT 5000,
  enabled BOOLEAN DEFAULT TRUE,
  worker_threads INT DEFAULT 1,
  queue_size INT DEFAULT 1000,
  config_params JSON,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version INT DEFAULT 0,
  CONSTRAINT uk_input_adapter_messagetype UNIQUE (messagetype),
  INDEX idx_type (type),
  INDEX idx_messagetype (messagetype),
  INDEX idx_enabled (enabled)
);
```

#### 2.1.3 파서 설정 테이블 (parsers)
```sql
CREATE TABLE parsers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(100) NOT NULL,
  messagetype VARCHAR(100) NOT NULL,
  param TEXT,
  priority INT DEFAULT 0,
  enabled BOOLEAN DEFAULT TRUE,
  continue_on_failure BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version INT DEFAULT 0,
  INDEX idx_type (type),
  INDEX idx_messagetype (messagetype),
  INDEX idx_enabled (enabled),
  INDEX idx_priority_messagetype (priority, messagetype)
);
```

#### 2.1.4 변환 설정 테이블 (transforms)
```sql
CREATE TABLE transforms (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(100) NOT NULL,
  messagetype VARCHAR(100) NOT NULL,
  priority INT DEFAULT 0,
  filter_pass JSON,  -- {"event_type": ["alert", "anomaly"]}
  filter_drop JSON,
  add_properties JSON,  -- {"tag": ["src_ip", "src_port"]}
  remove_properties JSON,  -- ["@timestamp"]
  config_params JSON,
  enabled BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version INT DEFAULT 0,
  INDEX idx_type (type),
  INDEX idx_messagetype (messagetype),
  INDEX idx_enabled (enabled)
);
```

#### 2.1.5 출력 어댑터 설정 테이블 (output_adapters)
```sql
CREATE TABLE output_adapters (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(100) NOT NULL,
  messagetype VARCHAR(255),
  host VARCHAR(255),
  port INT,
  url VARCHAR(500),
  method VARCHAR(10),  -- POST, PUT, PATCH
  headers JSON,
  topicid VARCHAR(255),
  bootstrapservers VARCHAR(255),
  key VARCHAR(255),
  index_template VARCHAR(255),
  os_username VARCHAR(255),
  os_password VARCHAR(255),
  action VARCHAR(50),  -- create, index, update, upsert
  routingkey VARCHAR(255),
  exchange VARCHAR(255),
  rmq_username VARCHAR(255),
  rmq_password VARCHAR(255),
  rmq_port INT,
  tagpass JSON,
  batch_size INT DEFAULT 100,
  flush_interval_ms INT DEFAULT 5000,
  retry_count INT DEFAULT 3,
  retry_delay_ms INT DEFAULT 1000,
  add_origin_text BOOLEAN DEFAULT FALSE,
  enabled BOOLEAN DEFAULT TRUE,
  timeout_ms INT DEFAULT 30000,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version INT DEFAULT 0,
  INDEX idx_type (type),
  INDEX idx_messagetype (messagetype),
  INDEX idx_enabled (enabled)
);
```

#### 2.1.6 설정 이력 테이블 (config_history)
```sql
CREATE TABLE config_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  entity_type VARCHAR(100),  -- INPUT_ADAPTER, PARSER, TRANSFORM, OUTPUT_ADAPTER, SETTINGS
  entity_id BIGINT,
  action VARCHAR(50),  -- CREATE, UPDATE, DELETE
  old_values JSON,
  new_values JSON,
  changed_by VARCHAR(255),  -- REST API 호출자 정보
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_entity_type_id (entity_type, entity_id),
  INDEX idx_action (action),
  INDEX idx_created_at (created_at)
);
```

#### 2.1.7 설정 버전/스냅샷 테이블 (configuration_versions)
```sql
CREATE TABLE configuration_versions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  version_name VARCHAR(100) NOT NULL,
  description TEXT,
  input_adapters JSON NOT NULL,
  parsers JSON NOT NULL,
  transforms JSON NOT NULL,
  output_adapters JSON NOT NULL,
  common_settings JSON NOT NULL,
  status VARCHAR(50),  -- DRAFT, ACTIVE, ARCHIVED
  created_by VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  activated_at TIMESTAMP,
  INDEX idx_status (status),
  INDEX idx_created_at (created_at)
);
```

### 2.2 데이터 관계도 (ERD)
```
config_settings (공통 설정)
input_adapters ──→ (messagetype) ──→ parsers
                                         ↓
                                    transforms
                                         ↓
output_adapters (messagetype 매핑) ←──┘

config_history (모든 변경 기록)
configuration_versions (스냅샷)
```

---

## 3. JPA Entity 구현 요구사항

### 3.1 Entity 클래스 목록
```
- ConfigSettingsEntity - 공통 설정
- InputAdapterEntity - 입력 어댑터 설정
- ParserEntity - 파서 설정
- TransformEntity - 변환 설정
- OutputAdapterEntity - 출력 어댑터 설정
- ConfigHistoryEntity - 설정 이력
- ConfigurationVersionEntity - 설정 버전
```

### 3.2 공통 Entity 요구사항
```java
@Entity
@Table(name = "entity_name")
@EntityListeners(AuditingEntityListener.class)
public class SomeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Integer version;  // 낙관적 잠금
}
```

### 3.3 특수 필드 처리
- **JSON 필드**: `@Convert(converter = JsonConverter.class)` 사용
- **암호화 필드**: `@Convert(converter = CryptoConverter.class)` (비밀번호, 토큰 등)
- **Enum 필드**: `@Enumerated(EnumType.STRING)`

---

## 4. Repository & Service 계층 구현 요구사항

### 4.1 Repository 인터페이스 (Spring Data JPA)

#### 4.1.1 InputAdapterRepository
```java
public interface InputAdapterRepository extends JpaRepository<InputAdapterEntity, Long> {
    List<InputAdapterEntity> findByType(String type);
    InputAdapterEntity findByMessagetype(String messagetype);
    List<InputAdapterEntity> findByEnabledTrue();
    Page<InputAdapterEntity> findAll(Pageable pageable);
}
```

#### 4.1.2 ParserRepository
```java
public interface ParserRepository extends JpaRepository<ParserEntity, Long> {
    List<ParserEntity> findByType(String type);
    List<ParserEntity> findByMessagetype(String messagetype);
    List<ParserEntity> findByMessagetypeOrderByPriorityAsc(String messagetype);
    List<ParserEntity> findByEnabledTrue();
}
```

#### 4.1.3 TransformRepository
```java
public interface TransformRepository extends JpaRepository<TransformEntity, Long> {
    List<TransformEntity> findByType(String type);
    List<TransformEntity> findByMessagetype(String messagetype);
    List<TransformEntity> findByMessagetypeOrderByPriorityAsc(String messagetype);
}
```

#### 4.1.4 OutputAdapterRepository
```java
public interface OutputAdapterRepository extends JpaRepository<OutputAdapterEntity, Long> {
    List<OutputAdapterEntity> findByType(String type);
    List<OutputAdapterEntity> findByMessagetype(String messagetype);
    List<OutputAdapterEntity> findByMessaetypeIsNullOrMessagetype(String messagetype);
    List<OutputAdapterEntity> findByEnabledTrue();
}
```

#### 4.1.5 ConfigSettingsRepository
```java
public interface ConfigSettingsRepository extends JpaRepository<ConfigSettingsEntity, Long> {
    ConfigSettingsEntity findByConfigKey(String key);
    void deleteByConfigKey(String key);
}
```

#### 4.1.6 ConfigHistoryRepository
```java
public interface ConfigHistoryRepository extends JpaRepository<ConfigHistoryEntity, Long> {
    List<ConfigHistoryEntity> findByEntityTypeAndEntityId(String entityType, Long entityId, Sort sort);
    List<ConfigHistoryEntity> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Sort sort);
    Page<ConfigHistoryEntity> findAll(Pageable pageable);
}
```

#### 4.1.7 ConfigurationVersionRepository
```java
public interface ConfigurationVersionRepository extends JpaRepository<ConfigurationVersionEntity, Long> {
    List<ConfigurationVersionEntity> findByStatusOrderByCreatedAtDesc(String status);
    Optional<ConfigurationVersionEntity> findByVersionName(String versionName);
}
```

### 4.2 Service 계층 기능

#### 4.2.1 ConfigManagementService (주요 비즈니스 로직)

**InputAdapter 관리**
```java
InputAdapterEntity createInputAdapter(CreateInputAdapterRequest request);
InputAdapterEntity updateInputAdapter(Long id, UpdateInputAdapterRequest request);
void deleteInputAdapter(Long id);
InputAdapterEntity getInputAdapter(Long id);
List<InputAdapterEntity> getAllInputAdapters();
List<InputAdapterEntity> getInputAdaptersByType(String type);
InputAdapterEntity getInputAdapterByMessageType(String messageType);
void enableInputAdapter(Long id);
void disableInputAdapter(Long id);
```

**Parser 관리**
```java
ParserEntity createParser(CreateParserRequest request);
ParserEntity updateParser(Long id, UpdateParserRequest request);
void deleteParser(Long id);
ParserEntity getParser(Long id);
List<ParserEntity> getAllParsers();
List<ParserEntity> getParsersByType(String type);
List<ParserEntity> getParsersByMessageType(String messageType);  // 우선순위 정렬
void updateParserPriority(Long id, Integer newPriority);
```

**Transform 관리**
```java
TransformEntity createTransform(CreateTransformRequest request);
TransformEntity updateTransform(Long id, UpdateTransformRequest request);
void deleteTransform(Long id);
TransformEntity getTransform(Long id);
List<TransformEntity> getAllTransforms();
List<TransformEntity> getTransformsByType(String type);
List<TransformEntity> getTransformsByMessageType(String messageType);  // 우선순위 정렬
void updateTransformPriority(Long id, Integer newPriority);
```

**OutputAdapter 관리**
```java
OutputAdapterEntity createOutputAdapter(CreateOutputAdapterRequest request);
OutputAdapterEntity updateOutputAdapter(Long id, UpdateOutputAdapterRequest request);
void deleteOutputAdapter(Long id);
OutputAdapterEntity getOutputAdapter(Long id);
List<OutputAdapterEntity> getAllOutputAdapters();
List<OutputAdapterEntity> getOutputAdaptersByType(String type);
List<OutputAdapterEntity> getOutputAdaptersByMessageType(String messageType);
void enableOutputAdapter(Long id);
void disableOutputAdapter(Long id);
```

**공통 설정 관리**
```java
void updateCommonSettings(Map<String, Object> settings);
Map<String, Object> getAllCommonSettings();
String getConfigValue(String key);
void setConfigValue(String key, Object value, DataType dataType);
```

#### 4.2.2 ConfigValidationService

**개별 검증**
```java
ValidationResult validateInputAdapter(InputAdapterEntity adapter);
ValidationResult validateParser(ParserEntity parser);
ValidationResult validateTransform(TransformEntity transform);
ValidationResult validateOutputAdapter(OutputAdapterEntity adapter);
```

**파이프라인 무결성 검증**
```java
/**
 * 전체 파이프라인 연결성 검증
 * 1. 모든 InputAdapter의 messagetype에 대응하는 Parser 존재 확인
 * 2. 모든 Parser의 messagetype에 대응하는 Transform/Output 존재 확인
 * 3. 모든 OutputAdapter의 messagetype이 Parser에서 생성되는 타입인지 확인
 * 4. 필수 어댑터(최소 1개 Input, 1개 Parser, 1개 Output) 존재 확인
 */
PipelineIntegrityResult validatePipelineIntegrity();

// 상세 검증 정보
List<ValidationError> getAllValidationErrors();
Map<String, List<ValidationError>> getErrorsByEntity();
```

#### 4.2.3 ConfigHistoryService

```java
void recordConfigChange(
    String entityType,  // INPUT_ADAPTER, PARSER, TRANSFORM, OUTPUT_ADAPTER, SETTINGS
    Long entityId,
    String action,      // CREATE, UPDATE, DELETE
    Object oldValues,
    Object newValues,
    String changedBy    // 요청자 정보
);

List<ConfigHistoryEntity> getHistoryForEntity(String entityType, Long entityId);
List<ConfigHistoryEntity> getHistoryByDateRange(LocalDateTime from, LocalDateTime to);
Page<ConfigHistoryEntity> getHistoryPage(Pageable pageable);
List<ConfigHistoryEntity> getHistoryByAction(String action);

// 이전 상태로 복원
void revertToHistory(Long historyId);

// 두 설정 비교
ConfigDiff compareHistories(Long historyId1, Long historyId2);
```

#### 4.2.4 ConfigVersionService

```java
// 현재 설정 전체를 새로운 버전으로 저장 (스냅샷)
ConfigurationVersionEntity createVersion(
    String versionName,
    String description,
    String createdBy
);

// 특정 버전 조회
ConfigurationVersionEntity getVersion(Long versionId);

// 특정 버전을 현재 설정으로 활성화 (복원)
void activateVersion(Long versionId);

// 모든 버전 조회
List<ConfigurationVersionEntity> listVersions();

// 상태별 버전 조회
List<ConfigurationVersionEntity> listVersionsByStatus(String status);

// 버전 삭제
void deleteVersion(Long versionId);

// 버전 내보내기
String exportVersionAsYaml(Long versionId);
String exportVersionAsJson(Long versionId);

// 버전 비교
ConfigDiff compareVersions(Long versionId1, Long versionId2);
```

#### 4.2.5 PipelineReloadService

```java
/**
 * 데이터베이스에서 최신 설정 로드 (파이프라인에 반영)
 * - 검증 자동 수행
 * - 실패 시 기존 설정 유지
 * - 재로드 이력 기록
 */
void reloadConfiguration();

/**
 * 검증 후 안전하게 재로드
 * - validatePipelineIntegrity() 먼저 수행
 * - 검증 실패 시 예외 발생 (재로드 없음)
 */
void validateAndReload() throws ConfigValidationException;

/**
 * 파이프라인 재시작 (주의: 진행 중인 메시지 손실 가능)
 * - 현재 큐에 있는 메시지 완료 대기
 * - 새로운 입력 차단
 * - 파이프라인 종료 및 재시작
 */
void restartPipeline() throws PipelineRestartException;

// 상태 조회
boolean isReloadInProgress();
PipelineStatus getPipelineStatus();
ReloadProgress getReloadProgress();

// 재로드 취소 (진행 중인 경우만)
void cancelReload();
```

#### 4.2.6 ConfigExportService

```java
// 현재 DB 설정을 YAML로 내보내기
String exportCurrentConfigAsYaml();

// JSON으로 내보내기
String exportCurrentConfigAsJson();

// YAML에서 설정 임포트 (DB에 저장)
void importFromYaml(String yamlContent, boolean overwrite) throws ImportException;

// JSON에서 설정 임포트
void importFromJson(String jsonContent, boolean overwrite) throws ImportException;

// 파일에서 임포트
void importFromFile(MultipartFile file, boolean overwrite) throws ImportException;
```

#### 4.2.7 ConfigMetadataService (메타데이터 제공)

```java
// 지원하는 어댑터 타입 및 정보
List<AdapterTypeInfo> getInputAdapterTypes();
List<AdapterTypeInfo> getOutputAdapterTypes();
List<AdapterTypeInfo> getParserTypes();
List<TransformTypeInfo> getTransformTypes();

// 특정 타입의 스키마 (필드, 필수 여부, 타입 등)
AdapterSchema getInputAdapterSchema(String type);
AdapterSchema getOutputAdapterSchema(String type);
AdapterSchema getParserSchema(String type);
TransformSchema getTransformSchema(String type);

// 지원하는 Codec, Protocol 등
List<String> getSupportedCodecs();
List<String> getSupportedHttpMethods();
// ...
```

---

## 5. REST API 명세

### 5.1 입력 어댑터 관리 API

```
GET    /api/v1/input-adapters
       - 모든 입력 어댑터 조회
       - Query params: page=0, size=20, sort=createdAt
       - Response: Page<InputAdapterDTO>

POST   /api/v1/input-adapters
       - 새로운 입력 어댑터 생성
       - Body: CreateInputAdapterRequest
       - Response: InputAdapterDTO

GET    /api/v1/input-adapters/{id}
       - 특정 입력 어댑터 조회
       - Response: InputAdapterDTO

PUT    /api/v1/input-adapters/{id}
       - 입력 어댑터 수정
       - Body: UpdateInputAdapterRequest
       - Response: InputAdapterDTO

DELETE /api/v1/input-adapters/{id}
       - 입력 어댑터 삭제
       - Response: 200 OK

GET    /api/v1/input-adapters/type/{type}
       - 타입별 입력 어댑터 조회
       - Response: List<InputAdapterDTO>

GET    /api/v1/input-adapters/messagetype/{messageType}
       - 메시지 타입별 입력 어댑터 조회
       - Response: InputAdapterDTO

PATCH  /api/v1/input-adapters/{id}/enable
       - 입력 어댑터 활성화
       - Response: InputAdapterDTO

PATCH  /api/v1/input-adapters/{id}/disable
       - 입력 어댑터 비활성화
       - Response: InputAdapterDTO
```

### 5.2 파서 관리 API

```
GET    /api/v1/parsers
       - 모든 파서 조회
       - Response: Page<ParserDTO>

POST   /api/v1/parsers
       - 새로운 파서 생성
       - Body: CreateParserRequest
       - Response: ParserDTO

GET    /api/v1/parsers/{id}
       - 특정 파서 조회
       - Response: ParserDTO

PUT    /api/v1/parsers/{id}
       - 파서 수정
       - Body: UpdateParserRequest
       - Response: ParserDTO

DELETE /api/v1/parsers/{id}
       - 파서 삭제
       - Response: 200 OK

GET    /api/v1/parsers/type/{type}
       - 타입별 파서 조회
       - Response: List<ParserDTO>

GET    /api/v1/parsers/messagetype/{messageType}
       - 메시지 타입별 파서 조회 (우선순위 정렬)
       - Response: List<ParserDTO>

PATCH  /api/v1/parsers/{id}/priority
       - 파서 우선순위 변경
       - Body: {"priority": 1}
       - Response: ParserDTO

POST   /api/v1/parsers/validate
       - 파서 설정 검증 (미리보기)
       - Body: CreateParserRequest
       - Response: ValidationResultDTO
```

### 5.3 변환 설정 관리 API

```
GET    /api/v1/transforms
       - 모든 변환 설정 조회
       - Response: Page<TransformDTO>

POST   /api/v1/transforms
       - 새로운 변환 설정 생성
       - Body: CreateTransformRequest
       - Response: TransformDTO

GET    /api/v1/transforms/{id}
       - 특정 변환 설정 조회
       - Response: TransformDTO

PUT    /api/v1/transforms/{id}
       - 변환 설정 수정
       - Body: UpdateTransformRequest
       - Response: TransformDTO

DELETE /api/v1/transforms/{id}
       - 변환 설정 삭제
       - Response: 200 OK

GET    /api/v1/transforms/type/{type}
       - 타입별 변환 설정 조회
       - Response: List<TransformDTO>

GET    /api/v1/transforms/messagetype/{messageType}
       - 메시지 타입별 변환 설정 조회 (우선순위 정렬)
       - Response: List<TransformDTO>

PATCH  /api/v1/transforms/{id}/priority
       - 변환 순서 변경
       - Body: {"priority": 0}
       - Response: TransformDTO
```

### 5.4 출력 어댑터 관리 API

```
GET    /api/v1/output-adapters
       - 모든 출력 어댑터 조회
       - Response: Page<OutputAdapterDTO>

POST   /api/v1/output-adapters
       - 새로운 출력 어댑터 생성
       - Body: CreateOutputAdapterRequest
       - Response: OutputAdapterDTO

GET    /api/v1/output-adapters/{id}
       - 특정 출력 어댑터 조회
       - Response: OutputAdapterDTO

PUT    /api/v1/output-adapters/{id}
       - 출력 어댑터 수정
       - Body: UpdateOutputAdapterRequest
       - Response: OutputAdapterDTO

DELETE /api/v1/output-adapters/{id}
       - 출력 어댑터 삭제
       - Response: 200 OK

GET    /api/v1/output-adapters/type/{type}
       - 타입별 출력 어댑터 조회
       - Response: List<OutputAdapterDTO>

GET    /api/v1/output-adapters/messagetype/{messageType}
       - 메시지 타입별 출력 어댑터 조회
       - Response: List<OutputAdapterDTO>

PATCH  /api/v1/output-adapters/{id}/enable
       - 출력 어댑터 활성화
       - Response: OutputAdapterDTO

PATCH  /api/v1/output-adapters/{id}/disable
       - 출력 어댑터 비활성화
       - Response: OutputAdapterDTO
```

### 5.5 공통 설정 관리 API

```
GET    /api/v1/settings
       - 모든 공통 설정 조회
       - Response: Map<String, SettingDTO>

PUT    /api/v1/settings
       - 공통 설정 일괄 업데이트
       - Body: Map<String, Object>
       - Response: Map<String, SettingDTO>

GET    /api/v1/settings/{key}
       - 특정 설정값 조회
       - Response: SettingDTO

PUT    /api/v1/settings/{key}
       - 특정 설정값 업데이트
       - Body: {"value": "100", "dataType": "INT"}
       - Response: SettingDTO
```

### 5.6 검증 API

```
GET    /api/v1/validate/pipeline
       - 전체 파이프라인 검증
       - Response: PipelineValidationResultDTO

POST   /api/v1/validate/input
       - 입력 어댑터 설정 검증 (미리보기)
       - Body: CreateInputAdapterRequest
       - Response: ValidationResultDTO

POST   /api/v1/validate/parser
       - 파서 설정 검증
       - Body: CreateParserRequest
       - Response: ValidationResultDTO

POST   /api/v1/validate/transform
       - 변환 설정 검증
       - Body: CreateTransformRequest
       - Response: ValidationResultDTO

POST   /api/v1/validate/output
       - 출력 어댑터 설정 검증
       - Body: CreateOutputAdapterRequest
       - Response: ValidationResultDTO

GET    /api/v1/validate/errors
       - 현재 모든 검증 오류 조회
       - Response: List<ValidationErrorDTO>
```

### 5.7 설정 이력 관리 API

```
GET    /api/v1/history
       - 모든 설정 변경 이력 조회 (페이지네이션)
       - Query: page=0, size=50, sort=-createdAt
       - Response: Page<ConfigHistoryDTO>

GET    /api/v1/history/entity/{entityType}/{entityId}
       - 특정 엔티티의 이력 조회
       - Response: List<ConfigHistoryDTO>

GET    /api/v1/history/date-range
       - 날짜 범위로 이력 조회
       - Query: from=2025-01-01T00:00:00, to=2025-01-31T23:59:59
       - Response: List<ConfigHistoryDTO>

POST   /api/v1/history/revert/{historyId}
       - 특정 이력 상태로 복원
       - Response: {"status": "SUCCESS", "message": "..."}

GET    /api/v1/history/diff/{id1}/{id2}
       - 두 설정 비교
       - Response: ConfigDiffDTO
```

### 5.8 버전 관리 API

```
GET    /api/v1/versions
       - 모든 설정 버전 조회
       - Response: Page<ConfigurationVersionDTO>

POST   /api/v1/versions
       - 현재 설정 버전 생성 (스냅샷)
       - Body: {"versionName": "v1.0.0", "description": "Initial version"}
       - Response: ConfigurationVersionDTO

GET    /api/v1/versions/{id}
       - 특정 버전 조회
       - Response: ConfigurationVersionDTO

PUT    /api/v1/versions/{id}/activate
       - 특정 버전 활성화 (복원)
       - Response: {"status": "SUCCESS", "message": "..."}

DELETE /api/v1/versions/{id}
       - 버전 삭제
       - Response: 200 OK

GET    /api/v1/versions/{id}/export/yaml
       - YAML로 내보내기
       - Response: YAML 파일

GET    /api/v1/versions/{id}/export/json
       - JSON으로 내보내기
       - Response: JSON 파일

GET    /api/v1/versions/diff/{id1}/{id2}
       - 두 버전 비교
       - Response: ConfigDiffDTO
```

### 5.9 파이프라인 제어 API

```
GET    /api/v1/pipeline/status
       - 파이프라인 현재 상태 조회
       - Response: PipelineStatusDTO {
           status: RUNNING|STOPPED|RELOADING,
           inputCount: 1,
           parserCount: 2,
           transformCount: 3,
           outputCount: 1,
           queueSize: 150,
           messagesProcessed: 10000,
           lastReloadTime: "2025-01-20T15:30:00Z"
         }

POST   /api/v1/pipeline/reload
       - 설정 재로드 (검증 필수, 실패 시 복원)
       - Response: {"status": "SUCCESS", "message": "..."}

POST   /api/v1/pipeline/restart
       - 파이프라인 재시작 (CAUTION: 진행 중 메시지 손실 가능)
       - Body: {"graceful": true}  // true면 진행 중 작업 완료 후 재시작
       - Response: {"status": "SUCCESS", "message": "..."}

GET    /api/v1/pipeline/reload-status
       - 재로드 진행 상황 조회
       - Response: {"inProgress": true, "progress": 75, "message": "Loading parsers..."}

POST   /api/v1/pipeline/validate-and-reload
       - 검증 후 안전한 재로드
       - Response: PipelineValidationResultDTO + reload result
```

### 5.10 설정 가져오기/내보내기 API

```
POST   /api/v1/config/import/yaml
       - YAML 파일 임포트
       - Body: multipart/form-data (file + overwrite flag)
       - Response: ImportResultDTO

POST   /api/v1/config/import/json
       - JSON 파일 임포트
       - Body: multipart/form-data
       - Response: ImportResultDTO

GET    /api/v1/config/export/yaml
       - 전체 설정을 YAML로 내보내기
       - Response: YAML 파일 (application/yaml)

GET    /api/v1/config/export/json
       - 전체 설정을 JSON으로 내보내기
       - Response: JSON 파일 (application/json)
```

### 5.11 메타데이터 조회 API

```
GET    /api/v1/metadata/adapter-types
       - 사용 가능한 어댑터 타입 목록
       - Response: List<AdapterTypeInfoDTO>

GET    /api/v1/metadata/parser-types
       - 사용 가능한 파서 타입 목록
       - Response: List<ParserTypeInfoDTO>

GET    /api/v1/metadata/transform-types
       - 사용 가능한 변환 타입 목록
       - Response: List<TransformTypeInfoDTO>

GET    /api/v1/metadata/input-adapter-schema/{type}
       - 특정 입력 어댑터 타입의 스키마
       - Response: SchemaDTO

GET    /api/v1/metadata/output-adapter-schema/{type}
       - 특정 출력 어댑터 타입의 스키마
       - Response: SchemaDTO

GET    /api/v1/metadata/parser-schema/{type}
       - 특정 파서 타입의 스키마
       - Response: SchemaDTO

GET    /api/v1/metadata/transform-schema/{type}
       - 특정 변환 타입의 스키마
       - Response: SchemaDTO
```

---

## 6. DTO 정의

### 6.1 InputAdapterDTO & Requests
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InputAdapterDTO {
    private Long id;
    private String type;
    private String messagetype;
    private String host;
    private Integer port;
    private String path;
    // ... 기타 필드
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

public class CreateInputAdapterRequest {
    @NotBlank @NotNull
    private String type;
    
    @NotBlank @NotNull
    private String messagetype;
    
    // 타입별 검증 @NotNull 어노테이션 추가
    private String path;
    // ...
}

public class UpdateInputAdapterRequest {
    // CreateInputAdapterRequest와 동일하되 일부 필드는 Optional
}
```

### 6.2 기타 DTO
- `ParserDTO`, `CreateParserRequest`, `UpdateParserRequest`
- `TransformDTO`, `CreateTransformRequest`, `UpdateTransformRequest`
- `OutputAdapterDTO`, `CreateOutputAdapterRequest`, `UpdateOutputAdapterRequest`
- `ConfigSettingsDTO`
- `ConfigHistoryDTO`
- `ConfigurationVersionDTO`
- `ValidationErrorDTO`, `ValidationResultDTO`, `PipelineValidationResultDTO`
- `PipelineStatusDTO`, `ReloadProgressDTO`
- `ConfigDiffDTO`, `ImportResultDTO`
- `AdapterTypeInfoDTO`, `SchemaDTO`, `FieldSchemaDTO`

---

## 7. 오류 처리 및 예외 관리

### 7.1 Custom Exception 클래스
```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ConfigNotFoundException extends RuntimeException {...}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ConfigValidationException extends RuntimeException {...}

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateConfigException extends RuntimeException {...}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PipelineIntegrityException extends RuntimeException {...}

@ResponseStatus(HttpStatus.CONFLICT)
public class ConfigConflictException extends RuntimeException {...}

@ResponseStatus(HttpStatus.NOT_FOUND)
public class VersionNotFoundException extends RuntimeException {...}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAdapterTypeException extends RuntimeException {...}

@ResponseStatus(HttpStatus.CONFLICT)
public class OptimisticLockException extends RuntimeException {...}
```

### 7.2 Global Exception Handler
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ConfigNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleConfigNotFound(...) {...}
    
    @ExceptionHandler(ConfigValidationException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationException(...) {...}
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrityViolation(...) {...}
    
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponseDTO> handleOptimisticLock(...) {...}
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleInternalServerError(...) {...}
}
```

### 7.3 표준 에러 응답 구조
```json
{
  "timestamp": "2025-01-21T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "입력 어댑터 설정 검증 실패",
  "details": [
    {
      "field": "port",
      "message": "포트는 1-65535 범위여야 합니다",
      "rejectedValue": 70000
    }
  ],
  "path": "/api/v1/input-adapters"
}
```

---

## 8. 마이그레이션 전략

### 8.1 기존 YAML 설정에서 DB로 마이그레이션

**마이그레이션 절차:**
1. 애플리케이션 시작 시 `config/config.yaml` 감지
2. 자동 또는 수동으로 YAML 데이터 DB로 임포트
3. 데이터 무결성 검증
4. 설정 이력에 "SYSTEM_IMPORT" 기록

**설정 플래그:**
```yaml
logparser:
  config-source: DATABASE  # DATABASE (권장) 또는 YAML
  migration:
    enabled: true
    auto-import-yaml: true  # 자동으로 YAML 임포트
    yaml-file-path: ./config/config.yaml
    backup-yaml: true  # 임포트 후 백업 파일 생성
```

### 8.2 데이터베이스 관리 (Flyway)

**마이그레이션 파일 구조:**
```
src/main/resources/db/migration/
├── V1__Initial_schema.sql
├── V2__Add_indexes.sql
├── V3__Add_config_history.sql
├── V4__Add_configuration_versions.sql
└── V5__Add_constraints.sql
```

---

## 9. 보안 고려사항

### 9.1 인증 및 인가
- API 키 또는 토큰 기반 인증 구현 (선택사항)
- 모든 설정 변경은 사용자 정보와 함께 기록

### 9.2 민감 데이터 보호
- 비밀번호, 토큰 등은 암호화하여 저장
- API 응답에 민감 정보 노출 금지
  ```java
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private String password;
  ```

### 9.3 입력 검증
- SQL Injection: JPA 파라미터 바인딩 (자동 방지)
- XSS: JSON 응답으로 인해 기본적으로 안전
- 정규식 패턴 검증

---

## 10. 성능 최적화

### 10.1 데이터베이스 인덱싱
```sql
CREATE INDEX idx_input_adapters_type ON input_adapters(type);
CREATE INDEX idx_input_adapters_messagetype ON input_adapters(messagetype);
CREATE INDEX idx_input_adapters_enabled ON input_adapters(enabled);
CREATE INDEX idx_parsers_messagetype_priority ON parsers(messagetype, priority);
CREATE INDEX idx_transforms_messagetype_priority ON transforms(messagetype, priority);
-- ...
```

### 10.2 캐싱 전략
```java
@Cacheable(cacheNames = "inputAdapters", key = "#type")
public List<InputAdapterEntity> getInputAdaptersByType(String type) {...}

@CacheEvict(cacheNames = {"inputAdapters", "pipelineStatus"}, allEntries = true)
public InputAdapterEntity updateInputAdapter(Long id, UpdateInputAdapterRequest request) {...}
```

### 10.3 쿼리 최적화
- N+1 쿼리 방지: `@EntityGraph` 또는 `fetch join`
- 페이지네이션 적용: 이력 조회, 버전 조회 등
- 불필요한 필드 로딩 방지: DTO 프로젝션

---

## 11. 테스트 요구사항

### 11.1 단위 테스트
```java
// ConfigManagementServiceTest
// ConfigValidationServiceTest
// ConfigHistoryServiceTest
// ConfigVersionServiceTest
// PipelineReloadServiceTest
```

### 11.2 통합 테스트
```java
@DataJpaTest  // JPA만 테스트
@WebMvcTest   // Controller만 테스트
@SpringBootTest  // 전체 통합 테스트
```

### 11.3 E2E 테스트
- YAML 임포트 → DB 저장 → API 조회 흐름
- 파이프라인 재로드 기능
- 버전 복원 기능

---

## 12. 배포 및 운영

### 12.1 데이터베이스 초기화
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 프로덕션: validate, 개발: create-drop
  flyway:
    enabled: true
    baseline-on-migrate: true
```

### 12.2 애플리케이션 프로퍼티
```yaml
logparser:
  config-source: DATABASE
  database:
    type: sqlite
    file-path: ./data/logparser.db
    auto-backup: true
```

### 12.3 모니터링 메트릭
- 설정 변경 횟수
- 파이프라인 재로드 성공/실패율
- API 응답 시간

---

## 13. 기존 코드 수정 사항

### 13.1 ApplicationProperties 클래스 수정
- YAML 대신 데이터베이스에서 설정 로드
- 초기 구동 시 YAML 자동 임포트

### 13.2 ConfigController 확장
- 기존 API 유지 (호환성)
- 신규 API 추가 (`/api/v1/**`)

### 13.3 컴포넌트 초기화 로직 수정
- `InputAdaptorComponent`, `MessageDispatcher` 등에서 데이터베이스 설정 로드

---

## 14. 추가 기능 (선택사항)

1. **설정 템플릿**: 자주 사용하는 설정 조합 저장/로드
2. **복잡한 검색/필터링**: 쿼리 빌더
3. **웹 UI 대시보드**: 설정 관리, 실시간 모니터링
4. **API 문서화**: Springdoc OpenAPI/Swagger

---

## 15. 구현 로드맵

### Phase 1: 데이터베이스 및 Entity (1-2주)
- SQLite 설정 및 Flyway 마이그레이션
- Entity 클래스 정의
- Repository 인터페이스 작성

### Phase 2: 핵심 서비스 계층 (2-3주)
- ConfigManagementService
- ConfigValidationService
- 기본 CRUD 기능

### Phase 3: REST API 구현 (2주)
- InputAdapter, Parser, Transform, OutputAdapter 엔드포인트
- 공통 설정 엔드포인트
- 검증 엔드포인트

### Phase 4: 고급 기능 (2-3주)
- 설정 이력 및 버전 관리
- 파이프라인 재로드
- 임포트/내보내기

### Phase 5: 테스트 및 최적화 (2주)
- 단위/통합/E2E 테스트
- 성능 최적화
- 문서화

---

## 16. 추가 의존성

```gradle
// JPA & Database
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
runtimeOnly 'org.xerial:sqlite-jdbc:3.44.0.0'
implementation 'org.hibernate.orm:hibernate-core'

// Flyway (마이그레이션)
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-sqlite'

// Validation
implementation 'org.springframework.boot:spring-boot-starter-validation'

// JSON 처리
implementation 'com.fasterxml.jackson.core:jackson-databind'
implementation 'com.google.code.gson:gson:2.10.1'  // 기존

// MapStruct (DTO 변환, 선택)
implementation 'org.mapstruct:mapstruct:1.5.5.Final'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'

// Springdoc OpenAPI (Swagger, 선택)
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.0.4'

// 암호화
implementation 'org.springframework.security:spring-security-crypto'

// 기존 Lombok (유지)
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'

// 테스트
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:junit-jupiter'
```

---

## 17. 체크리스트

- [ ] SQLite 데이터베이스 환경 설정
- [ ] Flyway 마이그레이션 파일 작성
- [ ] Entity 클래스 정의 완료
- [ ] Repository 인터페이스 작성
- [ ] Service 계층 구현
- [ ] REST Controller 구현
- [ ] 검증 로직 구현
- [ ] 예외 처리 및 에러 핸들링
- [ ] YAML → DB 자동 마이그레이션
- [ ] 설정 이력 및 버전 관리 기능
- [ ] 파이프라인 재로드 기능
- [ ] 단위 테스트 작성
- [ ] 통합 테스트 작성
- [ ] E2E 테스트 작성
- [ ] API 문서화 (Swagger)
- [ ] 성능 테스트
- [ ] 보안 검토
- [ ] 프로덕션 배포 전 데이터 검증