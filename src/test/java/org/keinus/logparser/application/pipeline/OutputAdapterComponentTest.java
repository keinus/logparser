package org.keinus.logparser.application.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keinus.logparser.application.service.BatchingOutputService;
import org.keinus.logparser.domain.configuration.model.OutputAdapterConfig;
import org.keinus.logparser.domain.delivery.model.OutputAdapter;
import org.keinus.logparser.domain.delivery.service.OutputFactory;
import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.infrastructure.config.ApplicationProperties;
import org.keinus.logparser.infrastructure.util.ThreadManager;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutputAdapterComponentTest {

    @Mock
    private ApplicationProperties appProp;
    @Mock
    private ThreadManager threadManager;
    @Mock
    private MessageDispatcher dispatcher;
    @Mock
    private BatchingOutputService batchingOutputService;

    private OutputAdapterComponent outputAdapterComponent;

    @BeforeEach
    void setUp() {
        outputAdapterComponent = new OutputAdapterComponent(appProp, threadManager, dispatcher, batchingOutputService);
    }

    @Test
    void testMessageDeliveryViaDedicatedAdapterWorker() throws Exception {
        // Arrange
        OutputAdapterConfig config = new OutputAdapterConfig();
        config.setId(1L);
        config.setType("MockAdapter");
        config.setMessagetype("test");
        config.setEnabled(true);
        
        when(appProp.getOutput()).thenReturn(List.of(config));
        
        LogEvent logEvent = new LogEvent("test message", "localhost", "test");
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean wasVirtual = new AtomicBoolean(false);

        OutputAdapter mockAdapter = new OutputAdapter(Map.of("messagetype", "test")) {
            @Override
            public void send(LogEvent logEvent) {
                wasVirtual.set(Thread.currentThread().isVirtual());
                latch.countDown();
            }
            @Override
            public void close() {}
            @Override
            public Long getId() { return 1L; }
        };

        try (MockedStatic<OutputFactory> mockedFactory = mockStatic(OutputFactory.class)) {
            mockedFactory.when(() -> OutputFactory.getOutputAdapter(any())).thenReturn(mockAdapter);

            // Mock dispatcher to return logEvent once, then null to stop the loop
            when(dispatcher.getOutputMsg()).thenReturn(logEvent).thenAnswer(inv -> {
                return null;
            });

            // Trigger threads manually when executeWithName is called
            doAnswer(invocation -> {
                Runnable task = invocation.getArgument(1);
                Thread thread = Thread.ofVirtual().start(task);
                return null;
            }).when(threadManager).executeWithName(anyString(), any(Runnable.class));

            // Act
            outputAdapterComponent.startPipeline();

            // Wait for delivery
            boolean delivered = latch.await(10, TimeUnit.SECONDS);

            // Assert
            assertTrue(delivered, "Message should be delivered to adapter");
            assertTrue(wasVirtual.get(), "Message should be delivered via a virtual thread worker");
            
            outputAdapterComponent.close();
        }
    }

    @Test
    void testBroadcastToMultipleAdapters() throws Exception {
        // Arrange
        OutputAdapterConfig config1 = new OutputAdapterConfig();
        config1.setId(1L);
        config1.setType("MockAdapter1");
        config1.setMessagetype("test");
        config1.setEnabled(true);
        config1.setAddOriginText(true);

        OutputAdapterConfig config2 = new OutputAdapterConfig();
        config2.setId(2L);
        config2.setType("MockAdapter2");
        config2.setMessagetype("test"); // Same message type
        config2.setEnabled(true);
        config2.setAddOriginText(true); // Same setting

        when(appProp.getOutput()).thenReturn(List.of(config1, config2));

        LogEvent logEvent = spy(new LogEvent("test message", "localhost", "test"));
        
        CountDownLatch latch = new CountDownLatch(2);

        OutputAdapter mockAdapter1 = new OutputAdapter(Map.of("messagetype", "test", "add_origin_text", "true", "id", "1")) {
            @Override
            public void send(LogEvent logEvent) {
                latch.countDown();
            }
            @Override
            public void close() {}
        };
        OutputAdapter mockAdapter2 = new OutputAdapter(Map.of("messagetype", "test", "add_origin_text", "true", "id", "2")) {
            @Override
            public void send(LogEvent logEvent) {
                latch.countDown();
            }
            @Override
            public void close() {}
        };

        try (MockedStatic<OutputFactory> mockedFactory = mockStatic(OutputFactory.class)) {
            mockedFactory.when(() -> OutputFactory.getOutputAdapter(any())).thenAnswer(inv -> {
                OutputAdapterConfig c = inv.getArgument(0);
                if (c.getId() == 1L) return mockAdapter1;
                return mockAdapter2;
            });

            // Dispatcher returns event then null
            when(dispatcher.getOutputMsg()).thenReturn(logEvent).thenAnswer(inv -> null);

             // Trigger threads manually
            doAnswer(invocation -> {
                Runnable task = invocation.getArgument(1);
                Thread.ofVirtual().start(task);
                return null;
            }).when(threadManager).executeWithName(anyString(), any(Runnable.class));

            // Act
            outputAdapterComponent.startPipeline();

            // Wait
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be delivered to both adapters");
            
            outputAdapterComponent.close();
        }
    }
}