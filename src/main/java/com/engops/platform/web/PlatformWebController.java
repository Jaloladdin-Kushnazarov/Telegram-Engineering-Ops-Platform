package com.engops.platform.web;

import com.engops.platform.infrastructure.security.CurrentActor;
import com.engops.platform.platform.PlatformTenantQueryService;
import com.engops.platform.platform.PlatformTenantSummary;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

/**
 * Phase 217b — PLATFORM_OWNER UI shim (thin web adapter).
 *
 * <p>Ikkita endpoint:</p>
 * <ul>
 *   <li>{@code GET /web/platform/tenants} — bosh sahifa (HTMX placeholder
 *       tbody). Phase 207+ base layout pattern bilan.</li>
 *   <li>{@code GET /web/api/platform/tenants} — HTMX fragment shim:
 *       {@link PlatformTenantQueryService#listAllTenants} chaqiradi va
 *       jadval rows fragment'ini qaytaradi. 403 holatida denied fragment.</li>
 * </ul>
 *
 * <p><strong>Authorization model:</strong> SecurityConfig'da
 * {@code /web/api/**} JWT-protected (Phase 209B). Service layer
 * {@code authorizeGlobal(actor, PLATFORM_TENANT_LIST)}'ni birinchi qadamda
 * tekshiradi. {@link AccessDeniedException} → 200 OK + denied fragment
 * (HTMX swap maqsadli; xato envelope emas). 401 (JWT yo'q) Phase 148
 * JsonAuthenticationEntryPoint orqali standart envelope qaytaradi.</p>
 *
 * <p>Bu controller Phase 217a service'iga teginmaydi — pure delegation.
 * Phase 218'da write API (Suspend/Delete) qo'shilganda yangi POST
 * metodlar shu yerga qo'shiladi (xuddi shu pattern).</p>
 */
@Controller
@RequestMapping("/web")
public class PlatformWebController {

    private final PlatformTenantQueryService platformTenantQueryService;

    public PlatformWebController(PlatformTenantQueryService platformTenantQueryService) {
        this.platformTenantQueryService = platformTenantQueryService;
    }

    /**
     * Platform tenants bosh sahifasi. Sahifa initial holatda bo'sh —
     * HTMX <tbody>'ni {@code /web/api/platform/tenants}'dan hydrate qiladi.
     */
    @GetMapping("/platform/tenants")
    public String platformTenantsPage(Model model) {
        model.addAttribute("pageTitle", "Platform tenants");
        model.addAttribute("contentFragment", "web/platform-tenants :: content");
        model.addAttribute("activeNav", "platform");
        // tenantId saqlanadi nav link conditional uchun (Phase 212 D2).
        model.addAttribute("tenantId", null);
        return "web/layout/base";
    }

    /**
     * HTMX shim — Phase 217a service'idan tenantlar ro'yxatini olib jadval
     * rows fragmentini render qiladi.
     *
     * <p>403 (PLATFORM_TENANT_LIST yo'q) → 200 OK + denied fragment.
     * Bu HTMX swap uchun moslangan: client {@code <tbody>}'ga rows yoki
     * denial xabari yozadi. HTTP 403 envelope HTMX swap'ni bekor qilardi.</p>
     */
    @GetMapping("/api/platform/tenants")
    public String tenantsFragment(@CurrentActor UUID actorUserId, Model model) {
        try {
            List<PlatformTenantSummary> tenants =
                    platformTenantQueryService.listAllTenants(actorUserId);
            model.addAttribute("tenants", tenants);
            model.addAttribute("count", tenants.size());
            return "web/fragments/platform-tenant-rows :: rows";
        } catch (AccessDeniedException ex) {
            model.addAttribute("error",
                    "Bu sahifaga kirish uchun PLATFORM_OWNER ruxsati talab qilinadi.");
            return "web/fragments/platform-tenant-rows-denied :: denied";
        }
    }
}
