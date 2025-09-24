package org.keinus.logparser.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.keinus.logparser.config.ApplicationProperties;
import org.keinus.logparser.config.OutputAdapterConfig;
import org.keinus.logparser.core.dispatch.OutputFactory;
import org.keinus.logparser.core.interfaces.OutputAdapter;
import org.keinus.logparser.core.util.MergingHashMap;
import org.keinus.logparser.core.util.ThreadManager;
import org.keinus.logparser.core.util.ThreadUtil;
import org.keinus.logparser.core.schema.LogEvent;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import com.google.gson.Gson;

/**
 * 출력 어댑터들을 관리하고 배치 전송을 처리하는 통합 컴포넌트입니다.
 * <p>
 * 이 클래스는 애플리케이션 설정({@link ApplicationProperties})을 바탕으로
 * 다양한 출력 어댑터({@link OutputAdapter})를 생성하고 관리합니다.
 * <p>
 * 주요 기능:
 * <ul>
 *   <li>메시지 타입별 배치 버퍼 관리</li>
 *   <li>설정된 주기(flush_interval)마다 배치 전송</li>
 *   <li>출력 어댑터별 독립적인 처리</li>
 * </ul>
 *
 * @see org.keinus.logparser.config.ApplicationProperties
 * @see org.keinus.logparser.core.interfaces.OutputAdapter
 */
@Slf4j
@Component
public class OutputAdaptorComponent {

    private static final AtomicBoolean running = new AtomicBoolean(true);

    private final Gson gson = new Gson();
    private final ThreadManager threadManager;
    private final MessageDispatcher dispatcher;
    private final long flushInterval;

    // 출력 어댑터를 메시지 타입별로 그룹화
    private final MergingHashMap<OutputAdapter> outputAdapterMap = new MergingHashMap<>();

    // 메시지 타입별 배치 버퍼
    private final Map<String, List<LogEvent>> batchBuffers = new HashMap<>();
    private final Object bufferLock = new Object();

    // 배치 전송 스케줄러
    private ScheduledExecutorService flushScheduler;

    public OutputAdaptorComponent(ApplicationProperties appProp, ThreadManager threadManager, MessageDispatcher dispatcher) {
        this.threadManager = threadManager;
        this.dispatcher = dispatcher;
        this.flushInterval = appProp.getFlushInterval();

        initializeOutputAdapters(appProp);
        initializeBatchBuffers();
        initializeScheduler();
    }

    private void initializeOutputAdapters(ApplicationProperties appProp) {
        for (OutputAdapterConfig config : appProp.getOutput()) {
            try {
                OutputAdapter adapter = OutputFactory.getOutputAdapter(config);
                String msgType = adapter.getType();
                outputAdapterMap.put(msgType, adapter);
                log.info("OutputAdapter {} registered for message type: {}",
                    adapter.getClass().getSimpleName(), msgType != null ? msgType : "all");
            } catch (Exception e) {
                log.error("OutputAdapter {} initialize error. {}", config.getType(), e.getMessage());
            }
        }
    }

    private void initializeBatchBuffers() {
        synchronized (bufferLock) {
            // 모든 등록된 메시지 타입에 대해 배치 버퍼 초기화
            for (String msgType : outputAdapterMap.getAllKeys()) {
                batchBuffers.put(msgType, new ArrayList<>());
            }
            // null 키 (전역 어댑터)를 위한 버퍼도 초기화
            if (!outputAdapterMap.get(null).isEmpty()) {
                batchBuffers.put(null, new ArrayList<>());
            }
        }
    }

