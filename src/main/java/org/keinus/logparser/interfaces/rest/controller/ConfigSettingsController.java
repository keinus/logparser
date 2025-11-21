package org.keinus.logparser.interfaces.rest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.application.config.ConfigManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Slf4j
public class ConfigSettingsController {

    private final ConfigManagementService configManagementService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllSettings() {
        log.info("GET /api/v1/settings");
        Map<String, Object> settings = configManagementService.getAllCommonSettings();
        return ResponseEntity.ok(settings);
    }

    @PutMapping
    public ResponseEntity<Void> updateSettings(@RequestBody Map<String, Object> settings) {
        log.info("PUT /api/v1/settings - count: {}", settings.size());
        configManagementService.updateCommonSettings(settings);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{key}")
    public ResponseEntity<String> getSettingValue(@PathVariable String key) {
        log.info("GET /api/v1/settings/{}", key);
        String value = configManagementService.getConfigValue(key);
        return ResponseEntity.ok(value);
    }

    @PutMapping("/{key}")
    public ResponseEntity<Void> updateSettingValue(
            @PathVariable String key,
            @RequestBody Map<String, Object> payload) {
        log.info("PUT /api/v1/settings/{}", key);
        Object value = payload.get("value");
        String dataType = (String) payload.getOrDefault("dataType", "STRING");
        configManagementService.setConfigValue(key, value, dataType);
        return ResponseEntity.ok().build();
    }
}
