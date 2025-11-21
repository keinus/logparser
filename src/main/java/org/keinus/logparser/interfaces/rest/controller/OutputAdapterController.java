package org.keinus.logparser.interfaces.rest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.application.config.ConfigManagementService;
import org.keinus.logparser.infrastructure.persistence.entity.OutputAdapterEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/output-adapters")
@RequiredArgsConstructor
@Slf4j
public class OutputAdapterController {

    private final ConfigManagementService configManagementService;

    @GetMapping
    public ResponseEntity<Page<OutputAdapterEntity>> getAllOutputAdapters(Pageable pageable) {
        log.info("GET /api/v1/output-adapters - pageable: {}", pageable);
        Page<OutputAdapterEntity> result = configManagementService.getAllOutputAdapters(pageable);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<OutputAdapterEntity> createOutputAdapter(@RequestBody OutputAdapterEntity entity) {
        log.info("POST /api/v1/output-adapters - type: {}, messagetype: {}", entity.getType(), entity.getMessagetype());
        OutputAdapterEntity created = configManagementService.createOutputAdapter(entity);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OutputAdapterEntity> getOutputAdapter(@PathVariable Long id) {
        log.info("GET /api/v1/output-adapters/{}", id);
        OutputAdapterEntity entity = configManagementService.getOutputAdapter(id);
        return ResponseEntity.ok(entity);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OutputAdapterEntity> updateOutputAdapter(
            @PathVariable Long id,
            @RequestBody OutputAdapterEntity entity) {
        log.info("PUT /api/v1/output-adapters/{}", id);
        OutputAdapterEntity updated = configManagementService.updateOutputAdapter(id, entity);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOutputAdapter(@PathVariable Long id) {
        log.info("DELETE /api/v1/output-adapters/{}", id);
        configManagementService.deleteOutputAdapter(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<OutputAdapterEntity>> getOutputAdaptersByType(@PathVariable String type) {
        log.info("GET /api/v1/output-adapters/type/{}", type);
        List<OutputAdapterEntity> result = configManagementService.getOutputAdaptersByType(type);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/messagetype/{messageType}")
    public ResponseEntity<List<OutputAdapterEntity>> getOutputAdaptersByMessageType(@PathVariable String messageType) {
        log.info("GET /api/v1/output-adapters/messagetype/{}", messageType);
        List<OutputAdapterEntity> result = configManagementService.getOutputAdaptersByMessageType(messageType);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<OutputAdapterEntity> enableOutputAdapter(@PathVariable Long id) {
        log.info("PATCH /api/v1/output-adapters/{}/enable", id);
        OutputAdapterEntity entity = configManagementService.enableOutputAdapter(id);
        return ResponseEntity.ok(entity);
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<OutputAdapterEntity> disableOutputAdapter(@PathVariable Long id) {
        log.info("PATCH /api/v1/output-adapters/{}/disable", id);
        OutputAdapterEntity entity = configManagementService.disableOutputAdapter(id);
        return ResponseEntity.ok(entity);
    }
}
