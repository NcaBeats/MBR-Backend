package com.app.proyectojuegosmonolito.account.user.repository;

import com.app.proyectojuegosmonolito.account.user.model.OAuthIdentity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, Long> {

    @EntityGraph(attributePaths = {"user"})
    Optional<OAuthIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);

    boolean existsByUserIdAndProvider(Long userId, String provider);
}
