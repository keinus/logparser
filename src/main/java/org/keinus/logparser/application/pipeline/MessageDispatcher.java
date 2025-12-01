package org.keinus.logparser.application.pipeline;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import org.keinus.logparser.infrastructure.config.ApplicationProperties;
import org.keinus.logparser.domain.parsing.service.ParseService;
import org.keinus.logparser.domain.transformation.service.TransformService;
import org.keinus.logparser.infrastructure.util.DeadLetterQueue;
import org.keinus.logparser.infrastructure.util.ThreadManager;
import org.keinus.logparser.infrastructure.util.ThreadUtil;
import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.infrastructure.monitoring.LogParserMonitoring;

/**
 * MessageDispatcher 클래스는 로그 메시지를 입력 어댑터로부터 받아와서 파싱하고,
 * 변환 후 출력 어댑터를 통해 전송하는 역할을 합니다.
 * 이 클래스는 Spring Framework의 @Service 애노테이션으로 선언되어 있으며,
 * 싱글톤 스코프로 관리됩니다.
 */
@Slf4j
@Service
public class MessageDispatcher {
    /**
     * 입력 메시지를 저장하는 BlockingQueue. 큐 크기는 10000으로 제한됩니다.
     */
    private final BlockingQueue<LogEvent> globalMessageQueue;

    /**
     * 출력 메시지를 저장하는 BlockingQueue의 HashMap. 큐 크기는 10000으로 제한됩니다.
     */
    private final BlockingQueue<LogEvent> outputMessageQueue;

    /**
     * 스레드 실행 지속 여부
     */
    private static final AtomicBoolean running = new AtomicBoolean(true);

    /**
     * 스레드 관리자
     */
    private ThreadManager threadManager;

    /**
     * ParseService 인스턴스
     */
    private ParseService parseService = null;

    /**
     * TransformService 인스턴스
     */
    private TransformService transformService = null;

    int threads = 1;

    // 큐 모니터링 메트릭
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
    private final AtomicLong totalMessagesDropped = new AtomicLong(0);
    private final AtomicLong totalMessagesFailed = new AtomicLong(0);

    // 큐 크기 및 임계값 설정
    private final int queueSize;
    private static final double QUEUE_WARNING_THRESHOLD = 0.8; // 80% 이상 시 경고
    private static final double QUEUE_CRITICAL_THRESHOLD = 0.95; // 95% 이상 시 위험

    // 타임아웃 및 대기 시간 상수
    private static final long NO_DATA_SLEEP_MS = 100;  // 데이터가 없을 때 대기 시간
    private static final long QUEUE_OFFER_TIMEOUT_MS = 5_000;  // 큐 삽입 시 최대 대기 시간 (5초)
    private static final long QUEUE_MONITORING_INTERVAL_MS = 30_000;  // 30초
    private static final long DLQ_FLUSH_INTERVAL_MS = 300_000;  // 5분

    // Parser 백프레셔 임계값 및 대기 시간
    private static final double PARSER_BACKPRESSURE_THRESHOLD_MEDIUM = 0.7;  // 70% - 중간 백프레셔
    private static final double PARSER_BACKPRESSURE_THRESHOLD_HIGH = 0.85;   // 85% - 높은 백프레셔
    private static final double PARSER_BACKPRESSURE_THRESHOLD_CRITICAL = 0.95; // 95% - 임계 백프레셔

    private static final long PARSER_BACKPRESSURE_SLEEP_MEDIUM_MS = 100;  // 70% 점유율 시 대기
    private static final long PARSER_BACKPRESSURE_SLEEP_HIGH_MS = 500;    // 85% 점유율 시 대기
    private static final long PARSER_BACKPRESSURE_SLEEP_CRITICAL_MS = 2000; // 95% 점유율 시 대기

    // Dead Letter Queue
    private DeadLetterQueue deadLetterQueue;

