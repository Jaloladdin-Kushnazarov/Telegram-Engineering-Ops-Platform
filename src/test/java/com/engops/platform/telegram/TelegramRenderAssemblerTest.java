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
                true,
                chatBindingId, topicId);

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
                false,
                null, null);

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
                false,
                null, null);

        TelegramRenderPayload render = assembler.assemble(payload);

        assertThat(render.getHeaderLine()).isEqualTo("Task | TASK-5");
        assertThat(render.getStatusLine()).isEqualTo("Status: TODO");
        assertThat(render.getDisplayTitle()).isEqualTo("[TASK-5] Deploy script");
    }

    @Test
    void nullPayloadRadEtilishi() {
        assertThatThrownBy(() -> assembler.assemble(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null bo'lishi mumkin emas");
    }
}
