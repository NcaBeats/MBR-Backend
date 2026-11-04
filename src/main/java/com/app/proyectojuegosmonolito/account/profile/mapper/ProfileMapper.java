package com.app.proyectojuegosmonolito.account.profile.mapper;

import com.app.proyectojuegosmonolito.account.profile.model.Comuna;
import com.app.proyectojuegosmonolito.account.profile.model.Profile;
import com.app.proyectojuegosmonolito.account.profile.model.Region;
import com.app.proyectojuegosmonolito.account.profile.dto.ProfileResponse;
import org.springframework.stereotype.Component;

@Component
public class ProfileMapper {

    public ProfileResponse toResponse(Profile profile) {
        Region region = profile.getRegion();
        Comuna comuna = profile.getComuna();
        return new ProfileResponse(
                profile.getUserId(),
                profile.getNickname(),
                profile.getAvatarImage(),
                profile.getBio(),
                profile.getVisibility(),
                profile.getRun(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getBirthDate(),
                region != null ? region.getId() : null,
                region != null ? region.getNombre() : null,
                comuna != null ? comuna.getId() : null,
                comuna != null ? comuna.getNombre() : null,
                profile.getAddress(),
                profile.getCreatedAt()
        );
    }
}