    // Circuit Breaker 관련
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    private static final long CIRCUIT_BREAKER_FAILURE_THRESHOLD = 10; // 연속 10번 실패 시 circuit open
    private static final long CIRCUIT_BREAKER_RESET_TIMEOUT_MS = 30_000; // 30초 후 재시도
    private volatile long circuitOpenedAt = 0;
    private enum CircuitState { CLOSED, OPEN, HALF_OPEN }
    private volatile CircuitState circuitState = CircuitState.CLOSED;

    /**
     * MessageDispatcher 생성자.
     * threadManager와 ApplicationProperties를 주입받아 초기화합니다.
     * 또한, 입력 어댑터와 출력 어댑터를 초기화하고,
     * parseAndTransform 메서드를 별도의 스레드로 실행시킵니다.
     *
     * @param threadManager 사용자 정의 thread 관리자
     * @param appProp       ApplicationProperties 설정
     */
    public MessageDispatcher(
            ThreadManager threadManager,
            ParseService parseService,
            TransformService transformService,
            ApplicationProperties applicationProperties,
            @Value("${log.message.queue-size:10000}") int queueSize) {

        this.threadManager = threadManager;
        this.queueSize = queueSize;
        this.globalMessageQueue = new LinkedBlockingQueue<>(queueSize);
        this.outputMessageQueue = new LinkedBlockingQueue<>(queueSize);

        this.parseService = parseService;
        this.transformService = transformService;
        this.threads = applicationProperties.getParserThreads();

        // Dead Letter Queue 초기화
        this.deadLetterQueue = new DeadLetterQueue();

        log.info("MessageDispatcher initialized with queue size: {}", queueSize);
    }

    /**
     * 메시지 디스패처를 초기화하고 파서 스레드를 시작
     * 메시지 디스패처를 초기화하고 파서 스레드를 시작합니다.
     * - 실행 상태 플래그 {@code running}을 활성화합니다.
     * - {@link ThreadManager}을 통해 지정된 수의 스레드를 생성하고, 각 스레드에서
     * {@link #parseAndTransform()} 작업을 병렬 실행합니다.
     * - 디스패처 시작 완료 시 INFO 레벨로 파서 스레드 수를 로깅합니다.
     *
     * @see ThreadManager#execute(Runnable)
     * @see Logger#info(String, Object...)
     */
    @PostConstruct
    public void startPipeline() {
        try {
            running.set(true);
            for (int i = 0; i < threads; i++) {
                String threadName = "LogParser-" + (i + 1);
                threadManager.executeWithName(threadName, this::parseAndTransform);
            }
            // 큐 모니터링 스레드 시작
            threadManager.executeWithName("QueueMonitor", this::monitorQueues);

            // DLQ flush 스레드 시작 (5분마다 flush)
            threadManager.executeWithName("DeadLetterQueueFlusher", this::flushDeadLetterQueue);

            log.info("MessageDispatcher started with {} parser threads and queue monitoring", threads);
        } catch (Exception e) {
            log.error("Failed to initialize input adapters.", e);
            throw new RuntimeException("ETL Pipeline startup failed", e);
        }
    }

    @PreDestroy
    public void stopPipeline() {
        try {
            // JMX 모니터링 해제
            LogParserMonitoring.unregisterMBean();

            close();
            log.info("All Output Adapters stopped successfully");
        } catch (Exception e) {
            log.error("Error during ETL Pipeline shutdown", e);
        }
    }

    /**
     * MessageDispatcher를 닫고 리소스를 해제하는 메서드.
     * 입력 메시지 큐에 있는 모든 메시지를 지우어 메모리나 자원을 해제합니다.
     * 사용자 정의 실행 서비스를 닫아서 모든 실행 중인 작업을 중단하고 관련된 리소스를 해제합니다.
     * 
     * @throws IOException I/O 오류가 발생할 경우 던지는 예외
     */
    public void close() throws IOException {
        // 먼저 running 플래그를 false로 설정하여 루프들이 종료되도록 함
        running.set(false);

        globalMessageQueue.clear();
        this.threadManager.shutdownAllThreads();
        log.info("Message Dispatcher closed");
    }

