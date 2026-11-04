package com.app.proyectojuegosmonolito.account.profile.dto;

import com.app.proyectojuegosmonolito.account.profile.model.Visibility;
import java.time.Instant;
import java.time.LocalDate;

public record ProfileResponse(
        Long userId,
        String nickname,
        String avatarImage,
        String bio,
        Visibility visibility,
        String run,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Long regionId,
        String region,
        Long comunaId,
        String comuna,
        String address,
        Instant createdAt
) {}
