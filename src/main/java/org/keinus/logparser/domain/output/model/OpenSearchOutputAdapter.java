package org.keinus.logparser.domain.output.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.infrastructure.util.PatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 처리된 메시지를 OpenSearch 또는 Elasticsearch에 동기 전송하는 출력 어댑터입니다.
 */
public class OpenSearchOutputAdapter extends OutputAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchOutputAdapter.class);
    private static final PatternCache PATTERN_CACHE = PatternCache.getInstance();
    private static final Pattern BRACED_STRING_PATTERN = PATTERN_CACHE.compile("%\\{(.*?)}");

    private final String baseUrl;
    private final String indexTemplate;
    private final String authorizationHeader;
    private final List<String> indexVars;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final CloseableHttpClient httpClient;
    private final PoolingHttpClientConnectionManager connectionManager;

    public OpenSearchOutputAdapter(Map<String, String> obj) throws IOException {
        super(obj);

        this.baseUrl = Objects.requireNonNull(obj.get("url"), "OpenSearch 'url' must not be null");
        this.indexTemplate = Objects.requireNonNull(obj.get("index"), "OpenSearch 'index' must not be null");
        this.indexVars = extractBracedStrings(indexTemplate);

        String username = obj.get("username");
        String password = obj.get("password");
        if (username != null && !username.isEmpty()) {
            String credentials = username + ":" + Objects.toString(password, "");
            this.authorizationHeader = "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        } else {
            this.authorizationHeader = null;
        }

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
                    .setMaxConnTotal(20)
                    .setMaxConnPerRoute(10)
                    .build();

            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.ofMilliseconds(getTimeoutMs()))
                    .setResponseTimeout(Timeout.ofMilliseconds(getTimeoutMs()))
                    .build();

            this.httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setDefaultRequestConfig(requestConfig)
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new IOException("Failed to initialize HTTP client for OpenSearch", e);
        }

        LOGGER.info("OpenSearch Output Adapter initialized for {}", baseUrl);
    }

    @Override
    public void send(LogEvent logEvent) {
        if (closed.get()) {
            throw deliveryFailure("Adapter is closed");
        }

        String targetIndex = null;
        try {
            Map<String, Object> json = outputMap(logEvent);
            targetIndex = resolveIndex(json);
            String payload = serializeEvent(logEvent);
            String url = buildDocumentUrl(targetIndex);

            HttpPost request = new HttpPost(url);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            if (authorizationHeader != null) {
                request.setHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
            }
            request.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
            if (statusCode < 200 || statusCode >= 300) {
                throw deliveryFailure(
                        "OpenSearch request failed with status " + statusCode + " for index " + targetIndex
                                + (responseBody.isBlank() ? "" : ": " + responseBody)
                );
            }
            }
        } catch (OutputDeliveryException e) {
            throw e;
        } catch (IOException | ParseException | IllegalArgumentException e) {
            throw deliveryFailure("Failed to send document to OpenSearch index " + targetIndex, e);
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            LOGGER.debug("OpenSearch Output Adapter already closed, skipping");
            return;
        }

        try {
            httpClient.close();
        } catch (IOException e) {
            LOGGER.error("Error closing OpenSearch HTTP client: {}", e.getMessage(), e);
            throw e;
        } finally {
            connectionManager.close();
        }

        LOGGER.info("OpenSearch Output Adapter closed");
    }

    private String resolveIndex(Map<String, Object> json) {
        String targetIndex = indexTemplate;
        for (String variable : indexVars) {
            String replacement = null;
            if (variable.startsWith("yy")) {
                replacement = new SimpleDateFormat(variable).format(new Date());
            } else {
                Object value = json.get(variable);
                if (value != null) {
                    replacement = value.toString();
                }
            }

            if (replacement == null || replacement.isEmpty()) {
                throw deliveryFailure("Missing value for OpenSearch index template variable: " + variable);
            }
            targetIndex = targetIndex.replace("%{" + variable + "}", replacement);
        }
        return targetIndex;
    }

    private String buildDocumentUrl(String targetIndex) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + "/" + targetIndex + "/_doc";
    }

    private List<String> extractBracedStrings(String input) {
        List<String> extractedStrings = new ArrayList<>();
        Matcher matcher = BRACED_STRING_PATTERN.matcher(input);
        while (matcher.find()) {
            extractedStrings.add(matcher.group(1));
        }
        return extractedStrings;
    }
}
