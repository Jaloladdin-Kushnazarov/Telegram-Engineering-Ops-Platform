package com.engops.platform.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 124 — Spring Security skeleton.
 *
 * <p>Bu konfiguratsiya hech qanday endpoint'ni autentifikatsiya talab qilmaydi.
 * Maqsadi: kelajakdagi JWT phase'lari uchun infrastructure'ni tayyorlash, lekin
 * mavjud HTTP endpoint xulqini saqlash.</p>
 *
 * <p>Hozirgi xulq:</p>
 * <ul>
 *   <li>CSRF disabled — stateless REST API</li>
 *   <li>Session stateless — har so'rov mustaqil</li>
 *   <li>Form login disabled — Spring Security default'i bartaraf etiladi</li>
 *   <li>HTTP Basic disabled — Spring Security default'i bartaraf etiladi</li>
 *   <li>Hamma so'rovlar permitAll — autentifikatsiya talab qilinmaydi</li>
 * </ul>
 *
 * <p>Atayin qilinmagan (kelajak phase'lar uchun):</p>
 * <ul>
 *   <li>JwtDecoder bean (Phase 125)</li>
 *   <li>oauth2ResourceServer chain (Phase 125)</li>
 *   <li>AuthenticatedActor + @CurrentActor resolver (Phase 125)</li>
 *   <li>Endpoint-darajasidagi authorization (Phase 126+)</li>
 *   <li>Permission enforcement (Phase 127+)</li>
 * </ul>
 *
 * <p><strong>Test slice integratsiyasi:</strong> Production'da bu klass
 * {@code @SpringBootApplication} component-scan orqali yuklanadi.
 * {@code @WebMvcTest} slice'i {@code @Configuration} klasslarni filter qiladi,
 * shuning uchun mavjud controller test'lar shu klassni
 * {@code @Import(SecurityConfig.class)} bilan explicit ravishda yuklab olishadi —
 * shu orqali Spring Boot'ning default secure-everything chain'i bartaraf
 * etiladi va test'lar 403/401 olmaydi.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
        return http.build();
    }
}
