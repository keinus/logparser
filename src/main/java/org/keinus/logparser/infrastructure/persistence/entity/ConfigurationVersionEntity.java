package org.keinus.logparser.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuration_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ConfigurationVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_name", unique = true, nullable = false, length = 255)
    private String versionName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "input_adapters", columnDefinition = "TEXT")
    private String inputAdapters;

    @Column(name = "parsers", columnDefinition = "TEXT")
    private String parsers;

    @Column(name = "transforms", columnDefinition = "TEXT")
    private String transforms;

    @Column(name = "output_adapters", columnDefinition = "TEXT")
    private String outputAdapters;

    @Column(name = "common_settings", columnDefinition = "TEXT")
    private String commonSettings;

    @Column(name = "status", length = 50)
    private String status = "DRAFT";

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;
}
