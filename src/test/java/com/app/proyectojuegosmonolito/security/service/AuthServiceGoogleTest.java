package com.app.proyectojuegosmonolito.security.service;

import com.app.proyectojuegosmonolito.account.user.model.GoogleIdentity;
import com.app.proyectojuegosmonolito.account.user.model.GoogleSignup;
import com.app.proyectojuegosmonolito.account.user.model.Role;
import com.app.proyectojuegosmonolito.account.user.model.User;
import com.app.proyectojuegosmonolito.account.user.service.UserService;
import com.app.proyectojuegosmonolito.exception.EmailDomainNotAllowedException;
import com.app.proyectojuegosmonolito.security.dto.GoogleAuthResponse;
import com.app.proyectojuegosmonolito.security.dto.GoogleCompleteRequest;
import com.app.proyectojuegosmonolito.security.mapper.AuthMapper;
import com.app.proyectojuegosmonolito.security.oauth.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleTest {

    private static final GoogleIdentity IDENTITY =
            new GoogleIdentity("sub-1", "nico@gmail.com", "Nico", "Beats", "https://example.com/a.png");

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserService userService;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private OnboardingTokenService onboardingTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginWithGoogle_whenNewAccountAndAllowedDomain_shouldReturnOnboarding() {
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(IDENTITY);
        when(userService.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());
        when(userService.findActiveOptionalByEmail("nico@gmail.com")).thenReturn(Optional.empty());
        when(onboardingTokenService.issue(IDENTITY)).thenReturn("onboarding-token");

        var result = authService.loginWithGoogle("id-token");

        assertThat(result.status()).isEqualTo(GoogleAuthResponse.ONBOARDING_REQUIRED);
        assertThat(result.onboardingToken()).isEqualTo("onboarding-token");
        assertThat(result.token()).isNull();
        assertThat(result.suggestedProfile().email()).isEqualTo("nico@gmail.com");
        verify(userService, never()).createFromGoogle(any());
    }

    @Test
    void loginWithGoogle_whenNewAccountAndDomainNotAllowed_shouldThrow() {
        var identity = new GoogleIdentity("sub-2", "nico@outlook.com", "Nico", "Beats", null);
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(identity);
        when(userService.findByGoogleSubject("sub-2")).thenReturn(Optional.empty());
        when(userService.findActiveOptionalByEmail("nico@outlook.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginWithGoogle("id-token"))
                .isInstanceOf(EmailDomainNotAllowedException.class);
    }

    @Test
    void loginWithGoogle_whenIdentityAlreadyLinked_shouldReturnAccessToken() {
        var user = user(7L);
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(IDENTITY);
        when(userService.findByGoogleSubject("sub-1")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("jwt");

        var result = authService.loginWithGoogle("id-token");

        assertThat(result.status()).isEqualTo(GoogleAuthResponse.AUTHENTICATED);
        assertThat(result.token()).isEqualTo("jwt");
        verify(userService).ensureGoogleIdentity(user, IDENTITY);
        verify(userService).updateOAuthAvatar(7L, IDENTITY.picture());
    }

    @Test
    void loginWithGoogle_whenEmailMatchesExistingAccount_shouldLinkAndAuthenticate() {
        var user = user(8L);
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(IDENTITY);
        when(userService.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());
        when(userService.findActiveOptionalByEmail("nico@gmail.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("jwt");

        var result = authService.loginWithGoogle("id-token");

        assertThat(result.token()).isEqualTo("jwt");
        verify(userService).ensureGoogleIdentity(user, IDENTITY);
        verify(userService).updateOAuthAvatar(8L, IDENTITY.picture());
    }

    @Test
    void completeGoogleSignup_shouldCreateAccount() {
        var request = new GoogleCompleteRequest("onboarding-token", "190110222", "nicobeats",
                null, 1L, "Av. Siempre Viva 742");
        var signup = new GoogleSignup(IDENTITY, "190110222", "nicobeats", null, 1L, "Av. Siempre Viva 742");
        var saved = user(9L);

        when(onboardingTokenService.parse("onboarding-token")).thenReturn(IDENTITY);
        when(userService.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());
        when(userService.findActiveOptionalByEmail("nico@gmail.com")).thenReturn(Optional.empty());
        when(authMapper.toGoogleSignup(IDENTITY, request)).thenReturn(signup);
        when(userService.createFromGoogle(signup)).thenReturn(saved);
        when(jwtService.generateAccessToken(saved)).thenReturn("jwt");

        var result = authService.completeGoogleSignup(request);

        assertThat(result.token()).isEqualTo("jwt");
        verify(userService).createFromGoogle(signup);
    }

    @Test
    void completeGoogleSignup_whenAccountAlreadyCreated_shouldNotDuplicate() {
        var request = new GoogleCompleteRequest("onboarding-token", "190110222", "nicobeats",
                null, 1L, "Av. Siempre Viva 742");
        var existing = user(10L);

        when(onboardingTokenService.parse("onboarding-token")).thenReturn(IDENTITY);
        when(userService.findByGoogleSubject("sub-1")).thenReturn(Optional.of(existing));
        when(jwtService.generateAccessToken(existing)).thenReturn("jwt");

        var result = authService.completeGoogleSignup(request);

        assertThat(result.token()).isEqualTo("jwt");
        verify(userService, never()).createFromGoogle(any());
    }

    private static User user(Long id) {
        return User.builder()
                .id(id)
                .email("nico@gmail.com")
                .role(Role.CLIENTE)
                .tokenVersion(0)
                .build();
    }
}
