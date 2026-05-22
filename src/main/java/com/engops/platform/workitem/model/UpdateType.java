package com.engops.platform.workitem.model;

/**
 * Work item yangilanish turlari.
 *
 * <p>{@code update_type_code} DB ustuni {@code VARCHAR(50)} — yangi enum
 * qiymati schema migration talab qilmaydi.</p>
 */
public enum UpdateType {
    COMMENT,
    STATUS_CHANGE,
    ASSIGNMENT,
    DESCRIPTION_CHANGE,
    PRIORITY_CHANGE,
    /** Phase 190 — severity_code yangilanganida yoziladi. */
    SEVERITY_CHANGE,
    REOPEN
}
