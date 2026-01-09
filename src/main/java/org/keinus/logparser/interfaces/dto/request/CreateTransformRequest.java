package org.keinus.logparser.interfaces.dto.request;

import java.util.List;
import java.util.Map;

public record CreateTransformRequest(
        String type,
        String messagetype,
        Integer priority,
        List<String> filterPass,
        List<String> filterDrop,
        Map<String, Object> addProperties,
        List<String> removeProperties,
        Map<String, Object> configParams,
        Boolean enabled
) {}
