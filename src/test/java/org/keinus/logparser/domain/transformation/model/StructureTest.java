package org.keinus.logparser.domain.transformation.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.transformation.service.StructuredTransformService;
import org.keinus.logparser.infrastructure.util.SpringContextUtil;
import org.mockito.MockedStatic;

class StructureTest {

    private Structure transform;
    private StructuredTransformService structuredTransformService;

    @BeforeEach
    void setUp() {
        transform = new Structure();
        structuredTransformService = mock(StructuredTransformService.class);
    }

    @Test
    void testTransformWithService() {
        try (MockedStatic<SpringContextUtil> mockedSpring = mockStatic(SpringContextUtil.class)) {
            mockedSpring.when(() -> SpringContextUtil.getBean(StructuredTransformService.class))
                .thenReturn(structuredTransformService);

            transform.init(null);
            LogEvent event = new LogEvent("test");
            
            when(structuredTransformService.applyToLogEvent(event)).thenReturn(true);
            
            boolean result = transform.transform(event);
            
            assertTrue(result);
            verify(structuredTransformService).applyToLogEvent(event);
        }
    }

    @Test
    void testTransformWithoutService() {
        try (MockedStatic<SpringContextUtil> mockedSpring = mockStatic(SpringContextUtil.class)) {
            mockedSpring.when(() -> SpringContextUtil.getBean(StructuredTransformService.class))
                .thenThrow(new RuntimeException("Not found"));

            transform.init(null);
            LogEvent event = new LogEvent("test");
            
            boolean result = transform.transform(event);
            
            assertTrue(result); // Should pass through if service is missing
        }
    }
}
