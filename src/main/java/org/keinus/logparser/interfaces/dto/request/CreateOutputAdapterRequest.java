package org.keinus.logparser.interfaces.dto.request;

import java.util.Map;

public record CreateOutputAdapterRequest(
        String type,
        String messagetype,
        String host,
        Integer port,
        String url,
        String method,
        Map<String, String> headers,
        String topicid,
        String bootstrapservers,
        String key,
        String indexTemplate,
        String osUsername,
        String osPassword,
        String action,
        String routingkey,
        String exchange,
        String rmqUsername,
        String rmqPassword,
        Integer rmqPort,
        Map<String, String> tagpass,
        Integer batchSize,
        Integer flushIntervalMs,
        Integer retryCount,
        Integer retryDelayMs,
        Boolean addOriginText,
        Boolean enabled,
        Integer timeoutMs
) {}
