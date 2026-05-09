package com.engops.platform.infrastructure.security;

import com.engops.platform.infrastructure.web.ApiErrorResponse;
import com.engops.platform.infrastructure.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Phase 148 — Spring Security filter-chain'ida autentifikatsiyalangan, lekin
 * ruxsatga ega bo'lmagan so'rov rad etilganda platforma
 * {@link ApiErrorResponse} envelope'ini JSON sifatida qaytaradigan
 * {@link AccessDeniedHandler} amalga oshiruvchisi.
 *
 * <p>Bu yo'l asosan Spring Security CSRF/method security/oauth2 scope-based
 * tekshiruvlari uchun ishlaydi. Joriy konfiguratsiyada CSRF disabled,
 * {@code @PreAuthorize} ishlatilmaydi va JWT identity-only — shu sababli
 * filter darajasida AccessDenied (401 emas) holati amaliyotda kam
 * uchraydi. Lekin defense-in-depth uchun bu handler {@link SecurityConfig}
 * o'zining {@code @Bean} factory metodi orqali yaratiladi va
 * {@code http.exceptionHandling(...).accessDeniedHandler(...)} hamda
 * {@code oauth2ResourceServer(...).accessDeniedHandler(...)} slot'lariga
 * ulanadi. @WebMvcTest slice'larida {@code @Import(SecurityConfig.class)}
 * orqali avtomatik yuklanadi va yagona envelope kontraktini ta'minlaydi.</p>
 *
 * <p>Javob shakli:</p>
 * <ul>
 *   <li>HTTP 403 Forbidden</li>
 *   <li>{@code errorCode = "ACCESS_DENIED"}</li>
 *   <li>{@code message} — ruxsat yo'qligi haqidagi xabar</li>
 *   <li>{@code timestamp} — joriy {@link java.time.Instant}</li>
 *   <li>{@code correlationId} — {@link CorrelationIdFilter#CORRELATION_ID_MDC_KEY}
 *       MDC'dan olinadi</li>
 *   <li>{@code path} — {@link HttpServletRequest#getRequestURI()}</li>
 *   <li>{@code Content-Type: application/json}</li>
 * </ul>
 *
 * <p>Diqqat: facade/service-level
 * {@link com.engops.platform.sharedkernel.exception.AccessDeniedException}
 * (sharedkernel) bu handler'dan o'tmaydi — u
 * {@code @RestControllerAdvice GlobalExceptionHandler} tomonidan ushlanadi
 * va aynan shu envelope shaklida 403 ACCESS_DENIED qaytaradi. Ikkala yo'l
 * ham bir xil JSON kontraktiga ega.</p>
 */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    static final String ERROR_CODE = "ACCESS_DENIED";
    static final String DEFAULT_MESSAGE = "Bu operatsiya uchun ruxsat yo'q";

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiErrorResponse body = ApiErrorResponse.of(
                ERROR_CODE,
                DEFAULT_MESSAGE,
                MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY),
                request.getRequestURI()
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
