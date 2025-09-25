package org.keinus.logparser.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.keinus.logparser.core.schema.LogEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TcpInputAdapter 클래스의 단위 테스트
 *
 * 테스트 대상 함수들:
 * - TcpInputAdapter(Map<String, String>) : 생성자 테스트
 * - close() : 서버 소켓 정리 테스트
 * - getType(), getSourceHost() : 기본 속성 테스트
 */
class TcpInputAdapterTest {

    private Map<String, String> validConfig;
    private TcpInputAdapter adapter;
    private int testPort = 19081; // 테스트용 포트

    @BeforeEach
    void setUp() {
        validConfig = new HashMap<>();
        validConfig.put("port", String.valueOf(testPort));
        validConfig.put("messagetype", "tcp-message");
        validConfig.put("host", "localhost");
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
            adapter = new TcpInputAdapter(validConfig);
        });
    }

    @Test
    @DisplayName("생성자 테스트 - null 설정으로 생성 시 예외 발생")
    void testConstructorWithNullConfig() {
        // When & Then
        assertThrows(IOException.class, () -> new TcpInputAdapter(null));
    }

    @Test
    @DisplayName("생성자 테스트 - 포트 누락 시 예외 발생")
    void testConstructorWithMissingPort() {
        // Given
        validConfig.remove("port");

        // When & Then
        assertThrows(NumberFormatException.class, () -> new TcpInputAdapter(validConfig));
    }

    @Test
    @DisplayName("생성자 테스트 - 잘못된 포트 형식")
    void testConstructorWithInvalidPort() {
        // Given
        validConfig.put("port", "invalid-port");

        // When & Then
        assertThrows(NumberFormatException.class, () -> new TcpInputAdapter(validConfig));
    }

    @Test
    @DisplayName("close() 테스트 - 서버 소켓 정리")
    void testClose() throws IOException {
        // Given
        adapter = new TcpInputAdapter(validConfig);

        // When & Then
        assertDoesNotThrow(() -> adapter.close());
    }

    @Test
    @DisplayName("close() 테스트 - 이미 닫힌 어댑터 재시도")
    void testCloseAlreadyClosed() throws IOException {
        // Given
        adapter = new TcpInputAdapter(validConfig);
        adapter.close();

        // When & Then
        assertDoesNotThrow(() -> adapter.close());
    }

    @Test
    @DisplayName("getType() 테스트 - 메시지 타입 반환")
    void testGetType() throws IOException {
        // Given
        adapter = new TcpInputAdapter(validConfig);

        // When
        String type = adapter.getMessageType();

        // Then
        assertEquals("tcp-message", type);
    }

    @Test
    @DisplayName("getSourceHost() 테스트 - 소스 호스트 반환")
    void testGetSourceHost() throws IOException {
        // Given
        adapter = new TcpInputAdapter(validConfig);

        // When
        String host = adapter.getSourceHost();

        // Then
        assertEquals("localhost", host);
    }

    @Test
    @DisplayName("TCP 기본 동작 테스트")
    void testTcpBasicOperation() throws IOException {
        // Given
        adapter = new TcpInputAdapter(validConfig);

        // When & Then
        // run() 메서드가 블로킹이므로 실제 연결 없이는 테스트하기 어려움
        // 대신 기본 동작들만 테스트
        assertEquals("tcp-message", adapter.getMessageType());
        assertEquals("localhost", adapter.getSourceHost());

        // close() 메서드도 테스트
        assertDoesNotThrow(() -> adapter.close());
    }
}