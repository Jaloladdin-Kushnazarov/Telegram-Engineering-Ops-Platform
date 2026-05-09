package com.engops.platform.infrastructure.security;

import com.engops.platform.infrastructure.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 148 mini-fix — {@link JsonAuthenticationEntryPoint} va
 * {@link JsonAccessDeniedHandler} uchun to'g'ridan-to'g'ri unit testlar.
 *
 * <p>Bu testlar Spring container va MockMvc'siz, faqat klassning o'zini va
 * Servlet API mock'larini ishlatib, envelope yozish kontraktini lock qiladi.
 * Maqsad: kelajakdagi refactor envelope shaklini buzsa, bu testlar
 * to'g'ridan-to'g'ri xatolik beradi (integration testlardan oldin).</p>
 *
 * <p>Tasdiqlanadi:</p>
 * <ul>
 *   <li>{@link JsonAccessDeniedHandler} — 403 status, {@code application/json}
 *       content-type, body'da {@code "errorCode":"ACCESS_DENIED"},
 *       MDC'dan o'qilgan correlationId va so'rov path'i.</li>
 *   <li>{@link JsonAuthenticationEntryPoint} — 401 status, WWW-Authenticate
 *       header'da {@code Bearer}, body'da {@code "errorCode":"UNAUTHORIZED"},
 *       MDC'dan o'qilgan correlationId va so'rov path'i.</li>
 *   <li>{@link JsonAuthenticationEntryPoint} — {@link OAuth2AuthenticationException}
 *       (invalid_token {@link BearerTokenError}) sharoitida WWW-Authenticate
 *       header'iga RFC 6750 diagnostic'i ({@code error="invalid_token"}) ham
 *       qo'shiladi (Approach 1 delegation kontraktini lock qiladi).</li>
 * </ul>
 */
class JsonSecurityHandlersUnitTest {

    private static final String TEST_PATH = "/api/secure/resource";
    private static final String CORR_ID = "test-corr-1234-abcd";

    private static ObjectMapper newPlatformLikeMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Test
    void accessDeniedHandlerWritesAccessDeniedEnvelopeWith403() throws Exception {
        ObjectMapper mapper = newPlatformLikeMapper();
        JsonAccessDeniedHandler handler = new JsonAccessDeniedHandler(mapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", TEST_PATH);
        request.setRequestURI(TEST_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, CORR_ID);
            handler.handle(request, response,
                    new AccessDeniedException("denied"));
        } finally {
            MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        }

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType())
                .as("Content-Type")
                .contains("application/json");

        String body = response.getContentAsString();
        assertThat(body)
                .as("body errorCode")
                .contains("\"errorCode\":\"ACCESS_DENIED\"");
        assertThat(body)
                .as("body correlationId")
                .contains("\"correlationId\":\"" + CORR_ID + "\"");
        assertThat(body)
                .as("body path")
                .contains("\"path\":\"" + TEST_PATH + "\"");
    }

    @Test
    void authenticationEntryPointWritesUnauthorizedEnvelopeWith401AndBearer() throws Exception {
        ObjectMapper mapper = newPlatformLikeMapper();
        JsonAuthenticationEntryPoint entryPoint = new JsonAuthenticationEntryPoint(mapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", TEST_PATH);
        request.setRequestURI(TEST_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, CORR_ID);
            entryPoint.commence(request, response,
                    new InsufficientAuthenticationException("missing token"));
        } finally {
            MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        }

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate"))
                .as("WWW-Authenticate header")
                .isNotNull()
                .contains("Bearer");
        assertThat(response.getContentType())
                .as("Content-Type")
                .contains("application/json");

        String body = response.getContentAsString();
        assertThat(body)
                .as("body errorCode")
                .contains("\"errorCode\":\"UNAUTHORIZED\"");
        assertThat(body)
                .as("body correlationId")
                .contains("\"correlationId\":\"" + CORR_ID + "\"");
        assertThat(body)
                .as("body path")
                .contains("\"path\":\"" + TEST_PATH + "\"");
    }

    @Test
    void authenticationEntryPointPreservesInvalidTokenDiagnosticFromOauth2Exception()
            throws Exception {
        // Phase 148 mini-fix Approach 1 lock: OAuth2AuthenticationException +
        // BearerTokenError sharoitida JsonAuthenticationEntryPoint delegate'i
        // (BearerTokenAuthenticationEntryPoint) RFC 6750 ga muvofiq
        // WWW-Authenticate header'iga error="invalid_token" diagnostic'ini
        // qo'shadi va biz uni saqlaymiz.
        ObjectMapper mapper = newPlatformLikeMapper();
        JsonAuthenticationEntryPoint entryPoint = new JsonAuthenticationEntryPoint(mapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", TEST_PATH);
        request.setRequestURI(TEST_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        BearerTokenError bearerError = new BearerTokenError(
                BearerTokenErrorCodes.INVALID_TOKEN,
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "An error occurred while attempting to decode the Jwt: Signed JWT rejected",
                "https://tools.ietf.org/html/rfc6750#section-3.1");
        AuthenticationException oauth2Exception = new OAuth2AuthenticationException(bearerError);

        entryPoint.commence(request, response, oauth2Exception);

        assertThat(response.getStatus()).isEqualTo(401);
        String wwwAuth = response.getHeader("WWW-Authenticate");
        assertThat(wwwAuth)
                .as("WWW-Authenticate header for invalid_token")
                .isNotNull()
                .contains("Bearer")
                .contains("error=\"invalid_token\"");

        String body = response.getContentAsString();
        assertThat(body)
                .as("body errorCode for OAuth2 invalid_token")
                .contains("\"errorCode\":\"UNAUTHORIZED\"");
        assertThat(body)
                .as("body path")
                .contains("\"path\":\"" + TEST_PATH + "\"");
    }
}
