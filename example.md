# Logparser REST API 설정 가이드

이 문서는 Logparser 애플리케이션을 REST API를 통해 설정하는 방법을 자세히 설명합니다.

## 목차

1. [개요](#개요)
2. [API 엔드포인트](#api-엔드포인트)
3. [Input Adapter 설정](#input-adapter-설정)
4. [Parser 설정](#parser-설정)
5. [Transform 설정](#transform-설정)
6. [Output Adapter 설정](#output-adapter-설정)
7. [파이프라인 관리](#파이프라인-관리)
8. [전체 워크플로우 예제](#전체-워크플로우-예제)

---

## 개요

Logparser는 **데이터베이스 기반 설정 관리**를 사용합니다. 모든 설정은 SQLite 데이터베이스에 저장되며, REST API를 통해 CRUD(생성, 조회, 수정, 삭제) 작업을 수행할 수 있습니다.

### 기본 URL

```
http://localhost:443/api/v1
```

> **참고**: 기본 포트는 443입니다. 필요시 `application.yml`에서 변경 가능합니다.

### 설정 흐름

```
1. Input Adapter 생성 → 로그 수신 방법 정의
2. Parser 생성 → 로그 파싱 방법 정의
3. Transform 생성 (선택) → 데이터 변환 규칙 정의
4. Output Adapter 생성 → 로그 출력 방법 정의
5. 파이프라인 재시작 → 설정 적용
```

---

## API 엔드포인트

### Input Adapters

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/v1/input-adapters` | 모든 Input Adapter 조회 (페이징) |
| POST | `/api/v1/input-adapters` | Input Adapter 생성 |
| GET | `/api/v1/input-adapters/{id}` | 특정 Input Adapter 조회 |
| PUT | `/api/v1/input-adapters/{id}` | Input Adapter 수정 |
| DELETE | `/api/v1/input-adapters/{id}` | Input Adapter 삭제 |
| GET | `/api/v1/input-adapters/type/{type}` | 타입별 조회 |
| GET | `/api/v1/input-adapters/messagetype/{messageType}` | 메시지 타입별 조회 |
| PATCH | `/api/v1/input-adapters/{id}/enable` | Input Adapter 활성화 |
| PATCH | `/api/v1/input-adapters/{id}/disable` | Input Adapter 비활성화 |

### Parsers

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/v1/parsers` | 모든 Parser 조회 (페이징) |
| POST | `/api/v1/parsers` | Parser 생성 |
| GET | `/api/v1/parsers/{id}` | 특정 Parser 조회 |
| PUT | `/api/v1/parsers/{id}` | Parser 수정 |
| DELETE | `/api/v1/parsers/{id}` | Parser 삭제 |
| GET | `/api/v1/parsers/type/{type}` | 타입별 조회 |
| GET | `/api/v1/parsers/messagetype/{messageType}` | 메시지 타입별 조회 (우선순위순) |
| PATCH | `/api/v1/parsers/{id}/priority` | 우선순위 변경 |
| POST | `/api/v1/parsers/validate` | Parser 검증 |

### Transforms

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/v1/transforms` | 모든 Transform 조회 (페이징) |
| POST | `/api/v1/transforms` | Transform 생성 |
| GET | `/api/v1/transforms/{id}` | 특정 Transform 조회 |
| PUT | `/api/v1/transforms/{id}` | Transform 수정 |
| DELETE | `/api/v1/transforms/{id}` | Transform 삭제 |
| GET | `/api/v1/transforms/type/{type}` | 타입별 조회 |
| GET | `/api/v1/transforms/messagetype/{messageType}` | 메시지 타입별 조회 (우선순위순) |
| PATCH | `/api/v1/transforms/{id}/priority` | 우선순위 변경 |

### Output Adapters

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/v1/output-adapters` | 모든 Output Adapter 조회 (페이징) |
| POST | `/api/v1/output-adapters` | Output Adapter 생성 |
| GET | `/api/v1/output-adapters/{id}` | 특정 Output Adapter 조회 |
| PUT | `/api/v1/output-adapters/{id}` | Output Adapter 수정 |
| DELETE | `/api/v1/output-adapters/{id}` | Output Adapter 삭제 |
| GET | `/api/v1/output-adapters/type/{type}` | 타입별 조회 |
| GET | `/api/v1/output-adapters/messagetype/{messageType}` | 메시지 타입별 조회 |
| PATCH | `/api/v1/output-adapters/{id}/enable` | Output Adapter 활성화 |
| PATCH | `/api/v1/output-adapters/{id}/disable` | Output Adapter 비활성화 |

### Pipeline 관리

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/v1/pipeline/status` | 파이프라인 상태 조회 |
| POST | `/api/v1/pipeline/reload` | 설정 리로드 |
| POST | `/api/v1/pipeline/validate-and-reload` | 검증 후 리로드 |
| POST | `/api/v1/pipeline/restart` | 파이프라인 재시작 |
| GET | `/api/v1/pipeline/reload-progress` | 리로드 진행률 조회 |
| POST | `/api/v1/pipeline/cancel-reload` | 리로드 취소 |

---

## Input Adapter 설정

Input Adapter는 로그 데이터를 수집하는 방법을 정의합니다.

### 공통 필드

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `type` | String | ✓ | Adapter 타입 |
| `messagetype` | String | ✓ | 메시지 타입 (고유값, 파이프라인 라우팅 키) |
| `enabled` | Boolean |  | 활성화 여부 (기본: true) |

### 1. FileInputAdapter - 파일에서 로그 읽기

**사용 사례**: 로그 파일을 tail -f 방식으로 읽기

```bash
curl -X POST http://localhost:443/api/v1/input-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "FileInputAdapter",
    "messagetype": "syslog",
    "path": "/var/log/syslog",
    "enabled": true
  }'
```

**필드 설명**:
- `path`: 읽을 파일 경로 (필수)
- `codec`: 인코딩 (선택, 기본: UTF-8)

**응답 예시**:
```json
{
  "id": 1,
  "type": "FileInputAdapter",
  "messagetype": "syslog",
  "path": "/var/log/syslog",
  "enabled": true,
  "createdAt": "2025-11-22T10:00:00",
  "updatedAt": "2025-11-22T10:00:00",
  "version": 0
}
```

### 2. TcpInputAdapter - TCP 서버로 로그 수신

**사용 사례**: 네트워크를 통해 TCP로 로그 전송

```bash
curl -X POST http://localhost:443/api/v1/input-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "TcpInputAdapter",
    "messagetype": "tcp-logs",
    "host": "0.0.0.0",
    "port": 5140,
    "enabled": true
  }'
```

**필드 설명**:
- `host`: 바인딩할 호스트 (기본: 0.0.0.0)
- `port`: 리스닝 포트 (필수)
- `bufferSize`: 버퍼 크기 (선택)
- `timeoutMs`: 타임아웃 (선택)

### 3. UdpInputAdapter - UDP로 로그 수신

**사용 사례**: Syslog 서버처럼 UDP로 로그 수신

```bash
curl -X POST http://localhost:443/api/v1/input-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "UdpInputAdapter",
    "messagetype": "syslog-udp",
    "port": 514,
    "enabled": true
  }'
```

**필드 설명**:
- `port`: 리스닝 포트 (필수)
- `bufferSize`: 버퍼 크기 (선택)

### 4. HttpInputAdapter - HTTP POST 엔드포인트

**사용 사례**: 애플리케이션에서 HTTP POST로 로그 전송

```bash
curl -X POST http://localhost:443/api/v1/input-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "HttpInputAdapter",
    "messagetype": "app-logs",
    "port": 8080,
    "path": "/logs",
    "enabled": true
  }'
```

**필드 설명**:
- `port`: HTTP 서버 포트 (필수)
- `path`: 엔드포인트 경로 (필수)

**로그 전송 예시**:
```bash
# 이 Input Adapter가 생성되면 다음과 같이 로그 전송 가능
curl -X POST http://localhost:8080/logs \
  -H "Content-Type: application/json" \
  -d '{"level":"ERROR","message":"Application error occurred"}'
```

### 5. KafkaInputAdapter - Kafka 토픽에서 로그 수신

**사용 사례**: Kafka 토픽을 구독하여 로그 수신

```bash
curl -X POST http://localhost:443/api/v1/input-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "KafkaInputAdapter",
    "messagetype": "kafka-logs",
    "bootstrapservers": "localhost:9092",
    "topicid": "application-logs",
    "groupId": "logparser-consumer-group",
    "enabled": true
  }'
```

**필드 설명**:
- `bootstrapservers`: Kafka 브로커 주소 (필수)
- `topicid`: 구독할 토픽 이름 (필수)
- `groupId`: 컨슈머 그룹 ID (필수)

### 6. FakeInputAdapter - 테스트 데이터 생성

**사용 사례**: 테스트용 더미 로그 생성

```bash
curl -X POST http://localhost:443/api/v1/input-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "FakeInputAdapter",
    "messagetype": "test-logs",
    "enabled": true
  }'
```

---

## Parser 설정

Parser는 수신한 원본 로그를 파싱하여 구조화된 데이터로 변환합니다.

### 공통 필드

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `type` | String | ✓ | Parser 타입 |
| `messagetype` | String | ✓ | 처리할 메시지 타입 (Input Adapter의 messagetype과 매칭) |
| `priority` | Integer |  | 실행 우선순위 (낮을수록 먼저 실행, 기본: 0) |
| `enabled` | Boolean |  | 활성화 여부 (기본: true) |
| `continueOnFailure` | Boolean |  | 파싱 실패 시 계속 진행 여부 (기본: false) |
| `param` | String |  | Parser별 설정 파라미터 (JSON 문자열) |

### 1. JsonParser - JSON 로그 파싱

**사용 사례**: JSON 형식의 로그 파싱

```bash
curl -X POST http://localhost:443/api/v1/parsers \
  -H "Content-Type: application/json" \
  -d '{
    "type": "JsonParser",
    "messagetype": "app-logs",
    "priority": 1,
    "enabled": true
  }'
```

**입력 예시**:
```json
{"timestamp":"2025-11-22T10:00:00Z","level":"ERROR","message":"Database connection failed"}
```

**출력 결과**:
```json
{
  "timestamp": "2025-11-22T10:00:00Z",
  "level": "ERROR",
  "message": "Database connection failed"
}
```

### 2. GrokParser - Grok 패턴 파싱

**사용 사례**: 비정형 로그를 Grok 패턴으로 파싱

```bash
curl -X POST http://localhost:443/api/v1/parsers \
  -H "Content-Type: application/json" \
  -d '{
    "type": "GrokParser",
    "messagetype": "syslog",
    "priority": 1,
    "param": "{\"pattern\": \"%{SYSLOGTIMESTAMP:timestamp} %{SYSLOGHOST:hostname} %{DATA:program}(?:\\[%{POSINT:pid}\\])?: %{GREEDYDATA:message}\"}",
    "enabled": true
  }'
