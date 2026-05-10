package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 171 — {@link TelegramCallbackQueryService} unit testlari.
 *
 * <p>Tekshiruvlar:</p>
 * <ul>
 *   <li>{@code callbackQuery == null} → {@code IGNORED_NULL_CALLBACK}
 *       (service exception tashlamaydi).</li>
 *   <li>{@code data == null} yoki {@code data.isBlank()} →
 *       {@code IGNORED_NULL_DATA}. Blank semantics: bo'sh string yoki
 *       faqat whitespace ham {@code IGNORED_NULL_DATA} qaytaradi —
 *       null bilan bir xil kategoriya (operatorga oddiy mental model).</li>
 *   <li>{@code data.length() > 64} → {@code IGNORED_TOO_LONG}.</li>
 *   <li>colon yo'q yoki noto'g'ri pozitsiyada → {@code IGNORED_MALFORMED}.</li>
 *   <li>workItemId UUID emas → {@code IGNORED_MALFORMED}.</li>
 *   <li>action code katalogda yo'q → {@code IGNORED_UNKNOWN_ACTION}.</li>
 *   <li>har bir mavjud action code (5 ta) → {@code ACCEPTED}.</li>
 *   <li>katalogdagi har bir action code 64-bayt callback_data budjetiga
 *       sig'adi (UUID 36 + ":" 1 + ACTION ≤ 27 ≤ 64).</li>
 * </ul>
 */
class TelegramCallbackQueryServiceTest {

    private final TelegramCallbackQueryService service = new TelegramCallbackQueryService();
    private final UUID workItemId = UUID.fromString("7c3b2a4d-1234-4abc-9def-0123456789ab");

    private TelegramCallbackQueryRequest cb(String data) {
        return new TelegramCallbackQueryRequest(
                "cb-id",
                new TelegramCallbackUserRequest(123456789L),
                new TelegramCallbackMessageRequest(
                        555L,
                        new TelegramCallbackChatRequest(-1001234567890L)),
                data);
    }

    // --- Null callback ---

    @Test
    void nullCallbackQueryIgnored() {
        TelegramCallbackQueryService.CallbackOutcome outcome = service.process(null);
        assertThat(outcome).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_NULL_CALLBACK);
    }

    // --- Null / blank data ---

    @Test
    void nullDataIgnored() {
        TelegramCallbackQueryService.CallbackOutcome outcome = service.process(cb(null));
        assertThat(outcome).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_NULL_DATA);
    }

    @Test
    void blankDataIgnoredAsNull() {
        // Documentation note (see service Javadoc): blank/whitespace data is
        // categorized as IGNORED_NULL_DATA — same bucket as actual null,
        // simpler operator mental model.
        TelegramCallbackQueryService.CallbackOutcome outcome = service.process(cb("   "));
        assertThat(outcome).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_NULL_DATA);
    }

    @Test
    void emptyDataIgnoredAsNull() {
        TelegramCallbackQueryService.CallbackOutcome outcome = service.process(cb(""));
        assertThat(outcome).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_NULL_DATA);
    }

    // --- Too long ---

    @Test
    void tooLongDataIgnored() {
        // 65 chars — Telegram'ning 64-bayt cheklovini ataylab oshirib yuboramiz.
        String tooLong = "a".repeat(65);
        TelegramCallbackQueryService.CallbackOutcome outcome = service.process(cb(tooLong));
        assertThat(outcome).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_TOO_LONG);
    }

    // --- Malformed ---

    @Test
    void noColonMalformed() {
        TelegramCallbackQueryService.CallbackOutcome outcome = service.process(cb("nocolonatall"));
        assertThat(outcome).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_MALFORMED);
    }

    @Test
    void leadingColonMalformed() {
        TelegramCallbackQueryService.CallbackOutcome outcome = service.process(cb(":START_PROCESSING"));
        assertThat(outcome).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_MALFORMED);
    }

    @Test
    void trailingColonMalformed() {
        TelegramCallbackQueryService.CallbackOutcome outcome = service.process(cb(workItemId + ":"));
        assertThat(outcome).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_MALFORMED);
    }

    @Test
    void badUuidMalformed() {
        TelegramCallbackQueryService.CallbackOutcome outcome =
                service.process(cb("not-a-uuid:START_PROCESSING"));
        assertThat(outcome).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_MALFORMED);
    }

    // --- Unknown action ---

    @Test
    void unknownActionIgnored() {
        TelegramCallbackQueryService.CallbackOutcome outcome =
                service.process(cb(workItemId + ":SOMETHING_NEW"));
        assertThat(outcome).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_UNKNOWN_ACTION);
    }

    // --- Known actions ---

    @ParameterizedTest
    @ValueSource(strings = {
            "START_PROCESSING",
            "SEND_TO_TESTING",
            "MARK_FIXED",
            "RETURN_TO_BUGS",
            "REOPEN"
    })
    void knownActionAccepted(String actionCode) {
        TelegramCallbackQueryService.CallbackOutcome outcome =
                service.process(cb(workItemId + ":" + actionCode));
        assertThat(outcome).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.ACCEPTED);
    }

    // --- Boundary lock: callback_data budget ---

    @Test
    void hammaActionCatalogiTelegramBudjetigaSigadi() {
        // UUID 36 + ":" 1 + ACTION_CODE = total bytes
        // Telegram limit = 64 → ACTION_CODE max = 27 chars
        for (String code : TelegramCallbackQueryService.KNOWN_ACTION_CODES) {
            int totalBytes = 36 + 1 + code.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            assertThat(totalBytes)
                    .as("Action code '%s' callback_data budjetidan oshmasligi shart "
                            + "(Telegram cheklovi 64 bayt)", code)
                    .isLessThanOrEqualTo(TelegramCallbackQueryService.MAX_CALLBACK_DATA_BYTES);
        }
    }
}
