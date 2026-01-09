package org.keinus.logparser.domain.configuration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.infrastructure.persistence.entity.*;
import org.keinus.logparser.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConfigValidationService {

    private final InputAdapterRepository inputAdapterRepository;
    private final ParserRepository parserRepository;
    private final TransformRepository transformRepository;
    private final OutputAdapterRepository outputAdapterRepository;

    private final List<ValidationError> validationErrors = new ArrayList<>();

    // ==================== Individual Validation ====================

    public ValidationResult validateInputAdapter(InputAdapterEntity entity) {
        List<String> errors = new ArrayList<>();

        if (entity.getType() == null || entity.getType().trim().isEmpty()) {
            errors.add("Type is required");
        }

        if (entity.getMessagetype() == null || entity.getMessagetype().trim().isEmpty()) {
            errors.add("Message type is required");
        }

        // Type-specific validation
        switch (entity.getType() != null ? entity.getType().toLowerCase() : "") {
            case "tcp", "udp" -> {
                if (entity.getHost() == null || entity.getPort() == null) {
                    errors.add("Host and port are required for TCP/UDP");
                }
            }
            case "http" -> {
                if (entity.getPort() == null) {
                    errors.add("Port is required for HTTP");
                }
            }
            case "kafka" -> {
                if (entity.getBootstrapservers() == null || entity.getTopicid() == null) {
                    errors.add("Bootstrap servers and topic are required for Kafka");
                }
            }
            case "file" -> {
                if (entity.getPath() == null) {
                    errors.add("Path is required for File");
                }
            }
            default -> {
                break;
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    public ValidationResult validateParser(ParserEntity entity) {
        List<String> errors = new ArrayList<>();

        if (entity.getType() == null || entity.getType().trim().isEmpty()) {
            errors.add("Type is required");
        }

        if (entity.getMessagetype() == null || entity.getMessagetype().trim().isEmpty()) {
            errors.add("Message type is required");
        }

        // Type-specific validation
        if ("grok".equalsIgnoreCase(entity.getType()) || "regex".equalsIgnoreCase(entity.getType())) {
            if (entity.getParam() == null || entity.getParam().trim().isEmpty()) {
                errors.add("Pattern parameter is required for " + entity.getType());
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    public ValidationResult validateTransform(TransformEntity entity) {
        List<String> errors = new ArrayList<>();

        if (entity.getType() == null || entity.getType().trim().isEmpty()) {
            errors.add("Type is required");
        }

        if (entity.getMessagetype() == null || entity.getMessagetype().trim().isEmpty()) {
            errors.add("Message type is required");
        }

        // Type-specific validation
        switch (entity.getType() != null ? entity.getType().toLowerCase() : "") {
            case "filter" -> {
                if ((entity.getFilterPass() == null || entity.getFilterPass().trim().isEmpty()) &&
                    (entity.getFilterDrop() == null || entity.getFilterDrop().trim().isEmpty())) {
                    errors.add("Either filterPass or filterDrop is required for Filter");
                }
            }
            case "add_property" -> {
                if (entity.getAddProperties() == null || entity.getAddProperties().trim().isEmpty()) {
                    errors.add("Add properties is required for AddProperty");
                }
            }
            case "remove_property" -> {
                if (entity.getRemoveProperties() == null || entity.getRemoveProperties().trim().isEmpty()) {
                    errors.add("Remove properties is required for RemoveProperty");
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    public ValidationResult validateOutputAdapter(OutputAdapterEntity entity) {
        List<String> errors = new ArrayList<>();

        if (entity.getType() == null || entity.getType().trim().isEmpty()) {
            errors.add("Type is required");
        }

        if (entity.getMessagetype() == null || entity.getMessagetype().trim().isEmpty()) {
            errors.add("Message type is required");
        }

        // Type-specific validation (전체 클래스명 사용)
        switch (entity.getType() != null ? entity.getType() : "") {
            case "TcpOutputAdapter" -> {
                if (entity.getHost() == null || entity.getPort() == null) {
                    errors.add("Host and port are required for TcpOutputAdapter");
                }
            }
            case "HttpOutputAdapter" -> {
                if (entity.getUrl() == null || entity.getUrl().trim().isEmpty()) {
                    errors.add("URL is required for HttpOutputAdapter");
                }
            }
            case "KafkaOutputAdapter" -> {
                if (entity.getBootstrapservers() == null || entity.getTopicid() == null) {
                    errors.add("Bootstrap servers and topic are required for KafkaOutputAdapter");
                }
            }
            case "OpenSearchOutputAdapter" -> {
                if (entity.getUrl() == null || entity.getUrl().trim().isEmpty()) {
                    errors.add("URL is required for OpenSearchOutputAdapter");
                }
                if (entity.getIndexTemplate() == null || entity.getIndexTemplate().trim().isEmpty()) {
                    errors.add("Index is required for OpenSearchOutputAdapter");
                }
            }
            case "RabbitMQAdapter" -> {
                if (entity.getHost() == null || entity.getHost().trim().isEmpty()) {
                    errors.add("Host is required for RabbitMQAdapter");
                }
                if (entity.getExchange() == null || entity.getExchange().trim().isEmpty()) {
                    errors.add("Exchange is required for RabbitMQAdapter");
                }
                if (entity.getRoutingkey() == null || entity.getRoutingkey().trim().isEmpty()) {
                    errors.add("Routing key is required for RabbitMQAdapter");
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    // ==================== Pipeline Integrity Validation ====================

    public PipelineIntegrityResult validatePipelineIntegrity() {
        log.info("Validating pipeline integrity");
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Get all entities
        List<InputAdapterEntity> inputAdapters = inputAdapterRepository.findAll();
        List<ParserEntity> parsers = parserRepository.findAll();
        List<OutputAdapterEntity> outputAdapters = outputAdapterRepository.findAll();

        // If DB is completely empty, return valid with warnings (allow runtime configuration)
        if (inputAdapters.isEmpty() && parsers.isEmpty() && outputAdapters.isEmpty()) {
            log.info("Database is empty - pipeline will not start until configuration is created");
            warnings.add("Database is empty - pipeline will not start");
            warnings.add("Please configure input adapters, parsers, and output adapters");
            return new PipelineIntegrityResult(true, errors, warnings);
        }

        // Collect all message types
        Set<String> inputMessageTypes = new HashSet<>();
        Set<String> parserMessageTypes = new HashSet<>();
        Set<String> outputMessageTypes = new HashSet<>();

        inputAdapters.forEach(ia -> inputMessageTypes.add(ia.getMessagetype()));
        parsers.forEach(p -> parserMessageTypes.add(p.getMessagetype()));
        outputAdapters.forEach(oa -> outputMessageTypes.add(oa.getMessagetype()));

        // Validate: Each input message type should have at least one parser (warning only)
        for (String inputMsgType : inputMessageTypes) {
            if (!parserMessageTypes.contains(inputMsgType)) {
                warnings.add(String.format("Input message type '%s' has no corresponding parser - will pass through", inputMsgType));
            }
        }

        // Validate: Each parser message type should have at least one output
        for (String parserMsgType : parserMessageTypes) {
            if (!outputMessageTypes.contains(parserMsgType)) {
                warnings.add(String.format("Parser message type '%s' has no corresponding output adapter", parserMsgType));
            }
        }

        // Check for orphaned output adapters
        for (String outputMsgType : outputMessageTypes) {
            if (!parserMessageTypes.contains(outputMsgType)) {
                warnings.add(String.format("Output message type '%s' has no corresponding parser", outputMsgType));
            }
        }

        // Check for enabled status consistency
        long enabledInputs = inputAdapters.stream().filter(InputAdapterEntity::getEnabled).count();
        long enabledOutputs = outputAdapters.stream().filter(OutputAdapterEntity::getEnabled).count();

        if (enabledInputs == 0) {
            warnings.add("No input adapters are enabled");
        }
        if (enabledOutputs == 0) {
            warnings.add("No output adapters are enabled");
        }

        boolean isValid = errors.isEmpty();
        log.info("Pipeline integrity validation completed: valid={}, errors={}, warnings={}",
                isValid, errors.size(), warnings.size());

        return new PipelineIntegrityResult(isValid, errors, warnings);
    }

    // ==================== Error Management ====================

    public List<ValidationError> getAllValidationErrors() {
        return new ArrayList<>(validationErrors);
    }

    public Map<String, List<ValidationError>> getErrorsByEntity() {
        Map<String, List<ValidationError>> errorsByEntity = new HashMap<>();
        for (ValidationError error : validationErrors) {
            errorsByEntity.computeIfAbsent(error.entityType(), k -> new ArrayList<>()).add(error);
        }
        return errorsByEntity;
    }

    public void clearValidationErrors() {
        validationErrors.clear();
    }

    // ==================== Inner Classes ====================

    public record ValidationResult(boolean isValid, List<String> errors) {}

    public record PipelineIntegrityResult(boolean isValid, List<String> errors, List<String> warnings) {}

    public record ValidationError(String entityType, Long entityId, String field, String message) {}
}
