package com.app.proyectojuegosmonolito.account.user;

import com.app.proyectojuegosmonolito.account.profile.model.Comuna;
import com.app.proyectojuegosmonolito.account.profile.model.Profile;
import com.app.proyectojuegosmonolito.account.profile.model.Region;
import com.app.proyectojuegosmonolito.account.user.model.Role;
import com.app.proyectojuegosmonolito.account.user.model.User;
import com.app.proyectojuegosmonolito.account.profile.model.Visibility;
import com.app.proyectojuegosmonolito.account.wallet.model.Wallet;
import com.app.proyectojuegosmonolito.account.profile.repository.RegionRepository;
import com.app.proyectojuegosmonolito.account.profile.repository.ComunaRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class UserFixtures {

    private static int runCounter = 10000000;

    public static User user() {
        return User.builder()
                .email("user@test.com")
                .password("pass123")
                .role(Role.CLIENTE)
                .createdAt(Instant.now())
                .build();
    }

    public static User user(Long id) {
        return User.builder()
                .id(id)
                .email("user" + id + "@test.com")
                .password("pass123")
                .role(Role.CLIENTE)
                .createdAt(Instant.now())
                .build();
    }

    public static User user(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .password("pass123")
                .role(Role.CLIENTE)
                .createdAt(Instant.now())
                .build();
    }

    public static User user(String email) {
        return User.builder()
                .email(email)
                .password("pass123")
                .role(Role.CLIENTE)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Variante sin repositorios para tests unitarios (Mockito) y para tests de
     * integracion: region y comuna se referencian como entidades detached con id
     * 1, que es el par que siembra {@code src/test/resources/test-data.sql}
     * (Metropolitana de Santiago / Santiago). No se persisten por cascade, solo
     * aportan la FK.
     */
    public static Profile profile(User user) {
        var region = detachRegion();
        return baseProfile(user)
                .region(region)
                .comuna(detachComuna(region))
                .visibility(Visibility.PUBLIC)
                .build();
    }

    public static Profile profile(User user, String nickname, Visibility visibility) {
        var region = detachRegion();
        return baseProfile(user)
                .nickname(nickname)
                .region(region)
                .comuna(detachComuna(region))
                .visibility(visibility)
                .build();
    }

    public static Profile profile(User user, RegionRepository regionRepository, ComunaRepository comunaRepository) {
        var region = regionRepository.findByNombre("Metropolitana de Santiago").orElseThrow();
        var comuna = comunaRepository.findByNombreAndRegion_Id("Santiago", region.getId()).orElseThrow();
        return Profile.builder()
                .userId(user.getId())
                .user(user)
                .nickname(user.getEmail())
                .run(generateUniqueRun())
                .firstName("Test")
                .lastName("User")
                .region(region)
                .comuna(comuna)
                .address("Calle Test 123")
                .visibility(Visibility.PUBLIC)
                .createdAt(Instant.now())
                .build();
    }

    public static Profile profile(User user, String nickname, Visibility visibility, RegionRepository regionRepository, ComunaRepository comunaRepository) {
        var region = regionRepository.findByNombre("Metropolitana de Santiago").orElseThrow();
        var comuna = comunaRepository.findByNombreAndRegion_Id("Santiago", region.getId()).orElseThrow();
        return Profile.builder()
                .userId(user.getId())
                .user(user)
                .nickname(nickname)
                .run(generateUniqueRun())
                .firstName("Test")
                .lastName("User")
                .region(region)
                .comuna(comuna)
                .address("Calle Test 123")
                .visibility(visibility)
                .createdAt(Instant.now())
                .build();
    }

    public static Wallet wallet(User user, BigDecimal balance) {
        return Wallet.builder()
                .userId(user.getId())
                .user(user)
                .balance(balance)
                .updatedAt(Instant.now())
                .build();
    }

    private static Profile.ProfileBuilder baseProfile(User user) {
        return Profile.builder()
                .userId(user.getId())
                .user(user)
                .nickname(user.getEmail())
                .run(generateUniqueRun())
                .firstName("Test")
                .lastName("User")
                .address("Calle Test 123")
                .createdAt(Instant.now());
    }

    private static Region detachRegion() {
        return Region.builder().id(1L).nombre("Metropolitana de Santiago").build();
    }

    private static Comuna detachComuna(Region region) {
        return Comuna.builder().id(1L).nombre("Santiago").region(region).build();
    }

    private static String generateUniqueRun() {
        return String.valueOf(runCounter++);
    }
}