```

**param 구조**:
```json
{
  "pattern": "Grok 패턴 문자열"
}
```

**입력 예시**:
```
Nov 22 10:00:00 server1 sshd[1234]: Failed password for root from 192.168.1.100
```

**출력 결과**:
```json
{
  "timestamp": "Nov 22 10:00:00",
  "hostname": "server1",
  "program": "sshd",
  "pid": "1234",
  "message": "Failed password for root from 192.168.1.100"
}
```

### 3. RegexParser - 정규표현식 파싱

**사용 사례**: 커스텀 정규표현식으로 로그 파싱

```bash
curl -X POST http://localhost:443/api/v1/parsers \
  -H "Content-Type: application/json" \
  -d '{
    "type": "RegexParser",
    "messagetype": "apache-logs",
    "priority": 1,
    "param": "{\"pattern\": \"^(?<ip>\\\\S+) \\\\S+ \\\\S+ \\\\[(?<timestamp>[^\\\\]]+)\\\\] \\\"(?<method>\\\\S+) (?<path>\\\\S+) \\\\S+\\\" (?<status>\\\\d+) (?<size>\\\\d+)\"}",
    "enabled": true
  }'
```

**param 구조**:
```json
{
  "pattern": "정규표현식 (named groups 사용)"
}
```

**입력 예시**:
```
192.168.1.100 - - [22/Nov/2025:10:00:00 +0000] "GET /index.html HTTP/1.1" 200 1234
```

**출력 결과**:
```json
{
  "ip": "192.168.1.100",
  "timestamp": "22/Nov/2025:10:00:00 +0000",
  "method": "GET",
  "path": "/index.html",
  "status": "200",
  "size": "1234"
}
```

### 4. RFC3164SyslogParser - BSD Syslog 파싱

**사용 사례**: 전통적인 Syslog 포맷 (RFC 3164) 파싱

```bash
curl -X POST http://localhost:443/api/v1/parsers \
  -H "Content-Type: application/json" \
  -d '{
    "type": "RFC3164SyslogParser",
    "messagetype": "syslog-udp",
    "priority": 1,
    "enabled": true
  }'
