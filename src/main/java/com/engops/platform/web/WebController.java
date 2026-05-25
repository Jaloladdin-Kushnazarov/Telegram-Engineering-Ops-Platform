package com.engops.platform.web;

import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 207–212 — Web UI controller for the {@code /web/**} surface.
 *
 * <p>Server-side HTML rendering uchun {@code @Controller} (NOT
 * {@code @RestController}). Phase 208'dan boshlab har bir endpoint
 * <strong>base layout</strong> pattern'ini ishlatadi:</p>
 * <pre>
 * model.addAttribute("pageTitle", "&lt;short title&gt;");
 * model.addAttribute("contentFragment", "web/&lt;page&gt; :: content");
 * return "web/layout/base";
 * </pre>
 *
 * <p>Auth approach (Phase 208 D1): browser-side localStorage JWT. Hech
 * qanday Spring Security form login yo'q; {@code /web/**} hali ham
 * SecurityConfig'da {@code permitAll} (Phase 207 surface, byte-frozen).
 * Browser-side {@code auth.js} JWT'ni localStorage'da saqlaydi va HTMX
 * so'rovlariga {@code Authorization: Bearer ...} header'ini avtomatik
 * ulaydi.</p>
 *
 * <p><strong>Phase 212 polish:</strong></p>
 * <ul>
 *   <li>D1 — tenant subtitle UUID o'rniga {@code displayName} +
 *       small UUID pill ko'rsatadi (TenantConfigQueryService orqali lookup).</li>
 *   <li>D2 — base.html nav link'lari {@code tenantId} URL parametrini
 *       saqlaydi (Thymeleaf {@code @{(name=value)}} syntax).</li>
 *   <li>D3 — Health sahifasi modernized: Status / Version / Profile /
 *       JWT decoder grid. Phase 208 marker olib tashlandi.</li>
 *   <li>D6 — {@link #addTenantContext(Model, UUID)} helper duplikatsiya
 *       oldini olish uchun.</li>
 *   <li>D12 — {@code PHASE} constant olib tashlandi (health.html endi
 *       buni interpolatsiya qilmaydi).</li>
 * </ul>
 */
@Controller
@RequestMapping("/web")
public class WebController {

    static final String APP_VERSION = "v0.1";

    private final TenantConfigQueryService tenantConfigQueryService;
    private final Environment environment;
    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;

    public WebController(TenantConfigQueryService tenantConfigQueryService,
                         Environment environment,
                         ObjectProvider<JwtDecoder> jwtDecoderProvider) {
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.environment = environment;
        this.jwtDecoderProvider = jwtDecoderProvider;
    }

    @GetMapping("/health")
    public String health(@RequestParam(required = false) UUID tenantId,
                          Model model) {
        model.addAttribute("status", "OK");
        model.addAttribute("appVersion", APP_VERSION);
        model.addAttribute("activeProfile", resolveActiveProfile());
        model.addAttribute("jwtDecoderEnabled",
                jwtDecoderProvider.getIfAvailable() != null);
        model.addAttribute("pageTitle", "System status");
        model.addAttribute("contentFragment", "web/health :: content");
        model.addAttribute("activeNav", "health");
        addTenantContext(model, tenantId);
        return "web/layout/base";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("pageTitle", "Login");
        model.addAttribute("contentFragment", "web/login :: content");
        // Phase 218b — Telegram Login Widget bot username (dev mode'da
        // application-dev.properties orqali sozlanadi). Bo'sh string
        // bo'lsa, Thymeleaf th:if = false → widget render qilinmaydi va
        // Phase 211 dev token tugmasi fallback sifatida ko'rinadi.
        model.addAttribute("telegramBotUsername",
                environment.getProperty("app.security.telegram.bot-username", ""));
        addTenantContext(model, null);
        return "web/layout/base";
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) UUID tenantId,
                             Model model) {
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("contentFragment", "web/dashboard :: content");
        model.addAttribute("activeNav", "dashboard");
        addTenantContext(model, tenantId);
        return "web/layout/base";
    }

    @GetMapping("/work-items")
    public String workItems(@RequestParam(required = false) UUID tenantId,
                             Model model) {
        model.addAttribute("pageTitle", "Work items");
        model.addAttribute("contentFragment", "web/work-items :: content");
        model.addAttribute("activeNav", "work-items");
        // Phase 220b — create modal dropdown qiymatlari. Statik bounded
        // ro'yxatlar (WorkItemType enum + WorkItemCommandService
        // ALLOWED_SEVERITY_CODES bilan mos). Aktyor talab qilinmaydi —
        // /web/** permitAll bo'lib qoladi (server-side authorization yo'q;
        // create POST /web/api/** ostida JWT bilan himoyalangan).
        model.addAttribute("workItemTypes", List.of("BUG", "INCIDENT", "TASK"));
        model.addAttribute("severities", List.of("CRITICAL", "HIGH", "MEDIUM", "LOW"));
        addTenantContext(model, tenantId);
        return "web/layout/base";
    }

    // ========== Helpers ==========

    /**
     * Phase 212 D6 — tenant context'ni Model'ga qo'shish. {@code tenantId}
     * va {@code tenantName} attributelarini o'rnatadi. {@code tenantId == null}
     * holatida {@code tenantName} attribute o'rnatilmaydi (template
     * {@code th:if} bilan filter qiladi). Noma'lum UUID ({@code Optional.empty()})
     * holatida {@code tenantName} = {@code "Unknown tenant"}.
     */
    private void addTenantContext(Model model, UUID tenantId) {
        model.addAttribute("tenantId", tenantId);
        if (tenantId != null) {
            Optional<Tenant> tenant = tenantConfigQueryService.findTenantById(tenantId);
            model.addAttribute("tenantName",
                    tenant.map(Tenant::getName).orElse("Unknown tenant"));
        }
    }

    /**
     * Phase 212 D3 — birinchi aktiv profile'ni qaytaradi. Hech qanday profile
     * aktiv bo'lmasa "default" qaytaradi (Spring Boot'ning {@code default}
     * profile placeholderini ifodalaydi).
     */
    private String resolveActiveProfile() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles == null || profiles.length == 0) {
            return "default";
        }
        return profiles[0];
    }
}
