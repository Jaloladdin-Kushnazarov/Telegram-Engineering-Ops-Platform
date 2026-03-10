package com.engops.platform.tenantconfig.repository;

import com.engops.platform.tenantconfig.model.RoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Yo'naltirish qoidasi uchun repository. Tenant-scoped.
 */
@Repository
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, UUID> {

    List<RoutingRule> findByTenantId(UUID tenantId);

    List<RoutingRule> findByTenantIdAndWorkItemType(UUID tenantId, String workItemType);

    List<RoutingRule> findByTenantIdAndActiveTrueOrderByPriorityDesc(UUID tenantId);
}
