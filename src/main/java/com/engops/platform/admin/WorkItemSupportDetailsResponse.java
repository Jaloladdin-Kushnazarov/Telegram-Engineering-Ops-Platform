package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Combined work item support details endpoint'ining HTTP response DTO'si.
 *
 * Ikki mustaqil concern'ni bitta response'ga jamlaydi:
 * - workItem: work item metadata + ordered update history
 * - deliveryObservability: delivery metrics + recent attempts
 *
 * Har bir section mavjud response DTO'larni qayta ishlatadi —
 * kontrakt dublikatsiyasi yo'q.
 *
 * @param workItem work item details section
 * @param deliveryObservability delivery observability section
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkItemSupportDetailsResponse(
        WorkItemDetailsResponse workItem,
        DeliveryObservabilityDetailsResponse deliveryObservability) {}
