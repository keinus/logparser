package org.keinus.logparser.interfaces.rest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.application.config.PipelineReloadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/pipeline")
@RequiredArgsConstructor
@Slf4j
public class PipelineController {

    private final PipelineReloadService pipelineReloadService;

    @GetMapping("/status")
    public ResponseEntity<PipelineReloadService.PipelineStatusInfo> getPipelineStatus() {
        log.info("GET /api/v1/pipeline/status");
        PipelineReloadService.PipelineStatusInfo status = pipelineReloadService.getPipelineStatus();
        return ResponseEntity.ok(status);
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reloadConfiguration() {
        log.info("POST /api/v1/pipeline/reload");
        try {
            pipelineReloadService.reloadConfiguration();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Configuration reloaded successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to reload configuration", e);
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/validate-and-reload")
    public ResponseEntity<Map<String, String>> validateAndReload() {
        log.info("POST /api/v1/pipeline/validate-and-reload");
        try {
            pipelineReloadService.validateAndReload();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Configuration validated and reloaded successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to validate and reload configuration", e);
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/restart")
    public ResponseEntity<Map<String, String>> restartPipeline() {
        log.info("POST /api/v1/pipeline/restart");
        try {
            pipelineReloadService.restartPipeline();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Pipeline restarted successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to restart pipeline", e);
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/reload-progress")
    public ResponseEntity<PipelineReloadService.ReloadProgress> getReloadProgress() {
        log.info("GET /api/v1/pipeline/reload-progress");
        PipelineReloadService.ReloadProgress progress = pipelineReloadService.getReloadProgress();
        return ResponseEntity.ok(progress);
    }

    @PostMapping("/cancel-reload")
    public ResponseEntity<Map<String, String>> cancelReload() {
        log.info("POST /api/v1/pipeline/cancel-reload");
        try {
            pipelineReloadService.cancelReload();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Reload cancelled successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to cancel reload", e);
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }
}
