package com.app.proyectojuegosmonolito.exception;

/**
 * El token de onboarding (el que se emite entre Google y el formulario de
 * completar perfil) no es valido o expiro. Se traduce a 401.
 */
public class InvalidOnboardingTokenException extends RuntimeException {

    public InvalidOnboardingTokenException(String message) {
        super(message);
    }

    public InvalidOnboardingTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
