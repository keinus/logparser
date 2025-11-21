package org.keinus.logparser.interfaces.rest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.application.config.ConfigManagementService;
import org.keinus.logparser.application.config.ConfigValidationService;
import org.keinus.logparser.infrastructure.persistence.entity.ParserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/parsers")
@RequiredArgsConstructor
@Slf4j
public class ParserController {

    private final ConfigManagementService configManagementService;
    private final ConfigValidationService validationService;

    @GetMapping
    public ResponseEntity<Page<ParserEntity>> getAllParsers(Pageable pageable) {
        log.info("GET /api/v1/parsers - pageable: {}", pageable);
        Page<ParserEntity> result = configManagementService.getAllParsers(pageable);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<ParserEntity> createParser(@RequestBody ParserEntity entity) {
        log.info("POST /api/v1/parsers - type: {}, messagetype: {}", entity.getType(), entity.getMessagetype());
        ParserEntity created = configManagementService.createParser(entity);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ParserEntity> getParser(@PathVariable Long id) {
        log.info("GET /api/v1/parsers/{}", id);
        ParserEntity entity = configManagementService.getParser(id);
        return ResponseEntity.ok(entity);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ParserEntity> updateParser(
            @PathVariable Long id,
            @RequestBody ParserEntity entity) {
        log.info("PUT /api/v1/parsers/{}", id);
        ParserEntity updated = configManagementService.updateParser(id, entity);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteParser(@PathVariable Long id) {
        log.info("DELETE /api/v1/parsers/{}", id);
        configManagementService.deleteParser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<ParserEntity>> getParsersByType(@PathVariable String type) {
        log.info("GET /api/v1/parsers/type/{}", type);
        List<ParserEntity> result = configManagementService.getParsersByType(type);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/messagetype/{messageType}")
    public ResponseEntity<List<ParserEntity>> getParsersByMessageType(@PathVariable String messageType) {
        log.info("GET /api/v1/parsers/messagetype/{}", messageType);
        List<ParserEntity> result = configManagementService.getParsersByMessageType(messageType);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/priority")
    public ResponseEntity<ParserEntity> updateParserPriority(
            @PathVariable Long id,
            @RequestParam Integer priority) {
        log.info("PATCH /api/v1/parsers/{}/priority?priority={}", id, priority);
        ParserEntity entity = configManagementService.updateParserPriority(id, priority);
        return ResponseEntity.ok(entity);
    }

    @PostMapping("/validate")
    public ResponseEntity<ConfigValidationService.ValidationResult> validateParser(
            @RequestBody ParserEntity entity) {
        log.info("POST /api/v1/parsers/validate - type: {}", entity.getType());
        ConfigValidationService.ValidationResult result = validationService.validateParser(entity);
        return ResponseEntity.ok(result);
    }
}
