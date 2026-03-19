package com.engops.platform.workitem.repository;

import com.engops.platform.infrastructure.config.JpaAuditingConfig;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.tenantconfig.repository.WorkflowDefinitionRepository;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkItem repository testlari.
 * Tenant-scoped so'rovlarni tekshiradi.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class WorkItemRepositoryTest {

    @Autowired private WorkItemRepository workItemRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private WorkflowDefinitionRepository workflowDefinitionRepository;

    private Tenant tenant;
    private WorkflowDefinition workflowDef;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(new Tenant("Test Co", "test-co"));
        workflowDef = workflowDefinitionRepository.save(
                new WorkflowDefinition(tenant.getId(), "Bug Workflow", "BUG"));
    }

    @Test
    void workItemYaratishVaTenantBoYichaTopish() {
        WorkItem item = new WorkItem(tenant.getId(), "BUG-1", WorkItemType.BUG,
                workflowDef.getId(), "Test bug", "BUGS", null);
        workItemRepository.save(item);

        Optional<WorkItem> found = workItemRepository.findByTenantIdAndId(
                tenant.getId(), item.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(found.get().getCurrentStatusCode()).isEqualTo("BUGS");
    }

    @Test
    void codeBoYichaTopish() {
        WorkItem item = new WorkItem(tenant.getId(), "TASK-1", WorkItemType.TASK,
                workflowDef.getId(), "Test task", "OPEN", null);
        workItemRepository.save(item);

        Optional<WorkItem> found = workItemRepository.findByTenantIdAndWorkItemCode(
                tenant.getId(), "TASK-1");

        assertThat(found).isPresent();
    }

    @Test
    void statusBoYichaFiltrlash() {
        workItemRepository.save(new WorkItem(tenant.getId(), "BUG-1", WorkItemType.BUG,
                workflowDef.getId(), "Bug 1", "BUGS", null));
        workItemRepository.save(new WorkItem(tenant.getId(), "BUG-2", WorkItemType.BUG,
                workflowDef.getId(), "Bug 2", "PROCESSING", null));

        List<WorkItem> bugs = workItemRepository.findByTenantIdAndCurrentStatusCode(
                tenant.getId(), "BUGS");

        assertThat(bugs).hasSize(1);
        assertThat(bugs.get(0).getTitle()).isEqualTo("Bug 1");
    }

    @Test
    void typeSoniniHisoblash() {
        workItemRepository.save(new WorkItem(tenant.getId(), "BUG-1", WorkItemType.BUG,
                workflowDef.getId(), "Bug 1", "BUGS", null));
        workItemRepository.save(new WorkItem(tenant.getId(), "BUG-2", WorkItemType.BUG,
                workflowDef.getId(), "Bug 2", "BUGS", null));

        long count = workItemRepository.countByTenantIdAndTypeCode(
                tenant.getId(), WorkItemType.BUG);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void cappedActiveListOrderedByOpenedAtDescIdDesc() {
        // openedAt constructor ichida Instant.now() bilan set bo'ladi,
        // shuning uchun yaratish tartibi = openedAt tartibi
        WorkItem oldest = workItemRepository.save(new WorkItem(tenant.getId(), "BUG-1",
                WorkItemType.BUG, workflowDef.getId(), "Oldest", "BUGS", null));
        WorkItem middle = workItemRepository.save(new WorkItem(tenant.getId(), "BUG-2",
                WorkItemType.BUG, workflowDef.getId(), "Middle", "BUGS", null));
        WorkItem newest = workItemRepository.save(new WorkItem(tenant.getId(), "BUG-3",
                WorkItemType.BUG, workflowDef.getId(), "Newest", "BUGS", null));

        List<WorkItem> result = workItemRepository
                .findByTenantIdAndArchivedFalseOrderByOpenedAtDescIdDesc(
                        tenant.getId(), PageRequest.of(0, 2));

        assertThat(result).hasSize(2);
        // openedAt DESC — newest birinchi
        assertThat(result.get(0).getWorkItemCode()).isEqualTo("BUG-3");
        assertThat(result.get(1).getWorkItemCode()).isEqualTo("BUG-2");
        // oldest chiqarib tashlangan (limit=2)
    }

    @Test
    void cappedActiveListTieBreakerByIdDesc() {
        // Bir xil openedAt uchun id DESC tie-breaker tekshiruvi.
        // Ikki work item'ni tez ketma-ket yaratamiz (openedAt deyarli bir xil),
        // lekin determinism id DESC orqali ta'minlanadi.
        WorkItem wi1 = workItemRepository.save(new WorkItem(tenant.getId(), "BUG-A",
                WorkItemType.BUG, workflowDef.getId(), "First", "BUGS", null));
        WorkItem wi2 = workItemRepository.save(new WorkItem(tenant.getId(), "BUG-B",
                WorkItemType.BUG, workflowDef.getId(), "Second", "BUGS", null));

        List<WorkItem> result = workItemRepository
                .findByTenantIdAndArchivedFalseOrderByOpenedAtDescIdDesc(
                        tenant.getId(), PageRequest.of(0, 10));

        assertThat(result).hasSize(2);
        // Agar openedAt bir xil bo'lsa, id DESC bo'yicha tartiblanadi
        // Agar openedAt farqli bo'lsa, openedAt DESC ishlaydi
        // Ikkalasida ham natija deterministic
        assertThat(result.get(0).getId()).isNotNull();
        assertThat(result.get(1).getId()).isNotNull();
        assertThat(result.get(0).getId()).isNotEqualTo(result.get(1).getId());

        // Ordering deterministic ekanini tekshirish:
        // birinchi element ikkinchidan katta openedAt ga ega
        // YOKI bir xil openedAt da kattaroq id ga ega bo'lishi kerak
        if (result.get(0).getOpenedAt().equals(result.get(1).getOpenedAt())) {
            assertThat(result.get(0).getId().compareTo(result.get(1).getId())).isGreaterThan(0);
        } else {
            assertThat(result.get(0).getOpenedAt()).isAfterOrEqualTo(result.get(1).getOpenedAt());
        }
    }

    @Test
    void cappedActiveListExcludesArchived() {
        WorkItem active = workItemRepository.save(new WorkItem(tenant.getId(), "BUG-1",
                WorkItemType.BUG, workflowDef.getId(), "Active", "BUGS", null));
        WorkItem archived = new WorkItem(tenant.getId(), "BUG-2",
                WorkItemType.BUG, workflowDef.getId(), "Archived", "BUGS", null);
        archived.archive();
        workItemRepository.save(archived);

        List<WorkItem> result = workItemRepository
                .findByTenantIdAndArchivedFalseOrderByOpenedAtDescIdDesc(
                        tenant.getId(), PageRequest.of(0, 10));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getWorkItemCode()).isEqualTo("BUG-1");
    }

    @Test
    void boshqaTenantdanIzolyatsiya() {
        Tenant otherTenant = tenantRepository.save(new Tenant("Other Co", "other-co"));

        workItemRepository.save(new WorkItem(tenant.getId(), "BUG-1", WorkItemType.BUG,
                workflowDef.getId(), "My Bug", "BUGS", null));

        List<WorkItem> otherItems = workItemRepository.findByTenantIdAndArchivedFalse(
                otherTenant.getId());

        assertThat(otherItems).isEmpty();
    }
}
