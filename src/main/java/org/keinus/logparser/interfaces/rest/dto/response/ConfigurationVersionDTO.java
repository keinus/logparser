package org.keinus.logparser.interfaces.rest.dto.response;

import java.time.LocalDateTime;

public record ConfigurationVersionDTO(
        Long id,
        String versionName,
        String description,
        String status,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime activatedAt
) {}
