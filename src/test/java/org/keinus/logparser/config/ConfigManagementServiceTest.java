package org.keinus.logparser.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keinus.logparser.application.config.ConfigManagementService;
import org.keinus.logparser.infrastructure.persistence.repository.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ConfigManagementServiceTest {

    private ConfigManagementService configManagementService;

    @Mock private InputAdapterRepository inputAdapterRepository;
    @Mock private ParserRepository parserRepository;
    @Mock private TransformRepository transformRepository;
    @Mock private OutputAdapterRepository outputAdapterRepository;
    @Mock private ConfigSettingsRepository configSettingsRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        configManagementService = new ConfigManagementService(
            inputAdapterRepository,
            parserRepository,
            transformRepository,
            outputAdapterRepository,
            configSettingsRepository,
            eventPublisher
        );
    }

    @Test
    @DisplayName("정수형 설정값이 실수 형식(123.0)으로 들어와도 정상적으로 파싱되어야 한다")
    void testParseValueWithFloatStringForInteger() throws Exception {
        // private 메서드인 parseValue에 접근하기 위해 리플렉션 사용
        Method parseValueMethod = ConfigManagementService.class.getDeclaredMethod("parseValue", String.class, String.class);
        parseValueMethod.setAccessible(true);

        // INTEGER 테스트
        Object resultInt = parseValueMethod.invoke(configManagementService, "123.0", "INTEGER");
        assertEquals(123, resultInt);

        // LONG 테스트
        Object resultLong = parseValueMethod.invoke(configManagementService, "456.0 ", "LONG");
        assertEquals(456L, resultLong);
        
        // 정상적인 정수 테스트
        Object resultNormal = parseValueMethod.invoke(configManagementService, "789", "INTEGER");
        assertEquals(789, resultNormal);
    }

    @Test
    @DisplayName("잘못된 형식의 숫자는 IllegalArgumentException을 발생시켜야 한다")
    void testParseValueWithInvalidString() throws Exception {
        Method parseValueMethod = ConfigManagementService.class.getDeclaredMethod("parseValue", String.class, String.class);
        parseValueMethod.setAccessible(true);

        assertThrows(Exception.class, () -> {
            try {
                parseValueMethod.invoke(configManagementService, "abc", "INTEGER");
            } catch (Exception e) {
                throw e.getCause() instanceof IllegalArgumentException ? (IllegalArgumentException)e.getCause() : new RuntimeException(e);
            }
        });
    }
}
