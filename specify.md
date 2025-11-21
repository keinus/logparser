# LogParser - 데이터베이스 기반 설정 관리 시스템 구현 기능 리스트

## Phase 1: 데이터베이스 및 Entity 구성 (1-2주)

### 1.1 데이터베이스 환경 설정
- [ ] SQLite 드라이버 의존성 추가 (`sqlite-jdbc`)
- [ ] Spring Data JPA 의존성 추가
- [ ] Flyway 의존성 추가
- [ ] Hibernate 검증 의존성 추가
- [ ] Jackson JSON 처리 의존성 추가
- [ ] Lombok 의존성 확인 및 유지
- [ ] Spring Security 암호화 의존성 추가

### 1.2 Flyway 마이그레이션 스크립트 작성
- [ ] `V1__Initial_schema.sql`: 기본 테이블 생성 (config_settings, input_adapters, parsers, transforms, output_adapters)
- [ ] `V2__Add_history_and_versions.sql`: 이력 및 버전 테이블 생성 (config_history, configuration_versions)
- [ ] `V3__Add_indexes.sql`: 성능 최적화를 위한 인덱스 생성
- [ ] `V4__Add_constraints.sql`: 제약조건 추가 (FOREIGN KEY, UNIQUE 등)
- [ ] Flyway 자동 마이그레이션 설정 (`spring.flyway.enabled=true`)

### 1.3 JPA Entity 클래스 생성
- [ ] `ConfigSettingsEntity`: 공통 설정 엔티티
  - id, configKey, configValue, dataType, description, createdAt, updatedAt, version
  
- [ ] `InputAdapterEntity`: 입력 어댑터 설정 엔티티
  - id, type, messagetype, host, port, path, topicid, bootstrapservers, groupId, codec, pathPattern
  - bufferSize, timeoutMs, enabled, workerThreads, queueSize, configParams
  - createdAt, updatedAt, version
  
- [ ] `ParserEntity`: 파서 설정 엔티티
  - id, type, messagetype, param, priority, enabled, continueOnFailure
  - createdAt, updatedAt, version
  
- [ ] `TransformEntity`: 변환 설정 엔티티
  - id, type, messagetype, priority, filterPass, filterDrop, addProperties, removeProperties
  - configParams, enabled, createdAt, updatedAt, version
  
- [ ] `OutputAdapterEntity`: 출력 어댑터 설정 엔티티
  - id, type, messagetype, host, port, url, method, headers, topicid, bootstrapservers, key
  - indexTemplate, osUsername, osPassword, action, routingkey, exchange
  - rmqUsername, rmqPassword, rmqPort, tagpass, batchSize, flushIntervalMs, retryCount
  - retryDelayMs, addOriginText, enabled, timeoutMs, createdAt, updatedAt, version
  
- [ ] `ConfigHistoryEntity`: 설정 이력 엔티티
  - id, entityType, entityId, action, oldValues, newValues, changedBy, createdAt
  
- [ ] `ConfigurationVersionEntity`: 설정 버전/스냅샷 엔티티
  - id, versionName, description, inputAdapters, parsers, transforms, outputAdapters
  - commonSettings, status, createdBy, createdAt, activatedAt

### 1.4 Entity 공통 기능 구현
- [ ] `@EntityListeners(AuditingEntityListener.class)` 적용
- [ ] `@EnableJpaAuditing` 설정
- [ ] `@CreatedDate`, `@LastModifiedDate` 필드 적용
- [ ] `@Version` 필드로 낙관적 잠금 구현
- [ ] JSON 변환을 위한 Custom Converter 작성 (`JsonConverter`)
- [ ] 민감 정보 암호화를 위한 Custom Converter 작성 (`CryptoConverter`)

### 1.5 Repository 인터페이스 작성
- [ ] `ConfigSettingsRepository extends JpaRepository<ConfigSettingsEntity, Long>`
  - `findByConfigKey(String key)`
  - `deleteByConfigKey(String key)`
  
- [ ] `InputAdapterRepository extends JpaRepository<InputAdapterEntity, Long>`
  - `findByType(String type)`
  - `findByMessagetype(String messagetype)`
  - `findByEnabledTrue()`
  - 페이지네이션 지원
  
- [ ] `ParserRepository extends JpaRepository<ParserEntity, Long>`
  - `findByType(String type)`
  - `findByMessagetype(String messagetype)`
  - `findByMessagetypeOrderByPriorityAsc(String messagetype)`
  - `findByEnabledTrue()`
  
- [ ] `TransformRepository extends JpaRepository<TransformEntity, Long>`
  - `findByType(String type)`
  - `findByMessagetype(String messagetype)`
  - `findByMessagetypeOrderByPriorityAsc(String messagetype)`
  
- [ ] `OutputAdapterRepository extends JpaRepository<OutputAdapterEntity, Long>`
  - `findByType(String type)`
  - `findByMessagetype(String messagetype)`
  - `findByEnabledTrue()`
  
