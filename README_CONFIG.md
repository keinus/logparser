# Logparser 설정 (config.yaml)

이 문서는 Logparser 애플리케이션에서 사용하는 `config.yaml` 파일의 구조와 매개변수에 대해 설명합니다.

## 전역 설정

이 설정들은 `config.yaml` 파일의 `logparser` 섹션 최상위에서 설정됩니다.

| 매개변수 | 설명 | 기본값 |
|---|---|---|
| `parser_threads` | 로그 메시지 파싱에 사용할 스레드 수. | `4` |
| `queue_size` | 입력과 파서 사이의 메시지를 버퍼링하기 위한 내부 큐의 크기. | `20000` |
| `flush_interval` | OpenSearch와 같은 일부 출력 어댑터가 버퍼를 플러시하는 간격(밀리초). | `5000` |

예시:
```yaml
logparser:
  parser_threads: 4
  queue_size: 20000
  flush_interval: 5000
  ...
```

## 파이프라인

Logparser 애플리케이션은 `input`, `parser`, `transform`, `output`의 네 단계로 구성된 파이프라인으로 설정됩니다. 각 단계는 어댑터 목록으로 구성됩니다.

- **input**: 다양한 소스에서 로그 메시지를 수신합니다.
- **parser**: 원시 로그 메시지를 구조화된 형식으로 파싱합니다.
- **transform**: 파싱된 로그 메시지를 수정합니다.
- **output**: 처리된 로그 메시지를 다양한 대상으로 보냅니다.

## 입력 어댑터

### FakeInputAdapter

테스트 목적으로 가짜 로그 데이터를 생성합니다.

- **type**: `FakeInputAdapter`
- **매개변수**: 없음

예시:
```yaml
input:
  - type: FakeInputAdapter
    messagetype: eve
```

### FileInputAdapter

`tail -f`와 유사하게 파일에서 로그 메시지를 읽습니다.

- **type**: `FileInputAdapter`
- **매개변수**:
    - `path` (필수): 로그 파일의 경로.
    - `isFromBeginning`: 파일 처음부터 읽을지 여부. 기본값은 `false`.

예시:
```yaml
input:
  - type: FileInputAdapter
    path: /var/log/app.log
    messagetype: syslog
    isFromBeginning: true
```

### HttpInputAdapter

HTTP POST 요청을 통해 로그 메시지를 수신합니다.

- **type**: `HttpInputAdapter`
- **매개변수**:
    - `port` (필수): 수신 대기할 포트.

예시:
```yaml
input:
  - type: HttpInputAdapter
    port: 8080
    messagetype: http
```

### KafkaInputAdapter

Kafka 토픽에서 로그 메시지를 수신합니다.

- **type**: `KafkaInputAdapter`
- **매개변수**:
    - `bootstrapservers` (필수): 쉼표로 구분된 Kafka 브로커 주소 목록.
    - `topicid` (필수): 구독할 Kafka 토픽.

예시:
```yaml
input:
  - type: KafkaInputAdapter
    bootstrapservers: "kafka1:9092,kafka2:9092"
    topicid: logs
    messagetype: json
```

### TcpInputAdapter

TCP 연결을 통해 한 줄에 하나의 메시지로 로그 메시지를 수신합니다.

- **type**: `TcpInputAdapter`
- **매개변수**:
    - `port` (필수): 수신 대기할 포트.

예시:
```yaml
input:
  - type: TcpInputAdapter
    port: 5140
    messagetype: syslog
```

### UdpInputAdapter

UDP를 통해 패킷당 하나의 메시지로 로그 메시지를 수신합니다.

- **type**: `UdpInputAdapter`
- **매개변수**:
    - `port` (필수): 수신 대기할 포트.

예시:
```yaml
input:
  - type: UdpInputAdapter
    port: 514
    messagetype: syslog
```

## 파서 어댑터

### GrokParser

Grok 패턴을 사용하여 비정형 텍스트 로그를 파싱합니다.

- **type**: `GrokParser`
- **매개변수**:
    - `param` (필수): 사용할 Grok 패턴.

예시:
```yaml
parser:
  - type: GrokParser
    messagetype: syslog
    param: "%{SYSLOGBASE} %{GREEDYDATA:message}"
```

### HttpParser

원시 HTTP 요청을 파싱하여 헤더와 본문을 분리합니다.

- **type**: `HttpParser`
- **매개변수**: 없음

예시:
```yaml
parser:
  - type: HttpParser
    messagetype: http
```

### JsonParser

JSON 형식의 로그 메시지를 파싱합니다.

- **type**: `JsonParser`
- **매개변수**: 없음

예시:
```yaml
parser:
  - type: JsonParser
    messagetype: json
```

### RegexParser

명명된 캡처 그룹이 있는 정규식을 사용하여 로그 메시지를 파싱합니다.

- **type**: `RegexParser`
- **매개변수**:
    - `param` (필수): 사용할 정규식.

예시:
```yaml
parser:
  - type: RegexParser
    messagetype: custom
    param: '(?<key>\w+)=(?<value>\w+)'
```

### RFC3164SyslogParser

RFC 3164 (BSD syslog) 형식의 로그 메시지를 파싱합니다.

- **type**: `RFC3164SyslogParser`
- **매개변수**: 없음

예시:
```yaml
parser:
  - type: RFC3164SyslogParser
    messagetype: syslog
```

### RFC5424SyslogParser

RFC 5424 (최신 syslog) 형식의 로그 메시지를 파싱합니다.

- **type**: `RFC5424SyslogParser`
- **매개변수**: 없음

