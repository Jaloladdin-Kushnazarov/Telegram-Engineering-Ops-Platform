package com.engops.platform.intake.command;

import com.engops.platform.admin.TenantOnboardingCommand;
import com.engops.platform.admin.TenantOnboardingResult;
import com.engops.platform.admin.TenantOnboardingService;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.telegram.TelegramBotCommand;
import com.engops.platform.telegram.TelegramBotCommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase 201 — {@code /onboard} bot command.
 *
 * <p>Operator Telegram orqali yangi tenant onboarding'ni boshlay oladi.
 * Argumentlar quoted-string parser orqali parsing qilinadi
 * ({@link TokenizedBotCommandArguments}), so'ng Phase 199
 * {@link TenantOnboardingService#onboard(TenantOnboardingCommand)} chaqiriladi.
 * Authorization service-layer tomonida (TENANT_ONBOARD permission), audit
 * Phase 199 + Phase 200 mavjud emitter'lari orqali.</p>
 *
 * <p><strong>Syntax:</strong>
 * {@code /onboard <slug> "<tenant_name>" <admin_telegram_user_id> "<admin_display_name>" <tpl1> [tpl2 ...]}
 * </p>
 *
 * <p><strong>Out of scope (Phase 201):</strong> --tz flag, --username flag,
 * interactive multi-step wizard, registry-driven /help. Faqat bitta one-shot
 * positional command.</p>
 */
@Component
public class OnboardCommand implements TelegramBotCommand {

    private static final Logger log = LoggerFactory.getLogger(OnboardCommand.class);

    static final String REPLY_USAGE =
            "❌ Foydalanish: /onboard <slug> \"<tenant_name>\" <admin_telegram_user_id> "
                    + "\"<admin_display_name>\" <template_code_1> [<template_code_2> ...]";

    static final String REPLY_ACCESS_DENIED = "❌ Sizda TENANT_ONBOARD ruxsati yo'q.";
    static final String REPLY_UNEXPECTED = "❌ Onboarding bajarilmadi (kutilmagan xatolik).";

    /**
     * BusinessRuleException errorCode → Uzbek user-friendly message template.
     * Templates'da {@code {slug}} va {@code {templateCode}} placeholder'lari
     * ishlatilgan joylar uchun maxsus formatlash dispatch metodida hal qilinadi.
     */
    private static final Map<String, String> ERROR_MESSAGES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("SLUG_TAKEN", "Bu slug allaqachon band: '{slug}'");
        m.put("UNKNOWN_WORKFLOW_TEMPLATE", "Noma'lum workflow shabloni: '{templateCode}'");
        m.put("DUPLICATE_WORKFLOW_NAME", "Bir xil workflow nomi takrorlangan");
        m.put("INVALID_SLUG", "Slug noto'g'ri (kichik harf/raqam/-, 3..50 belgi): '{slug}'");
        m.put("INVALID_TENANT_NAME", "Tenant nomi noto'g'ri yoki juda uzun");
        m.put("INVALID_DISPLAY_NAME", "Admin display name noto'g'ri yoki juda uzun");
        m.put("INVALID_TELEGRAM_USER_ID", "Telegram user id musbat raqam bo'lishi shart");
        m.put("NO_TEMPLATES_REQUESTED", "Kamida 1 ta workflow shabloni ko'rsating");
        m.put("TOO_MANY_TEMPLATES", "10 tadan ko'p shablon yuborilgan");
        m.put("ADMIN_ROLE_NOT_FOUND", "ADMIN role tizimda topilmadi");
        ERROR_MESSAGES = Map.copyOf(m);
    }

    private final TenantOnboardingService tenantOnboardingService;

    public OnboardCommand(TenantOnboardingService tenantOnboardingService) {
        this.tenantOnboardingService = tenantOnboardingService;
    }

    @Override
    public String commandName() {
        return "/onboard";
    }

    @Override
    public String execute(TelegramBotCommandContext context) {
        // 1. Quoted-string tokenization. Phase 200 dispatcher'ning oddiy
        //    whitespace-split argumentlari yetarli emas — multi-word qiymatlar
        //    (tenant name, display name) shu yerda hal qilinadi.
        List<String> tokens;
        try {
            tokens = TokenizedBotCommandArguments.parse(context.rawText());
        } catch (IllegalArgumentException ex) {
            return "❌ Argumentlarda xatolik: " + ex.getMessage();
        }

        // 2. Minimum 5 positional argument (slug, name, telegramId, displayName, ≥1 template).
        if (tokens.size() < 5) {
            return REPLY_USAGE;
        }

        String slug = tokens.get(0);
        String tenantName = tokens.get(1);
        String telegramIdRaw = tokens.get(2);
        String displayName = tokens.get(3);
        List<String> templateCodes = List.copyOf(tokens.subList(4, tokens.size()));

        // 3. Telegram user id parse — to'g'ridan-to'g'ri Long. Service yana
        //    musbatlikni tekshiradi, lekin shu yerda non-numeric bilan
        //    foydalanuvchiga aniq xabar bermoq foydali.
        Long telegramUserId;
        try {
            telegramUserId = Long.parseLong(telegramIdRaw);
        } catch (NumberFormatException ex) {
            return "❌ Telegram user id raqam bo'lishi shart: '" + telegramIdRaw + "'";
        }
        if (telegramUserId <= 0L) {
            return "❌ " + ERROR_MESSAGES.get("INVALID_TELEGRAM_USER_ID");
        }

        // 4. Build command. Timezone va username Phase 201'da YO'Q.
        TenantOnboardingCommand command = new TenantOnboardingCommand(
                tenantName, slug, null, telegramUserId, displayName, null,
                templateCodes, context.actorAppUserId());

        // 5. Service call — exception'larni user-friendly reply'ga konvert qilamiz.
        try {
            TenantOnboardingResult result = tenantOnboardingService.onboard(command);
            return successReply(result);
        } catch (AccessDeniedException ex) {
            return REPLY_ACCESS_DENIED;
        } catch (BusinessRuleException ex) {
            return "❌ " + formatBusinessRuleMessage(ex, slug, templateCodes);
        } catch (RuntimeException ex) {
            log.warn("OnboardCommand unexpected failure: actorAppUserId={} exceptionType={}",
                    context.actorAppUserId(), ex.getClass().getSimpleName());
            return REPLY_UNEXPECTED;
        }
    }

    private String successReply(TenantOnboardingResult result) {
        String codes = result.workflowDefinitions().stream()
                .map(TenantOnboardingResult.WorkflowDefinitionSummary::templateCode)
                .collect(Collectors.joining(", "));
        return "✅ Tenant yaratildi:\n"
                + "Slug: " + result.tenantSlug() + "\n"
                + "Tenant ID: " + result.tenantId() + "\n"
                + "Admin user ID: " + result.adminAppUserId() + "\n"
                + "Workflows: " + result.workflowDefinitions().size() + " ta (" + codes + ")";
    }

    /**
     * BusinessRuleException'ni Uzbek user-friendly matnga aylantiradi.
     * Bilingan errorCode'lar uchun shablon ishlatamiz; noma'lum errorCode
     * uchun fallback. Slug va template kodlari placeholder'lari faqat shu
     * yerda almashtiriladi — rawText echo'lanmaydi.
     */
    private String formatBusinessRuleMessage(BusinessRuleException ex, String slug,
                                              List<String> templateCodes) {
        String template = ERROR_MESSAGES.get(ex.getErrorCode());
        if (template == null) {
            return "Onboarding xatolik: " + ex.getErrorCode();
        }
        String message = template.replace("{slug}", slug);
        if (message.contains("{templateCode}")) {
            // UNKNOWN_WORKFLOW_TEMPLATE: service exception message ichida
            // qaysi template noma'lum ekanligi mavjud, lekin biz uni
            // foydalanuvchining yuborgan ro'yxatidan birinchisini ko'rsatish
            // bilan kifoyalanamiz (defensive — exception message echo'lanmaydi).
            String firstTemplate = templateCodes.isEmpty() ? "?" : templateCodes.get(0);
            // Try to identify the bad template by checking against ERROR_MESSAGES
            // semantics: service throws on first miss; without parsing the
            // exception message we approximate by reporting the joined list.
            message = message.replace("{templateCode}",
                    templateCodes.size() == 1 ? firstTemplate : String.join(", ", templateCodes));
        }
        return message;
    }
}
