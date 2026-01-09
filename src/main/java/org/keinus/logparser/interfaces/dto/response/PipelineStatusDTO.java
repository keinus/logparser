package org.keinus.logparser.interfaces.dto.response;

public record PipelineStatusDTO(
        String status,
        int inputAdapterCount,
        int parserCount,
        int transformCount,
        int outputAdapterCount,
        int queueSize,
        long processedMessages
) {}
