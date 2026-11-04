package com.app.proyectojuegosmonolito.exception;

/**
 * Se intento usar el login con Google pero {@code app.oauth.google.client-id}
 * esta vacio (tipicamente un entorno local sin configurar). Se traduce a 503:
 * la app funciona, solo ese endpoint no esta disponible.
 */
public class GoogleSignInNotConfiguredException extends RuntimeException {

    public GoogleSignInNotConfiguredException() {
        super("Google sign-in is not configured (GOOGLE_CLIENT_ID)");
    }
}
