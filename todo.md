# TODO

## 목적

리포지토리 전체 분석에서 확인된 런타임 버그, 리로드 안정성 문제, 설정/구현 불일치, 미사용 코드를 정리하고 **코드 커버리지 100%**를 달성한다.

## 테스트 커버리지 100% 달성 계획 (현재 진행 중)

### 1. 완료된 테스트
- **Domain 레이어**:
  - Parse Models: Json, Regex, Grok, RFC3164, RFC5424
  - Input Adapters: Tcp, Udp, Http, Kafka, File, Fake
  - Output Adapters: Http, Tcp, Console, Benchmark, OpenSearch, RabbitMQ
  - Configuration: 설정 모델 및 서비스
  - Transformation: AddProperty, RemoveProperty, Structure 등

### 2. 진행 중인 테스트 (Phase 2 & 3)
- **Infrastructure 레이어**:
  - Repositories: ParserRepository, TransformRepository, MappingRepository, ConfigSettingsRepository
  - Configuration: ThreadManagerBean, JpaConfig, WebSocketConfig, DatabaseConfig
- **Application 레이어**:
  - MessageDispatcher, PipelineConfigEventListener, ThreadMonitoringService
  - 리팩토링: OutputAdapterComponent, PipelineReloadService, ProcessingDispatcher

### 3. 향후 작업 예정 (Phase 4)
- **Interface 레이어**:
  - Controllers: 11개 컨트롤러 전체 (MockMvc 사용)
  - Exception Handling: GlobalExceptionHandler 커버리지 100%
  - WebSockets: LiveTailHandler
  - DTOs: 전체 DTO 검증

---

## 우선순위 높은 수정 항목 (로직/버그)

### 1. TransformService 로딩 버그 수정
- `TransformService`의 reflection 경로를 실제 패키지로 수정

### 2. Pipeline reload 중 메시지 유실 방지
- reload 순서 재설계 (입력 중단 -> drain -> 교체)

### 3. `/api/v1/pipeline/restart` 실제 동작 구현
- 재시작 로직 구현 또는 API 비활성화

### 4. Structured mapping 저장 후 캐시 무효화
- mapping 저장 직후 캐시 정리

### 5. Parser priority / continueOnFailure 실제 반영
- 런타임 실행 로직에 설정 반영

### 6. Output adapter 설정값과 실제 구현 정합성 맞추기
- HttpOutputAdapter(method, headers, https 등) 정책 강화

### 7. Global output adapter 정책 일치시키기
- 정책 결정 및 계층별 일관된 수정

## 코드 정리 작업

### 8. 미사용 DTO / Request 모델 정리
- 사용되지 않는 DTO 식별 및 제거/적용

### 9. 미사용 유틸 / 주석 코드 정리
- 죽은 코드 및 주석 덩어리 정리

### 10. `Structure` transform 경로 정리
- 중복 경로 해소

## 참고
- 테스트 커버리지 100% 달성 후 위 핵심 버그/구현 이슈를 순차적으로 해결할 예정.

## 편집 컨텍스트 기록
- **최근 작업 파일**: `/home/keinus/logparser/todo.md` (2026-04-21)
