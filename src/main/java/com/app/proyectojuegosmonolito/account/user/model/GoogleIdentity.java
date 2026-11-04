package com.app.proyectojuegosmonolito.account.user.model;

/**
 * Identidad verificada que devuelve Google despues de validar el ID token.
 *
 * @param subject   claim {@code sub}; clave estable de la cuenta en Google
 * @param email     correo verificado por Google
 * @param firstName claim {@code given_name} (puede venir vacio)
 * @param lastName  claim {@code family_name} (puede venir vacio)
 * @param picture   claim {@code picture} (URL publica del avatar)
 */
public record GoogleIdentity(
        String subject,
        String email,
        String firstName,
        String lastName,
        String picture
) {
}