    public void parseAndTransform() {
        log.info("Parser thread started: {}", Thread.currentThread().getName());
        while (running.get()) {
            try {
                // Circuit breaker 상태 확인
                if (!checkCircuitBreaker()) {
                    ThreadUtil.sleep(1000); // Circuit이 open 상태면 대기
                    continue;
                }

                // Output queue 점유율 확인하여 백프레셔 적용 (메시지를 처리하기 전에)
                double outputUtilization = getOutputQueueUtilization();
                applyParserBackpressure(outputUtilization);

                LogEvent logEvent = globalMessageQueue.take();
                if (logEvent != null) {
                    processLogEvent(logEvent);
                    // 성공 시 실패 카운터 리셋
                    recordSuccess();
                } else {
                    ThreadUtil.sleep(NO_DATA_SLEEP_MS);
                }
            } catch (InterruptedException e) {
                // Interrupt는 정상적인 shutdown 시그널일 수 있으므로
                // running 플래그를 확인하여 처리
                if (!running.get()) {
                    log.info("Parser thread shutting down: {}", Thread.currentThread().getName());
                    return;
                }
                // running이 true면 계속 실행 (일시적인 interrupt)
                log.debug("Parser thread interrupted but continuing: {}", Thread.currentThread().getName());
            } catch (Exception e) {
                // 다른 예외 발생 시 circuit breaker에 기록
                recordFailure();
                log.error("Error processing log event (consecutive failures: {}), continuing...",
                        consecutiveFailures.get(), e);
                ThreadUtil.sleep(100); // 짧은 대기 후 재시도
            }
        }
        log.info("Parser thread finished: {}", Thread.currentThread().getName());
    }

    /**
     * Circuit breaker 상태를 확인하고 요청 처리 가능 여부를 반환합니다.
     * @return true면 처리 가능, false면 circuit이 open 상태
     */
    private boolean checkCircuitBreaker() {
        long currentTime = System.currentTimeMillis();

        switch (circuitState) {
            case CLOSED:
                // 정상 상태 - 요청 처리 가능
                return true;

            case OPEN:
                // Circuit이 open 상태 - timeout 확인
                if (currentTime - circuitOpenedAt >= CIRCUIT_BREAKER_RESET_TIMEOUT_MS) {
                    log.info("Circuit breaker transitioning to HALF_OPEN state after timeout");
                    circuitState = CircuitState.HALF_OPEN;
                    return true;
                }
                // 아직 timeout 전이면 요청 거부
                return false;

            case HALF_OPEN:
                // Half-open 상태 - 시험적으로 요청 처리
                return true;

            default:
                return true;
        }
    }

    /**
     * 요청 처리 성공을 기록합니다.
     */
    private void recordSuccess() {
        long failures = consecutiveFailures.getAndSet(0);

        if (circuitState == CircuitState.HALF_OPEN) {
            log.info("Circuit breaker transitioning to CLOSED state after successful request");
            circuitState = CircuitState.CLOSED;
        } else if (failures > 0) {
            log.debug("Consecutive failures reset to 0 (was: {})", failures);
        }
    }

    /**
     * 요청 처리 실패를 기록합니다.
     */
    private void recordFailure() {
        long failures = consecutiveFailures.incrementAndGet();
        totalMessagesFailed.incrementAndGet();

        if (circuitState == CircuitState.HALF_OPEN) {
            log.warn("Circuit breaker transitioning back to OPEN state after failed request in HALF_OPEN");
            circuitState = CircuitState.OPEN;
            circuitOpenedAt = System.currentTimeMillis();
        } else if (failures >= CIRCUIT_BREAKER_FAILURE_THRESHOLD && circuitState == CircuitState.CLOSED) {
            log.error("Circuit breaker OPENING after {} consecutive failures", failures);
            circuitState = CircuitState.OPEN;
            circuitOpenedAt = System.currentTimeMillis();
        }
    }

