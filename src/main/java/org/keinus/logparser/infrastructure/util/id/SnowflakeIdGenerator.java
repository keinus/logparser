package org.keinus.logparser.infrastructure.util.id;

import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Twitter Snowflake 알고리즘 기반의 ID 생성기.
 * <p>
 * 64비트 ID 구조:
 * - 1 bit: Unused (sign bit)
 * - 41 bits: Timestamp (milliseconds since epoch)
 * - 5 bits: Datacenter ID
 * - 5 bits: Worker ID
 * - 12 bits: Sequence number
 */
@Component
public class SnowflakeIdGenerator {
    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    private static final long UNUSED_BITS = 1L;
    private static final long EPOCH_BITS = 41L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_DATACENTER_ID = (1L << DATACENTER_ID_BITS) - 1;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    // Custom Epoch (2024-01-01 00:00:00 UTC)
    private static final long CUSTOM_EPOCH = 1704067200000L;

    private final long datacenterId;
    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator() {
        this.datacenterId = getDatacenterId();
        this.workerId = getWorkerId(datacenterId);
        log.info("SnowflakeIdGenerator initialized. Datacenter ID: {}, Worker ID: {}", datacenterId, workerId);
    }

    public synchronized long nextId() {
        long currentTimestamp = timestamp();

        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException("Invalid System Clock!");
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence Exhausted, wait till next millisecond.
                currentTimestamp = waitNextMillis(currentTimestamp);
            }
        } else {
            // Reset sequence to start with random value to avoid pattern detection? No, standard is 0.
            sequence = 0;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - CUSTOM_EPOCH) << (DATACENTER_ID_BITS + WORKER_ID_BITS + SEQUENCE_BITS))
                | (datacenterId << (WORKER_ID_BITS + SEQUENCE_BITS))
                | (workerId << SEQUENCE_BITS)
                | sequence;
    }

    private long timestamp() {
        return Instant.now().toEpochMilli();
    }

    private long waitNextMillis(long currentTimestamp) {
        while (currentTimestamp == lastTimestamp) {
            currentTimestamp = timestamp();
        }
        return currentTimestamp;
    }

    private long getDatacenterId() {
        try {
            return (getMacAddress() & MAX_DATACENTER_ID);
        } catch (Exception e) {
            log.warn("Failed to get Mac Address for Datacenter ID, using random.", e);
            return new SecureRandom().nextInt((int) MAX_DATACENTER_ID + 1);
        }
    }

    private long getWorkerId(long datacenterId) {
        StringBuilder mpId = new StringBuilder();
        mpId.append(datacenterId);
        String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        if (name != null && !name.isEmpty()) {
            mpId.append(name.split("@")[0]);
        }
        return (mpId.toString().hashCode() & 0xffff) % (MAX_WORKER_ID + 1);
    }

    private long getMacAddress() throws Exception {
        Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
        while (en.hasMoreElements()) {
            NetworkInterface iface = en.nextElement();
            if (iface != null && !iface.isLoopback() && iface.getHardwareAddress() != null) {
                byte[] mac = iface.getHardwareAddress();
                return ((mac[mac.length - 1] & 0xFF) | ((mac[mac.length - 2] & 0xFF) << 8)) & 0xFFFF;
            }
        }
        return new SecureRandom().nextLong();
    }
}
