package com.app.proyectojuegosmonolito.security.mapper;

import com.app.proyectojuegosmonolito.security.dto.GoogleCompleteRequest;
import com.app.proyectojuegosmonolito.security.dto.RegisterRequest;
import com.app.proyectojuegosmonolito.account.user.model.GoogleIdentity;
import com.app.proyectojuegosmonolito.account.user.model.GoogleSignup;
import com.app.proyectojuegosmonolito.account.user.model.User;
import com.app.proyectojuegosmonolito.account.profile.model.Comuna;
import com.app.proyectojuegosmonolito.account.profile.model.Profile;
import com.app.proyectojuegosmonolito.account.profile.model.Visibility;
import com.app.proyectojuegosmonolito.account.profile.repository.ComunaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AuthMapper {

    private final ComunaRepository comunaRepository;

    public AuthMapper(ComunaRepository comunaRepository) {
        this.comunaRepository = comunaRepository;
    }

    public User toEntity(RegisterRequest request) {
        return User.builder()
                .email(request.email())
                .password(request.password())
                .build();
    }

    public Profile toProfile(RegisterRequest request, User user) {
        var comuna = comunaRepository.findById(request.comunaId())
                .orElseThrow(() -> new EntityNotFoundException("Comuna not found: " + request.comunaId()));
        return Profile.builder()
                .user(user)
                .nickname(request.email())
                .run(request.run())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .birthDate(request.birthDate())
                .comuna(comuna)
                .region(comuna.getRegion())
                .address(request.address())
                .visibility(Visibility.PUBLIC)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Combina la identidad ya verificada por Google con lo que el usuario
     * completo en el formulario de onboarding.
     */
    public GoogleSignup toGoogleSignup(GoogleIdentity identity, GoogleCompleteRequest request) {
        return new GoogleSignup(
                identity,
                request.run(),
                request.nickname(),
                request.birthDate(),
                request.comunaId(),
                request.address()
        );
    }
}
