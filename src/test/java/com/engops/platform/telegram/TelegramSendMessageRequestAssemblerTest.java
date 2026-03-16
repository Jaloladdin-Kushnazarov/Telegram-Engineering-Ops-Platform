package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TelegramSendMessageRequestAssembler unit testlari.
 *
 * Pure mapping tekshiruvi:
 * - SEND_NEW_MESSAGE command to'g'ri request'ga aylanadi
 * - null guard ishlaydi
 * - qo'llab-quvvatlanmagan operatsiya rad etiladi
 * - keyboard immutability saqlanadi
 * - null keyboard bo'sh list'ga aylanadi
 */
class TelegramSendMessageRequestAssemblerTest {

    private final TelegramSendMessageRequestAssembler assembler =
            new TelegramSendMessageRequestAssembler();

    @Test
    void sendNewMessageCommandMappedCorrectly() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        Long topicId = 42L;

        TelegramInlineKeyboardButton button = new TelegramInlineKeyboardButton(
                "Start Processing", workItemId + ":START_PROCESSING");
        TelegramInlineKeyboardRow row = new TelegramInlineKeyboardRow(List.of(button));

        TelegramDeliveryCommand command = new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                tenantId, workItemId,
                chatBindingId, topicId,
                "Bug | BUG-1\n[BUG-1] Login xato\nStatus: BUGS",
                List.of(row));

        TelegramSendMessageRequest request = assembler.assemble(command);

        assertThat(request.getTenantId()).isEqualTo(tenantId);
        assertThat(request.getWorkItemId()).isEqualTo(workItemId);
        assertThat(request.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(request.getTargetTopicId()).isEqualTo(topicId);
        assertThat(request.getText()).isEqualTo(
                "Bug | BUG-1\n[BUG-1] Login xato\nStatus: BUGS");
        assertThat(request.hasKeyboard()).isTrue();
        assertThat(request.getKeyboard()).hasSize(1);
        assertThat(request.getKeyboard().getFirst().getButtons().getFirst().getText())
                .isEqualTo("Start Processing");
    }

    @Test
    void nullCommandRejected() {
        assertThatThrownBy(() -> assembler.assemble(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null bo'lishi mumkin emas");
    }

    @Test
    void unsupportedOperationRejected() {
        TelegramDeliveryCommand command = new TelegramDeliveryCommand(
                TelegramDeliveryOperation.EDIT_MESSAGE,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 42L,
                "Some text",
                List.of());

        assertThatThrownBy(() -> assembler.assemble(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEND_NEW_MESSAGE")
                .hasMessageContaining("EDIT_MESSAGE");
    }

    @Test
    void keyboardImmutabilityPreserved() {
        TelegramDeliveryCommand command = new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 10L,
                "Test text",
                List.of(new TelegramInlineKeyboardRow(List.of(
                        new TelegramInlineKeyboardButton("Action", "data")))));

        TelegramSendMessageRequest request = assembler.assemble(command);

        assertThatThrownBy(() -> request.getKeyboard().add(
                new TelegramInlineKeyboardRow(List.of(
                        new TelegramInlineKeyboardButton("Fake", "fake")))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullKeyboardBecomesEmptyList() {
        TelegramSendMessageRequest request = new TelegramSendMessageRequest(
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 42L,
                "Test text",
                null);

        assertThat(request.hasKeyboard()).isFalse();
        assertThat(request.getKeyboard()).isEmpty();
    }

    @Test
    void emptyKeyboardCommandProducesEmptyKeyboardRequest() {
        TelegramDeliveryCommand command = new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 55L,
                "Incident | INC-1\n[INC-1] DB down\nStatus: OPEN",
                List.of());

        TelegramSendMessageRequest request = assembler.assemble(command);

        assertThat(request.hasKeyboard()).isFalse();
        assertThat(request.getKeyboard()).isEmpty();
    }
}
