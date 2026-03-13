package com.engops.platform.intake;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ProjectionAssembler unit testlari.
 *
 * Pure mapping tekshiruvi — barcha field'lar to'g'ri uzatilganini tasdiqlaydi.
 */
class ProjectionAssemblerTest {

    private final ProjectionAssembler assembler = new ProjectionAssembler();

    @Test
    void deliveryReadyTargetToProjectionPayload() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        long topicId = 42L;

        PreparedDeliveryTarget target = new PreparedDeliveryTarget(
                tenantId,
                workItemId, "BUG-1", "BUG", "Login xato", "BUGS",
                true,
                chatBindingId, topicId);

        ProjectionPayload payload = assembler.assemble(target);

        assertThat(payload.getTenantId()).isEqualTo(tenantId);
        assertThat(payload.getWorkItemId()).isEqualTo(workItemId);
        assertThat(payload.getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(payload.getWorkItemType()).isEqualTo("BUG");
        assertThat(payload.getTitle()).isEqualTo("Login xato");
        assertThat(payload.getCurrentStatusCode()).isEqualTo("BUGS");
        assertThat(payload.isDeliveryReady()).isTrue();
        assertThat(payload.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(payload.getTargetTopicId()).isEqualTo(topicId);
    }

    @Test
    void deliveryNotReadyTargetToProjectionPayload() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();

        PreparedDeliveryTarget target = new PreparedDeliveryTarget(
                tenantId,
                workItemId, "INCIDENT-1", "INCIDENT", "DB down", "OPEN",
                false,
                null, null);

        ProjectionPayload payload = assembler.assemble(target);

        assertThat(payload.getTenantId()).isEqualTo(tenantId);
        assertThat(payload.getWorkItemId()).isEqualTo(workItemId);
        assertThat(payload.getWorkItemCode()).isEqualTo("INCIDENT-1");
        assertThat(payload.getWorkItemType()).isEqualTo("INCIDENT");
        assertThat(payload.getTitle()).isEqualTo("DB down");
        assertThat(payload.getCurrentStatusCode()).isEqualTo("OPEN");
        assertThat(payload.isDeliveryReady()).isFalse();
        assertThat(payload.getTargetChatBindingId()).isNull();
        assertThat(payload.getTargetTopicId()).isNull();
    }

    @Test
    void nullTargetRadEtilishi() {
        assertThatThrownBy(() -> assembler.assemble(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null bo'lishi mumkin emas");
    }
}
