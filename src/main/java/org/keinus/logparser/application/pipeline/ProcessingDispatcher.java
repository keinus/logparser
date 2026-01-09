package org.keinus.logparser.application.pipeline;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.parse.service.ParseService;
import org.keinus.logparser.domain.transformation.service.StructuredTransformService;
import org.keinus.logparser.domain.transformation.service.TransformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProcessingDispatcher
 * 
 * Consolidates the Parse and Transform stages into a single processing unit.
 * Consumes raw events from inputQueue, parses them, applies transformations,
 * and puts them into outputQueue.
 */
public class ProcessingDispatcher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ProcessingDispatcher.class);

    private final BlockingQueue<LogEvent> inputQueue;
    private final BlockingQueue<LogEvent> outputQueue;
    private final ParseService parseService;
    private final TransformService transformService;
    private final StructuredTransformService structuredTransformService;
    private final AtomicBoolean running;
    private final AtomicLong totalMessagesFailed;

    public ProcessingDispatcher(
            BlockingQueue<LogEvent> inputQueue,
            BlockingQueue<LogEvent> outputQueue,
            ParseService parseService,
            TransformService transformService,
            StructuredTransformService structuredTransformService,
            AtomicBoolean running,
            AtomicLong totalMessagesFailed) {
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.parseService = parseService;
        this.transformService = transformService;
        this.structuredTransformService = structuredTransformService;
        this.running = running;
        this.totalMessagesFailed = totalMessagesFailed;
    }

    @Override
    public void run() {
        log.info("Processing thread started: {}", Thread.currentThread().getName());
        while (running.get()) {
            try {
                LogEvent logEvent = inputQueue.take();
                processEvent(logEvent);
            } catch (InterruptedException e) {
                log.info("Processing thread interrupted: {}", Thread.currentThread().getName());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                totalMessagesFailed.incrementAndGet();
                log.error("Unexpected error in processing thread", e);
            }
        }
        log.info("Processing thread finished: {}", Thread.currentThread().getName());
    }

    private void processEvent(LogEvent logEvent) {
        try {
            // ==========================================
            // Stage 1: Parsing
            // ==========================================
            // log.debug("Processing log event: {}", logEvent.getMessageType()); // Verbose log

            boolean parseResult = parseService.parse(logEvent);
            if (!parseResult) {
                // Parsing failed
                logEvent.markAsError("Parsing failed");
                totalMessagesFailed.incrementAndGet();
                log.error("Failed to parse log event: {}", logEvent);
                // Optionally decide whether to send to DLQ or drop. 
                // Currently logic implies we might just drop or log error.
                return; 
            }
            logEvent.markAsParsed();

            // ==========================================
            // Stage 2: Transformation (Legacy/Field)
            // ==========================================
            boolean transformResult = transformService.transform(logEvent);
            if (!transformResult) {
                log.debug("Log event filtered out by transform service");
                return; // Filtered out
            }

            // ==========================================
            // Stage 3: Transformation (Structured)
            // ==========================================
            if (structuredTransformService != null) {
                boolean structuredResult = structuredTransformService.applyToLogEvent(logEvent);
                if (!structuredResult) {
                    log.warn("Structured transformation failed for event: {}", logEvent);
                    // Decide whether to drop. For now, we continue if legacy succeeded.
                }
            }
            logEvent.markAsTransformed();

            // ==========================================
            // Stage 4: Output
            // ==========================================
            putOutputMsg(logEvent);

        } catch (Exception e) {
            log.error("Error processing log event: {}", logEvent, e);
            logEvent.markAsError("Processing error: " + e.getMessage());
            totalMessagesFailed.incrementAndGet();
        }
    }

    private boolean putOutputMsg(LogEvent logEvent) {
        try {
            // Infinite blocking wait if queue is full (Backpressure)
            outputQueue.put(logEvent);
            return true;
        } catch (InterruptedException e) {
            log.debug("Interrupted while putting message to output queue");
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
