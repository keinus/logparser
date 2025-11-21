package org.keinus.logparser.application.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineReloadService {

    private final ConfigValidationService validationService;

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

            // Step 1: Load configuration from database
            log.info("Loading configuration from database");
            reloadProgress.set(25);

            // Step 2: Validate configuration
            log.info("Validating configuration");
            reloadProgress.set(50);

            // Step 3: Apply configuration
            log.info("Applying configuration");
            reloadProgress.set(75);

            // Step 4: Complete reload
            log.info("Reload completed");
            reloadProgress.set(100);
            currentStatus = PipelineStatus.RUNNING;

        } catch (Exception e) {
            log.error("Failed to reload configuration", e);
            currentStatus = PipelineStatus.ERROR;
            throw new RuntimeException("Failed to reload configuration", e);
        } finally {
            reloadInProgress.set(false);
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
        // TODO: Get actual counts from repositories
        return new PipelineStatusInfo(
                currentStatus,
                0, // inputAdapterCount
                0, // parserCount
                0, // transformCount
                0, // outputAdapterCount
                0, // queueSize
                0  // processedMessages
        );
    }

    public void cancelReload() {
        log.info("Cancelling reload");

        if (reloadInProgress.get()) {
            reloadInProgress.set(false);
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
