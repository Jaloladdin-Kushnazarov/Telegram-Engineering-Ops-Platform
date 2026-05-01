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
 * Spring Security konfiguratsiyasi (Phase 124 + Phase 125 + Phase 126).
 *
 * <p>Phase 124'da skeleton: CSRF/formLogin/httpBasic disabled, STATELESS session,
 * hamma endpoint'lar {@code permitAll}.</p>
 *
 * <p>Phase 125'da JWT identity foundation: {@code AuthenticatedActor},
 * {@code @CurrentActor}, {@code CurrentActorArgumentResolver}, conditional
 * {@code JwtDecoder} bean ({@code app.security.jwt.hmac-secret} property
 * mavjud bo'lganda).</p>
 *
 * <p><strong>Phase 126:</strong> {@code oauth2ResourceServer} chain
 * <em>conditional ravishda</em> wire qilinadi — faqat {@link JwtDecoder} bean
 * mavjud bo'lsa. {@link ObjectProvider#getIfAvailable()} orqali decoder
 * tekshiriladi: yo'q bo'lsa, oauth2ResourceServer chaqirilmaydi va startup
 * mavjud sharoitlarda avvalgi singari ishlaydi (HMAC secret yo'q profillarda
 * JWT chain umuman qo'shilmaydi).</p>
 *
 * <p>Decoder mavjud bo'lganda:</p>
 * <ul>
 *   <li>{@code Authorization: Bearer ...} header'i bo'lgan so'rovlar uchun
 *       JWT decode qilinadi</li>
 *   <li>{@link JwtActorConverter} {@code AuthenticatedActor} principal'ini
 *       o'rnatadi</li>
 *   <li>Hech qanday Bearer berilmagan so'rovlar avvalgi xulqi bilan o'tadi
 *       ({@code permitAll})</li>
 *   <li>Yaroqsiz Bearer (imzo, expiry, format) — Spring Security default
 *       resource-server reject xulqi (401)</li>
 * </ul>
 *
 * <p>Endpoint enforcement Phase 126'da hali ham YO'Q — {@code anyRequest().permitAll()}
 * saqlanadi. JWT mavjud bo'lsa SecurityContext to'ladi, mavjud bo'lmasa request
 * davom etaveradi.</p>
 *
 * <p><strong>Test slice integratsiyasi:</strong> Production'da
 * {@code @SpringBootApplication} component-scan orqali yuklanadi.
 * {@code @WebMvcTest} slice'larida {@code @Import(SecurityConfig.class)} orqali
 * explicit yuklanadi — shu yondashuv mavjud controller test'larini
 * 403/401'siz saqlashda davom etadi.</p>
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
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());

        // Phase 126: JwtDecoder bean mavjud bo'lganda oauth2ResourceServer'ni
        // explicit decoder + JwtActorConverter bilan wire qilamiz. Decoder
        // mavjud bo'lmagan profillarda (HMAC secret property o'rnatilmagan)
        // chain umuman qo'shilmaydi va startup avvalgi sharoitda davom etadi.
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
