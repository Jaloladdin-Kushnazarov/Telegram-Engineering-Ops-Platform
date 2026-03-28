package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * OwnerUserId bo'yicha filtrlangan combined support details endpoint'ining HTTP response DTO'si.
 *
 * Har bir item to'liq support details o'z ichiga oladi:
 * - workItem: work item metadata + ordered update history
 * - deliveryObservability: delivery metrics + recent attempts
 *
 * Mavjud WorkItemSupportDetailsResponse'ni nested item sifatida qayta ishlatadi —
 * kontrakt dublikatsiyasi yo'q.
 *
 * @param items combined support details ro'yxati
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkItemSupportDetailsByOwnerResponse(
        List<WorkItemSupportDetailsResponse> items) {}
