package com.engops.platform.telegram;

import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Phase 158 — real Telegram Bot API outbound gateway uchun shartli
 * konfiguratsiya. Faqat {@code app.telegram.bot-token} non-blank bo'lganda
 * yuklanadi (Phase 125/137 JWT decoder pattern'i bilan bir xil
 * {@code @ConditionalOnExpression}).
 *
 * <p>Bo'sh yoki yo'q token sharoitida bu konfiguratsiya yuklanmaydi va
 * {@link StubTelegramOutboundGateway} (o'zining mutually-exclusive
 * conditional'i bilan) yagona {@link TelegramOutboundGateway} bean'i
 * sifatida qoladi.</p>
 *
 * <p>{@link TelegramProperties} faqat shu real-mode'da ro'yxatga olinadi —
 * stub fallback'da hech kim TelegramProperties'ni o'qimaydi.</p>
 */
@Configuration
@EnableConfigurationProperties(TelegramProperties.class)
@ConditionalOnExpression("'${app.telegram.bot-token:}'.trim().length() > 0")
public class TelegramOutboundGatewayConfiguration {

    /**
     * RestClient — connect/read timeout {@link TelegramProperties}'dan
     * o'rnatiladi. Default factory ({@link SimpleClientHttpRequestFactory})
     * spring-web ichida; yangi dependency talab qilmaydi.
     */
    @Bean
    public RestClient telegramRestClient(TelegramProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Bean
    public HttpTelegramOutboundGateway httpTelegramOutboundGateway(
            TelegramProperties properties,
            RestClient telegramRestClient,
            TenantConfigQueryService tenantConfigQueryService,
            ObjectMapper objectMapper) {
        return new HttpTelegramOutboundGateway(properties, telegramRestClient,
                tenantConfigQueryService, objectMapper);
    }
}