```

**입력 예시**:
```
<34>Nov 22 10:00:00 server1 sshd[1234]: Connection from 192.168.1.100
```

### 5. RFC5424SyslogParser - 최신 Syslog 파싱

**사용 사례**: 최신 Syslog 포맷 (RFC 5424) 파싱

```bash
curl -X POST http://localhost:443/api/v1/parsers \
  -H "Content-Type: application/json" \
  -d '{
    "type": "RFC5424SyslogParser",
    "messagetype": "syslog",
    "priority": 1,
    "enabled": true
  }'
```

**입력 예시**:
```
<165>1 2025-11-22T10:00:00.000Z server1 sshd 1234 ID47 - Connection established
```

### 6. HttpParser - HTTP 요청 파싱

**사용 사례**: HTTP 요청 정보 추출

```bash
curl -X POST http://localhost:443/api/v1/parsers \
  -H "Content-Type: application/json" \
  -d '{
    "type": "HttpParser",
    "messagetype": "http-logs",
    "priority": 1,
    "enabled": true
  }'
```

---

## Transform 설정

Transform은 파싱된 데이터를 변환, 필터링, 보강하는 역할을 합니다.

### 공통 필드

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `type` | String | ✓ | Transform 타입 |
| `messagetype` | String | ✓ | 처리할 메시지 타입 |
| `priority` | Integer |  | 실행 우선순위 (낮을수록 먼저 실행, 기본: 0) |
| `enabled` | Boolean |  | 활성화 여부 (기본: true) |

### 1. Filter - 데이터 필터링

**사용 사례**: 특정 조건에 맞는 로그만 통과

#### 포함 필터 (Pass Filter)

```bash
curl -X POST http://localhost:443/api/v1/transforms \
  -H "Content-Type: application/json" \
  -d '{
    "type": "Filter",
    "messagetype": "app-logs",
    "priority": 1,
    "filterPass": "{\"level\": [\"ERROR\", \"FATAL\"]}",
    "enabled": true
  }'
```

**filterPass 구조**:
```json
{
  "필드명": ["값1", "값2", ...]
}
```

**동작**: level 필드가 "ERROR" 또는 "FATAL"인 로그만 통과

#### 제외 필터 (Drop Filter)

```bash
curl -X POST http://localhost:443/api/v1/transforms \
  -H "Content-Type: application/json" \
  -d '{
    "type": "Filter",
    "messagetype": "app-logs",
    "priority": 2,
    "filterDrop": "{\"user\": [\"healthcheck\", \"monitor\"]}",
    "enabled": true
  }'
```

**filterDrop 구조**:
```json
{
  "필드명": ["값1", "값2", ...]
}
```

**동작**: user 필드가 "healthcheck" 또는 "monitor"인 로그는 제외

#### 복합 필터

```bash
curl -X POST http://localhost:443/api/v1/transforms \
  -H "Content-Type: application/json" \
  -d '{
    "type": "Filter",
    "messagetype": "app-logs",
    "priority": 1,
    "filterPass": "{\"level\": [\"ERROR\", \"WARN\"]}",
    "filterDrop": "{\"component\": [\"test\", \"debug\"]}",
    "enabled": true
  }'
```

**동작**:
1. level이 ERROR 또는 WARN인 것만 통과
2. 그 중에서 component가 test 또는 debug인 것은 제외

### 2. AddProperty - 필드 추가/수정

**사용 사례**: 새 필드 추가 또는 기존 필드 중첩

#### 단순 필드 추가

```bash
curl -X POST http://localhost:443/api/v1/transforms \
  -H "Content-Type: application/json" \
  -d '{
    "type": "AddProperty",
    "messagetype": "app-logs",
    "priority": 3,
    "addProperties": "{\"environment\": \"production\", \"datacenter\": \"us-west-1\", \"version\": \"1.0.0\"}",
    "enabled": true
  }'
