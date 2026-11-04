package com.app.proyectojuegosmonolito.account.user.dto;

import com.app.proyectojuegosmonolito.account.profile.model.ValidRun;
import com.app.proyectojuegosmonolito.validation.ValidEmailDomain;
import com.app.proyectojuegosmonolito.validation.PasswordRules;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record UserRequestCreate(
    @NotBlank @Email @Size(max = 100) @ValidEmailDomain String email,
    @NotBlank @Size(min = PasswordRules.MIN, max = PasswordRules.MAX) String password,
    @NotBlank @Size(max = 50) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotBlank @ValidRun String run,
    LocalDate birthDate,
    @NotNull Long comunaId,
    @NotBlank @Size(max = 300) String address
) {}
