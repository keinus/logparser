package org.keinus.logparser.interfaces.dto.request;

import java.util.Map;

public record CreateParserRequest(
        String type,
        String messagetype,
        Map<String, Object> param,
        Integer priority,
        Boolean enabled,
        Boolean continueOnFailure
) {}
