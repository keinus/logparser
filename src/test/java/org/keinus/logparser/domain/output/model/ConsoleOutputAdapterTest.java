package org.keinus.logparser.domain.output.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keinus.logparser.domain.model.LogEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleOutputAdapterTest {

    private Map<String, String> config;

    @BeforeEach
    void setUp() {
        config = new HashMap<>();
        config.put("id", "1");
        config.put("messagetype", "test");
    }

    @Test
    void testConstructorAndGetters() throws IOException {
        ConsoleOutputAdapter adapter = new ConsoleOutputAdapter(config);
        assertEquals(1L, adapter.getId());
        assertEquals("test", adapter.getMessageType());
        assertTrue(adapter.toString().contains("ConsoleOutputAdapter"));
    }

    @Test
    void testSend() throws IOException {
        ConsoleOutputAdapter adapter = new ConsoleOutputAdapter(config);
        LogEvent event = new LogEvent("test message", "localhost", "test");
        assertDoesNotThrow(() -> adapter.send(event));
    }

    @Test
    void testSendWhenClosed() throws IOException {
        ConsoleOutputAdapter adapter = new ConsoleOutputAdapter(config);
        adapter.close();
        LogEvent event = new LogEvent("test message", "localhost", "test");
        assertThrows(OutputDeliveryException.class, () -> adapter.send(event));
    }

    @Test
    void testCloseIdempotent() throws IOException {
        ConsoleOutputAdapter adapter = new ConsoleOutputAdapter(config);
        assertDoesNotThrow(adapter::close);
        assertDoesNotThrow(adapter::close);
    }
}
