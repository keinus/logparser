# Transform Implementation Task List

본 문서는 `transform.md`의 논리적 스키마 변환 및 매핑 기능을 구현하기 위한 작업 목록입니다.
RDB 종속성을 제거하고, 유연한 Map 기반의 데이터 처리를 지향합니다.

## Phase 1: Core Domain & Infrastructure

### Task 1.1: Logical Domain Modeling (Java)
- [ ] **Base Domain Object:** `StructuredEvent` 클래스 구현.
    - [ ] `CommonFields` (event_id, event_time, src_ip 등).
    - [ ] `SubFields` (Map<String, Object> or Specific Sub-classes).
    - [ ] `AdditionalAttributes` (Map<String, Object> for unmapped fields).
- [ ] **Field Deprecation:** `transform.md`에서 `(deprecated)`로 지정된 필드 제외 또는 별도 관리.

### Task 1.2: Utility Services
- [ ] **ID Generator:** `SnowflakeIdGenerator` 구현 (Long type UUID).
- [ ] **Type Converters:**
    - [ ] `TimestampParser`: 다양한 포맷 -> `java.time.Instant`.
    - [ ] `IpValidator`: 유효성 검증 및 표준 포맷 변환.
    - [ ] `NumberParser`: Safe casting (String -> Long/Int).

---

## Phase 2: Transformation Engine

### Task 2.1: Configuration Model
- [ ] **Mapping Configuration:** 매핑 규칙을 저장할 내부 모델 설계.
    - [ ] `ConditionRule` (e.g., `dst_port == 80` -> `event_web`).
    - [ ] `FieldMapping` (Source Field -> Target Logical Field).
- [ ] **Persistence:** SQLite에 매핑 설정(JSON)을 저장/로딩하는 Repository 구현.

### Task 2.2: Transformation Logic (`StructuredTransformService`)
- [ ] **Condition Evaluator:** 조건식 파싱 및 평가 로직.
- [ ] **Mapping Engine:**
    - [ ] **Common Field Mapping:** 공통 필드 매핑 및 타입 변환.
    - [ ] **Sub-Domain Mapping:** 조건에 따른 서브 도메인 필드 매핑.
    - [ ] **Deprecation Handling:** Deprecated 필드 무시 또는 `raw_log` 보존.
    - [ ] **Unmapped Handling:** 나머지 필드를 `additional_attributes`로 이동.

---

## Phase 3: API Layer

### Task 3.1: Management API
- [ ] `GET /api/transform/schema`: 논리적 타겟 스키마 메타데이터 반환.
- [ ] `GET /api/transform/mapping`: 매핑 설정 조회.
- [ ] `POST /api/transform/mapping`: 매핑 설정 저장/수정.

### Task 3.2: Simulation API
- [ ] `POST /api/transform/simulate`: 샘플 로그 입력 시 변환 결과 미리보기.
    - [ ] 성공 결과(JSON).
    - [ ] 타입 변환 오류 리포트.

---

## Phase 4: UI Implementation

### Task 4.1: Layout & Interaction
- [ ] **Mapping UI:** `ui_prototype.html` 기반의 매핑 인터페이스 구현.
    - [ ] Common/Sub-Schema 분리 표시.
    - [ ] Custom Field 입력 기능.
    - [ ] Auto-Map (Name Similarity) 기능.
- [ ] **Condition Builder:** 서브 도메인 결정을 위한 조건식 입력 UI.

---

## Phase 5: Integration

### Task 5.1: Output Adapter Compatibility
- [ ] **Output Format:** 변환된 `StructuredEvent`를 `Map<String, Object>` 형태로 직렬화하여 기존 Output Adapter(Elasticsearch, Kafka 등)에 전달.
- [ ] **Verification:** 실제 로그 인입 -> 변환 -> Output 전송 테스트.
