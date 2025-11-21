package org.keinus.logparser.infrastructure.config;

import java.util.List;
import jakarta.annotation.PostConstruct;

import org.keinus.logparser.domain.configuration.model.InputAdapterConfig;
import org.keinus.logparser.domain.configuration.model.OutputAdapterConfig;
import org.keinus.logparser.domain.configuration.model.ParserAdapterConfig;
import org.keinus.logparser.domain.configuration.model.TransformConfig;
import org.keinus.logparser.domain.configuration.service.ConfigValidator;
import org.keinus.logparser.infrastructure.util.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.util.CollectionUtils;

import lombok.Data;

/**
 * ETL 파이프라인의 모든 설정을 관리하는 중앙 설정 클래스입니다.
 * 타입 안전한 설정 클래스를 사용합니다.
 */
@Configuration
@ConfigurationProperties(prefix = "logparser")
@PropertySource(value = "file:./config/config.yaml", factory = YamlPropertySourceFactory.class)
@Data
public class ApplicationProperties {

    // === 타입 안전한 설정 ===
    private List<InputAdapterConfig> input;
    private List<OutputAdapterConfig> output;
    private List<ParserAdapterConfig> parser;
    private List<TransformConfig> transform;
    private int parserThreads;
    private long flushInterval;

    // === 설정 검증기 ===
    private final ConfigValidator configValidator;

    @PostConstruct
    public void validateProperties() {
        validateBasicProperties();
        validateRequiredConfigs();
        validateAdapters();
    }

    private void validateBasicProperties() {
        if (parserThreads <= 0) {
            throw new IllegalArgumentException("Parser threads must be greater than zero.");
        }
        if (flushInterval <= 0) {
            throw new IllegalArgumentException("Flush interval must be greater than zero.");
        }
    }

    private void validateRequiredConfigs() {
        if (CollectionUtils.isEmpty(input)) {
            throw new IllegalArgumentException("Input configuration cannot be empty.");
        }
        if (CollectionUtils.isEmpty(output)) {
            throw new IllegalArgumentException("Output configuration cannot be empty.");
        }
        if (CollectionUtils.isEmpty(parser)) {
            throw new IllegalArgumentException("Parser configuration cannot be empty.");
        }
    }

    private void validateAdapters() {
        if (configValidator == null) {
            return;
        }
        validateAdapterList(input, "Input adapter configuration validation failed", configValidator::validateInputAdapter);
        validateAdapterList(output, "Output adapter configuration validation failed", configValidator::validateOutputAdapter);
        validateAdapterList(parser, "Parser configuration validation failed", configValidator::validateParserAdapter);
    }

    private <T> void validateAdapterList(List<T> configs, String errorMessage, java.util.function.Function<T, ConfigValidator.ValidationResult> validator) {
        for (T config : configs) {
            ConfigValidator.ValidationResult result = validator.apply(config);
            if (result.hasErrors()) {
                result.logResults();
                throw new IllegalArgumentException(errorMessage);
            }
        }
    }

}

