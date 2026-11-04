package com.app.proyectojuegosmonolito.security.controller;

import com.app.proyectojuegosmonolito.TestcontainersConfiguration;
import com.app.proyectojuegosmonolito.account.profile.repository.ProfileRepository;
import com.app.proyectojuegosmonolito.account.user.model.GoogleIdentity;
import com.app.proyectojuegosmonolito.account.user.repository.OAuthIdentityRepository;
import com.app.proyectojuegosmonolito.account.user.repository.UserRepository;
import com.app.proyectojuegosmonolito.account.user.service.UserService;
import com.app.proyectojuegosmonolito.exception.InvalidGoogleTokenException;
import com.app.proyectojuegosmonolito.security.dto.GoogleAuthRequest;
import com.app.proyectojuegosmonolito.security.dto.GoogleCompleteRequest;
import com.app.proyectojuegosmonolito.security.oauth.GoogleIdTokenVerifier;
import com.app.proyectojuegosmonolito.security.service.OnboardingTokenService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.app.proyectojuegosmonolito.account.user.UserFixtures.profile;
import static com.app.proyectojuegosmonolito.account.user.UserFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class GoogleAuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private OAuthIdentityRepository oauthIdentityRepository;

    @Autowired
    private OnboardingTokenService onboardingTokenService;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Test
    void google_withNewAccount_shouldReturnOnboardingWithoutCreatingUser() throws Exception {
        var identity = identity("google-sub-new", "nuevo@gmail.com");
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(identity);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleAuthRequest("id-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONBOARDING_REQUIRED"))
                .andExpect(jsonPath("$.onboardingToken").isNotEmpty())
                .andExpect(jsonPath("$.token").value(nullValue()))
                .andExpect(jsonPath("$.suggestedProfile.email").value("nuevo@gmail.com"));

        assertThat(userRepository.findByEmail("nuevo@gmail.com")).isEmpty();
    }

    @Test
    void google_withExistingAccount_shouldReturnWorkingAccessToken() throws Exception {
        var user = userService.create(user("existente@gmail.com"), profile(user("existente@gmail.com")));
        var identity = identity("google-sub-existing", "existente@gmail.com");
        userService.ensureGoogleIdentity(user, identity);
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(identity);

        var result = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleAuthRequest("id-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        var token = body.get("token").asString();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("existente@gmail.com"));
    }

    @Test
    void google_complete_shouldCreateUserProfileAndIdentity() throws Exception {
        var identity = identity("google-sub-complete", "completo@gmail.com");
        var onboardingToken = onboardingTokenService.issue(identity);
        var request = new GoogleCompleteRequest(onboardingToken, "190110222", "nuevo_nick",
                null, 1L, "Av. Siempre Viva 742");

        mockMvc.perform(post("/api/v1/auth/google/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").isNumber());

        var saved = userRepository.findByEmail("completo@gmail.com").orElseThrow();
        assertThat(saved.getPassword()).isNull();
        assertThat(profileRepository.findById(saved.getId())).isPresent();
        assertThat(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-complete"))
                .isPresent();
    }

    @Test
    void google_complete_withInvalidToken_shouldReturn401() throws Exception {
        var request = new GoogleCompleteRequest("not-a-token", "190110222", "nuevo_nick",
                null, 1L, "Av. Siempre Viva 742");

        mockMvc.perform(post("/api/v1/auth/google/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void google_complete_withDuplicateRun_shouldReturn409() throws Exception {
        var identity = identity("google-sub-dup", "duplicado@gmail.com");
        var firstToken = onboardingTokenService.issue(identity);
        var first = new GoogleCompleteRequest(firstToken, "190110222", "nick_uno",
                null, 1L, "Calle 1");

        mockMvc.perform(post("/api/v1/auth/google/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        // La segunda cuenta usa el mismo RUN, que es UNIQUE en profile.
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(identity("google-sub-dup-2", "duplicado2@gmail.com"));
        var secondToken = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleAuthRequest("id-token"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var token = objectMapper.readTree(secondToken).get("onboardingToken").asString();
        var second = new GoogleCompleteRequest(token, "190110222", "nick_dos",
                null, 1L, "Calle 2");

        mockMvc.perform(post("/api/v1/auth/google/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict());
    }

    @Test
    void google_withDisallowedDomain_shouldReturn403() throws Exception {
        var identity = identity("google-sub-other", "alguien@outlook.com");
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(identity);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleAuthRequest("id-token"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_DOMAIN_NOT_ALLOWED"));
    }

    @Test
    void google_withInvalidGoogleToken_shouldReturn401() throws Exception {
        when(googleIdTokenVerifier.verify("bad-token"))
                .thenThrow(new InvalidGoogleTokenException("Invalid Google ID token"));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleAuthRequest("bad-token"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void google_withDeletedAccount_shouldReturn401() throws Exception {
        var user = userService.create(user("borrado@gmail.com"), profile(user("borrado@gmail.com")));
        userService.delete(user.getId());
        when(googleIdTokenVerifier.verify("id-token"))
                .thenReturn(identity("google-sub-deleted", "borrado@gmail.com"));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleAuthRequest("id-token"))))
                .andExpect(status().isUnauthorized());
    }

    private static GoogleIdentity identity(String subject, String email) {
        return new GoogleIdentity(subject, email, "Nuevo", "Usuario", "https://example.com/a.png");
    }
}
