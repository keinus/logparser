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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.keinus.logparser.infrastructure.config.ApplicationProperties;
import org.keinus.logparser.domain.parsing.service.ParseService;
import org.keinus.logparser.domain.transformation.service.TransformService;
import org.keinus.logparser.infrastructure.util.DeadLetterQueue;
import org.keinus.logparser.infrastructure.util.ThreadManager;
import org.keinus.logparser.infrastructure.util.ThreadUtil;
import org.keinus.logparser.domain.model.LogEvent;

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
    private final BlockingQueue<LogEvent> inputMessageQueue;

    /**
     * 변환 대기 메시지를 저장하는 BlockingQueue.
     */
    private final BlockingQueue<LogEvent> transformQueue;

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

    int parserThreads = 1;
    int transformThreads = 1;

    // 큐 모니터링 메트릭
    private final AtomicLong totalMessagesDropped = new AtomicLong(0);
    private final AtomicLong totalMessagesFailed = new AtomicLong(0);

    // Output throughput monitoring
    private final AtomicLong outputMessageCount = new AtomicLong(0);
    private volatile double currentOutputThroughput = 0.0;
    private long lastOutputMessageCount = 0;
    private long lastMonitorTime = System.currentTimeMillis();

    // 큐 크기 및 임계값 설정
    private final int queueSize;
    private static final double QUEUE_WARNING_THRESHOLD = 0.8; // 80% 이상 시 경고
    private static final double QUEUE_CRITICAL_THRESHOLD = 0.95; // 95% 이상 시 위험

    // 타임아웃 및 대기 시간 상수
    private static final long QUEUE_OFFER_TIMEOUT_MS = 50_000;  // 큐 삽입 시 최대 대기 시간 (50초)
    private static final long QUEUE_MONITORING_INTERVAL_MS = 30_000;  // 30초
    private static final long DLQ_FLUSH_INTERVAL_MS = 300_000;  // 5분

    // Dead Letter Queue
    private DeadLetterQueue deadLetterQueue;

    // Circuit Breaker 관련
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();

    // Backpressure 로그 제한을 위한 변수
    private long backpressureLastLogTime = 0;
    private static final long BACKPRESSURE_LOG_INTERVAL_MS = 5000; // 5초

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
        this.inputMessageQueue = new LinkedBlockingQueue<>(queueSize);
        this.transformQueue = new LinkedBlockingQueue<>(queueSize);
        this.outputMessageQueue = new LinkedBlockingQueue<>(queueSize);

        this.parseService = parseService;
        this.transformService = transformService;
        this.parserThreads = applicationProperties.getParserThreads();
        this.transformThreads = applicationProperties.getParserThreads();

        // Dead Letter Queue 초기화
        this.deadLetterQueue = new DeadLetterQueue();

        log.info("MessageDispatcher initialized with queue size: {}", queueSize);
    }

    /**
     * 메시지 디스패처를 초기화하고 파서 스레드를 시작합니다.
     * - 실행 상태 플래그 {@code running}을 활성화합니다.
     * - {@link ThreadManager}을 통해 지정된 수의 스레드를 생성하고, 각 스레드에서
     * {@link #parseLoop()} 및 {@link #transformLoop()} 작업을 병렬 실행합니다.
     * - 디스패처 시작 완료 시 INFO 레벨로 파서 스레드 수를 로깅합니다.
     *
     * @see ThreadManager#execute(Runnable)
     * @see Logger#info(String, Object...)
     */
    @PostConstruct
    public void startPipeline() {
        try {
            running.set(true);
            
            // Start Parser Threads
            for (int i = 0; i < parserThreads; i++) {
                String threadName = "ParserThread-" + (i + 1);
                ParseDispatcher parseDispatcher = new ParseDispatcher(
                    inputMessageQueue, transformQueue, parseService, 
                    deadLetterQueue, circuitBreaker, running, 
                    totalMessagesFailed, totalMessagesDropped, queueSize
                );
                threadManager.executeWithName(threadName, parseDispatcher);
            }

            // Start Transform Threads
            for (int i = 0; i < transformThreads; i++) {
                String threadName = "TransformThread-" + (i + 1);
                TransformDispatcher transformDispatcher = new TransformDispatcher(
                    transformQueue, outputMessageQueue, transformService, 
                    deadLetterQueue, running, totalMessagesFailed, 
                    totalMessagesDropped, queueSize
                );
                threadManager.executeWithName(threadName, transformDispatcher);
            }

            // 큐 모니터링 스레드 시작
            threadManager.executeWithName("QueueMonitor", this::monitorQueues);

            // DLQ flush 스레드 시작 (5분마다 flush)
            threadManager.executeWithName("DeadLetterQueueFlusher", this::flushDeadLetterQueue);

            log.info("MessageDispatcher started with {} parser threads, {} transformer threads and queue monitoring", parserThreads, transformThreads);
        } catch (Exception e) {
            log.error("Failed to initialize input adapters.", e);
            throw new RuntimeException("ETL Pipeline startup failed", e);
        }
    }

    @PreDestroy
    public void stopPipeline() {
        try {
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

        inputMessageQueue.clear();
        transformQueue.clear();
        this.threadManager.shutdownAllThreads();
        log.info("Message Dispatcher closed");
    }

    public boolean putInputMsg(LogEvent logEvent) {
        // 백프레셔 메커니즘: 어느 큐라도 임계값을 초과하면 즉시 거부 (Global Backpressure)
        int inputSize = inputMessageQueue.size();
        int transformSize = transformQueue.size();
        int outputSize = outputMessageQueue.size();
        
        double inputRate = (double) inputSize / queueSize;
        double transformRate = (double) transformSize / queueSize;
        double outputRate = (double) outputSize / queueSize;

        if (inputRate >= QUEUE_CRITICAL_THRESHOLD || 
            transformRate >= QUEUE_CRITICAL_THRESHOLD || 
            outputRate >= QUEUE_CRITICAL_THRESHOLD) {
            
            long currentTime = System.currentTimeMillis();
            if (currentTime - backpressureLastLogTime >= BACKPRESSURE_LOG_INTERVAL_MS) {
                log.warn("Backpressure triggered! Queue status - Input: {} ({}%), Transform: {} ({}%), Output: {} ({}%). Rejecting input message.",
                        inputSize, String.format("%.1f", inputRate * 100),
                        transformSize, String.format("%.1f", transformRate * 100),
                        outputSize, String.format("%.1f", outputRate * 100));
                backpressureLastLogTime = currentTime;
            }

            // 임계치 초과 시 즉시 거부 (input adapter가 백프레셔를 적용하도록)
            totalMessagesDropped.incrementAndGet();
            return false;
        } else {
            try {
                // offer(timeout)를 사용하여 무한 블로킹 방지
                boolean offered = inputMessageQueue.offer(logEvent, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!offered) {
                    // 타임아웃 발생 시 메시지 드롭
                    totalMessagesDropped.incrementAndGet();
                    log.warn("Message dropped due to queue insertion timeout. Queue size: {}/{}", inputSize,
                            queueSize);
                    return false;
                }
                return true;
            } catch (InterruptedException e) {
                log.debug("Interrupted while offering message to queue - this is expected during shutdown");
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    public LogEvent getOutputMsg() {
        try {
            LogEvent event = outputMessageQueue.take();
            outputMessageCount.incrementAndGet();
            return event;
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

                int inputQueueSize = inputMessageQueue.size();
                int transformQueueSize = transformQueue.size();
                int outputQueueSize = outputMessageQueue.size();

                double inputUtilization = (double) inputQueueSize / queueSize;
                double transformUtilization = (double) transformQueueSize / queueSize;
                double outputUtilization = (double) outputQueueSize / queueSize;

                // Calculate throughput
                long currentCount = outputMessageCount.get();
                long currentTime = System.currentTimeMillis();
                double elapsedSeconds = (currentTime - lastMonitorTime) / 1000.0;
                if (elapsedSeconds > 0) {
                    currentOutputThroughput = (currentCount - lastOutputMessageCount) / elapsedSeconds;
                }
                lastOutputMessageCount = currentCount;
                lastMonitorTime = currentTime;

                // 경고 레벨 체크
                if (inputUtilization >= QUEUE_WARNING_THRESHOLD || transformUtilization >= QUEUE_WARNING_THRESHOLD || outputUtilization >= QUEUE_WARNING_THRESHOLD) {
                    log.warn(
                            "Queue Status - Input: {}/{} ({}%), Transform: {}/{} ({}%), Output: {}/{} ({}%) | Dropped: {}, Failed: {} | DLQ: {} | Throughput: {:.1f}/s",
                            inputQueueSize, queueSize, String.format("%.1f", inputUtilization * 100),
                            transformQueueSize, queueSize, String.format("%.1f", transformUtilization * 100),
                            outputQueueSize, queueSize, String.format("%.1f", outputUtilization * 100),
                            totalMessagesDropped.get(), totalMessagesFailed.get(),
                            deadLetterQueue.getStats(), currentOutputThroughput);
                } else {
                    log.info(
                            "Queue Status - Input: {}/{} ({}%), Transform: {}/{} ({}%), Output: {}/{} ({}%) | Dropped: {}, Failed: {} | DLQ: {} | Throughput: {:.1f}/s",
                            inputQueueSize, queueSize, String.format("%.1f", inputUtilization * 100),
                            transformQueueSize, queueSize, String.format("%.1f", transformUtilization * 100),
                            outputQueueSize, queueSize, String.format("%.1f", outputUtilization * 100),
                            totalMessagesDropped.get(), totalMessagesFailed.get(),
                            deadLetterQueue.getStats(), currentOutputThroughput);
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
     * 디스패처 메트릭 조회 메서드
     */
    public DispatcherMetrics getDispatcherMetrics() {
        return new DispatcherMetrics(
                inputMessageQueue.size(),
                transformQueue.size(),
                outputMessageQueue.size(),
                queueSize,
                totalMessagesDropped.get(),
                totalMessagesFailed.get(),
                circuitBreaker.getState(),
                currentOutputThroughput);
    }

    /**
     * 디스패처 메트릭 데이터 클래스
     */
    @AllArgsConstructor
    public static class DispatcherMetrics {
        public final int globalQueueSize;
        public final int transformQueueSize;
        public final int outputQueueSize;
        public final int maxQueueSize;
        public final long totalDropped;
        public final long totalFailed;
        public final String circuitBreakerState;
        public final double outputThroughput;

        public double getGlobalUtilization() {
            return (double) globalQueueSize / maxQueueSize;
        }

        public double getTransformUtilization() {
            return (double) transformQueueSize / maxQueueSize;
        }

        public double getOutputUtilization() {
            return (double) outputQueueSize / maxQueueSize;
        }

        @Override
        public String toString() {
            return String.format(
                    "DispatcherMetrics{input=%d/%d (%.1f%%), transform=%d/%d (%.1f%%), output=%d/%d (%.1f%%), dropped=%d, failed=%d, circuit=%s, throughput=%.1f/s}",
                    globalQueueSize, maxQueueSize, getGlobalUtilization() * 100,
                    transformQueueSize, maxQueueSize, getTransformUtilization() * 100,
                    outputQueueSize, maxQueueSize, getOutputUtilization() * 100,
                    totalDropped, totalFailed,
                    circuitBreakerState,
                    outputThroughput);
        }
    }
}
