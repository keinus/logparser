package org.keinus.logparser.output;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.output.model.HttpOutputAdapter;
import org.keinus.logparser.domain.output.model.OutputDeliveryException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpOutputAdapterTest {

    private final Map<String, String> validConfig = new HashMap<>();
    private HttpOutputAdapter adapter;

    HttpOutputAdapterTest() {
        validConfig.put("messagetype", "http-output");
        validConfig.put("add_origin_text", "false");
        validConfig.put("timeoutMs", "5000");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (adapter != null) {
            adapter.close();
        }
    }

    @Test
    @DisplayName("생성자 테스트 - 유효한 설정으로 생성")
    void testConstructorWithValidConfig() throws IOException {
        try (StubHttpServer server = StubHttpServer.success()) {
            validConfig.put("url", server.url("/api/logs"));
            assertDoesNotThrow(() -> adapter = new HttpOutputAdapter(validConfig));
        }
    }

    @Test
    @DisplayName("생성자 테스트 - null 설정으로 생성 시 예외 발생")
    void testConstructorWithNullConfig() {
        assertThrows(IOException.class, () -> new HttpOutputAdapter(null));
    }

    @Test
    @DisplayName("생성자 테스트 - URL 누락 시 예외 발생")
    void testConstructorWithMissingUrl() {
        assertThrows(IOException.class, () -> new HttpOutputAdapter(validConfig));
    }

    @Test
    @DisplayName("생성자 테스트 - 잘못된 URL 형식")
    void testConstructorWithInvalidUrl() {
        validConfig.put("url", "invalid-url-format");
        assertThrows(IOException.class, () -> new HttpOutputAdapter(validConfig));
    }

    @Test
    @DisplayName("생성자 테스트 - HTTPS URL 허용")
    void testConstructorWithHttpsUrl() {
        validConfig.put("url", "https://example.com/api/logs");
        assertDoesNotThrow(() -> adapter = new HttpOutputAdapter(validConfig));
    }

    @Test
    @DisplayName("send() 테스트 - method, headers, origin_text를 반영한다")
    void testSendUsesConfiguredMethodHeadersAndOriginText() throws Exception {
        try (StubHttpServer server = StubHttpServer.success()) {
            validConfig.put("url", server.url("/api/logs"));
            validConfig.put("method", "PUT");
            validConfig.put("headers", "{\"X-Test-Header\":\"enabled\"}");
            validConfig.put("add_origin_text", "true");
            adapter = new HttpOutputAdapter(validConfig);

            LogEvent logEvent = new LogEvent("test log", "localhost", "test");
            logEvent.setField("level", "INFO");

            assertDoesNotThrow(() -> adapter.send(logEvent));
            server.awaitRequest();

            assertTrue(server.getRequestLine().startsWith("PUT /api/logs HTTP/1.1"));
            assertEquals("enabled", server.getHeaders().get("x-test-header"));
            assertTrue(server.getBody().contains("\"origin_text\":\"test log\""));
            assertTrue(server.getBody().contains("\"level\":\"INFO\""));
        }
    }

    @Test
    @DisplayName("send() 테스트 - 서버 연결 실패")
    void testSendConnectionFailure() throws IOException {
        validConfig.put("url", "http://localhost:19999/api/logs");
        adapter = new HttpOutputAdapter(validConfig);

        LogEvent logEvent = new LogEvent("test log", "localhost", "test");

        assertThrows(OutputDeliveryException.class, () -> adapter.send(logEvent));
    }

    @Test
    @DisplayName("send() 테스트 - non-2xx 응답이면 실패")
    void testSendNon2xxResponse() throws Exception {
        try (StubHttpServer server = StubHttpServer.error(500)) {
            validConfig.put("url", server.url("/api/logs"));
            adapter = new HttpOutputAdapter(validConfig);

            LogEvent logEvent = new LogEvent("test log", "localhost", "test");

            assertThrows(OutputDeliveryException.class, () -> adapter.send(logEvent));
            server.awaitRequest();
        }
    }

    @Test
    @DisplayName("close() 테스트 - 리소스 정리")
    void testClose() throws IOException {
        validConfig.put("url", "https://example.com/api/logs");
        adapter = new HttpOutputAdapter(validConfig);
        assertDoesNotThrow(() -> adapter.close());
    }

    private static final class StubHttpServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread serverThread;
        private final CountDownLatch requestLatch = new CountDownLatch(1);
        private final int statusCode;
        private volatile IOException serverError;
        private volatile String requestLine;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private volatile String body = "";

        private StubHttpServer(int statusCode) throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.statusCode = statusCode;
            this.serverThread = Thread.ofPlatform().start(this::serveOnce);
        }

        static StubHttpServer success() throws IOException {
            return new StubHttpServer(200);
        }

        static StubHttpServer error(int statusCode) throws IOException {
            return new StubHttpServer(statusCode);
        }

        String url(String path) {
            return "http://localhost:" + serverSocket.getLocalPort() + path;
        }

        void awaitRequest() throws Exception {
            boolean received = requestLatch.await(5, TimeUnit.SECONDS);
            if (!received) {
                throw new AssertionError("HTTP request was not received by stub server");
            }
            if (serverError != null) {
                throw serverError;
            }
        }

        String getRequestLine() {
            return requestLine;
        }

        Map<String, String> getHeaders() {
            return headers;
        }

        String getBody() {
            return body;
        }

        private void serveOnce() {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(5000);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                );
                requestLine = reader.readLine();

                int contentLength = 0;
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    int separatorIndex = line.indexOf(':');
                    if (separatorIndex > 0) {
                        String headerName = line.substring(0, separatorIndex).trim().toLowerCase();
                        String headerValue = line.substring(separatorIndex + 1).trim();
                        headers.put(headerName, headerValue);
                        if ("content-length".equals(headerName)) {
                            contentLength = Integer.parseInt(headerValue);
                        }
                    }
                }

                char[] buffer = new char[Math.max(contentLength, 1)];
                int offset = 0;
                while (offset < contentLength) {
                    int read = reader.read(buffer, offset, contentLength - offset);
                    if (read == -1) {
                        break;
                    }
                    offset += read;
                }
                body = new String(buffer, 0, offset);

                byte[] response = (
                        "HTTP/1.1 " + statusCode + " Test\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
                ).getBytes(StandardCharsets.UTF_8);

                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(response);
                outputStream.flush();
                requestLatch.countDown();
            } catch (IOException e) {
                serverError = e;
                requestLatch.countDown();
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            try {
                serverThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while stopping stub server", e);
            }
        }
    }
}
