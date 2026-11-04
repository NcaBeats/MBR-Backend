package com.app.proyectojuegosmonolito.account.user.controller;

import com.app.proyectojuegosmonolito.TestcontainersConfiguration;
import com.app.proyectojuegosmonolito.account.user.dto.UserRequestCreate;
import com.app.proyectojuegosmonolito.account.user.dto.UserUpdateRequest;
import com.app.proyectojuegosmonolito.account.user.repository.UserRepository;
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

import java.time.LocalDate;

import static com.app.proyectojuegosmonolito.account.user.UserFixtures.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void getMe_shouldReturn200() throws Exception {
        var saved = userRepository.save(user());
        var token = jwt().jwt(b -> b.subject(saved.getId().toString()));

        mockMvc.perform(get("/api/v1/users/me").with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    @Test
    void getAll_shouldReturnPage() throws Exception {
        userRepository.save(user("alpha@test.com"));
        userRepository.save(user("beta@test.com"));

        mockMvc.perform(get("/api/v1/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void create_shouldReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserRequestCreate("new@gmail.com", "pass123", "Nuevo", "Usuario",
                                        "190110222", LocalDate.of(2000, 1, 1),
                                        1L, "Av. Siempre Viva 123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("new@gmail.com"));
    }

    @Test
    void create_withInvalidBody_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRequestCreate(null, null, "Nuevo", "Usuario",
                                "190110222", LocalDate.of(2000, 1, 1),
                                1L, "Av. Siempre Viva 123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void update_shouldReturn200() throws Exception {
        var saved = userRepository.save(user());
        var token = jwt().jwt(b -> b.subject(saved.getId().toString()));

        mockMvc.perform(put("/api/v1/users").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserUpdateRequest("updated@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated@test.com"));
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        var saved = userRepository.save(user());
        var token = jwt().jwt(b -> b.subject(saved.getId().toString()));

        mockMvc.perform(delete("/api/v1/users").with(token))
                .andExpect(status().isNoContent());
    }
}
