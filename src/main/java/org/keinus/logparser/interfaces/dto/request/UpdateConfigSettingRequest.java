package org.keinus.logparser.interfaces.dto.request;

public record UpdateConfigSettingRequest(
        String configValue,
        String dataType,
        String description
) {}
