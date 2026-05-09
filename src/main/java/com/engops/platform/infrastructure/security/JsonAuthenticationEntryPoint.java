package com.engops.platform.infrastructure.security;

import com.engops.platform.infrastructure.web.ApiErrorResponse;
import com.engops.platform.infrastructure.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Phase 148 — Spring Security filter-chain'ida autentifikatsiya rad etilganda
 * platforma {@link ApiErrorResponse} envelope'ini JSON sifatida qaytaradigan
 * {@link AuthenticationEntryPoint} amalga oshiruvchisi.
 *
 * <p>Spring Security'ning default {@code Http403ForbiddenEntryPoint} (decoder
 * yo'q sharoit) yoki {@code BearerTokenAuthenticationEntryPoint} (decoder bor
 * sharoit) bo'sh body bilan javob qaytaradi. Bu klass shu reject yo'lini
 * mavjud {@code GlobalExceptionHandler} envelope shakliga muvofiqlashtiradi:</p>
 * <ul>
 *   <li>HTTP 401 Unauthorized</li>
 *   <li>{@code errorCode = "UNAUTHORIZED"}</li>
 *   <li>{@code message} — autentifikatsiya talab qilinishi haqida xabar</li>
 *   <li>{@code timestamp} — joriy {@link java.time.Instant}</li>
 *   <li>{@code correlationId} — {@link CorrelationIdFilter#CORRELATION_ID_MDC_KEY}
 *       MDC'dan olinadi (filter {@code HIGHEST_PRECEDENCE} tartibda Spring
 *       Security filter chain'idan oldin ishlaydi)</li>
 *   <li>{@code path} — {@link HttpServletRequest#getRequestURI()}</li>
 *   <li>{@code Content-Type: application/json}</li>
 *   <li>{@code WWW-Authenticate} — Spring Security'ning
 *       {@link BearerTokenAuthenticationEntryPoint} delegate sifatida
 *       chaqiriladi. Bu RFC 6750'ga muvofiq diagnostic detail'larni
 *       (masalan {@code Bearer error="invalid_token", error_description="..."})
 *       saqlaydi {@link org.springframework.security.oauth2.core.OAuth2AuthenticationException}
 *       sharoitida. Boshqa holatlarda delegate sodda {@code Bearer} qiymatini
 *       o'rnatadi. Defensive fallback: agar header hech qanday sababdan
 *       o'rnatilmagan bo'lsa, {@code Bearer} qo'lda set qilinadi.</li>
 * </ul>
 *
 * <p><strong>Phase 148 mini-fix — delegate xulqi (Approach 1):</strong>
 * {@link BearerTokenAuthenticationEntryPoint#commence(HttpServletRequest,
 * HttpServletResponse, AuthenticationException)} faqat status va header'ni
 * o'rnatadi (body'ga tegmaydi — verified Spring Security 6.4 source review).
 * Shu sababli undan oldin chaqirib, keyin status'ni 401 ga majburiy o'rnatib
 * (delegate ba'zi {@code BearerTokenError}'lar uchun 400 ham qaytarishi
 * mumkin), keyin envelope JSON body'ni yozish xavfsiz.</p>
 *
 * <p>{@link SecurityConfig} bu bean'ni o'z {@code @Bean} factory metodi orqali
 * yaratadi va {@code http.exceptionHandling(...).authenticationEntryPoint(...)}
 * hamda {@code oauth2ResourceServer(...).authenticationEntryPoint(...)}
 * slot'lariga ulaydi (decoder mavjud bo'lganda Bearer reject'lari ham shu
 * yo'ldan o'tadi). @WebMvcTest slice'larida {@code @Import(SecurityConfig.class)}
 * orqali avtomatik yuklanadi.</p>
 *
 * <p>Facade/service darajasidagi sharedkernel
 * {@link com.engops.platform.sharedkernel.exception.AccessDeniedException} bu
 * yo'ldan o'tmaydi — u {@code @RestControllerAdvice GlobalExceptionHandler}
 * tomonidan 403 ACCESS_DENIED envelope'iga aylantiriladi (kontrakt
 * o'zgarmaydi).</p>
 */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String ERROR_CODE = "UNAUTHORIZED";
    static final String DEFAULT_MESSAGE = "Autentifikatsiya talab qilinadi";

    /**
     * Spring Security RFC 6750 entry point — diagnostic detail'larni
     * (masalan {@code error="invalid_token"}) hisoblab WWW-Authenticate
     * header'iga yozadi. Stateless va thread-safe.
     */
    private static final BearerTokenAuthenticationEntryPoint BEARER_DELEGATE =
            new BearerTokenAuthenticationEntryPoint();

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // Phase 148 mini-fix (Approach 1): Spring Security delegate'iga RFC 6750
        // mos WWW-Authenticate header'ini hisoblashga ruxsat beramiz. Delegate
        // body yozmaydi (verified — faqat setStatus + addHeader).
        BEARER_DELEGATE.commence(request, response, authException);

        // Status'ni 401 ga majburiy o'rnatamiz — delegate ba'zi
        // BearerTokenError sharoitlarida 400 yoki boshqa status qaytarishi
        // mumkin; biz envelope kontrakti uchun har doim 401 ni ushlaymiz.
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Defensive fallback: delegate har holda Bearer header o'rnatishi
        // kerak, lekin agar nimadir noto'g'ri ketsa Bearer scheme'ni qo'lda
        // set qilamiz.
        if (response.getHeader(HttpHeaders.WWW_AUTHENTICATE) == null) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

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
