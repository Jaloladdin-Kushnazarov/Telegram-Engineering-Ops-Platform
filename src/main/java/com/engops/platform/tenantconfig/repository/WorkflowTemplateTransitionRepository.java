package com.engops.platform.tenantconfig.repository;

import com.engops.platform.tenantconfig.model.WorkflowTemplateTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Phase 198 — workflow template tranziya repositoriyasi. Global katalog.
 *
 * Pre-check / lookup pattern:
 * - {@code findAllByTemplate_IdOrderByFromStatusCodeAscToStatusCodeAsc} —
 *   shablon ichidagi tranziyalarni from → to bo'yicha barqaror tartibda
 *   qaytaradi.
 */
@Repository
public interface WorkflowTemplateTransitionRepository extends JpaRepository<WorkflowTemplateTransition, UUID> {

    List<WorkflowTemplateTransition> findAllByTemplate_IdOrderByFromStatusCodeAscToStatusCodeAsc(UUID templateId);
}
