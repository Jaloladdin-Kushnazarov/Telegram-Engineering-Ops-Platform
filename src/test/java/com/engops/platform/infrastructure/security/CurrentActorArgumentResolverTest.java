package com.engops.platform.infrastructure.security;

import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentActorArgumentResolverTest {

    private final CurrentActorArgumentResolver resolver = new CurrentActorArgumentResolver();

    // Hujjatlangan parametr namunalari turli supportsParameter / resolveArgument
    // case'lari uchun
    void uuidWithAnnotation(@CurrentActor UUID actorId) {}
    void actorWithAnnotation(@CurrentActor AuthenticatedActor actor) {}
    void uuidWithoutAnnotation(UUID id) {}
    void stringWithAnnotation(@CurrentActor String something) {}

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportsUuidParameterWithCurrentActorAnnotation() {
        MethodParameter param = parameterOf("uuidWithAnnotation");
        assertThat(resolver.supportsParameter(param)).isTrue();
    }

    @Test
    void supportsAuthenticatedActorParameterWithCurrentActorAnnotation() {
        MethodParameter param = parameterOf("actorWithAnnotation");
        assertThat(resolver.supportsParameter(param)).isTrue();
    }

    @Test
    void rejectsParameterWithoutCurrentActorAnnotation() {
        MethodParameter param = parameterOf("uuidWithoutAnnotation");
        assertThat(resolver.supportsParameter(param)).isFalse();
    }

    @Test
    void rejectsUnsupportedParameterTypeEvenWithAnnotation() {
        MethodParameter param = parameterOf("stringWithAnnotation");
        assertThat(resolver.supportsParameter(param)).isFalse();
    }

    @Test
    void resolvesUuidFromAuthenticatedActorPrincipal() throws Exception {
        UUID actorId = UUID.randomUUID();
        AuthenticatedActor actor = new AuthenticatedActor(actorId, 42L);
        setAuthentication(actor);

        Object resolved = resolver.resolveArgument(
                parameterOf("uuidWithAnnotation"), null, null, null);

        assertThat(resolved).isEqualTo(actorId);
    }

    @Test
    void resolvesAuthenticatedActorFromPrincipal() throws Exception {
        UUID actorId = UUID.randomUUID();
        AuthenticatedActor actor = new AuthenticatedActor(actorId, 99L);
        setAuthentication(actor);

        Object resolved = resolver.resolveArgument(
                parameterOf("actorWithAnnotation"), null, null, null);

        assertThat(resolved).isInstanceOf(AuthenticatedActor.class);
        assertThat(((AuthenticatedActor) resolved).appUserId()).isEqualTo(actorId);
        assertThat(((AuthenticatedActor) resolved).telegramUserId()).isEqualTo(99L);
    }

    @Test
    void rejectsMissingAuthentication() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> resolver.resolveArgument(
                parameterOf("uuidWithAnnotation"), null, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Autentifikatsiyalangan actor");
    }

    @Test
    void rejectsAnonymousAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymous",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThatThrownBy(() -> resolver.resolveArgument(
                parameterOf("uuidWithAnnotation"), null, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsPrincipalThatIsNotAuthenticatedActor() {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "some-string-principal", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(token);

        assertThatThrownBy(() -> resolver.resolveArgument(
                parameterOf("uuidWithAnnotation"), null, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("AuthenticatedActor");
    }

    private MethodParameter parameterOf(String methodName) {
        try {
            Method method = null;
            for (Method m : CurrentActorArgumentResolverTest.class.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 1) {
                    method = m;
                    break;
                }
            }
            if (method == null) {
                throw new IllegalStateException("Test metod topilmadi: " + methodName);
            }
            return new MethodParameter(method, 0);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setAuthentication(AuthenticatedActor actor) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                actor, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
