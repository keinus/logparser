package org.keinus.logparser.interfaces.dto.request;

import java.util.Map;

public record UpdateInputAdapterRequest(
        String type,
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
