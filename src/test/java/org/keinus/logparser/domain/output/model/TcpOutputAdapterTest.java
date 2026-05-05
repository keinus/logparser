package org.keinus.logparser.domain.output.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keinus.logparser.domain.model.LogEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TcpOutputAdapterTest {

    private Map<String, String> config;
    private TcpStubServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new TcpStubServer();
        new Thread(server).start();
        
        config = new HashMap<>();
        config.put("id", "3");
        config.put("host", "localhost");
        config.put("port", String.valueOf(server.getPort()));
        config.put("retryCount", "2");
        config.put("retryDelayMs", "100");
        config.put("timeoutMs", "1000");
        config.put("add_origin_text", "true");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
    }

    @Test
    void testSendSuccess() throws IOException, InterruptedException {
        TcpOutputAdapter adapter = new TcpOutputAdapter(config);
        LogEvent event = new LogEvent("test tcp message", "localhost", "test");
        
        adapter.send(event);
        
        assertTrue(server.awaitMessage(5, TimeUnit.SECONDS));
        String received = server.getMessage();
        assertTrue(received.contains("test tcp message"));
    }

    @Test
    void testSendFailure() throws IOException {
        server.close();
        try (ServerSocket closedSocket = new ServerSocket(0)) {
            config.put("port", String.valueOf(closedSocket.getLocalPort()));
        }
        config.put("retryCount", "1");
        config.put("retryDelayMs", "1");
        
        TcpOutputAdapter adapter = new TcpOutputAdapter(config);
        LogEvent event = new LogEvent("test failure", "localhost", "test");
        
        assertThrows(OutputDeliveryException.class, () -> adapter.send(event));
    }

    @Test
    void testConstructorWithInvalidConfig() {
        Map<String, String> invalidConfig = new HashMap<>();
        // missing port
        assertThrows(RuntimeException.class, () -> new TcpOutputAdapter(invalidConfig));
    }

    private static class TcpStubServer implements Runnable, AutoCloseable {
        private final ServerSocket serverSocket;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private volatile boolean running = true;

        public TcpStubServer() throws IOException {
            this.serverSocket = new ServerSocket(0);
        }

        public int getPort() {
            return serverSocket.getLocalPort();
        }

        public String getMessage() {
            return outputStream.toString();
        }

        public boolean awaitMessage(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        @Override
        public void run() {
            while (running) {
                try (Socket clientSocket = serverSocket.accept();
                     InputStream is = clientSocket.getInputStream()) {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                    }
                    latch.countDown();
                } catch (IOException e) {
                    if (running) e.printStackTrace();
                }
            }
        }

        @Override
        public void close() throws IOException {
            running = false;
            serverSocket.close();
        }
    }
}
