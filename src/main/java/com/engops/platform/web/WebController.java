package com.engops.platform.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Phase 207 + Phase 208 — Web UI controller for the {@code /web/**} surface.
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
 * <p>Track 5 layered roadmap:</p>
 * <ul>
 *   <li>Phase 207: minimal {@code /web/health} smoke (DONE).</li>
 *   <li>Phase 208: base layout + HTMX + login + dashboard placeholder (this phase).</li>
 *   <li>Phase 209: dashboard wired to Phase 205 analytics charts.</li>
 *   <li>Phase 210: WorkItem list / detail UI.</li>
 *   <li>Phase 211: Tenant onboarding UI form.</li>
 * </ul>
 */
@Controller
@RequestMapping("/web")
public class WebController {

    static final String PHASE = "208";

    @GetMapping("/health")
    public String health(Model model) {
        model.addAttribute("status", "OK");
        model.addAttribute("phase", PHASE);
        model.addAttribute("pageTitle", "Web Health");
        model.addAttribute("contentFragment", "web/health :: content");
        model.addAttribute("activeNav", "health");
        return "web/layout/base";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("pageTitle", "Login");
        model.addAttribute("contentFragment", "web/login :: content");
        // activeNav: null — login page does not highlight a nav tab.
        return "web/layout/base";
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) UUID tenantId,
                             Model model) {
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("contentFragment", "web/dashboard :: content");
        model.addAttribute("tenantId", tenantId);
        model.addAttribute("activeNav", "dashboard");
        return "web/layout/base";
    }
}
