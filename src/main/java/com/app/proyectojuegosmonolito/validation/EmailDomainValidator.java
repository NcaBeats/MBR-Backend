package com.app.proyectojuegosmonolito.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Locale;
import java.util.Set;

public class EmailDomainValidator implements ConstraintValidator<ValidEmailDomain, String> {

    public static final Set<String> ALLOWED_DOMAINS = Set.of(
            "duoc.cl",
            "profesor.duoc.cl",
            "gmail.com"
    );

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        return isAllowed(email);
    }

    /**
     * Regla compartida por el registro por formulario y el alta con Google: si el
     * flujo OAuth no la aplicara, se podrian crear cuentas con correos que el
     * registro normal rechaza.
     */
    public static boolean isAllowed(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return false;
        }

        String domain = email.substring(atIndex + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_DOMAINS.contains(domain);
    }
}
