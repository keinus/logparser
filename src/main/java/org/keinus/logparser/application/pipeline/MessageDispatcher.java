package org.keinus.logparser.components;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import org.keinus.logparser.config.ApplicationProperties;
import org.keinus.logparser.core.dispatch.ParseService;
import org.keinus.logparser.core.dispatch.TransformService;
import org.keinus.logparser.core.util.DeadLetterQueue;
import org.keinus.logparser.core.util.ThreadManager;
import org.keinus.logparser.core.util.ThreadUtil;
import org.keinus.logparser.core.schema.LogEvent;
import org.keinus.logparser.monitoring.LogParserMonitoring;

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

    // Dead Letter Queue
    private DeadLetterQueue deadLetterQueue;

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
                threadManager.execute(this::parseAndTransform);
            }
            // 큐 모니터링 스레드 시작
            threadManager.execute(this::monitorQueues);

            // DLQ flush 스레드 시작 (5분마다 flush)
            threadManager.execute(this::flushDeadLetterQueue);

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
        globalMessageQueue.clear();
        this.threadManager.shutdownAllThreads();
        log.info("Message Dispatcher closed");
    }

    public void parseAndTransform() {
        while (running.get()) {
            try {
                LogEvent logEvent = globalMessageQueue.take();
                if (logEvent != null) {
                    processLogEvent(logEvent);
                } else
                    ThreadUtil.sleep(100);
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for message in class {} method {}", this.getClass().getName(),
                        Thread.currentThread().getStackTrace()[2].getMethodName());
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("Error processing log event", e);
            }

        }
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
        totalMessagesReceived.incrementAndGet();

        // 백프레셔 메커니즘: 큐가 임계값을 초과하면 대기
        int currentSize = globalMessageQueue.size();
        double utilizationRate = (double) currentSize / queueSize;

        if (utilizationRate >= QUEUE_CRITICAL_THRESHOLD) {
            log.warn("Queue critical! Size: {}/{} ({}%), applying backpressure",
                    currentSize, queueSize, String.format("%.1f", utilizationRate * 100));

            // 오버플로우 전략: offer를 사용하여 큐가 가득 차면 가장 오래된 메시지를 드롭
            boolean offered = globalMessageQueue.offer(logEvent);
            if (!offered) {
                // 큐가 가득 차서 메시지를 드롭
                totalMessagesDropped.incrementAndGet();
                log.error("Message dropped due to queue overflow. Total dropped: {}", totalMessagesDropped.get());
                return false;
            }
            return true;
        }

        try {
            globalMessageQueue.put(logEvent);
            return true;
        } catch (InterruptedException e) {
            log.error("Interrupted while putting message to queue", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public boolean putOutputMsg(LogEvent logEvent) {
        totalMessagesProcessed.incrementAndGet();

        // 출력 큐에도 백프레셔 적용
        int currentSize = outputMessageQueue.size();
        double utilizationRate = (double) currentSize / queueSize;

        if (utilizationRate >= QUEUE_CRITICAL_THRESHOLD) {
            log.warn("Output queue critical! Size: {}/{} ({}%)",
                    currentSize, queueSize, String.format("%.1f", utilizationRate * 100));

            boolean offered = outputMessageQueue.offer(logEvent);
            if (!offered) {
                totalMessagesDropped.incrementAndGet();
                log.error("Output message dropped due to queue overflow. Total dropped: {}",
                        totalMessagesDropped.get());
                return false;
            }
            return true;
        }

        try {
            outputMessageQueue.put(logEvent);
            return true;
        } catch (InterruptedException e) {
            log.error("Interrupted while putting message to output queue", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public LogEvent getOutputMsg() {
        try {
            return outputMessageQueue.take();
        } catch (InterruptedException e) {
            log.error("Interrupted while getting message from output queue", e);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 큐 모니터링 스레드
     * 주기적으로 큐 상태를 체크하고 로깅합니다.
     */
    private void monitorQueues() {
        final long monitoringIntervalMs = 30000; // 30초마다 모니터링

        while (running.get()) {
            try {
                ThreadUtil.sleep(monitoringIntervalMs);

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
        final long flushIntervalMs = 300000; // 5분마다 flush

        while (running.get()) {
            try {
                ThreadUtil.sleep(flushIntervalMs);

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

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down MessageDispatcher...");
            running.set(false);
        }));
    }
}