package com.app.proyectojuegosmonolito.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita los intentos de login fallidos por correo y por IP para frenar ataques
 * de fuerza bruta y credential stuffing.
 *
 * <p>Cuenta solo los fallos dentro de una ventana fija: al alcanzar el maximo la
 * clave queda bloqueada hasta que la ventana expira. Un login correcto borra el
 * contador, de modo que un usuario legitimo que se equivoca un par de veces no
 * acaba bloqueado, mientras que un atacante que prueba contrasenas distintas
 * agota la ventana y tiene que esperar.
 *
 * <p>El estado vive en memoria del proceso, igual que {@link
 * com.app.proyectojuegosmonolito.TokenVersionCache}: sirve para una sola
 * instancia. Si se escala horizontalmente hay que mover los contadores a Redis.
 */
@Slf4j
@Component
public class LoginAttemptLimiter {

    private record Window(int failures, long windowStart, long blockedUntil) {
    }

    private final Map<String, Window> byEmail = new ConcurrentHashMap<>();
    private final Map<String, Window> byIp = new ConcurrentHashMap<>();

    private final int maxPerEmail;
    private final int maxPerIp;
    private final long windowMillis;

    public LoginAttemptLimiter(
            @Value("${app.security.login.max-per-email:5}") int maxPerEmail,
            @Value("${app.security.login.max-per-ip:20}") int maxPerIp,
            @Value("${app.security.login.window-ms:900000}") long windowMillis) {
        this.maxPerEmail = maxPerEmail;
        this.maxPerIp = maxPerIp;
        this.windowMillis = windowMillis;
    }

    /**
     * @return segundos que faltan para liberar el bloqueo, o 0 si la peticion
     *         puede pasar.
     */
    public long retryAfterSeconds(String email, String ip) {
        long now = System.currentTimeMillis();
        long emailWait = secondsUntilUnblocked(byEmail.get(key(email)), now);
        long ipWait = secondsUntilUnblocked(byIp.get(key(ip)), now);
        return Math.max(emailWait, ipWait);
    }

    /**
     * Variante para flujos donde no hay email antes de validar un token de
     * terceros (Google): solo se limita por IP.
     */
    public long retryAfterSecondsForIp(String ip) {
        return secondsUntilUnblocked(byIp.get(key(ip)), System.currentTimeMillis());
    }

    public void onFailure(String email, String ip) {
        long now = System.currentTimeMillis();
        byEmail.compute(key(email), (k, current) -> advance(current, now, maxPerEmail));
        byIp.compute(key(ip), (k, current) -> advance(current, now, maxPerIp));
    }

    public void onFailureForIp(String ip) {
        long now = System.currentTimeMillis();
        byIp.compute(key(ip), (k, current) -> advance(current, now, maxPerIp));
    }

    public void onSuccess(String email, String ip) {
        byEmail.remove(key(email));
        byIp.remove(key(ip));
    }

    public void onSuccessForIp(String ip) {
        byIp.remove(key(ip));
    }

    private Window advance(Window current, long now, int max) {
        // Una ventana vencida se reinicia en vez de arrastrar el contador.
        Window base = (current != null && now - current.windowStart() < windowMillis)
                ? current
                : new Window(0, now, 0L);
        int failures = base.failures() + 1;
        long blockedUntil = failures >= max ? base.windowStart() + windowMillis : 0L;
        if (blockedUntil > 0L) {
            log.warn("Login attempts limit reached (failures={}, limit={}, windowMs={})", failures, max, windowMillis);
        }
        return new Window(failures, base.windowStart(), blockedUntil);
    }

    private long secondsUntilUnblocked(Window window, long now) {
        if (window == null || window.blockedUntil() <= now) {
            return 0L;
        }
        return (window.blockedUntil() - now + 999L) / 1000L;
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
