package com.engops.platform.tenantconfig.model;

import com.engops.platform.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Telegram topic bog'lanishi — chat ichidagi topic'ni maqsadga bog'laydi.
 * Masalan: "bugs" topic, "incidents" topic.
 */
@Entity
@Table(name = "telegram_topic_binding")
public class TelegramTopicBinding extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_binding_id", nullable = false)
    private TelegramChatBinding chatBinding;

    @Column(name = "topic_id", nullable = false)
    private long topicId;

    @Column(name = "topic_name")
    @Size(max = 500)
    private String topicName;

    @NotBlank
    @Size(max = 100)
    @Column(name = "purpose", nullable = false)
    private String purpose;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected TelegramTopicBinding() {}

    public TelegramTopicBinding(TelegramChatBinding chatBinding, long topicId,
                                 String topicName, String purpose) {
        this.chatBinding = chatBinding;
        this.topicId = topicId;
        this.topicName = topicName;
        this.purpose = purpose;
    }

    public TelegramChatBinding getChatBinding() { return chatBinding; }
    public long getTopicId() { return topicId; }
    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }
    public String getPurpose() { return purpose; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
