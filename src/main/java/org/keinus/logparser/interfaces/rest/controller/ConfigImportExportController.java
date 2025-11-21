package org.keinus.logparser.interfaces.rest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.application.config.ConfigExportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@Slf4j
public class ConfigImportExportController {

    private final ConfigExportService exportService;

    @GetMapping("/export/yaml")
    public ResponseEntity<String> exportAsYaml() {
        log.info("GET /api/v1/config/export/yaml");
        String yaml = exportService.exportCurrentConfigAsYaml();
        return ResponseEntity.ok(yaml);
    }

    @GetMapping("/export/json")
    public ResponseEntity<String> exportAsJson() {
        log.info("GET /api/v1/config/export/json");
        String json = exportService.exportCurrentConfigAsJson();
        return ResponseEntity.ok(json);
    }

    @PostMapping("/import/yaml")
    public ResponseEntity<Map<String, String>> importFromYaml(@RequestBody Map<String, Object> request) {
        log.info("POST /api/v1/config/import/yaml");
        try {
            String yamlContent = (String) request.get("content");
            boolean overwrite = (Boolean) request.getOrDefault("overwrite", false);
            exportService.importFromYaml(yamlContent, overwrite);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Configuration imported successfully from YAML"
            ));
        } catch (Exception e) {
            log.error("Failed to import configuration from YAML", e);
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/import/json")
    public ResponseEntity<Map<String, String>> importFromJson(@RequestBody Map<String, Object> request) {
        log.info("POST /api/v1/config/import/json");
        try {
            String jsonContent = (String) request.get("content");
            boolean overwrite = (Boolean) request.getOrDefault("overwrite", false);
            exportService.importFromJson(jsonContent, overwrite);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Configuration imported successfully from JSON"
            ));
        } catch (Exception e) {
            log.error("Failed to import configuration from JSON", e);
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/validate/yaml")
    public ResponseEntity<Map<String, Object>> validateYaml(@RequestBody Map<String, String> request) {
        log.info("POST /api/v1/config/validate/yaml");
        try {
            String yamlContent = request.get("content");
            // Basic validation - just try to parse it
            exportService.importFromYaml(yamlContent, false);
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "errors", java.util.List.of()
            ));
        } catch (Exception e) {
            log.error("Failed to validate YAML", e);
            return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "errors", java.util.List.of(e.getMessage())
            ));
        }
    }

    @PostMapping("/validate/json")
    public ResponseEntity<Map<String, Object>> validateJson(@RequestBody Map<String, String> request) {
        log.info("POST /api/v1/config/validate/json");
        try {
            String jsonContent = request.get("content");
            // Basic validation - just try to parse it
            exportService.importFromJson(jsonContent, false);
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "errors", java.util.List.of()
            ));
        } catch (Exception e) {
            log.error("Failed to validate JSON", e);
            return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "errors", java.util.List.of(e.getMessage())
            ));
        }
    }
}
