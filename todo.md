# TODO

## 목적

리포지토리 전체 분석에서 확인된 런타임 버그, 리로드 안정성 문제, 설정/구현 불일치, 미사용 코드를 정리한다.

## 우선순위 높은 수정 항목

### 1. TransformService 로딩 버그 수정

- 문제: `TransformService`가 존재하지 않는 패키지(`org.keinus.logparser.domain.transform.model`)에서 transform 클래스를 로드하고 있음
- 영향: `Filter`, `AddProperty`, `RemoveProperty`, `Structure`가 런타임에 등록되지 않을 수 있음
- 작업:
  - `TransformService`의 reflection 경로를 실제 패키지로 수정
  - 애플리케이션 시작 시 transform 등록 로그가 정상 출력되는지 확인
  - `reload()` 경로도 동일하게 검증
- 완료 조건:
  - transform 설정이 실제로 로드됨
  - 관련 회귀 테스트 추가

### 2. Pipeline reload 중 메시지 유실 방지

- 문제: 리로드 중 output adapter를 먼저 내려 큐에 남은 메시지가 드롭될 수 있음
- 영향: `/api/v1/pipeline/reload` 실행 시 처리 중 메시지 유실 가능
- 작업:
  - reload 순서를 재설계
  - 입력 중단 -> 큐 drain 또는 안전한 전환 -> output 교체 순으로 변경
  - reload 실패 시 이전 파이프라인 상태로 복구하는 fallback 설계
  - 리로드 중 상태값과 progress 동작도 실제 상태와 일치시키기
- 완료 조건:
  - reload 중 큐 적재 메시지가 유실되지 않음
  - reload 실패 후에도 파이프라인이 완전히 죽은 상태로 남지 않음
  - 회귀 테스트 추가

### 3. `/api/v1/pipeline/restart` 실제 동작 구현 또는 비활성화

- 문제: restart API가 성공 응답을 주지만 실제 stop/reload/start를 수행하지 않음
- 영향: 운영자가 재시작되었다고 오해할 수 있음
- 작업:
  - `restartPipeline()`에 실제 재시작 로직 구현
  - 또는 미구현 상태라면 API를 비활성화하고 명확한 오류 응답 반환
- 완료 조건:
  - restart API가 실제로 재시작하거나, 미지원임을 명확히 반환함
  - 관련 테스트 추가

### 4. Structured mapping 저장 후 캐시 무효화

- 문제: `/api/v1/structure/mapping` 저장 후 `StructuredTransformService` 캐시가 유지됨
- 영향: 새 매핑이 즉시 반영되지 않음
- 작업:
  - mapping 저장 직후 해당 message type 캐시 또는 전체 캐시 무효화
  - 필요 시 condition cache도 같이 정리
- 완료 조건:
  - mapping 저장 후 다음 이벤트부터 새 규칙이 반영됨
  - 회귀 테스트 추가

## 동작 불일치 수정

### 5. Parser priority / continueOnFailure 실제 반영

- 문제: priority와 `continueOnFailure`가 모델/DB에는 있지만 런타임 실행에 반영되지 않음
- 작업:
  - parser 로드 시 priority 기준 정렬 반영
  - `continueOnFailure` 의미 정의 후 parse loop에 반영
  - transform도 priority가 있다면 동일하게 정렬 적용
- 완료 조건:
  - 설정한 priority 순서대로 실행됨
  - `continueOnFailure` 동작이 테스트로 보장됨

### 6. Output adapter 설정값과 실제 구현 정합성 맞추기

- 문제:
  - `HttpOutputAdapter`가 `method`, `headers`를 실제로 사용하지 않음
  - `https://` URL을 받아도 TLS 없이 평문 소켓을 사용함
  - `addOriginText` 설정이 출력 payload에 반영되지 않음
- 작업:
  - `HttpOutputAdapter`가 `method`와 `headers`를 실제 요청에 반영하도록 수정
  - HTTPS를 실제 지원하거나, 미지원이면 설정/검증/UI에서 차단
  - `addOriginText` 정책을 정의하고 `LogEvent` 출력 payload 생성에 반영
