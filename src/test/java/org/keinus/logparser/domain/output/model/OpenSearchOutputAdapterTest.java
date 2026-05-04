package org.keinus.logparser.domain.output.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keinus.logparser.domain.model.LogEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OpenSearchOutputAdapterTest {

    private Map<String, String> config;
    private StubHttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubHttpServer(200);
        new Thread(server).start();

        config = new HashMap<>();
        config.put("id", "4");
        config.put("url", "http://localhost:" + server.getPort() + "/");
        config.put("index", "logs-%{yyyyMMdd}-%{service}");
        config.put("username", "admin");
        config.put("password", "admin");
        config.put("timeoutMs", "1000");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
    }

    @Test
    void testSendSuccess() throws IOException, InterruptedException {
        OpenSearchOutputAdapter adapter = new OpenSearchOutputAdapter(config);
        LogEvent event = new LogEvent("test message", "localhost", "test");
        event.setField("service", "auth-service");

        adapter.send(event);

        assertTrue(server.awaitRequest(5, TimeUnit.SECONDS));
        String requestLine = server.getRequestLine();
        assertTrue(requestLine.contains("PUT") || requestLine.contains("POST"));
        // Index resolution check: yyyyMMdd should be today's date, service should be auth-service
        assertTrue(server.getPath().contains("logs-"));
        assertTrue(server.getPath().contains("auth-service"));
        assertTrue(server.getPath().endsWith("/_doc"));
        
        String authHeader = server.getHeaders().get("authorization");
        assertNotNull(authHeader);
        assertTrue(authHeader.startsWith("Basic "));
    }

    @Test
    void testSendFailure() throws IOException {
        server.setResponseCode(500);
        OpenSearchOutputAdapter adapter = new OpenSearchOutputAdapter(config);
        LogEvent event = new LogEvent("test message", "localhost", "test");

        assertThrows(OutputDeliveryException.class, () -> adapter.send(event));
    }

    @Test
    void testConstructorMissingUrl() {
        config.remove("url");
        assertThrows(NullPointerException.class, () -> new OpenSearchOutputAdapter(config));
    }

    @Test
    void testClose() throws IOException {
        OpenSearchOutputAdapter adapter = new OpenSearchOutputAdapter(config);
        assertDoesNotThrow(adapter::close);
    }

    private static class StubHttpServer implements Runnable, AutoCloseable {
        private final ServerSocket serverSocket;
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile int responseCode;
        private volatile String requestLine;
        private final Map<String, String> headers = new HashMap<>();
        private volatile boolean running = true;

        public StubHttpServer(int responseCode) throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.responseCode = responseCode;
        }

        public int getPort() {
            return serverSocket.getLocalPort();
        }

        public void setResponseCode(int code) {
            this.responseCode = code;
        }

        public String getRequestLine() {
            return requestLine;
        }

        public String getPath() {
            if (requestLine == null) return "";
            String[] parts = requestLine.split(" ");
            return parts.length > 1 ? parts[1] : "";
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public boolean awaitRequest(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        @Override
        public void run() {
            try {
                while (running) {
                    try (Socket socket = serverSocket.accept();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                         OutputStream os = socket.getOutputStream()) {
                        
                        requestLine = reader.readLine();
                        if (requestLine == null) continue;

                        String line;
                        while ((line = reader.readLine()) != null && !line.isEmpty()) {
                            int colon = line.indexOf(":");
                            if (colon > 0) {
                                headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
                            }
                        }

                        String response = "HTTP/1.1 " + responseCode + " OK\r\nContent-Length: 0\r\n\r\n";
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        latch.countDown();
                    } catch (IOException e) {
                        if (running) e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }

        @Override
        public void close() throws IOException {
            running = false;
            serverSocket.close();
        }
    }
}
