package org.keinus.logparser.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.infrastructure.persistence.entity.*;
import org.keinus.logparser.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConfigExportService {

    private final InputAdapterRepository inputAdapterRepository;
    private final ParserRepository parserRepository;
    private final TransformRepository transformRepository;
    private final OutputAdapterRepository outputAdapterRepository;
    private final ConfigSettingsRepository configSettingsRepository;

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    // ==================== Export Operations ====================

    public String exportCurrentConfigAsYaml() {
        log.info("Exporting current configuration as YAML");

        try {
            Map<String, Object> config = buildConfigMap();
            return yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to export configuration as YAML", e);
            throw new RuntimeException("Failed to export configuration as YAML", e);
        }
    }

    public String exportCurrentConfigAsJson() {
        log.info("Exporting current configuration as JSON");

        try {
            Map<String, Object> config = buildConfigMap();
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to export configuration as JSON", e);
            throw new RuntimeException("Failed to export configuration as JSON", e);
        }
    }

    // ==================== Import Operations ====================

    @Transactional
    public void importFromYaml(String yamlContent, boolean overwrite) {
        log.info("Importing configuration from YAML, overwrite={}", overwrite);

        try {
            Map<String, Object> config = yamlMapper.readValue(yamlContent, Map.class);
            importConfig(config, overwrite);
        } catch (Exception e) {
            log.error("Failed to import configuration from YAML", e);
            throw new RuntimeException("Failed to import configuration from YAML", e);
        }
    }

    @Transactional
    public void importFromJson(String jsonContent, boolean overwrite) {
        log.info("Importing configuration from JSON, overwrite={}", overwrite);

        try {
            Map<String, Object> config = jsonMapper.readValue(jsonContent, Map.class);
            importConfig(config, overwrite);
        } catch (Exception e) {
            log.error("Failed to import configuration from JSON", e);
            throw new RuntimeException("Failed to import configuration from JSON", e);
        }
    }

    @Transactional
    public void importFromFile(MultipartFile file, boolean overwrite) {
        log.info("Importing configuration from file: {}, overwrite={}", file.getOriginalFilename(), overwrite);

        try {
            String content = new String(file.getBytes());
            String filename = file.getOriginalFilename();

            if (filename != null && filename.endsWith(".yaml") || filename != null && filename.endsWith(".yml")) {
                importFromYaml(content, overwrite);
            } else if (filename != null && filename.endsWith(".json")) {
                importFromJson(content, overwrite);
            } else {
                throw new RuntimeException("Unsupported file format. Use .yaml, .yml, or .json");
            }
        } catch (Exception e) {
            log.error("Failed to import configuration from file", e);
            throw new RuntimeException("Failed to import configuration from file", e);
        }
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> buildConfigMap() {
        Map<String, Object> config = new HashMap<>();

        // Export input adapters
        List<InputAdapterEntity> inputAdapters = inputAdapterRepository.findAll();
        config.put("inputAdapters", inputAdapters);

        // Export parsers
        List<ParserEntity> parsers = parserRepository.findAll();
        config.put("parsers", parsers);

        // Export transforms
        List<TransformEntity> transforms = transformRepository.findAll();
        config.put("transforms", transforms);

        // Export output adapters
        List<OutputAdapterEntity> outputAdapters = outputAdapterRepository.findAll();
        config.put("outputAdapters", outputAdapters);

        // Export common settings
        List<ConfigSettingsEntity> settings = configSettingsRepository.findAll();
        Map<String, String> settingsMap = new HashMap<>();
        settings.forEach(s -> settingsMap.put(s.getConfigKey(), s.getConfigValue()));
        config.put("commonSettings", settingsMap);

        config.put("exportedAt", java.time.LocalDateTime.now());

        return config;
    }

    private void importConfig(Map<String, Object> config, boolean overwrite) {
        log.info("Importing configuration, overwrite={}", overwrite);

        // Validate configuration structure
        if (!config.containsKey("inputAdapters") && !config.containsKey("parsers") &&
            !config.containsKey("transforms") && !config.containsKey("outputAdapters")) {
            throw new RuntimeException("Invalid configuration format");
        }

        if (overwrite) {
            log.warn("Overwrite mode: clearing existing configuration");
            // Clear existing configuration
            inputAdapterRepository.deleteAll();
            parserRepository.deleteAll();
            transformRepository.deleteAll();
            outputAdapterRepository.deleteAll();
        }

        // Import input adapters
        if (config.containsKey("inputAdapters")) {
            List<Map<String, Object>> inputAdapters = (List<Map<String, Object>>) config.get("inputAdapters");
            for (Map<String, Object> ia : inputAdapters) {
                InputAdapterEntity entity = jsonMapper.convertValue(ia, InputAdapterEntity.class);
                entity.setId(null); // Clear ID to create new entities
                inputAdapterRepository.save(entity);
            }
            log.info("Imported {} input adapters", inputAdapters.size());
        }

        // Import parsers
        if (config.containsKey("parsers")) {
            List<Map<String, Object>> parsers = (List<Map<String, Object>>) config.get("parsers");
            for (Map<String, Object> p : parsers) {
                ParserEntity entity = jsonMapper.convertValue(p, ParserEntity.class);
                entity.setId(null);
                parserRepository.save(entity);
            }
            log.info("Imported {} parsers", parsers.size());
        }

        // Import transforms
        if (config.containsKey("transforms")) {
            List<Map<String, Object>> transforms = (List<Map<String, Object>>) config.get("transforms");
            for (Map<String, Object> t : transforms) {
                TransformEntity entity = jsonMapper.convertValue(t, TransformEntity.class);
                entity.setId(null);
                transformRepository.save(entity);
            }
            log.info("Imported {} transforms", transforms.size());
        }

        // Import output adapters
        if (config.containsKey("outputAdapters")) {
            List<Map<String, Object>> outputAdapters = (List<Map<String, Object>>) config.get("outputAdapters");
            for (Map<String, Object> oa : outputAdapters) {
                OutputAdapterEntity entity = jsonMapper.convertValue(oa, OutputAdapterEntity.class);
                entity.setId(null);
                outputAdapterRepository.save(entity);
            }
            log.info("Imported {} output adapters", outputAdapters.size());
        }

        // Import common settings
        if (config.containsKey("commonSettings")) {
            Map<String, String> settings = (Map<String, String>) config.get("commonSettings");
            for (Map.Entry<String, String> entry : settings.entrySet()) {
                ConfigSettingsEntity entity = ConfigSettingsEntity.builder()
                        .configKey(entry.getKey())
                        .configValue(entry.getValue())
                        .build();
                configSettingsRepository.save(entity);
            }
            log.info("Imported {} common settings", settings.size());
        }

        log.info("Configuration import completed");
    }
}
