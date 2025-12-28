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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
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
            public void send(Map<String, Object> json, String jsonString) {
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
}
