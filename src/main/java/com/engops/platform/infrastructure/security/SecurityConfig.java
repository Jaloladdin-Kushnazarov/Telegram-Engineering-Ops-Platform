package com.engops.platform.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security konfiguratsiyasi (Phase 124 + Phase 125 + Phase 126 +
 * Phase 146 + Phase 148).
 *
 * <p>Phase 124'da skeleton: CSRF/formLogin/httpBasic disabled, STATELESS session,
 * hamma endpoint'lar {@code permitAll}.</p>
 *
 * <p>Phase 125'da JWT identity foundation: {@code AuthenticatedActor},
 * {@code @CurrentActor}, {@code CurrentActorArgumentResolver}, conditional
 * {@code JwtDecoder} bean ({@code app.security.jwt.hmac-secret} property
 * mavjud bo'lganda).</p>
 *
 * <p>Phase 126'da {@code oauth2ResourceServer} chain conditional ravishda
 * wire qilindi — faqat {@link JwtDecoder} bean mavjud bo'lsa.</p>
 *
 * <p><strong>Phase 146 — endpoint authentication enforcement:</strong>
 * Avvalgi yagona {@code anyRequest().permitAll()} qoidasi explicit rule
 * tartibi bilan almashtirildi:</p>
 * <ol>
 *   <li>{@code /actuator/health/**} — {@code permitAll}: Kubernetes
 *       liveness/readiness probelar uchun ochiq qoldiriladi.</li>
 *   <li>{@code /actuator/**} — {@code authenticated}: metrics, info, flyway
 *       kabi sezgir endpoint'lar production information leak'ini oldini
 *       olish uchun avtorizatsiya talab qiladi.</li>
 *   <li>{@code /api/**} — {@code authenticated}: barcha business REST
 *       endpoint'lar filter chain'da default-deny posture'iga ega bo'ladi.
 *       Yangi controller {@code @CurrentActor}'ni unutsa ham accidentally
 *       public bo'lib qolmaydi.</li>
 *   <li>{@code anyRequest()} — {@code permitAll}: tegishli bo'lmagan yo'llar
 *       (masalan, mavjud bo'lmagan path'lar Spring MVC tomonidan 404 sifatida
 *       qaytariladi va auth challenge qaytarilmaydi).</li>
 * </ol>
 *
 * <p><strong>Phase 148 — JSON envelope for filter-chain rejects:</strong>
 * Spring Security filter-chain reject yo'llari (autentifikatsiya yo'q yoki
 * yaroqsiz, ruxsat yo'q) endi platforma {@link com.engops.platform.infrastructure.web.ApiErrorResponse}
 * shaklini saqlaydi. Mexanizm:</p>
 * <ul>
 *   <li>{@code http.exceptionHandling(...)} default fallback'ni
 *       {@link JsonAuthenticationEntryPoint} (401 + UNAUTHORIZED envelope) va
 *       {@link JsonAccessDeniedHandler} (403 + ACCESS_DENIED envelope) bilan
 *       almashtiradi. Bu Spring Security'ning default
 *       {@code Http403ForbiddenEntryPoint} (bo'sh body, 403) yo'lini
 *       to'sib qo'yadi.</li>
 *   <li>{@code oauth2ResourceServer(...)} chain'iga ham xuddi shu entry
 *       point va handler ulanadi — Bearer token reject'lari
 *       ({@code BearerTokenAuthenticationEntryPoint} default) ham platforma
 *       envelope shaklida qaytariladi.</li>
 *   <li>Correlation id {@link com.engops.platform.infrastructure.web.CorrelationIdFilter}
 *       (HIGHEST_PRECEDENCE) tomonidan filter chain'dan oldin MDC'ga
 *       o'rnatiladi va envelope'ga qo'shiladi.</li>
 *   <li>{@code WWW-Authenticate: Bearer} header 401 javoblarda saqlanadi
 *       (resource-server chain o'rnatgan header birinchi navbatda saqlanadi;
 *       aks holda fallback sifatida {@code Bearer} qiymati o'rnatiladi).</li>
 * </ul>
 *
 * <p><strong>JWT decoder posture'ga ta'siri (Phase 148 holati):</strong></p>
 * <ul>
 *   <li>Decoder mavjud (production: {@code issuer-uri}/{@code jwk-set-uri}
 *       sozlangan; test: HMAC secret @TestPropertySource bilan): missing yoki
 *       invalid Bearer uchun {@code JsonAuthenticationEntryPoint}
 *       <strong>401 + UNAUTHORIZED envelope</strong> qaytaradi
 *       ({@code WWW-Authenticate: Bearer}). Valid Bearer + DB permission yo'q:
 *       facade/service-level <strong>403 ACCESS_DENIED envelope</strong>
 *       ({@code GlobalExceptionHandler}, kontrakt o'zgarmaydi).</li>
 *   <li>Decoder mavjud emas (test slice'lar yoki misconfiguration): hech
 *       qanday resource-server entry point wire qilinmaydi va
 *       {@code http.exceptionHandling} default'lari ishlaydi —
 *       {@code authenticated()} qoidasi anonymous principal uchun bajarilmagani
 *       sababli {@code ExceptionTranslationFilter} entry point'ni chaqiradi va
 *       {@code JsonAuthenticationEntryPoint} <strong>401 + UNAUTHORIZED envelope</strong>
 *       qaytaradi (avvalgi {@code Http403ForbiddenEntryPoint} 403 bo'sh-body
 *       fallback'i o'rnini bosadi).</li>
 * </ul>
 *
 * <p><strong>Out of scope for Phase 148:</strong> {@code @PreAuthorize} yoki
 * method security; controller/facade/service o'zgarishlari;
 * {@code management.endpoints.web.exposure.include} cheklash; JWT claim
 * model.</p>
 *
 * <p><strong>Test slice integratsiyasi:</strong> Production'da
 * {@code @SpringBootApplication} component-scan orqali yuklanadi.
 * {@code @WebMvcTest} slice'larida {@code @Import(SecurityConfig.class)} orqali
 * explicit yuklanadi. {@link JsonAuthenticationEntryPoint} va
 * {@link JsonAccessDeniedHandler} klasslari shu konfiguratsiya ichidagi
 * {@code @Bean} factory metodlari ({@link #jsonAuthenticationEntryPoint(ObjectMapper)},
 * {@link #jsonAccessDeniedHandler(ObjectMapper)}) orqali yaratiladi va
 * SecurityConfig bilan birga yuklanadi (auto-configured Jackson
 * {@link ObjectMapper} dependency'sini ishlatadi).</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new JsonAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public JsonAccessDeniedHandler jsonAccessDeniedHandler(ObjectMapper objectMapper) {
        return new JsonAccessDeniedHandler(objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     ObjectProvider<JwtDecoder> jwtDecoderProvider,
                                                     JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint,
                                                     JsonAccessDeniedHandler jsonAccessDeniedHandler)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // Phase 146: Kubernetes/load-balancer health probelari
                        // uchun ochiq qoldiriladi.
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Phase 146: qolgan actuator endpoint'lari (info, metrics,
                        // flyway, va h.k.) production information leak'ini oldini
                        // olish uchun authenticated.
                        .requestMatchers("/actuator/**").authenticated()
                        // Phase 171: Telegram inbound webhook — Telegram JWT
                        // yubormaydi, shuning uchun JWT chain'idan ataylab
                        // chiqariladi. TelegramWebhookController o'zining
                        // X-Telegram-Bot-Api-Secret-Token tekshiruvi bilan
                        // fail-closed himoya qiladi (Phase 158 token-activation
                        // bilan bir xil pattern).
                        .requestMatchers(HttpMethod.POST, "/api/telegram/webhook").permitAll()
                        // Phase 211: dev profile token issuer endpoints — faqat
                        // app.security.dev-mode.enabled=true bo'lganda
                        // DevAuthController bean yaratiladi. Property
                        // o'rnatilmagan (production default) — controller yo'q
                        // va Spring MVC 404 qaytaradi; bu permitAll matcher
                        // hech qanday endpoint surface'ini ochmaydi. ZERO
                        // production impact. MUST precede /api/** authenticated.
                        .requestMatchers("/api/dev/**").permitAll()
                        // Phase 218a: Telegram Login Widget callback endpoint
                        // — JWT yo'q (foydalanuvchi hali login qilmagan).
                        // TelegramLoginService HMAC hash verify + 24h auth_date
                        // tekshiruvi orqali himoyalanadi (bot token
                        // app.security.telegram.bot-token bilan).
                        .requestMatchers("/api/auth/telegram-login").permitAll()
                        // Phase 146: barcha business API endpoint'lari default-deny.
                        .requestMatchers("/api/**").authenticated()
                        // Phase 209: web-side analytics shim endpoints — JWT-protected.
                        // /web/api/** matcher MUST precede broader /web/** permitAll
                        // (Spring Security uses first-match semantics).
                        .requestMatchers("/web/api/**").authenticated()
                        // Phase 207: web UI scaffolding (Thymeleaf-rendered server-side
                        // HTML). Hozircha anonymous — auth Phase 208'da qo'shiladi.
                        // Faqat /web/** namespace, /api/** ga ta'sir qilmaydi.
                        .requestMatchers("/web/**").permitAll()
                        // Boshqa yo'llar (masalan mavjud bo'lmagan endpoint'lar va
                        // SecurityConfigTest probe path'lari) uchun avvalgi
                        // permitAll posture saqlanadi — auth challenge qaytarilmaydi
                        // va MVC dispatcher 404 qaytaradi.
                        .anyRequest().permitAll())
                // Phase 148: filter-chain reject yo'llari ApiErrorResponse
                // envelope shaklini saqlasin uchun JSON entry point va access
                // denied handler default fallback'larni almashtiradi.
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                        .accessDeniedHandler(jsonAccessDeniedHandler));

        // Phase 126: JwtDecoder bean mavjud bo'lganda oauth2ResourceServer'ni
        // explicit decoder + JwtActorConverter bilan wire qilamiz. Phase 148:
        // resource-server chain'iga ham JSON entry point/handler ulanadi —
        // Bearer reject'lari (BearerTokenAuthenticationEntryPoint default
        // o'rniga) shu yo'ldan o'tadi va envelope shaklini saqlaydi.
        // Decoder mavjud bo'lmagan profillarda chain umuman qo'shilmaydi va
        // exceptionHandling default'lari (yuqorida) ishlaydi.
        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
        if (decoder != null) {
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                    .accessDeniedHandler(jsonAccessDeniedHandler)
                    .jwt(jwt -> jwt
                            .decoder(decoder)
                            .jwtAuthenticationConverter(new JwtActorConverter())));
        }

        return http.build();
    }
}
