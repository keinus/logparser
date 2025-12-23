# Logparser

Logparser is a high-performance, flexible, and dynamically configurable log processing application built with Spring Boot. It allows you to ingest logs from various sources, parse and transform them using powerful patterns, and forward the structured data to multiple destinations.

## Features

*   **Dynamic Pipelines**: Configure input, parsing, and output stages dynamically without restarting the application.
*   **Database-Backed Configuration**: Pipeline configurations are stored in SQLite for persistence and easy management.
*   **Hot Reload**: Changes to the configuration are automatically detected and applied.
*   **High Performance**: Built on non-blocking I/O principles and optimized for throughput.
*   **Security**: Sensitive configuration data (like passwords) is encrypted.

### Supported Components

**Input Adapters:**
*   **File**: Tail files and ingest new lines.
*   **HTTP**: Receive logs via HTTP POST requests.
*   **Kafka**: Consume messages from Kafka topics.
*   **TCP**: Listen for logs over raw TCP connections.
*   **UDP**: Receive logs via UDP packets.
*   **Fake**: Generate synthetic logs for testing and benchmarking.

**Parsers:**
*   **Grok**: Use powerful Grok patterns to parse unstructured text.
*   **JSON**: Parse JSON formatted logs.
*   **Regex**: Use Regular Expressions for custom extraction.
*   **Syslog**: Support for RFC3164 and RFC5424 syslog formats.
*   **HTTP**: Parse HTTP specific log formats.

**Output Adapters:**
*   **Console**: Print logs to standard output (useful for debugging).
*   **HTTP**: Forward logs to an external HTTP endpoint.
*   **Kafka**: Publish logs to Kafka topics.
*   **OpenSearch**: Index logs into OpenSearch clusters.
*   **RabbitMQ**: Publish logs to RabbitMQ exchanges.
*   **TCP**: Forward logs over TCP.
*   **Benchmark**: Measure throughput performance (blackhole).

## Prerequisites

*   **Java 21** or later
*   **Gradle** (Wrapper included)

## Getting Started

### Build

Use the provided Gradle wrapper to build the project:

```bash
./gradlew build
```

### Run

You can run the application directly using Gradle:

```bash
./gradlew bootRun
```

Or run the built JAR file:

```bash
java -jar build/libs/logparser-0.2.3.jar
```

## Configuration

### Application Configuration
The main application configuration is located in `src/main/resources/application.yml`. Key settings include:

*   **Server Port**: Defaults to `8765`.
*   **Database**: SQLite is used by default (`~/logparser/data/config.db`).
*   **Security**: Encryption keys for sensitive data.

### Environment Variables

For production, it is **highly recommended** to set the following environment variables:

*   `SERVER_PORT`: Port to run the application on (default: 8765).
*   `LOGPARSER_CRYPTO_KEY`: A 32-character base64 encoded secret key for encryption.
*   `LOGPARSER_CRYPTO_SALT`: A random salt value.

Example:
```bash
export LOGPARSER_CRYPTO_KEY="$(openssl rand -base64 32)"
export LOGPARSER_CRYPTO_SALT="$(openssl rand -hex 16)"
java -jar build/libs/logparser-0.2.3.jar
```

### Pipeline Configuration
Pipeline configurations (Inputs, Parsers, Outputs) are managed via the database. The application supports importing initial configurations or migrating them.

## API Documentation

The application exposes a REST API for management and monitoring. Swagger UI is available for interactive documentation:

*   **URL**: `http://localhost:8765/swagger-ui.html`

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
