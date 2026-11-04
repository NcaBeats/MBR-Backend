package com.app.proyectojuegosmonolito.security.service;

import com.app.proyectojuegosmonolito.account.user.model.GoogleIdentity;
import com.app.proyectojuegosmonolito.exception.InvalidOnboardingTokenException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * Emite y valida el token intermedio entre el login con Google y el formulario
 * que completa RUN/region/comuna. Es un JWT HS256 nuestro (mismo secreto que los
 * access tokens) con {@code type=onboarding} y 10 minutos de vida.
 */
@Service
public class OnboardingTokenService {

    static final String TYPE = "onboarding";
    private static final String SUBJECT_PREFIX = "google:";

    private final JwtService jwtService;
    private final JwtDecoder jwtDecoder;
    private final long ttlMillis;

    public OnboardingTokenService(
            JwtService jwtService,
            @Qualifier("jwtDecoder") JwtDecoder jwtDecoder,
            @Value("${app.oauth.onboarding.ttl-ms:600000}") long ttlMillis) {
        this.jwtService = jwtService;
        this.jwtDecoder = jwtDecoder;
        this.ttlMillis = ttlMillis;
    }

    public String issue(GoogleIdentity identity) {
        return jwtService.generateOnboardingToken(identity, ttlMillis);
    }

    public GoogleIdentity parse(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidOnboardingTokenException("Missing onboarding token");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidOnboardingTokenException("Invalid onboarding token", e);
        }

        if (!TYPE.equals(jwt.getClaimAsString("type"))) {
            throw new InvalidOnboardingTokenException("Token is not an onboarding token");
        }

        var subject = jwt.getSubject();
        if (subject == null || !subject.startsWith(SUBJECT_PREFIX) || subject.length() == SUBJECT_PREFIX.length()) {
            throw new InvalidOnboardingTokenException("Onboarding token has no Google subject");
        }

        var email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new InvalidOnboardingTokenException("Onboarding token has no email");
        }

        return new GoogleIdentity(
                subject.substring(SUBJECT_PREFIX.length()),
                email,
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                jwt.getClaimAsString("picture")
        );
    }
}
