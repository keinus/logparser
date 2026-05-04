package org.keinus.logparser.domain.configuration.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMetadataServiceTest {

    private final ConfigMetadataService service = new ConfigMetadataService();

    @Test
    void testGetInputAdapterTypes() {
        List<ConfigMetadataService.AdapterTypeInfo> types = service.getInputAdapterTypes();
        assertFalse(types.isEmpty());
        assertTrue(types.stream().anyMatch(t -> t.type().equals("TcpInputAdapter")));
    }

    @Test
    void testGetOutputAdapterTypes() {
        List<ConfigMetadataService.AdapterTypeInfo> types = service.getOutputAdapterTypes();
        assertFalse(types.isEmpty());
        assertTrue(types.stream().anyMatch(t -> t.type().equals("HttpOutputAdapter")));
    }

    @Test
    void testGetParserTypes() {
        List<ConfigMetadataService.AdapterTypeInfo> types = service.getParserTypes();
        assertFalse(types.isEmpty());
        assertTrue(types.stream().anyMatch(t -> t.type().equals("GrokParser")));
    }

    @Test
    void testGetTransformTypes() {
        List<ConfigMetadataService.TransformTypeInfo> types = service.getTransformTypes();
        assertFalse(types.isEmpty());
        assertTrue(types.stream().anyMatch(t -> t.type().equals("Filter")));
    }

    @Test
    void testGetInputAdapterSchema() {
        ConfigMetadataService.AdapterSchema schema = service.getInputAdapterSchema("TcpInputAdapter");
        assertEquals("TcpInputAdapter", schema.type());
        assertFalse(schema.fields().isEmpty());
        assertTrue(schema.fields().stream().anyMatch(f -> f.name().equals("port")));

        schema = service.getInputAdapterSchema("Unknown");
        assertTrue(schema.fields().isEmpty());
    }

    @Test
    void testGetOutputAdapterSchema() {
        ConfigMetadataService.AdapterSchema schema = service.getOutputAdapterSchema("HttpOutputAdapter");
        assertEquals("HttpOutputAdapter", schema.type());
        assertFalse(schema.fields().isEmpty());

        schema = service.getOutputAdapterSchema("Unknown");
        assertTrue(schema.fields().isEmpty());
    }

    @Test
    void testGetParserSchema() {
        ConfigMetadataService.AdapterSchema schema = service.getParserSchema("GrokParser");
        assertEquals("GrokParser", schema.type());
        assertFalse(schema.fields().isEmpty());
    }

    @Test
    void testGetTransformSchema() {
        ConfigMetadataService.TransformSchema schema = service.getTransformSchema("Filter");
        assertEquals("Filter", schema.type());
        assertFalse(schema.fields().isEmpty());
    }

    @Test
    void testSupportedOptions() {
        assertFalse(service.getSupportedCodecs().isEmpty());
        assertFalse(service.getSupportedHttpMethods().isEmpty());
    }
}
