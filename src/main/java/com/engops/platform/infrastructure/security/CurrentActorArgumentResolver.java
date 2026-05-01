package com.engops.platform.infrastructure.security;

import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Phase 125 — {@link CurrentActor} annotatsiyali parametr'larni Spring
 * SecurityContext'dan resolve qiladi.
 *
 * <p>Qo'llab-quvvatlanadigan parametr turlari:</p>
 * <ul>
 *   <li>{@link UUID} — {@link AuthenticatedActor#appUserId()} uzatiladi</li>
 *   <li>{@link AuthenticatedActor} — to'liq actor uzatiladi</li>
 * </ul>
 *
 * <p>Resolver xulqi:</p>
 * <ol>
 *   <li>Agar {@code Authentication} {@code null} yoki anonymous bo'lsa →
 *       {@link AccessDeniedException} (403 GlobalExceptionHandler orqali)</li>
 *   <li>Agar principal {@link AuthenticatedActor} bo'lmasa →
 *       {@link AccessDeniedException}</li>
 *   <li>Aks holda parametr turiga mos qiymat qaytariladi</li>
 * </ol>
 *
 * <p>Phase 125'da hech bir mavjud controller bu resolver'ni ishlatmaydi —
 * shuning uchun mavjud endpoint xulqi o'zgarmaydi va hech qanday yangi
 * autentifikatsiya majburiyati paydo bo'lmaydi. Resolver foundation sifatida
 * Phase 126+ migratsiyalari uchun tayyor.</p>
 */
public class CurrentActorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(CurrentActor.class)) {
            return false;
        }
        Class<?> type = parameter.getParameterType();
        return UUID.class.equals(type) || AuthenticatedActor.class.equals(type);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                   ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest,
                                   WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "Autentifikatsiyalangan actor talab qilinadi");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedActor actor)) {
            throw new AccessDeniedException(
                    "Authentication principal AuthenticatedActor turida emas");
        }

        Class<?> type = parameter.getParameterType();
        if (UUID.class.equals(type)) {
            return actor.appUserId();
        }
        if (AuthenticatedActor.class.equals(type)) {
            return actor;
        }
        // supportsParameter allaqachon filter qildi — bu yo'l yetib bormaydi
        throw new IllegalStateException(
                "@CurrentActor parametri qo'llab-quvvatlanmagan turda: " + type);
    }
}
