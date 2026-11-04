package com.app.proyectojuegosmonolito.account.user.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Vinculo entre una cuenta local y una identidad externa (hoy solo Google).
 *
 * <p>{@code providerUserId} guarda el {@code sub} del ID token: es estable
 * aunque el usuario cambie el correo en Google. El email se conserva como dato
 * de auditoria en {@code emailAtProvider}, nunca como clave de busqueda.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "oauth_identity", uniqueConstraints = {
        @UniqueConstraint(name = "uq_oauth_identity_provider_user",
                columnNames = {"provider", "provider_user_id"}),
        @UniqueConstraint(name = "uq_oauth_identity_user_provider",
                columnNames = {"user_id", "provider"})
})
public class OAuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "email_at_provider", length = 100)
    private String emailAtProvider;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;
}
