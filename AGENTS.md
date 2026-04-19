# Repository Guidelines

## Project Structure & Module Organization
`src/main/java/org/keinus/logparser` is organized by layer: `application/` handles pipeline orchestration, `domain/` contains parser/transform/input/output logic, `infrastructure/` holds config, persistence, and utilities, and `interfaces/` exposes REST, WebSocket, DTO, and exception entry points. `src/main/resources` contains `application.yml`, Flyway SQL in `db/migration`, logging config, and the built-in UI under `static/{js,css}`. Tests live in `src/test/java` and generally mirror production packages; `src/test/resources/application-test.yml` switches tests to H2. Diagrams and UI prototypes are kept in `readme/`.

## Build, Test, and Development Commands
- `./gradlew bootRun`: run the app locally on `http://localhost:8765`.
- `./gradlew test`: run the JUnit 5 test suite.
- `./gradlew build`: compile, test, and package `build/libs/logparser-<version>.jar`.
- `java -jar build/libs/logparser-0.3.0.jar`: run the packaged artifact.
- `./build.sh`: release-only script that builds and pushes Docker images; it assumes `sudo docker` and registry access.

## Coding Style & Naming Conventions
Use Java 21 and follow the existing Spring Boot conventions. Prefer 4-space indentation in Java files, constructor injection, and Lombok annotations already used in the codebase such as `@RequiredArgsConstructor` and `@Slf4j`. Keep class names role-based and predictable: `*Controller`, `*Service`, `*Repository`, `*DTO`, `*Adapter`. Use `camelCase` for methods and fields, `PascalCase` for types, and keep packages aligned with the current layered structure.

## Testing Guidelines
Tests use JUnit 5, Spring Boot test support, and Mockito-style mocking where full context is unnecessary. Name focused tests `*Test` and broader end-to-end flows `*IntegrationTest`. Add tests with every behavior change, especially around parsers, adapters, configuration reload, and transformation logic. No JaCoCo threshold is enforced, so cover the changed paths and edge cases explicitly.

## Commit & Pull Request Guidelines
Recent history mostly follows Conventional Commit prefixes such as `feat:`, `fix:`, `refactor:`, and `perf:`. Keep commit subjects short, imperative, and scoped, for example `feat(parser): add HTTP header normalization`. Pull requests should include a concise summary, test evidence (`./gradlew test` or targeted classes), linked issues when applicable, and screenshots for changes under `src/main/resources/static`.

## Security & Configuration Tips
Do not commit real secrets. Provide `LOGPARSER_CRYPTO_KEY` and `LOGPARSER_CRYPTO_SALT` through environment variables, and remember that default runtime data is stored in `${user.home}/logparser/data` via SQLite.
