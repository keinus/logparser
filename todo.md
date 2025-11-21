# Code Review To-Do List

## High Priority Issues:

*   **Failed Test:** `KafkaInputAdapterTest > close() 테스트 - 리소스 정리 FAILED`
    *   **Details:** `org.mockito.exceptions.verification.opentest4j.ArgumentsAreDifferent` at `KafkaInputAdapterTest.java:137`.
    *   **Action:** Investigate and fix the Mockito verification issue in the `close()` method of `KafkaInputAdapterTest`.

*   **Performance/Reliability Issue - Message Queue Overflows:**
    *   **Details:** Repeated `Output message dropped due to queue overflow` and `Output queue critical!` warnings in `MessageDispatcher.java`.
    *   **Action:** Analyze `MessageDispatcher.java` and `OutputAdaptorComponent.java` to understand queue management, identify bottlenecks, and propose solutions (e.g., increase queue size, improve message processing rate, implement more robust backpressure mechanisms).

*   **Data Loss Issue - Dead Letter Queue (DLQ) Accumulation:**
    *   **Details:** High `DLQ count: 40000` and `retries exceeded: 9998` reported, especially in `OpenSearchOutputAdapter.java` context.
    *   **Action:** Investigate why messages are failing and ending up in the DLQ. Review `OpenSearchOutputAdapter.java` and related components for error handling, retry logic, and potential issues with external service communication. Implement a strategy for handling or re-processing DLQ messages.

## Other Observations:

*   **Interrupted Sleeps:** `sleep interrupted` messages observed. While not critical, it could indicate abrupt thread termination or improper shutdown handling. (Lower priority to investigate after core issues are addressed).

## Next Steps:

1.  **Fix `KafkaInputAdapterTest.java`:** Prioritize fixing the failing test.
2.  **Analyze Queue Management:** Investigate `MessageDispatcher` and output queue logic.
3.  **Address DLQ Issues:** Understand and resolve the root cause of DLQ accumulation.
4.  **Static Analysis:** Check `build.gradle` for existing static analysis tool configurations (e.g., Checkstyle, PMD, SpotBugs) and propose adding them if missing.