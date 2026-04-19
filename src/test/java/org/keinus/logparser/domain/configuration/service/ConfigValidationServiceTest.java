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
}
