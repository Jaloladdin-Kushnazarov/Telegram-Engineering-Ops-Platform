package com.engops.platform.analytics;

/**
 * Phase 205 — Spring Data interface projection for GROUP BY aggregate
 * queries on {@code WorkItem}. JPQL aliases {@code AS label, AS count}
 * map to {@link #getLabel()} / {@link #getCount()}.
 *
 * <p>Bucket label nullable bo'la oladi (masalan severity bo'sh bo'lishi
 * mumkin) — service tomonida filtrlanadi yoki query'da WHERE bilan
 * eksklud qilinadi.</p>
 */
public interface AnalyticsBucketProjection {

    String getLabel();

    long getCount();
}
