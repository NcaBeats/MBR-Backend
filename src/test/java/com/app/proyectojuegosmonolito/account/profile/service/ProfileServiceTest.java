package com.app.proyectojuegosmonolito.account.profile.service;

import com.app.proyectojuegosmonolito.account.profile.dto.ProfilePatchRequest;
import com.app.proyectojuegosmonolito.account.profile.model.Visibility;
import com.app.proyectojuegosmonolito.account.profile.repository.ProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.app.proyectojuegosmonolito.account.user.UserFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void findByUserId_whenFound_shouldReturnProfile() {
        var user = user(1L);
        var profile = profile(user);
        when(profileRepository.findByIdWithRegionAndComuna(1L)).thenReturn(Optional.of(profile));

        var result = profileService.findByUserId(1L);

        assertThat(result).isEqualTo(profile);
    }

    @Test
    void findByUserId_whenNotFound_shouldThrow() {
        when(profileRepository.findByIdWithRegionAndComuna(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.findByUserId(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_shouldModifyAndSave() {
        var user = user(1L);
        var profile = profile(user, "old", Visibility.PUBLIC);
        when(profileRepository.findByIdWithRegionAndComuna(1L)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = profileService.update(1L, new ProfilePatchRequest(null, "newnick", "new bio", Visibility.PRIVATE, null, null, null, null, null));

        assertThat(result.getNickname()).isEqualTo("newnick");
        assertThat(result.getBio()).isEqualTo("new bio");
        assertThat(result.getVisibility()).isEqualTo(Visibility.PRIVATE);
        verify(profileRepository).save(profile);
    }
}
