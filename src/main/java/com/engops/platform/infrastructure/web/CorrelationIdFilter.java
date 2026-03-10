package com.engops.platform.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Har bir HTTP so'rovga noyob correlation-id biriktiruvchi filter.
 *
 * Correlation-id nima uchun kerak:
 * - Bitta so'rov davomidagi barcha loglarni bog'lash uchun
 * - Xatolik yuz berganda, shu ID orqali barcha tegishli loglarni topish oson
 * - Mikroservislar yoki tashqi tizimlar bilan integratsiyada so'rovni kuzatish uchun
 *
 * Ishlash tartibi:
 * 1. So'rov headeridan "X-Correlation-Id" qidiriladi
 * 2. Agar mavjud bo'lsa — shu ID ishlatiladi (tashqi tizimdan kelgan holat)
 * 3. Agar mavjud bo'lmasa — yangi UUID generatsiya qilinadi
 * 4. ID MDC (Mapped Diagnostic Context) ga qo'yiladi — barcha loglar bu ID ni o'z ichiga oladi
 * 5. Javob headeriga ham qo'shiladi — client o'z so'rovini kuzatishi uchun
 * 6. So'rov tugagach MDC tozalanadi (memory leak oldini olish)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** HTTP header nomi — so'rov va javobda ishlatiladi */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /** MDC kaliti — log pattern'da %X{correlationId} orqali chiqariladi */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * Har bir so'rovni qayta ishlaydi va correlation-id ni MDC ga joylashtiradi.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}