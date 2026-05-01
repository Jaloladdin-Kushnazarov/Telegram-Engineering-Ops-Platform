package com.engops.platform.infrastructure.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security konfiguratsiyasi (Phase 124 + Phase 125 + Phase 126 + Phase 146).
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
 * <p><strong>JWT decoder posture'ga ta'siri:</strong></p>
 * <ul>
 *   <li>Decoder mavjud (production: {@code issuer-uri}/{@code jwk-set-uri}
 *       sozlangan; test: HMAC secret @TestPropertySource bilan): missing yoki
 *       invalid Bearer token uchun {@code BearerTokenAuthenticationEntryPoint}
 *       <strong>401</strong> qaytaradi ({@code WWW-Authenticate: Bearer}).
 *       Valid Bearer + DB permission yo'q: facade/service-level
 *       <strong>403 ACCESS_DENIED</strong> ({@code GlobalExceptionHandler}).</li>
 *   <li>Decoder mavjud emas ({@code app.security.jwt.*} sozlanmagan,
 *       shu jumladan {@code @WebMvcTest} slice'lari): hech qanday
 *       resource-server entry point wire qilinmaydi va Spring Security
 *       default fallback {@code Http403ForbiddenEntryPoint} ishlatiladi —
 *       {@code /api/**} va himoyalangan {@code /actuator/**} so'rovlari uchun
 *       <strong>403</strong> qaytariladi (custom envelope'siz). Bu
 *       intentional fail-closed posture: production deployment'lar
 *       JwtDecoder'ni Phase 137 mexanizmi orqali sozlashi shart, aks holda
 *       barcha himoyalangan endpoint'lar 403 bo'lib qoladi.</li>
 * </ul>
 *
 * <p><strong>Out of scope for Phase 146:</strong> custom
 * {@code AuthenticationEntryPoint} JSON envelope (Phase 147 nomzodi);
 * {@code management.endpoints.web.exposure.include} cheklash (Phase 147);
 * {@code @PreAuthorize} yoki method security; controller/facade/service
 * o'zgarishlari.</p>
 *
 * <p><strong>Test slice integratsiyasi:</strong> Production'da
 * {@code @SpringBootApplication} component-scan orqali yuklanadi.
 * {@code @WebMvcTest} slice'larida {@code @Import(SecurityConfig.class)} orqali
 * explicit yuklanadi. {@code @WebMvcTest} slice'lari {@code JwtDecoder} bean
 * yuklamaydi, shuning uchun missing-actor controller testlari Phase 146'dan
 * keyin ham 403 kutadi (resource-server entry point yo'q).</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     ObjectProvider<JwtDecoder> jwtDecoderProvider)
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
                        // Phase 146: barcha business API endpoint'lari default-deny.
                        .requestMatchers("/api/**").authenticated()
                        // Boshqa yo'llar (masalan mavjud bo'lmagan endpoint'lar va
                        // SecurityConfigTest probe path'lari) uchun avvalgi
                        // permitAll posture saqlanadi — auth challenge qaytarilmaydi
                        // va MVC dispatcher 404 qaytaradi.
                        .anyRequest().permitAll());

        // Phase 126: JwtDecoder bean mavjud bo'lganda oauth2ResourceServer'ni
        // explicit decoder + JwtActorConverter bilan wire qilamiz. Decoder
        // mavjud bo'lmagan profillarda (HMAC secret/issuer-uri/jwk-set-uri
        // property o'rnatilmagan) chain umuman qo'shilmaydi va Spring Security
        // default fallback (Http403ForbiddenEntryPoint) himoyalangan
        // endpoint'lar uchun 403 qaytaradi.
        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
        if (decoder != null) {
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                            .decoder(decoder)
                            .jwtAuthenticationConverter(new JwtActorConverter())));
        }

        return http.build();
    }
}