- 완료 조건:
  - 노출된 설정이 실제 동작과 일치함
  - 미지원 기능은 설정 단계에서 막힘
  - 관련 테스트 추가

### 7. Global output adapter 정책 일치시키기

- 문제: 런타임은 `null`, 빈 문자열, `all`을 global output으로 허용하지만 DB/검증은 `messagetype`을 필수로 강제함
- 작업:
  - global output을 공식 지원할지 결정
  - 지원 시 DB/엔티티/검증/UI/API 전부 일관되게 수정
  - 미지원 시 런타임의 global 처리 제거
- 완료 조건:
  - global output 정책이 전 계층에서 일관됨
  - 혼란스러운 허용/차단 상태가 사라짐

## 코드 정리 작업

### 8. 미사용 DTO / Request 모델 정리

- 후보:
  - `CreateInputAdapterRequest`
  - `CreateOutputAdapterRequest`
  - `CreateParserRequest`
  - `CreateTransformRequest`
  - `UpdateInputAdapterRequest`
  - `UpdateOutputAdapterRequest`
  - `UpdateParserRequest`
  - `UpdateTransformRequest`
  - `UpdateConfigSettingRequest`
  - `PipelineStatusDTO`
  - `ReloadProgressDTO`
  - `ValidationResultDTO`
  - `PipelineIntegrityResultDTO`
  - `ConfigHistoryDTO`
  - `ConfigurationVersionDTO`
  - 그 외 선언만 있고 실제 컨트롤러에서 사용하지 않는 DTO
- 작업:
  - 실제 사용 여부 재확인
  - 미사용이면 삭제
  - 향후 사용할 예정이면 컨트롤러 반환 타입에 실제 적용
- 완료 조건:
  - 선언만 있고 어디에서도 안 쓰는 DTO가 제거되거나 실제 사용됨

### 9. 미사용 유틸 / 주석 코드 정리

- 후보:
  - `CustomExecutorService`
  - `YamlPropertySourceFactory`
  - 전체가 주석 처리된 `RequestLoggingFilter`
- 작업:
  - 사용 계획이 없으면 제거
  - 필요하다면 실제 wiring 추가
- 완료 조건:
  - 죽은 유틸 코드와 주석 덩어리 제거

### 10. `Structure` transform 경로 정리

- 문제: `Structure` 클래스는 transform 모델에 존재하지만 현재 파이프라인은 `StructuredTransformService`를 직접 호출하는 경로와 중복됨
- 작업:
  - `Structure`를 공식 transform으로 유지할지 결정
  - 유지 시 `TransformService` 경로와 역할 중복 정리
  - 미사용이면 제거
- 완료 조건:
  - structured transform 진입 경로가 하나로 정리됨

## 테스트 보강 작업

### 11. 회귀 테스트 추가

- 반드시 추가할 테스트:
  - `TransformService`가 실제 transform 클래스를 로드하는지
  - pipeline reload 중 메시지 유실이 없는지
  - restart API가 실제로 동작하는지
  - structured mapping 저장 후 캐시가 무효화되는지
  - parser priority / continueOnFailure가 반영되는지
  - `HttpOutputAdapter`의 method / headers / https 정책이 기대대로 동작하는지
  - global output adapter 정책이 구현과 일치하는지
- 완료 조건:
  - 이번 분석에서 지적된 버그가 테스트 없이 다시 들어오지 않음

## 작업 순서 제안

1. `TransformService` 버그 수정
2. reload / restart 안정화
3. structured mapping cache 무효화
4. parser / transform 실행 순서 정합성 수정
5. output adapter 설정/구현 정합성 맞춤
6. dead code 정리
7. 회귀 테스트 보강

## 참고

- 현재 테스트는 통과하지만, 위 핵심 버그들은 커버하지 못하고 있음
- 특히 `reload`, `restart`, reflection 기반 transform 로딩, structured mapping cache는 반드시 테스트로 고정해야 함
