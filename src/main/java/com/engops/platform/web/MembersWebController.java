package com.engops.platform.web;

import com.engops.platform.identity.membership.ChangeRoleRequest;
import com.engops.platform.identity.membership.InviteMemberRequest;
import com.engops.platform.identity.membership.MemberSummary;
import com.engops.platform.identity.membership.MembershipCommandService;
import com.engops.platform.identity.membership.MembershipQueryService;
import com.engops.platform.infrastructure.security.CurrentActor;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Phase 219b — tenant member management UI shim (thin web adapter).
 *
 * <p>Phase 217b {@link PlatformWebController} pattern'iga amal qiladi:
 * {@code @Controller} HTML view nomlarini qaytaradi, Phase 219a
 * {@link MembershipCommandService}/{@link MembershipQueryService} public
 * API'sini delegate qiladi (servicelar o'zgartirilmaydi).</p>
 *
 * <p><strong>Endpoint'lar:</strong></p>
 * <ul>
 *   <li>{@code GET /web/tenants/{tenantId}/members} — bosh sahifa
 *       (base layout + HTMX placeholder tbody).</li>
 *   <li>{@code GET /web/api/tenants/{tenantId}/members} — HTMX rows
 *       fragment (initial load + invite/role/remove'dan keyin refresh).</li>
 *   <li>{@code POST /web/api/tenants/{tenantId}/members} — invite (HTMX form).</li>
 *   <li>{@code POST /web/api/tenants/{tenantId}/members/{memberUserId}/role} — role o'zgartirish.</li>
 *   <li>{@code DELETE /web/api/tenants/{tenantId}/members/{memberUserId}} — remove.</li>
 * </ul>
 *
 * <p><strong>Exception model (WEB layer):</strong> {@link AccessDeniedException}
 * va {@link BusinessRuleException} ikkalasi ham <strong>200 OK + inline
 * error fragment</strong> (HX-Retarget {@code #invite-error}) qaytaradi —
 * HTMX swap UX'ni saqlash uchun. HTTP 4xx HTMX swap'ni bekor qilardi.
 * Phase 219a REST controller ({@code /api/tenants/{id}/members}) esa
 * programmatik clientlar uchun 403/422 semantikasini saqlaydi — bu
 * <strong>parallel surface</strong>, almashtiruvchi emas.</p>
 *
 * <p>SecurityConfig'da {@code /web/api/**} JWT-protected (Phase 209B) —
 * JWT yo'q bo'lsa 401 envelope (Phase 148). Per-tenant {@code MEMBER_MANAGE}
 * ruxsati service layer'da tekshiriladi.</p>
 */
@Controller
@RequestMapping("/web")
public class MembersWebController {

    /** Phase 219a whitelist bilan mos: invite/role-change uchun tayinlanadigan rollar. */
    private static final List<String> ASSIGNABLE_ROLES =
            List.of("ADMIN", "ENGINEER", "TESTER", "VIEWER");

    private final MembershipCommandService commandService;
    private final MembershipQueryService queryService;

    public MembersWebController(MembershipCommandService commandService,
                                MembershipQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping("/tenants/{tenantId}/members")
    public String membersPage(@PathVariable UUID tenantId, Model model) {
        model.addAttribute("pageTitle", "Members");
        model.addAttribute("contentFragment", "web/members :: content");
        model.addAttribute("activeNav", "members");
        model.addAttribute("tenantId", tenantId);
        return "web/layout/base";
    }

    @GetMapping("/api/tenants/{tenantId}/members")
    public String membersFragment(@CurrentActor UUID actorUserId,
                                  @PathVariable UUID tenantId,
                                  Model model) {
        try {
            populateRows(model, actorUserId, tenantId);
            return "web/fragments/member-rows :: rows";
        } catch (AccessDeniedException ex) {
            model.addAttribute("error",
                    "Bu sahifaga kirish uchun MEMBER_MANAGE ruxsati talab qilinadi.");
            return "web/fragments/member-rows-denied :: denied";
        }
    }

    @PostMapping("/api/tenants/{tenantId}/members")
    public String inviteMember(@CurrentActor UUID actorUserId,
                               @PathVariable UUID tenantId,
                               @RequestParam Long telegramUserId,
                               @RequestParam String displayName,
                               @RequestParam(required = false) String username,
                               @RequestParam String roleCode,
                               Model model,
                               HttpServletResponse response) {
        try {
            commandService.inviteMember(actorUserId, tenantId,
                    new InviteMemberRequest(telegramUserId, displayName,
                            emptyToNull(username), roleCode));
            populateRows(model, actorUserId, tenantId);
            response.setHeader("HX-Trigger", "memberInvited");
            return "web/fragments/member-rows :: rows";
        } catch (AccessDeniedException | BusinessRuleException ex) {
            return inlineError(model, response, ex.getMessage());
        }
    }

    @PostMapping("/api/tenants/{tenantId}/members/{memberUserId}/role")
    public String changeRole(@CurrentActor UUID actorUserId,
                             @PathVariable UUID tenantId,
                             @PathVariable UUID memberUserId,
                             @RequestParam String newRoleCode,
                             Model model,
                             HttpServletResponse response) {
        try {
            commandService.changeRole(actorUserId, tenantId, memberUserId,
                    new ChangeRoleRequest(newRoleCode));
            populateRows(model, actorUserId, tenantId);
            return "web/fragments/member-rows :: rows";
        } catch (AccessDeniedException | BusinessRuleException ex) {
            return inlineError(model, response, ex.getMessage());
        }
    }

    @DeleteMapping("/api/tenants/{tenantId}/members/{memberUserId}")
    public String removeMember(@CurrentActor UUID actorUserId,
                               @PathVariable UUID tenantId,
                               @PathVariable UUID memberUserId,
                               Model model,
                               HttpServletResponse response) {
        try {
            commandService.removeMember(actorUserId, tenantId, memberUserId);
            populateRows(model, actorUserId, tenantId);
            return "web/fragments/member-rows :: rows";
        } catch (AccessDeniedException | BusinessRuleException ex) {
            return inlineError(model, response, ex.getMessage());
        }
    }

    // ========== Helpers ==========

    /** A'zolar ro'yxatini va dropdown rollarini Model'ga to'ldiradi. */
    private void populateRows(Model model, UUID actorUserId, UUID tenantId) {
        List<MemberSummary> members = queryService.listMembers(actorUserId, tenantId);
        model.addAttribute("members", members);
        model.addAttribute("count", members.size());
        model.addAttribute("tenantId", tenantId);
        model.addAttribute("assignableRoles", ASSIGNABLE_ROLES);
    }

    /** Mutatsiya xatosini HTMX'ga inline error sifatida qaytaradi (#invite-error). */
    private String inlineError(Model model, HttpServletResponse response, String message) {
        model.addAttribute("error", message);
        response.setHeader("HX-Reswap", "innerHTML");
        response.setHeader("HX-Retarget", "#invite-error");
        return "web/fragments/member-rows :: inviteError";
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }
}
