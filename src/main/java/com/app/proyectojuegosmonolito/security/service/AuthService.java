package com.app.proyectojuegosmonolito.security.service;

import com.app.proyectojuegosmonolito.security.dto.AuthResponse;
import com.app.proyectojuegosmonolito.security.dto.GoogleAuthResponse;
import com.app.proyectojuegosmonolito.security.dto.GoogleCompleteRequest;
import com.app.proyectojuegosmonolito.security.dto.RegisterRequest;
import com.app.proyectojuegosmonolito.security.mapper.AuthMapper;
import com.app.proyectojuegosmonolito.account.user.model.GoogleIdentity;
import com.app.proyectojuegosmonolito.account.user.model.User;
import com.app.proyectojuegosmonolito.account.user.service.UserService;
import com.app.proyectojuegosmonolito.exception.EmailDomainNotAllowedException;
import com.app.proyectojuegosmonolito.exception.InvalidGoogleTokenException;
import com.app.proyectojuegosmonolito.security.oauth.GoogleIdTokenVerifier;
import com.app.proyectojuegosmonolito.validation.EmailDomainValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;
    private final AuthMapper authMapper;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final OnboardingTokenService onboardingTokenService;

    @Value("${app.jwt.expiration}")
    private long expiration;

    public AuthResponse login(String email, String password) {
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password)
        );
        var user = userService.findByEmail(email);
        var token = jwtService.generateAccessToken(user);
        return new AuthResponse(token, expiration);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        var user = authMapper.toEntity(request);
        var profile = authMapper.toProfile(request, user);
        var saved = userService.create(user, profile);
        var token = jwtService.generateAccessToken(saved);
        return new AuthResponse(token, expiration);
    }

    /**
     * Valida el ID token de Google y resuelve la cuenta:
     * <ul>
     *   <li>Si ya existe (por {@code sub} o por email verificado) devuelve el JWT
     *       normal y, si hacia falta, vincula la identidad.</li>
     *   <li>Si es nueva devuelve un token de onboarding: no se crea nada en la BD
     *       hasta que el usuario complete RUN/region/comuna.</li>
     * </ul>
     */
    public GoogleAuthResponse loginWithGoogle(String idToken) {
        var identity = googleIdTokenVerifier.verify(idToken);

        var existing = findExistingUser(identity);
        if (existing.isPresent()) {
            var user = existing.get();
            userService.ensureGoogleIdentity(user, identity);
            userService.updateOAuthAvatar(user.getId(), identity.picture());
            return GoogleAuthResponse.authenticated(jwtService.generateAccessToken(user), expiration);
        }

        if (!EmailDomainValidator.isAllowed(identity.email())) {
            throw new EmailDomainNotAllowedException(identity.email());
        }
        rejectSoftDeletedAccount(identity);

        return GoogleAuthResponse.onboardingRequired(onboardingTokenService.issue(identity), identity);
    }

    /**
     * Crea la cuenta con los datos del onboarding. Si el usuario ya fue creado
     * (reintento del frontend o una carrera con otro login), devuelve el token de
     * la cuenta existente en vez de duplicarla.
     */
    @Transactional
    public AuthResponse completeGoogleSignup(GoogleCompleteRequest request) {
        var identity = onboardingTokenService.parse(request.onboardingToken());

        var existing = findExistingUser(identity);
        if (existing.isPresent()) {
            var user = existing.get();
            userService.ensureGoogleIdentity(user, identity);
            userService.updateOAuthAvatar(user.getId(), identity.picture());
            return new AuthResponse(jwtService.generateAccessToken(user), expiration);
        }

        if (!EmailDomainValidator.isAllowed(identity.email())) {
            throw new EmailDomainNotAllowedException(identity.email());
        }
        rejectSoftDeletedAccount(identity);

        var saved = userService.createFromGoogle(authMapper.toGoogleSignup(identity, request));
        return new AuthResponse(jwtService.generateAccessToken(saved), expiration);
    }

    @Transactional
    public void logout(Long userId) {
        userService.revokeAllTokens(userId);
    }

    private Optional<User> findExistingUser(GoogleIdentity identity) {
        return userService.findByGoogleSubject(identity.subject())
                .or(() -> userService.findActiveOptionalByEmail(identity.email()));
    }

    /**
     * El soft delete conserva el email (sigue siendo UNIQUE), asi que una cuenta
     * borrada no puede volver a registrarse con Google: sin este guardia el alta
     * fallaria recien al insertar, con un 409 poco claro.
     */
    private void rejectSoftDeletedAccount(GoogleIdentity identity) {
        if (userService.findOptionalByEmail(identity.email()).isPresent()) {
            throw new InvalidGoogleTokenException("Account is disabled");
        }
    }
}
