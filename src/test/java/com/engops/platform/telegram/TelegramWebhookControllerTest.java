package com.engops.platform.telegram;

import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.intake.TelegramCallbackActionExecutionService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 171 / Phase 173 — {@link TelegramWebhookController} {@code @WebMvcTest} testlari.
 *
 * <p>Ikki ichki test class:</p>
 * <ul>
 *   <li>{@link WithConfiguredSecret} — secret token configured holatda
 *       (asosiy happy + sad path test'lar va Phase 173 orchestrator wiring).</li>
 *   <li>{@link WithBlankSecret} — secret token bo'sh holatda (fail-closed
 *       invariantı: hech qanday so'rov o'tmaydi).</li>
 * </ul>
 *
 * <p>Webhook secret token {@code @TestPropertySource} orqali pinned. Bu
 * real secret emas — test fixture.</p>
 */
class TelegramWebhookControllerTest {

    private static final String WEBHOOK_PATH = "/api/telegram/webhook";
    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";
    private static final String CONFIGURED_SECRET = "test-webhook-secret-12345";
    private static final UUID WORK_ITEM_ID = UUID.fromString("7c3b2a4d-1234-4abc-9def-0123456789ab");

    private static String callbackBody(String data) {
        return """
                {
                  "update_id": 100,
                  "callback_query": {
                    "id": "cb-id-1",
                    "from": {"id": 123456789},
                    "message": {
                      "message_id": 555,
                      "chat": {"id": -1001234567890}
                    },
                    "data": "%s"
                  }
                }
                """.formatted(data);
    }

    private static TelegramCallbackParseResult accepted(UUID workItemId, String actionCode) {
        return new TelegramCallbackParseResult(
                TelegramCallbackQueryService.CallbackOutcome.ACCEPTED, workItemId, actionCode);
    }

    private static TelegramCallbackParseResult ignored(
            TelegramCallbackQueryService.CallbackOutcome outcome) {
        return new TelegramCallbackParseResult(outcome, null, null);
    }

    // ============================================================
    // Configured-secret holati
    // ============================================================
    @Nested
    @WebMvcTest(TelegramWebhookController.class)
    @Import({SecurityConfig.class, SecurityWebMvcConfig.class})
    @TestPropertySource(properties = {
            "app.telegram.webhook.secret-token=" + CONFIGURED_SECRET
    })
    class WithConfiguredSecret {

        @Autowired private MockMvc mockMvc;
        @MockBean private TelegramCallbackQueryService callbackQueryService;
        @MockBean private TelegramCallbackActionExecutionService executionService;

        // --- Secret validation: missing/wrong ---

        @Test
        void missingSecretHeaderReturns401() throws Exception {
            mockMvc.perform(post(WEBHOOK_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"update_id\":1}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
            verifyNoInteractions(callbackQueryService);
            verifyNoInteractions(executionService);
        }

        @Test
        void wrongSecretHeaderReturns401() throws Exception {
            mockMvc.perform(post(WEBHOOK_PATH)
                            .header(SECRET_HEADER, "completely-different-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"update_id\":1}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
            verifyNoInteractions(callbackQueryService);
            verifyNoInteractions(executionService);
        }

        /**
         * Constant-time compare invariant: bir xil uzunlikdagi noto'g'ri
         * secret ham rad etiladi (timing oracle ham, length-based shortcut
         * ham ko'rinmaydi).
         */
        @Test
        void wrongSecretSameLengthReturns401() throws Exception {
            String wrongSecret = "X".repeat(CONFIGURED_SECRET.length());
            mockMvc.perform(post(WEBHOOK_PATH)
                            .header(SECRET_HEADER, wrongSecret)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"update_id\":1}"))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(callbackQueryService);
            verifyNoInteractions(executionService);
        }

        @Test
        void controllerNeverInvokesServiceOnAuthFailureRegardlessOfBody() throws Exception {
            // Hatto valid callback_query payload bo'lsa ham, secret noto'g'ri
            // bo'lsa parser ham, orchestrator ham umuman chaqirilmasligi shart.
            mockMvc.perform(post(WEBHOOK_PATH)
                            .header(SECRET_HEADER, "wrong")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackBody(WORK_ITEM_ID + ":START_PROCESSING")))
                    .andExpect(status().isUnauthorized());
            verify(callbackQueryService, never()).process(any());
            verifyNoInteractions(executionService);
        }

        // --- Valid secret routing ---

        @Test
        void validSecretNonCallbackUpdateReturns200AndIgnores() throws Exception {
            mockMvc.perform(post(WEBHOOK_PATH)
                            .header(SECRET_HEADER, CONFIGURED_SECRET)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"update_id\":42}"))
                    .andExpect(status().isOk());
            verifyNoInteractions(callbackQueryService);
            verifyNoInteractions(executionService);
        }

        @Test
        void validSecretEmptyBodyReturns200AndIgnores() throws Exception {
            mockMvc.perform(post(WEBHOOK_PATH)
                            .header(SECRET_HEADER, CONFIGURED_SECRET)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());
            verifyNoInteractions(callbackQueryService);
            verifyNoInteractions(executionService);
        }

        @Test
        void validSecretCallbackQueryReturns200AndDelegates() throws Exception {
            when(callbackQueryService.process(any(TelegramCallbackQueryRequest.class)))
                    .thenReturn(accepted(WORK_ITEM_ID, "START_PROCESSING"));
            when(executionService.execute(any(), any(), any()))
                    .thenReturn(TelegramCallbackActionExecutionService.ExecutionOutcome.EXECUTED);

            mockMvc.perform(post(WEBHOOK_PATH)
                            .header(SECRET_HEADER, CONFIGURED_SECRET)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackBody(WORK_ITEM_ID + ":START_PROCESSING")))
                    .andExpect(status().isOk());

            verify(callbackQueryService, times(1)).process(any(TelegramCallbackQueryRequest.class));
            verify(executionService, times(1))
                    .execute(any(TelegramCallbackQueryRequest.class),
                            eq(WORK_ITEM_ID), eq("START_PROCESSING"));
        }

