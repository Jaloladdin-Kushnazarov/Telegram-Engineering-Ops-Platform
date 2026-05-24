package com.engops.platform.web;

import com.engops.platform.admin.TenantOnboardingCommand;
import com.engops.platform.admin.TenantOnboardingResult;
import com.engops.platform.admin.TenantOnboardingService;
import com.engops.platform.infrastructure.security.CurrentActor;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Phase 213 — tenant onboarding HTML form shim.
 *
 * <p>THIN SHIM controller. Hech qanday biznes logikasi yo'q — faqat:</p>
 * <ol>
 *   <li>HTML form'ni render qilish (GET).</li>
 *   <li>Form fields'larni server-side shape validation (D3).</li>
 *   <li>{@link TenantOnboardingForm} → {@link TenantOnboardingCommand}
 *       adapter.</li>
 *   <li>{@link TenantOnboardingService#onboard(TenantOnboardingCommand)}
 *       chaqirish (Phase 195+ byte-frozen).</li>
 *   <li>Success: POST/Redirect/GET — {@code /web/dashboard?tenantId=&lt;new&gt;}.</li>
 *   <li>{@link BusinessRuleException} / {@link AccessDeniedException}'ni form
 *       error'lariga aylantirish va form'ni qayta render qilish.</li>
 * </ol>
 *
 * <p><strong>Authorization:</strong> {@code @CurrentActor UUID actorUserId}
 * Spring SecurityContext'dan resolve qilinadi (Phase 125). Anonymous
 * so'rovlar {@code JsonAuthenticationEntryPoint} (Phase 148) orqali 401
 * qaytaradi. Service ichida {@code OperationalAuthorizationService.authorizeGlobal(TENANT_ONBOARD)}
 * permission tekshiruvi bor (Phase 199 V8).</p>
 *
 * <p><strong>Form defaults (Phase 213 D2 vs Phase 199 Command shape):</strong></p>
 * <ul>
 *   <li>{@code workflowTemplateCodes} form'da yo'q → default
 *       {@code ["BUG_MINIMAL"]} (V7 katalogdan, MVP).</li>
 *   <li>{@code adminUsername} form'da yo'q → {@code null} (service ichida
 *       null-tolerant).</li>
 *   <li>{@code adminDisplayName} bo'sh bo'lsa → {@code "Tenant Admin"}
 *       fallback (service'ning {@code INVALID_DISPLAY_NAME} validation'ini
 *       chetlab o'tish uchun emas — operator tajribasini polish qilish uchun).</li>
 *   <li>{@code timezone} bo'sh bo'lsa → {@code "UTC"}.</li>
 * </ul>
 */
@Controller
@RequestMapping("/web/onboarding")
public class TenantOnboardingWebController {

    static final String DEFAULT_TIMEZONE = "UTC";
    static final String DEFAULT_ADMIN_DISPLAY_NAME = "Tenant Admin";
    static final String DEFAULT_WORKFLOW_TEMPLATE_CODE = "BUG_MINIMAL";

    static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final TenantOnboardingService tenantOnboardingService;

    public TenantOnboardingWebController(TenantOnboardingService tenantOnboardingService) {
        this.tenantOnboardingService = tenantOnboardingService;
    }

    @GetMapping
    public String getForm(Model model) {
        prepareLayout(model);
        return "web/layout/base";
    }

    @PostMapping
    public Object submitForm(@ModelAttribute TenantOnboardingForm form,
                              @CurrentActor UUID actorUserId,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                              Model model) {
        Map<String, String> errors = validateForm(form);
        if (!errors.isEmpty()) {
            // Error path: HTMX hx-select="form" form'ni response'dan ajratib oladi,
            // shu sababli HTMX/non-HTMX uchun bir xil view qaytarish yetarli.
            return renderWithErrors(model, form, errors);
        }

        try {
            TenantOnboardingCommand command = adapt(form, actorUserId);
            TenantOnboardingResult result = tenantOnboardingService.onboard(command);
            String redirectUrl = "/web/dashboard?tenantId=" + result.tenantId();
            if (hxRequest != null) {
                // Phase 213a — HTMX-aware success: native HTML form Authorization
                // header'ini yubormaydi, shuning uchun form HTMX submission'ga
                // o'tkazildi (auth.js htmx:configRequest JWT'ni avtomatik
                // qo'shadi). HX-Redirect header HTMX'ga client-side full-page
                // navigatsiyani buyuradi.
                return ResponseEntity.ok()
                        .header("HX-Redirect", redirectUrl)
                        .build();
            }
            // Non-HTMX (curl, legacy fallback): standart 302 redirect saqlanadi.
            return "redirect:" + redirectUrl;
        } catch (BusinessRuleException ex) {
            return renderWithErrors(model, form, mapBusinessRuleException(ex));
        } catch (AccessDeniedException ex) {
            Map<String, String> errors2 = new LinkedHashMap<>();
            errors2.put("_global",
                    "Sizda yangi tenant yaratish ruxsati (TENANT_ONBOARD) yo'q.");
            return renderWithErrors(model, form, errors2);
        }
    }

    // ========== Validation ==========

    /**
     * Server-side shape validation (D3). Field-level error'lar yig'iladi
     * — short-circuit yo'q, operator barchasini birdaniga ko'radi.
     */
    Map<String, String> validateForm(TenantOnboardingForm form) {
        Map<String, String> errors = new LinkedHashMap<>();

        String name = trim(form.name());
        if (name.isEmpty()) {
            errors.put("name", "Tenant nomi majburiy.");
        } else if (name.length() < 2 || name.length() > 80) {
            errors.put("name", "Tenant nomi 2..80 belgi oralig'ida bo'lishi shart.");
        }

        String slug = trim(form.slug());
        if (slug.isEmpty()) {
            errors.put("slug", "Slug majburiy.");
        } else if (slug.length() < 2 || slug.length() > 40) {
            errors.put("slug", "Slug 2..40 belgi oralig'ida bo'lishi shart.");
        } else if (!SLUG_PATTERN.matcher(slug).matches()) {
            errors.put("slug",
                    "Slug faqat kichik harf, raqam va tire (-) bo'lishi mumkin.");
        }

        if (form.adminTelegramUserId() == null) {
            errors.put("adminTelegramUserId", "Telegram user ID majburiy.");
        } else if (form.adminTelegramUserId() <= 0L) {
            errors.put("adminTelegramUserId",
                    "Telegram user ID musbat raqam bo'lishi shart.");
        }

        String adminDisplayName = trim(form.adminDisplayName());
        if (!adminDisplayName.isEmpty() && adminDisplayName.length() > 80) {
            errors.put("adminDisplayName",
                    "Admin nomi 80 belgidan oshmasligi shart.");
        }

        String timezone = trim(form.timezone());
        if (!timezone.isEmpty()) {
            try {
                ZoneId.of(timezone);
            } catch (DateTimeException ex) {
                errors.put("timezone",
                        "Noma'lum IANA timezone identifikatori: '" + timezone + "'.");
            }
        }

        return errors;
    }

    // ========== Adapter ==========

    TenantOnboardingCommand adapt(TenantOnboardingForm form, UUID actorUserId) {
        String adminDisplayName = trim(form.adminDisplayName());
        if (adminDisplayName.isEmpty()) {
            adminDisplayName = DEFAULT_ADMIN_DISPLAY_NAME;
        }
        String timezone = trim(form.timezone());
        if (timezone.isEmpty()) {
            timezone = DEFAULT_TIMEZONE;
        }
        return new TenantOnboardingCommand(
                trim(form.name()),
                trim(form.slug()),
                timezone,
                form.adminTelegramUserId(),
                adminDisplayName,
                null, // adminUsername — form'da yo'q
                List.of(DEFAULT_WORKFLOW_TEMPLATE_CODE),
                actorUserId);
    }

    // ========== Exception mapping (D5) ==========

    /**
     * {@link BusinessRuleException}'ni form field error'lariga aylantiradi.
     * Service errorCode'lari Phase 199 dan kelib chiqadi (audit log via
     * grep, lekin shim hard-code emas; noma'lum kod {@code _global}'ga
     * tushadi).
     */
    Map<String, String> mapBusinessRuleException(BusinessRuleException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        String code = ex.getErrorCode();
        String message = ex.getMessage();
        switch (code) {
            case "SLUG_TAKEN", "INVALID_SLUG" ->
                    errors.put("slug", code.equals("SLUG_TAKEN")
                            ? "Bu slug allaqachon mavjud. Boshqa slug tanlang."
                            : message);
            case "INVALID_TENANT_NAME" ->
                    errors.put("name", message);
            case "INVALID_TIMEZONE" ->
                    errors.put("timezone", message);
            case "INVALID_TELEGRAM_USER_ID" ->
                    errors.put("adminTelegramUserId", message);
            case "INVALID_DISPLAY_NAME" ->
                    errors.put("adminDisplayName", message);
            default ->
                // NO_TEMPLATES_REQUESTED, TOO_MANY_TEMPLATES, UNKNOWN_WORKFLOW_TEMPLATE,
                // INVALID_TEMPLATE_CODE, INVALID_TEMPLATE, DUPLICATE_WORKFLOW_NAME,
                // ADMIN_ROLE_NOT_FOUND, INVALID_USERNAME — barchasi global banner'da.
                errors.put("_global",
                        "Form ma'lumotlari qabul qilinmadi: " + message);
        }
        return errors;
    }

    // ========== Layout + helpers ==========

    private String renderWithErrors(Model model,
                                     TenantOnboardingForm form,
                                     Map<String, String> errors) {
        prepareLayout(model);
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        return "web/layout/base";
    }

    private void prepareLayout(Model model) {
        model.addAttribute("pageTitle", "Create tenant");
        model.addAttribute("contentFragment", "web/onboarding :: content");
        model.addAttribute("activeNav", "onboarding");
        model.addAttribute("tenantId", null);
    }

    private static String trim(String s) {
        return s == null ? "" : s.strip();
    }
}