- [ ] `ConfigHistoryRepository extends JpaRepository<ConfigHistoryEntity, Long>`
  - `findByEntityTypeAndEntityId(String entityType, Long entityId, Sort sort)`
  - `findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Sort sort)`
  - 페이지네이션 지원
  
- [ ] `ConfigurationVersionRepository extends JpaRepository<ConfigurationVersionEntity, Long>`
  - `findByStatusOrderByCreatedAtDesc(String status)`
  - `findByVersionName(String versionName)`

---

## Phase 2: 핵심 Service 계층 구현 (2-3주)

### 2.1 ConfigManagementService 구현
- [ ] **InputAdapter 관리 메서드**
  - `createInputAdapter(CreateInputAdapterRequest): InputAdapterEntity`
  - `updateInputAdapter(Long id, UpdateInputAdapterRequest): InputAdapterEntity`
  - `deleteInputAdapter(Long id): void`
  - `getInputAdapter(Long id): InputAdapterEntity`
  - `getAllInputAdapters(Pageable): Page<InputAdapterEntity>`
  - `getInputAdaptersByType(String type): List<InputAdapterEntity>`
  - `getInputAdapterByMessageType(String messageType): InputAdapterEntity`
  - `enableInputAdapter(Long id): InputAdapterEntity`
  - `disableInputAdapter(Long id): InputAdapterEntity`
  
- [ ] **Parser 관리 메서드**
  - `createParser(CreateParserRequest): ParserEntity`
  - `updateParser(Long id, UpdateParserRequest): ParserEntity`
  - `deleteParser(Long id): void`
  - `getParser(Long id): ParserEntity`
  - `getAllParsers(Pageable): Page<ParserEntity>`
  - `getParsersByType(String type): List<ParserEntity>`
  - `getParsersByMessageType(String messageType): List<ParserEntity>` (우선순위 정렬)
  - `updateParserPriority(Long id, Integer newPriority): ParserEntity`
  
- [ ] **Transform 관리 메서드**
  - `createTransform(CreateTransformRequest): TransformEntity`
  - `updateTransform(Long id, UpdateTransformRequest): TransformEntity`
  - `deleteTransform(Long id): void`
  - `getTransform(Long id): TransformEntity`
  - `getAllTransforms(Pageable): Page<TransformEntity>`
  - `getTransformsByType(String type): List<TransformEntity>`
  - `getTransformsByMessageType(String messageType): List<TransformEntity>` (우선순위 정렬)
  - `updateTransformPriority(Long id, Integer newPriority): TransformEntity`
  
- [ ] **OutputAdapter 관리 메서드**
  - `createOutputAdapter(CreateOutputAdapterRequest): OutputAdapterEntity`
  - `updateOutputAdapter(Long id, UpdateOutputAdapterRequest): OutputAdapterEntity`
  - `deleteOutputAdapter(Long id): void`
  - `getOutputAdapter(Long id): OutputAdapterEntity`
  - `getAllOutputAdapters(Pageable): Page<OutputAdapterEntity>`
  - `getOutputAdaptersByType(String type): List<OutputAdapterEntity>`
  - `getOutputAdaptersByMessageType(String messageType): List<OutputAdapterEntity>`
  - `enableOutputAdapter(Long id): OutputAdapterEntity`
  - `disableOutputAdapter(Long id): OutputAdapterEntity`
  
- [ ] **공통 설정 관리 메서드**
  - `updateCommonSettings(Map<String, Object>): void`
  - `getAllCommonSettings(): Map<String, Object>`
  - `getConfigValue(String key): String`
  - `setConfigValue(String key, Object value, DataType dataType): void`

### 2.2 ConfigValidationService 구현
- [ ] **개별 검증 메서드**
  - `validateInputAdapter(InputAdapterEntity): ValidationResult`
  - `validateParser(ParserEntity): ValidationResult`
  - `validateTransform(TransformEntity): ValidationResult`
  - `validateOutputAdapter(OutputAdapterEntity): ValidationResult`
  
- [ ] **파이프라인 무결성 검증**
  - `validatePipelineIntegrity(): PipelineIntegrityResult`
    - 모든 InputAdapter의 messagetype에 대응하는 Parser 존재 확인
    - 모든 Parser의 messagetype에 대응하는 Transform/Output 존재 확인
    - 모든 OutputAdapter의 messagetype이 Parser에서 생성되는 타입인지 확인
    - 필수 어댑터(최소 1개 Input, 1개 Parser, 1개 Output) 존재 확인
  
- [ ] **검증 오류 조회**
  - `getAllValidationErrors(): List<ValidationError>`
  - `getErrorsByEntity(): Map<String, List<ValidationError>>`
  - `clearValidationErrors(): void`

