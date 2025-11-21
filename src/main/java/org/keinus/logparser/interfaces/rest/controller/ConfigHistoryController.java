package org.keinus.logparser.interfaces.rest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.application.config.ConfigHistoryService;
import org.keinus.logparser.infrastructure.persistence.entity.ConfigHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
@Slf4j
public class ConfigHistoryController {

    private final ConfigHistoryService historyService;

    @GetMapping
    public ResponseEntity<Page<ConfigHistoryEntity>> getAllHistory(Pageable pageable) {
        log.info("GET /api/v1/history - pageable: {}", pageable);
        Page<ConfigHistoryEntity> result = historyService.getHistoryPage(pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<List<ConfigHistoryEntity>> getEntityHistory(
            @PathVariable String entityType,
            @PathVariable Long entityId) {
        log.info("GET /api/v1/history/entity/{}/{}", entityType, entityId);
        List<ConfigHistoryEntity> history = historyService.getHistoryForEntity(entityType, entityId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/action/{action}")
    public ResponseEntity<List<ConfigHistoryEntity>> getHistoryByAction(@PathVariable String action) {
        log.info("GET /api/v1/history/action/{}", action);
        List<ConfigHistoryEntity> history = historyService.getHistoryByAction(action);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/timerange")
    public ResponseEntity<List<ConfigHistoryEntity>> getHistoryByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("GET /api/v1/history/timerange?startTime={}&endTime={}", startTime, endTime);
        List<ConfigHistoryEntity> history = historyService.getHistoryByDateRange(startTime, endTime);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/compare/{historyId1}/{historyId2}")
    public ResponseEntity<ConfigHistoryService.ConfigDiff> compareHistoryEntries(
            @PathVariable Long historyId1,
            @PathVariable Long historyId2) {
        log.info("GET /api/v1/history/compare/{}/{}", historyId1, historyId2);
        ConfigHistoryService.ConfigDiff diff = historyService.compareHistories(historyId1, historyId2);
        return ResponseEntity.ok(diff);
    }

    @PostMapping("/revert/{historyId}")
    public ResponseEntity<Map<String, String>> revertToHistory(@PathVariable Long historyId) {
        log.info("POST /api/v1/history/revert/{}", historyId);
        try {
            historyService.revertToHistory(historyId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Successfully reverted to history"
            ));
        } catch (Exception e) {
            log.error("Failed to revert to history", e);
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }
}
