package com.app.proyectojuegosmonolito.security.service;

import com.app.proyectojuegosmonolito.account.user.model.GoogleIdentity;
import com.app.proyectojuegosmonolito.account.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtEncoder jwtEncoder;

    @Value("${app.jwt.expiration}")
    private long expiration;

    public String generateAccessToken(User user) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("proyecto-juegos")
                .subject(user.getId().toString())
                .claim("type", "access")
                .claim("name", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("ver", user.getTokenVersion())
                .issuedAt(now)
                .expiresAt(now.plusMillis(expiration))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * Token de vida corta que lleva la identidad de Google ya verificada hasta el
     * formulario de onboarding. NUNCA sirve como access token: el claim {@code type}
     * es distinto y el {@code sub} no es un id numerico de usuario local.
     */
    public String generateOnboardingToken(GoogleIdentity identity, long ttlMillis) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("proyecto-juegos")
                .subject("google:" + identity.subject())
                .claim("type", "onboarding")
                .claim("email", identity.email())
                .claim("given_name", identity.firstName())
                .claim("family_name", identity.lastName())
                .claim("picture", identity.picture())
                .issuedAt(now)
                .expiresAt(now.plusMillis(ttlMillis))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
