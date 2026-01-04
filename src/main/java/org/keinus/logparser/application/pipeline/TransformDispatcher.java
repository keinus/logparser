package org.keinus.logparser.application.pipeline;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.transformation.service.TransformService;
import org.keinus.logparser.domain.service.transform.StructuredTransformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransformDispatcher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(TransformDispatcher.class);

    private final BlockingQueue<LogEvent> transformQueue;
    private final BlockingQueue<LogEvent> outputQueue;
    private final TransformService transformService;
    private final StructuredTransformService structuredTransformService;
    private final AtomicBoolean running;
    private final AtomicLong totalMessagesFailed;

    public TransformDispatcher(
            BlockingQueue<LogEvent> transformQueue,
            BlockingQueue<LogEvent> outputQueue,
            TransformService transformService,
            StructuredTransformService structuredTransformService,
            AtomicBoolean running,
            AtomicLong totalMessagesFailed) {
        this.transformQueue = transformQueue;
        this.outputQueue = outputQueue;
        this.transformService = transformService;
        this.structuredTransformService = structuredTransformService;
        this.running = running;
        this.totalMessagesFailed = totalMessagesFailed;
    }

    @Override
    public void run() {
        log.info("Transform thread started: {}", Thread.currentThread().getName());
        while (running.get()) {
            try {
                LogEvent logEvent = transformQueue.take();
                processTransform(logEvent);
            } catch (InterruptedException e) {
                log.info("Transform thread interrupted: {}", Thread.currentThread().getName());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in transform loop", e);
            }
        }
        log.info("Transform thread finished: {}", Thread.currentThread().getName());
    }

    private void processTransform(LogEvent logEvent) {
        try {
            // 1. Apply Legacy Transforms (Filtering, AddProperty, RemoveProperty, etc.)
            boolean transformResult = transformService.transform(logEvent);
            if (!transformResult) {
                log.debug("Log event filtered out by transform service");
                return;
            }

            // 2. Apply Structured Transformation (Mapping to Logical Schema)
            if (structuredTransformService != null) {
                boolean structuredResult = structuredTransformService.applyToLogEvent(logEvent);
                if (!structuredResult) {
                    log.warn("Structured transformation failed for event: {}", logEvent);
                    // Decide whether to continue or drop. 
                    // For now, we continue since legacy transform succeeded.
                }
            }

            // 3. Queue for output
            logEvent.markAsTransformed();
            boolean queued = putOutputMsg(logEvent);
            log.debug("Log event transformed and queued for output: {}, queue size: {}", queued, outputQueue.size());

        } catch (Exception e) {
            log.error("Error transforming log event: {}", logEvent, e);
            logEvent.markAsError("Transform error: " + e.getMessage());
            totalMessagesFailed.incrementAndGet();
        }
    }

    private boolean putOutputMsg(LogEvent logEvent) {
        try {
            // 큐가 가득 차면 공간이 생길 때까지 무한 대기
            outputQueue.put(logEvent);
            return true;
        } catch (InterruptedException e) {
            log.debug("Interrupted while putting message to output queue");
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
