package com.engops.platform.sharedkernel.exception;

/**
 * Platformadagi barcha maxsus xatoliklarning ota (base) klassi.
 *
 * Barcha biznes va texnik xatoliklar shu klassdan voris oladi.
 * Bu orqali platformaga xos xatoliklarni Spring yoki boshqa kutubxona
 * xatoliklaridan ajratib olish oson bo'ladi.
 *
 * Ishlatilish tartibi:
 * - To'g'ridan-to'g'ri PlatformException ishlatilmaydi
 * - Uning vorislaridan biri tanlanadi:
 *   {@link ResourceNotFoundException}, {@link AccessDeniedException}, {@link BusinessRuleException}
 */
public abstract class PlatformException extends RuntimeException {

    private final String errorCode;

    /**
     * Xatolik kodi va xabar bilan exception yaratadi.
     *
     * @param errorCode qisqa xatolik identifikatori (masalan: "RESOURCE_NOT_FOUND")
     * @param message batafsil xatolik xabari
     */
    protected PlatformException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Xatolik kodi, xabar va asl sabab bilan exception yaratadi.
     *
     * @param errorCode qisqa xatolik identifikatori
     * @param message batafsil xatolik xabari
     * @param cause asl sabab bo'lgan exception
     */
    protected PlatformException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Xatolik kodini qaytaradi.
     * Bu kod API javoblarida va loglarda xatolikni identifikatsiya qilish uchun ishlatiladi.
     *
     * @return xatolik kodi string
     */
    public String getErrorCode() {
        return errorCode;
    }
}
