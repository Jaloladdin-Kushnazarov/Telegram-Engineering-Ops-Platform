package com.engops.platform.dev;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Phase 211 — DEV PROFILE ONLY auth endpoints.
 *
 * <p>Faqat {@code app.security.dev-mode.enabled=true} bo'lganda bean
 * yaratiladi. Production'da property o'rnatilmagan — controller bean
 * yo'q, Spring MVC ushbu URL'lar uchun 404 qaytaradi.</p>
 *
 * <p><strong>Security posture:</strong> /api/dev/** SecurityConfig'da
 * permitAll deb belgilangan (D17). Bu ataylab — dev mode opt-in property
 * orqali yoqiladi va production operatorlari hech qachon yoqmaydi. Agar
 * production deployment'da dev mode tasodifan yoqilsa, eng yomon stsenariy
 * — har qanday caller bootstrap admin (taniqli sentinel UUID) uchun JWT
 * so'ray oladi. Bu trade-off ataylab — dev mode "ishonchli zona"da
 * ishlatiladi.</p>
 *
 * <p><strong>Endpoint'lar:</strong></p>
 * <ul>
 *   <li>{@code GET /api/dev/auth/info} — dev mode tasdiqi + bootstrap UUID'lar</li>
 *   <li>{@code GET /api/dev/auth/bootstrap-admin-token} — bootstrap admin
 *       uchun 1 soatlik JWT</li>
 *   <li>{@code GET /api/dev/auth/token?userId=...} — istalgan UUID uchun
 *       1 soatlik JWT (test user'lar bilan tajriba uchun)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/dev/auth")
@ConditionalOnProperty(name = "app.security.dev-mode.enabled", havingValue = "true")
public class DevAuthController {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final DevTokenIssuer devTokenIssuer;

    public DevAuthController(DevTokenIssuer devTokenIssuer) {
        this.devTokenIssuer = devTokenIssuer;
    }

    @GetMapping("/info")
    public DevInfoResponse info() {
        return new DevInfoResponse(
                true,
                DevBootstrapInitializer.BOOTSTRAP_ADMIN_USER_ID,
                DevBootstrapInitializer.BOOTSTRAP_TENANT_ID);
    }

    @GetMapping("/bootstrap-admin-token")
    public DevTokenResponse bootstrapAdminToken() {
        String jwt = devTokenIssuer.issueToken(
                DevBootstrapInitializer.BOOTSTRAP_ADMIN_USER_ID, TOKEN_TTL);
        return new DevTokenResponse(jwt);
    }

    @GetMapping("/token")
    public DevTokenResponse tokenForUser(@RequestParam UUID userId) {
        String jwt = devTokenIssuer.issueToken(userId, TOKEN_TTL);
        return new DevTokenResponse(jwt);
    }
}
