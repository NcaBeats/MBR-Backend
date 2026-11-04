package com.app.proyectojuegosmonolito.security.config;

import com.app.proyectojuegosmonolito.TokenVersionCache;
import com.app.proyectojuegosmonolito.account.user.service.UserService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

public class TokenVersionValidatingJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final Converter<Jwt, AbstractAuthenticationToken> delegate;
    private final TokenVersionCache tokenVersionCache;
    private final UserService userService;

    public TokenVersionValidatingJwtAuthenticationConverter(
            Converter<Jwt, AbstractAuthenticationToken> delegate,
            TokenVersionCache tokenVersionCache,
            UserService userService) {
        this.delegate = delegate;
        this.tokenVersionCache = tokenVersionCache;
        this.userService = userService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Solo los tokens de acceso pueden autenticar requests. Sin este chequeo,
        // el token de onboarding (type=onboarding) seria aceptado por el resource
        // server y dependeria de que su subject no sea numerico para fallar.
        if (!"access".equals(jwt.getClaimAsString("type"))) {
            throw new BadCredentialsException("Not an access token");
        }
        Long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException e) {
            throw new BadCredentialsException("Invalid token subject");
        }
        int tokenVersion = jwt.hasClaim("ver") ? ((Number) jwt.getClaim("ver")).intValue() : 0;
        int currentVersion = tokenVersionCache.get(userId)
                .orElseGet(() -> {
                    var version = userService.getTokenVersion(userId).orElse(0);
                    tokenVersionCache.set(userId, version);
                    return version;
                });
        if (currentVersion > tokenVersion) {
            throw new BadCredentialsException("Token revoked");
        }
        return delegate.convert(jwt);
    }
}
