# 1. Transform 구현 사항

수집된 로그 데이터를 정형화된 **Logical Schema**로 변환하여, 추후 다양한 분석 및 저장소(RDB, NoSQL, SIEM 등)에서 활용할 수 있는 표준 구조를 정의합니다.
*   **Internal Representation:** 내부적으로는 유연한 `Map<String, Object>` 구조를 유지하며, 엄격한 RDB 테이블 구조에 얽매이지 않습니다.
*   **Output Agnostic:** 변환된 데이터는 Output Adapter의 구현에 따라 RDB에 저장될 수도, Elasticsearch에 인덱싱될 수도 있습니다.
*   **Schema Definition:** 아래 SQL 구문은 데이터의 **논리적 구조(Type & Structure)**를 설명하기 위한 예시이며, 실제 물리적 저장소 스키마와는 다를 수 있습니다.

## 2. 핵심 논리적 데이터 모델 (Logical Data Model)

```
event (공통 필드)
 ├─ event_network
 ├─ event_auth
 ├─ event_web
 ├─ event_dns
 ├─ event_endpoint
 ├─ event_malware
 ├─ event_ids
 ├─ event_email
 ├─ event_file
 ├─ event_vulnerability
 └─ event_change
```

---

### 공통 이벤트 모델 (CORE)

#### `event`

> **모든 이벤트가 공유하는 공통 필드 집합**

```sql
CREATE TABLE event (
    event_id            BIGINT PRIMARY KEY,
    event_time          TIMESTAMP NOT NULL,
    ingest_time         TIMESTAMP NOT NULL,

    event_category      VARCHAR(50) NOT NULL,
    event_type          VARCHAR(50) NOT NULL,
    event_action        VARCHAR(50),
    event_result        VARCHAR(20),

    severity            SMALLINT,

    src_ip              INET,
    src_port            INTEGER,
    dst_ip              INET,
    dst_port            INTEGER,
    protocol            VARCHAR(20),

    src_host            VARCHAR(255),
    dst_host            VARCHAR(255),

    user_name           VARCHAR(255),
    user_id             VARCHAR(255),

    log_source          VARCHAR(255),
    raw_log             TEXT
);
```

#### 컬럼 설명

| 컬럼              | 설명                                        |
| --------------- | ----------------------------------------- |
| event_id        | 내부 고유 이벤트 ID                              |
| event_time      | 실제 발생 시각                                  |
| ingest_time     | 시스템 수집 시각                                |
| event_category  | high-level 분류 (auth, network, endpoint 등) |
| event_type      | 세부 유형 (login, flow, dns_query 등)          |
| event_action    | allow, deny, create, delete               |
| event_result    | success / failure / unknown               |
| severity        | 1~10 또는 1~100                             |
| src_ip / dst_ip | 통신 주체 IP                                  |
| src_port/dst_port| 통신 포트                                    |
| protocol        | tcp, udp, icmp                            |
| user_name       | 사용자 계정명                                  |
| log_source      | 로그 발생 소스                                 |
| raw_log         | 원본 로그 텍스트                               |

---

### 세부 도메인 모델

#### 4. 네트워크 도메인 `event_network`

```sql
CREATE TABLE event_network (
    event_id        BIGINT PRIMARY KEY,
    bytes_in        BIGINT,
    bytes_out       BIGINT,
    packets_in     BIGINT,
    packets_out    BIGINT,
    direction       VARCHAR(20),
    session_id      VARCHAR(128),
    duration_ms     BIGINT
);
```

| 컬럼           | 설명                 |
| ------------ | ------------------ |
| bytes_in/out | 송수신 바이트            |
| packets_*    | 패킷 수               |
| direction    | inbound / outbound |
| session_id   | 세션 식별자             |
| duration_ms  | 세션 지속 시간           |

---

#### 5. 인증(Authentication) `event_auth`

