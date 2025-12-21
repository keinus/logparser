package org.keinus.logparser.domain.delivery.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.keinus.logparser.infrastructure.util.PatternCache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 처리된 메시지를 OpenSearch 또는 Elasticsearch 클러스터로 전송하는 출력 어댑터입니다.
 * <p>
 * 이 클래스는 {@link OutputAdapter}를 구현하며, 높은 처리량을 위해 OpenSearch의
 * 벌크(Bulk) API를 사용합니다. 메시지들은 내부 버퍼에 수집되었다가, 버퍼가 가득 차거나
 * 1초 이상 send()가 호출되지 않으면 자동으로 flush됩니다.
 * <p>
 * 주요 기능:
 * <ul>
 *     <li><b>벌크 인덱싱:</b> 여러 문서를 하나의 HTTP 요청으로 묶어 전송하여 네트워크 오버헤드를 최소화합니다.</li>
 *     <li><b>배치 처리:</b> MAX_BATCH_SIZE 문서가 쌓이거나, 1초 동안 send() 미호출 시 자동 flush합니다.</li>
 *     <li><b>동적 인덱스 이름:</b> 인덱스 이름 템플릿에 {@code %{fieldname}} 또는 날짜 형식을 사용합니다.</li>
 *     <li><b>HTTPS 및 인증 지원:</b> SSL/TLS 및 기본 인증(username/password)을 지원합니다.</li>
 *     <li><b>자동 재시도:</b> HTTP 요청 실패 시 최대 3회 재시도합니다.</li>
 *     <li><b>부분 실패 처리:</b> 벌크 응답을 파싱하여 실패한 항목만 다음 인덱싱 시 재시도합니다.</li>
 * </ul>
 */
