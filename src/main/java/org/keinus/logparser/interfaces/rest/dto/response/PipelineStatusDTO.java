package org.keinus.logparser.interfaces.rest.dto.response;

public record PipelineStatusDTO(
        String status,
        int inputAdapterCount,
        int parserCount,
        int transformCount,
        int outputAdapterCount,
        int queueSize,
        long processedMessages
) {}