```

**addProperties 구조**:
```json
{
  "새필드명1": "값1",
  "새필드명2": "값2"
}
```

**변환 전**:
```json
{
  "level": "ERROR",
  "message": "Database error"
}
```

**변환 후**:
```json
{
  "level": "ERROR",
  "message": "Database error",
  "environment": "production",
  "datacenter": "us-west-1",
  "version": "1.0.0"
}
```

#### 필드 중첩 (Nesting)

```bash
curl -X POST http://localhost:443/api/v1/transforms \
  -H "Content-Type: application/json" \
  -d '{
    "type": "AddProperty",
    "messagetype": "app-logs",
    "priority": 4,
    "addProperties": "{\"meta.source\": \"application\", \"meta.region\": \"us-west-1\"}",
    "enabled": true
  }'
```

**변환 전**:
```json
{
  "level": "ERROR",
  "message": "Database error"
}
```

**변환 후**:
```json
{
  "level": "ERROR",
  "message": "Database error",
  "meta": {
    "source": "application",
    "region": "us-west-1"
  }
}
```

### 3. RemoveProperty - 필드 제거

**사용 사례**: 민감한 정보나 불필요한 필드 제거

```bash
curl -X POST http://localhost:443/api/v1/transforms \
  -H "Content-Type: application/json" \
  -d '{
    "type": "RemoveProperty",
    "messagetype": "app-logs",
    "priority": 5,
    "removeProperties": "{\"fields\": [\"password\", \"api_key\", \"session_token\"]}",
    "enabled": true
  }'
```

**removeProperties 구조**:
```json
{
  "fields": ["필드명1", "필드명2", ...]
}
```

**변환 전**:
```json
{
  "username": "admin",
  "password": "secret123",
  "email": "admin@example.com",
  "api_key": "abc123"
}
```

**변환 후**:
```json
{
  "username": "admin",
  "email": "admin@example.com"
}
```

---

## Output Adapter 설정

Output Adapter는 처리된 로그를 외부 시스템으로 전송합니다.

### 공통 필드

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `type` | String | ✓ | Adapter 타입 |
| `messagetype` | String | ✓ | 전송할 메시지 타입 |
| `enabled` | Boolean |  | 활성화 여부 (기본: true) |
| `addOriginText` | Boolean |  | 원본 텍스트 포함 여부 (기본: false) |

### 1. ConsoleOutputAdapter - 콘솔 출력

**사용 사례**: 디버깅 또는 로그 확인

```bash
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "ConsoleOutputAdapter",
    "messagetype": "app-logs",
    "addOriginText": true,
    "enabled": true
  }'
```

**출력 예시** (표준 출력에 출력됨):
```json
{"level":"ERROR","message":"Database error","environment":"production","_origin":"original log text"}
```

### 2. TcpOutputAdapter - TCP 전송

**사용 사례**: 다른 로그 수집기로 TCP 전송

```bash
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "TcpOutputAdapter",
    "messagetype": "app-logs",
    "host": "log-collector.example.com",
    "port": 5140,
    "enabled": true
  }'
```

**필드 설명**:
- `host`: 대상 호스트 (필수)
- `port`: 대상 포트 (필수)
- `timeoutMs`: 연결 타임아웃 (선택)

### 3. HttpOutputAdapter - HTTP POST 전송

**사용 사례**: Webhook 또는 HTTP 기반 로그 수집 서비스

```bash
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "HttpOutputAdapter",
    "messagetype": "app-logs",
    "url": "https://logs.example.com/ingest",
    "method": "POST",
    "headers": "{\"Authorization\": \"Bearer token123\", \"Content-Type\": \"application/json\"}",
    "timeoutMs": 5000,
    "retryCount": 3,
    "retryDelayMs": 1000,
    "enabled": true
  }'
```

**필드 설명**:
- `url`: 대상 URL (필수)
- `method`: HTTP 메서드 (기본: POST)
- `headers`: HTTP 헤더 (JSON 문자열)
- `timeoutMs`: 요청 타임아웃
- `retryCount`: 재시도 횟수
- `retryDelayMs`: 재시도 대기 시간

### 4. KafkaOutputAdapter - Kafka 전송

**사용 사례**: Kafka 토픽으로 로그 발행

```bash
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "KafkaOutputAdapter",
    "messagetype": "app-logs",
    "bootstrapservers": "kafka1:9092,kafka2:9092,kafka3:9092",
    "topicid": "processed-logs",
    "enabled": true
  }'
```

**필드 설명**:
- `bootstrapservers`: Kafka 브로커 주소 (필수, 쉼표로 구분)
- `topicid`: 대상 토픽 (필수)
- `key`: 파티션 키 (선택)

### 5. OpenSearchOutputAdapter - OpenSearch/Elasticsearch 전송

**사용 사례**: OpenSearch 또는 Elasticsearch로 인덱싱

```bash
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "OpenSearchOutputAdapter",
    "messagetype": "app-logs",
    "url": "https://opensearch.example.com:9200",
    "indexTemplate": "logs-%{yyyy.MM.dd}",
    "osUsername": "admin",
    "osPassword": "admin123",
    "batchSize": 1000,
    "flushIntervalMs": 10000,
    "enabled": true
  }'
