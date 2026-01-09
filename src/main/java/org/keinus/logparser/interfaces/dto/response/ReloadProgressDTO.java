package org.keinus.logparser.interfaces.dto.response;

public record ReloadProgressDTO(
        int progress,
        String status,
        boolean inProgress
) {}