public class OpenSearchOutputAdapter extends OutputAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchOutputAdapter.class);
    private static final int MAX_BATCH_SIZE = 2000;
    private static final int MAX_RETRIES = 3;
    private static final int MAX_TOTAL_ITEMS = 50000;
    private static final long FLUSH_INTERVAL_MS = 1000; // 1초

    private static final PatternCache PATTERN_CACHE = PatternCache.getInstance();
    private static final Pattern BRACED_STRING_PATTERN = PATTERN_CACHE.compile("%\\{(.*?)}");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 재시도 가능한 항목을 추적하는 내부 클래스
     */
    private static class RetryableItem {
        final String data;
        int retryCount;

        RetryableItem(String data) {
            this.data = data;
            this.retryCount = 0;
        }

        boolean shouldRetry() {
            return retryCount < MAX_RETRIES;
        }

        void incrementRetry() {
            retryCount++;
        }
    }

    private final String baseUrl;
    private final String indexTemplate;
    private String credentials = null;
    private List<String> indexVars = null;

    private final ConcurrentHashMap<String, List<RetryableItem>> dataMap = new ConcurrentHashMap<>();
    private final AtomicInteger totalDocumentCount = new AtomicInteger(0);
    private final AtomicInteger deadLetterCount = new AtomicInteger(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong lastSendTime = new AtomicLong(System.currentTimeMillis());

    private CloseableHttpClient httpClient;
    private PoolingHttpClientConnectionManager connectionManager;

    // 내부 flush 타이머
    private final ScheduledExecutorService flushScheduler;
    private ScheduledFuture<?> flushTask;
    private final Object flushLock = new Object();

    // 처리량 측정
    private long lastFlushTime = System.currentTimeMillis();

    // 인덱스 헤더 캐싱
    private static final ConcurrentHashMap<String, String> indexHeaderCache = new ConcurrentHashMap<>();
    private static final int AVG_DOCUMENT_SIZE = 1024;
    private static final int HEADER_SIZE = 100;

    public OpenSearchOutputAdapter(Map<String, String> obj) throws IOException {
        super(obj);

        baseUrl = Objects.requireNonNull(obj.get("url"), "OpenSearch 'url' must not be null");
        indexTemplate = Objects.requireNonNull(obj.get("index"), "OpenSearch 'index' must not be null");
        indexVars = extractBracedStrings(indexTemplate);

        String username = obj.get("username");
        String password = obj.get("password");
        if (username != null && !username.isEmpty()) {
            credentials = username + ":" + password;
        }

        LOGGER.info("OpenSearch Output Adapter Init. {}", baseUrl);

        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, TrustSelfSignedStrategy.INSTANCE)
                    .build();

            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    NoopHostnameVerifier.INSTANCE
            );

            this.connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .setMaxConnTotal(100)
                    .setMaxConnPerRoute(50)
                    .build();

            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                    .setResponseTimeout(Timeout.ofSeconds(60))
                    .build();

            this.httpClient = HttpClients.custom()
                    .setConnectionManager(this.connectionManager)
                    .setDefaultRequestConfig(requestConfig)
                    .build();

            LOGGER.info("HTTP client initialized with connection pool (max: 100, per-route: 50)");

        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new IOException("Failed to initialize HTTP client for OpenSearch", e);
        }

        // Flush 타이머 초기화
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OpenSearch-FlushTimer");
            t.setDaemon(true);
            return t;
        });
        scheduleFlushCheck();

        LOGGER.info("OpenSearch Output Adapter initialized with auto-flush timer ({}ms)", FLUSH_INTERVAL_MS);
    }

    /**
     * 주기적으로 flush 조건을 확인하는 타이머를 스케줄링합니다.
     */
    private void scheduleFlushCheck() {
        flushTask = flushScheduler.scheduleAtFixedRate(() -> {
            try {
                long elapsed = System.currentTimeMillis() - lastSendTime.get();
                if (elapsed >= FLUSH_INTERVAL_MS && totalDocumentCount.get() > 0) {
                    LOGGER.debug("Auto-flush triggered after {}ms of inactivity", elapsed);
                    flush();
                }
            } catch (Exception e) {
                LOGGER.error("Error in flush timer: {}", e.getMessage(), e);
            }
        }, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS / 2, TimeUnit.MILLISECONDS);
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
     */
    private void sendToDeadLetterQueue(String index, RetryableItem item) {
        deadLetterCount.incrementAndGet();
        LOGGER.error("Item sent to Dead Letter Queue [index: {}, retryCount: {}]: {}",
                index,
                item.retryCount,
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
        if (totalDocumentCount.get() >= MAX_TOTAL_ITEMS) {
            dropOldestItems();
        }

        RetryableItem item = new RetryableItem(jsonString);
        dataMap.computeIfAbsent(index, k -> Collections.synchronizedList(new ArrayList<>())).add(item);
        totalDocumentCount.incrementAndGet();
    }

    private void addRetryableItem(String index, RetryableItem item) {
        dataMap.computeIfAbsent(index, k -> Collections.synchronizedList(new ArrayList<>())).add(item);
        totalDocumentCount.incrementAndGet();
    }

    @Override
    public void send(Map<String, Object> json, String jsonString) {
        if (closed.get()) {
            LOGGER.warn("Adapter is closed, ignoring send request");
            return;
        }

        lastSendTime.set(System.currentTimeMillis());

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
            flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            LOGGER.debug("OpenSearch Output Adapter already closed, skipping");
            return;
        }

        LOGGER.info("Closing OpenSearch Output Adapter and flushing remaining data.");

        // Flush 타이머 중지
        if (flushTask != null) {
            flushTask.cancel(false);
        }
        flushScheduler.shutdown();
        try {
            if (!flushScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                flushScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            flushScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 마지막 flush
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

        LOGGER.info("OpenSearch Output Adapter closed. DLQ count: {}", deadLetterCount.get());
    }

    private static StringBuilder formatBulkRequestForIndex(String index, List<RetryableItem> list) {
        int estimatedSize = list.size() * (AVG_DOCUMENT_SIZE + HEADER_SIZE);
        StringBuilder sb = new StringBuilder(estimatedSize);

        String indexHeader = indexHeaderCache.computeIfAbsent(index,
                idx -> "{ \"index\": { \"_index\": \"" + idx + "\" } }\n");

        for (RetryableItem item : list) {
            sb.append(indexHeader);
            sb.append(item.data).append("\n");
        }
        return sb;
    }

    public void flush() {
        synchronized (flushLock) {
            if (Thread.currentThread().isInterrupted()) {
                LOGGER.warn("Flush interrupted, moving {} pending items to DLQ", totalDocumentCount.get());
                handleInterrupt();
                return;
            }

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

            long currentTime = System.currentTimeMillis();
            long elapsedTimeMs = currentTime - lastFlushTime;
            double elapsedTimeSec = elapsedTimeMs / 1000.0;

            int successCount = 0;
            int failedCount = 0;

            for (Map.Entry<String, List<RetryableItem>> entry : itemsToFlush.entrySet()) {
                String indexTarget = entry.getKey();
                List<RetryableItem> documents = entry.getValue();

                String url = baseUrl + "/" + indexTarget + "/_bulk";
                String body = formatBulkRequestForIndex(indexTarget, documents).toString();

                try {
                    BulkResult result = sendRestWithRetry(url, body, documents, indexTarget);
                    successCount += result.successCount;
                    failedCount += result.failedCount;

                    if (elapsedTimeSec > 0) {
                        double throughput = result.successCount / elapsedTimeSec;
                        LOGGER.info("{} items indexed for '{}' (throughput: {} docs/sec)",
                                result.successCount, indexTarget, String.format("%.2f", throughput));
                    }

                } catch (IOException e) {
                    LOGGER.error("Failed to send data for index '{}' after {} retries: {}",
                            indexTarget, MAX_RETRIES, e.getMessage());

                    // 모든 항목 재시도 또는 DLQ로 이동
                    for (RetryableItem item : documents) {
                        item.incrementRetry();
                        if (item.shouldRetry()) {
                            addRetryableItem(indexTarget, item);
                            failedCount++;
                        } else {
                            sendToDeadLetterQueue(indexTarget, item);
                        }
                    }
                }
            }

            lastFlushTime = currentTime;

            if (successCount > 0 || failedCount > 0) {
                LOGGER.info("Flush completed - success: {}, retry queued: {}, total DLQ: {}",
                        successCount, failedCount, deadLetterCount.get());
            }
        }
    }

    private void handleInterrupt() {
        synchronized (dataMap) {
            for (Map.Entry<String, List<RetryableItem>> entry : dataMap.entrySet()) {
                String indexTarget = entry.getKey();
                for (RetryableItem item : entry.getValue()) {
                    sendToDeadLetterQueue(indexTarget, item);
                }
            }
            int movedCount = totalDocumentCount.get();
            dataMap.clear();
            totalDocumentCount.set(0);
            LOGGER.info("Moved {} items to DLQ due to interrupt", movedCount);
        }
        Thread.currentThread().interrupt();
    }

    /**
     * Bulk 인덱싱 결과
     */
    private static class BulkResult {
        int successCount;
        int failedCount;

        BulkResult(int successCount, int failedCount) {
            this.successCount = successCount;
            this.failedCount = failedCount;
        }
    }

    /**
     * REST 요청을 최대 3회 재시도하며 전송합니다.
     * 부분 실패 시 실패한 항목만 다음 인덱싱에서 재시도합니다.
     */
    private BulkResult sendRestWithRetry(String url, String body, List<RetryableItem> documents, String indexTarget) throws IOException {
        IOException lastException = new IOException("No retry attempts made");

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String responseBody = sendRest(url, body);
                return parseBulkResponse(responseBody, documents, indexTarget);
            } catch (IOException e) {
                lastException = e;
                LOGGER.warn("Attempt {}/{} failed for index '{}': {}", attempt, MAX_RETRIES, indexTarget, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    try {
                        long backoffMs = (long) Math.pow(2, attempt) * 1000; // 지수 백오프: 2초, 4초
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry interrupted", ie);
                    }
                }
            }
        }

        throw lastException;
    }

    /**
     * Bulk 응답을 파싱하여 실패한 항목을 재시도 큐에 추가합니다.
     */
    private BulkResult parseBulkResponse(String responseBody, List<RetryableItem> documents, String indexTarget) {
        int successCount = 0;
        int failedCount = 0;

        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);

            if (!root.has("errors") || !root.get("errors").asBoolean()) {
                // 모든 항목 성공
                return new BulkResult(documents.size(), 0);
            }

            JsonNode items = root.get("items");
            if (items == null || !items.isArray()) {
                LOGGER.warn("Invalid bulk response format, assuming all items succeeded");
                return new BulkResult(documents.size(), 0);
            }

            for (int i = 0; i < items.size() && i < documents.size(); i++) {
                JsonNode itemResult = items.get(i);
                JsonNode indexResult = itemResult.get("index");

                if (indexResult == null) {
                    indexResult = itemResult.get("create");
                }

                if (indexResult != null) {
                    int status = indexResult.has("status") ? indexResult.get("status").asInt() : 200;

                    if (status >= 200 && status < 300) {
                        successCount++;
                    } else {
                        // 실패한 항목
                        RetryableItem failedItem = documents.get(i);
                        failedItem.incrementRetry();

                        String errorReason = "unknown";
                        if (indexResult.has("error") && indexResult.get("error").has("reason")) {
                            errorReason = indexResult.get("error").get("reason").asText();
                        }

                        if (failedItem.shouldRetry()) {
                            // 다음 인덱싱 시 재시도
                            addRetryableItem(indexTarget, failedItem);
                            failedCount++;
                            LOGGER.debug("Item {} failed (status: {}, reason: {}), will retry (attempt {}/{})",
                                    i, status, errorReason, failedItem.retryCount, MAX_RETRIES);
                        } else {
                            // DLQ로 이동
                            sendToDeadLetterQueue(indexTarget, failedItem);
                            LOGGER.warn("Item {} failed after max retries (status: {}, reason: {}), moved to DLQ",
                                    i, status, errorReason);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse bulk response: {}", e.getMessage());
            // 파싱 실패 시 모든 항목 성공으로 처리
            return new BulkResult(documents.size(), 0);
        }

        return new BulkResult(successCount, failedCount);
    }

    /**
     * REST API를 통해 OpenSearch에 데이터를 전송합니다.
     */
    private String sendRest(String url, String body) throws IOException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

        if (credentials != null) {
            String base64Credentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            httpPost.setHeader("Authorization", "Basic " + base64Credentials);
        }

        return httpClient.execute(httpPost, response -> {
            int statusCode = response.getCode();
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("OpenSearch indexing failed with status " + statusCode + ". Response: " + responseBody);
            }

            LOGGER.debug("Successfully sent data to OpenSearch. Status: {}", statusCode);
            return responseBody;
        });
    }
}
