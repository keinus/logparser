package org.keinus.logparser.interfaces.dto.response;

import java.time.LocalDateTime;

public record ConfigHistoryDTO(
        Long id,
        String entityType,
        Long entityId,
        String action,
        String oldValues,
        String newValues,
        String changedBy,
        LocalDateTime createdAt
) {}
