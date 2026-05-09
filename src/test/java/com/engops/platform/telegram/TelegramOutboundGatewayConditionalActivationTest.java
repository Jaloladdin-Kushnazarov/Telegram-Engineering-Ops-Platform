package com.engops.platform.telegram;

import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Phase 158 mini-fix — {@link TelegramOutboundGateway} bean'ining shartli
 * aktivatsiya kontraktini lock qiluvchi context testi.
 *
 * <p>Tasdiqlanadi:</p>
 * <ul>
 *   <li>{@code app.telegram.bot-token} <strong>o'rnatilmagan</strong> →
 *       yagona bean {@link StubTelegramOutboundGateway} bo'ladi va
 *       {@link HttpTelegramOutboundGateway} yuklanmaydi.</li>
 *   <li>{@code app.telegram.bot-token} <strong>bo'sh/whitespace</strong>
 *       (masalan {@code "   "}) → yagona bean {@link StubTelegramOutboundGateway}
 *       bo'ladi (Phase 125/137 JWT decoder pattern bilan bir xil — bo'sh
 *       string real bean'ni AKTIVLASHTIRMAYDI).</li>
 *   <li>{@code app.telegram.bot-token} <strong>non-blank</strong> →
 *       {@link HttpTelegramOutboundGateway} yagona bean bo'ladi va
 *       {@link StubTelegramOutboundGateway} yuklanmaydi (mutually-exclusive
 *       conditional ikki tomonlama ishlaydi).</li>
 * </ul>
 *
 * <p>{@link ApplicationContextRunner} orqali Spring context lightweight
 * bo'lib ko'tariladi — {@code @SpringBootTest} kerak emas, network/auth/DB
 * ishtirok etmaydi.</p>
 */
class TelegramOutboundGatewayConditionalActivationTest {

    private final TenantConfigQueryService mockedTenantConfigQueryService =
            mock(TenantConfigQueryService.class);

    /**
     * Kontekst runner: TelegramOutboundGatewayConfiguration (real gateway) va
     * StubTelegramOutboundGateway (fallback) ikkalasini ham candidate sifatida
     * ro'yxatga oladi. Conditional'lar property mavjudligiga qarab qaysi bean
     * yuklanishini hal qiladi. Kerakli collaborator'lar mocked/stubbed.
     */
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        JacksonAutoConfiguration.class,
                        TestSupportConfig.class,
                        TelegramOutboundGatewayConfiguration.class,
                        StubTelegramOutboundGateway.class)
                .withBean(TenantConfigQueryService.class, () -> mockedTenantConfigQueryService);
    }

    @Configuration
    static class TestSupportConfig {
        /**
         * Defensive ObjectMapper bean — agar Jackson autoconfig context'ga
         * include qilinmasa ham, gateway konfiguratsiyasi `ObjectMapper`'ni
         * topadi.
         */
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    void tokenMissing_loadsOnlyStubGateway() {
        runner().run(context -> {
            assertThat(context).hasSingleBean(TelegramOutboundGateway.class);
            assertThat(context.getBean(TelegramOutboundGateway.class))
                    .isInstanceOf(StubTelegramOutboundGateway.class);
            assertThat(context).doesNotHaveBean(HttpTelegramOutboundGateway.class);
            assertThat(context).doesNotHaveBean(TelegramProperties.class);
        });
    }

    @Test
    void tokenBlank_loadsOnlyStubGateway() {
        runner()
                .withPropertyValues("app.telegram.bot-token=   ")
                .run(context -> {
                    assertThat(context).hasSingleBean(TelegramOutboundGateway.class);
                    assertThat(context.getBean(TelegramOutboundGateway.class))
                            .isInstanceOf(StubTelegramOutboundGateway.class);
                    assertThat(context).doesNotHaveBean(HttpTelegramOutboundGateway.class);
                    assertThat(context).doesNotHaveBean(TelegramProperties.class);
                });
    }

    @Test
    void tokenEmptyString_loadsOnlyStubGateway() {
        // Defensiv qoplama: aniq bo'sh string ham real gateway'ni
        // aktivlashtirmasligini tasdiqlaydi.
        runner()
                .withPropertyValues("app.telegram.bot-token=")
                .run(context -> {
                    assertThat(context).hasSingleBean(TelegramOutboundGateway.class);
                    assertThat(context.getBean(TelegramOutboundGateway.class))
                            .isInstanceOf(StubTelegramOutboundGateway.class);
                    assertThat(context).doesNotHaveBean(HttpTelegramOutboundGateway.class);
                });
    }

    @Test
    void tokenNonBlank_loadsHttpGatewayAndExcludesStub() {
        runner()
                .withPropertyValues("app.telegram.bot-token=1234567890:TEST_BOT_TOKEN_NOT_REAL")
                .run(context -> {
                    assertThat(context).hasSingleBean(TelegramOutboundGateway.class);
                    assertThat(context.getBean(TelegramOutboundGateway.class))
                            .isInstanceOf(HttpTelegramOutboundGateway.class);
                    assertThat(context).doesNotHaveBean(StubTelegramOutboundGateway.class);
                    assertThat(context).hasSingleBean(TelegramProperties.class);
                });
    }
}
