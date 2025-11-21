package org.keinus.logparser.interfaces.rest.dto.request;

public record UpdateConfigSettingRequest(
        String configValue,
        String dataType,
        String description
) {}
