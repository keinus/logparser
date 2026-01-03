package org.keinus.logparser.domain.repository;

import java.util.Optional;
import org.keinus.logparser.domain.model.mapping.MappingConfiguration;

public interface MappingRepository {
    Optional<MappingConfiguration> findByMessageType(String messageType);
    void save(MappingConfiguration config);
}