### 2.3 ConfigHistoryService 구현
- [ ] `recordConfigChange(String entityType, Long entityId, String action, Object oldValues, Object newValues, String changedBy): void`
- [ ] `getHistoryForEntity(String entityType, Long entityId): List<ConfigHistoryEntity>`
- [ ] `getHistoryByDateRange(LocalDateTime from, LocalDateTime to): List<ConfigHistoryEntity>`
- [ ] `getHistoryPage(Pageable pageable): Page<ConfigHistoryEntity>`
- [ ] `getHistoryByAction(String action): List<ConfigHistoryEntity>`
- [ ] `revertToHistory(Long historyId): void` (이전 설정 상태로 복원)
- [ ] `compareHistories(Long historyId1, Long historyId2): ConfigDiff` (두 설정 비교)

### 2.4 ConfigVersionService 구현
- [ ] `createVersion(String versionName, String description, String createdBy): ConfigurationVersionEntity` (스냅샷 생성)
- [ ] `getVersion(Long versionId): ConfigurationVersionEntity`
- [ ] `activateVersion(Long versionId): void` (버전 활성화/복원)
- [ ] `listVersions(): List<ConfigurationVersionEntity>`
- [ ] `listVersionsByStatus(String status): List<ConfigurationVersionEntity>`
- [ ] `deleteVersion(Long versionId): void`
- [ ] `exportVersionAsYaml(Long versionId): String`
- [ ] `exportVersionAsJson(Long versionId): String`
- [ ] `compareVersions(Long versionId1, Long versionId2): ConfigDiff`

### 2.5 PipelineReloadService 구현
- [ ] `reloadConfiguration(): void` (DB에서 최신 설정 로드)
- [ ] `validateAndReload(): void` (검증 후 안전한 재로드)
- [ ] `restartPipeline(): void` (파이프라인 재시작)
- [ ] `isReloadInProgress(): boolean`
- [ ] `getPipelineStatus(): PipelineStatus`
- [ ] `getReloadProgress(): ReloadProgress`
- [ ] `cancelReload(): void`
- [ ] ApplicationContext 접근을 통한 런타임 빈 업데이트

### 2.6 ConfigExportService 구현
- [ ] `exportCurrentConfigAsYaml(): String`
- [ ] `exportCurrentConfigAsJson(): String`
- [ ] `importFromYaml(String yamlContent, boolean overwrite): void`
- [ ] `importFromJson(String jsonContent, boolean overwrite): void`
- [ ] `importFromFile(MultipartFile file, boolean overwrite): void`
- [ ] YAML/JSON 파싱 및 유효성 검사

### 2.7 ConfigMetadataService 구현
- [ ] `getInputAdapterTypes(): List<AdapterTypeInfo>`
- [ ] `getOutputAdapterTypes(): List<AdapterTypeInfo>`
- [ ] `getParserTypes(): List<AdapterTypeInfo>`
- [ ] `getTransformTypes(): List<TransformTypeInfo>`
- [ ] `getInputAdapterSchema(String type): AdapterSchema`
- [ ] `getOutputAdapterSchema(String type): AdapterSchema`
- [ ] `getParserSchema(String type): AdapterSchema`
- [ ] `getTransformSchema(String type): TransformSchema`
- [ ] `getSupportedCodecs(): List<String>`
- [ ] `getSupportedHttpMethods(): List<String>`

---

## Phase 3: REST API Controller 구현 (2주)

### 3.1 InputAdapterController
- [ ] `GET /api/v1/input-adapters` - 모든 입력 어댑터 조회 (페이지네이션)
- [ ] `POST /api/v1/input-adapters` - 새로운 입력 어댑터 생성
- [ ] `GET /api/v1/input-adapters/{id}` - 특정 입력 어댑터 조회
- [ ] `PUT /api/v1/input-adapters/{id}` - 입력 어댑터 수정
- [ ] `DELETE /api/v1/input-adapters/{id}` - 입력 어댑터 삭제
- [ ] `GET /api/v1/input-adapters/type/{type}` - 타입별 입력 어댑터 조회
- [ ] `GET /api/v1/input-adapters/messagetype/{messageType}` - 메시지 타입별 조회
- [ ] `PATCH /api/v1/input-adapters/{id}/enable` - 활성화
- [ ] `PATCH /api/v1/input-adapters/{id}/disable` - 비활성화

### 3.2 ParserController
- [ ] `GET /api/v1/parsers` - 모든 파서 조회
- [ ] `POST /api/v1/parsers` - 새로운 파서 생성
- [ ] `GET /api/v1/parsers/{id}` - 특정 파서 조회
- [ ] `PUT /api/v1/parsers/{id}` - 파서 수정
- [ ] `DELETE /api/v1/parsers/{id}` - 파서 삭제
- [ ] `GET /api/v1/parsers/type/{type}` - 타입별 파서 조회
- [ ] `GET /api/v1/parsers/messagetype/{messageType}` - 메시지 타입별 조회
- [ ] `PATCH /api/v1/parsers/{id}/priority` - 우선순위 변경
- [ ] `POST /api/v1/parsers/validate` - 파서 검증 (미리보기)

