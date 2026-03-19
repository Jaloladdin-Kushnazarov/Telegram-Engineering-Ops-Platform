package com.engops.platform.workitem.repository;

import com.engops.platform.infrastructure.config.JpaAuditingConfig;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.tenantconfig.repository.WorkflowDefinitionRepository;
import com.engops.platform.workitem.model.UpdateType;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.model.WorkItemUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkItemUpdateRepository integration testlari.
 *
 * Deterministic ordering semantikasini real repository/query path orqali isbotlaydi.
 *
 * Tekshiruvlar:
 * - createdAt ASC tartib (eng eski birinchi)
 * - id ASC tie-breaker (bir xil createdAt da)
 * - tenant izolyatsiya (boshqa tenant update'lari oqmaydi)
 * - work item izolyatsiya (boshqa work item update'lari oqmaydi)
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class WorkItemUpdateRepositoryTest {

    @Autowired private WorkItemUpdateRepository workItemUpdateRepository;
    @Autowired private WorkItemRepository workItemRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private WorkflowDefinitionRepository workflowDefinitionRepository;

    private Tenant tenant;
    private WorkItem workItem;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(new Tenant("Test Co", "test-co"));
        WorkflowDefinition workflowDef = workflowDefinitionRepository.save(
                new WorkflowDefinition(tenant.getId(), "Bug Workflow", "BUG"));
        workItem = workItemRepository.save(new WorkItem(
                tenant.getId(), "BUG-1", WorkItemType.BUG,
                workflowDef.getId(), "Test bug", "BUGS", null));
    }

    @Test
    void findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc_returnsOldestFirst() {
        Instant t1 = Instant.parse("2026-03-10T10:00:00Z");
        Instant t2 = Instant.parse("2026-03-10T11:00:00Z");
        Instant t3 = Instant.parse("2026-03-10T12:00:00Z");

        WorkItemUpdate oldest = createUpdate(tenant.getId(), workItem.getId(),
                UpdateType.COMMENT, "Birinchi", t1);
        WorkItemUpdate middle = createUpdate(tenant.getId(), workItem.getId(),
                UpdateType.STATUS_CHANGE, "Ikkinchi", t2);
        WorkItemUpdate newest = createUpdate(tenant.getId(), workItem.getId(),
                UpdateType.COMMENT, "Uchinchi", t3);

        workItemUpdateRepository.saveAll(List.of(newest, oldest, middle));

        List<WorkItemUpdate> result = workItemUpdateRepository
                .findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc(
                        tenant.getId(), workItem.getId());

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getBody()).isEqualTo("Birinchi");
        assertThat(result.get(0).getCreatedAt()).isEqualTo(t1);
        assertThat(result.get(1).getBody()).isEqualTo("Ikkinchi");
        assertThat(result.get(1).getCreatedAt()).isEqualTo(t2);
        assertThat(result.get(2).getBody()).isEqualTo("Uchinchi");
        assertThat(result.get(2).getCreatedAt()).isEqualTo(t3);
    }

    @Test
    void findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc_usesIdAscTieBreaker() {
        Instant sameTime = Instant.parse("2026-03-10T10:00:00Z");

        // Kontrolli UUID'lar — id1 < id2 < id3 leksikografik va UUID tartibda
        UUID id1 = UUID.fromString("00000001-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000002-0000-0000-0000-000000000002");
        UUID id3 = UUID.fromString("00000003-0000-0000-0000-000000000003");

        WorkItemUpdate u1 = createUpdateWithId(id2, tenant.getId(), workItem.getId(),
                UpdateType.COMMENT, "O'rta id", sameTime);
        WorkItemUpdate u2 = createUpdateWithId(id3, tenant.getId(), workItem.getId(),
                UpdateType.STATUS_CHANGE, "Katta id", sameTime);
        WorkItemUpdate u3 = createUpdateWithId(id1, tenant.getId(), workItem.getId(),
                UpdateType.COMMENT, "Kichik id", sameTime);

        // Aralash tartibda saqlash — ordering DB tomonidan bo'lishi kerak
        workItemUpdateRepository.saveAll(List.of(u1, u2, u3));

        List<WorkItemUpdate> result = workItemUpdateRepository
                .findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc(
                        tenant.getId(), workItem.getId());

        assertThat(result).hasSize(3);
        // Barcha createdAt bir xil — id ASC bo'yicha tartiblanishi kerak
        assertThat(result.get(0).getCreatedAt()).isEqualTo(sameTime);
        assertThat(result.get(1).getCreatedAt()).isEqualTo(sameTime);
        assertThat(result.get(2).getCreatedAt()).isEqualTo(sameTime);

        assertThat(result.get(0).getId()).isEqualTo(id1);
        assertThat(result.get(0).getBody()).isEqualTo("Kichik id");
        assertThat(result.get(1).getId()).isEqualTo(id2);
        assertThat(result.get(1).getBody()).isEqualTo("O'rta id");
        assertThat(result.get(2).getId()).isEqualTo(id3);
        assertThat(result.get(2).getBody()).isEqualTo("Katta id");
    }

    @Test
    void findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc_isTenantScoped() {
        Tenant otherTenant = tenantRepository.save(new Tenant("Other Co", "other-co"));

        WorkItemUpdate ownUpdate = createUpdate(tenant.getId(), workItem.getId(),
                UpdateType.COMMENT, "O'z update", Instant.parse("2026-03-10T10:00:00Z"));
        WorkItemUpdate foreignUpdate = createUpdate(otherTenant.getId(), workItem.getId(),
                UpdateType.COMMENT, "Begona update", Instant.parse("2026-03-10T11:00:00Z"));

        workItemUpdateRepository.saveAll(List.of(ownUpdate, foreignUpdate));

        List<WorkItemUpdate> result = workItemUpdateRepository
                .findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc(
                        tenant.getId(), workItem.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBody()).isEqualTo("O'z update");
    }

    @Test
    void findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc_isWorkItemScoped() {
        WorkflowDefinition wfDef = workflowDefinitionRepository.findAll().get(0);
        WorkItem otherWorkItem = workItemRepository.save(new WorkItem(
                tenant.getId(), "BUG-2", WorkItemType.BUG,
                wfDef.getId(), "Boshqa bug", "BUGS", null));

        WorkItemUpdate ownUpdate = createUpdate(tenant.getId(), workItem.getId(),
                UpdateType.COMMENT, "BUG-1 update", Instant.parse("2026-03-10T10:00:00Z"));
        WorkItemUpdate otherWiUpdate = createUpdate(tenant.getId(), otherWorkItem.getId(),
                UpdateType.COMMENT, "BUG-2 update", Instant.parse("2026-03-10T11:00:00Z"));

        workItemUpdateRepository.saveAll(List.of(ownUpdate, otherWiUpdate));

        List<WorkItemUpdate> result = workItemUpdateRepository
                .findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc(
                        tenant.getId(), workItem.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBody()).isEqualTo("BUG-1 update");
    }

    @Test
    void findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc_emptyWhenNoUpdates() {
        List<WorkItemUpdate> result = workItemUpdateRepository
                .findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc(
                        tenant.getId(), workItem.getId());

        assertThat(result).isEmpty();
    }

    // ========== Yordamchi metodlar ==========

    /**
     * Kontrolli createdAt bilan WorkItemUpdate yaratadi.
     * ReflectionTestUtils orqali createdAt o'rnatiladi —
     * entity'da setter yo'q, bu faqat test uchun kerak.
     */
    private WorkItemUpdate createUpdate(UUID tenantId, UUID workItemId,
                                         UpdateType type, String body,
                                         Instant createdAt) {
        WorkItemUpdate update = new WorkItemUpdate(tenantId, workItemId, null, type, body);
        ReflectionTestUtils.setField(update, "createdAt", createdAt);
        return update;
    }

    /**
     * Kontrolli id va createdAt bilan WorkItemUpdate yaratadi.
     * Tie-breaker testlari uchun — bir xil createdAt, farqli id.
     */
    private WorkItemUpdate createUpdateWithId(UUID id, UUID tenantId, UUID workItemId,
                                               UpdateType type, String body,
                                               Instant createdAt) {
        WorkItemUpdate update = new WorkItemUpdate(tenantId, workItemId, null, type, body);
        ReflectionTestUtils.setField(update, "id", id);
        ReflectionTestUtils.setField(update, "createdAt", createdAt);
        return update;
    }
}
