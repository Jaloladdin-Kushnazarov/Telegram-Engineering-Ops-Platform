package com.engops.platform.tenantconfig.model;

import com.engops.platform.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Telegram chat bog'lanishi — tenantni Telegram guruhiga bog'laydi.
 */
@Entity
@Table(name = "telegram_chat_binding", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "chat_id"}))
public class TelegramChatBinding extends BaseEntity {

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "chat_id", nullable = false)
    private long chatId;

    @Column(name = "chat_title")
    private String chatTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "binding_type", nullable = false)
    private ChatBindingType bindingType = ChatBindingType.MAIN_GROUP;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "chatBinding", fetch = FetchType.LAZY)
    private List<TelegramTopicBinding> topicBindings = new ArrayList<>();

    protected TelegramChatBinding() {}

    public TelegramChatBinding(UUID tenantId, long chatId, String chatTitle) {
        this.tenantId = tenantId;
        this.chatId = chatId;
        this.chatTitle = chatTitle;
    }

    public UUID getTenantId() { return tenantId; }
    public long getChatId() { return chatId; }
    public String getChatTitle() { return chatTitle; }
    public void setChatTitle(String chatTitle) { this.chatTitle = chatTitle; }
    public ChatBindingType getBindingType() { return bindingType; }
    public void setBindingType(ChatBindingType bindingType) { this.bindingType = bindingType; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<TelegramTopicBinding> getTopicBindings() { return Collections.unmodifiableList(topicBindings); }
}