### 3.3 TransformController
- [ ] `GET /api/v1/transforms` - 모든 변환 설정 조회
- [ ] `POST /api/v1/transforms` - 새로운 변환 설정 생성
- [ ] `GET /api/v1/transforms/{id}` - 특정 변환 설정 조회
- [ ] `PUT /api/v1/transforms/{id}` - 변환 설정 수정
- [ ] `DELETE /api/v1/transforms/{id}` - 변환 설정 삭제
- [ ] `GET /api/v1/transforms/type/{type}` - 타입별 변환 설정 조회
- [ ] `GET /api/v1/transforms/messagetype/{messageType}` - 메시지 타입별 조회
- [ ] `PATCH /api/v1/transforms/{id}/priority` - 순서 변경

### 3.4 OutputAdapterController
- [ ] `GET /api/v1/output-adapters` - 모든 출력 어댑터 조회
- [ ] `POST /api/v1/output-adapters` - 새로운 출력 어댑터 생성
- [ ] `GET /api/v1/output-adapters/{id}` - 특정 출력 어댑터 조회
- [ ] `PUT /api/v1/output-adapters/{id}` - 출력 어댑터 수정
- [ ] `DELETE /api/v1/output-adapters/{id}` - 출력 어댑터 삭제
- [ ] `GET /api/v1/output-adapters/type/{type}` - 타입별 조회
- [ ] `GET /api/v1/output-adapters/messagetype/{messageType}` - 메시지 타입별 조회
- [ ] `PATCH /api/v1/output-adapters/{id}/enable` - 활성화
- [ ] `PATCH /api/v1/output-adapters/{id}/disable` - 비활성화

### 3.5 ConfigSettingsController
- [ ] `GET /api/v1/settings` - 모든 공통 설정 조회
- [ ] `PUT /api/v1/settings` - 공통 설정 일괄 업데이트
- [ ] `GET /api/v1/settings/{key}` - 특정 설정값 조회
- [ ] `PUT /api/v1/settings/{key}` - 특정 설정값 업데이트

### 3.6 ValidationController
- [ ] `GET /api/v1/validate/pipeline` - 전체 파이프라인 검증
- [ ] `POST /api/v1/validate/input` - 입력 어댑터 검증 (미리보기)
- [ ] `POST /api/v1/validate/parser` - 파서 검증
- [ ] `POST /api/v1/validate/transform` - 변환 검증
- [ ] `POST /api/v1/validate/output` - 출력 어댑터 검증
- [ ] `GET /api/v1/validate/errors` - 현재 모든 검증 오류 조회

### 3.7 ConfigHistoryController
- [ ] `GET /api/v1/history` - 모든 이력 조회 (페이지네이션)
- [ ] `GET /api/v1/history/entity/{entityType}/{entityId}` - 엔티티별 이력 조회
- [ ] `GET /api/v1/history/date-range` - 날짜 범위로 조회
- [ ] `POST /api/v1/history/revert/{historyId}` - 이전 상태로 복원
- [ ] `GET /api/v1/history/diff/{id1}/{id2}` - 두 설정 비교

### 3.8 ConfigVersionController
- [ ] `GET /api/v1/versions` - 모든 버전 조회
- [ ] `POST /api/v1/versions` - 현재 설정 버전 생성
- [ ] `GET /api/v1/versions/{id}` - 특정 버전 조회
- [ ] `PUT /api/v1/versions/{id}/activate` - 버전 활성화
- [ ] `DELETE /api/v1/versions/{id}` - 버전 삭제
- [ ] `GET /api/v1/versions/{id}/export/yaml` - YAML 내보내기
- [ ] `GET /api/v1/versions/{id}/export/json` - JSON 내보내기
- [ ] `GET /api/v1/versions/diff/{id1}/{id2}` - 버전 비교

### 3.9 PipelineController
- [ ] `GET /api/v1/pipeline/status` - 파이프라인 상태 조회
- [ ] `POST /api/v1/pipeline/reload` - 설정 재로드
- [ ] `POST /api/v1/pipeline/restart` - 파이프라인 재시작
- [ ] `GET /api/v1/pipeline/reload-status` - 재로드 진행 상황 조회
- [ ] `POST /api/v1/pipeline/validate-and-reload` - 검증 후 재로드

### 3.10 ConfigImportExportController
- [ ] `POST /api/v1/config/import/yaml` - YAML 임포트
- [ ] `POST /api/v1/config/import/json` - JSON 임포트
- [ ] `GET /api/v1/config/export/yaml` - YAML 내보내기
- [ ] `GET /api/v1/config/export/json` - JSON 내보내기

### 3.11 MetadataController
- [ ] `GET /api/v1/metadata/adapter-types` - 어댑터 타입 목록
- [ ] `GET /api/v1/metadata/parser-types` - 파서 타입 목록
- [ ] `GET /api/v1/metadata/transform-types` - 변환 타입 목록
- [ ] `GET /api/v1/metadata/input-adapter-schema/{type}` - 입력 어댑터 스키마
- [ ] `GET /api/v1/metadata/output-adapter-schema/{type}` - 출력 어댑터 스키마
- [ ] `GET /api/v1/metadata/parser-schema/{type}` - 파서 스키마
- [ ] `GET /api/v1/metadata/transform-schema/{type}` - 변환 스키마

