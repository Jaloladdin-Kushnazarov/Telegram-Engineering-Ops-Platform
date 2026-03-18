package com.engops.platform.infrastructure.web;

import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.PlatformException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Platformadagi barcha xatoliklarni markaziy qayta ishlovchi.
 *
 * Bu klass har bir controller'da alohida try-catch yozish o'rniga,
 * barcha exception'larni bitta joyda ushlaydi va standart formatdagi
 * {@link ApiErrorResponse} ga aylantiradi.
 *
 * Xatolik turlari va HTTP status kodlari:
 * - {@link ResourceNotFoundException} -> 404 Not Found
 * - {@link AccessDeniedException} -> 403 Forbidden
 * - {@link BusinessRuleException} -> 422 Unprocessable Entity
 * - Boshqa {@link PlatformException} -> 400 Bad Request
 * - Kutilmagan xatoliklar -> 500 Internal Server Error
 *
 * Har bir xatolik logga yoziladi va correlation-id javobga qo'shiladi.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Resurs topilmadi xatoligini qayta ishlaydi.
     *
     * @param ex yuz bergan exception
     * @param request HTTP so'rov ob'ekti
     * @return 404 statusli standart xatolik javobi
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                           HttpServletRequest request) {
        log.warn("Resurs topilmadi: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex, request);
    }

    /**
     * Ruxsat rad etildi xatoligini qayta ishlaydi.
     *
     * @param ex yuz bergan exception
     * @param request HTTP so'rov ob'ekti
     * @return 403 statusli standart xatolik javobi
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        log.warn("Ruxsat rad etildi: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, ex, request);
    }

    /**
     * Biznes qoidasi buzildi xatoligini qayta ishlaydi.
     *
     * @param ex yuz bergan exception
     * @param request HTTP so'rov ob'ekti
     * @return 422 statusli standart xatolik javobi
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(BusinessRuleException ex,
                                                               HttpServletRequest request) {
        log.warn("Biznes qoidasi buzildi: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex, request);
    }

    /**
     * Boshqa platforma xatoliklarini qayta ishlaydi.
     *
     * @param ex yuz bergan exception
     * @param request HTTP so'rov ob'ekti
     * @return 400 statusli standart xatolik javobi
     */
    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiErrorResponse> handlePlatformException(PlatformException ex,
                                                                    HttpServletRequest request) {
        log.warn("Platforma xatoligi: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex, request);
    }

    /**
     * So'rov parametri yetishmayotgan yoki turi noto'g'ri bo'lganda qayta ishlaydi.
     *
     * @param ex yuz bergan exception
     * @param request HTTP so'rov ob'ekti
     * @return 400 statusli standart xatolik javobi
     */
    @ExceptionHandler({MissingServletRequestParameterException.class,
                        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleRequestBinding(Exception ex,
                                                                  HttpServletRequest request) {
        log.warn("So'rov parametri xatosi: {}", ex.getMessage());

        ApiErrorResponse body = ApiErrorResponse.of(
                "BAD_REQUEST",
                ex.getMessage(),
                MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Noto'g'ri argument xatoliklarini qayta ishlaydi.
     *
     * Facade/service qatlamidagi IllegalArgumentException'lar shu yerda
     * 400 Bad Request sifatida qaytariladi.
     *
     * @param ex yuz bergan exception
     * @param request HTTP so'rov ob'ekti
     * @return 400 statusli standart xatolik javobi
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                                   HttpServletRequest request) {
        log.warn("Noto'g'ri argument: {}", ex.getMessage());

        ApiErrorResponse body = ApiErrorResponse.of(
                "BAD_REQUEST",
                ex.getMessage(),
                MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Kutilmagan (dasturiy) xatoliklarni qayta ishlaydi.
     * Stack trace to'liq logga yoziladi, lekin client'ga faqat umumiy xabar yuboriladi.
     *
     * @param ex yuz bergan exception
     * @param request HTTP so'rov ob'ekti
     * @return 500 statusli standart xatolik javobi
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex,
                                                             HttpServletRequest request) {
        log.error("Kutilmagan xatolik yuz berdi", ex);

        ApiErrorResponse body = ApiErrorResponse.of(
                "INTERNAL_ERROR",
                "Ichki server xatoligi yuz berdi",
                MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * PlatformException'dan standart javob yaratadi.
     * Yordamchi metod — takroriy kodni kamaytiradi.
     */
    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status,
                                                           PlatformException ex,
                                                           HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY),
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}