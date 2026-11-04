package com.app.proyectojuegosmonolito.security.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
        @NotBlank String idToken
) {
}
