package org.keinus.logparser.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.infrastructure.persistence.entity.*;
import org.keinus.logparser.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ConfigVersionService {

    private final ConfigurationVersionRepository versionRepository;
    private final InputAdapterRepository inputAdapterRepository;
    private final ParserRepository parserRepository;
    private final TransformRepository transformRepository;
    private final OutputAdapterRepository outputAdapterRepository;
    private final ConfigSettingsRepository configSettingsRepository;
    private final PipelineReloadService pipelineReloadService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final YAMLMapper yamlMapper = new YAMLMapper();

    // ==================== Version Management ====================

    public ConfigurationVersionEntity createVersion(String versionName, String description, String createdBy) {
        log.info("Creating configuration version: versionName={}, createdBy={}", versionName, createdBy);

        try {
            // Capture current configuration
            List<InputAdapterEntity> inputAdapters = inputAdapterRepository.findAll();
            List<ParserEntity> parsers = parserRepository.findAll();
            List<TransformEntity> transforms = transformRepository.findAll();
            List<OutputAdapterEntity> outputAdapters = outputAdapterRepository.findAll();
            List<ConfigSettingsEntity> settings = configSettingsRepository.findAll();

            ConfigurationVersionEntity version = ConfigurationVersionEntity.builder()
                    .versionName(versionName)
                    .description(description)
                    .inputAdapters(objectMapper.writeValueAsString(inputAdapters))
                    .parsers(objectMapper.writeValueAsString(parsers))
                    .transforms(objectMapper.writeValueAsString(transforms))
                    .outputAdapters(objectMapper.writeValueAsString(outputAdapters))
                    .commonSettings(objectMapper.writeValueAsString(settings))
                    .status("DRAFT")
                    .createdBy(createdBy)
                    .build();

            return versionRepository.save(version);
        } catch (Exception e) {
            log.error("Failed to create version", e);
            throw new RuntimeException("Failed to create version", e);
        }
    }

    @Transactional(readOnly = true)
    public ConfigurationVersionEntity getVersion(Long versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new RuntimeException("Version not found: " + versionId));
    }

    public void activateVersion(Long versionId) {
        log.info("Activating version: versionId={}", versionId);

        ConfigurationVersionEntity version = getVersion(versionId);

        try {
            // Deactivate all other versions
            List<ConfigurationVersionEntity> activeVersions = versionRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
            for (ConfigurationVersionEntity activeVersion : activeVersions) {
                activeVersion.setStatus("ARCHIVED");
                versionRepository.save(activeVersion);
            }

            // Activate this version
            version.setStatus("ACTIVE");
            version.setActivatedAt(LocalDateTime.now());
            versionRepository.save(version);

            // Apply the configuration from this version
            log.info("Reloading pipeline configuration for activated version: {}", version.getVersionName());
            pipelineReloadService.reloadConfiguration();
            log.info("Pipeline configuration reloaded successfully");

        } catch (Exception e) {
            log.error("Failed to activate version", e);
            throw new RuntimeException("Failed to activate version", e);
        }
    }

    @Transactional(readOnly = true)
    public List<ConfigurationVersionEntity> listVersions() {
        return versionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ConfigurationVersionEntity> listVersionsByStatus(String status) {
        return versionRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public void deleteVersion(Long versionId) {
        log.info("Deleting version: versionId={}", versionId);
        ConfigurationVersionEntity version = getVersion(versionId);

        if ("ACTIVE".equals(version.getStatus())) {
            throw new RuntimeException("Cannot delete active version");
        }

        versionRepository.deleteById(versionId);
    }

    // ==================== Export ====================

    @Transactional(readOnly = true)
    public String exportVersionAsYaml(Long versionId) {
        log.info("Exporting version as YAML: versionId={}", versionId);
        ConfigurationVersionEntity version = getVersion(versionId);

        try {
            Map<String, Object> export = new HashMap<>();
            export.put("versionName", version.getVersionName());
            export.put("description", version.getDescription());
            export.put("status", version.getStatus());
            export.put("createdBy", version.getCreatedBy());
            export.put("createdAt", version.getCreatedAt());
            export.put("activatedAt", version.getActivatedAt());

            export.put("inputAdapters", objectMapper.readValue(version.getInputAdapters(), List.class));
            export.put("parsers", objectMapper.readValue(version.getParsers(), List.class));
            export.put("transforms", objectMapper.readValue(version.getTransforms(), List.class));
            export.put("outputAdapters", objectMapper.readValue(version.getOutputAdapters(), List.class));
            export.put("commonSettings", objectMapper.readValue(version.getCommonSettings(), List.class));

            return yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(export);
        } catch (Exception e) {
            log.error("Failed to export version as YAML", e);
            throw new RuntimeException("Failed to export version as YAML", e);
        }
    }

    @Transactional(readOnly = true)
    public String exportVersionAsJson(Long versionId) {
        log.info("Exporting version as JSON: versionId={}", versionId);
        ConfigurationVersionEntity version = getVersion(versionId);

        try {
            Map<String, Object> export = new HashMap<>();
            export.put("versionName", version.getVersionName());
            export.put("description", version.getDescription());
            export.put("status", version.getStatus());
            export.put("createdBy", version.getCreatedBy());
            export.put("createdAt", version.getCreatedAt());
            export.put("activatedAt", version.getActivatedAt());

            export.put("inputAdapters", objectMapper.readValue(version.getInputAdapters(), List.class));
            export.put("parsers", objectMapper.readValue(version.getParsers(), List.class));
            export.put("transforms", objectMapper.readValue(version.getTransforms(), List.class));
            export.put("outputAdapters", objectMapper.readValue(version.getOutputAdapters(), List.class));
            export.put("commonSettings", objectMapper.readValue(version.getCommonSettings(), List.class));

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(export);
        } catch (Exception e) {
            log.error("Failed to export version as JSON", e);
            throw new RuntimeException("Failed to export version as JSON", e);
        }
    }

    // ==================== Compare Versions ====================

    @Transactional(readOnly = true)
    public VersionDiff compareVersions(Long versionId1, Long versionId2) {
        log.info("Comparing versions: versionId1={}, versionId2={}", versionId1, versionId2);

        ConfigurationVersionEntity version1 = getVersion(versionId1);
        ConfigurationVersionEntity version2 = getVersion(versionId2);

        try {
            List<String> differences = new java.util.ArrayList<>();

            // Compare input adapters
            List<?> inputAdapters1 = objectMapper.readValue(version1.getInputAdapters(), List.class);
            List<?> inputAdapters2 = objectMapper.readValue(version2.getInputAdapters(), List.class);
            if (!inputAdapters1.equals(inputAdapters2)) {
                differences.add("Input adapters differ: " + inputAdapters1.size() + " vs " + inputAdapters2.size());
            }

            // Compare parsers
            List<?> parsers1 = objectMapper.readValue(version1.getParsers(), List.class);
            List<?> parsers2 = objectMapper.readValue(version2.getParsers(), List.class);
            if (!parsers1.equals(parsers2)) {
                differences.add("Parsers differ: " + parsers1.size() + " vs " + parsers2.size());
            }

            // Compare transforms
            List<?> transforms1 = objectMapper.readValue(version1.getTransforms(), List.class);
            List<?> transforms2 = objectMapper.readValue(version2.getTransforms(), List.class);
            if (!transforms1.equals(transforms2)) {
                differences.add("Transforms differ: " + transforms1.size() + " vs " + transforms2.size());
            }

            // Compare output adapters
            List<?> outputAdapters1 = objectMapper.readValue(version1.getOutputAdapters(), List.class);
            List<?> outputAdapters2 = objectMapper.readValue(version2.getOutputAdapters(), List.class);
            if (!outputAdapters1.equals(outputAdapters2)) {
                differences.add("Output adapters differ: " + outputAdapters1.size() + " vs " + outputAdapters2.size());
            }

            return new VersionDiff(version1, version2, differences);
        } catch (Exception e) {
            log.error("Failed to compare versions", e);
            throw new RuntimeException("Failed to compare versions", e);
        }
    }

    // ==================== Inner Classes ====================

    public record VersionDiff(
            ConfigurationVersionEntity version1,
            ConfigurationVersionEntity version2,
            List<String> differences
    ) {}
}
