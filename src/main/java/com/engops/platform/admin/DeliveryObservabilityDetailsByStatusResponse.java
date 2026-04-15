package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * StatusCode bo'yicha filtrlangan delivery observability details endpoint'ining HTTP response DTO'si.
 *
 * Har bir item to'liq delivery observability details o'z ichiga oladi:
 * - work item metadata (id, code, title, type, status)
 * - latest delivery metrics
 * - recent delivery attempts
 *
 * Mavjud DeliveryObservabilityDetailsResponse'ni nested item sifatida qayta ishlatadi —
 * kontrakt dublikatsiyasi yo'q.
 *
 * @param items delivery observability details ro'yxati
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryObservabilityDetailsByStatusResponse(
        List<DeliveryObservabilityDetailsResponse> items) {}
