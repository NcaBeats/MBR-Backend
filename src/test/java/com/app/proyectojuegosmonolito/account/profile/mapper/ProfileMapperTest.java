package com.app.proyectojuegosmonolito.account.profile.mapper;

import com.app.proyectojuegosmonolito.account.profile.dto.ProfileResponse;
import com.app.proyectojuegosmonolito.account.profile.model.Profile;
import com.app.proyectojuegosmonolito.account.profile.model.Visibility;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class ProfileMapperTest {

    private final ProfileMapper mapper = new ProfileMapper();

    @Test
    void toResponse_shouldMapAllFields() {
        var createdAt = Instant.parse("2026-01-01T00:00:00Z");
        var profile = Profile.builder()
                .userId(1L).nickname("nick")
                .avatarImage("https://example.com/a.png")
                .bio("bio")
                .visibility(Visibility.PUBLIC).createdAt(createdAt)
                .build();

        var result = mapper.toResponse(profile);

        assertThat(result).isEqualTo(new ProfileResponse(1L, "nick",
                "https://example.com/a.png", "bio",
                Visibility.PUBLIC, null, null, null, null, null, null, null, null, null, createdAt));
    }
}
