package org.keinus.logparser.interfaces.rest.dto.request;

public record CreateVersionRequest(
        String versionName,
        String description,
        String createdBy
) {}