```

**필드 설명**:
- `url`: OpenSearch URL (필수)
- `indexTemplate`: 인덱스 이름 템플릿 (필수)
  - `%{yyyy.MM.dd}`: 날짜 기반 인덱스
  - `%{fieldname}`: 필드 값 기반 인덱스
- `osUsername`: 사용자명 (Basic Auth)
- `osPassword`: 비밀번호 (Basic Auth)
- `batchSize`: 배치 크기 (기본: 1000)
- `flushIntervalMs`: Flush 주기 (기본: 10000ms)

**인덱스 템플릿 예시**:
```
"logs-%{yyyy.MM.dd}"           → logs-2025.11.22
"app-%{environment}-%{yyyy}"   → app-production-2025
"%{datacenter}-logs"           → us-west-1-logs
```

### 6. RabbitMQAdapter - RabbitMQ 전송

**사용 사례**: RabbitMQ 익스체인지로 메시지 발행

```bash
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "RabbitMQAdapter",
    "messagetype": "app-logs",
    "host": "rabbitmq.example.com",
    "rmqPort": 5672,
    "exchange": "logs-exchange",
    "routingkey": "app.error",
    "rmqUsername": "guest",
    "rmqPassword": "guest",
    "enabled": true
  }'
```

**필드 설명**:
- `host`: RabbitMQ 호스트 (필수)
- `rmqPort`: RabbitMQ 포트 (기본: 5672)
- `exchange`: 익스체인지 이름 (필수)
- `routingkey`: 라우팅 키 (필수)
- `rmqUsername`: 사용자명
- `rmqPassword`: 비밀번호

### 7. BenchmarkAdapter - 성능 측정

**사용 사례**: 처리량 측정

```bash
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "BenchmarkAdapter",
    "messagetype": "app-logs",
    "enabled": true
  }'
```

---

## 파이프라인 관리

설정을 변경한 후에는 파이프라인을 재시작하거나 리로드해야 적용됩니다.

### 1. 파이프라인 상태 조회

```bash
curl -X GET http://localhost:443/api/v1/pipeline/status
```

**응답 예시**:
```json
{
  "status": "RUNNING",
  "inputAdapterCount": 2,
  "parserCount": 3,
  "transformCount": 5,
  "outputAdapterCount": 2,
  "queueSize": 150,
  "processedMessages": 12345
}
```

**상태 값**:
- `RUNNING`: 정상 실행 중
- `STOPPED`: 중지됨
- `RELOADING`: 리로드 중
- `STOPPING`: 중지 중
- `ERROR`: 오류 발생

### 2. 설정 리로드 (빠른 재시작)

```bash
curl -X POST http://localhost:443/api/v1/pipeline/reload
```

**동작**:
- 데이터베이스에서 최신 설정 로드
- 파이프라인 컴포넌트 재초기화
- 큐에 있는 메시지 유지

**응답 예시**:
```json
{
  "status": "success",
  "message": "Configuration reloaded successfully"
}
```

### 3. 검증 후 리로드 (안전한 재시작)

```bash
curl -X POST http://localhost:443/api/v1/pipeline/validate-and-reload
```

**동작**:
1. 설정 무결성 검증
2. 검증 성공 시에만 리로드
3. 실패 시 에러 메시지 반환

**응답 예시 (성공)**:
```json
{
  "status": "success",
  "message": "Configuration validated and reloaded successfully"
}
```

**응답 예시 (실패)**:
```json
{
  "status": "error",
  "message": "Pipeline validation failed: [No parser defined for messagetype 'app-logs']"
}
```

### 4. 파이프라인 재시작 (완전 재시작)

```bash
curl -X POST http://localhost:443/api/v1/pipeline/restart
```

**동작**:
1. 입력 어댑터 중지
2. 큐 비우기
3. 모든 컴포넌트 중지
4. 설정 리로드
5. 컴포넌트 재시작

**응답 예시**:
```json
{
  "status": "success",
  "message": "Pipeline restarted successfully"
}
```

### 5. 리로드 진행률 조회

```bash
curl -X GET http://localhost:443/api/v1/pipeline/reload-progress
```

**응답 예시**:
```json
{
  "progress": 75,
  "status": "RELOADING",
  "inProgress": true
}
```

### 6. 리로드 취소

```bash
curl -X POST http://localhost:443/api/v1/pipeline/cancel-reload
```

---

## 전체 워크플로우 예제

다음은 완전한 로그 파이프라인을 REST API로 구성하는 실제 예제입니다.

### 시나리오

**목표**: 애플리케이션 로그를 HTTP로 수신하여, JSON 파싱 후, ERROR 레벨만 필터링하고, 환경 정보를 추가한 뒤 OpenSearch에 저장

### Step 1: Input Adapter 생성

```bash
curl -X POST http://localhost:443/api/v1/input-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "HttpInputAdapter",
    "messagetype": "webapp",
    "port": 8080,
    "path": "/api/logs",
    "enabled": true
  }'
```

**결과**: HTTP POST 엔드포인트 `http://localhost:8080/api/logs` 생성

### Step 2: Parser 생성

```bash
curl -X POST http://localhost:443/api/v1/parsers \
  -H "Content-Type: application/json" \
  -d '{
    "type": "JsonParser",
    "messagetype": "webapp",
    "priority": 1,
    "enabled": true
  }'
```

### Step 3: Transform 생성 (필터링)