---

## Phase 4: DTO 및 요청/응답 객체 구현 (1주)

### 4.1 InputAdapter 관련 DTO
- [ ] `InputAdapterDTO` - 응답 DTO
- [ ] `CreateInputAdapterRequest` - 생성 요청 DTO
- [ ] `UpdateInputAdapterRequest` - 수정 요청 DTO

### 4.2 Parser 관련 DTO
- [ ] `ParserDTO` - 응답 DTO
- [ ] `CreateParserRequest` - 생성 요청 DTO
- [ ] `UpdateParserRequest` - 수정 요청 DTO
- [ ] `ParserValidationRequest` - 검증용 요청 DTO

### 4.3 Transform 관련 DTO
- [ ] `TransformDTO` - 응답 DTO
- [ ] `CreateTransformRequest` - 생성 요청 DTO
- [ ] `UpdateTransformRequest` - 수정 요청 DTO

### 4.4 OutputAdapter 관련 DTO
- [ ] `OutputAdapterDTO` - 응답 DTO
- [ ] `CreateOutputAdapterRequest` - 생성 요청 DTO
- [ ] `UpdateOutputAdapterRequest` - 수정 요청 DTO

### 4.5 ConfigSettings 관련 DTO
- [ ] `ConfigSettingsDTO` - 응답 DTO
- [ ] `UpdateConfigSettingRequest` - 수정 요청 DTO

### 4.6 ConfigHistory 관련 DTO
- [ ] `ConfigHistoryDTO` - 응답 DTO
- [ ] `RevertHistoryRequest` - 복원 요청 DTO
- [ ] `ConfigDiffDTO` - 설정 비교 결과 DTO

### 4.7 ConfigurationVersion 관련 DTO
- [ ] `ConfigurationVersionDTO` - 응답 DTO
- [ ] `CreateVersionRequest` - 버전 생성 요청 DTO
- [ ] `ActivateVersionRequest` - 버전 활성화 요청 DTO

### 4.8 파이프라인 관련 DTO
- [ ] `PipelineStatusDTO` - 파이프라인 상태 DTO
- [ ] `PipelineReloadRequest` - 재로드 요청 DTO
- [ ] `PipelineRestartRequest` - 재시작 요청 DTO
- [ ] `ReloadProgressDTO` - 재로드 진행 상황 DTO

### 4.9 검증 관련 DTO
- [ ] `ValidationErrorDTO` - 검증 오류 DTO
- [ ] `ValidationResultDTO` - 검증 결과 DTO
- [ ] `PipelineValidationResultDTO` - 파이프라인 검증 결과 DTO
- [ ] `PipelineIntegrityResultDTO` - 파이프라인 무결성 검증 결과 DTO

### 4.10 메타데이터 관련 DTO
- [ ] `AdapterTypeInfoDTO` - 어댑터 타입 정보 DTO
- [ ] `ParserTypeInfoDTO` - 파서 타입 정보 DTO
- [ ] `TransformTypeInfoDTO` - 변환 타입 정보 DTO
- [ ] `SchemaDTO` - 스키마 DTO (어댑터/파서/변환 공통)
- [ ] `FieldSchemaDTO` - 필드 스키마 정보 DTO

### 4.11 임포트/내보내기 관련 DTO
- [ ] `ImportResultDTO` - 임포트 결과 DTO
- [ ] `ExportResultDTO` - 내보내기 결과 DTO

### 4.12 공통 응답 DTO
- [ ] `ErrorResponseDTO` - 에러 응답 DTO
- [ ] `SuccessResponseDTO` - 성공 응답 DTO (generic)
- [ ] `ApiResponseDTO<T>` - 범용 API 응답 DTO

### 4.13 DTO Mapper 구현
- [ ] MapStruct 또는 ModelMapper를 사용한 변환 로직 구현
- [ ] Entity ↔ DTO 양방향 변환
- [ ] 복잡한 nested 객체 변환 처리

---

## Phase 5: 예외 처리 및 에러 핸들링 (1주)

### 5.1 Custom Exception 클래스
- [ ] `ConfigNotFoundException` - 설정을 찾을 수 없음
- [ ] `ConfigValidationException` - 설정 검증 실패
- [ ] `DuplicateConfigException` - 중복 설정
- [ ] `PipelineIntegrityException` - 파이프라인 무결성 위반
- [ ] `ConfigConflictException` - 설정 충돌
- [ ] `VersionNotFoundException` - 버전을 찾을 수 없음
- [ ] `InvalidAdapterTypeException` - 잘못된 어댑터 타입
- [ ] `OptimisticLockException` - 낙관적 잠금 실패 (동시 수정)
- [ ] `ImportException` - 임포트 실패
- [ ] `PipelineRestartException` - 파이프라인 재시작 실패

