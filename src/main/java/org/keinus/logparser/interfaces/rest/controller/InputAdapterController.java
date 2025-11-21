package org.keinus.logparser.interfaces.rest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.application.config.ConfigManagementService;
import org.keinus.logparser.infrastructure.persistence.entity.InputAdapterEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/input-adapters")
@RequiredArgsConstructor
@Slf4j
public class InputAdapterController {

    private final ConfigManagementService configManagementService;

    @GetMapping
    public ResponseEntity<Page<InputAdapterEntity>> getAllInputAdapters(Pageable pageable) {
        log.info("GET /api/v1/input-adapters - pageable: {}", pageable);
        Page<InputAdapterEntity> result = configManagementService.getAllInputAdapters(pageable);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<InputAdapterEntity> createInputAdapter(@RequestBody InputAdapterEntity entity) {
        log.info("POST /api/v1/input-adapters - type: {}, messagetype: {}", entity.getType(), entity.getMessagetype());
        InputAdapterEntity created = configManagementService.createInputAdapter(entity);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InputAdapterEntity> getInputAdapter(@PathVariable Long id) {
        log.info("GET /api/v1/input-adapters/{}", id);
        InputAdapterEntity entity = configManagementService.getInputAdapter(id);
        return ResponseEntity.ok(entity);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InputAdapterEntity> updateInputAdapter(
            @PathVariable Long id,
            @RequestBody InputAdapterEntity entity) {
        log.info("PUT /api/v1/input-adapters/{}", id);
        InputAdapterEntity updated = configManagementService.updateInputAdapter(id, entity);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInputAdapter(@PathVariable Long id) {
        log.info("DELETE /api/v1/input-adapters/{}", id);
        configManagementService.deleteInputAdapter(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<InputAdapterEntity>> getInputAdaptersByType(@PathVariable String type) {
        log.info("GET /api/v1/input-adapters/type/{}", type);
        List<InputAdapterEntity> result = configManagementService.getInputAdaptersByType(type);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/messagetype/{messageType}")
    public ResponseEntity<InputAdapterEntity> getInputAdapterByMessageType(@PathVariable String messageType) {
        log.info("GET /api/v1/input-adapters/messagetype/{}", messageType);
        InputAdapterEntity entity = configManagementService.getInputAdapterByMessageType(messageType);
        return ResponseEntity.ok(entity);
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<InputAdapterEntity> enableInputAdapter(@PathVariable Long id) {
        log.info("PATCH /api/v1/input-adapters/{}/enable", id);
        InputAdapterEntity entity = configManagementService.enableInputAdapter(id);
        return ResponseEntity.ok(entity);
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<InputAdapterEntity> disableInputAdapter(@PathVariable Long id) {
        log.info("PATCH /api/v1/input-adapters/{}/disable", id);
        InputAdapterEntity entity = configManagementService.disableInputAdapter(id);
        return ResponseEntity.ok(entity);
    }
}
