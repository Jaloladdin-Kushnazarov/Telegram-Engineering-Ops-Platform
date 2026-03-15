package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TelegramDeliveryCommandAssembler unit testlari.
 *
 * Pure mapping tekshiruvi:
 * - delivery-ready message to'g'ri command'ga aylanadi
 * - null guard ishlaydi
 * - targetChatBindingId null bo'lsa fail-fast
 * - targetTopicId null bo'lsa fail-fast
 * - keyboard immutability saqlanadi
 */
class TelegramDeliveryCommandAssemblerTest {

    private final TelegramDeliveryCommandAssembler assembler =
            new TelegramDeliveryCommandAssembler();

    @Test
    void deliveryReadyMessageToSendCommand() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        Long topicId = 42L;

        TelegramInlineKeyboardButton button = new TelegramInlineKeyboardButton(
                "Start Processing", workItemId + ":START_PROCESSING");
        TelegramInlineKeyboardRow row = new TelegramInlineKeyboardRow(List.of(button));

        TelegramMessage message = new TelegramMessage(
                tenantId, workItemId,
                "Bug | BUG-1\n[BUG-1] Login xato\nStatus: BUGS",
                List.of(row),
                chatBindingId, topicId);

        TelegramDeliveryCommand command = assembler.assembleSend(message);

        assertThat(command.getOperation()).isEqualTo(TelegramDeliveryOperation.SEND_NEW_MESSAGE);
        assertThat(command.getTenantId()).isEqualTo(tenantId);
        assertThat(command.getWorkItemId()).isEqualTo(workItemId);
        assertThat(command.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(command.getTargetTopicId()).isEqualTo(topicId);
        assertThat(command.getText()).isEqualTo(
                "Bug | BUG-1\n[BUG-1] Login xato\nStatus: BUGS");
        assertThat(command.hasKeyboard()).isTrue();
        assertThat(command.getKeyboard()).hasSize(1);
        assertThat(command.getKeyboard().getFirst().getButtons().getFirst().getText())
                .isEqualTo("Start Processing");
        assertThat(command.getKeyboard().getFirst().getButtons().getFirst().getCallbackData())
                .isEqualTo(workItemId + ":START_PROCESSING");
    }

    @Test
    void nullMessageRejected() {
        assertThatThrownBy(() -> assembler.assembleSend(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null bo'lishi mumkin emas");
    }

    @Test
    void missingTargetChatBindingRejected() {
        TelegramMessage message = new TelegramMessage(
                UUID.randomUUID(), UUID.randomUUID(),
                "Some text",
                List.of(),
                null, 42L);

        assertThatThrownBy(() -> assembler.assembleSend(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetChatBindingId majburiy");
    }

    @Test
    void missingTargetTopicIdRejected() {
        TelegramMessage message = new TelegramMessage(
                UUID.randomUUID(), UUID.randomUUID(),
                "Some text",
                List.of(),
                UUID.randomUUID(), null);

        assertThatThrownBy(() -> assembler.assembleSend(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetTopicId majburiy");
    }

    @Test
    void immutableKeyboardPreserved() {
        UUID workItemId = UUID.randomUUID();

        TelegramInlineKeyboardButton button = new TelegramInlineKeyboardButton(
                "Mark Fixed", workItemId + ":MARK_FIXED");
        TelegramInlineKeyboardRow row = new TelegramInlineKeyboardRow(List.of(button));

        TelegramMessage message = new TelegramMessage(
                UUID.randomUUID(), workItemId,
                "Bug | BUG-5\n[BUG-5] Crash\nStatus: TESTING",
                List.of(row),
                UUID.randomUUID(), 10L);

        TelegramDeliveryCommand command = assembler.assembleSend(message);

        assertThatThrownBy(() -> command.getKeyboard().add(
                new TelegramInlineKeyboardRow(List.of(
                        new TelegramInlineKeyboardButton("Fake", "fake:data")))))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
