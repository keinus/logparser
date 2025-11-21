package org.keinus.logparser.interfaces.rest.dto.response;

public record ReloadProgressDTO(
        int progress,
        String status,
        boolean inProgress
) {}
