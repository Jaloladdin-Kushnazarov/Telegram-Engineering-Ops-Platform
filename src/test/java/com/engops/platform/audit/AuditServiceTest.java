package com.engops.platform.audit;

import com.engops.platform.audit.model.AuditEvent;
import com.engops.platform.audit.repository.AuditEventRepository;
import com.engops.platform.infrastructure.config.JpaAuditingConfig;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuditEvent persistence testi.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class AuditServiceTest {

    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private TenantRepository tenantRepository;

    @Test
    void auditEventSaqlashVaSorash() {
        Tenant tenant = tenantRepository.save(new Tenant("Audit Tenant", "audit-tenant"));
        UUID entityId = UUID.randomUUID();

        AuditEvent event = new AuditEvent(tenant.getId(), "WORK_ITEM", entityId,
                "CREATED", UUID.randomUUID());
        event.setOldValueJson(null);
        event.setNewValueJson("{\"code\":\"BUG-1\"}");
        auditEventRepository.save(event);

        List<AuditEvent> events = auditEventRepository.findByTenantIdAndEntityTypeAndEntityId(
                tenant.getId(), "WORK_ITEM", entityId);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("CREATED");
        assertThat(events.get(0).getNewValueJson()).contains("BUG-1");
        assertThat(events.get(0).getOccurredAt()).isNotNull();
    }
}