```sql
CREATE TABLE event_auth (
    event_id                BIGINT PRIMARY KEY,
    auth_method             VARCHAR(50),
    auth_protocol           VARCHAR(50),
    failure_reason          VARCHAR(255),
    mfa_used                BOOLEAN
);
```

| 컬럼             | 설명                    |
| -------------- | --------------------- |
| auth_method    | password, certificate |
| auth_protocol  | ldap, kerberos        |
| failure_reason | 실패 사유                 |
| mfa_used       | MFA 여부                |

---

#### 6. Web / Proxy `event_web`

```sql
CREATE TABLE event_web (
    event_id        BIGINT PRIMARY KEY,
    http_method     VARCHAR(10),
    url             TEXT,
    uri_path        TEXT,
    http_status     INTEGER,
    user_agent      TEXT,
    referer         TEXT,
    bytes           BIGINT
);
```

---

#### 7. DNS / Network Resolution `event_dns`

```sql
CREATE TABLE event_dns (
    event_id        BIGINT PRIMARY KEY,
    query_name      VARCHAR(255),
    query_type      VARCHAR(20),
    response_code   VARCHAR(20),
    answer          TEXT
);
```

---

#### 8. Endpoint / Host `event_endpoint`

```sql
CREATE TABLE event_endpoint (
    event_id        BIGINT PRIMARY KEY,
    host_name       VARCHAR(255),
    os_name         VARCHAR(100),
    os_version      VARCHAR(50),
    process_name    VARCHAR(255),
    process_id      INTEGER,
    parent_process  VARCHAR(255),
    command_line    TEXT
);
```

---

#### 9. Malware `event_malware`

```sql
CREATE TABLE event_malware (
    event_id        BIGINT PRIMARY KEY,
    malware_name    VARCHAR(255),
    malware_type    VARCHAR(50),
    detection_engine VARCHAR(100),
    action_taken    VARCHAR(50)
);
```

---

#### 10. IDS / IPS `event_ids`

```sql
CREATE TABLE event_ids (
    event_id        BIGINT PRIMARY KEY,
    signature_id    VARCHAR(100),
    signature_name  TEXT,
    attack_type     VARCHAR(100),
    rule_severity   SMALLINT
);
```

---

#### 11. Email `event_email`

```sql
CREATE TABLE event_email (
    event_id        BIGINT PRIMARY KEY,
    mail_from       VARCHAR(255),
    mail_to         TEXT,
    subject         TEXT,
    attachment_cnt  INTEGER,
    spam_score      DECIMAL(5,2)
);
```

---

#### 12. File / Data Access `event_file`

```sql
CREATE TABLE event_file (
    event_id        BIGINT PRIMARY KEY,
    file_path       TEXT,
    file_name       VARCHAR(255),
    file_hash       VARCHAR(128),
    operation       VARCHAR(50)
);
```

---

#### 13. Vulnerability `event_vulnerability`

```sql
CREATE TABLE event_vulnerability (
    event_id        BIGINT PRIMARY KEY,
    cve_id          VARCHAR(50),
    cvss_score      DECIMAL(3,1),
    severity        VARCHAR(20),
    status          VARCHAR(20)
);
```

---

#### 14. Change / Configuration `event_change`

