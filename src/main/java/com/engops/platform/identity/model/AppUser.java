package com.engops.platform.identity.model;

import com.engops.platform.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Platforma foydalanuvchisi.
 * telegram_user_id — asosiy Telegram identifikatori (username emas!).
 */
@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

    @NotNull
    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private Long telegramUserId;

    @Size(max = 255)
    @Column(name = "username")
    private String username;

    @Size(max = 255)
    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    protected AppUser() {}

    public AppUser(Long telegramUserId, String displayName) {
        this.telegramUserId = telegramUserId;
        this.displayName = displayName;
        this.status = UserStatus.ACTIVE;
    }

    public AppUser(UUID id, Long telegramUserId, String displayName) {
        super(id);
        this.telegramUserId = telegramUserId;
        this.displayName = displayName;
        this.status = UserStatus.ACTIVE;
    }

    public Long getTelegramUserId() { return telegramUserId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public boolean isActive() { return status == UserStatus.ACTIVE; }
}
