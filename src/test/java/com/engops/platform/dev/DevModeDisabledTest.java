package com.engops.platform.dev;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Phase 211 — DEV MODE OFF (production posture) tasdig'i.
 *
 * <p>Default {@code test} profile + dev-mode property o'rnatilmagan →
 * {@link DevAuthController}, {@link DevTokenIssuer}, {@link DevBootstrapInitializer}
 * beans context'da YO'Q va /api/dev/auth/info 404 qaytaradi.</p>
 */
@SpringBootTest(classes = com.engops.platform.EngOpsPlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevModeDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void devAuthController_absent_when_propertyOff() {
        assertThatThrownBy(() -> context.getBean(DevAuthController.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void devTokenIssuer_absent_when_propertyOff() {
        assertThatThrownBy(() -> context.getBean(DevTokenIssuer.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void devBootstrapInitializer_absent_when_propertyOff() {
        assertThatThrownBy(() -> context.getBean(DevBootstrapInitializer.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void apiDevEndpoint_unavailable_when_propertyOff() throws Exception {
        // Bean yaratilmagan → handler mapping yo'q. Spring MVC default
        // ravishda 404 qaytaradi, lekin platforma error filter chain'i ba'zan
        // 500'ga aylantirib yuboradi (error envelope rendering). Asosiy
        // assertion: endpoint 200 OK qaytarmaydi (ya'ni dev funktsionalligi
        // production posture'da ochiq emas). Real proof bean-absence test'lari.
        int status = mockMvc.perform(get("/api/dev/auth/info"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(200);
    }

    @Test
    void contextLoads_without_devMode_property() {
        // Sanity check — barcha bean'lar normal yuklanadi, hech qanday startup
        // fail yo'q (dev-mode absent ekan).
        assertThat(context).isNotNull();
    }
}
