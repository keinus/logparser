package org.keinus.logparser.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.infrastructure.persistence.entity.ConfigHistoryEntity;
import org.keinus.logparser.infrastructure.persistence.repository.ConfigHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ConfigHistoryService {

    private final ConfigHistoryRepository configHistoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== Record History ====================

    public void recordConfigChange(String entityType, Long entityId, String action,
                                   Object oldValues, Object newValues, String changedBy) {
        log.info("Recording config change: entityType={}, entityId={}, action={}, changedBy={}",
                entityType, entityId, action, changedBy);

        try {
            ConfigHistoryEntity history = ConfigHistoryEntity.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .oldValues(oldValues != null ? objectMapper.writeValueAsString(oldValues) : null)
                    .newValues(newValues != null ? objectMapper.writeValueAsString(newValues) : null)
                    .changedBy(changedBy != null ? changedBy : "system")
                    .build();

            configHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("Failed to record config change", e);
        }
    }

    // ==================== Query History ====================

    @Transactional(readOnly = true)
    public List<ConfigHistoryEntity> getHistoryForEntity(String entityType, Long entityId) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        return configHistoryRepository.findByEntityTypeAndEntityId(entityType, entityId, sort);
    }

    @Transactional(readOnly = true)
    public List<ConfigHistoryEntity> getHistoryByDateRange(LocalDateTime from, LocalDateTime to) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        return configHistoryRepository.findByCreatedAtBetween(from, to, sort);
    }

    @Transactional(readOnly = true)
    public Page<ConfigHistoryEntity> getHistoryPage(Pageable pageable) {
        return configHistoryRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<ConfigHistoryEntity> getHistoryByAction(String action) {
        return configHistoryRepository.findByAction(action);
    }

    // ==================== Revert to History ====================

    public void revertToHistory(Long historyId) {
        log.info("Reverting to history: historyId={}", historyId);

        ConfigHistoryEntity history = configHistoryRepository.findById(historyId)
                .orElseThrow(() -> new RuntimeException("History not found: " + historyId));

        // This would require additional service methods to actually apply the old values
        // Implementation depends on the specific entity type
        log.warn("Revert functionality requires integration with entity-specific services");

        // Record the revert action
        recordConfigChange(
                history.getEntityType(),
                history.getEntityId(),
                "REVERT",
                history.getNewValues(),
                history.getOldValues(),
                "system"
        );
    }

    // ==================== Compare Histories ====================

    @Transactional(readOnly = true)
    public ConfigDiff compareHistories(Long historyId1, Long historyId2) {
        log.info("Comparing histories: historyId1={}, historyId2={}", historyId1, historyId2);

        ConfigHistoryEntity history1 = configHistoryRepository.findById(historyId1)
                .orElseThrow(() -> new RuntimeException("History not found: " + historyId1));
        ConfigHistoryEntity history2 = configHistoryRepository.findById(historyId2)
                .orElseThrow(() -> new RuntimeException("History not found: " + historyId2));

        try {
            Map<String, Object> values1 = objectMapper.readValue(
                    history1.getNewValues() != null ? history1.getNewValues() : "{}",
                    Map.class
            );
            Map<String, Object> values2 = objectMapper.readValue(
                    history2.getNewValues() != null ? history2.getNewValues() : "{}",
                    Map.class
            );

            return new ConfigDiff(history1, history2, values1, values2, computeDifferences(values1, values2));
        } catch (Exception e) {
            log.error("Failed to compare histories", e);
            throw new RuntimeException("Failed to compare histories", e);
        }
    }

    private List<DiffEntry> computeDifferences(Map<String, Object> values1, Map<String, Object> values2) {
        List<DiffEntry> differences = new java.util.ArrayList<>();

        // Find added and modified fields
        for (Map.Entry<String, Object> entry : values2.entrySet()) {
            String key = entry.getKey();
            Object value2 = entry.getValue();
            Object value1 = values1.get(key);

            if (value1 == null) {
                differences.add(new DiffEntry(key, null, value2, "ADDED"));
            } else if (!value1.equals(value2)) {
                differences.add(new DiffEntry(key, value1, value2, "MODIFIED"));
            }
        }

        // Find removed fields
        for (String key : values1.keySet()) {
            if (!values2.containsKey(key)) {
                differences.add(new DiffEntry(key, values1.get(key), null, "REMOVED"));
            }
        }

        return differences;
    }

    // ==================== Inner Classes ====================

    public record ConfigDiff(
            ConfigHistoryEntity history1,
            ConfigHistoryEntity history2,
            Map<String, Object> values1,
            Map<String, Object> values2,
            List<DiffEntry> differences
    ) {}

    public record DiffEntry(
            String field,
            Object oldValue,
            Object newValue,
            String changeType
    ) {}
}
