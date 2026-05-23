package com.engops.platform.telegram;

import com.engops.platform.intake.ProjectionPayload;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TelegramRenderAssembler unit testlari.
 *
 * ProjectionPayload → TelegramRenderPayload konvertatsiyasini tekshiradi:
 * - barcha field'lar to'g'ri uzatilganini
 * - render-specific field'lar to'g'ri computed bo'lganini
 * - routing ready va not-ready holatlarni
 * - null guard'ni
 */
class TelegramRenderAssemblerTest {

    private final TelegramRenderAssembler assembler = new TelegramRenderAssembler();

    @Test
    void deliveryReadyPayloadToRenderPayload() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        long topicId = 42L;

        ProjectionPayload payload = new ProjectionPayload(
                tenantId,
                workItemId, "BUG-1", "BUG", "Login xato", "BUGS",
                "[BUG-1] Login xato", "Bug",
                null, null,
                true,
                chatBindingId, topicId,
                null);

        TelegramRenderPayload render = assembler.assemble(payload);

        // Identity fields
        assertThat(render.getTenantId()).isEqualTo(tenantId);
        assertThat(render.getWorkItemId()).isEqualTo(workItemId);
        assertThat(render.getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(render.getWorkItemType()).isEqualTo("BUG");
        assertThat(render.getTitle()).isEqualTo("Login xato");
        assertThat(render.getCurrentStatusCode()).isEqualTo("BUGS");

        // Display fields (from ProjectionPayload)
        assertThat(render.getDisplayTitle()).isEqualTo("[BUG-1] Login xato");
        assertThat(render.getDisplayTypeLabel()).isEqualTo("Bug");

        // Render-specific fields
        assertThat(render.getHeaderLine()).isEqualTo("Bug | BUG-1");
        assertThat(render.getStatusLine()).isEqualTo("Status: BUGS");

        // Phase 194 — priority/severity absent
        assertThat(render.getPriorityCode()).isNull();
        assertThat(render.getSeverityCode()).isNull();

        // Phase 196 — owner display label absent
        assertThat(render.getOwnerDisplayLabel()).isNull();

        // Delivery target
        assertThat(render.isDeliveryReady()).isTrue();
        assertThat(render.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(render.getTargetTopicId()).isEqualTo(topicId);
    }

    @Test
    void deliveryNotReadyPayloadToRenderPayload() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();

        ProjectionPayload payload = new ProjectionPayload(
                tenantId,
                workItemId, "INCIDENT-1", "INCIDENT", "DB down", "OPEN",
                "[INCIDENT-1] DB down", "Incident",
                null, null,
                false,
                null, null,
                null);

        TelegramRenderPayload render = assembler.assemble(payload);

        assertThat(render.getWorkItemCode()).isEqualTo("INCIDENT-1");
        assertThat(render.getDisplayTypeLabel()).isEqualTo("Incident");
        assertThat(render.getHeaderLine()).isEqualTo("Incident | INCIDENT-1");
        assertThat(render.getStatusLine()).isEqualTo("Status: OPEN");
        assertThat(render.isDeliveryReady()).isFalse();
        assertThat(render.getTargetChatBindingId()).isNull();
        assertThat(render.getTargetTopicId()).isNull();
    }

    @Test
    void taskTypeRenderFieldsCorrect() {
        ProjectionPayload payload = new ProjectionPayload(
                UUID.randomUUID(),
                UUID.randomUUID(), "TASK-5", "TASK", "Deploy script", "TODO",
                "[TASK-5] Deploy script", "Task",
                null, null,
                false,
                null, null,
                null);

        TelegramRenderPayload render = assembler.assemble(payload);

        assertThat(render.getHeaderLine()).isEqualTo("Task | TASK-5");
        assertThat(render.getStatusLine()).isEqualTo("Status: TODO");
        assertThat(render.getDisplayTitle()).isEqualTo("[TASK-5] Deploy script");
    }

    /**
     * Phase 194 — render assembler must pass priorityCode / severityCode
     * verbatim from the projection payload to the render payload. The
     * assembler is pure mapping; the renderer alone decides whether to
     * emit optional lines.
     */
    @Test
    void priorityAndSeverityPassedThroughFromPayload() {
        ProjectionPayload payload = new ProjectionPayload(
                UUID.randomUUID(),
                UUID.randomUUID(), "BUG-3", "BUG", "Race condition", "PROCESSING",
                "[BUG-3] Race condition", "Bug",
                "HIGH", "CRITICAL",
                true,
                UUID.randomUUID(), 7L,
                null);

        TelegramRenderPayload render = assembler.assemble(payload);

        assertThat(render.getPriorityCode()).isEqualTo("HIGH");
        assertThat(render.getSeverityCode()).isEqualTo("CRITICAL");
    }

    /**
     * Phase 196 — owner display label passes through verbatim. The assembler
     * never imports identity; it only forwards the pre-resolved String.
     */
    @Test
    void ownerDisplayLabelPassedThroughFromPayload() {
        ProjectionPayload payload = new ProjectionPayload(
                UUID.randomUUID(),
                UUID.randomUUID(), "BUG-4", "BUG", "Owned bug", "BUGS",
                "[BUG-4] Owned bug", "Bug",
                null, null,
                true,
                UUID.randomUUID(), 8L,
                "Bakhrom Yuldashev");

        TelegramRenderPayload render = assembler.assemble(payload);

        assertThat(render.getOwnerDisplayLabel()).isEqualTo("Bakhrom Yuldashev");
    }

    /**
     * Phase 196 — null owner display label propagates as null.
     */
    @Test
    void nullOwnerDisplayLabelPassedThroughAsNull() {
        ProjectionPayload payload = new ProjectionPayload(
                UUID.randomUUID(),
                UUID.randomUUID(), "BUG-5", "BUG", "Unowned", "BUGS",
                "[BUG-5] Unowned", "Bug",
                null, null,
                true,
                UUID.randomUUID(), 8L,
                null);

        TelegramRenderPayload render = assembler.assemble(payload);

        assertThat(render.getOwnerDisplayLabel()).isNull();
    }

    @Test
    void nullPayloadRadEtilishi() {
        assertThatThrownBy(() -> assembler.assemble(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null bo'lishi mumkin emas");
    }
}
