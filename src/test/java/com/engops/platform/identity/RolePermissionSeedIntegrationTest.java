package com.engops.platform.identity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 141 seed contract testi.
 *
 * <p>{@code V6__role_permission_default_bindings.sql} migration faylini classpath'dan
 * matn sifatida o'qib, INSERT INTO permission va INSERT INTO role_permission
 * bog'lanishlarini parse qiladi va default role-permission matritsasi kutilgan
 * holda ekanini tasdiqlaydi.</p>
 *
 * <p><strong>Nima uchun SQL-text parsing?</strong> Test profili
 * ({@code application-test.properties}) {@code spring.flyway.enabled=false} va
 * {@code spring.jpa.hibernate.ddl-auto=create-drop} ishlatadi — H2 in-memory
 * baza Hibernate orqali entity'lardan yaratiladi va Flyway migration'lari ishga
 * tushmaydi. Migration'lar PostgreSQL-specific syntax'dan foydalanadi
 * ({@code TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()}), shuning uchun ularni
 * H2'da to'g'ridan-to'g'ri ijro qilib bo'lmaydi. Yangi DB infrastruktura
 * (Testcontainers, fixture fayllari) qo'shmaslik uchun bu test V6 fayl
 * mazmunini deterministic ravishda parse qiladi va kontraktni tasdiqlaydi.
 * Real production'da Flyway V6'ni odatdagidek PostgreSQL'ga qo'llaydi.</p>
 *
 * <p>Tekshiruvlar:</p>
 * <ul>
 *   <li>V6 TENANT_CONFIG_READ va TENANT_CONFIG_WRITE permission catalog rows
 *       qo'shadi (V2'dagi gap'ni yopadi)</li>
 *   <li>Har bir default role uchun aniq kutilgan permission set bog'langan</li>
 *   <li>Bog'lanishlar soni jami 28 ta (ADMIN 13 + ENGINEER 7 + TESTER 5 +
 *       VIEWER 3)</li>
 *   <li>UUID konstantalari V2 seed fayli bilan moslashadi</li>
 * </ul>
 */
class RolePermissionSeedIntegrationTest {

    // V2 role UUIDs (sealed migration — bu identifikatorlar o'zgarmaydi)
    private static final UUID ADMIN_ROLE_ID =
            UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID ENGINEER_ROLE_ID =
            UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID TESTER_ROLE_ID =
            UUID.fromString("b0000000-0000-0000-0000-000000000003");
    private static final UUID VIEWER_ROLE_ID =
            UUID.fromString("b0000000-0000-0000-0000-000000000004");

    // V2 permission UUIDs (sealed) + V6 permission UUIDs (this migration)
    private static final UUID WORK_ITEM_CREATE =
            UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID WORK_ITEM_VIEW =
            UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID WORK_ITEM_UPDATE =
            UUID.fromString("a0000000-0000-0000-0000-000000000003");
    private static final UUID WORK_ITEM_TRANSITION =
            UUID.fromString("a0000000-0000-0000-0000-000000000004");
    private static final UUID WORK_ITEM_ASSIGN =
            UUID.fromString("a0000000-0000-0000-0000-000000000005");
    private static final UUID TENANT_MANAGE =
            UUID.fromString("a0000000-0000-0000-0000-000000000006");
    private static final UUID MEMBER_MANAGE =
            UUID.fromString("a0000000-0000-0000-0000-000000000007");
    private static final UUID ROLE_MANAGE =
            UUID.fromString("a0000000-0000-0000-0000-000000000008");
    private static final UUID WORKFLOW_MANAGE =
            UUID.fromString("a0000000-0000-0000-0000-000000000009");
    private static final UUID ROUTING_MANAGE =
            UUID.fromString("a0000000-0000-0000-0000-00000000000a");
    private static final UUID ANALYTICS_VIEW =
            UUID.fromString("a0000000-0000-0000-0000-00000000000b");
    private static final UUID TENANT_CONFIG_READ =
            UUID.fromString("a0000000-0000-0000-0000-00000000000c");
    private static final UUID TENANT_CONFIG_WRITE =
            UUID.fromString("a0000000-0000-0000-0000-00000000000d");

