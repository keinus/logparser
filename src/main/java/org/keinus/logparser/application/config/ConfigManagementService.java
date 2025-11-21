package org.keinus.logparser.application.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.infrastructure.persistence.entity.*;
import org.keinus.logparser.infrastructure.persistence.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ConfigManagementService {

    private final InputAdapterRepository inputAdapterRepository;
    private final ParserRepository parserRepository;
    private final TransformRepository transformRepository;
    private final OutputAdapterRepository outputAdapterRepository;
    private final ConfigSettingsRepository configSettingsRepository;

    // ==================== InputAdapter Management ====================

    public InputAdapterEntity createInputAdapter(InputAdapterEntity entity) {
        log.info("Creating input adapter: type={}, messagetype={}", entity.getType(), entity.getMessagetype());
        return inputAdapterRepository.save(entity);
    }

    public InputAdapterEntity updateInputAdapter(Long id, InputAdapterEntity entity) {
        log.info("Updating input adapter: id={}", id);
        InputAdapterEntity existing = getInputAdapter(id);
        entity.setId(existing.getId());
        entity.setVersion(existing.getVersion());
        return inputAdapterRepository.save(entity);
    }

    public void deleteInputAdapter(Long id) {
        log.info("Deleting input adapter: id={}", id);
        inputAdapterRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public InputAdapterEntity getInputAdapter(Long id) {
        return inputAdapterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("InputAdapter not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<InputAdapterEntity> getAllInputAdapters(Pageable pageable) {
        return inputAdapterRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<InputAdapterEntity> getInputAdaptersByType(String type) {
        return inputAdapterRepository.findByType(type);
    }

    @Transactional(readOnly = true)
    public InputAdapterEntity getInputAdapterByMessageType(String messageType) {
        return inputAdapterRepository.findByMessagetype(messageType)
                .orElseThrow(() -> new RuntimeException("InputAdapter not found for messageType: " + messageType));
    }

    public InputAdapterEntity enableInputAdapter(Long id) {
        log.info("Enabling input adapter: id={}", id);
        InputAdapterEntity entity = getInputAdapter(id);
        entity.setEnabled(true);
        return inputAdapterRepository.save(entity);
    }

    public InputAdapterEntity disableInputAdapter(Long id) {
        log.info("Disabling input adapter: id={}", id);
        InputAdapterEntity entity = getInputAdapter(id);
        entity.setEnabled(false);
        return inputAdapterRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<InputAdapterEntity> getEnabledInputAdapters() {
        return inputAdapterRepository.findByEnabledTrue();
    }

    // ==================== Parser Management ====================

    public ParserEntity createParser(ParserEntity entity) {
        log.info("Creating parser: type={}, messagetype={}", entity.getType(), entity.getMessagetype());
        return parserRepository.save(entity);
    }

    public ParserEntity updateParser(Long id, ParserEntity entity) {
        log.info("Updating parser: id={}", id);
        ParserEntity existing = getParser(id);
        entity.setId(existing.getId());
        entity.setVersion(existing.getVersion());
        return parserRepository.save(entity);
    }

    public void deleteParser(Long id) {
        log.info("Deleting parser: id={}", id);
        parserRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public ParserEntity getParser(Long id) {
        return parserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Parser not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ParserEntity> getAllParsers(Pageable pageable) {
        return parserRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<ParserEntity> getParsersByType(String type) {
        return parserRepository.findByType(type);
    }

    @Transactional(readOnly = true)
    public List<ParserEntity> getParsersByMessageType(String messageType) {
        return parserRepository.findByMessagetypeOrderByPriorityAsc(messageType);
    }

    public ParserEntity updateParserPriority(Long id, Integer newPriority) {
        log.info("Updating parser priority: id={}, newPriority={}", id, newPriority);
        ParserEntity entity = getParser(id);
        entity.setPriority(newPriority);
        return parserRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ParserEntity> getEnabledParsers() {
        return parserRepository.findByEnabledTrue();
    }

    // ==================== Transform Management ====================

    public TransformEntity createTransform(TransformEntity entity) {
        log.info("Creating transform: type={}, messagetype={}", entity.getType(), entity.getMessagetype());
        return transformRepository.save(entity);
    }

    public TransformEntity updateTransform(Long id, TransformEntity entity) {
        log.info("Updating transform: id={}", id);
        TransformEntity existing = getTransform(id);
        entity.setId(existing.getId());
        entity.setVersion(existing.getVersion());
        return transformRepository.save(entity);
    }

    public void deleteTransform(Long id) {
        log.info("Deleting transform: id={}", id);
        transformRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public TransformEntity getTransform(Long id) {
        return transformRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transform not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<TransformEntity> getAllTransforms(Pageable pageable) {
        return transformRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<TransformEntity> getTransformsByType(String type) {
        return transformRepository.findByType(type);
    }

    @Transactional(readOnly = true)
    public List<TransformEntity> getTransformsByMessageType(String messageType) {
        return transformRepository.findByMessagetypeOrderByPriorityAsc(messageType);
    }

    public TransformEntity updateTransformPriority(Long id, Integer newPriority) {
        log.info("Updating transform priority: id={}, newPriority={}", id, newPriority);
        TransformEntity entity = getTransform(id);
        entity.setPriority(newPriority);
        return transformRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<TransformEntity> getEnabledTransforms() {
        return transformRepository.findByEnabledTrue();
    }

    // ==================== OutputAdapter Management ====================

    public OutputAdapterEntity createOutputAdapter(OutputAdapterEntity entity) {
        log.info("Creating output adapter: type={}, messagetype={}", entity.getType(), entity.getMessagetype());
        return outputAdapterRepository.save(entity);
    }

    public OutputAdapterEntity updateOutputAdapter(Long id, OutputAdapterEntity entity) {
        log.info("Updating output adapter: id={}", id);
        OutputAdapterEntity existing = getOutputAdapter(id);
        entity.setId(existing.getId());
        entity.setVersion(existing.getVersion());
        return outputAdapterRepository.save(entity);
    }

    public void deleteOutputAdapter(Long id) {
        log.info("Deleting output adapter: id={}", id);
        outputAdapterRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public OutputAdapterEntity getOutputAdapter(Long id) {
        return outputAdapterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OutputAdapter not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<OutputAdapterEntity> getAllOutputAdapters(Pageable pageable) {
        return outputAdapterRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<OutputAdapterEntity> getOutputAdaptersByType(String type) {
        return outputAdapterRepository.findByType(type);
    }

    @Transactional(readOnly = true)
    public List<OutputAdapterEntity> getOutputAdaptersByMessageType(String messageType) {
        return outputAdapterRepository.findByMessagetype(messageType);
    }

    public OutputAdapterEntity enableOutputAdapter(Long id) {
        log.info("Enabling output adapter: id={}", id);
        OutputAdapterEntity entity = getOutputAdapter(id);
        entity.setEnabled(true);
        return outputAdapterRepository.save(entity);
    }

    public OutputAdapterEntity disableOutputAdapter(Long id) {
        log.info("Disabling output adapter: id={}", id);
        OutputAdapterEntity entity = getOutputAdapter(id);
        entity.setEnabled(false);
        return outputAdapterRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<OutputAdapterEntity> getEnabledOutputAdapters() {
        return outputAdapterRepository.findByEnabledTrue();
    }

    // ==================== Common Settings Management ====================

    public void updateCommonSettings(Map<String, Object> settings) {
        log.info("Updating common settings: count={}", settings.size());
        settings.forEach((key, value) -> {
            setConfigValue(key, value, determineDataType(value));
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAllCommonSettings() {
        return configSettingsRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ConfigSettingsEntity::getConfigKey,
                        entity -> parseValue(entity.getConfigValue(), entity.getDataType())
                ));
    }

    @Transactional(readOnly = true)
    public String getConfigValue(String key) {
        return configSettingsRepository.findByConfigKey(key)
                .map(ConfigSettingsEntity::getConfigValue)
                .orElse(null);
    }

    public void setConfigValue(String key, Object value, String dataType) {
        log.info("Setting config value: key={}, dataType={}", key, dataType);
        ConfigSettingsEntity entity = configSettingsRepository.findByConfigKey(key)
                .orElse(ConfigSettingsEntity.builder()
                        .configKey(key)
                        .build());

        entity.setConfigValue(value != null ? value.toString() : null);
        entity.setDataType(dataType);
        configSettingsRepository.save(entity);
    }

    // ==================== Helper Methods ====================

    private String determineDataType(Object value) {
        if (value == null) return "STRING";
        if (value instanceof Integer) return "INTEGER";
        if (value instanceof Long) return "LONG";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof Double || value instanceof Float) return "DOUBLE";
        return "STRING";
    }

    private Object parseValue(String value, String dataType) {
        if (value == null) return null;
        try {
            return switch (dataType) {
                case "INTEGER" -> Integer.parseInt(value);
                case "LONG" -> Long.parseLong(value);
                case "BOOLEAN" -> Boolean.parseBoolean(value);
                case "DOUBLE" -> Double.parseDouble(value);
                default -> value;
            };
        } catch (Exception e) {
            log.warn("Failed to parse value: value={}, dataType={}", value, dataType, e);
            return value;
        }
    }
}
