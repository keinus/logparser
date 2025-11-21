package org.keinus.logparser.infrastructure.persistence.repository;

import org.keinus.logparser.infrastructure.persistence.entity.ConfigurationVersionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigurationVersionRepository extends JpaRepository<ConfigurationVersionEntity, Long> {

    List<ConfigurationVersionEntity> findByStatusOrderByCreatedAtDesc(String status);

    Optional<ConfigurationVersionEntity> findByVersionName(String versionName);

    Page<ConfigurationVersionEntity> findAll(Pageable pageable);
}
