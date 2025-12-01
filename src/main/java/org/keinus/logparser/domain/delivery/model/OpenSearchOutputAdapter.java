package org.keinus.logparser.domain.delivery.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.keinus.logparser.application.service.BatchingOutputService;
import org.keinus.logparser.domain.delivery.model.OutputAdapter;
import org.keinus.logparser.infrastructure.util.PatternCache;
import org.keinus.logparser.infrastructure.util.ThreadUtil;

/**
 * 처리된 메시지를 OpenSearch 또는 Elasticsearch 클러스터로 전송하는 출력 어댑터입니다.
 * <p>
 * 이 클래스는 {@link OutputAdapter}를 구현하며, 높은 처리량을 위해 OpenSearch의
 * 벌크(Bulk) API를 사용합니다. 메시지들은 내부 버퍼에 수집되었다가, 버퍼가 가득 차거나
 * 주기적인 스케줄러에 의해 일괄적으로 전송됩니다.
 * <p>
 * 주요 기능:
 * <ul>
 *     <li><b>벌크 인덱싱:</b> 여러 문서를 하나의 HTTP 요청으로 묶어 전송하여 네트워크 오버헤드를 최소화합니다.</li>
 *     <li><b>배치 처리:</b> 2000개의 문서가 쌓이거나, 10초의 시간이 경과하면 자동으로 flush하여 데이터를 전송합니다.</li>
 *     <li><b>동적 인덱스 이름:</b> 인덱스 이름 템플릿에 {@code %{fieldname}} 또는 날짜 형식(예: {@code yyyy.MM.dd})을
 *         사용하여 메시지 내용이나 시간에 따라 동적으로 인덱스 이름을 결정할 수 있습니다.</li>
 *     <li><b>HTTPS 및 인증 지원:</b> SSL/TLS 및 기본 인증(username/password)을 지원합니다.</li>
 *     <li><b>신뢰할 수 있는 전송:</b> 데이터 전송 실패 시, 실패한 항목들을 다시 큐에 넣어 재전송을 시도합니다.</li>
 * </ul>
 *
 * @see org.keinus.logparser.core.interfaces.OutputAdapter
 * @see org.apache.http.impl.client.CloseableHttpClient
 */