    private void initializeScheduler() {
        flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OutputFlushScheduler");
            t.setDaemon(true);
            return t;
        });

        // 주기적으로 배치 전송
        flushScheduler.scheduleAtFixedRate(this::flushAllBuffers,
            flushInterval, flushInterval, TimeUnit.MILLISECONDS);
    }

    @PostConstruct
    public void startPipeline() {
        try {
            log.info("Starting Output Adaptor Component with flush interval: {}ms", flushInterval);
            running.set(true);

            // 메시지 처리 스레드 시작
            threadManager.executeWithName("processOutputAdapter", this::processOutputMessages);
            log.info("Started output message processor with {} output adapters",
                outputAdapterMap.getAllKeys().size());
        } catch (Exception e) {
            log.error("Failed to initialize output adapters.", e);
            throw new RuntimeException("Output Pipeline startup failed", e);
        }
    }

    @PreDestroy
    public void stopPipeline() {
        try {
            close();
            log.info("All Output Adapters stopped successfully");
        } catch (Exception e) {
            log.error("Error during Output Pipeline shutdown", e);
        }
    }

    /**
     * 메시지 처리 메인 루프
     */
    private void processOutputMessages() {
        while (running.get()) {
            LogEvent logEvent = dispatcher.getOutputMsg();
            if (logEvent == null) {
                ThreadUtil.sleep(100);
                continue;
            }

            enqueueLogEvent(logEvent);
        }
    }

    /**
     * LogEvent를 적절한 배치 버퍼에 추가
     */
    private void enqueueLogEvent(LogEvent logEvent) {
        String messageType = logEvent.getMessageType();
        var adapters = outputAdapterMap.get(messageType);

        if (adapters.isEmpty()) {
            log.error("No output adapters found for message type: {}", messageType);
            return;
        }

        synchronized (bufferLock) {
            // 특정 메시지 타입 어댑터가 있는 경우
            if (batchBuffers.containsKey(messageType)) {
                batchBuffers.get(messageType).add(logEvent);
            }

            // 전역 어댑터 (null 키)가 있는 경우
            if (batchBuffers.containsKey(null) && !outputAdapterMap.get(null).isEmpty()) {
                batchBuffers.get(null).add(logEvent);
            }
        }

        log.debug("Enqueued log event for message type: {}", messageType);
    }

    /**
     * 모든 배치 버퍼를 flush
     */
    private void flushAllBuffers() {
        Map<String, List<LogEvent>> buffersToFlush = new HashMap<>();

        synchronized (bufferLock) {
            for (Map.Entry<String, List<LogEvent>> entry : batchBuffers.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    buffersToFlush.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                    entry.getValue().clear();
                }
            }
        }

        // 각 메시지 타입별로 배치 전송
        for (Map.Entry<String, List<LogEvent>> entry : buffersToFlush.entrySet()) {
            String messageType = entry.getKey();
            List<LogEvent> events = entry.getValue();

            if (!events.isEmpty()) {
                flushBuffer(messageType, events);
            }
        }
    }

    /**
     * 특정 메시지 타입의 배치 버퍼를 flush
     */
    private void flushBuffer(String messageType, List<LogEvent> events) {
        var adapters = outputAdapterMap.get(messageType);

        for (OutputAdapter adapter : adapters) {
            try {
                sendBulkToAdapter(adapter, events);
            } catch (Exception e) {
                log.error("Error sending bulk to adapter {} for message type {}: {}",
                    adapter.getClass().getSimpleName(), messageType, e.getMessage());
            }
        }

        log.debug("Flushed {} events for message type: {}", events.size(), messageType);
    }

    /**
     * 특정 어댑터에 배치 전송
     */
    private void sendBulkToAdapter(OutputAdapter adapter, List<LogEvent> events) {
        boolean addOriginText = adapter.getAddOriginText();

        for (LogEvent event : events) {
            Map<String, Object> outputMap = event.toOutputMap(addOriginText);
            String jsonString = gson.toJson(outputMap);
            adapter.send(outputMap, jsonString);
        }
    }

    /**
     * 컴포넌트 종료
     */
    public void close() {
        log.info("Shutting down OutputAdaptorComponent...");

        // 실행 중단
        running.set(false);

        // 마지막으로 남은 데이터 전송
        flushAllBuffers();

        // 스케줄러 종료
        shutdownScheduler();

        // 모든 어댑터 종료
        closeAllAdapters();

        // 버퍼 정리
        synchronized (bufferLock) {
            batchBuffers.clear();
        }

        // ThreadManager 종료
        shutdownThreadManager();

        log.info("OutputAdaptorComponent shutdown completed");
    }

    private void shutdownScheduler() {
        if (flushScheduler != null && !flushScheduler.isShutdown()) {
            flushScheduler.shutdown();
            try {
                if (!flushScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    flushScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                flushScheduler.shutdownNow();
            }
        }
    }

    private void closeAllAdapters() {
        for (String msgType : outputAdapterMap.getAllKeys()) {
            for (OutputAdapter adapter : outputAdapterMap.get(msgType)) {
                try {
                    adapter.close();
                    log.info("Closed output adapter for message type: {}", msgType != null ? msgType : "all");
                } catch (Exception e) {
                    log.error("Error closing output adapter for type {}: {}", msgType, e.getMessage());
                }
            }
        }
        outputAdapterMap.clear();
    }

    private void shutdownThreadManager() {
        try {
            threadManager.shutdownAllThreads();
            log.info("All output adapter threads have been shut down");
        } catch (Exception e) {
            log.error("Error shutting down thread manager: {}", e.getMessage());
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down OutputAdaptorComponent...");
            running.set(false);
        }));
    }
}