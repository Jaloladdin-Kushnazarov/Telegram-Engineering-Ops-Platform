package com.engops.platform.admin.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * Phase 143 — birinchi admin uchun bootstrap konfiguratsiyasi.
 *
 * <p>Property prefix: {@code app.bootstrap.admin}</p>
 *
 * <p>Default {@link #enabled} = false — production deployment'lar tasodifan
 * bootstrap qilmasligi uchun. Operator atayin {@code enabled=true} + barcha
 * required field'larni o'rnatadi (env var orqali tavsiya etiladi):</p>
 * <ul>
 *   <li>{@code app.bootstrap.admin.tenant-name}</li>
 *   <li>{@code app.bootstrap.admin.tenant-slug}</li>
 *   <li>{@code app.bootstrap.admin.tenant-timezone} (default "UTC")</li>
 *   <li>{@code app.bootstrap.admin.app-user-id} (UUID — JWT {@code sub} ga teng)</li>
 *   <li>{@code app.bootstrap.admin.telegram-user-id}</li>
 *   <li>{@code app.bootstrap.admin.display-name}</li>
 *   <li>{@code app.bootstrap.admin.username} (ixtiyoriy)</li>
 * </ul>
 *
 * <p>Required field'lar {@link BootstrapAdminInitializer} ichida fail-fast
 * tekshiriladi (enabled=true bo'lsa).</p>
 */
@ConfigurationProperties("app.bootstrap.admin")
public class BootstrapProperties {

    /** Default false — bootstrap atayin yoqilishi shart. */
    private boolean enabled = false;

    /** Yangi tenant nomi (REQUIRED if enabled). */
    private String tenantName;

    /** Yangi tenant slug — idempotensiya kaliti (REQUIRED if enabled, unique). */
    private String tenantSlug;

    /** Yangi tenant timezone (default UTC). */
    private String tenantTimezone = "UTC";

    /**
     * Birinchi admin AppUser uchun deterministik UUID.
     * <p>Operator IdP'sida JWT {@code sub} claim'i AYNI shu UUID bo'lishi shart —
     * aks holda admin JWT bilan kelganida {@code AuthenticatedActor.appUserId}
     * yaratilgan AppUser'ga mos kelmaydi va 403 oladi.</p>
     */
    private UUID appUserId;

    /** Telegram user ID (REQUIRED if enabled, unique). */
    private Long telegramUserId;

    /** Telegram username (ixtiyoriy). */
    private String username;

    /** Display name (REQUIRED if enabled). */
    private String displayName;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getTenantSlug() {
        return tenantSlug;
    }

    public void setTenantSlug(String tenantSlug) {
        this.tenantSlug = tenantSlug;
    }

    public String getTenantTimezone() {
        return tenantTimezone;
    }

    public void setTenantTimezone(String tenantTimezone) {
        this.tenantTimezone = tenantTimezone;
    }

    public UUID getAppUserId() {
        return appUserId;
    }

    public void setAppUserId(UUID appUserId) {
        this.appUserId = appUserId;
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public void setTelegramUserId(Long telegramUserId) {
        this.telegramUserId = telegramUserId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
