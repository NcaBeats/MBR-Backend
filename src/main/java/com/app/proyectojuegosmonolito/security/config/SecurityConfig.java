package com.app.proyectojuegosmonolito.security.config;

import com.app.proyectojuegosmonolito.TokenVersionCache;
import com.app.proyectojuegosmonolito.account.user.service.UserService;
import com.app.proyectojuegosmonolito.security.oauth.GoogleIdTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            TokenVersionCache tokenVersionCache,
            UserService userService,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(new TokenVersionValidatingJwtAuthenticationConverter(
                                jwtAuthenticationConverter, tokenVersionCache, userService))
                ))
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Games: anyone can read; ADMIN and VENDEDOR can create; ADMIN and owning VENDEDOR can update
                        .requestMatchers(HttpMethod.GET, "/api/v1/games").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/games/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/games").hasAnyRole("ADMIN", "VENDEDOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/games/**").hasAnyRole("ADMIN", "VENDEDOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/games/**").hasAnyRole("ADMIN", "VENDEDOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/games/**").hasRole("ADMIN")
                        // Categories: anyone can read, only ADMIN can write
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/categories/**").hasRole("ADMIN")
                        // Users: ADMIN for list/create/get-by-id/update, authenticated for self-only endpoints
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/password").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").hasRole("ADMIN")
                        // Profile: everyone can view another user's public profile (READ visibility), ADMIN edits any
                        .requestMatchers(HttpMethod.GET, "/api/v1/profile/{userId}").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/profile/{userId}").hasRole("ADMIN")
                        // Wallet: ADMIN only for funding
                        .requestMatchers(HttpMethod.PUT, "/api/v1/wallet").hasRole("ADMIN")
                        // Purchases: only ADMIN and CLIENTE (VENDEDOR cannot buy nor see orders)
                        .requestMatchers(HttpMethod.POST, "/api/v1/purchases").hasAnyRole("ADMIN", "CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/purchases").hasAnyRole("ADMIN", "CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/purchases/**").hasAnyRole("ADMIN", "CLIENTE")
                        // Library: only ADMIN and CLIENTE (VENDEDOR has no library)
                        .requestMatchers("/api/v1/library").hasAnyRole("ADMIN", "CLIENTE")
                        .requestMatchers("/api/v1/library/**").hasAnyRole("ADMIN", "CLIENTE")
                        // Blogs: anyone can read, only ADMIN can write/delete
                        .requestMatchers(HttpMethod.GET, "/api/v1/blogs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/blogs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/blogs").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/blogs/media/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/blogs/{id}/cover").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/blogs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/blogs/**").hasRole("ADMIN")
                        // Contacts: public to create, ADMIN only to list
                        .requestMatchers(HttpMethod.POST, "/api/v1/contacts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/contacts").hasRole("ADMIN")
                        // Regions & Comunas: public read for catalog
                        .requestMatchers(HttpMethod.GET, "/api/v1/regions/**").permitAll()
                        // Staff management: ADMIN and VENDEDOR (vendors only see their own catalog/orders)
                        .requestMatchers("/api/v1/manage/**").hasAnyRole("ADMIN", "VENDEDOR")
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        var config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var grantedAuthorities = new JwtGrantedAuthoritiesConverter();
        grantedAuthorities.setAuthorityPrefix("ROLE_");
        grantedAuthorities.setAuthoritiesClaimName("role");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthorities);
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(@Value("${app.jwt.secret}") String secret) {
        var secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        return NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Decoder del ID token de Google. Es un bean distinto del decoder de la app
     * (HS256) y por eso se inyecta siempre con {@code @Qualifier("googleIdTokenDecoder")}.
     *
     * <p>La firma se valida contra el JWKS publico de Google con RS256 (el default
     * del builder) y {@code RemoteJWKSet} cachea las claves: no hay una llamada de
     * red por request. El issuer se valida aca porque Google acepta dos formas.
     */
    @Bean
    public JwtDecoder googleIdTokenDecoder(
            @Value("${app.oauth.google.jwk-set-uri}") String jwkSetUri) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtClaimValidator<String>("iss",
                        issuer -> issuer != null && GoogleIdTokenVerifier.VALID_ISSUERS.contains(issuer))
        ));
        return decoder;
    }

    @Bean
    @Primary
    public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
        var secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
