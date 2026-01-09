package org.keinus.logparser.interfaces.dto.response;

import java.util.List;

public record PipelineIntegrityResultDTO(
        boolean isValid,
        List<String> errors,
        List<String> warnings
) {}
