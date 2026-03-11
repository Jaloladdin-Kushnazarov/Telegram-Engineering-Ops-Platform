package com.engops.platform.audit;

import com.engops.platform.audit.model.AuditEvent;
import com.engops.platform.audit.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Audit moduli servisi — biznes uchun muhim o'zgarishlarni qayd qiladi.
 * Audit yozuvi asosiy tranzaksiya bilan birga saqlanadi.
 */
@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Audit event yaratadi — to'liq parametrlar bilan.
     *
     * @param tenantId tenant identifikatori
     * @param entityType entity turi (masalan "WORK_ITEM")
     * @param entityId entity identifikatori
     * @param eventType hodisa turi (masalan "CREATED", "STATUS_TRANSITION")
     * @param actorUserId amal bajaruvchi
     * @param actionSource amal manbai (masalan "MANUAL", "TELEGRAM", "API")
     * @param oldValue eski qiymat (nullable)
     * @param newValue yangi qiymat (nullable)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent recordEvent(UUID tenantId, String entityType, UUID entityId,
                                   String eventType, UUID actorUserId,
                                   String actionSource,
                                   String oldValue, String newValue) {
        AuditEvent event = new AuditEvent(tenantId, entityType, entityId, eventType, actorUserId);
        event.setActionSource(actionSource);
        event.setOldValueJson(oldValue);
        event.setNewValueJson(newValue);
        return auditEventRepository.save(event);
    }

    /**
     * Audit event yaratadi — actionSource yo'q (oddiy holat).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent recordEvent(UUID tenantId, String entityType, UUID entityId,
                                   String eventType, UUID actorUserId,
                                   String oldValue, String newValue) {
        return recordEvent(tenantId, entityType, entityId, eventType, actorUserId,
                null, oldValue, newValue);
    }

    /**
     * Audit event yaratadi — faqat asosiy parametrlar bilan.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent recordEvent(UUID tenantId, String entityType, UUID entityId,
                                   String eventType, UUID actorUserId) {
        AuditEvent event = new AuditEvent(tenantId, entityType, entityId, eventType, actorUserId);
        return auditEventRepository.save(event);
    }
}
