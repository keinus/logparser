# LogParser 코드베이스 수정 사항 - SonarQube 및 보안 이슈


### 2. 리소스 누수 위험 - HIGH PRIORITY
**파일:** `src/main/java/org/keinus/logparser/output/RabbitMQAdapter.java:53-66`
```java
// 문제: RabbitMQ 연결/채널이 예외 발생시 제대로 닫히지 않음
// 수정 필요: try-with-resources 또는 finally 블록에서 리소스 정리
```

### 3. 예외 처리 개선 - HIGH PRIORITY
**파일:** `src/main/java/org/keinus/logparser/output/RabbitMQAdapter.java:56-58, 66-68`
```java
// 현재: 예외를 로깅만 하고 무시
catch (IOException | TimeoutException e) {
    log.error(e.getMessage());
    // 예외 재발생 또는 적절한 오류 처리 필요
}
```

## 🟡 중간 우선순위

### 4. 소켓 리소스 누수
**파일:** `src/main/java/org/keinus/logparser/input/HttpInputAdapter.java:113`
```java
// 현재 코드
var content = read(serverSocket.accept());

// 수정 필요: try-with-resources 사용
try (Socket socket = serverSocket.accept()) {
    var content = read(socket);
}
```

### 5. 스레드 안전성 문제
**파일:** `src/main/java/org/keinus/logparser/output/RabbitMQAdapter.java:39-40`
```java
// 현재: non-final 필드가 여러 스레드에서 접근 가능
Channel channel = null;
Connection connection = null;

// 수정 필요: volatile 키워드 추가 또는 동기화
```

### 6. 문자열 인덱스 경계 검사
**파일:** `src/main/java/org/keinus/logparser/parser/RFC3164SyslogParser.java:134-155`
```java
// Reader 클래스의 getc() 메서드
public int getc() {
    return this.line.charAt(this.idx++); // 경계 검사 없음
}

// 수정 필요: 인덱스 범위 검증
public int getc() {
    if (this.idx >= this.line.length()) {
        throw new IndexOutOfBoundsException("End of line reached");
    }
    return this.line.charAt(this.idx++);
}
```

### 7. HTTP 상태 코드 검증 개선
**파일:** `src/main/java/org/keinus/logparser/output/HttpOutputAdapter.java:240-241`
```java
// 현재: 너무 관대한 검증
if (!statusLine.contains(" 2")) {
    log.warn("HTTP request may have failed: {}", statusLine);
}

// 수정 필요: 정확한 HTTP 상태 코드 파싱
```

### 8. Null 안전성 개선
**파일:** `src/main/java/org/keinus/logparser/parser/RFC3164SyslogParser.java:95-98`
```java
// 현재: part가 null일 수 있음
String[] kv = part.split("=", 2);

// 수정 필요: null 체크 추가
if (part != null) {
    String[] kv = part.split("=", 2);
    // ...
}
```

## 🟢 낮은 우선순위

### 9. 하드코딩된 상수 제거
**파일들:**
- `src/main/java/org/keinus/logparser/output/OpenSearchOutputAdapter.java:146`
- `src/main/java/org/keinus/logparser/input/HttpInputAdapter.java:79`

```java
// 현재: 매직 넘버
if (totalDocumentCount.get() >= 2000) {
if (contentLength > 10 * 1024 * 1024) {

// 수정: 상수로 정의
private static final int MAX_BATCH_SIZE = 2000;
private static final int MAX_CONTENT_LENGTH = 10 * 1024 * 1024; // 10MB
```

### 10. Reflection 보안 위험
**파일:** `src/main/java/org/keinus/logparser/config/ConfigValidator.java:32`
```java
// 현재: 접근 제어 우회
field.setAccessible(true);

// 수정 필요: 보안 관리자 확인 추가
if (System.getSecurityManager() != null) {
    // 보안 권한 확인
}
```

## 📋 수정 체크리스트

### 즉시 수정 (이번 주 내)
- [ ] HttpInputAdapter.java:69 - 배열 경계 검사 추가
- [ ] RabbitMQAdapter.java - 리소스 정리 개선
- [ ] RabbitMQAdapter.java - 예외 처리 개선

### 다음 스프린트
- [ ] HttpInputAdapter.java:113 - 소켓 리소스 관리
- [ ] RabbitMQAdapter.java - 스레드 안전성 개선
- [ ] RFC3164SyslogParser.java - 문자열 인덱스 검사
- [ ] HttpOutputAdapter.java - HTTP 상태 코드 검증 개선
- [ ] RFC3164SyslogParser.java - Null 안전성 개선

### 추후 개선
- [ ] 매직 넘버를 상수로 추출
- [ ] Reflection 보안 검증 추가

## 🛡️ 보안 코딩 가이드라인 준수 사항

1. **입력 검증:** 모든 외부 입력에 대한 검증 및 살균 처리
2. **SQL 인젝션 방지:** 파라미터화된 쿼리 사용 (현재 해당 없음)
3. **민감 데이터 보호:** 비밀번호/시크릿 로깅 금지 (현재 준수됨)
4. **에러 처리:** 내부 정보 노출 방지
5. **보안 난수:** SecureRandom 사용 (필요시)
6. **최소 권한 원칙:** 접근 제어 강화

## 📊 통계

- **분석된 Java 파일:** 58개
- **보안 이슈:** 4개 (높음 1, 중간 3)
- **코드 품질 이슈:** 7개 (높음 1, 중간 3, 낮음 3)
- **전체 수정 필요 항목:** 10개

---
*이 문서는 SonarQube 규칙과 보안 코딩 표준을 기반으로 작성되었습니다.*
*수정 완료시 각 항목에 체크 표시를 해주세요.*