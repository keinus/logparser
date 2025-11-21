package org.keinus.logparser.domain.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Dead Letter Queue에 저장되는 실패한 메시지를 나타내는 클래스입니다.
 * <p>
 * 이 클래스는 파싱이나 전송에 반복적으로 실패한 메시지의 정보를 담고 있으며,
 * 추후 분석이나 재처리를 위해 보관됩니다.
 */
public class FailedMessage {
    private final String originalMessage;
    private final String errorReason;
    private final int retryCount;
    private final long timestamp;
    private final String messageType;
    private final String sourceHost;

    /**
     * FailedMessage를 생성합니다.
     *
     * @param originalMessage 원본 메시지 내용
     * @param errorReason 실패 사유
     * @param retryCount 재시도 횟수
     * @param timestamp 실패 시각 (밀리초)
     */
    public FailedMessage(String originalMessage, String errorReason, int retryCount, long timestamp) {
        this(originalMessage, errorReason, retryCount, timestamp, "unknown", "unknown");
    }

    /**
     * FailedMessage를 생성합니다 (상세 정보 포함).
     *
     * @param originalMessage 원본 메시지 내용
     * @param errorReason 실패 사유
     * @param retryCount 재시도 횟수
     * @param timestamp 실패 시각 (밀리초)
     * @param messageType 메시지 타입
     * @param sourceHost 소스 호스트
     */
    public FailedMessage(String originalMessage, String errorReason, int retryCount,
                        long timestamp, String messageType, String sourceHost) {
        this.originalMessage = originalMessage;
        this.errorReason = errorReason;
        this.retryCount = retryCount;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.sourceHost = sourceHost;
    }

    /**
     * LogEvent로부터 FailedMessage를 생성합니다.
     *
     * @param logEvent 실패한 LogEvent
     * @param retryCount 재시도 횟수
     * @return FailedMessage 인스턴스
     */
    public static FailedMessage fromLogEvent(LogEvent logEvent, int retryCount) {
        return new FailedMessage(
            logEvent.getOriginalText(),
            logEvent.getProcessingError() != null ? logEvent.getProcessingError() : "Unknown error",
            retryCount,
            System.currentTimeMillis(),
            logEvent.getMessageType(),
            logEvent.getSourceHost()
        );
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getSourceHost() {
        return sourceHost;
    }

    /**
     * 타임스탬프를 ISO-8601 형식의 문자열로 반환합니다.
     *
     * @return ISO-8601 형식의 타임스탬프
     */
    public String getFormattedTimestamp() {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * JSON 형식으로 변환합니다.
     *
     * @return JSON 문자열
     */
    public String toJson() {
        return String.format(
            "{\"timestamp\":\"%s\",\"messageType\":\"%s\",\"sourceHost\":\"%s\",\"retryCount\":%d,\"errorReason\":\"%s\",\"originalMessage\":\"%s\"}",
            getFormattedTimestamp(),
            escapeJson(messageType),
            escapeJson(sourceHost),
            retryCount,
            escapeJson(errorReason),
            escapeJson(originalMessage)
        );
    }

    /**
     * CSV 형식으로 변환합니다.
     *
     * @return CSV 문자열
     */
    public String toCsv() {
        return String.format(
            "%s,%s,%s,%d,\"%s\",\"%s\"",
            getFormattedTimestamp(),
            escapeCsv(messageType),
            escapeCsv(sourceHost),
            retryCount,
            escapeCsv(errorReason),
            escapeCsv(originalMessage)
        );
    }

    /**
     * CSV 헤더를 반환합니다.
     *
     * @return CSV 헤더 문자열
     */
    public static String getCsvHeader() {
        return "timestamp,messageType,sourceHost,retryCount,errorReason,originalMessage";
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        return str.replace("\"", "\"\"");
    }

    @Override
    public String toString() {
        return String.format(
            "FailedMessage{timestamp=%s, type=%s, host=%s, retries=%d, reason=%s, message=%s}",
            getFormattedTimestamp(),
            messageType,
            sourceHost,
            retryCount,
            errorReason,
            originalMessage.length() > 50 ? originalMessage.substring(0, 50) + "..." : originalMessage
        );
    }
}
