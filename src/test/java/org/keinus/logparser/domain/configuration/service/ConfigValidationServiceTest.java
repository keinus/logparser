package org.keinus.logparser.domain.configuration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keinus.logparser.infrastructure.persistence.entity.InputAdapterEntity;
import org.keinus.logparser.infrastructure.persistence.entity.OutputAdapterEntity;
import org.keinus.logparser.infrastructure.persistence.entity.ParserEntity;
import org.keinus.logparser.infrastructure.persistence.repository.InputAdapterRepository;
import org.keinus.logparser.infrastructure.persistence.repository.OutputAdapterRepository;
import org.keinus.logparser.infrastructure.persistence.repository.ParserRepository;
import org.keinus.logparser.infrastructure.persistence.repository.TransformRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigValidationServiceTest {

    private InputAdapterRepository inputAdapterRepository;
    private ParserRepository parserRepository;
    private TransformRepository transformRepository;
    private OutputAdapterRepository outputAdapterRepository;
    private ConfigValidationService configValidationService;

    @BeforeEach
    void setUp() {
        inputAdapterRepository = mock(InputAdapterRepository.class);
        parserRepository = mock(ParserRepository.class);
        transformRepository = mock(TransformRepository.class);
        outputAdapterRepository = mock(OutputAdapterRepository.class);
        configValidationService = new ConfigValidationService(
                inputAdapterRepository,
                parserRepository,
                transformRepository,
                outputAdapterRepository
        );
    }

    @Test
    void globalOutputAdapterSatisfiesParserOutputRequirement() {
        InputAdapterEntity input = InputAdapterEntity.builder()
                .messagetype("access")
                .enabled(true)
                .build();
        ParserEntity parser = ParserEntity.builder()
                .messagetype("access")
                .enabled(true)
                .build();
        OutputAdapterEntity globalOutput = OutputAdapterEntity.builder()
                .messagetype("all")
                .enabled(true)
                .build();

        when(inputAdapterRepository.findAll()).thenReturn(List.of(input));
        when(parserRepository.findAll()).thenReturn(List.of(parser));
        when(outputAdapterRepository.findAll()).thenReturn(List.of(globalOutput));

        ConfigValidationService.PipelineIntegrityResult result = configValidationService.validatePipelineIntegrity();

        assertTrue(result.warnings().stream().noneMatch(warning -> warning.contains("has no corresponding output adapter")));
        assertTrue(result.warnings().stream().noneMatch(warning -> warning.contains("Output message type 'all'")));
    }

    @Test
    void snmpInputAdapterRequiresConfigParams() {
        InputAdapterEntity missingParams = InputAdapterEntity.builder()
                .type("SnmpInputAdapter")
                .messagetype("snmp-metrics")
                .enabled(true)
                .build();

        ConfigValidationService.ValidationResult missingResult =
                configValidationService.validateInputAdapter(missingParams);

        assertFalse(missingResult.isValid());
        assertTrue(missingResult.errors().stream().anyMatch(error -> error.contains("configParams")));

        InputAdapterEntity valid = InputAdapterEntity.builder()
                .type("SnmpInputAdapter")
                .messagetype("snmp-metrics")
                .configParams("""
                        {"targets":[{"host":"192.0.2.10","community":"public"}],"oids":["1.3.6.1.2.1.1.5.0"]}
                        """)
                .enabled(true)
                .build();

        ConfigValidationService.ValidationResult validResult =
                configValidationService.validateInputAdapter(valid);

        assertTrue(validResult.isValid());
    }

    @Test
    void rabbitMqInputAdapterRequiresQueueConfigParam() {
        InputAdapterEntity missingParams = InputAdapterEntity.builder()
                .type("RabbitMqInputAdapter")
                .messagetype("rabbit-logs")
                .enabled(true)
                .build();

        ConfigValidationService.ValidationResult missingResult =
                configValidationService.validateInputAdapter(missingParams);

        assertFalse(missingResult.isValid());
        assertTrue(missingResult.errors().stream().anyMatch(error -> error.contains("configParams")));

        InputAdapterEntity missingQueue = InputAdapterEntity.builder()
                .type("RabbitMqInputAdapter")
                .messagetype("rabbit-logs")
                .configParams("{\"host\":\"rabbit.local\"}")
                .enabled(true)
                .build();

        ConfigValidationService.ValidationResult missingQueueResult =
                configValidationService.validateInputAdapter(missingQueue);

        assertFalse(missingQueueResult.isValid());
        assertTrue(missingQueueResult.errors().stream().anyMatch(error -> error.contains("queue")));

        InputAdapterEntity valid = InputAdapterEntity.builder()
                .type("RabbitMqInputAdapter")
                .messagetype("rabbit-logs")
                .host("rabbit.local")
                .port(5672)
                .configParams("{\"queue\":\"logs.input\"}")
                .enabled(true)
                .build();

        ConfigValidationService.ValidationResult validResult =
                configValidationService.validateInputAdapter(valid);

        assertTrue(validResult.isValid());
    }
}
