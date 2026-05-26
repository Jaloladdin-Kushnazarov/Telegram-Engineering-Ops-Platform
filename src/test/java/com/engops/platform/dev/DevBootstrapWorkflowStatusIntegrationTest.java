package com.engops.platform.dev;

import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.repository.RoleRepository;
import com.engops.platform.intake.IntakeApplicationService;
import com.engops.platform.intake.IntakeCommand;
import com.engops.platform.intake.IntakeResult;
import com.engops.platform.workitem.OperationalAuthorizationService;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.repository.WorkItemCounterRepository;
import com.engops.platform.workitem.repository.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 221 — regression lock: bootstrap'dan keyin {@code IntakeApplicationService.submit}
 * demo tenant uchun {@code initialStatusCode}'siz ham muvaffaqiyatli ishlashi kerak.
 *
 * <p>Bu test bug'ni qulflaydi: Phase 221 fix'dan OLDIN
 * {@code resolveInitialStatus} {@code NO_INITIAL_STATUS}
 * ({@code "'Demo ... Workflow' workflow ta'rifida boshlang'ich status topilmadi"})
 * tashlardi, chunki {@link DevBootstrapInitializer} workflow'larni status'siz
 * yaratardi. Fix'dan KEYIN REPORTED status resolve qilinadi.</p>
 *
 * <p><strong>Phase 222 (kengaytirilgan scope):</strong> bu test endi
 * {@code work_item_counter} regressiyasini ham qulflaydi — demo kodlar
 * (BUG-1..BUG-9, TASK-4..TASK-10) bilan to'qnashmasdan yangi work item
 * yaratilishini tasdiqlaydi (BUG-10, TASK-11.., INCIDENT-1..).</p>
 *
 * <p>Authorization ({@link OperationalAuthorizationService}) {@code @MockBean}
 * bilan bypass qilinadi — test profile'da Flyway off, V6 role_permission
 * binding'lari yo'q. Bu test status resolution + code generation'ni tekshiradi,
 * authorization'ni emas. Authorization servisi MODIFIKATSIYA QILINMAYDI — faqat
 * test kontekstida mock bean almashtiriladi.</p>
 */
@SpringBootTest(classes = com.engops.platform.EngOpsPlatformApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.dev-mode.enabled=true",
        "app.security.jwt.hmac-secret=test-only-secret-padded-to-be-32-bytes-long-enough"
})
class DevBootstrapWorkflowStatusIntegrationTest {

    @Autowired
    private DevBootstrapInitializer initializer;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private IntakeApplicationService intakeApplicationService;
    @Autowired
    private WorkItemRepository workItemRepository;
    @Autowired
    private WorkItemCounterRepository workItemCounterRepository;

    /** Auth bypass — status resolution + code generation'ga fokus. */
    @MockBean
    private OperationalAuthorizationService operationalAuthorizationService;

    @BeforeEach
    void seedAdminRoleAndBootstrap() {
        if (roleRepository.findByCode("ADMIN").isEmpty()) {
            roleRepository.save(new Role("ADMIN", "Administrator", true));
        }
        initializer.run(null);

        // Deterministik baseline: bu testlar @Transactional EMAS (pastga qarang),
        // shuning uchun submit() commit qiladi va kontekst DB'si test metodlari
        // orasida ulashiladi. Har test toza holatdan boshlashi uchun:
        //  1) faqat TEST yaratgan item'larni ("regression test%") o'chiramiz —
        //     demo item'lar (BUG-1..BUG-9, TASK-4..TASK-10) SAQLANADI, ular
        //     code-generator to'qnashuvi regressiyasini ushlash uchun zarur;
        //  2) counter'larni o'chirib, repair orqali qayta tiklaymiz
        //     (BUG=10, INCIDENT=1, TASK=11) — har test bir xil nuqtadan boshlaydi.
        List<WorkItem> testCreated = workItemRepository
                .findByTenantIdAndArchivedFalse(DevBootstrapInitializer.BOOTSTRAP_TENANT_ID)
                .stream()
                .filter(w -> w.getTitle() != null && w.getTitle().startsWith("regression test"))
                .toList();
        workItemRepository.deleteAll(testCreated);
        workItemCounterRepository.deleteAll();
        initializer.run(null);
    }

    // @Transactional ATAYLAB ishlatilmaydi: @BeforeEach seed bilan bir xil
    // session bo'lsa, parent WorkflowDefinition'ning in-memory statuses
    // collection'i bo'sh qoladi (seed saveAll orqali yoziladi). Real dev
    // oqimida @BeforeEach commit qilib session yopiladi va submit() fresh
    // lazy-load qiladi — shuni aks ettiramiz.

    @Test
    void afterBootstrap_firstBugSubmitProducesBugCode10_notCollision() {
        // Phase 221 + Phase 222 regression lock — foydalanuvchi xabar bergan
        // simptom: "Create work item bilan BUG type ishlamayapti."
        // P221 oldin: NO_INITIAL_STATUS. P222 oldin: DataIntegrityViolationException
        // (BUG-1 seed bilan to'qnashadi). Ikkalasidan keyin: "BUG-10".
        IntakeResult result = submitDemo(WorkItemType.BUG);
        assertThat(result.getCurrentStatusCode()).isEqualTo("REPORTED");
        assertThat(result.getWorkItemCode()).isEqualTo("BUG-10");
    }

    @Test
    void afterBootstrap_multipleSubmitsAcrossTypes_noCollision() {
        // BUG: 1 submit → BUG-10
        assertThat(submitDemo(WorkItemType.BUG).getWorkItemCode()).isEqualTo("BUG-10");

        // INCIDENT: prefix mismatch (seed "INC-", generator "INCIDENT-") → 1, 2
        assertThat(submitDemo(WorkItemType.INCIDENT).getWorkItemCode()).isEqualTo("INCIDENT-1");
        assertThat(submitDemo(WorkItemType.INCIDENT).getWorkItemCode()).isEqualTo("INCIDENT-2");

        // TASK: 4 submit (pre-fix shu yerda 4-da to'qnashardi) → 11..14
        assertThat(submitDemo(WorkItemType.TASK).getWorkItemCode()).isEqualTo("TASK-11");
        assertThat(submitDemo(WorkItemType.TASK).getWorkItemCode()).isEqualTo("TASK-12");
        assertThat(submitDemo(WorkItemType.TASK).getWorkItemCode()).isEqualTo("TASK-13");
        assertThat(submitDemo(WorkItemType.TASK).getWorkItemCode()).isEqualTo("TASK-14");
    }

    private IntakeResult submitDemo(WorkItemType type) {
        IntakeCommand command = IntakeCommand.builder()
                .tenantId(DevBootstrapInitializer.BOOTSTRAP_TENANT_ID)
                .createdByUserId(DevBootstrapInitializer.BOOTSTRAP_ADMIN_USER_ID)
                .typeCode(type)
                .title("regression test " + type)
                .actionSource("TEST")
                .build();
        return intakeApplicationService.submit(command);
    }
}
