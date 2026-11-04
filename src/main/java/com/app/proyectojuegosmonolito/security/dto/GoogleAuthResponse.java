package com.app.proyectojuegosmonolito.security.dto;

import com.app.proyectojuegosmonolito.account.user.model.GoogleIdentity;

/**
 * Respuesta discriminada de {@code POST /auth/google}.
 *
 * <ul>
 *   <li>{@code AUTHENTICATED}: la cuenta ya existe y {@code token} es un access
 *       token normal.</li>
 *   <li>{@code ONBOARDING_REQUIRED}: la cuenta es nueva; {@code onboardingToken}
 *       es la credencial de un solo uso (10 min) para completar el perfil.</li>
 * </ul>
 */
public record GoogleAuthResponse(
        String status,
        String token,
        Long expiresIn,
        String onboardingToken,
        GoogleSuggestedProfile suggestedProfile
) {
    public static final String AUTHENTICATED = "AUTHENTICATED";
    public static final String ONBOARDING_REQUIRED = "ONBOARDING_REQUIRED";

    public static GoogleAuthResponse authenticated(String token, long expiresIn) {
        return new GoogleAuthResponse(AUTHENTICATED, token, expiresIn, null, null);
    }

    public static GoogleAuthResponse onboardingRequired(String onboardingToken, GoogleIdentity identity) {
        return new GoogleAuthResponse(
                ONBOARDING_REQUIRED,
                null,
                null,
                onboardingToken,
                new GoogleSuggestedProfile(
                        identity.email(),
                        identity.firstName(),
                        identity.lastName(),
                        identity.picture()
                )
        );
    }
}
