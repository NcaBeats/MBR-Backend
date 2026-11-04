package com.app.proyectojuegosmonolito.account.user.service;

import com.app.proyectojuegosmonolito.TokenVersionCache;
import com.app.proyectojuegosmonolito.account.profile.model.Profile;
import com.app.proyectojuegosmonolito.account.profile.model.Visibility;
import com.app.proyectojuegosmonolito.account.profile.repository.ComunaRepository;
import com.app.proyectojuegosmonolito.account.user.dto.AdminUserUpdateRequest;
import com.app.proyectojuegosmonolito.account.user.model.GoogleIdentity;
import com.app.proyectojuegosmonolito.account.user.model.GoogleSignup;
import com.app.proyectojuegosmonolito.account.user.model.OAuthIdentity;
import com.app.proyectojuegosmonolito.account.user.model.Role;
import com.app.proyectojuegosmonolito.account.user.model.User;
import com.app.proyectojuegosmonolito.account.wallet.model.Wallet;
import com.app.proyectojuegosmonolito.account.user.repository.OAuthIdentityRepository;
import com.app.proyectojuegosmonolito.account.user.repository.UserRepository;
import com.app.proyectojuegosmonolito.exception.BusinessException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.app.proyectojuegosmonolito.common.RepositoryUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** Valor de {@code oauth_identity.provider} para Google Sign-In. */
    public static final String PROVIDER_GOOGLE = "google";

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final OAuthIdentityRepository oauthIdentityRepository;
    private final ComunaRepository comunaRepository;
    private final TokenVersionCache tokenVersionCache;

    @Transactional
    public User create(User user, Profile profile) {
        if (profile == null) {
            throw new BusinessException(BusinessException.PROFILE_REQUIRED,
                    "A profile is required to create a user.");
        }
        var now = Instant.now();
        user.setCreatedAt(now);

        profile.setUser(user);
        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(now);
        }
        user.setProfile(profile);

        user.setWallet(Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .updatedAt(now)
                .build());
        if (user.getRole() == null) {
            user.setRole(Role.CLIENTE);
        }
        // Una cuenta creada con Google no tiene password local: se guarda NULL y
        // UserDetailsServiceImpl rechaza el login por password.
        if (user.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        var saved = userRepository.save(user);
        log.info("Created user: {} (id={})", saved.getEmail(), saved.getId());
        return saved;
    }

    /**
     * Alta de una cuenta creada con Google: misma ruta que {@link #create} para no
     * duplicar reglas (wallet, rol, createdAt), pero el perfil se arma aca porque
     * los datos de Google (nombres) vienen del ID token y no de un DTO HTTP.
     */
    @Transactional
    public User createFromGoogle(GoogleSignup signup) {
        var identity = signup.identity();
        var comuna = comunaRepository.findById(signup.comunaId())
                .orElseThrow(() -> new EntityNotFoundException("Comuna not found: " + signup.comunaId()));

        var user = User.builder()
                .email(identity.email())
                .password(null)
                .build();
        var profile = Profile.builder()
                .user(user)
                .nickname(signup.nickname())
                .avatarImage(avatarUrl(identity.picture()))
                .run(signup.run())
                .firstName(truncate(defaultIfBlank(identity.firstName(), signup.nickname()), 50))
                .lastName(truncate(defaultIfBlank(identity.lastName(), "-"), 100))
                .birthDate(signup.birthDate())
                .region(comuna.getRegion())
                .comuna(comuna)
                .address(signup.address())
                .visibility(Visibility.PUBLIC)
                .build();

        var saved = create(user, profile);
        ensureGoogleIdentity(saved, identity);
        log.info("Created user from Google: {} (id={})", saved.getEmail(), saved.getId());
        return saved;
    }

    /**
     * Crea el vinculo con Google o refresca el email de auditoria. Idempotente: si
     * dos logins concurrentes del mismo usuario llegan a la vez, el UNIQUE de
     * (provider, provider_user_id) corta al segundo y el llamador puede reintentar.
     */
    @Transactional
    public void ensureGoogleIdentity(User user, GoogleIdentity identity) {
        var existing = oauthIdentityRepository
                .findByProviderAndProviderUserId(PROVIDER_GOOGLE, identity.subject());
        if (existing.isPresent()) {
            var link = existing.get();
            if (!link.getUser().getId().equals(user.getId())) {
                throw new BusinessException(BusinessException.OAUTH_ACCOUNT_CONFLICT,
                        "This Google account is already linked to another user.");
            }
            link.setEmailAtProvider(identity.email());
            return;
        }
        oauthIdentityRepository.save(OAuthIdentity.builder()
                .user(user)
                .provider(PROVIDER_GOOGLE)
                .providerUserId(identity.subject())
                .emailAtProvider(identity.email())
                .linkedAt(Instant.now())
                .build());
    }

    public Optional<User> findByGoogleSubject(String subject) {
        return oauthIdentityRepository
                .findByProviderAndProviderUserId(PROVIDER_GOOGLE, subject)
                .map(OAuthIdentity::getUser)
                .filter(user -> user.getDeletedAt() == null);
    }

    /**
     * Refresca la foto de perfil con la que devuelve Google. Se llama en cada
     * login: la URL cambia cuando el usuario cambia su foto. Solo acepta https
     * (el claim viene firmado, pero no queremos guardar cualquier esquema).
     */
    @Transactional
    public void updateOAuthAvatar(Long userId, String picture) {
        var avatar = avatarUrl(picture);
        if (avatar == null) {
            return;
        }
        var user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getProfile() == null) {
            return;
        }
        if (!avatar.equals(user.getProfile().getAvatarImage())) {
            user.getProfile().setAvatarImage(avatar);
            log.info("Updated OAuth avatar for user {}", userId);
        }
    }

    public User findById(Long id) {
        log.info("Fetching user by id: {}", id);
        return RepositoryUtils.findOrThrow(userRepository, id, "User");
    }

    public User findByEmail (String email) {
        log.info("Fetching user by email: {}", email);
        return RepositoryUtils.orNotFound(userRepository.findByEmailAndDeletedAtIsNull(email), "User", email);
    }

    public Optional<User> findOptionalByEmail(String email) {
        log.info("Fetching optional user by email: {}", email);
        return userRepository.findByEmail(email);
    }

    /**
     * Igual que {@link #findOptionalByEmail} pero excluye las cuentas soft-deleted:
     * un login con Google no debe resucitar una cuenta borrada.
     */
    public Optional<User> findActiveOptionalByEmail(String email) {
        log.info("Fetching optional active user by email: {}", email);
        return userRepository.findByEmailAndDeletedAtIsNull(email);
    }

    public Page<User> findAll(Pageable pageable) {
        log.info("Fetching all users with pageable: {}", pageable);
        return userRepository.findAllByDeletedAtIsNull(pageable);
    }

    public Page<User> searchByEmail(String email, Pageable pageable) {
        log.info("Searching users by email: {} with pageable: {}", email, pageable);
        return userRepository.findByEmailContainingIgnoreCase(email, pageable);
    }

    @Transactional
    public User update(Long id, String email) {
        var userEntity = RepositoryUtils.findOrThrow(userRepository, id, "User");
        log.info("Updating user {}: email={}", id, email.replaceAll("[\\r\\n]", " "));
        userEntity.update(email);
        log.info("Updated user {}", userEntity.getId());
        return userEntity;
    }

    @Transactional
    public User adminUpdate(Long id, AdminUserUpdateRequest request) {
        var user = RepositoryUtils.findOrThrow(userRepository, id, "User");

        if (request.email() != null && !request.email().isBlank()) {
            user.setEmail(request.email());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }

        var saved = userRepository.save(user);
        log.info("Admin updated user {}: email={}, role={}", id, saved.getEmail(), saved.getRole());
        return saved;
    }

    @Transactional
    public void updatePassword (Long id, String currentPassword, String newPassword) {
        var user = RepositoryUtils.findOrThrow(userRepository, id, "User");
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        incrementTokenVersion(user);
    }

    @Transactional
    public int revokeAllTokens(Long id) {
        var user = RepositoryUtils.findOrThrow(userRepository, id, "User");
        return incrementTokenVersion(user);
    }

    public Optional<Integer> getTokenVersion(Long id) {
        return userRepository.findById(id).map(User::getTokenVersion);
    }

    private int incrementTokenVersion(User user) {
        var newVersion = user.getTokenVersion() + 1;
        user.setTokenVersion(newVersion);
        log.info("Invalidated tokens for user {}: tokenVersion={}", user.getId(), newVersion);
        tokenVersionCache.set(user.getId(), newVersion);
        return newVersion;
    }

    @Transactional
    public void delete(Long id) {
        var user = RepositoryUtils.findOrThrow(userRepository, id, "User");
        if (user.getRole() == Role.ADMIN) {
            log.warn("Attempted to delete admin account: {}", id);
            throw new IllegalArgumentException("The admin account cannot be deleted");
        }
        if (user.getDeletedAt() != null) {
            log.info("User {} is already deleted", id);
            return;
        }
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        incrementTokenVersion(user);
        log.info("Soft-deleted user {}", id);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** La columna es TEXT, pero no queremos guardar URLs basura ni esquemas raros. */
    private static String avatarUrl(String picture) {
        if (picture == null) {
            return null;
        }
        var trimmed = picture.trim();
        return trimmed.startsWith("https://") && trimmed.length() <= 500 ? trimmed : null;
    }

    /**
     * Google no garantiza largos: un `given_name` de 60 caracteres romperia el
     * @Size(50) de Profile al persistir. Se recorta en vez de devolver un 500.
     */
    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
