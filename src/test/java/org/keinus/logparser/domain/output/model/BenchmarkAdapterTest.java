package org.keinus.logparser.domain.output.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keinus.logparser.domain.model.LogEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkAdapterTest {

    private Map<String, String> config;

    @BeforeEach
    void setUp() {
        config = new HashMap<>();
        config.put("id", "2");
        config.put("messagetype", "benchmark");
    }

    @Test
    void testConstructorAndGetters() throws IOException {
        BenchmarkAdapter adapter = new BenchmarkAdapter(config);
        assertEquals(2L, adapter.getId());
        assertEquals("benchmark", adapter.getMessageType());
    }

    @Test
    void testSend() throws IOException, InterruptedException {
        BenchmarkAdapter adapter = new BenchmarkAdapter(config);
        LogEvent event = new LogEvent("test message", "localhost", "test");
        
        // Send multiple events to trigger TPS calculation logic
        for (int i = 0; i < 1100; i++) {
            adapter.send(event);
        }
        
        // Wait a bit to ensure elapsed >= 1000ms if needed, 
        // but BenchmarkAdapter uses System.currentTimeMillis() which might be fast.
        // Actually the code:
        // if (elapsed >= 1000) { ... }
        // To test this we might need to sleep.
        Thread.sleep(1100);
        adapter.send(event); // This should trigger the log
        
        assertDoesNotThrow(() -> adapter.send(event));
    }

    @Test
    void testSendWhenClosed() throws IOException {
        BenchmarkAdapter adapter = new BenchmarkAdapter(config);
        adapter.close();
        LogEvent event = new LogEvent("test message", "localhost", "test");
        assertThrows(OutputDeliveryException.class, () -> adapter.send(event));
    }

    @Test
    void testCloseIdempotent() throws IOException {
        BenchmarkAdapter adapter = new BenchmarkAdapter(config);
        assertDoesNotThrow(adapter::close);
        assertDoesNotThrow(adapter::close);
    }
}