        @Test
        void validSecretCallbackUnknownActionReturns200AndDoesNotInvokeExecutor() throws Exception {
            // Parser IGNORED_UNKNOWN_ACTION qaytarsa, controller 200 qaytaradi
            // va orchestrator UMUMAN chaqirilmaydi.
            when(callbackQueryService.process(any(TelegramCallbackQueryRequest.class)))
                    .thenReturn(ignored(
                            TelegramCallbackQueryService.CallbackOutcome.IGNORED_UNKNOWN_ACTION));

            mockMvc.perform(post(WEBHOOK_PATH)
                            .header(SECRET_HEADER, CONFIGURED_SECRET)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackBody(WORK_ITEM_ID + ":UNRECOGNIZED_ACTION")))
                    .andExpect(status().isOk());

            verify(callbackQueryService, times(1)).process(any(TelegramCallbackQueryRequest.class));
            verifyNoInteractions(executionService);
        }

        @Test
        void validSecretCallbackMalformedDataReturns200AndDoesNotInvokeExecutor() throws Exception {
            when(callbackQueryService.process(any(TelegramCallbackQueryRequest.class)))
                    .thenReturn(ignored(
                            TelegramCallbackQueryService.CallbackOutcome.IGNORED_MALFORMED));

            mockMvc.perform(post(WEBHOOK_PATH)
                            .header(SECRET_HEADER, CONFIGURED_SECRET)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackBody("totally-malformed")))
                    .andExpect(status().isOk());

            verify(callbackQueryService, times(1)).process(any(TelegramCallbackQueryRequest.class));
            verifyNoInteractions(executionService);
        }

        // --- Phase 173: executor business/auth outcomes still return 200 ---

        @Test
        void executorPermissionDeniedStillReturns200() throws Exception {
            when(callbackQueryService.process(any(TelegramCallbackQueryRequest.class)))
                    .thenReturn(accepted(WORK_ITEM_ID, "START_PROCESSING"));
            when(executionService.execute(any(), any(), any()))
                    .thenReturn(TelegramCallbackActionExecutionService.ExecutionOutcome.PERMISSION_DENIED);

            mockMvc.perform(post(WEBHOOK_PATH)
                            .header(SECRET_HEADER, CONFIGURED_SECRET)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackBody(WORK_ITEM_ID + ":START_PROCESSING")))
                    .andExpect(status().isOk());

            verify(executionService, times(1)).execute(any(), any(), any());
        }

        @Test
        void executorUnexpectedFailureStillReturns200() throws Exception {
            when(callbackQueryService.process(any(TelegramCallbackQueryRequest.class)))
                    .thenReturn(accepted(WORK_ITEM_ID, "MARK_FIXED"));
            when(executionService.execute(any(), any(), any()))
                    .thenReturn(TelegramCallbackActionExecutionService.ExecutionOutcome.UNEXPECTED_FAILURE);

            mockMvc.perform(post(WEBHOOK_PATH)
                            .header(SECRET_HEADER, CONFIGURED_SECRET)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackBody(WORK_ITEM_ID + ":MARK_FIXED")))
                    .andExpect(status().isOk());

            verify(executionService, times(1)).execute(any(), any(), any());
        }
    }

    // ============================================================
    // Blank-secret holati: fail-closed invariant
    // ============================================================
    @Nested
    @WebMvcTest(TelegramWebhookController.class)
    @Import({SecurityConfig.class, SecurityWebMvcConfig.class})
    @TestPropertySource(properties = {
            // Ataylab bo'sh — production'da konfiguratsiya yo'q sharoitini
            // simulyatsiya qiladi (env yo'q yoki secret manager qo'lda
            // tushirilmagan).
            "app.telegram.webhook.secret-token="
    })
    class WithBlankSecret {

        @Autowired private MockMvc mockMvc;
        @MockBean private TelegramCallbackQueryService callbackQueryService;
        @MockBean private TelegramCallbackActionExecutionService executionService;

        @Test
        void blankConfiguredSecretRejectsEverythingWith401() throws Exception {
            // Hatto "to'g'ri ko'ringan" header bilan ham — agar configured
            // secret bo'sh bo'lsa, hech kim o'tmaydi. Bu fail-closed posture
            // (Phase 158 token-activation pattern bilan bir xil).
            mockMvc.perform(post(WEBHOOK_PATH)
                            .header(SECRET_HEADER, "any-incoming-value")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackBody(WORK_ITEM_ID + ":START_PROCESSING")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
            verifyNoInteractions(callbackQueryService);
            verifyNoInteractions(executionService);
        }

        @Test
        void blankConfiguredSecretMissingHeaderAlsoReturns401() throws Exception {
            mockMvc.perform(post(WEBHOOK_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"update_id\":1}"))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(callbackQueryService);
            verifyNoInteractions(executionService);
        }
    }
}
