package org.keinus.logparser.output;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
import org.apache.http.ssl.SSLContexts;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.keinus.logparser.core.interfaces.OutputAdapter;
import org.keinus.logparser.core.util.ThreadUtil;

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
public class OpenSearchOutputAdapter extends OutputAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchOutputAdapter.class);
    private static final int MAX_BATCH_SIZE = 2000;

    private String host;
    private int port;
    private String indexTemplate;
    private String credentials = null;
    private List<String> indexVars = null;

    private final ConcurrentHashMap<String, List<String>> dataMap = new ConcurrentHashMap<>();
    private final AtomicInteger totalDocumentCount = new AtomicInteger(0);

    private CloseableHttpClient httpClient;
    private ScheduledExecutorService scheduler;

    public OpenSearchOutputAdapter(Map<String, String> obj) throws IOException {
        super(obj);

        host = Objects.requireNonNull(obj.get("host"), "OpenSearch 'host' must not be null");
        indexTemplate = Objects.requireNonNull(obj.get("index"), "OpenSearch 'index' must not be null"); // Use
                                                                                                         // indexTemplate
        String portStr = Objects.requireNonNull(obj.get("port"), "OpenSearch 'port' must not be null");
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("OpenSearch 'port' must be a valid number: " + portStr, e);
        }

        indexVars = extractBracedStrings(indexTemplate);
        String username = obj.get("username");
        String password = obj.get("password");
        if (username == null || username.isEmpty()) {
            credentials = null;
        } else {
            credentials = username + ":" + password;
        }

        LOGGER.info("OpenSearch Output Adapter Init. {}:{}", host, port);

        try {
            SSLConnectionSocketFactory scsf = new SSLConnectionSocketFactory(
                    SSLContexts.custom().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build(),
                    NoopHostnameVerifier.INSTANCE);
            this.httpClient = HttpClients.custom().setSSLSocketFactory(scsf).build();

        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            LOGGER.error("Failed to initialize HTTP client for OpenSearch: {}", e.getMessage(), e);
            throw new IOException("Failed to initialize HTTP client for OpenSearch", e);
        }

        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduler.scheduleAtFixedRate(this::flush, 10, 10, TimeUnit.SECONDS);
        LOGGER.info("OpenSearch Output Adapter scheduled to flush every 10 seconds.");
    }

    private List<String> extractBracedStrings(String input) {
        List<String> extractedStrings = new ArrayList<>();
        Pattern pattern = Pattern.compile("%\\{(.*?)}");
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            extractedStrings.add(matcher.group(1));
        }
        return extractedStrings;
    }

    private void addJsonString(String index, String jsonString) {
        dataMap.computeIfAbsent(index, k -> Collections.synchronizedList(new ArrayList<>())).add(jsonString);
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
        LOGGER.info("Closing OpenSearch Output Adapter and flushing remaining data.");

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.warn("Scheduler did not terminate in 5 seconds, forcing shutdown.");
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                LOGGER.error("Scheduler shutdown interrupted: {}", e.getMessage(), e);
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

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
    }

    private static StringBuilder formatBulkRequestForIndex(String index, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String str : list) {
            sb.append("{ \"index\": { \"_index\": \"").append(index).append("\" } }").append("\n");
            sb.append(str).append("\n");
        }
        return sb;
    }

    public void flush() {
        Map<String, List<String>> failedItems = new ConcurrentHashMap<>();

        ConcurrentHashMap<String, List<String>> itemsToFlush = new ConcurrentHashMap<>();
        synchronized (dataMap) {
            itemsToFlush.putAll(dataMap);
            dataMap.clear();
            totalDocumentCount.set(0);
        }

        if (itemsToFlush.isEmpty()) {
            LOGGER.debug("No items to flush.");
            return;
        }

        for (Map.Entry<String, List<String>> entry : itemsToFlush.entrySet()) {
            String indexTarget = entry.getKey();
            List<String> documents = entry.getValue();
            int count = documents.size();

            String url = "https://" + host + ":" + port + "/" + indexTarget + "/_bulk";
            String body = formatBulkRequestForIndex(indexTarget, documents).toString();

            try {
                sendRest(url, body);
                LOGGER.info("{} items processed for index '{}'", count, indexTarget);
            } catch (IOException e) {
                LOGGER.error("Failed to send data for index '{}'. Will retry later. Error: {}", indexTarget,
                        e.getMessage());
                failedItems.computeIfAbsent(indexTarget, k -> Collections.synchronizedList(new ArrayList<>()))
                        .addAll(documents);

                ThreadUtil.sleep(5000);
            }
        }

        if (!failedItems.isEmpty()) {
            synchronized (dataMap) {
                for (Map.Entry<String, List<String>> entry : failedItems.entrySet()) {
                    dataMap.computeIfAbsent(entry.getKey(), k -> Collections.synchronizedList(new ArrayList<>()))
                            .addAll(entry.getValue());
                    totalDocumentCount.addAndGet(entry.getValue().size());
                }
            }
            LOGGER.warn("Re-queued {} items for retry.", totalDocumentCount.get());
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
