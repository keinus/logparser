# 성능 문제 및 메모리 누수 검토 결과

---

## 🟢 **보통 (Low Priority) - 개선 권장**

### 13. MessageDispatcher - 큐 오버플로우 관리

**파일:** `src/main/java/org/keinus/logparser/components/MessageDispatcher.java:37, 83-84`

**권장사항:**
- 큐 모니터링 추가
- 오버플로우 전략 구현 (가장 오래된 항목 삭제 등)
- 백프레셔 메커니즘 구현

---

### 14. MergingHashMap - 무제한 증가

**파일:** `src/main/java/org/keinus/logparser/core/util/MergingHashMap.java:31, 34`

**권장사항:** 최대 크기 제한 추가

---

### 15. Regex 패턴 캐싱

**파일:** 여러 Parser 파일들

**권장사항:** Pattern.compile() 결과를 캐시하여 CPU 오버헤드 감소

---

## 📊 **테스트 권장사항**

### 1. 부하 테스트
- 높은 메시지 볼륨으로 busy waiting 확인
- 초당 10,000+ 메시지 처리 테스트

### 2. 장애 테스트
- OpenSearch, Kafka, RabbitMQ 연결 끊기
- 메모리 증가 모니터링

### 3. 장기 실행 테스트
- 24시간+ 연속 실행
- 느린 메모리 누수 확인

### 4. 리소스 모니터링
- 스레드 수 모니터링
- 파일 디스크립터 모니터링
- 메모리 사용량 모니터링
- CPU 사용률 모니터링

### 5. 스트레스 테스트
- 여러 입력 소스 동시 사용
- 출력 어댑터 동시 실행

---

## 🎯 **수정 우선순위**

### 즉시 (이번 주 내)
1. ✅ InputAdaptorComponent busy waiting (Issue #1)
2. ✅ OpenSearchOutputAdapter 무제한 누적 (Issue #2)
3. ✅ ThreadManager 메모리 누수 (Issue #3)
4. ✅ TcpOutputAdapter 재시도 루프 (Issue #4)
5. ✅ TcpOutputAdapter 소켓 재사용 (Issue #5)

### 빠른 시일 내 (다음 주)
6. ⬜ Kafka Consumer 미닫힘 (Issue #6)
7. ⬜ CustomExecutorService 메모리 누수 (Issue #7)
8. ⬜ HttpOutputAdapter 소켓 누수 (Issue #8)
9. ⬜ HTTP Client 미닫힘 (Issue #9)
10. ⬜ RabbitMQ 리소스 미해제 (Issue #10)
11. ⬜ Scheduler 미종료 (Issue #11)
12. ⬜ File Reader 누수 (Issue #12)

### 개선 (한 달 내)
13. ⬜ 큐 오버플로우 관리
14. ⬜ MergingHashMap 크기 제한
15. ⬜ Regex 패턴 캐싱
16. ⬜ 성능 메트릭 및 모니터링 추가
17. ⬜ Graceful degradation 전략 구현

---

## 💡 **추가 권장사항**

### 모니터링 도구 추가
```java
// JMX MBean으로 모니터링 노출
public interface LogParserMonitoringMBean {
    long getQueueSize();
    long getProcessedMessages();
    long getFailedMessages();
    double getCpuUsage();
    long getMemoryUsage();
    int getActiveThreads();
}
```

### 서킷 브레이커 패턴 구현
```java
// 백엔드 장애 시 자동으로 요청 차단
public class CircuitBreaker {
    private int failureCount = 0;
    private static final int THRESHOLD = 5;
    private State state = State.CLOSED;

    public boolean allowRequest() {
        if (state == State.OPEN) {
            // 일정 시간 후 재시도
            if (shouldAttemptReset()) {
                state = State.HALF_OPEN;
                return true;
            }
            return false;
        }
        return true;
    }

    public void recordSuccess() {
        failureCount = 0;
        state = State.CLOSED;
    }

    public void recordFailure() {
        failureCount++;
        if (failureCount >= THRESHOLD) {
            state = State.OPEN;
        }
    }
}
```

### Dead Letter Queue 구현
```java
// 반복 실패한 메시지를 별도 큐로 이동
public class DeadLetterQueue {
    private final BlockingQueue<FailedMessage> dlq = new LinkedBlockingQueue<>(1000);

    public void add(String message, String reason, int retryCount) {
        dlq.offer(new FailedMessage(message, reason, retryCount, System.currentTimeMillis()));
    }

    // 주기적으로 파일에 저장하거나 별도 저장소에 저장
    public void flush() {
        // DLQ 내용을 파일 또는 DB에 저장
    }
}
```

---

## 📝 **코드 리뷰 체크리스트**

향후 코드 작성 시 다음 사항을 확인:

- [ ] 모든 루프에 적절한 sleep/wait 있는가?
- [ ] 모든 리소스(스트림, 소켓, 연결)가 finally 또는 try-with-resources로 닫히는가?
- [ ] 컬렉션에 최대 크기 제한이 있는가?
- [ ] 재시도 로직에 최대 횟수와 대기 시간이 있는가?
- [ ] 스레드/스케줄러가 적절히 종료되는가?
- [ ] 타임아웃이 모든 I/O 작업에 설정되어 있는가?
- [ ] 에러 처리가 리소스 누수를 방지하는가?

---

**검토 완료일:** 2025-11-20
**검토자:** Claude Code
**다음 검토 예정일:** 수정 완료 후 재검토 필요
