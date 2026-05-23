package com.engops.platform.tenantconfig.repository;

import com.engops.platform.tenantconfig.model.WorkflowTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 198 — global workflow template katalog repositoriyasi. Tenant-agnostic.
 */
@Repository
public interface WorkflowTemplateRepository extends JpaRepository<WorkflowTemplate, UUID> {

    Optional<WorkflowTemplate> findByCode(String code);

    List<WorkflowTemplate> findAllByOrderByCodeAsc();
}
