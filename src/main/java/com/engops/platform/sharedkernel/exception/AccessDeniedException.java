package com.engops.platform.sharedkernel.exception;

/**
 * Foydalanuvchida kerakli ruxsat (permission) bo'lmaganda ishlatiladi.
 *
 * Bu exception HTTP 403 javobiga aylantiriladi.
 * Spring Security'ning AccessDeniedException'idan farqli — bu platformaning o'z biznes qoidasi.
 *
 * Ishlatilish namunasi:
 * {@code throw new AccessDeniedException("Bu tenantda workitem yaratish huquqi yo'q")}
 */
public class AccessDeniedException extends PlatformException {

    private static final String ERROR_CODE = "ACCESS_DENIED";

    /**
     * Ruxsat rad etilganligi haqida xatolik yaratadi.
     *
     * @param message nima uchun ruxsat berilmaganligining tavsifi
     */
    public AccessDeniedException(String message) {
        super(ERROR_CODE, message);
    }
}
