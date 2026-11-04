package com.app.proyectojuegosmonolito.account.profile.service;

import com.app.proyectojuegosmonolito.TestcontainersConfiguration;
import com.app.proyectojuegosmonolito.account.profile.dto.ProfilePatchRequest;
import com.app.proyectojuegosmonolito.account.profile.model.Visibility;
import com.app.proyectojuegosmonolito.account.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

import static com.app.proyectojuegosmonolito.account.user.UserFixtures.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class ProfileServiceIntegrationTest {

    @Autowired
    private ProfileService profileService;

    @Autowired
    private UserService userService;

    @Test
    void findByUserId_shouldReturnProfile() {
        var user = userService.create(user(), profile(user()));

        var profile = profileService.findByUserId(user.getId());

        assertThat(profile.getNickname()).isEqualTo("user@test.com");
        assertThat(profile.getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void findByUserId_whenNotFound_shouldThrow() {
        assertThatThrownBy(() -> profileService.findByUserId(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_shouldModifyAndSave() {
        var user = userService.create(user(), profile(user()));

        var result = profileService.update(user.getId(), new ProfilePatchRequest(null, "newnick", "new bio", Visibility.PRIVATE, null, null, null, null, null));

        assertThat(result.getNickname()).isEqualTo("newnick");
        assertThat(result.getBio()).isEqualTo("new bio");
        assertThat(result.getVisibility()).isEqualTo(Visibility.PRIVATE);
    }
}