### 5.2 Global Exception Handler
- [ ] `@RestControllerAdvice` 클래스 작성
- [ ] `handleConfigNotFound()` - 404
- [ ] `handleValidationException()` - 400
- [ ] `handleDuplicateConfig()` - 409
- [ ] `handleOptimisticLock()` - 409
- [ ] `handleDataIntegrityViolation()` - 400
- [ ] `handleInternalServerError()` - 500
- [ ] `handleMethodArgumentNotValid()` - 400 (Bean Validation)
- [ ] `handleHttpMessageNotReadable()` - 400 (JSON 파싱 오류)

### 5.3 에러 응답 포맷 표준화
- [ ] 표준 에러 응답 구조 정의
- [ ] 에러 코드 매핑 (ERROR_CODE enum)
- [ ] 상세 에러 메시지 작성
- [ ] 에러 필드별 상세 정보 포함

---

## Phase 6: 마이그레이션 및 초기화 기능 (1주)

### 6.1 YAML → DB 자동 임포트
- [ ] 애플리케이션 시작 시 `config/config.yaml` 감지
- [ ] 자동 또는 수동 임포트 플래그 설정
- [ ] YAML 파싱 및 검증
- [ ] DB 트랜잭션 처리
- [ ] 임포트 이력 기록
- [ ] 백업 파일 생성 (옵션)

### 6.2 마이그레이션 설정
- [ ] `application.yml` 또는 `application-db.yml` 설정 파일 작성
  - `logparser.config-source: DATABASE`
  - `logparser.migration.enabled: true`
  - `logparser.migration.auto-import-yaml: true`
  - `logparser.migration.yaml-file-path: ./config/config.yaml`

### 6.3 ApplicationProperties 수정
- [ ] DB 기반 설정 로드로 변경
- [ ] 기존 YAML 바인딩 유지 (호환성)
- [ ] 초기화 로직 수정

### 6.4 Flyway 설정
- [ ] `spring.flyway.enabled: true`
- [ ] `spring.flyway.baseline-on-migrate: true`
- [ ] `spring.jpa.hibernate.ddl-auto: validate` (프로덕션)
- [ ] `spring.jpa.show-sql: false` (프로덕션)

---

## Phase 7: 설정 이력 및 버전 관리 기능 (1주)

### 7.1 설정 변경 감시 및 기록
- [ ] AOP를 사용한 자동 이력 기록
  - InputAdapter 생성/수정/삭제 시
  - Parser 생성/수정/삭제 시
  - Transform 생성/수정/삭제 시
  - OutputAdapter 생성/수정/삭제 시
  - 공통 설정 수정 시
  
- [ ] 이력 정보: entityType, entityId, action, oldValues, newValues, changedBy, createdAt

### 7.2 이력 기반 복원 기능
- [ ] `revertToHistory(Long historyId)` 구현
- [ ] 복원 전 검증
- [ ] 트랜잭션 처리
- [ ] 복원 이력 기록

### 7.3 설정 버전 스냅샷
- [ ] `createVersion()` - 현재 DB 설정 전체를 JSON으로 저장
- [ ] `activateVersion()` - 특정 버전을 현재 설정으로 복원
- [ ] 버전 상태 관리: DRAFT, ACTIVE, ARCHIVED
- [ ] 버전 메타데이터: versionName, description, createdBy, createdAt, activatedAt

### 7.4 설정 비교 기능
- [ ] `compareHistories()` - 두 이력 비교
- [ ] `compareVersions()` - 두 버전 비교
- [ ] 변경된 항목 상세 표시
- [ ] 추가/수정/삭제 표시

---

## Phase 8: 파이프라인 재로드 기능 (1.5주)

### 8.1 설정 재로드 메커니즘
- [ ] `reloadConfiguration()` 구현
  - DB에서 최신 설정 로드
  - 메모리 캐시 갱신
  - 파이프라인 컴포넌트 업데이트
  - 실패 시 기존 설정 유지
  
- [ ] `validateAndReload()` 구현
  - 파이프라인 무결성 검증 먼저 수행
  - 검증 실패 시 예외 발생
  - 검증 성공 시 재로드 실행

### 8.2 파이프라인 재시작
- [ ] `restartPipeline()` 구현
  - 현재 큐의 메시지 처리 완료 대기
  - 새로운 입력 차단
  - Input/Parser/Transform/Output 컴포넌트 재시작
  - 재시작 상태 모니터링

### 8.3 재로드 진행 상황 모니터링
- [ ] `isReloadInProgress()` - 재로드 중인지 확인
- [ ] `getReloadProgress()` - 재로드 진행 상황 조회
- [ ] `getPipelineStatus()` - 파이프라인 상태 조회
  - RUNNING, STOPPED, RELOADING
  - Input/Parser/Transform/Output 개수
  - 큐 크기, 처리된 메시지 수 등
  
- [ ] `cancelReload()` - 진행 중인 재로드 취소

### 8.4 ApplicationContext 연동
- [ ] Spring 빈 라이프사이클 이해 및 활용
- [ ] 런타임 빈 업데이트 (재시작 없이)
- [ ] 설정 변경 감시 (PropertyChangeListener 등)
- [ ] 이벤트 기반 재로드 트리거

---

