package com.engops.platform.web;

import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.infrastructure.security.CurrentActor;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.TenantSummary;
import com.engops.platform.tenantconfig.model.Tenant;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 210 — tenant selector HTMX shim.
 *
 * <p>Returns a Thymeleaf {@code <select>} fragment listing the tenants the
 * authenticated actor has any ACTIVE membership in. The dropdown drives
 * the {@code ?tenantId=<uuid>} query parameter used by /web/dashboard,
 * /web/work-items, and subsequent UI pages.</p>
 *
 * <p>Authorization model: implicit via "actor must have an ACTIVE
 * Membership in the listed tenant". The endpoint itself is JWT-protected
 * by the Phase 209B {@code /web/api/**} matcher; no per-tenant
 * AdminAuthorizationService check is needed because the result is
 * already scoped to the actor's own memberships (no cross-tenant leak).</p>
 *
 * <p>Phase 210 design note: a dedicated {@code listTenantsForActor}
 * method on {@link TenantConfigQueryService} was considered (D2) but
 * skipped — the existing public surface (MembershipRepository +
 * findTenantById) composes correctly without modifying the tenantconfig
 * query service.</p>
 */
@Controller
@RequestMapping("/web/api/tenants")
public class TenantsWebController {

    private final MembershipRepository membershipRepository;
    private final TenantConfigQueryService tenantConfigQueryService;

    public TenantsWebController(MembershipRepository membershipRepository,
                                 TenantConfigQueryService tenantConfigQueryService) {
        this.membershipRepository = membershipRepository;
        this.tenantConfigQueryService = tenantConfigQueryService;
    }

    @GetMapping("/options")
    public String options(@CurrentActor UUID actorUserId,
                           @RequestParam(required = false) UUID activeTenantId,
                           Model model) {
        List<TenantSummary> tenants = collectActorTenants(actorUserId);
        model.addAttribute("tenants", tenants);
        model.addAttribute("activeTenantId", activeTenantId);
        return "web/fragments/tenant-select :: select";
    }

    private List<TenantSummary> collectActorTenants(UUID actorUserId) {
        if (actorUserId == null) {
            return List.of();
        }
        List<Membership> memberships = membershipRepository.findByUserId(actorUserId);
        Set<UUID> seenTenantIds = new HashSet<>();
        List<TenantSummary> tenants = new ArrayList<>();
        for (Membership m : memberships) {
            if (m.getStatus() != MembershipStatus.ACTIVE) {
                continue;
            }
            if (!seenTenantIds.add(m.getTenantId())) {
                continue;
            }
            tenantConfigQueryService.findTenantById(m.getTenantId())
                    .map(TenantSummary::from)
                    .ifPresent(tenants::add);
        }
        // Deterministic ordering: alphabetical by display name, then by slug.
        tenants.sort(Comparator
                .comparing((TenantSummary t) -> t.displayName() == null ? "" : t.displayName())
                .thenComparing(TenantSummary::slug));
        return tenants;
    }
}
