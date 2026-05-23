package com.engops.platform.telegram;

import java.util.List;
import java.util.UUID;

/**
 * Phase 200 — bot command kontekst yozuvi.
 *
 * <p>Resolved actor + tenant + parsed text bo'laklarini bitta immutable
 * shaklda buyruq implementatsiyalariga uzatadi. Bu yozuvga reach qilish
 * uchun command'lar dispatcher tomonida hal qilingan bir nechta
 * lookup'larning natijasi:</p>
 * <ul>
 *   <li>{@code actorAppUserId}, {@code actorDisplayName} —
 *       {@code IdentityQueryService.findUserByTelegramUserId} natijasi.</li>
 *   <li>{@code tenantId}, {@code tenantSlug} — actor'ning birinchi ACTIVE
 *       membership'i orqali topilgan tenant (Phase 200 single-tenant
 *       routing soddalashtirishi).</li>
 *   <li>{@code arguments} — buyruq nomidan keyin bo'sh joy bilan ajratilgan
 *       tokenlar; bo'sh ro'yxat ham mumkin.</li>
 *   <li>{@code rawText} — original message text (debugging / future
 *       commands; bot dispatcher AUDIT payload'iga rawText'ni QO'SHMAYDI,
 *       chunki user-kiritgan PII bo'lishi mumkin).</li>
 * </ul>
 */
public record TelegramBotCommandContext(
        UUID actorAppUserId,
        String actorDisplayName,
        UUID tenantId,
        String tenantSlug,
        Long telegramUserId,
        Long telegramChatId,
        String commandName,
        List<String> arguments,
        String rawText) {

    public TelegramBotCommandContext {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}
