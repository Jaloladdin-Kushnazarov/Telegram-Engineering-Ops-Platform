package com.engops.platform.sharedkernel.exception;

/**
 * Biznes qoidasi buzilganida ishlatiladi.
 *
 * Masalan: WorkItem noto'g'ri holatga o'tkazilmoqchi bo'lganda,
 * yoki yaroqsiz ma'lumot bilan operatsiya bajarilmoqchi bo'lganda.
 *
 * Bu exception HTTP 422 (Unprocessable Entity) javobiga aylantiriladi.
 *
 * Ishlatilish namunasi:
 * {@code throw new BusinessRuleException("INVALID_TRANSITION", "FIXED holatidan PROCESSING ga o'tish mumkin emas")}
 */
public class BusinessRuleException extends PlatformException {

    /**
     * Biznes qoidasi buzilganligi haqida xatolik yaratadi.
     *
     * @param errorCode aniq xatolik kodi (masalan: "INVALID_TRANSITION")
     * @param message batafsil tavsif
     */
    public BusinessRuleException(String errorCode, String message) {
        super(errorCode, message);
    }
}