    /**
     * Output queue 점유율에 따라 parser 백프레셔를 적용합니다.
     * 점유율이 높을수록 더 오래 대기하여 processing 속도를 조절합니다.
     *
     * @param outputUtilization 현재 output queue 점유율 (0.0 ~ 1.0)
     */
    private void applyParserBackpressure(double outputUtilization) {
        if (outputUtilization >= PARSER_BACKPRESSURE_THRESHOLD_CRITICAL) {
            // 95% 이상: 임계 상태 - 매우 긴 대기
            log.debug("Output queue critical ({}%), applying strong backpressure to parser",
                    String.format("%.1f", outputUtilization * 100));
            ThreadUtil.sleep(PARSER_BACKPRESSURE_SLEEP_CRITICAL_MS);
        } else if (outputUtilization >= PARSER_BACKPRESSURE_THRESHOLD_HIGH) {
            // 85% 이상: 높은 점유율 - 긴 대기
            log.debug("Output queue high ({}%), applying high backpressure to parser",
                    String.format("%.1f", outputUtilization * 100));
            ThreadUtil.sleep(PARSER_BACKPRESSURE_SLEEP_HIGH_MS);
        } else if (outputUtilization >= PARSER_BACKPRESSURE_THRESHOLD_MEDIUM) {
            // 70% 이상: 중간 점유율 - 중간 대기
            log.debug("Output queue medium ({}%), applying medium backpressure to parser",
                    String.format("%.1f", outputUtilization * 100));
            ThreadUtil.sleep(PARSER_BACKPRESSURE_SLEEP_MEDIUM_MS);
        }
        // 70% 미만: 정상 상태 - 대기 없음
    }

    private void processLogEvent(LogEvent logEvent) {
        try {
            log.debug("Processing log event of type: {}", logEvent.getMessageType());

            // 파싱 단계
            boolean parseResult = parseService.parse(logEvent);
            if (parseResult) {
                logEvent.markAsParsed();

                // 변환 단계
                boolean transformResult = transformService.transform(logEvent);
                if (transformResult) {
                    logEvent.markAsTransformed();
                    boolean queued = putOutputMsg(logEvent);
                    log.debug("Log event processed and queued: {}, queue size: {}", queued, outputMessageQueue.size());
                } else {
                    log.debug("Log event filtered out by transform service");
                }
            } else {
                log.debug("Log event parsing failed");
                logEvent.markAsError("Parsing failed");
                totalMessagesFailed.incrementAndGet();
                // DLQ에 추가 (재시도 횟수 0으로 시작)
                deadLetterQueue.addFromLogEvent(logEvent, 0);
            }
        } catch (Exception e) {
            log.error("Error processing log event: {}", logEvent, e);
            logEvent.markAsError("Processing error: " + e.getMessage());
            totalMessagesFailed.incrementAndGet();
            // DLQ에 추가
            deadLetterQueue.addFromLogEvent(logEvent, 0);
        }
    }

    public boolean putGlobalMsg(LogEvent logEvent) {
        // 백프레셔 메커니즘: 큐가 임계값을 초과하면 즉시 거부
        int currentSize = globalMessageQueue.size();
        double utilizationRate = (double) currentSize / queueSize;

        boolean success = false;

        if (utilizationRate >= QUEUE_CRITICAL_THRESHOLD) {
            log.warn("Queue critical! Size: {}/{} ({}%), rejecting message",
                    currentSize, queueSize, String.format("%.1f", utilizationRate * 100));

            // 임계치 초과 시 즉시 거부 (input adapter가 백프레셔를 적용하도록)
            totalMessagesDropped.incrementAndGet();
            return false;
        } else {
            try {
                // offer(timeout)를 사용하여 무한 블로킹 방지
                boolean offered = globalMessageQueue.offer(logEvent, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!offered) {
                    // 타임아웃 발생 시 메시지 드롭
                    totalMessagesDropped.incrementAndGet();
                    log.warn("Message dropped due to queue insertion timeout. Queue size: {}/{}", currentSize,
                            queueSize);
                    return false;
                }
                success = true;
            } catch (InterruptedException e) {
                log.debug("Interrupted while offering message to queue - this is expected during shutdown");
                Thread.currentThread().interrupt();
                return false;
            }
        }

        // 성공 시에만 카운터 증가
        if (success) {
            totalMessagesReceived.incrementAndGet();
        }

        return success;
    }

