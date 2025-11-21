package org.keinus.logparser.infrastructure.persistence.repository;

import org.keinus.logparser.infrastructure.persistence.entity.ConfigHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ConfigHistoryRepository extends JpaRepository<ConfigHistoryEntity, Long> {

    List<ConfigHistoryEntity> findByEntityTypeAndEntityId(String entityType, Long entityId, Sort sort);

    List<ConfigHistoryEntity> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Sort sort);

    Page<ConfigHistoryEntity> findAll(Pageable pageable);

    List<ConfigHistoryEntity> findByAction(String action);
}
