package org.keinus.logparser.domain.transformation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keinus.logparser.application.pipeline.ProcessingDispatcher;
import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.parse.service.ParseService;
import org.keinus.logparser.domain.transformation.service.ConditionEvaluator;
import org.keinus.logparser.domain.transformation.service.StructuredEventSerializer;
import org.keinus.logparser.domain.transformation.service.StructuredTransformService;
import org.keinus.logparser.domain.transformation.service.TransformService;
import org.keinus.logparser.infrastructure.persistence.repository.MappingRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class Phase5IntegrationTest {

    private ProcessingDispatcher processingDispatcher;
    private BlockingQueue<LogEvent> inputQueue;
    private BlockingQueue<LogEvent> outputQueue;
    private ParseService parseService;
    private TransformService transformService;
    private StructuredTransformService structuredTransformService;
    private MappingRepository mappingRepository;

    @BeforeEach
    public void setup() {
        inputQueue = new LinkedBlockingQueue<>();
        outputQueue = new LinkedBlockingQueue<>();
        parseService = mock(ParseService.class);
        transformService = mock(TransformService.class);
        mappingRepository = mock(MappingRepository.class);
        
        // Setup real StructuredTransformService dependencies
        ConditionEvaluator conditionEvaluator = new ConditionEvaluator(); // Assuming simple default constructor or mock if needed
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        StructuredEventSerializer serializer = new StructuredEventSerializer(objectMapper);

        structuredTransformService = new StructuredTransformService(
            mappingRepository, 
            conditionEvaluator, 
            serializer
        );

        processingDispatcher = new ProcessingDispatcher(
            inputQueue,
            outputQueue,
            parseService,
            transformService,
            structuredTransformService,
            null, // LiveTailService
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
        
        inputQueue.put(event);

        // Mock parse and legacy transform to pass
        when(parseService.parse(any(LogEvent.class))).thenReturn(true);
        when(transformService.transform(any(LogEvent.class))).thenReturn(true);

        // When
        // Run the dispatcher logic manually or start thread. 
        Thread t = new Thread(processingDispatcher);
        t.start();
        
        // Wait for output
        LogEvent processedEvent = outputQueue.take();
        
        // Stop thread
        t.interrupt();
        
        // Then
        Assertions.assertNotNull(processedEvent);
        Assertions.assertTrue(processedEvent.isTransformed());
        
        // Verify Structure
        Map<String, Object> fields = processedEvent.getFields();
        Assertions.assertTrue(fields.containsKey("common"));
        Assertions.assertTrue(fields.containsKey("additionalAttributes"));
        
        // Check if flat fields are moved to additionalAttributes (since we mocked repository to return empty/null config, it falls back to default)
        @SuppressWarnings("unchecked")
        Map<String, Object> additional = (Map<String, Object>) fields.get("additionalAttributes");
        Assertions.assertEquals("192.168.1.1", additional.get("src_ip"));
        
        System.out.println("Transformed Fields: " + fields);
    }
}
