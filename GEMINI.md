# Logparser Context Guide

## Project Overview
**Logparser** is a high-performance log processing pipeline engine built with Spring Boot. It functions similarly to Logstash or Fluentd, collecting logs from various sources, structuring them via pattern matching (Grok, Regex), and forwarding them to multiple destinations in real-time.

Key features include:
- **Dynamic Configuration:** Database-backed configuration with hot-reload support (no restart required).
- **Multi-Protocol Support:** Input via File, TCP, UDP, HTTP, Kafka. Output to Kafka, OpenSearch, RDB, Console, etc.
- **Pipeline Architecture:** Input -> Parser -> Transformer -> Output.
- **Observability:** Real-time Pipeline Topology visualization and Live Tail (WebSocket).

## Tech Stack
- **Language:** Java 21
- **Framework:** Spring Boot 3.5.9
- **Build System:** Gradle
- **Database:** SQLite (for configuration storage), JPA/Hibernate
- **Libraries:**
  - `java-grok` (Parsing)
  - `spring-kafka`, `spring-amqp` (Messaging)
  - `spring-websocket` (Live Tail)
  - `springdoc-openapi` (Documentation)
  - `sqlite-jdbc` (Persistence)

## Architecture
The application follows a standard pipeline architecture:
1.  **Input Adapters:** Ingest raw data from sources.
2.  **Internal Queue 1:** Buffers raw data.
3.  **ProcessingDispatcher:** Unified thread handling Parsing and Transformation.
    - **Parsers:** Convert raw data to structured format.
    - **Transformers:** Filter, mask, or modify fields.
    - **Live Tail Hook:** Broadcasts processed events via WebSocket.
4.  **Internal Queue 2:** Buffers processed data.
5.  **Output Adapters:** Dispatch data to final destinations.

**Control Plane:** Configuration is stored in an SQLite database and managed via a REST API. Changes trigger a hot-reload event to update active components.

## Key Directories
- **`src/main/java/org/keinus/logparser/`**: Root package.
  - **`application/pipeline/`**: Core pipeline orchestration logic (`MessageDispatcher`, `ProcessingDispatcher`).
  - **`application/service/`**: Core services (`LiveTailService`).
  - **`domain/configuration/`**: Configuration models and validation logic.
  - **`interfaces/websocket/`**: WebSocket handlers (`LiveTailHandler`).
  - **`interfaces/controller/`**: REST Controllers.
- **`src/main/resources/`**:
  - `application.yml`: Main configuration.
  - `static/`: Frontend assets (Vanilla JS + Tailwind).
- **`data/`**: Runtime data storage (SQLite database).

## Build and Run
**Prerequisites:** Java 21+

**Build:**
```bash
./gradlew build
```

**Run:**
```bash
# Set security keys (recommended)
export LOGPARSER_CRYPTO_KEY="$(openssl rand -base64 32)"
export LOGPARSER_CRYPTO_SALT="$(openssl rand -hex 16)"

java -jar build/libs/logparser-0.2.3.jar
```
Or via Gradle:
```bash
./gradlew bootRun
```

**API Documentation:**
Available at `http://localhost:8765/swagger-ui.html` when running.

## Development Conventions
- **Code Style:** Standard Java/Spring conventions.
- **Testing:** JUnit 5. Tests should be located in `src/test/java`.
- **Versioning:** Semantic versioning (currently 0.2.3).

## Known Issues & TODOs
- **Output Fan-out:** Current implementation may block if one output adapter is slow. Needs asynchronous/parallel dispatch.
- **Threading:** Fixed thread pool sizes can lead to bottlenecks under burst traffic. Dynamic scaling is planned.
- **I/O:** Some adapters utilize blocking I/O; migration to non-blocking (Async) I/O is recommended.