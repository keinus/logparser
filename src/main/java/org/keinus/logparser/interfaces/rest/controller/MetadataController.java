package org.keinus.logparser.interfaces.rest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.application.config.ConfigMetadataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/metadata")
@RequiredArgsConstructor
@Slf4j
public class MetadataController {

    private final ConfigMetadataService metadataService;

    @GetMapping("/input-adapter-types")
    public ResponseEntity<List<ConfigMetadataService.AdapterTypeInfo>> getInputAdapterTypes() {
        log.info("GET /api/v1/metadata/input-adapter-types");
        List<ConfigMetadataService.AdapterTypeInfo> types = metadataService.getInputAdapterTypes();
        return ResponseEntity.ok(types);
    }

    @GetMapping("/parser-types")
    public ResponseEntity<List<ConfigMetadataService.AdapterTypeInfo>> getParserTypes() {
        log.info("GET /api/v1/metadata/parser-types");
        List<ConfigMetadataService.AdapterTypeInfo> types = metadataService.getParserTypes();
        return ResponseEntity.ok(types);
    }

    @GetMapping("/transform-types")
    public ResponseEntity<List<ConfigMetadataService.TransformTypeInfo>> getTransformTypes() {
        log.info("GET /api/v1/metadata/transform-types");
        List<ConfigMetadataService.TransformTypeInfo> types = metadataService.getTransformTypes();
        return ResponseEntity.ok(types);
    }

    @GetMapping("/output-adapter-types")
    public ResponseEntity<List<ConfigMetadataService.AdapterTypeInfo>> getOutputAdapterTypes() {
        log.info("GET /api/v1/metadata/output-adapter-types");
        List<ConfigMetadataService.AdapterTypeInfo> types = metadataService.getOutputAdapterTypes();
        return ResponseEntity.ok(types);
    }

    @GetMapping("/input-adapter-schema/{type}")
    public ResponseEntity<ConfigMetadataService.AdapterSchema> getInputAdapterSchema(@PathVariable("type") String type) {
        log.info("GET /api/v1/metadata/input-adapter-schema/{}", type);
        ConfigMetadataService.AdapterSchema schema = metadataService.getInputAdapterSchema(type);
        return ResponseEntity.ok(schema);
    }

    @GetMapping("/parser-schema/{type}")
    public ResponseEntity<ConfigMetadataService.AdapterSchema> getParserSchema(@PathVariable("type") String type) {
        log.info("GET /api/v1/metadata/parser-schema/{}", type);
        ConfigMetadataService.AdapterSchema schema = metadataService.getParserSchema(type);
        return ResponseEntity.ok(schema);
    }

    @GetMapping("/transform-schema/{type}")
    public ResponseEntity<ConfigMetadataService.TransformSchema> getTransformSchema(@PathVariable("type") String type) {
        log.info("GET /api/v1/metadata/transform-schema/{}", type);
        ConfigMetadataService.TransformSchema schema = metadataService.getTransformSchema(type);
        return ResponseEntity.ok(schema);
    }

    @GetMapping("/output-adapter-schema/{type}")
    public ResponseEntity<ConfigMetadataService.AdapterSchema> getOutputAdapterSchema(@PathVariable("type") String type) {
        log.info("GET /api/v1/metadata/output-adapter-schema/{}", type);
        ConfigMetadataService.AdapterSchema schema = metadataService.getOutputAdapterSchema(type);
        return ResponseEntity.ok(schema);
    }

    @GetMapping("/supported-codecs")
    public ResponseEntity<List<String>> getSupportedCodecs() {
        log.info("GET /api/v1/metadata/supported-codecs");
        List<String> codecs = metadataService.getSupportedCodecs();
        return ResponseEntity.ok(codecs);
    }

    @GetMapping("/supported-http-methods")
    public ResponseEntity<List<String>> getSupportedHttpMethods() {
        log.info("GET /api/v1/metadata/supported-http-methods");
        List<String> methods = metadataService.getSupportedHttpMethods();
        return ResponseEntity.ok(methods);
    }
}
