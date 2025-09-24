# LogParser Project - Claude Development Guide

## Project Overview

Spring Boot 3.5.6 application for parsing and processing logs with Java 21.
Integrates with Elasticsearch, Kafka, and RabbitMQ for log ingestion and storage.

## Build Commands

- **Build**: `./gradlew build`
- **Test**: `./gradlew test`
- **Run**: `./gradlew bootRun`
- **Clean**: `./gradlew clean`

## Development Guidelines

### Java Coding Standards

- Use Java 21 features appropriately
- Follow Spring Boot conventions and best practices
- Utilize Lombok annotations to reduce boilerplate code
- Write comprehensive Javadoc for all public methods and classes

### Documentation Requirements

- All public classes must have class-level Javadoc
- All public methods must have method-level Javadoc with @param and @return tags
- Include usage examples in Javadoc where appropriate
- Document configuration properties and their purposes

### Testing

- Write unit tests using JUnit 5
- Use Spring Boot Test for integration tests
- Maintain test coverage for critical parsing logic
- Test Kafka and RabbitMQ integrations with test containers when possible

### Code Style

- Use consistent formatting and naming conventions
- Prefer composition over inheritance
- Keep methods focused and single-purpose
- Use meaningful variable and method names
- Fix all SonarQube warnings and code quality issues
- Follow secure coding practices:
  - Validate all inputs and sanitize data
  - Use parameterized queries to prevent SQL injection
  - Handle sensitive data securely (no logging of secrets/passwords)
  - Implement proper error handling without exposing internal details
  - Use secure random number generators
  - Follow principle of least privilege for access controls

### Spring Boot Specific

- Use @ConfigurationProperties for external configuration
- Leverage Spring's dependency injection appropriately
- Follow Spring Boot auto-configuration principles
- Use proper exception handling with @ControllerAdvice when applicable

## Key Dependencies

- Spring Boot Starter (AMQP, Data Elasticsearch)
- Spring Kafka
- Java Grok for log pattern matching
- Gson for JSON processing
- Apache HttpComponents
- Lombok for code generation

## Configuration

- Application properties should be documented
- Use type-safe configuration properties
- Separate development and production configurations appropriately