public class OpenSearchOutputAdapter extends OutputAdapter implements BatchingOutputService.BatchableOutputAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchOutputAdapter.class);
    private static final int MAX_BATCH_SIZE = 10000;
    private static final int MAX_RETRIES = 3;
    private static final int MAX_TOTAL_ITEMS = 50000;
    private static final int MAX_ITEM_AGE_MS = 300000; // 5분

    // Pattern 캐싱을 위한 인스턴스
    private static final PatternCache PATTERN_CACHE = PatternCache.getInstance();
    private static final Pattern BRACED_STRING_PATTERN = PATTERN_CACHE.compile("%\\{(.*?)}");

    /**
     * 재시도 가능한 항목을 추적하는 내부 클래스
     */
    private static class RetryableItem {
        final String data;
        int retryCount;
        final long firstAttemptTime;

        RetryableItem(String data) {
            this.data = data;
            this.retryCount = 0;
            this.firstAttemptTime = System.currentTimeMillis();
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - firstAttemptTime) > MAX_ITEM_AGE_MS;
        }

        boolean shouldRetry() {
            return retryCount < MAX_RETRIES && !isExpired();
        }

        void incrementRetry() {
            retryCount++;
        }
    }

    private String baseUrl;
    private String indexTemplate;
    private String credentials = null;
    private List<String> indexVars = null;

    private final ConcurrentHashMap<String, List<RetryableItem>> dataMap = new ConcurrentHashMap<>();
    private final AtomicInteger totalDocumentCount = new AtomicInteger(0);
    private final AtomicInteger deadLetterCount = new AtomicInteger(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private CloseableHttpClient httpClient;
    private PoolingHttpClientConnectionManager connectionManager;

    // 처리량 측정을 위한 변수들
    private long lastFlushTime = System.currentTimeMillis();

    // 어댑터 고유 식별자
    private String adapterId;

    public OpenSearchOutputAdapter(Map<String, String> obj) throws IOException {
        super(obj);

        baseUrl = Objects.requireNonNull(obj.get("url"), "OpenSearch 'url' must not be null");
        indexTemplate = Objects.requireNonNull(obj.get("index"), "OpenSearch 'index' must not be null");
        indexVars = extractBracedStrings(indexTemplate);

        // 어댑터 고유 식별자 생성 (URL + 인덱스 템플릿 기반)
        this.adapterId = "OpenSearch-" + baseUrl.hashCode() + "-" + indexTemplate.hashCode();

        String username = obj.get("username");
        String password = obj.get("password");
        if (username == null || username.isEmpty()) {
            credentials = null;
        } else {
            credentials = username + ":" + password;
        }

        LOGGER.info("OpenSearch Output Adapter Init. {} (adapterId: {})", baseUrl, adapterId);

        try {
            SSLConnectionSocketFactory scsf = new SSLConnectionSocketFactory(
                    SSLContexts.custom().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build(),
                    NoopHostnameVerifier.INSTANCE);

            // Connection pool 설정
            this.connectionManager = new PoolingHttpClientConnectionManager();
            this.connectionManager.setDefaultMaxPerRoute(50); // 라우트당 최대 연결 수
            this.connectionManager.setMaxTotal(100); // 전체 최대 연결 수
            this.connectionManager.closeIdleConnections(30, TimeUnit.SECONDS); // 30초 유휴 연결 정리

            this.httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setSSLSocketFactory(scsf)
                    .build();

            LOGGER.info("HTTP client initialized with connection pool (max: 100, per-route: 50)");

        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new IOException("Failed to initialize HTTP client for OpenSearch", e);
        }

        LOGGER.info("OpenSearch Output Adapter initialized. Flush will be managed by BatchingOutputService.");
    }

    @Override
    public String getAdapterId() {
        return adapterId;
    }

    private List<String> extractBracedStrings(String input) {
        List<String> extractedStrings = new ArrayList<>();
        Matcher matcher = BRACED_STRING_PATTERN.matcher(input);

        while (matcher.find()) {
            extractedStrings.add(matcher.group(1));
        }
        return extractedStrings;
    }

    /**
     * Dead Letter Queue에 메시지를 저장합니다.
     * 재시도 횟수를 초과하거나 만료된 항목들을 로깅하여 추후 분석할 수 있도록 합니다.
     */
    private void sendToDeadLetterQueue(String index, RetryableItem item) {
        deadLetterCount.incrementAndGet();
        LOGGER.error("Item sent to Dead Letter Queue [index: {}, retryCount: {}, age: {}ms]: {}",
                index,
                item.retryCount,
                System.currentTimeMillis() - item.firstAttemptTime,
                item.data.length() > 200 ? item.data.substring(0, 200) + "..." : item.data);
    }

    /**
     * 큐가 최대 크기를 초과할 때 가장 오래된 항목들을 제거합니다.
     */
    private void dropOldestItems() {
        int itemsToRemove = totalDocumentCount.get() - MAX_TOTAL_ITEMS + MAX_BATCH_SIZE;
        if (itemsToRemove <= 0) {
            return;
        }

        LOGGER.warn("Queue overflow detected. Current size: {}, max: {}. Dropping oldest {} items.",
                totalDocumentCount.get(), MAX_TOTAL_ITEMS, itemsToRemove);

        synchronized (dataMap) {
            int removed = 0;
            for (Map.Entry<String, List<RetryableItem>> entry : dataMap.entrySet()) {
                List<RetryableItem> items = entry.getValue();
                while (!items.isEmpty() && removed < itemsToRemove) {
                    RetryableItem removedItem = items.remove(0);
                    sendToDeadLetterQueue(entry.getKey(), removedItem);
                    removed++;
                    totalDocumentCount.decrementAndGet();
                }
                if (removed >= itemsToRemove) {
                    break;
                }
            }
        }
    }

    private void addJsonString(String index, String jsonString) {
        // 큐 크기 제한 확인
        if (totalDocumentCount.get() >= MAX_TOTAL_ITEMS) {
            dropOldestItems();
        }

        RetryableItem item = new RetryableItem(jsonString);
        dataMap.computeIfAbsent(index, k -> Collections.synchronizedList(new ArrayList<>())).add(item);
        totalDocumentCount.incrementAndGet();
    }

    @Override
    public void send(Map<String, Object> json, String jsonString) {
        String targetIndex = indexTemplate;

        for (var variable : indexVars) {
            if (variable.startsWith("yy")) {
                String time = new SimpleDateFormat(variable).format(new Date());
                if (time != null && !time.isEmpty())
                    targetIndex = targetIndex.replace("%{" + variable + "}", time);
            } else {
                var value = json.get(variable);
                if (value != null)
                    targetIndex = targetIndex.replace("%{" + variable + "}", value.toString());
            }
        }
        addJsonString(targetIndex, jsonString);

        if (totalDocumentCount.get() >= MAX_BATCH_SIZE) {
            this.flush();
        }
    }

    @Override
    public void close() throws IOException {
        // 멱등성 보장: 이미 닫혔으면 즉시 리턴
        if (!closed.compareAndSet(false, true)) {
            LOGGER.debug("OpenSearch Output Adapter already closed, skipping");
            return;
        }

        LOGGER.info("Closing OpenSearch Output Adapter (adapterId: {}) and flushing remaining data.", adapterId);

        // 마지막 flush 수행
        flush();
        dataMap.clear();
        totalDocumentCount.set(0);

        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                LOGGER.error("Error closing HTTP client: {}", e.getMessage(), e);
            }
        }

        if (connectionManager != null) {
            try {
                connectionManager.close();
                LOGGER.info("Connection manager closed");
            } catch (Exception e) {
                LOGGER.error("Error closing connection manager: {}", e.getMessage(), e);
            }
        }

        LOGGER.info("OpenSearch Output Adapter closed (adapterId: {}). DLQ count: {}", adapterId, deadLetterCount.get());
    }

    // 반복적으로 생성되는 인덱스 헤더 문자열 캐싱
    private static final ConcurrentHashMap<String, String> indexHeaderCache = new ConcurrentHashMap<>();
    private static final int AVG_DOCUMENT_SIZE = 1024;  // 평균 문서 크기 (바이트)
    private static final int HEADER_SIZE = 100;  // 헤더 라인 크기 추정

    private static StringBuilder formatBulkRequestForIndex(String index, List<RetryableItem> list) {
        // StringBuilder 초기 크기를 미리 할당하여 재할당 방지
        int estimatedSize = list.size() * (AVG_DOCUMENT_SIZE + HEADER_SIZE);
        StringBuilder sb = new StringBuilder(estimatedSize);

        // 인덱스 헤더 문자열 캐싱
        String indexHeader = indexHeaderCache.computeIfAbsent(index,
            idx -> "{ \"index\": { \"_index\": \"" + idx + "\" } }\n");

        for (RetryableItem item : list) {
            sb.append(indexHeader);
            sb.append(item.data).append("\n");
        }
        return sb;
    }

    public void flush() {
        // Interrupt 체크 - 인터럽트 시 큐의 데이터를 DLQ로 이동
        if (Thread.currentThread().isInterrupted()) {
            LOGGER.warn("Flush interrupted, moving {} pending items to DLQ", totalDocumentCount.get());

            synchronized (dataMap) {
                for (Map.Entry<String, List<RetryableItem>> entry : dataMap.entrySet()) {
                    String indexTarget = entry.getKey();
                    for (RetryableItem item : entry.getValue()) {
                        sendToDeadLetterQueue(indexTarget, item);
                        LOGGER.debug("Item moved to DLQ due to interrupt [index: {}]", indexTarget);
                    }
                }
                int movedCount = totalDocumentCount.get();
                dataMap.clear();
                totalDocumentCount.set(0);
                LOGGER.info("Moved {} items to DLQ due to interrupt", movedCount);
            }
            Thread.currentThread().interrupt();  // 인터럽트 상태 복원
            return;
        }

        Map<String, List<RetryableItem>> failedItems = new ConcurrentHashMap<>();

        ConcurrentHashMap<String, List<RetryableItem>> itemsToFlush = new ConcurrentHashMap<>();
        synchronized (dataMap) {
            itemsToFlush.putAll(dataMap);
            dataMap.clear();
            totalDocumentCount.set(0);
        }

        if (itemsToFlush.isEmpty()) {
            LOGGER.debug("No items to flush.");
            return;
        }

        // 처리량 계산을 위한 시간 측정
        long currentTime = System.currentTimeMillis();
        long elapsedTimeMs = currentTime - lastFlushTime;
        double elapsedTimeSec = elapsedTimeMs / 1000.0;

        int successCount = 0;
        int failedCount = 0;
        int expiredCount = 0;
        int retriesExceededCount = 0;

        for (Map.Entry<String, List<RetryableItem>> entry : itemsToFlush.entrySet()) {
            String indexTarget = entry.getKey();
            List<RetryableItem> documents = entry.getValue();
            int count = documents.size();

            String url = baseUrl + "/" + indexTarget + "/_bulk";
            String body = formatBulkRequestForIndex(indexTarget, documents).toString();

            try {
                sendRest(url, body);

                // 초당 처리량 계산
                double throughput = elapsedTimeSec > 0 ? count / elapsedTimeSec : 0;

                LOGGER.info("{} items processed for index '{}' (throughput: {} docs/sec)",
                    count, indexTarget, String.format("%.2f", throughput));

                successCount += count;
            } catch (IOException e) {
                LOGGER.error("Failed to send data for index '{}'. Will retry later. Error: {}", indexTarget,
                        e.getMessage());

                // 실패한 항목들의 재시도 가능 여부 확인
                List<RetryableItem> itemsToRetry = new ArrayList<>();
                for (RetryableItem item : documents) {
                    item.incrementRetry();

                    if (item.isExpired()) {
                        expiredCount++;
                        sendToDeadLetterQueue(indexTarget, item);
                        LOGGER.warn("Item expired after {}ms, moved to DLQ [index: {}, retryCount: {}]",
                                System.currentTimeMillis() - item.firstAttemptTime, indexTarget, item.retryCount);
                    } else if (!item.shouldRetry()) {
                        retriesExceededCount++;
                        sendToDeadLetterQueue(indexTarget, item);
                        LOGGER.warn("Item exceeded max retries ({}), moved to DLQ [index: {}]",
                                MAX_RETRIES, indexTarget);
                    } else {
                        itemsToRetry.add(item);
                    }
                }

                if (!itemsToRetry.isEmpty()) {
                    failedItems.computeIfAbsent(indexTarget, k -> Collections.synchronizedList(new ArrayList<>()))
                            .addAll(itemsToRetry);
                    failedCount += itemsToRetry.size();
                }

                ThreadUtil.sleep(5000);
            }
        }

        // 다음 flush를 위해 시간 업데이트
        lastFlushTime = currentTime;

        // 재시도 가능한 실패 항목만 다시 큐에 추가
        if (!failedItems.isEmpty()) {
            synchronized (dataMap) {
                for (Map.Entry<String, List<RetryableItem>> entry : failedItems.entrySet()) {
                    dataMap.computeIfAbsent(entry.getKey(), k -> Collections.synchronizedList(new ArrayList<>()))
                            .addAll(entry.getValue());
                    totalDocumentCount.addAndGet(entry.getValue().size());
                }
            }
            LOGGER.warn("Re-queued {} items for retry. DLQ stats - expired: {}, retries exceeded: {}, total DLQ: {}",
                    failedCount, expiredCount, retriesExceededCount, deadLetterCount.get());
        }

        // 통계 로깅
        if (successCount > 0 || failedCount > 0) {
            LOGGER.info("Flush completed - success: {}, retry: {}, DLQ: {} (expired: {}, max retries: {})",
                    successCount, failedCount, expiredCount + retriesExceededCount, expiredCount, retriesExceededCount);
        }
    }

    public void sendRest(String url, String json) throws IOException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setEntity(new StringEntity(json, StandardCharsets.UTF_8));

        if (credentials != null) {
            String base64Credentials = Base64.encodeBase64String(credentials.getBytes(StandardCharsets.UTF_8));
            String authorization = "Basic " + base64Credentials;
            httpPost.setHeader("Authorization", authorization);
        }

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < 200 || statusCode >= 300) {
                String responseBody = new BasicResponseHandler().handleResponse(response);
                throw new IOException(
                        "OpenSearch indexing failed with status " + statusCode + ". Response: " + responseBody);
            } else {
                LOGGER.debug("Successfully sent data to OpenSearch. Status: {}", statusCode);
            }
        }
    }
}
