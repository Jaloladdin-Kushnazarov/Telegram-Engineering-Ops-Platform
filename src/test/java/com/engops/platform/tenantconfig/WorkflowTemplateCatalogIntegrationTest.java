package com.engops.platform.tenantconfig;

import com.engops.platform.infrastructure.config.JpaAuditingConfig;
import com.engops.platform.tenantconfig.model.WorkflowTemplate;
import com.engops.platform.tenantconfig.model.WorkflowTemplateStatus;
import com.engops.platform.tenantconfig.model.WorkflowTemplateTransition;
import com.engops.platform.tenantconfig.repository.WorkflowTemplateRepository;
import com.engops.platform.tenantconfig.repository.WorkflowTemplateStatusRepository;
import com.engops.platform.tenantconfig.repository.WorkflowTemplateTransitionRepository;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Workflow template katalogi uchun integratsiya testlari.
 *
 * IKKI QISMDAN IBORAT:
 * 1) Tashqi {@code @DataJpaTest} — H2 in-memory orqali entity mapping va
 *    repositoriyalar xulq-atvori (uniqueness, ordering) tekshiriladi.
 *    Test profili {@code application-test.properties}:
 *    {@code ddl-auto=create-drop} va {@code spring.flyway.enabled=false} —
 *    shu sababli V7 seed bloki bu testlarda yuklanmaydi; seed kontrakti
 *    pastdagi {@link V7SeedContract} ichida tekshiriladi.
 * 2) Nested {@link V7SeedContract} — V7 SQL faylini classpath'dan o'qib,
 *    uning struktura va seed mazmunini tekshiradi (AC12..AC14).
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class WorkflowTemplateCatalogIntegrationTest {

    @Autowired private WorkflowTemplateRepository templateRepository;
    @Autowired private WorkflowTemplateStatusRepository statusRepository;
    @Autowired private WorkflowTemplateTransitionRepository transitionRepository;

    private WorkflowTemplate bugMin;
    private WorkflowTemplate taskBasic;

    @BeforeEach
    void setUp() {
        bugMin = templateRepository.save(new WorkflowTemplate(
                "BUG_MINIMAL", "Bug Min", WorkItemType.BUG, "MVP bug flow"));
        taskBasic = templateRepository.save(new WorkflowTemplate(
                "TASK_BASIC", "Task Basic", WorkItemType.TASK, null));
    }

    @Test
    void findAllByOrderByCodeAsc_alfavitTartibdaQaytaradi() {
        List<WorkflowTemplate> all = templateRepository.findAllByOrderByCodeAsc();
        assertThat(all).extracting(WorkflowTemplate::getCode)
                .containsExactly("BUG_MINIMAL", "TASK_BASIC");
    }

    @Test
    void findByCode_mavjudShablonniQaytaradi() {
        assertThat(templateRepository.findByCode("BUG_MINIMAL")).isPresent();
    }

    @Test
    void findByCode_nomalumKodEmpty() {
        assertThat(templateRepository.findByCode("DOES_NOT_EXIST")).isEmpty();
    }

    @Test
    void duplicateCode_dataIntegrityViolation() {
        WorkflowTemplate duplicate = new WorkflowTemplate(
                "BUG_MINIMAL", "Duplicate", WorkItemType.BUG, null);
        assertThatThrownBy(() -> {
            templateRepository.save(duplicate);
            templateRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listStatuses_statusOrderBoYichaTartiblangan() {
        statusRepository.save(new WorkflowTemplateStatus(bugMin, "FIXED", "Fixed", false, 4));
        statusRepository.save(new WorkflowTemplateStatus(bugMin, "BUGS", "Bugs", true, 1));
        statusRepository.save(new WorkflowTemplateStatus(bugMin, "PROCESSING", "Processing", false, 2));
        statusRepository.save(new WorkflowTemplateStatus(bugMin, "TESTING", "Testing", false, 3));
        statusRepository.flush();

        List<WorkflowTemplateStatus> ordered =
                statusRepository.findAllByTemplate_IdOrderByStatusOrderAsc(bugMin.getId());

        assertThat(ordered).extracting(WorkflowTemplateStatus::getStatusCode)
                .containsExactly("BUGS", "PROCESSING", "TESTING", "FIXED");
        assertThat(ordered.get(0).isInitial()).isTrue();
    }

    @Test
    void duplicateStatusCodeInSameTemplate_dataIntegrityViolation() {
        statusRepository.save(new WorkflowTemplateStatus(bugMin, "BUGS", "Bugs", true, 1));
        statusRepository.flush();

        WorkflowTemplateStatus duplicate =
                new WorkflowTemplateStatus(bugMin, "BUGS", "Bugs Again", false, 99);
        assertThatThrownBy(() -> {
            statusRepository.save(duplicate);
            statusRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameStatusCodeAcrossDifferentTemplates_isAllowed() {
        statusRepository.save(new WorkflowTemplateStatus(bugMin, "IN_PROGRESS", "In Progress", false, 1));
        statusRepository.save(new WorkflowTemplateStatus(taskBasic, "IN_PROGRESS", "In Progress", false, 1));
        statusRepository.flush();

        assertThat(statusRepository.findAllByTemplate_IdOrderByStatusOrderAsc(bugMin.getId()))
                .hasSize(1);
        assertThat(statusRepository.findAllByTemplate_IdOrderByStatusOrderAsc(taskBasic.getId()))
                .hasSize(1);
    }

    @Test
    void listTransitions_fromToBoYichaBarqarorTartibda() {
        transitionRepository.save(new WorkflowTemplateTransition(bugMin, "TESTING", "FIXED", "Mark Fixed"));
        transitionRepository.save(new WorkflowTemplateTransition(bugMin, "BUGS", "PROCESSING", "Start"));
        transitionRepository.save(new WorkflowTemplateTransition(bugMin, "PROCESSING", "TESTING", "Mark Ready for Test"));
        transitionRepository.flush();

        List<WorkflowTemplateTransition> ordered =
                transitionRepository.findAllByTemplate_IdOrderByFromStatusCodeAscToStatusCodeAsc(bugMin.getId());

        assertThat(ordered).extracting(t -> t.getFromStatusCode() + "->" + t.getToStatusCode())
                .containsExactly("BUGS->PROCESSING", "PROCESSING->TESTING", "TESTING->FIXED");
    }

    @Test
    void duplicateTransitionInSameTemplate_dataIntegrityViolation() {
        transitionRepository.save(new WorkflowTemplateTransition(bugMin, "BUGS", "PROCESSING", "Start"));
        transitionRepository.flush();

        WorkflowTemplateTransition duplicate =
                new WorkflowTemplateTransition(bugMin, "BUGS", "PROCESSING", "Restart");
        assertThatThrownBy(() -> {
            transitionRepository.save(duplicate);
            transitionRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameTransitionAcrossDifferentTemplates_isAllowed() {
        transitionRepository.save(new WorkflowTemplateTransition(bugMin, "A", "B", "labelBM"));
        transitionRepository.save(new WorkflowTemplateTransition(taskBasic, "A", "B", "labelTB"));
        transitionRepository.flush();

        assertThat(transitionRepository
                .findAllByTemplate_IdOrderByFromStatusCodeAscToStatusCodeAsc(bugMin.getId())).hasSize(1);
        assertThat(transitionRepository
                .findAllByTemplate_IdOrderByFromStatusCodeAscToStatusCodeAsc(taskBasic.getId())).hasSize(1);
    }

    @Test
    void repositoryQueries_filterByTemplateId() {
        statusRepository.save(new WorkflowTemplateStatus(bugMin, "BUGS", "Bugs", true, 1));
        statusRepository.save(new WorkflowTemplateStatus(bugMin, "FIXED", "Fixed", false, 2));
        statusRepository.save(new WorkflowTemplateStatus(taskBasic, "TODO", "To Do", true, 1));
        statusRepository.flush();

        List<WorkflowTemplateStatus> bugMinStatuses =
                statusRepository.findAllByTemplate_IdOrderByStatusOrderAsc(bugMin.getId());
        assertThat(bugMinStatuses).hasSize(2);
        assertThat(bugMinStatuses).allMatch(s -> s.getTemplate().getId().equals(bugMin.getId()));
    }

    // =================================================================
    // V7 SQL faylining SEED kontrakti — Flyway test profilida o'chirilgan
    // bo'lganligi sababli, V7 mazmunini text bo'yicha tekshiramiz.
    // =================================================================
    @Nested
    class V7SeedContract {

        private static final String V7_RESOURCE = "db/migration/V7__workflow_template_catalog.sql";

        private String readV7() throws IOException {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(V7_RESOURCE)) {
                assertThat(is).as("V7 migration resource topilmadi").isNotNull();
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        @Test
        void v7MigrationFileExists() throws IOException {
            String sql = readV7();
            assertThat(sql).contains("CREATE TABLE workflow_template");
            assertThat(sql).contains("CREATE TABLE workflow_template_status");
            assertThat(sql).contains("CREATE TABLE workflow_template_transition");
        }

        @Test
        void workItemType_checkConstraintMavjud() throws IOException {
            String sql = readV7().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
            assertThat(sql).contains("CHECK (WORK_ITEM_TYPE IN ('BUG', 'INCIDENT', 'TASK'))");
        }

        @Test
        void cascadeDelete_FKBilanBelgilangan() throws IOException {
            String sql = readV7();
            assertThat(sql).containsPattern("REFERENCES\\s+workflow_template\\(id\\)\\s+ON\\s+DELETE\\s+CASCADE");
        }

        @Test
        void aniq4TaShablonSeed() throws IOException {
            String sql = readV7();
            long templateInserts = countTemplateRows(sql, "aaaaaaaa-aaaa-aaaa-aaaa-");
            assertThat(templateInserts).isEqualTo(4);

            assertThat(sql).contains("'BUG_MINIMAL'");
            assertThat(sql).contains("'BUG_FULL'");
            assertThat(sql).contains("'INCIDENT_BASIC'");
            assertThat(sql).contains("'TASK_BASIC'");
        }

        @Test
        void aniq20TaStatusSeed() throws IOException {
            String sql = readV7();
            long statusRows = countTemplateRows(sql, "bbbbbbbb-bbbb-bbbb-bbbb-");
            assertThat(statusRows).isEqualTo(20L);
        }

        @Test
        void aniq21TaTransitionSeed() throws IOException {
            String sql = readV7();
            long transitionRows = countTemplateRows(sql, "cccccccc-cccc-cccc-cccc-");
            assertThat(transitionRows).isEqualTo(21L);
        }

        @Test
        void bugMinimal_4Status5Transition() throws IOException {
            String sql = readV7();
            assertThat(rowsContainingTemplateUuid(sql, "aaaaaaaa-aaaa-aaaa-aaaa-000000000001", "bbbbbbbb"))
                    .isEqualTo(4);
            assertThat(rowsContainingTemplateUuid(sql, "aaaaaaaa-aaaa-aaaa-aaaa-000000000001", "cccccccc"))
                    .isEqualTo(5);
        }

        @Test
        void bugFull_7Status8Transition() throws IOException {
            String sql = readV7();
            assertThat(rowsContainingTemplateUuid(sql, "aaaaaaaa-aaaa-aaaa-aaaa-000000000002", "bbbbbbbb"))
                    .isEqualTo(7);
            assertThat(rowsContainingTemplateUuid(sql, "aaaaaaaa-aaaa-aaaa-aaaa-000000000002", "cccccccc"))
                    .isEqualTo(8);
        }

        @Test
        void incidentBasic_5Status4Transition() throws IOException {
            String sql = readV7();
            assertThat(rowsContainingTemplateUuid(sql, "aaaaaaaa-aaaa-aaaa-aaaa-000000000003", "bbbbbbbb"))
                    .isEqualTo(5);
            assertThat(rowsContainingTemplateUuid(sql, "aaaaaaaa-aaaa-aaaa-aaaa-000000000003", "cccccccc"))
                    .isEqualTo(4);
        }

        @Test
        void taskBasic_4Status4Transition() throws IOException {
            String sql = readV7();
            assertThat(rowsContainingTemplateUuid(sql, "aaaaaaaa-aaaa-aaaa-aaaa-000000000004", "bbbbbbbb"))
                    .isEqualTo(4);
            assertThat(rowsContainingTemplateUuid(sql, "aaaaaaaa-aaaa-aaaa-aaaa-000000000004", "cccccccc"))
                    .isEqualTo(4);
        }

        @Test
        void har1Shablonda1InitialStatus() throws IOException {
            String sql = readV7();
            for (String templateUuid : List.of(
                    "aaaaaaaa-aaaa-aaaa-aaaa-000000000001",
                    "aaaaaaaa-aaaa-aaaa-aaaa-000000000002",
                    "aaaaaaaa-aaaa-aaaa-aaaa-000000000003",
                    "aaaaaaaa-aaaa-aaaa-aaaa-000000000004")) {
                long initialCount = countInitialRowsForTemplate(sql, templateUuid);
                assertThat(initialCount)
                        .as("Template " + templateUuid + " uchun aniq 1 ta initial status bo'lishi shart")
                        .isEqualTo(1);
            }
        }

        @Test
        void bugMinimal_initialBUGS() throws IOException {
            String sql = readV7();
            assertThat(initialStatusCode(sql, "aaaaaaaa-aaaa-aaaa-aaaa-000000000001"))
                    .isEqualTo("BUGS");
        }

        @Test
        void bugFull_initialTRIAGE() throws IOException {
            String sql = readV7();
            assertThat(initialStatusCode(sql, "aaaaaaaa-aaaa-aaaa-aaaa-000000000002"))
                    .isEqualTo("TRIAGE");
        }

        @Test
        void incidentBasic_initialREPORTED() throws IOException {
            String sql = readV7();
            assertThat(initialStatusCode(sql, "aaaaaaaa-aaaa-aaaa-aaaa-000000000003"))
                    .isEqualTo("REPORTED");
        }

        @Test
        void taskBasic_initialTODO() throws IOException {
            String sql = readV7();
            assertThat(initialStatusCode(sql, "aaaaaaaa-aaaa-aaaa-aaaa-000000000004"))
                    .isEqualTo("TODO");
        }

        @Test
        void bugMinimal_statusesMatchBootstrapWorkflow() throws IOException {
            String sql = readV7();
            String bugMinId = "aaaaaaaa-aaaa-aaaa-aaaa-000000000001";
            assertThat(containsTemplateStatusCode(sql, bugMinId, "BUGS")).isTrue();
            assertThat(containsTemplateStatusCode(sql, bugMinId, "PROCESSING")).isTrue();
            assertThat(containsTemplateStatusCode(sql, bugMinId, "TESTING")).isTrue();
            assertThat(containsTemplateStatusCode(sql, bugMinId, "FIXED")).isTrue();
        }

        @Test
        void bugMinimal_transitionsMatchBootstrapWorkflow() throws IOException {
            String sql = readV7();
            String bugMinId = "aaaaaaaa-aaaa-aaaa-aaaa-000000000001";
            assertThat(containsTransition(sql, bugMinId, "BUGS", "PROCESSING")).isTrue();
            assertThat(containsTransition(sql, bugMinId, "PROCESSING", "TESTING")).isTrue();
            assertThat(containsTransition(sql, bugMinId, "TESTING", "FIXED")).isTrue();
            assertThat(containsTransition(sql, bugMinId, "TESTING", "BUGS")).isTrue();
            assertThat(containsTransition(sql, bugMinId, "FIXED", "BUGS")).isTrue();
        }

        @Test
        void har1UuidYagona() throws IOException {
            String sql = readV7();
            Set<String> templates = pkUuidsByPrefix(sql, "aaaaaaaa-");
            Set<String> statuses = pkUuidsByPrefix(sql, "bbbbbbbb-");
            Set<String> transitions = pkUuidsByPrefix(sql, "cccccccc-");
            assertThat(templates).hasSize(4);
            assertThat(statuses).hasSize(20);
            assertThat(transitions).hasSize(21);
        }

        private Set<String> pkUuidsByPrefix(String sql, String prefix) {
            Pattern p = Pattern.compile(
                    "^\\s*\\('(" + Pattern.quote(prefix) + "[0-9a-f-]+)'",
                    Pattern.MULTILINE);
            Matcher m = p.matcher(sql);
            Set<String> seen = new java.util.HashSet<>();
            while (m.find()) {
                String uuid = m.group(1);
                if (!seen.add(uuid)) {
                    throw new AssertionError("Duplicate seed UUID: " + uuid);
                }
            }
            return seen;
        }

        // --- Helpers ---

        private long countTemplateRows(String sql, String uuidPrefix) {
            Pattern p = Pattern.compile("^\\s*\\('" + Pattern.quote(uuidPrefix), Pattern.MULTILINE);
            return p.matcher(sql).results().count();
        }

        private long rowsContainingTemplateUuid(String sql, String templateUuid, String rowKindPrefix) {
            Pattern p = Pattern.compile(
                    "^\\s*\\('" + Pattern.quote(rowKindPrefix) + "[0-9a-f-]+',\\s*'" + Pattern.quote(templateUuid) + "'",
                    Pattern.MULTILINE);
            return p.matcher(sql).results().count();
        }

        private long countInitialRowsForTemplate(String sql, String templateUuid) {
            String[] lines = sql.split("\\r?\\n");
            return java.util.Arrays.stream(lines)
                    .filter(l -> l.contains(templateUuid))
                    .filter(l -> l.contains("bbbbbbbb"))
                    .filter(l -> l.matches(".*,\\s*TRUE\\s*,\\s*\\d+\\)[,;].*"))
                    .count();
        }

        private String initialStatusCode(String sql, String templateUuid) {
            String[] lines = sql.split("\\r?\\n");
            for (String l : lines) {
                if (l.contains(templateUuid)
                        && l.contains("bbbbbbbb")
                        && l.matches(".*,\\s*TRUE\\s*,\\s*\\d+\\)[,;].*")) {
                    Matcher m = Pattern.compile("'([A-Z_]+)',\\s*'[^']*',\\s*TRUE").matcher(l);
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            }
            return null;
        }

        private boolean containsTemplateStatusCode(String sql, String templateUuid, String statusCode) {
            String[] lines = sql.split("\\r?\\n");
            for (String l : lines) {
                if (l.contains(templateUuid)
                        && l.contains("bbbbbbbb")
                        && l.contains("'" + statusCode + "'")) {
                    return true;
                }
            }
            return false;
        }

        private boolean containsTransition(String sql, String templateUuid, String from, String to) {
            String[] lines = sql.split("\\r?\\n");
            for (String l : lines) {
                if (l.contains(templateUuid)
                        && l.contains("cccccccc")
                        && l.contains("'" + from + "'")
                        && l.contains("'" + to + "'")) {
                    return true;
                }
            }
            return false;
        }
    }
}
