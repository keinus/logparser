package org.keinus.logparser.application.pipeline;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.transformation.service.TransformService;
import org.keinus.logparser.infrastructure.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransformDispatcher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(TransformDispatcher.class);

    private final BlockingQueue<LogEvent> transformQueue;
    private final BlockingQueue<LogEvent> outputQueue;
    private final TransformService transformService;
    private final AtomicBoolean running;
    private final AtomicLong totalMessagesFailed;

    public TransformDispatcher(
            BlockingQueue<LogEvent> transformQueue,
            BlockingQueue<LogEvent> outputQueue,
            TransformService transformService,
            AtomicBoolean running,
            AtomicLong totalMessagesFailed) {
        this.transformQueue = transformQueue;
        this.outputQueue = outputQueue;
        this.transformService = transformService;
        this.running = running;
        this.totalMessagesFailed = totalMessagesFailed;
    }

    @Override
    public void run() {
        log.info("Transform thread started: {}", Thread.currentThread().getName());
        while (running.get()) {
            try {
                LogEvent logEvent = transformQueue.poll();
                if(logEvent != null)
                    processTransform(logEvent);
                else
                    ThreadUtil.sleep(100);
            } catch (Exception e) {
                log.error("Error in transform loop", e);
                ThreadUtil.sleep(100);
            }
        }
        log.info("Transform thread finished: {}", Thread.currentThread().getName());
    }

    private void processTransform(LogEvent logEvent) {
        try {
            boolean transformResult = transformService.transform(logEvent);
            if (transformResult) {
                logEvent.markAsTransformed();
                boolean queued = putOutputMsg(logEvent);
                log.debug("Log event transformed and queued for output: {}, queue size: {}", queued, outputQueue.size());
            } else {
                log.debug("Log event filtered out by transform service");
            }
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
