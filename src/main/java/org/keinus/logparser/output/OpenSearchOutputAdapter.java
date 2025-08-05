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
import org.apache.http.conn.ssl.NoopHostnameVerifier; // Consider implications for production
import org.keinus.logparser.interfaces.OutputAdapter;
import org.keinus.logparser.util.ThreadUtil;

public class OpenSearchOutputAdapter extends OutputAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchOutputAdapter.class);
    private String host;
    private int port;
    private String indexTemplate;
    private String credentials = null;
    private List<String> indexVars = null;
    
    private final ConcurrentHashMap<String, List<String>> dataMap = new ConcurrentHashMap<>();
    private final AtomicInteger totalDocumentCount = new AtomicInteger(0); 
    
    private CloseableHttpClient httpClient;

    public OpenSearchOutputAdapter(Map<String, String> obj) throws IOException {
        super(obj);

        host = Objects.requireNonNull(obj.get("host"), "OpenSearch 'host' must not be null");
        indexTemplate = Objects.requireNonNull(obj.get("index"), "OpenSearch 'index' must not be null"); // Use indexTemplate
        String portStr = Objects.requireNonNull(obj.get("port"), "OpenSearch 'port' must not be null");
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("OpenSearch 'port' must be a valid number: " + portStr, e); // Pass original exception
        }
        
        indexVars = extractBracedStrings(indexTemplate); // Use indexTemplate
        String username = obj.get("username");
        String password = obj.get("password");
        if(username == null || username.isEmpty()) { // Check for empty username as well
            credentials = null;
        } else {
            credentials = username + ":" + password;
        }

        LOGGER.info("OpenSearch Output Adapter Init. {}:{}", host, port); // Corrected log message

        try {
            SSLConnectionSocketFactory scsf = new SSLConnectionSocketFactory(
                    SSLContexts.custom().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build(),
                    NoopHostnameVerifier.INSTANCE);
            this.httpClient = HttpClients.custom().setSSLSocketFactory(scsf).build();

        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            LOGGER.error("Failed to initialize HTTP client for OpenSearch: {}", e.getMessage(), e);
            throw new IOException("Failed to initialize HTTP client for OpenSearch", e); 
        }
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

        if (totalDocumentCount.get() >= 2000) {
            this.flush();
        }
    }

    @Override
    public void close() throws IOException {
        LOGGER.info("Closing OpenSearch Output Adapter and flushing remaining data.");
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

    @Override
    public void flush() {
        Map<String, List<String>> failedItems = new ConcurrentHashMap<>();

        ConcurrentHashMap<String, List<String>> itemsToFlush = new ConcurrentHashMap<>();
        synchronized (dataMap) { // Synchronize to ensure atomic swap
            itemsToFlush.putAll(dataMap);
            dataMap.clear();
            totalDocumentCount.set(0); // Reset total count after moving items
        }

        if (itemsToFlush.isEmpty()) {
            LOGGER.debug("No items to flush.");
            return;
        }

        for (Map.Entry<String, List<String>> entry : itemsToFlush.entrySet()) {
            long startElapse = System.currentTimeMillis();

            String indexTarget = entry.getKey();
            List<String> documents = entry.getValue();
            int count = documents.size();
            
            String url = "https://" + host + ":" + port + "/" + indexTarget + "/_bulk";
            String body = formatBulkRequestForIndex(indexTarget, documents).toString();

            try {
                sendRest(url, body);
                long endElapse = System.currentTimeMillis();
                double elapsedSeconds = (endElapse - startElapse) / 1000.0;
                int processedPerSecond = (int) (elapsedSeconds > 0 ? count / elapsedSeconds : count); 
                LOGGER.info("{} items processed for index '{}' in {} seconds: {}/s", count, indexTarget, String.format("%.3f", elapsedSeconds), processedPerSecond);
            } catch (IOException e) {
                LOGGER.error("Failed to send data for index '{}'. Will retry later. Error: {}", indexTarget, e.getMessage());
                failedItems.computeIfAbsent(indexTarget, k -> Collections.synchronizedList(new ArrayList<>())).addAll(documents);

                ThreadUtil.sleep(5000); 
            }
        }
        
        if (!failedItems.isEmpty()) {
            synchronized (dataMap) { 
                for (Map.Entry<String, List<String>> entry : failedItems.entrySet()) {
                    dataMap.computeIfAbsent(entry.getKey(), k -> Collections.synchronizedList(new ArrayList<>())).addAll(entry.getValue());
                    totalDocumentCount.addAndGet(entry.getValue().size()); // Update total count for retried items
                }
            }
            LOGGER.warn("Re-queued {} items for retry.", totalDocumentCount.get());
        }
    }

    public void sendRest(String url, String json) throws IOException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setEntity(new StringEntity(json, StandardCharsets.UTF_8));

        if(credentials != null) {
            String base64Credentials = Base64.encodeBase64String(credentials.getBytes(StandardCharsets.UTF_8));
            String authorization = "Basic " + base64Credentials;
            httpPost.setHeader("Authorization", authorization);
        }

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < 200 || statusCode >= 300) {
                String responseBody = new BasicResponseHandler().handleResponse(response);
                throw new IOException("OpenSearch indexing failed with status " + statusCode + ". Response: " + responseBody);
            } else {
                LOGGER.debug("Successfully sent data to OpenSearch. Status: {}", statusCode);
            }
        }
    }
}