    private static String v6Sql;
    private static Map<UUID, Set<UUID>> roleToPermissionIds;

    @BeforeAll
    static void loadAndParseV6() throws IOException {
        v6Sql = readClasspathResource("db/migration/V6__role_permission_default_bindings.sql");
        roleToPermissionIds = parseRolePermissionBindings(v6Sql);
    }

    // ========== Permission catalog additions ==========

    @Test
    void v6FileExistsAndIsNonEmpty() {
        assertThat(v6Sql).isNotNull().isNotBlank();
    }

    @Test
    void v6AddsTenantConfigReadToPermissionCatalog() {
        assertThat(v6Sql).contains("'TENANT_CONFIG_READ'");
        assertThat(v6Sql).contains(TENANT_CONFIG_READ.toString());
    }

    @Test
    void v6AddsTenantConfigWriteToPermissionCatalog() {
        assertThat(v6Sql).contains("'TENANT_CONFIG_WRITE'");
        assertThat(v6Sql).contains(TENANT_CONFIG_WRITE.toString());
    }

    @Test
    void v6PermissionInsertHasExactlyTwoNewRows() {
        // INSERT INTO permission ... VALUES (...), (...);
        // ikkita VALUES tuple bo'lishi shart va boshqa permission INSERT block
        // umuman bo'lmasligi kerak (V2'dagi 11 ta row tegilmaydi).
        Pattern permissionBlock = Pattern.compile(
                "INSERT INTO permission\\s*\\(id,\\s*code,\\s*description\\)\\s*VALUES(.*?);",
                Pattern.DOTALL);
        Matcher m = permissionBlock.matcher(v6Sql);
        assertThat(m.find()).as("V6 must contain a single INSERT INTO permission block").isTrue();

        String tuples = m.group(1);
        // Har bir tuple "(...)," yoki "(...)" — vergullar bo'yicha sanaymiz
        long tupleCount = tuples.chars().filter(c -> c == '(').count();
        assertThat(tupleCount).as("V6 permission tuples").isEqualTo(2);

        assertThat(m.find()).as("V6 must NOT contain a second INSERT INTO permission block").isFalse();
    }

    // ========== Role-permission binding totals ==========

    @Test
    void v6InsertsExactly28RolePermissionBindings() {
        int total = roleToPermissionIds.values().stream()
                .mapToInt(Set::size)
                .sum();
        assertThat(total).isEqualTo(28);
    }

    @Test
    void v6CoversExactlyFourSeededRoles() {
        assertThat(roleToPermissionIds.keySet())
                .containsExactlyInAnyOrder(
                        ADMIN_ROLE_ID, ENGINEER_ROLE_ID,
                        TESTER_ROLE_ID, VIEWER_ROLE_ID);
    }

    // ========== Per-role exact permission set ==========

    @Test
    void adminRoleGrantedAll13Permissions() {
        Set<UUID> adminPermissions = roleToPermissionIds.get(ADMIN_ROLE_ID);
        assertThat(adminPermissions).containsExactlyInAnyOrder(
                WORK_ITEM_CREATE, WORK_ITEM_VIEW, WORK_ITEM_UPDATE,
                WORK_ITEM_TRANSITION, WORK_ITEM_ASSIGN,
                TENANT_MANAGE, MEMBER_MANAGE, ROLE_MANAGE,
                WORKFLOW_MANAGE, ROUTING_MANAGE, ANALYTICS_VIEW,
                TENANT_CONFIG_READ, TENANT_CONFIG_WRITE);
    }

