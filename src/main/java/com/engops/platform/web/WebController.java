package com.engops.platform.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Phase 207 — Web UI scaffolding (Track 5 minimal slice).
 *
 * <p>Server-side HTML rendering uchun {@code @Controller} (NOT
 * {@code @RestController}) — Thymeleaf orqali
 * {@code src/main/resources/templates/web/} dan template'lar resolve
 * qilinadi.</p>
 *
 * <p>Phase 207 da YAGONA endpoint: {@code GET /web/health}. Maqsad —
 * Thymeleaf stack ishlayotganini va birinchi {@code /web/*} surface
 * ochilganini tasdiqlash. HTMX, auth, session, dashboard, charts —
 * kelajakdagi phase'larga reja qilingan:</p>
 * <ul>
 *   <li>Phase 208: session auth + login page + HTMX + base layout.</li>
 *   <li>Phase 209: dashboard with Phase 205 analytics charts.</li>
 *   <li>Phase 210: WorkItem list / detail UI.</li>
 *   <li>Phase 211: Tenant onboarding UI form.</li>
 * </ul>
 *
 * <p>Phase 207 da {@code /web/**} SecurityConfig'da {@code permitAll}
 * — anonymous reachable. Auth Phase 208'da qo'shiladi.</p>
 */
@Controller
@RequestMapping("/web")
public class WebController {

    static final String PHASE = "207";

    @GetMapping("/health")
    public String health(Model model) {
        model.addAttribute("status", "OK");
        model.addAttribute("phase", PHASE);
        return "web/health";
    }
}
