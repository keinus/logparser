package org.keinus.logparser.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CreateInputAdapterRequest(
        @NotBlank(message = "Type is required")
        String type,

        @NotBlank(message = "Message type is required")
        String messagetype,

        String host,
        Integer port,
        String path,
        String topicid,
        String bootstrapservers,
        String groupId,
        String codec,
        String pathPattern,
        Integer bufferSize,
        Integer timeoutMs,
        Boolean enabled,
        Integer workerThreads,
        Integer queueSize,
        Map<String, Object> configParams
) {}
