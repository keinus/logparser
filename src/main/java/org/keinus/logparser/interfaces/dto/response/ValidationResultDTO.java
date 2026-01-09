package org.keinus.logparser.interfaces.dto.response;

import java.util.List;

public record ValidationResultDTO(
        boolean isValid,
        List<String> errors
) {}
