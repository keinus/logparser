package org.keinus.logparser.interfaces.rest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.application.config.ConfigVersionService;
import org.keinus.logparser.infrastructure.persistence.entity.ConfigurationVersionEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/versions")
@RequiredArgsConstructor
@Slf4j
public class ConfigVersionController {

    private final ConfigVersionService versionService;

    @GetMapping
    public ResponseEntity<List<ConfigurationVersionEntity>> getAllVersions() {
        log.info("GET /api/v1/versions");
        List<ConfigurationVersionEntity> result = versionService.listVersions();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<ConfigurationVersionEntity> createVersion(@RequestBody Map<String, String> request) {
        String versionName = request.get("versionName");
        String description = request.get("description");
        String createdBy = request.getOrDefault("createdBy", "system");
        log.info("POST /api/v1/versions - name: {}, createdBy: {}", versionName, createdBy);
        ConfigurationVersionEntity version = versionService.createVersion(versionName, description, createdBy);
        return ResponseEntity.ok(version);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConfigurationVersionEntity> getVersion(@PathVariable Long id) {
        log.info("GET /api/v1/versions/{}", id);
        ConfigurationVersionEntity version = versionService.getVersion(id);
        return ResponseEntity.ok(version);
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ConfigurationVersionEntity> activateVersion(@PathVariable Long id) {
        log.info("POST /api/v1/versions/{}/activate", id);
        versionService.activateVersion(id);
        ConfigurationVersionEntity version = versionService.getVersion(id);
        return ResponseEntity.ok(version);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ConfigurationVersionEntity>> getVersionsByStatus(@PathVariable String status) {
        log.info("GET /api/v1/versions/status/{}", status);
        List<ConfigurationVersionEntity> versions = versionService.listVersionsByStatus(status);
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/{id}/export/json")
    public ResponseEntity<String> exportVersionAsJson(@PathVariable Long id) {
        log.info("GET /api/v1/versions/{}/export/json", id);
        String json = versionService.exportVersionAsJson(id);
        return ResponseEntity.ok(json);
    }

    @GetMapping("/{id}/export/yaml")
    public ResponseEntity<String> exportVersionAsYaml(@PathVariable Long id) {
        log.info("GET /api/v1/versions/{}/export/yaml", id);
        String yaml = versionService.exportVersionAsYaml(id);
        return ResponseEntity.ok(yaml);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVersion(@PathVariable Long id) {
        log.info("DELETE /api/v1/versions/{}", id);
        versionService.deleteVersion(id);
        return ResponseEntity.noContent().build();
    }
}
