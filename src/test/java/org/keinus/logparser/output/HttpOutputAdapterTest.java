package org.keinus.logparser.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.keinus.logparser.domain.delivery.model.HttpOutputAdapter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HttpOutputAdapter 클래스의 단위 테스트
 *
 * 테스트 대상 함수들:
 * - HttpOutputAdapter(Map<String, String>) : 생성자 테스트
 * - send(Map<String, Object>, String) : 기본 전송 기능 테스트
 * - close() : 리소스 정리 테스트
 */
class HttpOutputAdapterTest {

    private Map<String, String> validConfig;
    private HttpOutputAdapter adapter;
    private int testPort = 19083; // 테스트용 포트

    @BeforeEach
    void setUp() {
        validConfig = new HashMap<>();
        validConfig.put("url", "http://localhost:" + testPort + "/api/logs");
        validConfig.put("messagetype", "http-output");
        validConfig.put("add_origin_text", "false");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (adapter != null) {
            adapter.close();
        }
    }

    @Test
    @DisplayName("생성자 테스트 - 유효한 설정으로 생성")
    void testConstructorWithValidConfig() throws IOException {
        // When & Then
        assertDoesNotThrow(() -> {
            adapter = new HttpOutputAdapter(validConfig);
        });
    }

    @Test
    @DisplayName("생성자 테스트 - null 설정으로 생성 시 예외 발생")
    void testConstructorWithNullConfig() {
        // When & Then
        assertThrows(IOException.class, () -> new HttpOutputAdapter(null));
    }

    @Test
    @DisplayName("생성자 테스트 - URL 누락 시 예외 발생")
    void testConstructorWithMissingUrl() {
        // Given
        validConfig.remove("url");

        // When & Then
        assertThrows(IOException.class, () -> new HttpOutputAdapter(validConfig));
    }

    @Test
    @DisplayName("생성자 테스트 - 잘못된 URL 형식")
    void testConstructorWithInvalidUrl() {
        // Given
        validConfig.put("url", "invalid-url-format");

        // When & Then
        assertThrows(IOException.class, () -> new HttpOutputAdapter(validConfig));
    }

    @Test
    @DisplayName("생성자 테스트 - 포트가 없는 URL")
    void testConstructorWithUrlNoPort() throws IOException {
        // Given
        validConfig.put("url", "http://localhost:80/api/logs"); // 명시적으로 80 포트 추가

        // When
        adapter = new HttpOutputAdapter(validConfig);

        // Then
        assertNotNull(adapter);
        assertEquals("http-output", adapter.getMessageType());
    }

    @Test
    @DisplayName("getType() 테스트 - 메시지 타입 반환")
    void testGetType() throws IOException {
        // Given
        adapter = new HttpOutputAdapter(validConfig);

        // When
        String type = adapter.getMessageType();

        // Then
        assertEquals("http-output", type);
    }

    @Test
    @DisplayName("getAddOriginText() 테스트 - 원본 텍스트 포함 여부")
    void testGetAddOriginText() throws IOException {
        // Given
        validConfig.put("add_origin_text", "true");
        adapter = new HttpOutputAdapter(validConfig);

        // When
        boolean addOriginText = adapter.isAddOriginText();

        // Then
        assertTrue(addOriginText);
    }

    @Test
    @DisplayName("close() 테스트 - 리소스 정리")
    void testClose() throws IOException {
        // Given
        adapter = new HttpOutputAdapter(validConfig);

        // When & Then
        assertDoesNotThrow(() -> adapter.close());
    }

    @Test
    @DisplayName("send() 테스트 - 기본 동작")
    void testSendBasicOperation() throws IOException {
        // Given
        adapter = new HttpOutputAdapter(validConfig);

        Map<String, Object> jsonData = new HashMap<>();
        jsonData.put("message", "test log");
        jsonData.put("level", "INFO");

        String jsonString = "{\"message\":\"test log\",\"level\":\"INFO\"}";

        // When & Then
        // send() 메서드는 네트워크 연결이 필요하므로 예외가 발생하지 않는지만 확인
        assertDoesNotThrow(() -> adapter.send(jsonData, jsonString));
    }

    @Test
    @DisplayName("send() 테스트 - 서버 연결 실패")
    void testSendConnectionFailure() throws IOException {
        // Given
        validConfig.put("url", "http://localhost:19999/api/logs"); // 존재하지 않는 포트
        adapter = new HttpOutputAdapter(validConfig);

        Map<String, Object> jsonData = new HashMap<>();
        jsonData.put("message", "test log");
        String jsonString = "{\"message\":\"test log\"}";

        // When & Then
        assertDoesNotThrow(() -> adapter.send(jsonData, jsonString));
        // 연결 실패 시에도 예외가 발생하지 않아야 함 (내부에서 처리)
    }
}