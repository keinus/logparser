package org.keinus.logparser.infrastructure.monitoring;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.keinus.logparser.application.pipeline.MessageDispatcher;
import org.keinus.logparser.infrastructure.util.PatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.OperatingSystemMXBean;

/**
 * LogParser 애플리케이션의 모니터링 메트릭을 수집하고 JMX를 통해 노출하는 구현 클래스입니다.
 * <p>
 * 이 클래스는 {@link LogParserMonitoringMBean} 인터페이스를 구현하며,
 * MessageDispatcher와 시스템 메트릭을 수집하여 JMX를 통해 제공합니다.
 *
 * @see LogParserMonitoringMBean
 * @see MessageDispatcher
 */
public class LogParserMonitoring implements LogParserMonitoringMBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogParserMonitoring.class);

    private final MessageDispatcher messageDispatcher;
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    private final OperatingSystemMXBean osMXBean;
    private final long startTime;

    /**
     * LogParserMonitoring 인스턴스를 생성합니다.
     *
     * @param messageDispatcher 모니터링할 MessageDispatcher 인스턴스
     */
    public LogParserMonitoring(MessageDispatcher messageDispatcher) {
        this.messageDispatcher = messageDispatcher;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.osMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public long getGlobalQueueSize() {
        MessageDispatcher.QueueMetrics metrics = messageDispatcher.getQueueMetrics();
        return metrics.globalQueueSize;
    }

    @Override
    public long getOutputQueueSize() {
        MessageDispatcher.QueueMetrics metrics = messageDispatcher.getQueueMetrics();
        return metrics.outputQueueSize;
    }

    @Override
    public long getMaxQueueSize() {
        MessageDispatcher.QueueMetrics metrics = messageDispatcher.getQueueMetrics();
        return metrics.maxQueueSize;
    }

    @Override
    public double getGlobalQueueUtilization() {
        MessageDispatcher.QueueMetrics metrics = messageDispatcher.getQueueMetrics();
        return metrics.getGlobalUtilization() * 100.0;
    }

    @Override
    public double getOutputQueueUtilization() {
        MessageDispatcher.QueueMetrics metrics = messageDispatcher.getQueueMetrics();
        return metrics.getOutputUtilization() * 100.0;
    }

    @Override
    public long getTotalMessagesReceived() {
        MessageDispatcher.QueueMetrics metrics = messageDispatcher.getQueueMetrics();
        return metrics.totalReceived;
    }

    @Override
    public long getTotalMessagesProcessed() {
        MessageDispatcher.QueueMetrics metrics = messageDispatcher.getQueueMetrics();
        return metrics.totalProcessed;
    }

    @Override
    public long getTotalMessagesDropped() {
        MessageDispatcher.QueueMetrics metrics = messageDispatcher.getQueueMetrics();
        return metrics.totalDropped;
    }

    @Override
    public long getTotalMessagesFailed() {
        MessageDispatcher.QueueMetrics metrics = messageDispatcher.getQueueMetrics();
        return metrics.totalFailed;
    }

    @Override
    public double getCpuUsage() {
        try {
            return osMXBean.getProcessCpuLoad() * 100.0;
        } catch (Exception e) {
            LOGGER.debug("Unable to get CPU usage: {}", e.getMessage());
            return -1.0;
        }
    }

    @Override
    public long getUsedMemory() {
        return memoryMXBean.getHeapMemoryUsage().getUsed();
    }

    @Override
    public long getMaxMemory() {
        return memoryMXBean.getHeapMemoryUsage().getMax();
    }

    @Override
    public double getMemoryUtilization() {
        long used = getUsedMemory();
        long max = getMaxMemory();
        if (max <= 0) {
            return 0.0;
        }
        return (double) used / max * 100.0;
    }

    @Override
    public int getActiveThreads() {
        return threadMXBean.getThreadCount();
    }

    @Override
    public long getUptimeMillis() {
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public String getUptimeFormatted() {
        long uptimeMs = getUptimeMillis();
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours % 24, minutes % 60, seconds % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    @Override
    public double getMessagesPerSecond() {
        long uptime = getUptimeMillis() / 1000; // seconds
        if (uptime == 0) {
            return 0.0;
        }
        return (double) getTotalMessagesProcessed() / uptime;
    }

    @Override
    public double getPatternCacheHitRate() {
        try {
            PatternCache cache = PatternCache.getInstance();
            return cache.getHitRate();
        } catch (Exception e) {
            LOGGER.debug("Unable to get pattern cache hit rate: {}", e.getMessage());
            return 0.0;
        }
    }

    @Override
    public int getPatternCacheSize() {
        try {
            PatternCache cache = PatternCache.getInstance();
            return cache.size();
        } catch (Exception e) {
            LOGGER.debug("Unable to get pattern cache size: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public void resetStatistics() {
        LOGGER.info("Resetting statistics...");

        // MessageDispatcher 통계 리셋
        messageDispatcher.resetStatistics();

        // PatternCache 통계 리셋
        try {
            PatternCache cache = PatternCache.getInstance();
            cache.resetStatistics();
        } catch (Exception e) {
            LOGGER.warn("Failed to reset pattern cache statistics: {}", e.getMessage());
        }

        LOGGER.info("Statistics reset completed");
    }

    @Override
    public void forceGarbageCollection() {
        LOGGER.info("Forcing garbage collection...");
        System.gc();
        LOGGER.info("Garbage collection completed");
    }

    @Override
    public int getDeadLetterQueueSize() {
        return messageDispatcher.getDeadLetterQueue().size();
    }

    @Override
    public int getDeadLetterQueueMaxSize() {
        return messageDispatcher.getDeadLetterQueue().getMaxSize();
    }

    @Override
    public double getDeadLetterQueueUtilization() {
        return messageDispatcher.getDeadLetterQueue().getUtilization();
    }

    @Override
    public long getTotalDeadLetterMessages() {
        return messageDispatcher.getDeadLetterQueue().getTotalFailedMessages();
    }

    @Override
    public long getTotalFlushedDeadLetterMessages() {
        return messageDispatcher.getDeadLetterQueue().getTotalFlushedMessages();
    }

    @Override
    public int flushDeadLetterQueue() {
        LOGGER.info("Manual DLQ flush requested via JMX");
        return messageDispatcher.getDeadLetterQueue().flush();
    }

    /**
     * JMX MBean을 등록합니다.
     *
     * @param messageDispatcher 모니터링할 MessageDispatcher 인스턴스
     * @return 등록된 LogParserMonitoring 인스턴스, 실패 시 null
     */
    public static LogParserMonitoring registerMBean(MessageDispatcher messageDispatcher) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("org.keinus.logparser:type=Monitoring");

            // 이미 등록된 경우 제거
            if (mbs.isRegistered(name)) {
                LOGGER.info("Unregistering existing MBean");
                mbs.unregisterMBean(name);
            }

            LogParserMonitoring monitoring = new LogParserMonitoring(messageDispatcher);
            mbs.registerMBean(monitoring, name);
            LOGGER.info("JMX MBean registered successfully: {}", name);
            return monitoring;
        } catch (Exception e) {
            LOGGER.error("Failed to register JMX MBean", e);
            return null;
        }
    }

    /**
     * JMX MBean을 등록 해제합니다.
     */
    public static void unregisterMBean() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("org.keinus.logparser:type=Monitoring");
            if (mbs.isRegistered(name)) {
                mbs.unregisterMBean(name);
                LOGGER.info("JMX MBean unregistered successfully");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to unregister JMX MBean", e);
        }
    }
}
