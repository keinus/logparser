package org.keinus.logparser.application.pipeline;

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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.keinus.logparser.infrastructure.config.ApplicationProperties;
import org.keinus.logparser.domain.transformation.service.StructuredTransformService;
import org.keinus.logparser.domain.transformation.service.TransformService;
import org.keinus.logparser.infrastructure.util.ThreadManager;
import org.keinus.logparser.infrastructure.util.ThreadUtil;
import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.parse.service.ParseService;

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
     * 출력 메시지를 저장하는 BlockingQueue의 HashMap. 큐 크기는 10000으로 제한됩니다.
     */
    private final BlockingQueue<LogEvent> outputMessageQueue;

    /**
     * 스레드 실행 지속 여부 (Global shutdown)
     */
    private static final AtomicBoolean running = new AtomicBoolean(true);
    
    /**
     * 워커 스레드 제어 (Restartable)
     */
    private AtomicBoolean workerActive;

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

    /**
     * StructuredTransformService 인스턴스
     */
    private StructuredTransformService structuredTransformService = null;

    /**
     * ApplicationProperties 인스턴스
     */
    private final ApplicationProperties applicationProperties;
    
    /**
     * LiveTailService 인스턴스
     */
    private final org.keinus.logparser.application.service.LiveTailService liveTailService;

    // 큐 모니터링 메트릭
    private final AtomicLong totalMessagesDropped = new AtomicLong(0);
    private final AtomicLong totalMessagesFailed = new AtomicLong(0);

    // Output throughput monitoring
    private final AtomicLong outputMessageCount = new AtomicLong(0);
    private volatile double currentOutputThroughput = 0.0;
    private long lastOutputMessageCount = 0;
    private long lastMonitorTime = System.currentTimeMillis();

    // 큐 크기 설정
    private final int queueSize;

    // 타임아웃 및 대기 시간 상수
    private static final long QUEUE_MONITORING_INTERVAL_MS = 30_000;  // 30초

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
            StructuredTransformService structuredTransformService,
            org.keinus.logparser.application.service.LiveTailService liveTailService,
            ApplicationProperties applicationProperties,
            @Value("${log.message.queue-size:10000}") int queueSize) {

        this.threadManager = threadManager;
        this.queueSize = queueSize;
        this.inputMessageQueue = new LinkedBlockingQueue<>(queueSize);
        // Transform Queue removed
        this.outputMessageQueue = new LinkedBlockingQueue<>(queueSize);

        this.parseService = parseService;
        this.transformService = transformService;
        this.structuredTransformService = structuredTransformService;
        this.liveTailService = liveTailService;
        this.applicationProperties = applicationProperties;

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
            startWorkers();

            // 큐 모니터링 스레드 시작
            threadManager.executeWithName("QueueMonitor", this::monitorQueues);

            log.info("MessageDispatcher started");
        } catch (Exception e) {
            log.error("Failed to initialize input adapters.", e);
            throw new RuntimeException("ETL Pipeline startup failed", e);
        }
    }

    private synchronized void startWorkers() {
        if (workerActive != null && workerActive.get()) {
            log.warn("Workers are already running. Stop them first.");
            return;
        }

        int processingThreads = applicationProperties.getParserThreads(); // Reusing config key for general processing threads
        
        this.workerActive = new AtomicBoolean(true);

        // Start Processing Threads (Combined Parse + Transform)
        for (int i = 0; i < processingThreads; i++) {
            String threadName = "ProcessingThread-" + (i + 1);
            ProcessingDispatcher processingDispatcher = new ProcessingDispatcher(
                inputMessageQueue, outputMessageQueue,
                parseService, transformService, structuredTransformService,
                liveTailService,
                workerActive,
                totalMessagesFailed
            );
            threadManager.executeWithName(threadName, processingDispatcher);
        }

        log.info("Started {} processing threads", processingThreads);
    }

    private synchronized void stopWorkers() {
        if (workerActive != null) {
            log.info("Stopping worker threads...");
            workerActive.set(false);

            threadManager.stopThreadsStartingWith("ProcessingThread-");
            log.info("Stopped all threads.");
        }
    }

    public void updateWorkerThreadCount() {
        log.info("Updating worker threads");
        stopWorkers();
        applicationProperties.loadConfigurationFromDatabase();
        startWorkers();
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
        if (workerActive != null) workerActive.set(false);

        inputMessageQueue.clear();
        this.threadManager.shutdownAllThreads();
        log.info("Message Dispatcher closed");
    }

    public boolean putInputMsg(LogEvent logEvent) {
        try {
            // 큐가 가득 차면 공간이 생길 때까지 무한 대기 (Blocking Backpressure)
            inputMessageQueue.put(logEvent);
            return true;
        } catch (InterruptedException e) {
            log.debug("Interrupted while putting message to input queue - this is expected during shutdown");
            Thread.currentThread().interrupt();
            return false;
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
                int outputQueueSize = outputMessageQueue.size();

                double inputUtilization = (double) inputQueueSize / queueSize;
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
                log.info(
                        "Queue Status - Input: {}/{} ({}%), Output: {}/{} ({}%) | Dropped: {}, Failed: {} | Throughput: {}/s",
                        inputQueueSize, queueSize, String.format("%.1f", inputUtilization * 100),
                        outputQueueSize, queueSize, String.format("%.1f", outputUtilization * 100),
                        totalMessagesDropped.get(), 
                        totalMessagesFailed.get(),
                        currentOutputThroughput);
            } catch (Exception e) {
                log.error("Error in queue monitoring thread", e);
            }
        }
    }

    /**
     * 디스패처 메트릭 조회 메서드
     */
    public DispatcherMetrics getDispatcherMetrics() {
        return new DispatcherMetrics(
                inputMessageQueue.size(),
                outputMessageQueue.size(),
                queueSize,
                totalMessagesDropped.get(),
                totalMessagesFailed.get(),
                currentOutputThroughput);
    }

    /**
     * 디스패처 메트릭 데이터 클래스
     */
    @AllArgsConstructor
    public static class DispatcherMetrics {
        public final int globalQueueSize;
        public final int outputQueueSize;
        public final int maxQueueSize;
        public final long totalDropped;
        public final long totalFailed;
        public final double outputThroughput;

        public double getGlobalUtilization() {
            return (double) globalQueueSize / maxQueueSize;
        }

        public double getOutputUtilization() {
            return (double) outputQueueSize / maxQueueSize;
        }

        @Override
        public String toString() {
            return String.format(
                    "DispatcherMetrics{input=%d/%d (%.1f%%), output=%d/%d (%.1f%%), dropped=%d, failed=%d, throughput=%.1f/s}",
                    globalQueueSize, maxQueueSize, getGlobalUtilization() * 100,
                    outputQueueSize, maxQueueSize, getOutputUtilization() * 100,
                    totalDropped, totalFailed,
                    outputThroughput);
        }
    }
}
