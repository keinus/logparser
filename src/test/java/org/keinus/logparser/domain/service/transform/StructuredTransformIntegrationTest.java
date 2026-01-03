package org.keinus.logparser.domain.service.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.model.mapping.FieldMapping;
import org.keinus.logparser.domain.model.mapping.MappingConfiguration;
import org.keinus.logparser.domain.model.mapping.SubTableRule;
import org.keinus.logparser.domain.repository.MappingRepository;
import org.keinus.logparser.infrastructure.util.id.SnowflakeIdGenerator;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
public class StructuredTransformIntegrationTest {

    @Mock
    private MappingRepository mappingRepository;
    
    private StructuredTransformService transformService;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
        ConditionEvaluator evaluator = new ConditionEvaluator();
        SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator();
        StructuredEventSerializer serializer = new StructuredEventSerializer(objectMapper);
        
        transformService = new StructuredTransformService(mappingRepository, evaluator, idGenerator, serializer);
    }

    @Test
    void testApplyToLogEvent() {
        // 1. Setup Mock Config
        MappingConfiguration config = new MappingConfiguration();
        config.setMessageType("syslog");
        config.setCommonMappings(Arrays.asList(
            new FieldMapping("src", "src_ip", null),
            new FieldMapping("dst", "dst_ip", null)
        ));
        
        SubTableRule rule = new SubTableRule();
        rule.setTargetSubTable("event_network");
        rule.setConditionExpression("['dst_port'] == 80"); // Should match
        rule.setMappings(Arrays.asList(
            new FieldMapping("bytes", "bytes_in", "0")
        ));
        config.setSubTableRules(Arrays.asList(rule));

        when(mappingRepository.findByMessageType("syslog")).thenReturn(Optional.of(config));

        // 2. Create LogEvent
        LogEvent event = new LogEvent("original log", "localhost", "syslog");
        Map<String, Object> fields = new HashMap<>();
        fields.put("src", "192.168.1.1");
        fields.put("dst", "10.0.0.1");
        fields.put("dst_port", 80); // Triggers condition
        fields.put("bytes", 1024);
        fields.put("extra", "foo"); // Unmapped
        event.setFields(fields);

        // 3. Execute
        boolean success = transformService.applyToLogEvent(event);

        // 4. Verify
        assertTrue(success);
        assertTrue(event.isTransformed());
        
        Map<String, Object> resultFields = event.getFields();
        System.out.println("Result Fields: " + resultFields);

        // Check Common
        Map<String, Object> common = (Map<String, Object>) resultFields.get("common");
        assertNotNull(common);
        assertEquals("192.168.1.1", common.get("srcIp"));
        
        // Check SubFields
        assertEquals("event_network", resultFields.get("subDomainType"));
        Map<String, Object> sub = (Map<String, Object>) resultFields.get("subFields");
        assertNotNull(sub, "SubFields is null");
        Object bytesVal = sub.get("bytes_in");
        assertNotNull(bytesVal, "bytes_in is null");
        assertEquals(1024, ((Number)bytesVal).intValue());
        
        // Check Additional Attributes
        Map<String, Object> additional = (Map<String, Object>) resultFields.get("additionalAttributes");
        System.out.println("Additional Attributes: " + additional);
        assertEquals("foo", additional.get("extra"));
        
        assertTrue(additional.containsKey("dst_port"), "additionalAttributes should contain 'dst_port'. Actual keys: " + additional.keySet());
    }
}
