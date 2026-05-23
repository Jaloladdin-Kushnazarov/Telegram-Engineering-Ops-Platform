package com.engops.platform.analytics;

/**
 * Phase 205 — analytics aggregate bucket.
 *
 * Bitta dimension qiymati (e.g. status code, work item type, severity)
 * va shu qiymat bilan mos keladigan yozuvlar soni.
 */
public record AnalyticsBucket(String label, long count) {}
