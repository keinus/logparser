package org.keinus.logparser.application.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ConfigMetadataService {

    // ==================== Adapter Type Information ====================

    public List<AdapterTypeInfo> getInputAdapterTypes() {
        return Arrays.asList(
                new AdapterTypeInfo("tcp", "TCP Input", "Listen for TCP connections"),
                new AdapterTypeInfo("udp", "UDP Input", "Listen for UDP datagrams"),
                new AdapterTypeInfo("http", "HTTP Input", "HTTP REST endpoint"),
                new AdapterTypeInfo("kafka", "Kafka Input", "Consume from Kafka topic"),
                new AdapterTypeInfo("file", "File Input", "Read from files"),
                new AdapterTypeInfo("fake", "Fake Input", "Generate test data")
        );
    }

    public List<AdapterTypeInfo> getOutputAdapterTypes() {
        return Arrays.asList(
                new AdapterTypeInfo("console", "Console Output", "Print to console"),
                new AdapterTypeInfo("tcp", "TCP Output", "Send via TCP"),
                new AdapterTypeInfo("http", "HTTP Output", "Send via HTTP POST/PUT"),
                new AdapterTypeInfo("kafka", "Kafka Output", "Produce to Kafka topic"),
                new AdapterTypeInfo("opensearch", "OpenSearch Output", "Index to OpenSearch/Elasticsearch"),
                new AdapterTypeInfo("rabbitmq", "RabbitMQ Output", "Publish to RabbitMQ exchange"),
                new AdapterTypeInfo("benchmark", "Benchmark Output", "Performance testing")
        );
    }

    public List<AdapterTypeInfo> getParserTypes() {
        return Arrays.asList(
                new AdapterTypeInfo("json", "JSON Parser", "Parse JSON formatted logs"),
                new AdapterTypeInfo("grok", "Grok Parser", "Parse with Grok patterns"),
                new AdapterTypeInfo("regex", "Regex Parser", "Parse with regular expressions"),
                new AdapterTypeInfo("rfc3164", "RFC3164 Syslog Parser", "Parse RFC3164 syslog format"),
                new AdapterTypeInfo("rfc5424", "RFC5424 Syslog Parser", "Parse RFC5424 syslog format"),
                new AdapterTypeInfo("http", "HTTP Parser", "Parse HTTP access logs")
        );
    }

    public List<TransformTypeInfo> getTransformTypes() {
        return Arrays.asList(
                new TransformTypeInfo("filter", "Filter Transform", "Filter messages based on conditions"),
                new TransformTypeInfo("add_property", "Add Property", "Add fields to messages"),
                new TransformTypeInfo("remove_property", "Remove Property", "Remove fields from messages")
        );
    }

    // ==================== Schema Information ====================

    public AdapterSchema getInputAdapterSchema(String type) {
        return switch (type.toLowerCase()) {
            case "tcp", "udp" -> new AdapterSchema(
                    type,
                    Arrays.asList(
                            new FieldSchema("host", "string", true, "Host to bind to"),
                            new FieldSchema("port", "integer", true, "Port to listen on"),
                            new FieldSchema("bufferSize", "integer", false, "Buffer size in bytes"),
                            new FieldSchema("timeoutMs", "integer", false, "Connection timeout in ms")
                    )
            );
            case "http" -> new AdapterSchema(
                    type,
                    Arrays.asList(
                            new FieldSchema("port", "integer", true, "Port to listen on"),
                            new FieldSchema("path", "string", false, "HTTP path"),
                            new FieldSchema("workerThreads", "integer", false, "Number of worker threads")
                    )
            );
            case "kafka" -> new AdapterSchema(
                    type,
                    Arrays.asList(
                            new FieldSchema("bootstrapservers", "string", true, "Kafka bootstrap servers"),
                            new FieldSchema("topicid", "string", true, "Topic to consume from"),
                            new FieldSchema("groupId", "string", true, "Consumer group ID")
                    )
            );
            case "file" -> new AdapterSchema(
                    type,
                    Arrays.asList(
                            new FieldSchema("path", "string", true, "File path or pattern"),
                            new FieldSchema("pathPattern", "string", false, "Pattern for multiple files")
                    )
            );
            default -> new AdapterSchema(type, List.of());
        };
    }

    public AdapterSchema getOutputAdapterSchema(String type) {
        return switch (type.toLowerCase()) {
            case "tcp" -> new AdapterSchema(
                    type,
                    Arrays.asList(
                            new FieldSchema("host", "string", true, "Destination host"),
                            new FieldSchema("port", "integer", true, "Destination port"),
                            new FieldSchema("timeoutMs", "integer", false, "Connection timeout")
                    )
            );
            case "http" -> new AdapterSchema(
                    type,
                    Arrays.asList(
                            new FieldSchema("url", "string", true, "Target URL"),
                            new FieldSchema("method", "string", false, "HTTP method (POST, PUT)"),
                            new FieldSchema("headers", "json", false, "HTTP headers")
                    )
            );
            case "kafka" -> new AdapterSchema(
                    type,
                    Arrays.asList(
                            new FieldSchema("bootstrapservers", "string", true, "Kafka bootstrap servers"),
                            new FieldSchema("topicid", "string", true, "Topic to produce to"),
                            new FieldSchema("key", "string", false, "Message key")
                    )
            );
            case "opensearch" -> new AdapterSchema(
                    type,
                    Arrays.asList(
                            new FieldSchema("host", "string", true, "OpenSearch host"),
                            new FieldSchema("port", "integer", true, "OpenSearch port"),
                            new FieldSchema("indexTemplate", "string", false, "Index name template"),
                            new FieldSchema("osUsername", "string", false, "Username"),
                            new FieldSchema("osPassword", "string", false, "Password")
                    )
            );
            case "rabbitmq" -> new AdapterSchema(
                    type,
                    Arrays.asList(
                            new FieldSchema("host", "string", true, "RabbitMQ host"),
                            new FieldSchema("exchange", "string", true, "Exchange name"),
                            new FieldSchema("routingkey", "string", false, "Routing key"),
                            new FieldSchema("rmqUsername", "string", false, "Username"),
                            new FieldSchema("rmqPassword", "string", false, "Password")
                    )
            );
            default -> new AdapterSchema(type, List.of());
        };
    }

    public AdapterSchema getParserSchema(String type) {
        return switch (type.toLowerCase()) {
            case "grok", "regex" -> new AdapterSchema(
                    type,
                    List.of(
                            new FieldSchema("param", "string", true, "Pattern to match")
                    )
            );
            case "json", "rfc3164", "rfc5424", "http" -> new AdapterSchema(
                    type,
                    List.of()
            );
            default -> new AdapterSchema(type, List.of());
        };
    }

    public TransformSchema getTransformSchema(String type) {
        return switch (type.toLowerCase()) {
            case "filter" -> new TransformSchema(
                    type,
                    Arrays.asList(
                            new FieldSchema("filterPass", "json", false, "Pass conditions"),
                            new FieldSchema("filterDrop", "json", false, "Drop conditions")
                    )
            );
            case "add_property" -> new TransformSchema(
                    type,
                    List.of(
                            new FieldSchema("addProperties", "json", true, "Properties to add")
                    )
            );
            case "remove_property" -> new TransformSchema(
                    type,
                    List.of(
                            new FieldSchema("removeProperties", "array", true, "Properties to remove")
                    )
            );
            default -> new TransformSchema(type, List.of());
        };
    }

    // ==================== Supported Options ====================

    public List<String> getSupportedCodecs() {
        return Arrays.asList("plain", "json", "line");
    }

    public List<String> getSupportedHttpMethods() {
        return Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE");
    }

    // ==================== Inner Classes ====================

    public record AdapterTypeInfo(String type, String displayName, String description) {}

    public record TransformTypeInfo(String type, String displayName, String description) {}

    public record AdapterSchema(String type, List<FieldSchema> fields) {}

    public record TransformSchema(String type, List<FieldSchema> fields) {}

    public record FieldSchema(
            String name,
            String dataType,
            boolean required,
            String description
    ) {}
}
