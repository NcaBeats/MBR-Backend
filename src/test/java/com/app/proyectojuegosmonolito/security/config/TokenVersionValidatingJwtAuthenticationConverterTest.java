package com.app.proyectojuegosmonolito.security.config;

import com.app.proyectojuegosmonolito.TokenVersionCache;
import com.app.proyectojuegosmonolito.account.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenVersionValidatingJwtAuthenticationConverterTest {

    @Mock
    private UserService userService;

    private TokenVersionCache cache;
    private TokenVersionValidatingJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        cache = new TokenVersionCache(60_000);
        converter = new TokenVersionValidatingJwtAuthenticationConverter(
                jwt -> new JwtAuthenticationToken(jwt), cache, userService);
    }

    @Test
    void convert_withMatchingVersion_shouldReturnAuthentication() {
        cache.set(1L, 2);

        var result = converter.convert(token(1L, 2));

        assertThat(result).isInstanceOf(JwtAuthenticationToken.class);
        verify(userService, never()).getTokenVersion(1L);
    }

    @Test
    void convert_withRevokedToken_shouldThrow() {
        cache.set(1L, 3);

        assertThatThrownBy(() -> converter.convert(token(1L, 2)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Token revoked");
    }

    @Test
    void convert_onCacheMiss_shouldLoadFromDbAndCache() {
        when(userService.getTokenVersion(1L)).thenReturn(Optional.of(5));

        var result = converter.convert(token(1L, 5));

        assertThat(result).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(cache.get(1L)).hasValue(5);
    }

    @Test
    void convert_withMissingVer_shouldTreatAsZero() {
        cache.set(1L, 1);

        assertThatThrownBy(() -> converter.convert(tokenWithoutVer(1L)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void convert_withNonNumericSubject_shouldThrow() {
        assertThatThrownBy(() -> converter.convert(token("abc", 0)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid token subject");
    }

    @Test
    void convert_withOnboardingToken_shouldThrow() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("google:123")
                .claim("type", "onboarding")
                .build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Not an access token");
    }

    private Jwt token(Long subject, int ver) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject.toString())
                .claim("type", "access")
                .claim("ver", ver)
                .build();
    }

    private Jwt token(String subject, int ver) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .claim("type", "access")
                .claim("ver", ver)
                .build();
    }

    private Jwt tokenWithoutVer(Long subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject.toString())
                .claim("type", "access")
                .build();
    }
}
