package com.app.proyectojuegosmonolito.account.user.service;

import com.app.proyectojuegosmonolito.account.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(@NonNull String email) throws UsernameNotFoundException {
        var user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        // Cuenta creada con Google: no hay password local que comparar. Sin este
        // guardia, User.withUsername(...).password(null) lanza IllegalArgumentException
        // y el login responde 500 en vez de 401.
        if (user.getPassword() == null) {
            throw new UsernameNotFoundException("User has no local password");
        }
        return User.withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }
}
