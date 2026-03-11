package com.engops.platform.audit.repository;

import com.engops.platform.audit.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * AuditEvent uchun repository. Append-only.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByTenantIdAndEntityTypeAndEntityId(UUID tenantId, String entityType, UUID entityId);

    List<AuditEvent> findByTenantIdOrderByOccurredAtDesc(UUID tenantId);
}
