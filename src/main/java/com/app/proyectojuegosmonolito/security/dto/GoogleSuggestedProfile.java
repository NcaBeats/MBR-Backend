package com.app.proyectojuegosmonolito.security.dto;

/**
 * Datos que Google ya entrega verificados y que el frontend usa para precargar
 * el formulario de onboarding.
 */
public record GoogleSuggestedProfile(
        String email,
        String firstName,
        String lastName,
        String picture
) {
}
