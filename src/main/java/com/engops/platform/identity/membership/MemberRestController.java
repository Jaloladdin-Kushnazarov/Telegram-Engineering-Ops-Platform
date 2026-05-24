package com.engops.platform.identity.membership;

import com.engops.platform.infrastructure.security.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 219a — tenant member management REST surface.
 *
 * <p>Sirt: {@code /api/tenants/{tenantId}/members}. Barcha endpoint'lar
 * {@code /api/**} default-deny chain ostida (SecurityConfig) — JWT
 * majburiy. Actor {@code @CurrentActor UUID} orqali SecurityContext'dan
 * resolve qilinadi (request body actor maydoniga e'tibor berilmaydi).</p>
 *
 * <p>Per-tenant {@code MEMBER_MANAGE} ruxsat tekshiruvi service
 * layer'da ({@link MembershipCommandService} / {@link MembershipQueryService})
 * amalga oshadi.</p>
 *
 * <p><strong>Exception mapping (GlobalExceptionHandler):</strong>
 * {@code AccessDeniedException} → 403; {@code BusinessRuleException} → 422
 * (errorCode envelope: ALREADY_MEMBER, INVALID_ROLE_CODE, MEMBER_NOT_FOUND,
 * CANNOT_REMOVE_SELF, CANNOT_CHANGE_OWN_ROLE); bean-validation buzilishi →
 * 400; autentifikatsiyasiz so'rov → 401 (filter chain).</p>
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/members")
public class MemberRestController {

    private final MembershipCommandService commandService;
    private final MembershipQueryService queryService;

    public MemberRestController(MembershipCommandService commandService,
                                 MembershipQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    public List<MemberSummary> list(@CurrentActor UUID actorUserId,
                                    @PathVariable UUID tenantId) {
        return queryService.listMembers(actorUserId, tenantId);
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> invite(
            @CurrentActor UUID actorUserId,
            @PathVariable UUID tenantId,
            @Valid @RequestBody InviteMemberRequest request) {
        UUID membershipId = commandService.inviteMember(actorUserId, tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("membershipId", membershipId));
    }

    @PostMapping("/{memberUserId}/role")
    public ResponseEntity<Void> changeRole(
            @CurrentActor UUID actorUserId,
            @PathVariable UUID tenantId,
            @PathVariable UUID memberUserId,
            @Valid @RequestBody ChangeRoleRequest request) {
        commandService.changeRole(actorUserId, tenantId, memberUserId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{memberUserId}")
    public ResponseEntity<Void> remove(
            @CurrentActor UUID actorUserId,
            @PathVariable UUID tenantId,
            @PathVariable UUID memberUserId) {
        commandService.removeMember(actorUserId, tenantId, memberUserId);
        return ResponseEntity.noContent().build();
    }
}
