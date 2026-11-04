package com.app.proyectojuegosmonolito.account.user.mapper;

import com.app.proyectojuegosmonolito.account.profile.model.Profile;
import com.app.proyectojuegosmonolito.account.profile.model.Visibility;
import com.app.proyectojuegosmonolito.account.profile.repository.ComunaRepository;
import com.app.proyectojuegosmonolito.account.user.model.User;
import com.app.proyectojuegosmonolito.account.user.dto.UserRequestCreate;
import com.app.proyectojuegosmonolito.account.user.dto.UserResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class UserMapper {

    private final ComunaRepository comunaRepository;

    public UserMapper(ComunaRepository comunaRepository) {
        this.comunaRepository = comunaRepository;
    }

    public User toEntityCreate(UserRequestCreate request) {
        return User.builder()
                .email(request.email())
                .password(request.password())
                .build();
    }

    /**
     * La region se deriva de la comuna, igual que en el registro publico: el
     * cliente solo manda {@code comunaId}, asi no puede guardar una combinacion
     * region/comuna invalida.
     */
    public Profile toProfileCreate(UserRequestCreate request, User user) {
        var comuna = comunaRepository.findById(request.comunaId())
                .orElseThrow(() -> new EntityNotFoundException("Comuna not found: " + request.comunaId()));
        return Profile.builder()
                .user(user)
                .nickname(user.getEmail())
                .run(request.run())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .birthDate(request.birthDate())
                .region(comuna.getRegion())
                .comuna(comuna)
                .address(request.address())
                .visibility(Visibility.PUBLIC)
                .createdAt(Instant.now())
                .build();
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
