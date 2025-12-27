package org.keinus.logparser.application.pipeline;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.parsing.service.ParseService;
import org.keinus.logparser.infrastructure.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParseDispatcher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ParseDispatcher.class);

    private final BlockingQueue<LogEvent> inputQueue;
    private final BlockingQueue<LogEvent> transformQueue;
    private final ParseService parseService;
    private final CircuitBreaker circuitBreaker;
    private final AtomicBoolean running;
    private final AtomicLong totalMessagesFailed;

    public ParseDispatcher(
            BlockingQueue<LogEvent> inputQueue,
            BlockingQueue<LogEvent> transformQueue,
            ParseService parseService,
            CircuitBreaker circuitBreaker,
            AtomicBoolean running,
            AtomicLong totalMessagesFailed) {
        this.inputQueue = inputQueue;
        this.transformQueue = transformQueue;
        this.parseService = parseService;
        this.circuitBreaker = circuitBreaker;
        this.running = running;
        this.totalMessagesFailed = totalMessagesFailed;
    }

    @Override
    public void run() {
        log.info("Parser thread started: {}", Thread.currentThread().getName());
        while (running.get()) {
            try {
                if (!circuitBreaker.check()) {
                    ThreadUtil.sleep(1000);
                    continue;
                }

                LogEvent logEvent = inputQueue.poll();
                if(logEvent != null) {
                    processParse(logEvent);
                    circuitBreaker.recordSuccess();
                } else {
                    ThreadUtil.sleep(100);
                }
            } catch (Exception e) {
                circuitBreaker.recordFailure();
                log.error("Error processing log event (consecutive failures: {}), continuing...",
                        circuitBreaker.getConsecutiveFailures(), e);
                ThreadUtil.sleep(100);
            }
        }
        log.info("Parser thread finished: {}", Thread.currentThread().getName());
    }

    private void processParse(LogEvent logEvent) {
        try {
            log.debug("Parsing log event of type: {}", logEvent.getMessageType());

            boolean parseResult = parseService.parse(logEvent);
            if (parseResult) {
                logEvent.markAsParsed();
                boolean queued = putTransformMsg(logEvent);
                log.debug("Log event parsed and queued for transform: {}, queue size: {}", queued, transformQueue.size());
            } else {
                log.debug("Log event parsing failed");
                logEvent.markAsError("Parsing failed");
                totalMessagesFailed.incrementAndGet();
                log.error("Failed to parse log event: {}", logEvent);
            }
        } catch (Exception e) {
            log.error("Error parsing log event: {}", logEvent, e);
            logEvent.markAsError("Parsing error: " + e.getMessage());
            totalMessagesFailed.incrementAndGet();
        }
    }

    private boolean putTransformMsg(LogEvent logEvent) {
        try {
            // 큐가 가득 차면 공간이 생길 때까지 무한 대기
            transformQueue.put(logEvent);
            return true;
        } catch (InterruptedException e) {
            log.debug("Interrupted while putting message to transform queue");
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