```bash
# Transform 1: ERROR 레벨만 통과
curl -X POST http://localhost:443/api/v1/transforms \
  -H "Content-Type: application/json" \
  -d '{
    "type": "Filter",
    "messagetype": "webapp",
    "priority": 1,
    "filterPass": "{\"level\": [\"ERROR\", \"FATAL\"]}",
    "enabled": true
  }'

# Transform 2: 환경 정보 추가
curl -X POST http://localhost:443/api/v1/transforms \
  -H "Content-Type: application/json" \
  -d '{
    "type": "AddProperty",
    "messagetype": "webapp",
    "priority": 2,
    "addProperties": "{\"environment\": \"production\", \"datacenter\": \"us-west-1\", \"app_version\": \"2.3.1\"}",
    "enabled": true
  }'

# Transform 3: 민감한 필드 제거
curl -X POST http://localhost:443/api/v1/transforms \
  -H "Content-Type: application/json" \
  -d '{
    "type": "RemoveProperty",
    "messagetype": "webapp",
    "priority": 3,
    "removeProperties": "{\"fields\": [\"password\", \"token\", \"api_key\"]}",
    "enabled": true
  }'
```

### Step 4: Output Adapter 생성

```bash
# Output 1: OpenSearch로 전송
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "OpenSearchOutputAdapter",
    "messagetype": "webapp",
    "url": "https://opensearch.example.com:9200",
    "indexTemplate": "webapp-errors-%{yyyy.MM.dd}",
    "osUsername": "admin",
    "osPassword": "SecurePassword123!",
    "batchSize": 500,
    "flushIntervalMs": 5000,
    "enabled": true
  }'

# Output 2: 콘솔에도 출력 (디버깅용)
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "ConsoleOutputAdapter",
    "messagetype": "webapp",
    "addOriginText": false,
    "enabled": true
  }'
```

### Step 5: 설정 검증 및 적용

```bash
# 설정 검증 후 리로드
curl -X POST http://localhost:443/api/v1/pipeline/validate-and-reload
```

**응답**:
```json
{
  "status": "success",
  "message": "Configuration validated and reloaded successfully"
}
```

### Step 6: 파이프라인 테스트

```bash
# 로그 전송 (INFO 레벨 - 필터링됨)
curl -X POST http://localhost:8080/api/logs \
  -H "Content-Type: application/json" \
  -d '{
    "timestamp": "2025-11-22T10:00:00Z",
    "level": "INFO",
    "message": "User logged in successfully",
    "user": "john@example.com"
  }'

# 로그 전송 (ERROR 레벨 - 처리됨)
curl -X POST http://localhost:8080/api/logs \
  -H "Content-Type: application/json" \
  -d '{
    "timestamp": "2025-11-22T10:01:00Z",
    "level": "ERROR",
    "message": "Database connection failed",
    "error_code": "DB_CONN_TIMEOUT",
    "password": "secret123"
  }'
```

**처리 결과** (OpenSearch에 저장되는 데이터):
```json
{
  "timestamp": "2025-11-22T10:01:00Z",
  "level": "ERROR",
  "message": "Database connection failed",
  "error_code": "DB_CONN_TIMEOUT",
  "environment": "production",
  "datacenter": "us-west-1",
  "app_version": "2.3.1"
}
```
> **참고**: password 필드는 RemoveProperty Transform에 의해 제거됨

### Step 7: 설정 조회 및 관리

```bash
# 모든 Input Adapter 조회
curl -X GET "http://localhost:443/api/v1/input-adapters?page=0&size=10"

# 특정 messagetype의 Parser 조회
curl -X GET http://localhost:443/api/v1/parsers/messagetype/webapp

# 특정 Transform 수정
curl -X PUT http://localhost:443/api/v1/transforms/2 \
  -H "Content-Type: application/json" \
  -d '{
    "id": 2,
    "type": "AddProperty",
    "messagetype": "webapp",
    "priority": 2,
    "addProperties": "{\"environment\": \"staging\", \"datacenter\": \"us-east-1\", \"app_version\": \"2.4.0\"}",
    "enabled": true,
    "version": 0
  }'

# 설정 변경 후 리로드
curl -X POST http://localhost:443/api/v1/pipeline/reload

# Output Adapter 비활성화
curl -X PATCH http://localhost:443/api/v1/output-adapters/2/disable

# 파이프라인 상태 확인
curl -X GET http://localhost:443/api/v1/pipeline/status
```

---

## 추가 예제

### 예제 1: Syslog 서버 구축

```bash
# 1. UDP Syslog 수신
curl -X POST http://localhost:443/api/v1/input-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "UdpInputAdapter",
    "messagetype": "syslog",
    "port": 514,
    "enabled": true
  }'

# 2. RFC3164 Syslog 파싱
curl -X POST http://localhost:443/api/v1/parsers \
  -H "Content-Type: application/json" \
  -d '{
    "type": "RFC3164SyslogParser",
    "messagetype": "syslog",
    "priority": 1,
    "enabled": true
  }'

# 3. Kafka로 전송
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "KafkaOutputAdapter",
    "messagetype": "syslog",
    "bootstrapservers": "kafka:9092",
    "topicid": "syslog-events",
    "enabled": true
  }'

# 4. 적용
curl -X POST http://localhost:443/api/v1/pipeline/reload
```

### 예제 2: Apache 액세스 로그 처리

