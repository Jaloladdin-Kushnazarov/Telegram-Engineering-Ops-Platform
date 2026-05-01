package com.engops.platform.infrastructure.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Phase 125 — {@link CurrentActorArgumentResolver}'ni Spring MVC ga ro'yxatga oladi.
 *
 * <p>Bu konfiguratsiya {@code @WebMvcTest} slice'i tomonidan ham yuklanadi
 * (Phase 124 mexanizmi: {@code @Import(SecurityConfig.class)} controller test'larida —
 * keyingi phase'da bu yerga ham {@link SecurityWebMvcConfig} qo'shiladi
 * agar @CurrentActor controller test'larida ishlatilsa).</p>
 */
@Configuration
public class SecurityWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentActorArgumentResolver());
    }
}
