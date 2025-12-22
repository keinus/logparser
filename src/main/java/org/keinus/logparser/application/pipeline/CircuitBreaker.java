package org.keinus.logparser.application.pipeline;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircuitBreaker {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    private static final long FAILURE_THRESHOLD = 10;
    private static final long RESET_TIMEOUT_MS = 30_000;
    
    private volatile long circuitOpenedAt = 0;
    private enum CircuitState { CLOSED, OPEN, HALF_OPEN }
    private volatile CircuitState circuitState = CircuitState.CLOSED;

    public boolean check() {
        long currentTime = System.currentTimeMillis();

        switch (circuitState) {
            case CLOSED:
                return true;
            case OPEN:
                if (currentTime - circuitOpenedAt >= RESET_TIMEOUT_MS) {
                    log.info("Circuit breaker transitioning to HALF_OPEN state after timeout");
                    circuitState = CircuitState.HALF_OPEN;
                    return true;
                }
                return false;
            case HALF_OPEN:
                return true;
            default:
                return true;
        }
    }

    public void recordSuccess() {
        long failures = consecutiveFailures.getAndSet(0);

        if (circuitState == CircuitState.HALF_OPEN) {
            log.info("Circuit breaker transitioning to CLOSED state after successful request");
            circuitState = CircuitState.CLOSED;
        } else if (failures > 0) {
            log.debug("Consecutive failures reset to 0 (was: {})", failures);
        }
    }

    public void recordFailure() {
        long failures = consecutiveFailures.incrementAndGet();

        if (circuitState == CircuitState.HALF_OPEN) {
            log.warn("Circuit breaker transitioning back to OPEN state after failed request in HALF_OPEN");
            circuitState = CircuitState.OPEN;
            circuitOpenedAt = System.currentTimeMillis();
        } else if (failures >= FAILURE_THRESHOLD && circuitState == CircuitState.CLOSED) {
            log.error("Circuit breaker OPENING after {} consecutive failures", failures);
            circuitState = CircuitState.OPEN;
            circuitOpenedAt = System.currentTimeMillis();
        }
    }

    public String getState() {
        return circuitState.name();
    }
    
    public long getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
}
