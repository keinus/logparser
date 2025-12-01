package org.keinus.logparser.application.service;

import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.infrastructure.util.ThreadManager;
import org.keinus.logparser.infrastructure.util.ThreadUtil;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 배치형 출력 어댑터들의 flush 작업을 통합 관리하는 서비스입니다.
 * <p>
 * 이 서비스는 OpenSearchOutputAdapter와 같이 배치 처리가 필요한 출력 어댑터들을
 * 등록받아, 주기적으로 flush() 메서드를 호출하여 버퍼링된 데이터를 전송합니다.
 * <p>
 * 주요 기능:
 * <ul>
 *     <li>배치형 어댑터 등록/해제</li>
 *     <li>주기적인 flush 스케줄링 (기본 10초)</li>
 *     <li>ThreadManager를 통한 스레드 통합 관리</li>
 *     <li>Graceful shutdown 지원</li>
 * </ul>
 */
@Service
@Slf4j
public class BatchingOutputService {

    private static final long DEFAULT_FLUSH_INTERVAL_MS = 10_000; // 10초

    private final ThreadManager threadManager;
    private final Map<String, BatchableOutputAdapter> batchingAdapters = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final long flushIntervalMs;

    /**
     * 배치 처리가 가능한 출력 어댑터 인터페이스
     */
    public interface BatchableOutputAdapter {
        /**
         * 버퍼링된 데이터를 전송합니다.
         */
        void flush();

        /**
         * 어댑터의 고유 식별자를 반환합니다.
         * @return 어댑터 식별자 (예: "opensearch-production")
         */
        String getAdapterId();
    }

    public BatchingOutputService(ThreadManager threadManager) {
        this.threadManager = threadManager;
        this.flushIntervalMs = DEFAULT_FLUSH_INTERVAL_MS;
        log.info("BatchingOutputService initialized with flush interval: {}ms", flushIntervalMs);
    }

    /**
     * 배치형 어댑터를 등록합니다.
     *
     * @param adapter 등록할 어댑터
     */
    public void registerAdapter(BatchableOutputAdapter adapter) {
        String adapterId = adapter.getAdapterId();
        batchingAdapters.put(adapterId, adapter);
        log.info("Registered batching adapter: {}", adapterId);
    }

    /**
     * 배치형 어댑터를 해제합니다.
     *
     * @param adapterId 해제할 어댑터 ID
     */
    public void unregisterAdapter(String adapterId) {
        BatchableOutputAdapter removed = batchingAdapters.remove(adapterId);
        if (removed != null) {
            log.info("Unregistered batching adapter: {}", adapterId);
        }
    }

    /**
     * 배치 flush 스케줄러를 시작합니다.
     */
    @PostConstruct
    public void startBatchScheduler() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting batch flush scheduler");
            try {
                threadManager.executeWithName("BatchFlushScheduler", this::flushLoop);
                log.info("Batch flush scheduler started successfully");
            } catch (IllegalStateException e) {
                log.warn("Batch flush scheduler already running: {}", e.getMessage());
                running.set(false);
            }
        }
    }

    /**
     * 배치 flush 스케줄러를 중지합니다.
     */
    @PreDestroy
    public void stopBatchScheduler() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping batch flush scheduler");

            // 마지막 flush 수행
            flushAllAdapters();

            log.info("Batch flush scheduler stopped. Total registered adapters: {}", batchingAdapters.size());
        }
    }

    /**
     * 주기적으로 모든 어댑터를 flush하는 루프
     */
    private void flushLoop() {
        log.info("Batch flush loop started with {} registered adapters", batchingAdapters.size());

        while (running.get()) {
            try {
                ThreadUtil.sleep(flushIntervalMs);

                if (!running.get()) {
                    break;
                }

                flushAllAdapters();

            } catch (Exception e) {
                log.error("Error in batch flush loop: {}", e.getMessage(), e);
                // 오류가 발생해도 계속 실행
            }
        }

        log.info("Batch flush loop terminated");
    }

    /**
     * 등록된 모든 어댑터를 flush합니다.
     */
    private void flushAllAdapters() {
        if (batchingAdapters.isEmpty()) {
            log.debug("No adapters to flush");
            return;
        }

        int successCount = 0;
        int failureCount = 0;

        for (Map.Entry<String, BatchableOutputAdapter> entry : batchingAdapters.entrySet()) {
            String adapterId = entry.getKey();
            BatchableOutputAdapter adapter = entry.getValue();

            try {
                log.debug("Flushing adapter: {}", adapterId);
                adapter.flush();
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Error flushing adapter '{}': {}", adapterId, e.getMessage(), e);
            }
        }

        if (successCount > 0 || failureCount > 0) {
            log.debug("Batch flush completed - success: {}, failed: {}", successCount, failureCount);
        }
    }

    /**
     * 현재 등록된 어댑터 수를 반환합니다.
     *
     * @return 등록된 어댑터 수
     */
    public int getAdapterCount() {
        return batchingAdapters.size();
    }

    /**
     * 스케줄러가 실행 중인지 반환합니다.
     *
     * @return 실행 중이면 true
     */
    public boolean isRunning() {
        return running.get();
    }
}
