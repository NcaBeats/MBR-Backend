package com.app.proyectojuegosmonolito.exception;

/**
 * El ID token de Google no supero la validacion (firma, issuer, audiencia,
 * expiracion o email no verificado). Se traduce a 401.
 */
public class InvalidGoogleTokenException extends RuntimeException {

    public InvalidGoogleTokenException(String message) {
        super(message);
    }

    public InvalidGoogleTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
