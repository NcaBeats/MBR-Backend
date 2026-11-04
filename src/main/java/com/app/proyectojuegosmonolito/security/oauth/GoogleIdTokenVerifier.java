package com.app.proyectojuegosmonolito.security.oauth;

import com.app.proyectojuegosmonolito.account.user.model.GoogleIdentity;
import com.app.proyectojuegosmonolito.exception.GoogleSignInNotConfiguredException;
import com.app.proyectojuegosmonolito.exception.InvalidGoogleTokenException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Valida en local el ID token que emite Google Identity Services.
 *
 * <p>La firma se comprueba contra el JWKS publico de Google (cacheado por Nimbus),
 * y el decoder configurado en {@code SecurityConfig} ya valida {@code exp}/{@code nbf}
 * e {@code iss}. Aca se cierran los dos chequeos que dependen de nuestra
 * configuracion: {@code aud} (que el token sea para esta app) y
 * {@code email_verified} (que Google haya verificado el correo).
 *
 * <p>No hay ninguna llamada de red por request: {@code RemoteJWKSet} cachea las
 * claves y solo las refresca cuando aparece un {@code kid} desconocido.
 */
@Component
public class GoogleIdTokenVerifier {

    /** Google acepta el issuer con y sin esquema; los tokens nuevos usan el https. */
    public static final Set<String> VALID_ISSUERS =
            Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder decoder;
    private final String clientId;

    public GoogleIdTokenVerifier(
            @Qualifier("googleIdTokenDecoder") JwtDecoder decoder,
            @Value("${app.oauth.google.client-id}") String clientId) {
        this.decoder = decoder;
        this.clientId = clientId;
    }

    public GoogleIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidGoogleTokenException("Missing Google ID token");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new GoogleSignInNotConfiguredException();
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidGoogleTokenException("Invalid Google ID token", e);
        }

        if (!jwt.getAudience().contains(clientId)) {
            throw new InvalidGoogleTokenException("Google ID token audience does not match this application");
        }
        if (!isEmailVerified(jwt)) {
            throw new InvalidGoogleTokenException("Google account email is not verified");
        }

        var subject = jwt.getClaimAsString("sub");
        var email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new InvalidGoogleTokenException("Google ID token is missing sub or email");
        }

        return new GoogleIdentity(
                subject.trim(),
                email.trim().toLowerCase(Locale.ROOT),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                jwt.getClaimAsString("picture")
        );
    }

    private static boolean isEmailVerified(Jwt jwt) {
        Object value = jwt.getClaim("email_verified");
        if (value instanceof Boolean verified) {
            return verified;
        }
        // Algunos emisores lo mandan como string; Google como boolean.
        return value != null && "true".equalsIgnoreCase(value.toString());
    }
}
