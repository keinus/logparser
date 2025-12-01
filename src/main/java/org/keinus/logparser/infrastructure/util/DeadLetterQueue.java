package org.keinus.logparser.infrastructure.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.keinus.logparser.domain.model.FailedMessage;
import org.keinus.logparser.domain.model.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dead Letter Queue (DLQ) - 반복적으로 실패한 메시지를 별도로 관리하는 큐입니다.
 * <p>
 * 파싱이나 전송에 실패한 메시지를 보관하고, 주기적으로 파일에 저장하여
 * 추후 분석이나 재처리를 가능하게 합니다.
 * <p>
 * 주요 기능:
 * <ul>
 *     <li>실패한 메시지를 메모리 큐에 보관</li>
 *     <li>주기적으로 파일에 flush하여 영구 저장</li>
 *     <li>JSON 또는 CSV 형식으로 저장</li>
 *     <li>큐 크기 제한으로 메모리 보호</li>
 *     <li>통계 추적 (총 실패 메시지 수)</li>
 * </ul>
 */
public class DeadLetterQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeadLetterQueue.class);
    private static final int DEFAULT_MAX_SIZE = 10000;
    private static final String DEFAULT_OUTPUT_DIR = "./dlq";
    private static final long DEFAULT_MESSAGE_RETENTION_MS = 24 * 60 * 60 * 1000; // 24시간

    private final BlockingQueue<FailedMessage> dlq;
    private final int maxSize;
    private final String outputDirectory;
    private final OutputFormat outputFormat;
    private final long messageRetentionMs;

    // 통계
    private final AtomicLong totalFailedMessages = new AtomicLong(0);
    private final AtomicLong totalFlushedMessages = new AtomicLong(0);
    private final AtomicLong droppedDueToCapacity = new AtomicLong(0);
    private final AtomicLong expiredMessages = new AtomicLong(0);

    /**
     * 출력 형식을 정의하는 열거형
     */
    public enum OutputFormat {
        JSON,
        CSV
    }

    /**
     * 기본 설정으로 DeadLetterQueue를 생성합니다.
     */
    public DeadLetterQueue() {
        this(DEFAULT_MAX_SIZE, DEFAULT_OUTPUT_DIR, OutputFormat.JSON, DEFAULT_MESSAGE_RETENTION_MS);
    }

    /**
     * 지정된 설정으로 DeadLetterQueue를 생성합니다.
     *
     * @param maxSize 최대 큐 크기
     * @param outputDirectory 출력 디렉토리
     * @param outputFormat 출력 형식 (JSON 또는 CSV)
     */
    public DeadLetterQueue(int maxSize, String outputDirectory, OutputFormat outputFormat) {
        this(maxSize, outputDirectory, outputFormat, DEFAULT_MESSAGE_RETENTION_MS);
    }

    /**
     * 지정된 설정으로 DeadLetterQueue를 생성합니다.
     *
     * @param maxSize 최대 큐 크기
     * @param outputDirectory 출력 디렉토리
     * @param outputFormat 출력 형식 (JSON 또는 CSV)
     * @param messageRetentionMs 메시지 보관 시간 (밀리초)
     */
    public DeadLetterQueue(int maxSize, String outputDirectory, OutputFormat outputFormat, long messageRetentionMs) {
        this.maxSize = maxSize;
        this.outputDirectory = outputDirectory;
        this.outputFormat = outputFormat;
        this.messageRetentionMs = messageRetentionMs;
        this.dlq = new LinkedBlockingQueue<>(maxSize);

        // 출력 디렉토리 생성
        createOutputDirectory();

        LOGGER.info("DeadLetterQueue initialized: maxSize={}, outputDir={}, format={}, retentionMs={}ms ({}h)",
                maxSize, outputDirectory, outputFormat, messageRetentionMs, messageRetentionMs / (60 * 60 * 1000));
    }

    private void createOutputDirectory() {
        try {
            Path path = Paths.get(outputDirectory);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                LOGGER.info("Created DLQ output directory: {}", outputDirectory);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create DLQ output directory: {}", outputDirectory, e);
        }
    }

    /**
     * 실패한 메시지를 DLQ에 추가합니다.
     *
     * @param message 원본 메시지
     * @param reason 실패 사유
     * @param retryCount 재시도 횟수
     * @return 추가 성공 여부
     */
    public boolean add(String message, String reason, int retryCount) {
        return add(new FailedMessage(message, reason, retryCount, System.currentTimeMillis()));
    }

    /**
     * 실패한 LogEvent를 DLQ에 추가합니다.
     *
     * @param logEvent 실패한 LogEvent
     * @param retryCount 재시도 횟수
     * @return 추가 성공 여부
     */
    public boolean addFromLogEvent(LogEvent logEvent, int retryCount) {
        return add(FailedMessage.fromLogEvent(logEvent, retryCount));
    }

    /**
     * FailedMessage를 DLQ에 추가합니다.
     *
     * @param failedMessage 실패한 메시지
     * @return 추가 성공 여부
     */
    public boolean add(FailedMessage failedMessage) {
        totalFailedMessages.incrementAndGet();

        boolean offered = dlq.offer(failedMessage);
        if (!offered) {
            LOGGER.warn("DLQ is full (size: {}), attempting to clean expired messages", dlq.size());

            // 1. 먼저 expired 메시지 제거 시도
            int removedExpired = removeExpiredMessages();
            if (removedExpired > 0) {
                LOGGER.info("Removed {} expired messages from DLQ", removedExpired);
                // Expired 메시지 제거 후 다시 시도
                offered = dlq.offer(failedMessage);
            }

            // 2. 여전히 실패하면 flush 시도
            if (!offered) {
                droppedDueToCapacity.incrementAndGet();
                LOGGER.warn("DLQ still full after cleanup, attempting flush. Total dropped: {}", droppedDueToCapacity.get());
                flush();
                // Flush 후 다시 시도
                offered = dlq.offer(failedMessage);
            }
        }

        if (offered) {
            LOGGER.debug("Added message to DLQ: {}", failedMessage);
        } else {
            LOGGER.error("Failed to add message to DLQ even after cleanup and flush");
        }

        return offered;
    }

    /**
     * 보관 시간이 지난 메시지들을 제거합니다.
     *
     * @return 제거된 메시지 수
     */
    public int removeExpiredMessages() {
        long currentTime = System.currentTimeMillis();
        long expiryThreshold = currentTime - messageRetentionMs;

        List<FailedMessage> toRemove = new ArrayList<>();

        // Expired 메시지 찾기 (큐를 순회하면서)
        for (FailedMessage message : dlq) {
            if (message.getTimestamp() < expiryThreshold) {
                toRemove.add(message);
            }
        }

        // Expired 메시지 제거
        int removedCount = 0;
        for (FailedMessage message : toRemove) {
            if (dlq.remove(message)) {
                removedCount++;
                expiredMessages.incrementAndGet();
                LOGGER.debug("Removed expired message from DLQ: age={}ms, message={}",
                        currentTime - message.getTimestamp(), message);
            }
        }

        if (removedCount > 0) {
            LOGGER.info("Removed {} expired messages from DLQ (retention: {}h)",
                    removedCount, messageRetentionMs / (60 * 60 * 1000));
        }

        return removedCount;
    }

    /**
     * DLQ의 모든 메시지를 파일에 저장합니다.
     * 원자적 파일 쓰기를 사용하여 부분 쓰기 실패를 방지합니다.
     *
     * @return 저장된 메시지 수
     */
    public int flush() {
        if (dlq.isEmpty()) {
            LOGGER.debug("DLQ is empty, nothing to flush");
            return 0;
        }

        List<FailedMessage> messages = new ArrayList<>();
        dlq.drainTo(messages);

        if (messages.isEmpty()) {
            return 0;
        }

        String filename = generateFilename();
        Path filePath = Paths.get(outputDirectory, filename);
        Path tempFilePath = Paths.get(outputDirectory, filename + ".tmp");

        try {
            // 1. 임시 파일에 먼저 작성
            int written = writeToFile(tempFilePath, messages);

            // 2. 원자적으로 최종 파일로 이동
            Files.move(tempFilePath, filePath, StandardCopyOption.ATOMIC_MOVE);

            totalFlushedMessages.addAndGet(written);
            LOGGER.info("Flushed {} messages to DLQ file: {}", written, filePath);
            return written;

        } catch (IOException e) {
            LOGGER.error("Failed to flush DLQ to file: {}", filePath, e);

            // 임시 파일 정리
            try {
                if (Files.exists(tempFilePath)) {
                    Files.delete(tempFilePath);
                    LOGGER.debug("Cleaned up temporary file: {}", tempFilePath);
                }
            } catch (IOException cleanupEx) {
                LOGGER.warn("Failed to cleanup temporary file: {}", tempFilePath, cleanupEx);
            }

            // 실패한 경우 메시지를 다시 큐에 넣기 (블로킹 방식으로 안전하게)
            int requeuedCount = 0;
            for (FailedMessage msg : messages) {
                try {
                    dlq.put(msg);
                    requeuedCount++;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    int lostCount = messages.size() - requeuedCount;
                    LOGGER.error("Interrupted while re-queuing failed messages. {} messages re-queued, {} messages LOST",
                            requeuedCount, lostCount, ie);
                    // 나머지 메시지를 로그에 기록
                    for (int i = requeuedCount; i < messages.size(); i++) {
                        FailedMessage lostMsg = messages.get(i);
                        LOGGER.error("LOST MESSAGE: messageType={}, retryCount={}, error={}, originalMessage={}",
                                lostMsg.getMessageType(), lostMsg.getRetryCount(), lostMsg.getErrorReason(),
                                lostMsg.getOriginalMessage().length() > 200 ?
                                    lostMsg.getOriginalMessage().substring(0, 200) + "..." : lostMsg.getOriginalMessage());
                    }
                    break;
                }
            }
            return 0;
        }
    }

    private String generateFilename() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String extension = outputFormat == OutputFormat.JSON ? "json" : "csv";
        return String.format("dlq_%s.%s", timestamp, extension);
    }

    private int writeToFile(Path filePath, List<FailedMessage> messages) throws IOException {
        // CSV 헤더 처리: 파일이 존재하지 않을 때만 헤더 작성
        boolean needsHeader = outputFormat == OutputFormat.CSV && !Files.exists(filePath);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            // CSV 형식이면 헤더 추가 (파일이 새로 생성된 경우)
            if (needsHeader) {
                writer.write(FailedMessage.getCsvHeader());
                writer.newLine();
            }

            for (FailedMessage message : messages) {
                String line = outputFormat == OutputFormat.JSON
                        ? message.toJson()
                        : message.toCsv();
                writer.write(line);
                writer.newLine();
            }

            writer.flush();
            return messages.size();
        }
    }

    /**
     * DLQ의 현재 크기를 반환합니다.
     *
     * @return 큐에 있는 메시지 수
     */
    public int size() {
        return dlq.size();
    }

    /**
     * DLQ가 비어있는지 확인합니다.
     *
     * @return 비어있으면 true
     */
    public boolean isEmpty() {
        return dlq.isEmpty();
    }

    /**
     * DLQ의 최대 크기를 반환합니다.
     *
     * @return 최대 크기
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * 총 실패한 메시지 수를 반환합니다.
     *
     * @return 총 실패 메시지 수
     */
    public long getTotalFailedMessages() {
        return totalFailedMessages.get();
    }

    /**
     * 파일에 저장된 총 메시지 수를 반환합니다.
     *
     * @return 총 flush된 메시지 수
     */
    public long getTotalFlushedMessages() {
        return totalFlushedMessages.get();
    }

    /**
     * 용량 초과로 드롭된 메시지 수를 반환합니다.
     *
     * @return 드롭된 메시지 수
     */
    public long getDroppedDueToCapacity() {
        return droppedDueToCapacity.get();
    }

    /**
     * 만료되어 제거된 메시지 수를 반환합니다.
     *
     * @return 만료된 메시지 수
     */
    public long getExpiredMessages() {
        return expiredMessages.get();
    }

    /**
     * DLQ의 사용률을 백분율로 반환합니다.
     *
     * @return 사용률 (0.0 ~ 100.0)
     */
    public double getUtilization() {
        return (double) size() / maxSize * 100.0;
    }

    /**
     * DLQ를 비웁니다.
     */
    public void clear() {
        dlq.clear();
        LOGGER.info("DLQ cleared");
    }

    /**
     * DLQ의 통계 정보를 문자열로 반환합니다.
     *
     * @return 통계 정보 문자열
     */
    public String getStats() {
        return String.format(
            "DeadLetterQueue{size=%d/%d, utilization=%.1f%%, totalFailed=%d, totalFlushed=%d, dropped=%d, expired=%d}",
            size(), maxSize, getUtilization(),
            totalFailedMessages.get(), totalFlushedMessages.get(), droppedDueToCapacity.get(), expiredMessages.get()
        );
    }

    @Override
    public String toString() {
        return getStats();
    }
}