```bash
# 1. 파일에서 로그 읽기
curl -X POST http://localhost:443/api/v1/input-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "FileInputAdapter",
    "messagetype": "apache",
    "path": "/var/log/apache2/access.log",
    "enabled": true
  }'

# 2. Regex 파싱 (Combined Log Format)
curl -X POST http://localhost:443/api/v1/parsers \
  -H "Content-Type: application/json" \
  -d '{
    "type": "RegexParser",
    "messagetype": "apache",
    "priority": 1,
    "param": "{\"pattern\": \"^(?<ip>\\\\S+) \\\\S+ (?<user>\\\\S+) \\\\[(?<timestamp>[^\\\\]]+)\\\\] \\\"(?<method>\\\\S+) (?<path>\\\\S+) (?<protocol>\\\\S+)\\\" (?<status>\\\\d+) (?<size>\\\\d+)\"}",
    "enabled": true
  }'

# 3. 4xx, 5xx 에러만 필터링
curl -X POST http://localhost:443/api/v1/transforms \
  -H "Content-Type: application/json" \
  -d '{
    "type": "Filter",
    "messagetype": "apache",
    "priority": 1,
    "filterPass": "{\"status\": [\"400\", \"401\", \"403\", \"404\", \"500\", \"502\", \"503\", \"504\"]}",
    "enabled": true
  }'

# 4. OpenSearch로 전송
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "OpenSearchOutputAdapter",
    "messagetype": "apache",
    "url": "https://opensearch:9200",
    "indexTemplate": "apache-errors-%{yyyy.MM}",
    "osUsername": "admin",
    "osPassword": "admin",
    "enabled": true
  }'

# 5. 적용
curl -X POST http://localhost:443/api/v1/pipeline/validate-and-reload
```

### 예제 3: 다중 출력 (Fan-out)

동일한 messagetype에 여러 Output Adapter를 연결하여 여러 곳으로 전송

```bash
# 같은 messagetype "webapp"에 여러 Output 생성

# OpenSearch
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "OpenSearchOutputAdapter",
    "messagetype": "webapp",
    "url": "https://opensearch:9200",
    "indexTemplate": "webapp-%{yyyy.MM.dd}",
    "enabled": true
  }'

# Kafka
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "KafkaOutputAdapter",
    "messagetype": "webapp",
    "bootstrapservers": "kafka:9092",
    "topicid": "webapp-logs",
    "enabled": true
  }'

# HTTP Webhook
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "HttpOutputAdapter",
    "messagetype": "webapp",
    "url": "https://alerting.example.com/webhook",
    "method": "POST",
    "enabled": true
  }'

# 적용
curl -X POST http://localhost:443/api/v1/pipeline/reload
```

---

## 문제 해결

### 1. 설정이 적용되지 않음

**원인**: 설정 변경 후 파이프라인 리로드를 하지 않음

**해결**:
```bash
curl -X POST http://localhost:443/api/v1/pipeline/reload
```

### 2. 파싱이 실패함

**원인**: Parser의 messagetype이 Input Adapter의 messagetype과 일치하지 않음

**확인**:
```bash
# Input Adapter 확인
curl -X GET http://localhost:443/api/v1/input-adapters/messagetype/webapp

# Parser 확인
curl -X GET http://localhost:443/api/v1/parsers/messagetype/webapp
```

### 3. Transform이 동작하지 않음

**원인**: priority 설정 오류 또는 messagetype 불일치

**확인**:
```bash
# messagetype별 Transform 조회 (priority 순으로 정렬됨)
curl -X GET http://localhost:443/api/v1/transforms/messagetype/webapp
```

**해결**: priority를 올바르게 설정 (낮을수록 먼저 실행)

### 4. OpenSearch 연결 실패

**원인**: 네트워크 문제, 인증 오류, SSL 인증서 문제

**확인**:
```bash
# OpenSearch 연결 테스트
curl -k -u admin:password https://opensearch:9200/_cluster/health
```

### 5. 리로드 진행 중 중단

**확인**:
```bash
curl -X GET http://localhost:443/api/v1/pipeline/reload-progress
```

**해결**:
```bash
# 리로드 취소
curl -X POST http://localhost:443/api/v1/pipeline/cancel-reload

# 다시 시도
curl -X POST http://localhost:443/api/v1/pipeline/reload
```

---

## 자동 파이프라인 재시작

Logparser는 REST API를 통해 설정을 변경하면 **자동으로 파이프라인을 재시작**하는 기능을 제공합니다.

### 동작 원리

1. REST API를 통해 Input Adapter, Parser, Transform, Output Adapter를 생성/수정/삭제
2. ConfigChangeListener (AOP)가 설정 변경 감지
3. 2초 후 자동으로 파이프라인 검증 및 리로드 실행
4. 기존 실행 중인 스레드 중지 → 새 설정 로드 → 새 스레드 시작

### 자동 재시작 프로세스

```
설정 변경 (REST API)
    ↓
트랜잭션 커밋
    ↓
AOP Interceptor (ConfigChangeListener)
    ↓
2초 대기 (트랜잭션 완료 보장)
    ↓
Input Adapters 중지
    ↓
큐 처리 대기 (1초)
    ↓
Output Adapters 중지
    ↓
설정 검증
    ↓
Input Adapters 재시작 (새 설정)
    ↓
Output Adapters 재시작 (새 설정)
    ↓
파이프라인 실행 재개
```

### 예제: 자동 재시작 확인

