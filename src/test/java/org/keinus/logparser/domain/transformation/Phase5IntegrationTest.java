package org.keinus.logparser.domain.transformation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keinus.logparser.application.pipeline.TransformDispatcher;
import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.transformation.service.TransformService;
import org.keinus.logparser.domain.service.transform.ConditionEvaluator;
import org.keinus.logparser.domain.service.transform.StructuredEventSerializer;
import org.keinus.logparser.domain.service.transform.StructuredTransformService;
import org.keinus.logparser.domain.repository.MappingRepository;
import org.keinus.logparser.infrastructure.util.id.SnowflakeIdGenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class Phase5IntegrationTest {

    private TransformDispatcher transformDispatcher;
    private BlockingQueue<LogEvent> transformQueue;
    private BlockingQueue<LogEvent> outputQueue;
    private TransformService transformService;
    private StructuredTransformService structuredTransformService;
    private MappingRepository mappingRepository;

    @BeforeEach
    public void setup() {
        transformQueue = new LinkedBlockingQueue<>();
        outputQueue = new LinkedBlockingQueue<>();
        transformService = mock(TransformService.class);
        mappingRepository = mock(MappingRepository.class);
        
        // Setup real StructuredTransformService dependencies
        ConditionEvaluator conditionEvaluator = new ConditionEvaluator(); // Assuming simple default constructor or mock if needed
        SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        StructuredEventSerializer serializer = new StructuredEventSerializer(objectMapper);

        structuredTransformService = new StructuredTransformService(
            mappingRepository, 
            conditionEvaluator, 
            idGenerator, 
            serializer
        );

        transformDispatcher = new TransformDispatcher(
            transformQueue,
            outputQueue,
            transformService,
            structuredTransformService,
            new AtomicBoolean(true),
            new AtomicLong(0)
        );
    }

    @Test
    public void testIntegrationFlow() throws InterruptedException {
        // Given
        LogEvent event = new LogEvent("raw log", "localhost", "test-type");
        event.setField("src_ip", "192.168.1.1");
        event.setField("dst_port", 80); // Should trigger event_web if rules were set up
        
        transformQueue.put(event);

        // Mock legacy transform to pass
        when(transformService.transform(any(LogEvent.class))).thenReturn(true);

        // When
        // Run the dispatcher logic manually or start thread. 
        // Since it's a loop, we can just run the runnable in a separate thread and wait a bit,
        // or extract the logic. But easier to just start thread and stop it.
        
        Thread t = new Thread(transformDispatcher);
        t.start();
        
        // Wait for output
        LogEvent processedEvent = outputQueue.take();
        
        // Stop thread
        // (In real test we'd control the AtomicBoolean running flag, 
        // but here we just needed one event processed)
        
        // Then
        Assertions.assertNotNull(processedEvent);
        Assertions.assertTrue(processedEvent.isTransformed());
        
        // Verify Structure
        Map<String, Object> fields = processedEvent.getFields();
        Assertions.assertTrue(fields.containsKey("common"));
        Assertions.assertTrue(fields.containsKey("additionalAttributes"));
        
        // Check if flat fields are moved to additionalAttributes (since we mocked repository to return empty/null config, it falls back to default)
        Map<String, Object> additional = (Map<String, Object>) fields.get("additionalAttributes");
        Assertions.assertEquals("192.168.1.1", additional.get("src_ip"));
        
        System.out.println("Transformed Fields: " + fields);
        
        t.interrupt();
    }
}