예시:
```yaml
parser:
  - type: RFC5424SyslogParser
    messagetype: syslog
```

## 변환 어댑터

### AddProperty

기존 속성을 그룹화하여 새 중첩 속성을 추가합니다.

- **type**: `AddProperty`
- **매개변수**:
    - `param`:
        - `add` (필수): 키가 새 부모 속성이고 값이 이동할 기존 속성 목록인 맵.

예시:
```yaml
transform:
  - type: AddProperty
    messagetype: all
    param:
      add:
        user:
          - username
          - userid
```

### Filter

필드 값을 기준으로 메시지를 필터링합니다.

- **type**: `Filter`
- **매개변수**:
    - `param`:
        - `pass`: 허용할 필드와 쉼표로 구분된 값의 맵.
        - `drop`: 삭제할 필드와 쉼표로 구분된 값의 맵.

예시:
```yaml
transform:
  - type: Filter
    messagetype: all
    param:
      pass:
        level: "INFO,WARN,ERROR"
      drop:
        source: "health-check"
```

### RemoveProperty

메시지에서 속성을 제거합니다.

- **type**: `RemoveProperty`
- **매개변수**:
    - `param`:
        - `remove` (필수): 제거할 속성 목록.

예시:
```yaml
transform:
  - type: RemoveProperty
    messagetype: all
    param:
      remove:
        - "debug_info"
        - "temp_field"
```

## 출력 어댑터

### BenchmarkAdapter

처리량(초당 메시지 수)을 측정하고 기록합니다. 메시지를 다른 곳으로 보내지 않습니다.

- **type**: `BenchmarkAdapter`
- **매개변수**: 없음

예시:
```yaml
output:
  - type: BenchmarkAdapter
    messagetype: all
```

### ConsoleOutputAdapter

메시지를 콘솔에 출력합니다.

- **type**: `ConsoleOutputAdapter`
- **매개변수**: 없음

예시:
```yaml
output:
  - type: ConsoleOutputAdapter
    messagetype: all
```

### HttpOutputAdapter

POST 요청을 통해 HTTP 엔드포인트로 메시지를 보냅니다.

- **type**: `HttpOutputAdapter`
- **매개변수**:
    - `url` (필수): HTTP 엔드포인트의 URL.

예시:
```yaml
output:
  - type: HttpOutputAdapter
    url: "http://localhost:8080/logs"
    messagetype: all
```

### KafkaOutputAdapter

Kafka 토픽으로 메시지를 보냅니다.

- **type**: `KafkaOutputAdapter`
- **매개변수**:
    - `bootstrapservers` (필수): 쉼표로 구분된 Kafka 브로커 주소 목록.
    - `topicid` (필수): 발행할 Kafka 토픽.

예시:
```yaml
output:
  - type: KafkaOutputAdapter
    bootstrapservers: "kafka1:9092,kafka2:9092"
    topicid: processed-logs
    messagetype: all
```

### OpenSearchOutputAdapter

Bulk API를 사용하여 OpenSearch 또는 Elasticsearch 클러스터로 메시지를 보냅니다.

- **type**: `OpenSearchOutputAdapter`
- **매개변수**:
    - `url` (필수): OpenSearch 클러스터의 기본 URL.
    - `index` (필수): 인덱스 이름 또는 템플릿. `%{fieldname}` 또는 `yyyy.MM.dd`와 같은 날짜 패턴을 사용할 수 있습니다.
    - `username`: 기본 인증을 위한 사용자 이름.
    - `password`: 기본 인증을 위한 암호.

예시:
```yaml
output:
  - type: OpenSearchOutputAdapter
    url: "https://localhost:9200"
    index: "logparser-%{yyMMdd}"
    username: "admin"
    password: "password"
    messagetype: all
```

### RabbitMQAdapter

RabbitMQ exchange로 메시지를 보냅니다.

- **type**: `RabbitMQAdapter`
- **매개변수**:
    - `host` (필수): RabbitMQ 서버 호스트.
    - `port` (필수): RabbitMQ 서버 포트.
    - `username` (필수): 인증을 위한 사용자 이름.
    - `password` (필수): 인증을 위한 암호.
    - `exchange` (필수): 발행할 exchange의 이름.
    - `routingkey` (필수): 사용할 라우팅 키.

예시:
```yaml
output:
  - type: RabbitMQAdapter
    host: "localhost"
    port: 5672
    username: "guest"
    password: "guest"
    exchange: "logs"
    routingkey: "log.info"
    messagetype: all
```

### TcpOutputAdapter

TCP 서버로 메시지를 보냅니다.

- **type**: `TcpOutputAdapter`
- **매개변수**:
    - `host` (필수): TCP 서버 호스트.
    - `port` (필수): TCP 서버 포트.

예시:
```yaml
output:
  - type: TcpOutputAdapter
    host: "localhost"
    port: 5141
    messagetype: all
```

## 설정을 위한 API

Logparser 애플리케이션은 `config.yaml` 파일을 동적으로 관리하기 위한 REST API를 제공합니다.

- `GET /api/config`: 현재 설정을 검색합니다.
- `POST /api/config/{section}`: 섹션(`input`, `parser`, `transform`, `output`)에 새 설정을 추가합니다.
- `PUT /api/config/{section}/{index}`: 섹션의 기존 설정을 업데이트합니다.
- `DELETE /api/config/{section}/{index}`: 섹션에서 설정을 삭제합니다.
- `PUT /api/config/common`: 공통 설정(`parser_threads`, `queue_size`, `flush_interval`)을 업데이트합니다.