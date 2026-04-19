package org.keinus.logparser.domain.output.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.domain.model.LogEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 처리된 메시지를 지정된 URL로 HTTP 요청으로 전송하는 출력 어댑터입니다.
 */
@Slf4j
public class HttpOutputAdapter extends OutputAdapter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> HEADERS_TYPE = new TypeReference<>() {};

    private final URI targetUri;
    private final String method;
    private final Map<String, String> headers;
    private final HttpClient httpClient;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public HttpOutputAdapter(Map<String, String> obj) throws IOException {
        super(obj);

        String url = obj.get("url");
        if (url == null || url.isBlank()) {
            throw new IOException("URL is required for HttpOutputAdapter");
        }

        this.targetUri = validateUri(url);
        this.method = normalizeMethod(obj.get("method"));
        this.headers = parseHeaders(obj.get("headers"));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(getTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        log.info("HTTP Output Adapter configured for {} {}", method, targetUri);
    }

    @Override
    public void send(LogEvent logEvent) {
        if (closed.get()) {
            throw deliveryFailure("Adapter is closed");
        }

        String payload = serializeEvent(logEvent);
        if (payload.isEmpty()) {
            throw deliveryFailure("Empty JSON payload");
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(targetUri)
                .timeout(Duration.ofMillis(getTimeoutMs()));

        boolean hasContentTypeHeader = false;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder.header(entry.getKey(), entry.getValue());
            if ("content-type".equalsIgnoreCase(entry.getKey())) {
                hasContentTypeHeader = true;
            }
        }

        if (!hasContentTypeHeader) {
            requestBuilder.header("Content-Type", "application/json");
        }

        requestBuilder.header("User-Agent", "LogParser/1.0");
        requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(payload));

        try {
            HttpResponse<Void> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw deliveryFailure("HTTP request failed with status code " + statusCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw deliveryFailure("HTTP send interrupted", e);
        } catch (IOException e) {
            throw deliveryFailure("HTTP send failed", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            log.debug("HTTP Output Adapter already closed, skipping");
            return;
        }

        log.info("HTTP Output Adapter closed");
    }

    private URI validateUri(String url) throws IOException {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IOException("URL must start with http:// or https://");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IOException("Host cannot be empty");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid URL format", e);
        }
    }

    private String normalizeMethod(String configuredMethod) {
        if (configuredMethod == null || configuredMethod.isBlank()) {
            return "POST";
        }

        String normalizedMethod = configuredMethod.toUpperCase(Locale.ROOT);
        return switch (normalizedMethod) {
            case "POST", "PUT", "PATCH" -> normalizedMethod;
            default -> throw deliveryFailure("Unsupported HTTP method: " + configuredMethod);
        };
    }

    private Map<String, String> parseHeaders(String rawHeaders) throws IOException {
        if (rawHeaders == null || rawHeaders.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, String> parsedHeaders = OBJECT_MAPPER.readValue(rawHeaders, HEADERS_TYPE);
            return new LinkedHashMap<>(parsedHeaders);
        } catch (Exception e) {
            throw new IOException("Invalid HTTP headers configuration", e);
        }
    }
}