```sql
CREATE TABLE event_change (
    event_id        BIGINT PRIMARY KEY,
    object_type     VARCHAR(50),
    object_name     VARCHAR(255),
    change_type     VARCHAR(50),
    before_value    TEXT,
    after_value     TEXT
);

# 3. 구현을 위한 추가 요구사항 및 점검 사항

위 스키마 구조를 실제 코드(`TransformService` 및 UI)로 구현하기 위해 사전에 확정하거나 추가 개발이 필요한 제반 사항입니다.

## 3.1. 매핑 설정 (Mapping Configuration)
파서(Parser)를 통해 추출된 비정형 `LogEvent.fields` (Map<String, Object>) 데이터를 정형화된 테이블 컬럼에 매핑하는 메타데이터 관리 기능입니다.

*   **Dual-Layer Mapping:**
    *   **Core Mapping:** 모든 이벤트는 기본적으로 `event` 테이블(공통 필드: `src_ip`, `event_time` 등)에 대한 매핑을 포함해야 합니다.
    *   **Sub-Table Mapping:** 조건에 따라 선택된 서브 테이블(`event_web` 등)의 전용 필드를 추가로 매핑합니다.
*   **Rule-based Categorization:**
    *   `messagetype`과 `LogEvent.fields` 데이터를 기반으로 서브 테이블을 결정하는 조건식 구현.
    *   예: `dest_port == 80` OR `protocol == 'HTTP'` -> `event_web` 자동 선택.
*   **Field Mapping Strategy:**
    *   **Direct Mapping:** 소스 필드명과 타겟 컬럼명이 다를 경우 수동 연결.
    *   **Default Value:** 소스 데이터가 없을 경우 기본값(Default) 설정 기능.

## 3.2. 데이터 타입 변환 및 검증 (Type Conversion & Validation)
DB 스키마는 엄격한 타입(`INET`, `TIMESTAMP`, `BIGINT`, `BOOLEAN`)을 요구하므로, Transform 단계에서 강력한 타입 캐스팅 및 유효성 검사가 선행되어야 합니다.

*   **Timestamp Normalization:** 다양한 날짜 포맷(Syslog, ISO8601 등)을 표준 `TIMESTAMP`로 통일.
*   **IP Address Validation:** `src_ip`, `dst_ip` 유효성 검증 및 실패 시 NULL 처리/에러 로깅.
*   **Numeric Parsing:** 문자열 숫자(`"1,000"`)를 `BIGINT` 등으로 안전하게 변환.
*   **Truncation:** 컬럼 길이(`VARCHAR`) 초과 시 데이터 자동 자르기(Truncate).

## 3.3. ID 생성 전략 (Global Unique ID)
*   **Long type UUID:** 분산 환경에서 유일성을 보장하며 인덱싱 효율이 좋은 ID 생성 전략 수립 (예: Snowflake ID).

## 3.4. 확장 필드 처리 (Unmapped Fields Handling)
*   **JSON Column:** 매핑되지 않은 나머지 필드는 `event` 테이블의 `additional_attributes` (JSON) 컬럼에 일괄 저장하여 데이터 유실 방지.

## 3.5. Output Adapter 연동 고려사항
*   **Transaction:** `event` 테이블과 서브 테이블(`event_web`)에 대한 Insert가 트랜잭션으로 묶이거나, 올바른 순서(FK 고려)로 처리되어야 함.

## 3.6. UI 구성 요구사항 (UX Optimization)
다수의 필드를 효율적으로 매핑하기 위한 사용자 친화적 UI가 필수적입니다.
*   **Auto-Mapping (Smart Match):** 소스 필드명과 타겟 컬럼명이 유사할 경우(예: `client_ip` ≈ `src_ip`) 버튼 클릭 한 번으로 자동 매핑되는 기능.
*   **Custom Source Field Entry:** 자동 감지된 필드 목록에 없는 경우에도 사용자가 직접 소스 필드명을 입력하여 추가하고 매핑에 사용할 수 있는 기능.
*   **Bulk Mapping Interface:**
    *   필드가 많을 경우 Drag & Drop은 비효율적이므로, **리스트/그리드 뷰**에서 Dropdown이나 검색을 통해 빠르게 매핑할 수 있는 인터페이스 병행 제공.
*   **Schema Mapper UI:** 소스 필드(List) <-> 타겟 스키마(Tree/List) 구조의 시각적 매핑 도구.
*   **Type Simulator:** 샘플 로그 입력 시 변환 결과 및 타입 에러 여부를 실시간으로 확인하는 시뮬레이터.
```
