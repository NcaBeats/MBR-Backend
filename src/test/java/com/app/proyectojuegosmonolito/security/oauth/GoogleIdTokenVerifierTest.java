package com.app.proyectojuegosmonolito.security.oauth;

import com.app.proyectojuegosmonolito.exception.GoogleSignInNotConfiguredException;
import com.app.proyectojuegosmonolito.exception.InvalidGoogleTokenException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cubre la validacion sin red: se genera un par RSA local y el decoder usa la
 * clave publica, con el mismo validador de issuer que arma SecurityConfig.
 */
class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "test-client-id.apps.googleusercontent.com";
    private static final String VALID_ISSUER = "https://accounts.google.com";

    private static KeyPair keyPair;

    private JwtEncoder encoder;
    private NimbusJwtDecoder decoder;
    private GoogleIdTokenVerifier verifier;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        encoder = NimbusJwtEncoder
                .withKeyPair((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate())
                .build();
        decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtClaimValidator<String>("iss", GoogleIdTokenVerifier.VALID_ISSUERS::contains)));
        verifier = new GoogleIdTokenVerifier(decoder, CLIENT_ID);
    }

    @Test
    void verify_withoutClientId_shouldThrowNotConfigured() {
        var notConfigured = new GoogleIdTokenVerifier(decoder, "");

        assertThatThrownBy(() -> notConfigured.verify(token(VALID_ISSUER, CLIENT_ID, true, "nico@gmail.com", "sub")))
                .isInstanceOf(GoogleSignInNotConfiguredException.class)
                .hasMessageContaining("GOOGLE_CLIENT_ID");
    }

    @Test
    void verify_withValidToken_shouldReturnIdentity() {
        var result = verifier.verify(token(VALID_ISSUER, CLIENT_ID, true, "nico@gmail.com", "google-sub-1"));

        assertThat(result.subject()).isEqualTo("google-sub-1");
        assertThat(result.email()).isEqualTo("nico@gmail.com");
        assertThat(result.firstName()).isEqualTo("Nico");
        assertThat(result.lastName()).isEqualTo("Beats");
        assertThat(result.picture()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    void verify_withLegacyIssuer_shouldAccept() {
        var result = verifier.verify(token("accounts.google.com", CLIENT_ID, true, "nico@gmail.com", "sub"));

        assertThat(result.subject()).isEqualTo("sub");
    }

    @Test
    void verify_withUppercaseEmail_shouldNormalize() {
        var result = verifier.verify(token(VALID_ISSUER, CLIENT_ID, true, "Nico@Gmail.com", "sub"));

        assertThat(result.email()).isEqualTo("nico@gmail.com");
    }

    @Test
    void verify_withWrongAudience_shouldThrow() {
        var idToken = token(VALID_ISSUER, "another-client.apps.googleusercontent.com", true, "nico@gmail.com", "sub");

        assertThatThrownBy(() -> verifier.verify(idToken))
                .isInstanceOf(InvalidGoogleTokenException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void verify_withWrongIssuer_shouldThrow() {
        var idToken = token("https://evil.example.com", CLIENT_ID, true, "nico@gmail.com", "sub");

        assertThatThrownBy(() -> verifier.verify(idToken))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void verify_withExpiredToken_shouldThrow() {
        var idToken = token(VALID_ISSUER, CLIENT_ID, true, "nico@gmail.com", "sub",
                Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

        assertThatThrownBy(() -> verifier.verify(idToken))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void verify_withUnverifiedEmail_shouldThrow() {
        var idToken = token(VALID_ISSUER, CLIENT_ID, false, "nico@gmail.com", "sub");

        assertThatThrownBy(() -> verifier.verify(idToken))
                .isInstanceOf(InvalidGoogleTokenException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    void verify_withoutEmail_shouldThrow() {
        var idToken = token(VALID_ISSUER, CLIENT_ID, true, "", "sub");

        assertThatThrownBy(() -> verifier.verify(idToken))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void verify_withBlankToken_shouldThrow() {
        assertThatThrownBy(() -> verifier.verify("  "))
                .isInstanceOf(InvalidGoogleTokenException.class)
                .hasMessageContaining("Missing");
    }

    private String token(String issuer, String audience, boolean emailVerified, String email, String subject) {
        return token(issuer, audience, emailVerified, email, subject,
                Instant.now(), Instant.now().plusSeconds(3600));
    }

    private String token(String issuer, String audience, boolean emailVerified, String email, String subject,
                         Instant issuedAt, Instant expiresAt) {
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .audience(List.of(audience))
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("given_name", "Nico")
                .claim("family_name", "Beats")
                .claim("picture", "https://example.com/avatar.png")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
