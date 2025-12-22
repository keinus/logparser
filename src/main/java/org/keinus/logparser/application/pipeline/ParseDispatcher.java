package org.keinus.logparser.application.pipeline;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.parsing.service.ParseService;
import org.keinus.logparser.infrastructure.util.DeadLetterQueue;
import org.keinus.logparser.infrastructure.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParseDispatcher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ParseDispatcher.class);

    private final BlockingQueue<LogEvent> inputQueue;
    private final BlockingQueue<LogEvent> transformQueue;
    private final ParseService parseService;
    private final DeadLetterQueue deadLetterQueue;
    private final CircuitBreaker circuitBreaker;
    private final AtomicBoolean running;
    private final AtomicLong totalMessagesFailed;
    private final AtomicLong totalMessagesDropped;
    private final int maxQueueSize;

    private long lastCriticalLogTime = 0;
    private static final long LOG_INTERVAL_MS = 5000;

    private static final double QUEUE_CRITICAL_THRESHOLD = 0.95;
    private static final long QUEUE_OFFER_TIMEOUT_MS = 50_000;

    public ParseDispatcher(
            BlockingQueue<LogEvent> inputQueue,
            BlockingQueue<LogEvent> transformQueue,
            ParseService parseService,
            DeadLetterQueue deadLetterQueue,
            CircuitBreaker circuitBreaker,
            AtomicBoolean running,
            AtomicLong totalMessagesFailed,
            AtomicLong totalMessagesDropped,
            int maxQueueSize) {
        this.inputQueue = inputQueue;
        this.transformQueue = transformQueue;
        this.parseService = parseService;
        this.deadLetterQueue = deadLetterQueue;
        this.circuitBreaker = circuitBreaker;
        this.running = running;
        this.totalMessagesFailed = totalMessagesFailed;
        this.totalMessagesDropped = totalMessagesDropped;
        this.maxQueueSize = maxQueueSize;
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

                LogEvent logEvent = inputQueue.take();
                processParse(logEvent);
                circuitBreaker.recordSuccess();

            } catch (InterruptedException e) {
                if (!running.get()) {
                    log.info("Parser thread shutting down: {}", Thread.currentThread().getName());
                    return;
                }
                log.debug("Parser thread interrupted but continuing: {}", Thread.currentThread().getName());
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
                deadLetterQueue.addFromLogEvent(logEvent, 0);
            }
        } catch (Exception e) {
            log.error("Error parsing log event: {}", logEvent, e);
            logEvent.markAsError("Parsing error: " + e.getMessage());
            totalMessagesFailed.incrementAndGet();
            deadLetterQueue.addFromLogEvent(logEvent, 0);
        }
    }

    private boolean putTransformMsg(LogEvent logEvent) {
        int currentSize = transformQueue.size();
        double utilizationRate = (double) currentSize / maxQueueSize;

        if (utilizationRate >= QUEUE_CRITICAL_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastCriticalLogTime >= LOG_INTERVAL_MS) {
                log.warn("Transform queue critical! Size: {}/{} ({}%), rejecting message",
                        currentSize, maxQueueSize, String.format("%.1f", utilizationRate * 100));
                lastCriticalLogTime = now;
            }
            totalMessagesDropped.incrementAndGet();
            return false;
        }

        try {
            boolean offered = transformQueue.offer(logEvent, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!offered) {
                totalMessagesDropped.incrementAndGet();
                log.warn("Transform message dropped due to queue insertion timeout. Queue size: {}/{}",
                        currentSize, maxQueueSize);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            log.debug("Interrupted while offering message to transform queue");
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