## Phase 9: 캐싱 및 성능 최적화 (1주)

### 9.1 캐싱 전략 구현
- [ ] Spring Cache 설정
- [ ] `@Cacheable` 적용
  - 메타데이터 캐싱 (어댑터/파서/변환 타입 정보)
  - 자주 조회하는 설정 캐싱
  
- [ ] `@CacheEvict` 적용
  - 설정 변경 시 캐시 무효화
  
- [ ] `@CachePut` 적용
  - 캐시와 DB 동시 업데이트

### 9.2 데이터베이스 쿼리 최적화
- [ ] N+1 쿼리 문제 해결
  - `@EntityGraph` 사용
  - `fetch join` 사용
  
- [ ] 페이지네이션 적용
  - 이력 조회
  - 버전 조회
  - 설정 조회
  
- [ ] 인덱스 활용 확인
  - type, messagetype, enabled, priority 컬럼 인덱스
  - 쿼리 계획 분석

### 9.3 API 응답 최적화
- [ ] DTO 프로젝션 (필요한 필드만 조회)
- [ ] 지연 로딩 설정
- [ ] JSON 직렬화 최적화

---

## Phase 10: 테스트 작성 (2주)

### 10.1 단위 테스트
- [ ] `ConfigManagementServiceTest`
  - InputAdapter, Parser, Transform, OutputAdapter CRUD 테스트
  - 공통 설정 관리 테스트
  
- [ ] `ConfigValidationServiceTest`
  - 개별 엔티티 검증 테스트
  - 파이프라인 무결성 검증 테스트
  
- [ ] `ConfigHistoryServiceTest`
  - 이력 기록 테스트
  - 이력 복원 테스트
  
- [ ] `ConfigVersionServiceTest`
  - 버전 생성 테스트
  - 버전 활성화 테스트
  
- [ ] `ConfigExportServiceTest`
  - YAML/JSON 내보내기 테스트
  - YAML/JSON 임포트 테스트
  
- [ ] `PipelineReloadServiceTest`
  - 재로드 기능 테스트
  - 상태 조회 테스트

### 10.2 통합 테스트
- [ ] `InputAdapterControllerTest` (@WebMvcTest)
- [ ] `ParserControllerTest`
- [ ] `TransformControllerTest`
- [ ] `OutputAdapterControllerTest`
- [ ] `ConfigSettingsControllerTest`
- [ ] `ValidationControllerTest`
- [ ] `ConfigHistoryControllerTest`
- [ ] `ConfigVersionControllerTest`
- [ ] `PipelineControllerTest`
- [ ] `ConfigImportExportControllerTest`
- [ ] `MetadataControllerTest`

### 10.3 E2E 테스트 (@SpringBootTest)
- [ ] YAML 임포트 → DB 저장 → API 조회 전체 흐름
- [ ] 설정 변경 → 이력 기록 → 이력 복원 흐름
- [ ] 버전 생성 → 버전 활성화 → 설정 확인 흐름
- [ ] 파이프라인 재로드 기능 테스트
- [ ] 동시성 테스트 (낙관적 잠금)

### 10.4 성능 테스트
- [ ] 대량 데이터 조회 성능 테스트
- [ ] 캐시 효율성 테스트
- [ ] 데이터베이스 쿼리 성능 테스트

---

## Phase 11: API 문서화 (1주)

### 11.1 Swagger/OpenAPI 통합
- [ ] Springdoc OpenAPI 의존성 추가
- [ ] `@OpenAPIDefinition` 설정
- [ ] `@Tag` 및 `@Operation` 어노테이션 적용
- [ ] `@Parameter`, `@RequestBody`, `@ApiResponse` 정의

### 11.2 API 엔드포인트별 문서화
- [ ] 각 Controller 클래스에 `@Tag` 추가
- [ ] 각 메서드에 `@Operation` 추가
- [ ] 요청/응답 예시 작성
- [ ] 에러 응답 정의

### 11.3 Swagger UI 활성화
- [ ] `/swagger-ui.html` 제공
- [ ] `/v3/api-docs` JSON 스키마 제공
- [ ] 테스트 가능한 UI 제공

---

## Phase 12: 보안 강화 (1주)

### 12.1 민감 데이터 보호
- [ ] 비밀번호 암호화 저장
  - `CryptoConverter` 구현
  - Spring Security의 `PasswordEncoder` 사용
  
- [ ] API 응답에서 민감 정보 제외
  - `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` 사용
  - 민감한 필드 제외
  
- [ ] 데이터베이스 암호화 (선택)
  - SQLCipher 고려

### 12.2 입력 검증
- [ ] Jakarta Bean Validation 어노테이션 적용
  - `@NotNull`, `@NotBlank`, `@Size`, `@Pattern`, `@Range` 등
  
- [ ] Custom Validator 작성
  - 어댑터 타입별 필드 검증
  - 설정값 범위 검증

### 12.3 API 인증/인가 (선택사항)
- [ ] API 키 또는 토큰 기반 인증 구현
- [ ] 역할 기반 권한 관리
- [ ] 감사 로깅

