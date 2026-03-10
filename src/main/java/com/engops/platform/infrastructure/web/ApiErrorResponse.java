package com.engops.platform.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * API xatolik javobining standart formati.
 *
 * Barcha xatoliklar client'ga bir xil strukturada qaytariladi.
 * Bu client tomonida xatoliklarni qayta ishlashni osonlashtiradi.
 *
 * Javob namunasi:
 * <pre>
 * {
 *   "errorCode": "RESOURCE_NOT_FOUND",
 *   "message": "WorkItem topilmadi: 123",
 *   "timestamp": "2026-03-10T12:00:00Z",
 *   "correlationId": "abc-123-def",
 *   "path": "/api/workitems/123"
 * }
 * </pre>
 *
 * @param errorCode xatolik turi identifikatori
 * @param message inson o'qiy oladigan xatolik tavsifi
 * @param timestamp xatolik yuz bergan vaqt (UTC)
 * @param correlationId so'rov correlation identifikatori (loglardan topish uchun)
 * @param path xatolik yuz bergan URL yo'li
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String errorCode,
        String message,
        Instant timestamp,
        String correlationId,
        String path
) {

    /**
     * Yangi xatolik javobi yaratish uchun builder usuli.
     *
     * @param errorCode xatolik kodi
     * @param message xatolik xabari
     * @param correlationId so'rov identifikatori
     * @param path so'rov yo'li
     * @return formatlangan xatolik javobi
     */
    public static ApiErrorResponse of(String errorCode, String message,
                                      String correlationId, String path) {
        return new ApiErrorResponse(errorCode, message, Instant.now(), correlationId, path);
    }
}