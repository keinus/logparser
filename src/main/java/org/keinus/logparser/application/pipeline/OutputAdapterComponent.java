package org.keinus.logparser.application.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.keinus.logparser.infrastructure.config.ApplicationProperties;
import org.keinus.logparser.domain.configuration.model.OutputAdapterConfig;
import org.keinus.logparser.domain.delivery.service.OutputFactory;
import org.keinus.logparser.domain.delivery.model.OutputAdapter;
import org.keinus.logparser.application.service.BatchingOutputService;
import org.keinus.logparser.infrastructure.util.MergingHashMap;
import org.keinus.logparser.infrastructure.util.ThreadManager;
import org.keinus.logparser.domain.model.LogEvent;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonPrimitive;


/**
 * 출력 어댑터들을 관리하고 즉시 전송을 처리하는 통합 컴포넌트입니다.
 * <p>
 * 이 클래스는 애플리케이션 설정({@link ApplicationProperties})을 바탕으로
 * 다양한 출력 어댑터({@link OutputAdapter})를 생성하고 관리합니다.
 * <p>
 * 주요 기능:
 * <ul>
 *   <li>메시지 타입별 출력 어댑터 관리</li>
 *   <li>메시지 수신 시 즉시 전송</li>
 *   <li>출력 어댑터별 독립적인 처리</li>
 * </ul>
 *
 * @see org.keinus.logparser.infrastructure.config.ApplicationProperties
 * @see org.keinus.logparser.domain.delivery.model.OutputAdapter
 */
@Slf4j
@Component
public class OutputAdapterComponent implements ApplicationListener<ApplicationReadyEvent> {

    private static final String DEFAULT_MESSAGE_TYPE = "all";
    private static final String OUTPUT_PROCESSOR_THREAD_NAME = "OutputDispatcher";

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Gson gson;
    private final ThreadManager threadManager;
    private final MessageDispatcher dispatcher;
    private final BatchingOutputService batchingOutputService;
    private final ApplicationProperties appProp;

    // 출력 어댑터를 메시지 타입별로 그룹화
    private final MergingHashMap<OutputAdapter> outputAdapterMap = new MergingHashMap<>();
    private final Map<Long, OutputAdapter> adapterIdMap = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Instant를 ISO-8601 문자열로 직렬화하는 JsonSerializer
     */
    private static final JsonSerializer<Instant> INSTANT_SERIALIZER = (src, typeOfSrc, context) ->
            new JsonPrimitive(src.toString());

