package com.app.proyectojuegosmonolito.account.profile.controller;

import com.app.proyectojuegosmonolito.TestcontainersConfiguration;
import com.app.proyectojuegosmonolito.account.profile.dto.ProfilePatchRequest;
import com.app.proyectojuegosmonolito.account.profile.model.Visibility;
import com.app.proyectojuegosmonolito.account.user.service.UserService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

import static com.app.proyectojuegosmonolito.account.user.UserFixtures.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class ProfileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Test
    void getMyProfile_shouldReturn200() throws Exception {
        var user = userService.create(user(), profile(user()));
        var token = jwt().jwt(b -> b.subject(user.getId().toString()));

        mockMvc.perform(get("/api/v1/profile").with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("user@test.com"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));
    }

    @Test
    void update_shouldReturn200() throws Exception {
        var user = userService.create(user(), profile(user()));
        var token = jwt().jwt(b -> b.subject(user.getId().toString()));

        mockMvc.perform(patch("/api/v1/profile").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProfilePatchRequest(null, "new_nick", "My bio", Visibility.PRIVATE, null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("new_nick"))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }

    @Test
    void update_withEmptyBody_shouldReturn200() throws Exception {
        var user = userService.create(user(), profile(user()));
        var token = jwt().jwt(b -> b.subject(user.getId().toString()));

        mockMvc.perform(patch("/api/v1/profile").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProfilePatchRequest(null, null, null, null, null, null, null, null, null))))
                .andExpect(status().isOk());
    }

    @Test
    void update_withTooLongNickname_shouldReturn400() throws Exception {
        var user = userService.create(user(), profile(user()));
        var token = jwt().jwt(b -> b.subject(user.getId().toString()));
        var tooLongNickname = "n".repeat(256);

        mockMvc.perform(patch("/api/v1/profile").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProfilePatchRequest(null, tooLongNickname, null, null, null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.errors[0]").value(containsString("nickname")));
    }
}
