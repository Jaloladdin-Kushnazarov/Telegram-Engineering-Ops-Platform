package com.engops.platform.tenantconfig.repository;

import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Workflow ta'rifi uchun repository. Tenant-scoped.
 */
@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    Optional<WorkflowDefinition> findByTenantIdAndId(UUID tenantId, UUID id);

    List<WorkflowDefinition> findByTenantId(UUID tenantId);

    Optional<WorkflowDefinition> findByTenantIdAndName(UUID tenantId, String name);

    Optional<WorkflowDefinition> findByTenantIdAndWorkItemType(UUID tenantId, String workItemType);

    List<WorkflowDefinition> findByTenantIdAndActiveTrue(UUID tenantId);
}