    public boolean putOutputMsg(LogEvent logEvent) {
        // 출력 큐에도 백프레셔 적용
        int currentSize = outputMessageQueue.size();
        double utilizationRate = (double) currentSize / queueSize;

        boolean success = false;

        if (utilizationRate >= QUEUE_CRITICAL_THRESHOLD) {
            log.warn("Output queue critical! Size: {}/{} ({}%), rejecting message",
                    currentSize, queueSize, String.format("%.1f", utilizationRate * 100));

            // 임계치 초과 시 즉시 거부
            totalMessagesDropped.incrementAndGet();
            return false;
        } else {
            try {
                // offer(timeout)를 사용하여 무한 블로킹 방지
                boolean offered = outputMessageQueue.offer(logEvent, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!offered) {
                    totalMessagesDropped.incrementAndGet();
                    log.warn("Output message dropped due to queue insertion timeout. Queue size: {}/{}",
                            currentSize, queueSize);
                    return false;
                }
                success = true;
            } catch (InterruptedException e) {
                log.debug("Interrupted while offering message to output queue - this is expected during shutdown");
                Thread.currentThread().interrupt();
                return false;
            }
        }

        // 성공 시에만 카운터 증가
        if (success) {
            totalMessagesProcessed.incrementAndGet();
        }

        return success;
    }

