package com.engops.platform.intake;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.telegram.TelegramBotCommand;
import com.engops.platform.telegram.TelegramBotCommandContext;
import com.engops.platform.telegram.TelegramBotCommandRegistry;
import com.engops.platform.telegram.TelegramMessageRequest;
import com.engops.platform.telegram.TelegramOutboundGateway;
import com.engops.platform.telegram.TelegramUpdateRequest;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Phase 200 — Telegram inbound text message'lar uchun bot command dispatcher.
 *
 * <p><strong>Workflow:</strong></p>
 * <ol>
 *   <li>Webhook controller "/" bilan boshlanadigan text message'ni
 *       {@link #handle(TelegramUpdateRequest)}'ga delegate qiladi.</li>
 *   <li>Service buyruq nomini va argumentlarni parse qiladi.</li>
 *   <li>Noma'lum buyruq — polite "Noma'lum buyruq" reply yuboriladi,
 *       audit YOZILMAYDI (noise robustness).</li>
 *   <li>Telegram user → AppUser resolve. Mavjud emas → polite
 *       "ro'yxatdan o'tmagan" reply + bounded warn log (audit YO'Q,
 *       chunki entity_id majburiy non-null).</li>
 *   <li>Aktiv membership topilmasa — graceful reply + NOT_REGISTERED audit
 *       (entity_id=appUser.id).</li>
 *   <li>Birinchi ACTIVE membership tenantId'sini olib context quramiz
 *       (D13 known limitation).</li>
 *   <li>Buyruqni bajarib reply'ni Telegram'ga yuboramiz, audit yozamiz.</li>
 * </ol>
 *
 * <p><strong>Card dispatch pipeline'idan ataylab alohida:</strong> Phase 200
 * bot reply'lari {@code TelegramOutboundGateway.sendBotReply(chatId, text)}
 * orqali fresh sendMessage qiladi. Workflow card refresh / edit-first
 * (Phase 179) yo'lidan o'tmaydi. Audit row Telegram delivery attempt
 * jadvalida saqlanmaydi — bu conversational reply, kard projektsiyasi emas.</p>
 *
 * <p><strong>Audit invariant:</strong> payload faqat buyruq nomi va
 * tenantId UUID'sini o'z ichiga oladi. {@code arguments} va {@code rawText}
 * AUDIT'GA HECH QACHON kirmaydi — user-kiritgan matn PII bo'lishi mumkin.</p>
 */
@Service
public class TelegramBotCommandService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotCommandService.class);

    static final String ACTION_SOURCE = "TELEGRAM_COMMAND";
    static final String AUDIT_EVENT_EXECUTED = "TELEGRAM_BOT_COMMAND_EXECUTED";
    static final String AUDIT_EVENT_NOT_REGISTERED = "TELEGRAM_BOT_COMMAND_NOT_REGISTERED";

    static final String REPLY_UNKNOWN_COMMAND =
            "Noma'lum buyruq. Mavjud buyruqlar uchun /help yozing.";
    static final String REPLY_NOT_REGISTERED =
            "Siz hali ro'yxatdan o'tmagansiz. Iltimos administrator bilan bog'laning.";

    private final TelegramBotCommandRegistry registry;
    private final IdentityQueryService identityQueryService;
    private final MembershipRepository membershipRepository;
    private final TenantConfigQueryService tenantConfigQueryService;
    private final TelegramOutboundGateway outboundGateway;
    private final AuditService auditService;

    public TelegramBotCommandService(TelegramBotCommandRegistry registry,
                                      IdentityQueryService identityQueryService,
                                      MembershipRepository membershipRepository,
                                      TenantConfigQueryService tenantConfigQueryService,
                                      TelegramOutboundGateway outboundGateway,
                                      AuditService auditService) {
        this.registry = registry;
        this.identityQueryService = identityQueryService;
        this.membershipRepository = membershipRepository;
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.outboundGateway = outboundGateway;
        this.auditService = auditService;
    }

    /**
     * Bot command webhook update'ini handle qiladi. Webhook fail-soft
     * boundary'sida chaqiriladi — exception tashlasa, controller uni
     * o'rab 200 OK qaytaradi (existing Phase 171 invariant).
     */
    public void handle(TelegramUpdateRequest update) {
        if (update == null || update.message() == null) {
            return; // defensive — webhook allaqachon filtrlagan bo'lishi kerak
        }
        TelegramMessageRequest message = update.message();
        String text = message.text();
        if (text == null || text.isBlank() || !text.startsWith("/")) {
            return;
        }
        if (message.chat() == null || message.chat().id() == null
                || message.from() == null || message.from().id() == null) {
            log.warn("Bot command webhook ignored: missing chat or from in message");
            return;
        }

        long chatId = message.chat().id();
        long telegramUserId = message.from().id();

        String[] tokens = text.strip().split("\\s+");
        String commandName = tokens[0];
        List<String> arguments = new ArrayList<>();
        for (int i = 1; i < tokens.length; i++) {
            arguments.add(tokens[i]);
        }

        // 1. Registry lookup — noma'lum buyruq → polite reply + NO audit.
        Optional<TelegramBotCommand> commandOpt = registry.findByName(commandName);
        if (commandOpt.isEmpty()) {
            outboundGateway.sendBotReply(chatId, REPLY_UNKNOWN_COMMAND);
            log.info("Bot command ignored (unknown): telegramUserId={} commandName={}",
                    telegramUserId, commandName);
            return;
        }
        TelegramBotCommand command = commandOpt.get();

        // 2. AppUser resolve.
        Optional<AppUser> userOpt = identityQueryService.findUserByTelegramUserId(telegramUserId);
        if (userOpt.isEmpty()) {
            outboundGateway.sendBotReply(chatId, REPLY_NOT_REGISTERED);
            log.warn("Bot command rejected (NOT_REGISTERED: no AppUser): "
                    + "telegramUserId={} commandName={}", telegramUserId, commandName);
            return;
        }
        AppUser appUser = userOpt.get();

        // 3. Birinchi ACTIVE membership orqali tenant scope (D13).
        Optional<Membership> activeMembership = firstActiveMembership(appUser.getId());
        if (activeMembership.isEmpty()) {
            outboundGateway.sendBotReply(chatId, REPLY_NOT_REGISTERED);
            auditService.recordEventInNewTransaction(null, "APP_USER", appUser.getId(),
                    AUDIT_EVENT_NOT_REGISTERED, appUser.getId(), ACTION_SOURCE, null,
                    "{\"command\":\"" + commandName.toLowerCase(java.util.Locale.ROOT) + "\"}");
            log.warn("Bot command rejected (NOT_REGISTERED: no ACTIVE membership): "
                    + "appUserId={} commandName={}", appUser.getId(), commandName);
            return;
        }
        Membership membership = activeMembership.get();

        // 4. Tenant lookup — slug resolve. Tenant topilmasa (yo'q bo'lmasligi
        //    kerak agar membership ACTIVE bo'lsa, lekin defensive), command'ga
        //    null slug uzatamiz.
        Optional<Tenant> tenantOpt = tenantConfigQueryService.findTenantById(membership.getTenantId());
        String tenantSlug = tenantOpt.map(Tenant::getSlug).orElse(null);

        // 5. Context yig'ish va buyruq bajarish.
        TelegramBotCommandContext context = new TelegramBotCommandContext(
                appUser.getId(),
                appUser.getDisplayName(),
                membership.getTenantId(),
                tenantSlug,
                telegramUserId,
                chatId,
                command.commandName(),
                arguments,
                text);

        String reply = command.execute(context);
        if (reply == null || reply.isBlank()) {
            reply = "(bot command bo'sh javob qaytardi)";
            log.warn("Bot command returned empty reply: commandName={}", command.commandName());
        }

        outboundGateway.sendBotReply(chatId, reply);

        // 6. Audit — payload faqat command + tenantId (arguments / rawText YO'Q).
        //    REQUIRES_NEW: webhook fail-soft konteksti — bot command service
        //    @Transactional emas, audit yozuvi mustaqil transactionda commit.
        auditService.recordEventInNewTransaction(
                membership.getTenantId(), "APP_USER", appUser.getId(),
                AUDIT_EVENT_EXECUTED, appUser.getId(), ACTION_SOURCE, null,
                "{\"command\":\"" + command.commandName().toLowerCase(java.util.Locale.ROOT)
                        + "\",\"tenantId\":\"" + membership.getTenantId() + "\"}");
    }

    private Optional<Membership> firstActiveMembership(java.util.UUID userId) {
        List<Membership> memberships = membershipRepository.findByUserId(userId);
        for (Membership m : memberships) {
            if (m.getStatus() == MembershipStatus.ACTIVE) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }
}
