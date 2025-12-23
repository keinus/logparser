# Logparser (로그파서)

Logparser는 Spring Boot로 구축된 고성능, 유연성 및 동적 설정이 가능한 로그 처리 애플리케이션입니다. 다양한 소스에서 로그를 수집하고, 강력한 패턴을 사용하여 구문 분석(Parsing) 및 변환(Transform)한 후 구조화된 데이터를 여러 목적지로 전달할 수 있습니다.

## 주요 기능

*   **동적 파이프라인**: 애플리케이션 재시작 없이 입력, 파싱, 출력 단계를 동적으로 구성할 수 있습니다.
*   **데이터베이스 기반 설정**: 파이프라인 설정은 영속성 및 관리 용이성을 위해 SQLite에 저장됩니다.
*   **핫 리로드 (Hot Reload)**: 설정 변경사항을 자동으로 감지하고 즉시 적용합니다.
*   **고성능**: 논블로킹(Non-blocking) I/O 원칙을 기반으로 구축되었으며 처리량에 최적화되어 있습니다.
*   **보안**: 비밀번호와 같은 민감한 설정 데이터는 암호화되어 저장됩니다.

### 지원 구성 요소

**입력 어댑터 (Input Adapters):**
*   **File**: 파일을 추적(Tail)하여 새로운 라인을 수집합니다.
*   **HTTP**: HTTP POST 요청을 통해 로그를 수신합니다.
*   **Kafka**: Kafka 토픽에서 메시지를 소비합니다.
*   **TCP**: Raw TCP 연결을 통해 로그를 수신합니다.
*   **UDP**: UDP 패킷을 통해 로그를 수신합니다.
*   **Fake**: 테스트 및 벤치마킹을 위한 가상 로그를 생성합니다.

**파서 (Parsers):**
*   **Grok**: 강력한 Grok 패턴을 사용하여 비정형 텍스트를 분석합니다.
*   **JSON**: JSON 형식의 로그를 파싱합니다.
*   **Regex**: 사용자 정의 추출을 위해 정규 표현식을 사용합니다.
*   **Syslog**: RFC3164 및 RFC5424 시스로그 형식을 지원합니다.
*   **HTTP**: HTTP 전용 로그 형식을 파싱합니다.

**출력 어댑터 (Output Adapters):**
*   **Console**: 표준 출력(stdout)으로 로그를 출력합니다 (디버깅용).
*   **HTTP**: 외부 HTTP 엔드포인트로 로그를 전달합니다.
*   **Kafka**: Kafka 토픽으로 로그를 발행합니다.
*   **OpenSearch**: OpenSearch 클러스터에 로그를 인덱싱합니다.
*   **RabbitMQ**: RabbitMQ 익스체인지로 로그를 발행합니다.
*   **TCP**: TCP를 통해 로그를 전달합니다.
*   **Benchmark**: 처리 성능을 측정합니다 (데이터 폐기).

## 사전 요구 사항

*   **Java 21** 이상
*   **Gradle** (Wrapper 포함)

## 시작하기

### 빌드

제공된 Gradle 래퍼를 사용하여 프로젝트를 빌드합니다:

```bash
./gradlew build
```

### 실행

Gradle을 사용하여 직접 애플리케이션을 실행할 수 있습니다:

```bash
./gradlew bootRun
```

또는 빌드된 JAR 파일을 실행합니다:

```bash
java -jar build/libs/logparser-0.2.3.jar
```

## 설정 (Configuration)

### 애플리케이션 설정
주요 설정 파일은 `src/main/resources/application.yml`에 위치합니다:

*   **서버 포트**: 기본값은 `8765`입니다.
*   **데이터베이스**: 기본적으로 SQLite가 사용됩니다 (`~/logparser/data/config.db`).
*   **보안**: 민감한 데이터 암호화를 위한 키 설정.

### 환경 변수

운영 환경에서는 다음과 같은 환경 변수를 설정하는 것을 **강력히 권장**합니다:

*   `SERVER_PORT`: 애플리케이션 실행 포트 (기본값: 8765).
*   `LOGPARSER_CRYPTO_KEY`: 암호화를 위한 32자 이상의 Base64 인코딩된 비밀키.
*   `LOGPARSER_CRYPTO_SALT`: 임의의 솔트(Salt) 값.

예시:
```bash
export LOGPARSER_CRYPTO_KEY="$(openssl rand -base64 32)"
export LOGPARSER_CRYPTO_SALT="$(openssl rand -hex 16)"
java -jar build/libs/logparser-0.2.3.jar
```

### 파이프라인 설정
파이프라인 설정(입력, 파서, 출력)은 데이터베이스를 통해 관리됩니다. 애플리케이션은 초기 설정 가져오기 및 마이그레이션을 지원합니다.

## API 문서

애플리케이션은 관리 및 모니터링을 위한 REST API를 제공합니다. Swagger UI를 통해 대화형 문서를 확인할 수 있습니다:

*   **URL**: `http://localhost:8765/swagger-ui.html`

## 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.