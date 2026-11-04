package com.app.proyectojuegosmonolito.account.user.model;

import java.time.LocalDate;

/**
 * Datos que el usuario completa en el onboarding de Google. La identidad ya
 * viene verificada en el token de onboarding, por eso no se vuelve a pedir el
 * email ni los nombres.
 */
public record GoogleSignup(
        GoogleIdentity identity,
        String run,
        String nickname,
        LocalDate birthDate,
        Long comunaId,
        String address
) {
}
