package com.engops.platform.web;

import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 207 — {@link WebController} @WebMvcTest testlari.
 *
 * Stack health proof: Thymeleaf template resolution + SecurityConfig
 * /web/** permitAll branch.
 */
@WebMvcTest(WebController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_returnsOk_andRendersHealthTemplate() throws Exception {
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void health_renderedHtml_containsStatusOK() throws Exception {
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Status:")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OK")));
    }

    @Test
    void health_renderedHtml_containsPhase207() throws Exception {
        // Thymeleaf renders "Phase <span>207</span>" — the literal substring
        // "Phase 207" does NOT appear because of the inline element. Assert
        // the two pieces separately.
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Phase ")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">207<")));
    }

    @Test
    void health_endpointReachable_withoutJwt_dueToPermitAll() throws Exception {
        // No withActor() / no Authorization header — anonymous request must succeed.
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk());
    }

    @Test
    void health_renderedHtml_containsUzbekConfirmationSentence() throws Exception {
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Web rendering stack ishlamoqda")));
    }
}
