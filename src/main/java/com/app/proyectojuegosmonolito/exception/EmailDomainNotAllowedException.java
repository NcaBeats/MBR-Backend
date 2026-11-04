package com.app.proyectojuegosmonolito.exception;

/**
 * La cuenta de Google es valida pero su dominio no esta permitido para crear
 * cuentas nuevas. Se traduce a 403.
 */
public class EmailDomainNotAllowedException extends RuntimeException {

    public EmailDomainNotAllowedException(String email) {
        super("Email domain not allowed: " + email);
    }
}
