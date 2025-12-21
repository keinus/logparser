package org.keinus.logparser.application.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.keinus.logparser.application.config.ConfigManagementService;
import org.keinus.logparser.application.config.ConfigValidationService;
import org.keinus.logparser.domain.parsing.service.ParseService;
import org.keinus.logparser.domain.transformation.service.TransformService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineReloadService {

    private final ConfigValidationService validationService;
    private final ApplicationContext applicationContext;
    private final ConfigManagementService configManagementService;

    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    private final AtomicInteger reloadProgress = new AtomicInteger(0);
    private volatile PipelineStatus currentStatus = PipelineStatus.RUNNING;

    // ==================== Reload Operations ====================

    public void reloadConfiguration() {
        log.info("Reloading configuration from database");

        if (!reloadInProgress.compareAndSet(false, true)) {
            throw new RuntimeException("Reload already in progress");
        }

        try {
            reloadProgress.set(0);
            currentStatus = PipelineStatus.RELOADING;

            // Step 1: 현재 파이프라인 컴포넌트 중지
            log.info("Stopping current pipeline components");
            stopPipelineComponents();
            reloadProgress.set(33);

            // Step 2: 데이터베이스에서 설정 로드 및 검증
            log.info("Loading and validating configuration from database");
            validateConfiguration();
            reloadProgress.set(66);

            // Step 3: 새 설정으로 파이프라인 컴포넌트 재시작
            log.info("Restarting pipeline components with new configuration");
            startPipelineComponents();
            reloadProgress.set(100);

            currentStatus = PipelineStatus.RUNNING;
            log.info("Pipeline reload completed successfully");

        } catch (Exception e) {
            log.error("Failed to reload configuration", e);
            currentStatus = PipelineStatus.ERROR;
            throw new RuntimeException("Failed to reload configuration", e);
        } finally {
            reloadInProgress.set(false);
        }
    }

    /**
     * 파이프라인 컴포넌트 중지
     */
    private void stopPipelineComponents() {
        try {
            // Input Adapters 중지
            InputAdapterComponent inputComponent = applicationContext.getBean(InputAdapterComponent.class);
            inputComponent.stopPipeline();
            log.info("Input adapters stopped");

            // 큐 처리 대기 (큐가 어느정도 비워지도록 잠시 대기)
            Thread.sleep(1000);

            // Message Dispatcher는 계속 실행 (큐 처리를 위해)
            log.info("Message dispatcher continues processing existing messages");

            // Output Adapters 중지
            OutputAdapterComponent outputComponent = applicationContext.getBean(OutputAdapterComponent.class);
            outputComponent.stopPipeline();
            log.info("Output adapters stopped");

        } catch (Exception e) {
            log.error("Error stopping pipeline components", e);
            throw new RuntimeException("Failed to stop pipeline components", e);
        }
    }

    /**
     * 설정 검증
     */
    private void validateConfiguration() {
        var validationResult = validationService.validatePipelineIntegrity();

        if (!validationResult.isValid()) {
            log.error("Pipeline validation failed: {}", validationResult.errors());
            throw new RuntimeException("Pipeline validation failed: " + validationResult.errors());
        }

        if (!validationResult.warnings().isEmpty()) {
            log.warn("Pipeline validation warnings: {}", validationResult.warnings());
        }
    }

    /**
     * 파이프라인 컴포넌트 재시작
     */
    private void startPipelineComponents() {
        try {
            // Parse Service와 Transform Service 리로드
            ParseService parseService = applicationContext.getBean(ParseService.class);
            TransformService transformService = applicationContext.getBean(TransformService.class);

            // 데이터베이스에서 설정을 다시 로드
            log.info("Reloading Parse and Transform services from database");
            parseService.reload();
            transformService.reload();
            log.info("Parse and Transform services reloaded successfully");

            // Input Adapters 재시작
            InputAdapterComponent inputComponent = applicationContext.getBean(InputAdapterComponent.class);
            inputComponent.startPipeline();
            log.info("Input adapters restarted");

            // Output Adapters 재시작
            OutputAdapterComponent outputComponent = applicationContext.getBean(OutputAdapterComponent.class);
            outputComponent.startPipeline();
            log.info("Output adapters restarted");

        } catch (Exception e) {
            log.error("Error starting pipeline components", e);
            throw new RuntimeException("Failed to start pipeline components", e);
        }
    }

    public void validateAndReload() {
        log.info("Validating pipeline before reload");

        // Validate pipeline integrity first
        var validationResult = validationService.validatePipelineIntegrity();

        if (!validationResult.isValid()) {
            log.error("Pipeline validation failed: {}", validationResult.errors());
            throw new RuntimeException("Pipeline validation failed: " + validationResult.errors());
        }

        if (!validationResult.warnings().isEmpty()) {
            log.warn("Pipeline validation warnings: {}", validationResult.warnings());
        }

        // Proceed with reload
        reloadConfiguration();
    }

    public void restartPipeline() {
        log.info("Restarting pipeline");

        if (!reloadInProgress.compareAndSet(false, true)) {
            throw new RuntimeException("Reload already in progress");
        }

        try {
            reloadProgress.set(0);
            currentStatus = PipelineStatus.STOPPING;

            // Step 1: Stop accepting new messages
            log.info("Stopping input adapters");
            reloadProgress.set(20);

            // Step 2: Wait for queue to drain
            log.info("Draining message queue");
            reloadProgress.set(40);

            // Step 3: Stop all components
            log.info("Stopping all components");
            reloadProgress.set(60);

            currentStatus = PipelineStatus.STOPPED;

            // Step 4: Reload configuration
            log.info("Reloading configuration");
            reloadProgress.set(70);

            // Step 5: Restart components
            log.info("Restarting components");
            reloadProgress.set(90);

            // Step 6: Complete restart
            log.info("Restart completed");
            reloadProgress.set(100);
            currentStatus = PipelineStatus.RUNNING;

        } catch (Exception e) {
            log.error("Failed to restart pipeline", e);
            currentStatus = PipelineStatus.ERROR;
            throw new RuntimeException("Failed to restart pipeline", e);
        } finally {
            reloadInProgress.set(false);
        }
    }

    // ==================== Status Operations ====================

    public boolean isReloadInProgress() {
        return reloadInProgress.get();
    }

    public ReloadProgress getReloadProgress() {
        return new ReloadProgress(
                reloadProgress.get(),
                currentStatus,
                reloadInProgress.get()
        );
    }

    public PipelineStatusInfo getPipelineStatus() {
        try {
            // 데이터베이스에서 활성화된 컴포넌트 수 조회
            int inputAdapterCount = configManagementService.getEnabledInputAdapters().size();
            int parserCount = configManagementService.getEnabledParsers().size();
            int transformCount = configManagementService.getEnabledTransforms().size();
            int outputAdapterCount = configManagementService.getEnabledOutputAdapters().size();

            // MessageDispatcher에서 큐 정보 가져오기
            int queueSize = 0;
            long processedMessages = 0;

            try {
                MessageDispatcher dispatcher = applicationContext.getBean(MessageDispatcher.class);
                var metrics = dispatcher.getQueueMetrics();
                queueSize = metrics.globalQueueSize + metrics.outputQueueSize;
                processedMessages = metrics.totalProcessed;
            } catch (Exception e) {
                log.debug("Could not retrieve queue metrics: {}", e.getMessage());
            }

            return new PipelineStatusInfo(
                    currentStatus,
                    inputAdapterCount,
                    parserCount,
                    transformCount,
                    outputAdapterCount,
                    queueSize,
                    processedMessages
            );
        } catch (Exception e) {
            log.error("Error getting pipeline status", e);
            return new PipelineStatusInfo(
                    PipelineStatus.ERROR,
                    0, 0, 0, 0, 0, 0
            );
        }
    }

    public void cancelReload() {
        log.info("Cancelling reload");

        if (reloadInProgress.compareAndSet(true, false)) {
            currentStatus = PipelineStatus.RUNNING;
            reloadProgress.set(0);
            log.info("Reload cancelled");
        } else {
            log.warn("No reload in progress to cancel");
        }
    }

    // ==================== Inner Classes ====================

    public enum PipelineStatus {
        RUNNING,
        STOPPED,
        RELOADING,
        STOPPING,
        ERROR
    }

    public record ReloadProgress(
            int progress,
            PipelineStatus status,
            boolean inProgress
    ) {}

    public record PipelineStatusInfo(
            PipelineStatus status,
            int inputAdapterCount,
            int parserCount,
            int transformCount,
            int outputAdapterCount,
            int queueSize,
            long processedMessages
    ) {}
}
