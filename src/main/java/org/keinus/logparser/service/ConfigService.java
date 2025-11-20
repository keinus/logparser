package org.keinus.logparser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.keinus.logparser.config.ConfigValidator;
import org.keinus.logparser.config.InputAdapterConfig;
import org.keinus.logparser.config.OutputAdapterConfig;
import org.keinus.logparser.config.ParserAdapterConfig;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class ConfigService {

    private static final String CONFIG_FILE_PATH = "./config/config.yaml";
    private final ConfigValidator configValidator;
    private final ObjectMapper objectMapper;

    public ConfigService(ConfigValidator configValidator, ObjectMapper objectMapper) {
        this.configValidator = configValidator;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getConfig() throws FileNotFoundException {
        Yaml yaml = new Yaml();
        InputStream inputStream = new FileInputStream(CONFIG_FILE_PATH);
        return yaml.load(inputStream);
    }

    public void saveConfig(Map<String, Object> config) throws IOException {
        Yaml yaml = new Yaml();
        FileWriter writer = new FileWriter(CONFIG_FILE_PATH);
        yaml.dump(config, writer);
    }

    public Map<String, Object> addConfig(String section, Map<String, Object> newConfig) throws IOException {
        validateConfig(section, newConfig);
        Map<String, Object> config = getConfig();
        Map<String, Object> logparserConfig = (Map<String, Object>) config.get("logparser");
        List<Map<String, Object>> sectionList = (List<Map<String, Object>>) logparserConfig.get(section);
        sectionList.add(newConfig);
        saveConfig(config);
        return config;
    }

    public Map<String, Object> updateConfig(String section, int index, Map<String, Object> newConfig) throws IOException {
        validateConfig(section, newConfig);
        Map<String, Object> config = getConfig();
        Map<String, Object> logparserConfig = (Map<String, Object>) config.get("logparser");
        List<Map<String, Object>> sectionList = (List<Map<String, Object>>) logparserConfig.get(section);
        sectionList.set(index, newConfig);
        saveConfig(config);
        return config;
    }

    public Map<String, Object> deleteConfig(String section, int index) throws IOException {
        Map<String, Object> config = getConfig();
        Map<String, Object> logparserConfig = (Map<String, Object>) config.get("logparser");
        List<Map<String, Object>> sectionList = (List<Map<String, Object>>) logparserConfig.get(section);
        sectionList.remove(index);
        saveConfig(config);
        return config;
    }

    public Map<String, Object> updateCommonConfig(Map<String, Object> commonConfig) throws IOException {
        Map<String, Object> config = getConfig();
        Map<String, Object> logparserConfig = (Map<String, Object>) config.get("logparser");
        logparserConfig.putAll(commonConfig);
        saveConfig(config);
        return config;
    }

    private void validateConfig(String section, Map<String, Object> newConfig) {
        String type = (String) newConfig.get("type");
        if (type == null) {
            throw new IllegalArgumentException("Type is required");
        }

        try {
            String className = getClassName(section, type);
            Object configObject = objectMapper.convertValue(newConfig, Class.forName(className));

            ConfigValidator.ValidationResult result;
            switch (section) {
                case "input":
                    result = configValidator.validateInputAdapter((InputAdapterConfig) configObject);
                    break;
                case "output":
                    result = configValidator.validateOutputAdapter((OutputAdapterConfig) configObject);
                    break;
                case "parser":
                    result = configValidator.validateParserAdapter((ParserAdapterConfig) configObject);
                    break;
                // No validation for transform
                default:
                    return;
            }

            if (result.hasErrors()) {
                throw new IllegalArgumentException("Validation failed: " + result.getErrors());
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Invalid type: " + type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Validation failed: " + e.getMessage());
        }
    }

    private String getClassName(String section, String type) {
        return "org.keinus.logparser." + section + "." + type;
    }
}



