package org.keinus.logparser.application.pipeline;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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


/**
 * 출력 어댑터들을 관리하고 각 어댑터별 전용 스레드를 통해 비동기 전송을 처리하는 통합 컴포넌트입니다.
 */
@Slf4j
@Component
public class OutputAdapterComponent implements ApplicationListener<ApplicationReadyEvent> {

    private static final String DEFAULT_MESSAGE_TYPE = "all";
    private static final String OUTPUT_PROCESSOR_THREAD_NAME = "OutputDispatcher";
    private static final int ADAPTER_QUEUE_SIZE = 10000;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ThreadManager threadManager;
    private final MessageDispatcher dispatcher;
    private final BatchingOutputService batchingOutputService;
    private final ApplicationProperties appProp;

    // 출력 어댑터 및 관련 러너 관리
    private final MergingHashMap<AdapterRunner> outputAdapterMap = new MergingHashMap<>();
    private final Map<Long, AdapterRunner> adapterIdMap = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 각 어댑터의 전송 작업을 독립적으로 수행하는 내부 클래스
     */
    private class AdapterRunner {
        private final OutputAdapter adapter;
        private final BlockingQueue<LogEvent> queue;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final String threadName;

        public AdapterRunner(OutputAdapter adapter) {
            this.adapter = adapter;
            this.queue = new LinkedBlockingQueue<>(ADAPTER_QUEUE_SIZE);
            this.threadName = "AdapterWorker-" + adapter.getId() + "-" + adapter.getClass().getSimpleName();
            
            threadManager.executeWithName(threadName, this::run);
        }

        private void run() {
            log.info("Worker thread started for adapter: {}", threadName);
            while (active.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    LogEvent event = queue.poll(1, TimeUnit.SECONDS);
                    if (event != null) {
                        adapter.send(event);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Unexpected error in adapter worker {}: {}", threadName, e.getMessage(), e);
                }
            }
            log.info("Worker thread finished for adapter: {}", threadName);
        }

        public void put(LogEvent logEvent) throws InterruptedException {
            queue.put(logEvent);
        }

        public void stop() {
            active.set(false);
            threadManager.stopThread(threadName);
            try {
                adapter.close();
            } catch (Exception e) {
                log.error("Error closing adapter {}: {}", threadName, e.getMessage());
            }
        }
    }

    public OutputAdapterComponent(ApplicationProperties appProp, ThreadManager threadManager,
                                  MessageDispatcher dispatcher, BatchingOutputService batchingOutputService) {
        this.appProp = appProp;
        this.threadManager = threadManager;
        this.dispatcher = dispatcher;
        this.batchingOutputService = batchingOutputService;
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
     * 메시지 처리 메인 루프 - 디스패처로부터 메시지를 가져와 각 어댑터 큐에 배분
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

            String messageType = logEvent.getMessageType();
            try {
                var runners = outputAdapterMap.get(messageType);
                if (runners.isEmpty()) {
                    return;
                }

                for (AdapterRunner runner : runners) {
                    try {
                        runner.put(logEvent);
                    } catch (InterruptedException e) {
                        log.debug("Interrupted while putting message to adapter queue");
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
            }
        }
    }

    /**
     * 컴포넌트 종료
     */
    public void close() {
        log.info("Shutting down OutputAdapterComponent...");

        running.set(false);
        closeAllAdapters();

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
                if (!Boolean.TRUE.equals(config.getEnabled())) {
                    continue;
                }
                try {
                    log.info("Creating OutputAdapter for type: {}, messageType: {}",
                            config.getType(), config.getMessagetype());

                    OutputAdapter adapter = OutputFactory.getOutputAdapter(config);
                    String msgType = adapter.getType();

                    AdapterRunner runner = new AdapterRunner(adapter);
                    outputAdapterMap.put(msgType, runner);
                    if (adapter.getId() != null) {
                        adapterIdMap.put(adapter.getId(), runner);
                    }
                    log.info("OutputAdapter {} registered with worker thread for message type: {}",
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

    private void closeAllAdapters() {
        lock.writeLock().lock();
        try {
            for (AdapterRunner runner : adapterIdMap.values()) {
                runner.stop();
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
            String msgType = adapter.getType();

            AdapterRunner runner = new AdapterRunner(adapter);
            outputAdapterMap.put(msgType, runner);
            if (adapter.getId() != null) {
                adapterIdMap.put(adapter.getId(), runner);
            }
            log.info("Added output adapter with worker: id={}, type={}", adapter.getId(), adapter.getClass().getSimpleName());
            
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
            AdapterRunner runner = adapterIdMap.remove(id);
            if (runner != null) {
                outputAdapterMap.removeValue(runner);
                runner.stop();
                log.info("Removed output adapter and stopped worker: id={}", id);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void restartAdapter(OutputAdapterConfig config) {
        removeAdapter(config.getId());
        addAdapter(config);
    }

    /**
     * 메시지 타입을 로깅용 문자열로 변환
     */
    private String getDisplayMessageType(String msgType) {
        return msgType != null ? msgType : DEFAULT_MESSAGE_TYPE;
    }
}