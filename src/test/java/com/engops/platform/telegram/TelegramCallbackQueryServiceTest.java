package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 171/173 — {@link TelegramCallbackQueryService} unit testlari.
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
 *   <li>har bir mavjud action code (5 ta) → {@code ACCEPTED} va parsed
 *       maydonlar to'ldiriladi.</li>
 *   <li>katalogdagi har bir action code 64-bayt callback_data budjetiga
 *       sig'adi (UUID 36 + ":" 1 + ACTION ≤ 27 ≤ 64).</li>
 *   <li>Phase 173: ignored outcome'lar uchun {@code workItemId} va
 *       {@code actionCode} natija ichida {@code null}.</li>
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
        TelegramCallbackParseResult result = service.process(null);
        assertThat(result.outcome()).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_NULL_CALLBACK);
        assertThat(result.workItemId()).isNull();
        assertThat(result.actionCode()).isNull();
    }

    // --- Null / blank data ---

    @Test
    void nullDataIgnored() {
        TelegramCallbackParseResult result = service.process(cb(null));
        assertThat(result.outcome()).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_NULL_DATA);
        assertThat(result.workItemId()).isNull();
        assertThat(result.actionCode()).isNull();
    }

    @Test
    void blankDataIgnoredAsNull() {
        // Documentation note (see service Javadoc): blank/whitespace data is
        // categorized as IGNORED_NULL_DATA — same bucket as actual null,
        // simpler operator mental model.
        TelegramCallbackParseResult result = service.process(cb("   "));
        assertThat(result.outcome()).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_NULL_DATA);
    }

    @Test
    void emptyDataIgnoredAsNull() {
        TelegramCallbackParseResult result = service.process(cb(""));
        assertThat(result.outcome()).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_NULL_DATA);
    }

    // --- Too long ---

    @Test
    void tooLongDataIgnored() {
        // 65 chars — Telegram'ning 64-bayt cheklovini ataylab oshirib yuboramiz.
        String tooLong = "a".repeat(65);
        TelegramCallbackParseResult result = service.process(cb(tooLong));
        assertThat(result.outcome()).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_TOO_LONG);
        assertThat(result.workItemId()).isNull();
        assertThat(result.actionCode()).isNull();
    }

    // --- Malformed ---

    @Test
    void noColonMalformed() {
        TelegramCallbackParseResult result = service.process(cb("nocolonatall"));
        assertThat(result.outcome()).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_MALFORMED);
    }

    @Test
    void leadingColonMalformed() {
        TelegramCallbackParseResult result = service.process(cb(":START_PROCESSING"));
        assertThat(result.outcome()).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_MALFORMED);
    }

    @Test
    void trailingColonMalformed() {
        TelegramCallbackParseResult result = service.process(cb(workItemId + ":"));
        assertThat(result.outcome()).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_MALFORMED);
    }

    @Test
    void badUuidMalformed() {
        TelegramCallbackParseResult result =
                service.process(cb("not-a-uuid:START_PROCESSING"));
        assertThat(result.outcome()).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_MALFORMED);
    }

    // --- Unknown action ---

    @Test
    void unknownActionIgnored() {
        TelegramCallbackParseResult result =
                service.process(cb(workItemId + ":SOMETHING_NEW"));
        assertThat(result.outcome()).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.IGNORED_UNKNOWN_ACTION);
        // Phase 173: unknown action holatida ham parser parsed UUID'ni
        // result'da ataylab ochmaydi — orchestrator hech qachon shu
        // outcome uchun chaqirilmaydi.
        assertThat(result.workItemId()).isNull();
        assertThat(result.actionCode()).isNull();
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
        TelegramCallbackParseResult result =
                service.process(cb(workItemId + ":" + actionCode));
        assertThat(result.outcome()).isEqualTo(TelegramCallbackQueryService.CallbackOutcome.ACCEPTED);
        assertThat(result.workItemId()).isEqualTo(workItemId);
        assertThat(result.actionCode()).isEqualTo(actionCode);
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