    public OutputAdapterComponent(ApplicationProperties appProp, ThreadManager threadManager,
                                  MessageDispatcher dispatcher, BatchingOutputService batchingOutputService) {
        this.appProp = appProp;
        this.threadManager = threadManager;
        this.dispatcher = dispatcher;
        this.batchingOutputService = batchingOutputService;

        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, INSTANT_SERIALIZER)
                .create();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("ApplicationReadyEvent received, starting pipeline");
        startPipeline();
    }

    public void startPipeline() {
        log.info("Starting Output Adapters");

        initializeOutputAdapters();

        int adapterCount = outputAdapterMap.getAllKeys().size();
        log.info("Output Adapter Component initialized with {} adapters", adapterCount);

        if (adapterCount == 0) {
            log.warn("No output adapters to start!");
            return;
        }

        running.set(true);
        threadManager.executeWithName(OUTPUT_PROCESSOR_THREAD_NAME, this::processOutputMessages);

        log.info("Output Adapters started successfully");
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
     * 메시지 처리 메인 루프 - 즉시 전송
     */
    private void processOutputMessages() {
        while (running.get()) {
            LogEvent logEvent = dispatcher.getOutputMsg();

            if (logEvent == null) {
                if (Thread.currentThread().isInterrupted() || !running.get()) {
                    log.debug("Output message processing interrupted, stopping loop");
                    break;
                }
                continue;
            }

            processLogEvent(logEvent);
        }
    }

    /**
     * 컴포넌트 종료
     */
    public void close() {
        log.info("Shutting down OutputAdapterComponent...");

        running.set(false);
        closeAllAdapters();
        // ThreadManager 종료는 애플리케이션 레벨에서 처리됨 (다른 컴포넌트와 공유)

        log.info("OutputAdapterComponent shutdown completed");
    }

    private void initializeOutputAdapters() {
        List<OutputAdapterConfig> outputConfigs = appProp.getOutput();

        if (outputConfigs == null || outputConfigs.isEmpty()) {
            log.warn("No output adapters configured in ApplicationProperties!");
            return;
        }

        log.info("Initializing {} output adapters", outputConfigs.size());

        lock.writeLock().lock();
        try {
            for (OutputAdapterConfig config : outputConfigs) {
                if (!config.getEnabled()) {
                    continue;
                }
                try {
                    log.info("Creating OutputAdapter for type: {}, messageType: {}",
                            config.getType(), config.getMessagetype());

                    OutputAdapter adapter = OutputFactory.getOutputAdapter(config);
                    String msgType = adapter.getType(); // This is actually messagetype (field name confusion in OutputAdapter)

                    outputAdapterMap.put(msgType, adapter);
                    if (adapter.getId() != null) {
                        adapterIdMap.put(adapter.getId(), adapter);
                    }
                    log.info("OutputAdapter {} registered for message type: {}",
                            adapter.getClass().getSimpleName(), getDisplayMessageType(msgType));

                    registerToBatchingServiceIfApplicable(adapter);

                } catch (Exception e) {
                    log.error("Failed to initialize OutputAdapter {}: {}", config.getType(), e.getMessage());
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void registerToBatchingServiceIfApplicable(OutputAdapter adapter) {
        if (adapter instanceof BatchingOutputService.BatchableOutputAdapter batchableAdapter) {
            batchingOutputService.registerAdapter(batchableAdapter);
            log.info("Registered {} with BatchingOutputService", adapter.getClass().getSimpleName());
        }
    }

    private void processLogEvent(LogEvent logEvent) {
        String messageType = logEvent.getMessageType();
        
        lock.readLock().lock();
        try {
            var adapters = outputAdapterMap.get(messageType);

            if (adapters.isEmpty()) {
                log.warn("No output adapters found for message type: {}", messageType);
                return;
            }

            sendToAdapters(adapters, logEvent);
            log.debug("Sent log event for message type: {}", messageType);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 어댑터들에게 로그 이벤트를 즉시 전송
     */
    private void sendToAdapters(Iterable<OutputAdapter> adapters, LogEvent event) {
        for (OutputAdapter adapter : adapters) {
            sendToAdapter(adapter, event);
        }
    }

    private void sendToAdapter(OutputAdapter adapter, LogEvent event) {
        try {
            boolean addOriginText = adapter.isAddOriginText();
            Map<String, Object> outputMap = event.toOutputMap(addOriginText);
            String jsonString = gson.toJson(outputMap);
            adapter.send(outputMap, jsonString);
        } catch (Exception e) {
            log.error("Error sending to adapter {} for message type {}: {}",
                    adapter.getClass().getSimpleName(), event.getMessageType(), e.getMessage());
        }
    }

    private void closeAllAdapters() {
        lock.writeLock().lock();
        try {
            for (String msgType : outputAdapterMap.getAllKeys()) {
                for (OutputAdapter adapter : outputAdapterMap.get(msgType)) {
                    closeAdapter(adapter, msgType);
                }
            }
            outputAdapterMap.clear();
            adapterIdMap.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addAdapter(OutputAdapterConfig config) {
        if (!config.getEnabled()) {
            return;
        }
        lock.writeLock().lock();
        try {
            OutputAdapter adapter = OutputFactory.getOutputAdapter(config);
            String msgType = adapter.getType(); // messagetype

            outputAdapterMap.put(msgType, adapter);
            if (adapter.getId() != null) {
                adapterIdMap.put(adapter.getId(), adapter);
            }
            log.info("Added output adapter: id={}, type={}, messageType={}", adapter.getId(), adapter.getClass().getSimpleName(), msgType);
            
            registerToBatchingServiceIfApplicable(adapter);
        } catch (Exception e) {
            log.error("Failed to add output adapter", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeAdapter(Long id) {
        lock.writeLock().lock();
        try {
            OutputAdapter adapter = adapterIdMap.remove(id);
            if (adapter != null) {
                outputAdapterMap.removeValue(adapter);
                try {
                    adapter.close();
                } catch (Exception e) {
                    log.error("Error closing adapter id={}", id, e);
                }
                log.info("Removed output adapter: id={}", id);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void restartAdapter(OutputAdapterConfig config) {
        removeAdapter(config.getId());
        addAdapter(config);
    }

    private void closeAdapter(OutputAdapter adapter, String msgType) {
        try {
            adapter.close();
            log.info("Closed output adapter for message type: {}", getDisplayMessageType(msgType));
        } catch (Exception e) {
            log.error("Error closing output adapter for type {}: {}", msgType, e.getMessage());
        }
    }

    /**
     * 메시지 타입을 로깅용 문자열로 변환
     */
    private String getDisplayMessageType(String msgType) {
        return msgType != null ? msgType : DEFAULT_MESSAGE_TYPE;
    }
}