```bash
# 1. 현재 파이프라인 상태 확인
curl -X GET http://localhost:443/api/v1/pipeline/status

# 응답:
# {
#   "status": "RUNNING",
#   "inputAdapterCount": 1,
#   "parserCount": 1,
#   "transformCount": 2,
#   "outputAdapterCount": 1,
#   "queueSize": 0,
#   "processedMessages": 0
# }

# 2. 새 Output Adapter 추가
curl -X POST http://localhost:443/api/v1/output-adapters \
  -H "Content-Type: application/json" \
  -d '{
    "type": "KafkaOutputAdapter",
    "messagetype": "webapp",
    "bootstrapservers": "kafka:9092",
    "topicid": "backup-logs",
    "enabled": true
  }'

# 응답:
# {
#   "id": 5,
#   "type": "KafkaOutputAdapter",
#   ...
# }

# 3. 로그 확인 (자동 재시작 발생)
# [INFO] Output Adapter configuration changed, scheduling pipeline reload in 2000ms
# [INFO] Auto-reloading pipeline after OutputAdapter configuration change
# [INFO] Stopping current pipeline components
# [INFO] Input adapters stopped
# [INFO] Output adapters stopped
# [INFO] Loading and validating configuration from database
# [INFO] Restarting pipeline components with new configuration
# [INFO] Input adapters restarted
# [INFO] Output adapters restarted
# [INFO] Pipeline reload completed successfully after OutputAdapter configuration change

# 4. 리로드 진행률 확인 (진행 중일 경우)
curl -X GET http://localhost:443/api/v1/pipeline/reload-progress

# 응답 (진행 중):
# {
#   "progress": 66,
#   "status": "RELOADING",
#   "inProgress": true
# }

# 응답 (완료):
# {
#   "progress": 100,
#   "status": "RUNNING",
#   "inProgress": false
# }

# 5. 파이프라인 상태 재확인
curl -X GET http://localhost:443/api/v1/pipeline/status

# 응답:
# {
#   "status": "RUNNING",
#   "inputAdapterCount": 1,
#   "parserCount": 1,
#   "transformCount": 2,
#   "outputAdapterCount": 2,  ← 증가!
#   "queueSize": 0,
#   "processedMessages": 15
# }
```

### 자동 재시작 설정

`application.yml`에서 자동 재시작 기능을 제어할 수 있습니다:

```yaml
logparser:
  # 자동 재시작 활성화/비활성화
  auto-reload: true

  # 설정 변경 후 재시작 전 대기 시간 (밀리초)
  # 트랜잭션이 완전히 커밋되도록 충분한 시간 필요
  auto-reload-delay-ms: 2000
```

**설정 옵션**:
- `auto-reload: true` (기본값): 설정 변경 시 자동 재시작
- `auto-reload: false`: 수동으로만 재시작 (REST API 호출 필요)
- `auto-reload-delay-ms`: 대기 시간 (기본: 2000ms = 2초)

### 수동 재시작

자동 재시작을 비활성화했거나 즉시 재시작이 필요한 경우:

```bash
# 검증 없이 빠른 리로드
curl -X POST http://localhost:443/api/v1/pipeline/reload

# 검증 후 안전한 리로드 (권장)
curl -X POST http://localhost:443/api/v1/pipeline/validate-and-reload

# 완전 재시작 (큐 비우기 포함)
curl -X POST http://localhost:443/api/v1/pipeline/restart
```

### 주의사항

1. **데이터 손실 방지**:
   - Input Adapter 중지 후 큐 처리 대기 시간(1초) 제공
   - 기존 큐에 있는 메시지는 처리 완료 후 재시작

2. **설정 검증**:
   - 재시작 전 자동으로 설정 무결성 검증
   - 검증 실패 시 재시작 중단 및 오류 로그

3. **동시성 제어**:
   - 리로드 진행 중 추가 리로드 요청 차단
   - "Reload already in progress" 오류 메시지 반환

4. **롤백**:
   - 재시작 실패 시 자동 롤백 없음
   - 설정을 이전 상태로 수동 복원 필요

### 모니터링

```bash
# 1. 리로드 진행률 모니터링
while true; do
  curl -s http://localhost:443/api/v1/pipeline/reload-progress | jq '.'
  sleep 1
done

# 2. 파이프라인 상태 모니터링
watch -n 1 'curl -s http://localhost:443/api/v1/pipeline/status | jq "."'

# 3. 로그 실시간 확인
tail -f /var/log/logparser/application.log | grep -E 'reload|Restarting|stopped|restarted'
```

---

## 베스트 프랙티스

### 1. messagetype 네이밍 규칙

- 소문자와 하이픈 사용: `webapp-logs`, `syslog-events`
- 명확하고 설명적인 이름 사용
- 환경별 구분: `prod-webapp`, `dev-webapp`

### 2. priority 설정

- **Parser**: 보통 1부터 시작
- **Transform**:
  - Filter: 1-10 (가장 먼저)
  - AddProperty: 11-20
  - RemoveProperty: 21-30 (가장 나중)

### 3. 설정 변경 워크플로우

```bash
# 1. 설정 변경
curl -X PUT ...

# 2. 검증 (선택)
curl -X POST http://localhost:443/api/v1/pipeline/validate-and-reload

# 3. 상태 확인
curl -X GET http://localhost:443/api/v1/pipeline/status

# 4. 로그 모니터링
tail -f /var/log/logparser/application.log
```

### 4. 보안

- 프로덕션 환경에서는 HTTPS 사용
- API 엔드포인트에 인증/인가 추가 권장
- 비밀번호 필드 암호화 (CryptoConverter 사용)

### 5. 성능 최적화

- OpenSearch: `batchSize`와 `flushIntervalMs` 조정
- Kafka: 적절한 토픽 파티션 설정
- 파서/변환 스레드 수 조정: `application.yml`의 `parser_threads`

---

## 참고 자료

- [README.md](README.md) - 전체 프로젝트 개요
- [todo.md](todo.md) - 알려진 이슈 및 개선 사항
- REST API Swagger UI: `http://localhost:443/swagger-ui.html` (구현 시)

---

**작성일**: 2025-11-22
**작성자**: Claude Code
