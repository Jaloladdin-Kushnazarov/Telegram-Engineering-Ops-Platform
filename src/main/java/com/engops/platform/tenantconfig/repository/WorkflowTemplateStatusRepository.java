package com.engops.platform.tenantconfig.repository;

import com.engops.platform.tenantconfig.model.WorkflowTemplateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Phase 198 — workflow template status repositoriyasi. Global katalog.
 *
 * Pre-check / lookup pattern:
 * - {@code findAllByTemplate_IdOrderByStatusOrderAsc} — shablon ichidagi
 *   statuslarni status_order bo'yicha tartiblangan holda qaytaradi.
 */
@Repository
public interface WorkflowTemplateStatusRepository extends JpaRepository<WorkflowTemplateStatus, UUID> {

    List<WorkflowTemplateStatus> findAllByTemplate_IdOrderByStatusOrderAsc(UUID templateId);
}
