# LogParser TODO

## Output Fetch Cycle Implementation

### Current State
- **실시간 방식**: 메시지가 들어오는 즉시 개별 OutputAdapter로 전송
- `OutputAdapterProcedure.java:55-72`에서 메시지를 즉시 처리
- 각 OutputAdapter가 개별 큐(1000개)를 가지고 운영

### Proposed Changes

#### 1. Fetch Cycle vs 실시간 분석
**Fetch Cycle (배치/주기적 전송) 장점:**
- 성능 최적화: HTTP keep-alive, connection pooling 효과적 활용
- 네트워크 효율성: 다수 메시지를 한 번에 전송으로 오버헤드 감소
- 백프레셔 제어: 다운스트림 시스템 부하 조절 가능
- 에러 복구: 배치 단위로 재시도 로직 구현 용이

**실시간 방식 장점:**
- 낮은 지연시간: 즉시 처리로 실시간 모니터링 가능
- 단순한 구조: 버퍼링 로직 불필요

#### 2. 구현 방안
1. **설정 가능한 배치 크기** (예: 100~1000개)
2. **시간 기반 플러시** (예: 5초마다 강제 전송)
3. **어댑터별 설정** (Kafka는 배치, Console은 실시간)

#### 3. 수정 대상 파일
- `OutputAdapterProcedure.java:55-72` - 배치 로직 추가
- `ApplicationProperties` - 배치 설정 추가
- 각 OutputAdapter - 배치 전송 메서드 구현

### Decision Needed
로그 파서 특성상 **Fetch Cycle**이 더 적합할 것으로 판단되나 최종 결정 필요

---

## Other Issues

### Code Cleanup
- `HttpOutputAdapter.java:11-12` - 사용하지 않는 Logger, LoggerFactory import 제거 필요

## 코드 검토 결과 발견된 문제점

### 보안 관련
- `build.gradle:25-26` - HTTP 프로토콜 사용으로 인한 보안 취약점 (HTTPS 사용 권장)
- `HttpInputAdapter.java:79-81` - Content-Length 검증은 있으나 추가 보안 검증 필요

### 성능 및 리소스 관리
- `KafkaInputAdapter.java:48` - messageQueue가 인스턴스 변수이지만 static으로 선언된 부분이 있어 여러 인스턴스 간 큐 공유 우려
- `HttpOutputAdapter.java:67-97` - 연결 재사용을 위한 로직이 있으나 복잡하고 오류 가능성 존재
- `MessageDispatcher.java:45` - static running 변수로 인해 여러 인스턴스에서 동시 제어 문제 가능성

### 코드 품질
- `KafkaInputAdapter.java:14` - 잘못된 import (StringDeserializer가 Jackson에서 import됨, Kafka deserializer 사용해야 함)
- `HttpParser.java:43` - 콜론 분할 시 길이 검증은 있으나 배열 접근 시 안전성 부족
- `TcpInputAdapter.java:69-74` - SocketException 시 서버소켓 재초기화 로직이 있으나 무한 루프 가능성
- `Filter.java:60,68` - null 검사 없이 targetProp 사용으로 NPE 가능성

### 로깅 및 디버깅
- `ConfigValidator.java:21` - Logger 변수명이 LOGGER로 일관성 부족 (다른 파일들은 log 사용)
- `UdpInputAdapter.java:58-60` - MAX_PACKET_SIZE 체크가 receive 후에 실행되어 의미 없음

### 설정 및 구성
- `build.gradle:36` - Spring Boot Elasticsearch 버전이 다른 의존성들과 일치하지 않음 (3.3.5 vs 3.4.10)
- `HttpOutputAdapter.java:50-58` - URL 파싱 로직이 복잡하고 예외 상황 처리 부족

### 동시성 및 스레드 안전성
- `MessageDispatcher.java:198-203` - shutdown hook에서 static 변수만 설정하여 완전한 종료 보장 어려움
- `HttpOutputAdapter.java:111` - synchronized 블록 사용하나 전체 메서드 동기화로 성능 저하 가능