    public LogEvent getOutputMsg() {
        try {
            return outputMessageQueue.take();
        } catch (InterruptedException e) {
            // 종료 시 예상되는 동작이므로 DEBUG 레벨로 로깅
            log.debug("Interrupted while getting message from output queue - this is expected during shutdown");
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 큐 모니터링 스레드
     * 주기적으로 큐 상태를 체크하고 로깅합니다.
     */
    private void monitorQueues() {
        while (running.get()) {
            try {
                ThreadUtil.sleep(QUEUE_MONITORING_INTERVAL_MS);

                int globalQueueSize = globalMessageQueue.size();
                int outputQueueSize = outputMessageQueue.size();

                double globalUtilization = (double) globalQueueSize / queueSize;
                double outputUtilization = (double) outputQueueSize / queueSize;

                // 경고 레벨 체크
                if (globalUtilization >= QUEUE_WARNING_THRESHOLD || outputUtilization >= QUEUE_WARNING_THRESHOLD) {
                    log.warn(
                            "Queue Status - Global: {}/{} ({}%), Output: {}/{} ({}%) | Received: {}, Processed: {}, Dropped: {}, Failed: {} | DLQ: {}",
                            globalQueueSize, queueSize, String.format("%.1f", globalUtilization * 100),
                            outputQueueSize, queueSize, String.format("%.1f", outputUtilization * 100),
                            totalMessagesReceived.get(), totalMessagesProcessed.get(),
                            totalMessagesDropped.get(), totalMessagesFailed.get(),
                            deadLetterQueue.getStats());
                } else {
                    log.info(
                            "Queue Status - Global: {}/{} ({}%), Output: {}/{} ({}%) | Received: {}, Processed: {}, Dropped: {}, Failed: {} | DLQ: {}",
                            globalQueueSize, queueSize, String.format("%.1f", globalUtilization * 100),
                            outputQueueSize, queueSize, String.format("%.1f", outputUtilization * 100),
                            totalMessagesReceived.get(), totalMessagesProcessed.get(),
                            totalMessagesDropped.get(), totalMessagesFailed.get(),
                            deadLetterQueue.getStats());
                }
            } catch (Exception e) {
                log.error("Error in queue monitoring thread", e);
            }
        }
    }

    /**
     * Dead Letter Queue flush 스레드
     * 주기적으로 DLQ를 파일에 저장합니다.
     */
    private void flushDeadLetterQueue() {
        while (running.get()) {
            try {
                ThreadUtil.sleep(DLQ_FLUSH_INTERVAL_MS);

                if (!deadLetterQueue.isEmpty()) {
                    int flushed = deadLetterQueue.flush();
                    if (flushed > 0) {
                        log.info("Flushed {} messages from Dead Letter Queue", flushed);
                    }
                }

            } catch (Exception e) {
                log.error("Error in DLQ flush thread", e);
            }
        }
    }

    /**
     * 큐 메트릭 조회 메서드
     */
    public QueueMetrics getQueueMetrics() {
        return new QueueMetrics(
                globalMessageQueue.size(),
                outputMessageQueue.size(),
                queueSize,
                totalMessagesReceived.get(),
                totalMessagesProcessed.get(),
                totalMessagesDropped.get(),
                totalMessagesFailed.get());
    }

    /**
     * Global queue의 현재 점유율을 반환합니다 (0.0 ~ 1.0)
     * Input adapter가 백프레셔를 적용하기 위해 사용합니다.
     *
     * @return queue 점유율 (0.0 = 비어있음, 1.0 = 가득 참)
     */
    public double getGlobalQueueUtilization() {
        return (double) globalMessageQueue.size() / queueSize;
    }

    /**
     * Output queue의 현재 점유율을 반환합니다 (0.0 ~ 1.0)
     * Parser 스레드가 백프레셔를 적용하기 위해 사용합니다.
     *
     * @return queue 점유율 (0.0 = 비어있음, 1.0 = 가득 참)
     */
    public double getOutputQueueUtilization() {
        return (double) outputMessageQueue.size() / queueSize;
    }

    /**
     * Dead Letter Queue를 반환합니다.
     *
     * @return DeadLetterQueue 인스턴스
     */
    public DeadLetterQueue getDeadLetterQueue() {
        return deadLetterQueue;
    }

    /**
     * 메시지 처리 통계를 초기화합니다.
     * 총 수신, 처리, 드롭, 실패 카운터를 0으로 리셋합니다.
     */
    public void resetStatistics() {
        totalMessagesReceived.set(0);
        totalMessagesProcessed.set(0);
        totalMessagesDropped.set(0);
        totalMessagesFailed.set(0);
        log.info("MessageDispatcher statistics reset");
    }

    /**
     * 큐 메트릭 데이터 클래스
     */
    public static class QueueMetrics {
        public final int globalQueueSize;
        public final int outputQueueSize;
        public final int maxQueueSize;
        public final long totalReceived;
        public final long totalProcessed;
        public final long totalDropped;
        public final long totalFailed;

        public QueueMetrics(int globalQueueSize, int outputQueueSize, int maxQueueSize,
                long totalReceived, long totalProcessed, long totalDropped, long totalFailed) {
            this.globalQueueSize = globalQueueSize;
            this.outputQueueSize = outputQueueSize;
            this.maxQueueSize = maxQueueSize;
            this.totalReceived = totalReceived;
            this.totalProcessed = totalProcessed;
            this.totalDropped = totalDropped;
            this.totalFailed = totalFailed;
        }

        public double getGlobalUtilization() {
            return (double) globalQueueSize / maxQueueSize;
        }

        public double getOutputUtilization() {
            return (double) outputQueueSize / maxQueueSize;
        }

        @Override
        public String toString() {
            return String.format(
                    "QueueMetrics{global=%d/%d (%.1f%%), output=%d/%d (%.1f%%), received=%d, processed=%d, dropped=%d, failed=%d}",
                    globalQueueSize, maxQueueSize, getGlobalUtilization() * 100,
                    outputQueueSize, maxQueueSize, getOutputUtilization() * 100,
                    totalReceived, totalProcessed, totalDropped, totalFailed);
        }
    }
}