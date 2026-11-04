package com.app.proyectojuegosmonolito.security.dto;

import com.app.proyectojuegosmonolito.account.profile.model.ValidRun;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record GoogleCompleteRequest(
        @NotBlank String onboardingToken,
        @NotBlank @ValidRun String run,
        @NotBlank @Size(max = 255) String nickname,
        LocalDate birthDate,
        @NotNull Long comunaId,
        @NotBlank @Size(max = 300) String address
) {
}
