-- =====================================================================
-- Phase 21: Telegram delivery attempt persistence
-- =====================================================================

-- Telegram outbound delivery urinishlari — append-only
-- Har bir dispatch attempt bir yozuv sifatida saqlanadi.
-- "Latest" aniqlash: attempted_at DESC, id DESC (deterministic tie-breaker).
CREATE TABLE telegram_delivery_attempt (
    id                      UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL REFERENCES tenant(id),
    work_item_id            UUID            NOT NULL REFERENCES work_item(id),
    operation               VARCHAR(50)     NOT NULL,
    target_chat_binding_id  UUID            NOT NULL,
    target_topic_id         BIGINT          NOT NULL,
    delivery_outcome        VARCHAR(50)     NOT NULL,
    external_message_id     BIGINT,
    failure_code            VARCHAR(255),
    failure_reason          TEXT,
    attempted_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_tda_tenant_workitem_latest
    ON telegram_delivery_attempt(tenant_id, work_item_id, attempted_at DESC, id DESC);
