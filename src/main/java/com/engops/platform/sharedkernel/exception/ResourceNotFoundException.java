package com.engops.platform.sharedkernel.exception;

/**
 * So'ralgan resurs topilmaganida ishlatiladi.
 *
 * Masalan: WorkItem, User yoki Tenant bazadan topilmasa.
 * Bu exception HTTP 404 javobiga aylantiriladi
 * ({@link com.engops.platform.infrastructure.web.GlobalExceptionHandler} tomonidan).
 *
 * Ishlatilish namunasi:
 * {@code throw new ResourceNotFoundException("WorkItem", workItemId)}
 */
public class ResourceNotFoundException extends PlatformException {

    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    /**
     * Resurs turi va identifikatori asosida xatolik yaratadi.
     *
     * @param resourceType resurs turi nomi (masalan: "WorkItem", "User")
     * @param resourceId resursning identifikatori
     */
    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(ERROR_CODE, "%s topilmadi: %s".formatted(resourceType, resourceId));
    }
}