    @Test
    void engineerRoleGrantedExactly7Permissions() {
        Set<UUID> engineerPermissions = roleToPermissionIds.get(ENGINEER_ROLE_ID);
        assertThat(engineerPermissions).containsExactlyInAnyOrder(
                WORK_ITEM_CREATE, WORK_ITEM_VIEW, WORK_ITEM_UPDATE,
                WORK_ITEM_TRANSITION, WORK_ITEM_ASSIGN,
                TENANT_CONFIG_READ, ANALYTICS_VIEW);
        // Engineer tenant config'ni o'zgartira olmasligi kerak.
        assertThat(engineerPermissions).doesNotContain(TENANT_CONFIG_WRITE);
        // Engineer tenant/member/role/workflow/routing'ni umumiy boshqarmasligi kerak.
        assertThat(engineerPermissions).doesNotContain(
                TENANT_MANAGE, MEMBER_MANAGE, ROLE_MANAGE,
                WORKFLOW_MANAGE, ROUTING_MANAGE);
    }

    @Test
    void testerRoleGrantedExactly5Permissions() {
        Set<UUID> testerPermissions = roleToPermissionIds.get(TESTER_ROLE_ID);
        assertThat(testerPermissions).containsExactlyInAnyOrder(
                WORK_ITEM_VIEW, WORK_ITEM_UPDATE, WORK_ITEM_TRANSITION,
                TENANT_CONFIG_READ, ANALYTICS_VIEW);
        // Tester yangi work item yaratmaydi (Engineer'ga tegishli).
        assertThat(testerPermissions).doesNotContain(WORK_ITEM_CREATE);
        // Tester assign qilmaydi.
        assertThat(testerPermissions).doesNotContain(WORK_ITEM_ASSIGN);
        // Tester tenant config'ni o'zgartira olmaydi.
        assertThat(testerPermissions).doesNotContain(TENANT_CONFIG_WRITE);
    }

    @Test
    void viewerRoleGrantedExactly3ReadOnlyPermissions() {
        Set<UUID> viewerPermissions = roleToPermissionIds.get(VIEWER_ROLE_ID);
        assertThat(viewerPermissions).containsExactlyInAnyOrder(
                WORK_ITEM_VIEW, TENANT_CONFIG_READ, ANALYTICS_VIEW);
        // Viewer hech qanday yozish/o'zgartirish/o'tkazish/tayinlash qila olmaydi.
        assertThat(viewerPermissions).doesNotContain(
                WORK_ITEM_CREATE, WORK_ITEM_UPDATE, WORK_ITEM_TRANSITION,
                WORK_ITEM_ASSIGN, TENANT_CONFIG_WRITE);
    }

    // ========== Helpers ==========

    private static String readClasspathResource(String path) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Classpath resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * INSERT INTO role_permission (id, role_id, permission_id) VALUES (uuid1, uuid2, uuid3), ...;
     * blokini parse qiladi va role_id → set(permission_id) map qaytaradi.
     */
    private static Map<UUID, Set<UUID>> parseRolePermissionBindings(String sql) {
        Pattern blockPattern = Pattern.compile(
                "INSERT INTO role_permission\\s*\\(id,\\s*role_id,\\s*permission_id\\)\\s*VALUES(.*?);",
                Pattern.DOTALL);
        Matcher blockMatcher = blockPattern.matcher(sql);
        if (!blockMatcher.find()) {
            throw new AssertionError(
                    "V6 must contain an INSERT INTO role_permission ... VALUES block");
        }
        String tuples = blockMatcher.group(1);
        Pattern tuplePattern = Pattern.compile(
                "\\(\\s*'([0-9a-fA-F-]{36})'\\s*,\\s*'([0-9a-fA-F-]{36})'\\s*,\\s*'([0-9a-fA-F-]{36})'\\s*\\)");
        Matcher tupleMatcher = tuplePattern.matcher(tuples);

        Map<UUID, Set<UUID>> result = new HashMap<>();
        Set<UUID> bindingIdsSeen = new HashSet<>();
        while (tupleMatcher.find()) {
            UUID bindingId = UUID.fromString(tupleMatcher.group(1));
            UUID roleId = UUID.fromString(tupleMatcher.group(2));
            UUID permissionId = UUID.fromString(tupleMatcher.group(3));
            if (!bindingIdsSeen.add(bindingId)) {
                throw new AssertionError("Duplicate role_permission binding id: " + bindingId);
            }
            result.computeIfAbsent(roleId, k -> new HashSet<>()).add(permissionId);
        }
        return result;
    }
}