---

## Phase 13: 배포 준비 (1주)

### 13.1 Docker 컨테이너화
- [ ] Dockerfile 수정 (SQLite 지원)
- [ ] docker-compose.yml 수정 (필요시)
- [ ] 볼륨 마운트 설정 (DB 파일 영속성)

### 13.2 데이터베이스 백업 및 복구
- [ ] 자동 백업 스크립트 작성
- [ ] 복구 절차 문서화
- [ ] 마이그레이션 롤백 계획

### 13.3 배포 체크리스트
- [ ] SQLite 데이터베이스 경로 설정
- [ ] Flyway 마이그레이션 자동 실행 확인
- [ ] 환경별 설정 파일 준비 (dev, staging, prod)
- [ ] 로깅 설정
- [ ] 모니터링 메트릭 설정

---

## Phase 14: 추가 기능 (선택사항)

### 14.1 웹 UI 대시보드
- [ ] React/Vue.js를 사용한 설정 관리 UI
- [ ] 실시간 파이프라인 상태 모니터링
- [ ] 설정 변경 이력 시각화
- [ ] 설정 템플릿 관리

### 14.2 설정 템플릿 기능
- [ ] 자주 사용하는 설정 조합을 템플릿으로 저장
- [ ] 템플릿 기반 빠른 설정 생성
- [ ] 커뮤니티 템플릿 공유

### 14.3 고급 검색 및 필터링
- [ ] 복잡한 쿼리 지원
- [ ] 커스텀 필터 작성 기능
- [ ] 저장된 필터 관리

### 14.4 설정 비교 시각화
- [ ] 두 설정의 변경사항 시각적 표시
- [ ] Diff 뷰
- [ ] 변경 전후 비교 UI

---

## 구현 체크리스트

### 데이터베이스 (Phase 1)
- [ ] SQLite 드라이버 및 의존성 설치
- [ ] Flyway 마이그레이션 스크립트 작성 (V1-V4)
- [ ] Entity 클래스 정의 (7개)
- [ ] Repository 인터페이스 작성 (7개)

### 서비스 계층 (Phase 2)
- [ ] ConfigManagementService (40+ 메서드)
- [ ] ConfigValidationService (10+ 메서드)
- [ ] ConfigHistoryService (8+ 메서드)
- [ ] ConfigVersionService (10+ 메서드)
- [ ] PipelineReloadService (7+ 메서드)
- [ ] ConfigExportService (5+ 메서드)
- [ ] ConfigMetadataService (10+ 메서드)

### REST API (Phase 3)
- [ ] 11개 Controller 클래스
- [ ] 80+ API 엔드포인트

### DTO (Phase 4)
- [ ] 50+ DTO/Request/Response 클래스

### 예외 처리 (Phase 5)
- [ ] 10개 Custom Exception 클래스
- [ ] 1개 Global Exception Handler

### 마이그레이션 (Phase 6)
- [ ] YAML → DB 임포트 기능
- [ ] 애플리케이션 초기화 로직 수정

### 고급 기능 (Phase 7-9)
- [ ] 설정 이력 및 버전 관리
- [ ] 파이프라인 재로드
- [ ] 캐싱 및 성능 최적화

### 테스트 (Phase 10)
- [ ] 30+ 단위 테스트
- [ ] 11개 통합 테스트
- [ ] 5+ E2E 테스트

### 문서화 및 배포 (Phase 11-13)
- [ ] Swagger/OpenAPI 통합
- [ ] 보안 강화
- [ ] 배포 준비

---

## 예상 소요 시간

| Phase | 작업 | 예상 시간 |
|-------|------|----------|
| 1 | DB 및 Entity | 1-2주 |
| 2 | Service 계층 | 2-3주 |
| 3 | REST API | 2주 |
| 4 | DTO | 1주 |
| 5 | 예외 처리 | 1주 |
| 6 | 마이그레이션 | 1주 |
| 7 | 이력/버전 관리 | 1주 |
| 8 | 파이프라인 재로드 | 1.5주 |
| 9 | 캐싱/성능 | 1주 |
| 10 | 테스트 | 2주 |
| 11 | 문서화 | 1주 |
| 12 | 보안 | 1주 |
| 13 | 배포 준비 | 1주 |
| **합계** | | **18-20주** |

---

## 구현 우선순위

### 1차 (필수)
- Phase 1: 데이터베이스 및 Entity
- Phase 2: 핵심 Service 계층
- Phase 3: 기본 REST API
- Phase 6: YAML → DB 마이그레이션

### 2차 (중요)
- Phase 4: DTO
- Phase 5: 예외 처리
- Phase 7: 이력/버전 관리
- Phase 8: 파이프라인 재로드

### 3차 (성능 및 안정성)
- Phase 9: 캐싱/성능 최적화
- Phase 10: 테스트
- Phase 12: 보안 강화

### 4차 (운영 및 확장)
- Phase 11: 문서화
- Phase 13: 배포 준비
- Phase 14: 추가 기능

