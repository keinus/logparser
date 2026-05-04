package org.keinus.logparser.infrastructure.config;

import org.keinus.logparser.infrastructure.persistence.entity.ParserEntity;
import org.keinus.logparser.infrastructure.persistence.repository.ParserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
class JpaConfigTest {

    @Autowired
    private ParserRepository parserRepository;

    @Test
    void auditingShouldWork() {
        ParserEntity parser = ParserEntity.builder()
                .type("test")
                .messagetype("test")
                .build();
        
        ParserEntity saved = parserRepository.save(parser);
        
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
