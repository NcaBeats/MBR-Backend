package com.app.proyectojuegosmonolito.security.controller;

import com.app.proyectojuegosmonolito.TestcontainersConfiguration;
import com.app.proyectojuegosmonolito.security.dto.GoogleAuthRequest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un entorno local sin GOOGLE_CLIENT_ID tiene que poder arrancar: la unica
 * consecuencia es que el endpoint de Google responde 503 en vez de tumbar el
 * contexto entero.
 */
@SpringBootTest(properties = "app.oauth.google.client-id=")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class GoogleSignInDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void google_withoutClientId_shouldReturn503() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleAuthRequest("id-token"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.code").value("GOOGLE_SIGN_IN_NOT_CONFIGURED"));
    }
}
