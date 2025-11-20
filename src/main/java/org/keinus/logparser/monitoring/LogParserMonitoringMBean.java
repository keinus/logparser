package org.keinus.logparser.monitoring;

/**
 * LogParser 애플리케이션의 모니터링 메트릭을 JMX를 통해 노출하는 MBean 인터페이스입니다.
 * <p>
 * 이 인터페이스는 Java Management Extensions (JMX)를 사용하여 런타임 메트릭을
 * 수집하고 모니터링할 수 있도록 합니다. JConsole, VisualVM, 또는 다른 JMX 클라이언트를
 * 통해 애플리케이션의 상태를 실시간으로 모니터링할 수 있습니다.
 * <p>
 * 주요 메트릭:
 * <ul>
 *     <li>큐 크기 및 사용률</li>
 *     <li>메시지 처리 통계</li>
 *     <li>시스템 리소스 사용량 (CPU, 메모리)</li>
 *     <li>스레드 정보</li>
 * </ul>
 *
 * @see javax.management.MXBean
 */
public interface LogParserMonitoringMBean {

    /**
     * 현재 전역 메시지 큐의 크기를 반환합니다.
     *
     * @return 큐에 있는 메시지 개수
     */
    long getGlobalQueueSize();

    /**
     * 현재 출력 메시지 큐의 크기를 반환합니다.
     *
     * @return 출력 큐에 있는 메시지 개수
     */
    long getOutputQueueSize();

    /**
     * 전역 큐의 최대 크기를 반환합니다.
     *
     * @return 큐의 최대 용량
     */
    long getMaxQueueSize();

    /**
     * 전역 큐의 사용률을 백분율로 반환합니다.
     *
     * @return 사용률 (0.0 ~ 100.0)
     */
    double getGlobalQueueUtilization();

    /**
     * 출력 큐의 사용률을 백분율로 반환합니다.
     *
     * @return 사용률 (0.0 ~ 100.0)
     */
    double getOutputQueueUtilization();

    /**
     * 시작 이후 수신한 총 메시지 수를 반환합니다.
     *
     * @return 수신한 메시지 수
     */
    long getTotalMessagesReceived();

    /**
     * 시작 이후 처리한 총 메시지 수를 반환합니다.
     *
     * @return 처리한 메시지 수
     */
    long getTotalMessagesProcessed();

    /**
     * 시작 이후 드롭된 총 메시지 수를 반환합니다.
     *
     * @return 드롭된 메시지 수
     */
    long getTotalMessagesDropped();

    /**
     * 시작 이후 실패한 총 메시지 수를 반환합니다.
     *
     * @return 실패한 메시지 수
     */
    long getTotalMessagesFailed();

    /**
     * 현재 프로세스의 CPU 사용률을 반환합니다.
     *
     * @return CPU 사용률 (0.0 ~ 100.0), 측정 불가 시 -1
     */
    double getCpuUsage();

    /**
     * 현재 힙 메모리 사용량을 바이트로 반환합니다.
     *
     * @return 사용 중인 힙 메모리 (bytes)
     */
    long getUsedMemory();

    /**
     * 최대 힙 메모리 크기를 바이트로 반환합니다.
     *
     * @return 최대 힙 메모리 (bytes)
     */
    long getMaxMemory();

    /**
     * 메모리 사용률을 백분율로 반환합니다.
     *
     * @return 메모리 사용률 (0.0 ~ 100.0)
     */
    double getMemoryUtilization();

    /**
     * 현재 활성 스레드 수를 반환합니다.
     *
     * @return 활성 스레드 수
     */
    int getActiveThreads();

    /**
     * 애플리케이션 가동 시간을 밀리초로 반환합니다.
     *
     * @return 가동 시간 (milliseconds)
     */
    long getUptimeMillis();

    /**
     * 애플리케이션 가동 시간을 읽기 쉬운 형식으로 반환합니다.
     *
     * @return 가동 시간 문자열 (예: "2d 3h 45m 12s")
     */
    String getUptimeFormatted();

    /**
     * 초당 처리 메시지 수를 반환합니다 (평균).
     *
     * @return 초당 메시지 처리량
     */
    double getMessagesPerSecond();

    /**
     * 패턴 캐시 히트율을 반환합니다.
     *
     * @return 히트율 (0.0 ~ 100.0)
     */
    double getPatternCacheHitRate();

    /**
     * 패턴 캐시 크기를 반환합니다.
     *
     * @return 캐시된 패턴 수
     */
    int getPatternCacheSize();

    /**
     * 모든 통계를 초기화합니다.
     */
    void resetStatistics();

    /**
     * 가비지 컬렉션을 강제로 실행합니다.
     */
    void forceGarbageCollection();

    /**
     * Dead Letter Queue의 현재 크기를 반환합니다.
     *
     * @return DLQ에 있는 메시지 수
     */
    int getDeadLetterQueueSize();

    /**
     * Dead Letter Queue의 최대 크기를 반환합니다.
     *
     * @return DLQ 최대 크기
     */
    int getDeadLetterQueueMaxSize();

    /**
     * Dead Letter Queue의 사용률을 반환합니다.
     *
     * @return DLQ 사용률 (0.0 ~ 100.0)
     */
    double getDeadLetterQueueUtilization();

    /**
     * Dead Letter Queue에 추가된 총 메시지 수를 반환합니다.
     *
     * @return 총 DLQ 메시지 수
     */
    long getTotalDeadLetterMessages();

    /**
     * Dead Letter Queue에서 파일로 저장된 총 메시지 수를 반환합니다.
     *
     * @return 총 flush된 DLQ 메시지 수
     */
    long getTotalFlushedDeadLetterMessages();

    /**
     * Dead Letter Queue를 즉시 파일에 저장합니다.
     *
     * @return 저장된 메시지 수
     */
    int flushDeadLetterQueue();
}
