# Proyecto-Juegos-Monolito — Agent Guide

## Stack
- **Spring Boot 4.1.0** + **Java 25**, Maven wrapper (`mvnw`)
- **Spring Modulith 2.1.0** — modular monolith
- **PostgreSQL 17** via Docker Compose; **Flyway** (`ddl-auto: none`) — `src/main/resources/db/migration/`
- **Lombok** — never write getters/setters/constructors
- **Jackson 3.x** — import `tools.jackson.databind.ObjectMapper` (NOT `com.fasterxml.jackson`)
- **Testcontainers** for integration tests (requires Docker)

## Key commands
```sh
./mvnw compile              # compile
./mvnw test -Dtest=ModulithTests   # Modulith architecture verification (no Docker needed)
./mvnw test                 # all tests — REQUIRES Docker Desktop running (Testcontainers)
./mvnw test -Dtest=SpecificTest
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # dev server: DB creds + JWT secret + Cloudinary + auto-starts compose.yaml Postgres
./mvnw verify               # includes integration tests
./mvnw verify -Pdependency-check   # OWASP dependency-check (downloads NVD, slow; HTML report in target/)
docker compose up -d        # dev Postgres only (reads .env)
```
- Deploy to prod is **Render/Railway only** (builds `Dockerfile`, env vars typed directly in the Render dashboard — there is NO prod env file in the repo). There is NO `compose.prod.yaml`.
- **Obsolete env vars still sitting in the Render panel** (safe to delete, nothing reads them): `APP_MIGRATE_IMAGES` (its `ImageMigrationRunner` was removed; replaced by `APP_MEDIA_NORMALIZE_ENABLED`) and `CLOUDINARY_URL` (all media lives in R2). `R2_PUBLIC_BASE_URL` is NOT obsolete — Render has no such var, so it resolves from the `application.yaml` default, which already points at the prod bucket.

## Profiles — activated EXTERNALLY (no `spring.profiles.active` in `application.yaml`)
- **dev** — via the `spring-boot-maven-plugin` `<profiles>` in `pom.xml`, so it ONLY applies to `./mvnw spring-boot:run`. Running the main class from IntelliJ requires `SPRING_PROFILES_ACTIVE=dev` env var in the Run Configuration. `application-dev.yaml` reads the whole local config from `.env` (via `spring.config.import: "optional:file:.env[.properties]"`) for `spring.datasource.*` (3 vars), `app.jwt.secret` and `app.jwt.expiration`. The R2 vars and `CLOUDINARY_URL` (legacy, unused) resolve through `application.yaml` with empty defaults. **Those placeholders have NO default on purpose**: if `.env` is missing or incomplete the app fails at boot naming the variable, instead of silently connecting to a wrong localhost. The `dev` profile also forces `app.seed.enabled: true` and `SPRING_DATASOURCE_*` defaults are overridden by the env var of the same name.
- **test** — via `@ActiveProfiles("test")` on all 14 `@SpringBootTest` classes; `src/test/resources/application-test.yaml` supplies fake JWT/Cloudinary/R2 values so no env var is needed. `app.jwt.secret` must be ≥256 bits for HS256, else `KeyLengthException`. The 4 `@DataJpaTest` classes have NO profile — they get the datasource from `TestcontainersConfiguration` via `@ServiceConnection` (highest-precedence property source), so `application.yaml`'s datasource defaults are never used.
- **prod** — via `SPRING_PROFILES_ACTIVE=prod` env var (set by Render). `application-prod.yaml` has NO defaults for `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`, `APP_JWT_SECRET`/`APP_JWT_EXPIRATION` and `APP_CORS_ALLOWED_ORIGINS` → app won't boot without them. `CLOUDINARY_URL` and the R2 vars resolve through `application.yaml` with EMPTY defaults, so if they're missing the app **boots fine and only fails on the first upload** — check the deploy logs, not just the health endpoint.
- `DataInitializer` (`config/`) is `@ConditionalOnProperty("app.seed.enabled", havingValue = "true")` (NOT `@Profile("dev")`): seeds 44 games, 9 categories, player1 (ADMIN, $200), player2 (CLIENTE, $50), broke_player (CLIENTE). All users have full profile data (RUN, name, region, etc.). The `dev` profile forces it to `true`; in prod set `APP_SEED_ENABLED` explicitly — `false` starts with an EMPTY DB and NO ADMIN (promote one via SQL, since `POST /api/v1/users` is ADMIN-only), `true` creates player1 with a known password that must be changed immediately.
- **R2 env-precedence gotcha (real incident 2026-09-30)**: Spring resolves the `app.r2.*` placeholders from **OS env vars BEFORE** the `.env` import, so a stale `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` in the shell/IDE env silently overrides `.env` → **every S3 call returns `401 Unauthorized`** (fixed by clearing them). `.env` is the single source of truth: `Remove-Item Env:R2_ACCESS_KEY_ID, Env:R2_SECRET_ACCESS_KEY` before `mvnw spring-boot:run`.

## Media: invariante de URLs absolutas
- **Toda URL de media persistida es absoluta** (`https?://`): `game.video_url`, `game.image_url`, `game.banner_url`, `game_image.url`. Nunca rutas relativas.
- El contrato vive en `game/model/MediaUrl.java`. `GameService.requireAbsoluteMedia()` + `MediaUrl.requireAbsolute()` validan en **los 10 caminos de escritura** (`create`, `update`, `updateWithFiles`, `applyMediaUrls`, `updateImage/BannerUrl/VideoUrl`, `assignBanner`, `addGalleryImage`, `replaceGallery`). Rechazo estricto → **400**, no normalización silenciosa.
- **null/vacío sigue siendo válido** y significa "no cambiar" en la edición parcial (`applyMediaUrls`, `update`). El guardián valida el valor **entrante**, nunca el que ya estaba guardado: si no, editar el precio de un juego con video legacy daría 400 por un dato que el admin no está tocando.
- `GameService.saveAll()` queda **deliberadamente sin validar** — es el único método que persiste sin pasar por el guardián, porque `MediaUrlNormalizer` necesita escribir justo las filas que incumplen la regla. Si le agregás validación, el runner deja de poder hacer su trabajo.
- `MediaUrlNormalizer` (`config/`, `CommandLineRunner`, `@Order(1)`) es el runner de una pasada: gateado por `app.media.normalize.enabled` (default `false`), idempotente, y **reporta `found=N rewritten=M` en una línea de log**. No sube/borrá nada en R2: solo reescribe el string, porque lo que se guarda es `LEGACY_TRAILER_PREFIX` + la clave exacta del bucket. Si `R2_PUBLIC_BASE_URL` está vacío → log `ERROR` y skip (preferimos fallar a escribir una URL que no resuelve).
- Los efectos en borrado son **ninguno**: `deleteVideo` usa slug (no URL) y `deleteImage` solo deriva key de URLs absolutas.
- El campo `PresignedUploadResponse.publicPath` **es una URL absoluta** pese al nombre. No se renombró a propósito: hacerlo rompería el frontend viejo y obligaría a desplegar ambos a la vez.
- El frontend ya **no resuelve** rutas de media: `lib/media.ts` solo valida el invariante y lanza si recibe una ruta. `NEXT_PUBLIC_R2_PUBLIC_BASE_URL` es obsoleta.
- **Tests**: `MediaUrlTest` (contrato), `MediaUrlNormalizerIntegrationTest` (el seguro funciona), y en `GameServiceTest` los `*_whenRelativePath_shouldReject`. `application-test.yaml` define `public-base-url: https://pub-test.r2.dev`, así que los asserts usan ese dominio.
- El perfil `dev` **no** activa el runner (default `false` en `application.yaml`): en una base creada con el código actual no hay nada que normalizar.

## Auth & Security
JWT via `spring-boot-starter-oauth2-resource-server` (Nimbus) — **no jjwt**
- HMAC-SHA256: `NimbusJwtEncoder.withSecretKey(secret).algorithm(MacAlgorithm.HS256)`; `@Value("${app.jwt.secret}")`
- `Role` enum (ADMIN/VENTEDOR/CLIENTE), default CLIENTE, assigned in `UserService.create()`
- `JwtAuthenticationConverter` maps claim `"role"` → `ROLE_<role>`; tokens without claim → `ROLE_null`
- `server.port: ${PORT:9090}` and `/actuator/health` is `permitAll` (Render/Railway health checks)

Filter chain (`security/config/SecurityConfig.java`):
```
POST /api/v1/auth/logout        → authenticated (declared BEFORE the auth/** permitAll)
/api/v1/auth/**                  → permitAll
/actuator/health                 → permitAll
POST/PUT/DELETE /api/v1/games/** → ADMIN only
GET/POST /api/v1/users          → ADMIN only (exact paths; `/users/me` falls through to authenticated)
PUT /api/v1/wallet               → ADMIN only
anyRequest                        → authenticated (any role)
```

### Token revocation (M2 — token versioned, no DB per request)
- Tokens carry claim `ver` = `user.tokenVersion` (`JwtService.generateAccessToken`). `POST /api/v1/auth/logout` and `PUT /api/v1/users/password` both call `UserService` to bump `token_version` in DB **and** in `TokenVersionCache` (base package, `ConcurrentHashMap` + TTL `app.jwt.token-version-cache-ttl:60000`) → all previously issued tokens for that user become invalid immediately.
- `TokenVersionValidatingJwtAuthenticationConverter` (wraps the role converter) compares `ver` vs cached version on every authenticated request: in-memory lookup only (no DB). On cache-miss it loads the version from DB once and caches; missing `ver` claim / unknown user → treated as version 0 (preserves prior stateless contract). Mismatch → `BadCredentialsException` → 401.
- **Single-instance only**: the cache is in-JVM. Render / Railway run 1 replica → OK. If scaled to N replicas, swap `TokenVersionCache` for Redis (version is already in DB; the swap is localized).

### Google Sign-In (ID token + onboarding)
- **Flujo**: el frontend usa Google Identity Services y manda el `idToken` a `POST /api/v1/auth/google`. El backend lo valida **en local** con `GoogleIdTokenVerifier`: firma contra `https://www.googleapis.com/oauth2/v3/certs` (Nimbus cachea el JWKS, sin red por request), `iss` (`https://accounts.google.com` o `accounts.google.com`), `aud` = `GOOGLE_CLIENT_ID`, `exp` y `email_verified=true`. **No hay sesiones, cookies, `redirect_uri` ni client secret.**
- **Cuenta existente** → `200 {status:"AUTHENTICATED", token, expiresIn}`. Se busca primero por `(provider, provider_user_id)` = `sub` (el email puede cambiar) y, si no hay vínculo, por email verificado; en ese caso se crea la fila en `oauth_identity` (auto-link).
- **Cuenta nueva** → `200 {status:"ONBOARDING_REQUIRED", onboardingToken, suggestedProfile}`. **No se crea nada en la BD**: el onboarding token (HS256 nuestro, `type=onboarding`, `sub=google:<sub>`, TTL `app.oauth.onboarding.ttl-ms` = 10 min) viaja al formulario y `POST /api/v1/auth/google/complete` crea `User` + `Profile` + `Wallet` + `OAuthIdentity` en una transacción. Sin esto, el `Profile` (RUN/región/comuna/dirección NOT NULL) no se puede armar con lo que entrega Google.
- **El onboarding token no autentica requests**: el `TokenVersionValidatingJwtAuthenticationConverter` exige `type=access`. (Los access tokens ya llevaban ese claim; ahora se valida.)
- **Password nullable**: una cuenta Google tiene `password NULL`; `UserDetailsServiceImpl` la rechaza con `UsernameNotFoundException` (→ 401), nunca con un 500. `updatePassword` sigue exigiendo la password actual, así que un usuario OAuth no puede fijar una (follow-up).
- **Avatar**: el claim `picture` se guarda en `profile.avatar_image` al crear la cuenta y se refresca en cada login (`UserService.updateOAuthAvatar`). El header lo lee de `GET /profile` (campo `avatarImage`) y, si no hay foto, muestra un ícono genérico.
- **Dominio**: al **crear** cuenta se exige `EmailDomainValidator.isAllowed(email)` (`duoc.cl`, `profesor.duoc.cl`, `gmail.com`). Una cuenta existente con otro dominio puede seguir logueándose con Google.
- **Soft delete**: una cuenta borrada conserva el email (UNIQUE), así que el login con Google la rechaza con 401 en vez de fallar al insertar.
- **Rate limit**: `/auth/google` y `/auth/google/complete` usan `LoginAttemptLimiter.retryAfterSecondsForIp` / `onFailureForIp` (solo IP, porque el email recién se conoce tras validar el token). Un dominio no permitido **no** cuenta como fallo.
- **Migración**: `V1.1.0__oauth_google.sql` (`oauth_identity` + `password` nullable). `GOOGLE_CLIENT_ID` es **opcional en dev** (vacío: la app arranca y `/auth/google` responde 503 `GOOGLE_SIGN_IN_NOT_CONFIGURED`) y **obligatoria en prod** (sin default: el deploy falla al arrancar). En `test` hay un client id ficticio.

### Security hardening (OWASP Top 10 audit — 2026-08)
Implemented:
- **H1** POST `/api/v1/users` → ADMIN only (was permitAll — anyone could create an admin-role user)
- **H2** rate limiting on `/api/v1/auth/login`: `LoginAttemptLimiter` (`security/service`) counts failures per-email **and** per-IP in a `ConcurrentHashMap` with a sliding window, and `AuthController` consults it before authenticating. Tunables `app.security.login.max-per-email` (5), `max-per-ip` (20), `window-ms` (900000). **Single-instance only**, same caveat as `TokenVersionCache`: in-process state, so N replicas need Redis.
- **M1/L1/L2** `GlobalExceptionHandler` returns generic details ("Resource not found.", "Bad request.") and `sanitize()` strips `\r\n` from all logged values; `UserService.update` logs sanitized username
- **M3** optional OWASP plugin: `./mvnw verify -Pdependency-check` (`org.owasp:dependency-check-maven:12.1.0`, `failBuildOnCVSS > 7`, HTML report in `target/`)
Already solid, no action: BCrypt, HS256 Nimbus (no alg-confusion), no raw SQL/@Query, Bean Validation, SpringDoc/actuator off in prod, no SSRF surface, generic 500s

Known debt (explicitly deferred, not urgent):
- **H3** password length is `@Size(min = 4, max = 10)` in all five DTOs, via `validation/PasswordRules.MIN/MAX` (one source; mirrored by `PASSWORD_MIN/MAX` in the frontend's `schemas/password.schema.ts`). Four characters is weak for BCrypt and ten is an odd ceiling — raise both constants when you do. **Careful**: `DataInitializer` seeds the test users with `pass123` (7 chars), so a minimum above 7 locks them out of login until the seed changes too.

### Purchase idempotency (L3 — Idempotency-Key)
- `POST /api/v1/purchases` **requires** the `Idempotency-Key` header (missing → 400 via `MissingRequestHeaderException`). `PurchaseService.create(userId, key, items)` looks up `purchaseRepository.findByUser_IdAndIdempotencyKey` first and replays the existing purchase (no wallet deduction, no library re-add) on a match; otherwise creates with `idempotency_key` set.
- Guard: `purchase` has `UNIQUE (user_id, idempotency_key)` (in `V1.0.0__init_schema.sql`) — a concurrent same-key race makes the loser hit `DataIntegrityViolationException` → mapped to **409** (no double charge possible).

### BOLA conventions
- **No user IDs in paths** — self-scoped endpoints `/api/v1/wallet`, `/api/v1/profile`, `/api/v1/library`, `/api/v1/purchases`, `/api/v1/users/me`; user id comes from JWT
- Ownership enforced in controllers via `SecurityContext.getCurrentUserId()` (injected bean, not `@AuthenticationPrincipal Jwt`). `SecurityContext` lives in the BASE package (`com.app.proyectojuegosmonolito.SecurityContext`) as shared infrastructure — if it were in `security`, the account↔security cycle would break Modulith verification
- Ownership violations return **404** (not 403) to avoid leaking valid user IDs

## Architecture conventions
- **Services** receive/return entities only (`PurchaseService.create` receives `List<PurchaseLine>`, a domain record in `purchase/model`, not the HTTP DTO)
- **Controllers** receive DTOs, call mappers (`toEntityCreate`, `toLines`), call services, map responses (`toResponse`)
- Entity `update()` methods take individual primitive fields, never DTOs
- `UserService.create()` auto-creates `Profile` + `Wallet`; `PurchaseService.create()` validates balance, deducts wallet, sets `COMPLETED`, adds games to library
- `Wallet` uses `@Version` optimistic locking (migration `V1.0.0`) — concurrent stale writes throw `OptimisticLockingFailureException` → mapped to **409 Conflict** in `GlobalExceptionHandler`
- `GlobalExceptionHandler` (exception/) returns `ProblemDetail` (RFC 9457); `spring.mvc.problemdetails.enabled: true`
- Cross-module deps in `package-info.java` use Modulith named-interface syntax, e.g. `@ApplicationModule(allowedDependencies = {"account :: user-service", "game :: service", "security :: service"})`
- `ModulithTests` (root test package) enforces module boundaries via `ApplicationModules.verify()` — run it before the full suite; named interfaces require a `@NamedInterface("...")` in the sub-package's `package-info.java` (e.g. `account :: wallet-model`, `account :: profile-model`)

## Database
- Dev: `.env` (gitignored) holds `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` — the SAME three names that prod uses, so there is no translation table. Two ways they're read: `application-dev.yaml` imports `.env` via `spring.config.import` (host-run), and `compose.yaml` injects them with `env_file` (Docker). Compose interpolates them to build the Postgres container's `POSTGRES_USER`/`POSTGRES_PASSWORD`, then overrides `SPRING_DATASOURCE_URL` with the `postgres:` host, because inside the container network the host is the service name, not localhost.
- `compose.yaml`: Postgres only, host port 5432, volume `postgres-data`
- Prod DB runs on Render/Railway only. The Render dashboard IS the secrets store: 13 vars typed there, no file in the repo (a local `.env.prod` would be a second source that silently desyncs). Full list in README > Variables de entorno.
- Tables: `user` (has `role varchar(10)` + `token_version integer` + `password` **nullable** for Google-only accounts), `profile`, `wallet` (has `version bigint` for optimistic locking), `game`, `library`, `purchase` (has `idempotency_key` + `UNIQUE (user_id, idempotency_key)`), `purchase_item`, `oauth_identity` (`UNIQUE (provider, provider_user_id)` + `UNIQUE (user_id, provider)`)
- **Migrations**: `V1.0.0__init_schema.sql` holds the original schema and `V1.1.0__oauth_google.sql` adds `oauth_identity` + makes `user.password` nullable. From here on, **prod is already migrated**: never edit an applied file (Flyway checksum → app won't boot); add `V1.2.0__...`, `V1.3.0__...`, etc. For local, `docker compose down -v` gives a clean replay of all versions.

## ⚠️ CRITICAL: Reset DB + image when changing a migration or Java code

**Symptom** (what you see when this rule is broken):
- `GET http://localhost:9090/api/v1/games` (a `permitAll()` endpoint) returns **401** with header `WWW-Authenticate: Bearer resource_metadata="http://127.0.0.1:9090/.well-known/oauth-protected-resource"` — but `curl /api/v1/categories` and `curl /actuator/health` also fail
- `docker compose logs app` shows `Migration checksum mismatch for migration version 1.0.0 — Applied to database: <num>, Resolved locally: <num>`
- Or: the app starts but Hibernate logs old SQL (e.g. `insert into "user" (..., username) values ...`) → the running JAR is stale

**Cause** (why this happens):
1. You edited `V1.0.0__init_schema.sql` / `V1.1.0__oauth_google.sql` (or Java code in `src/main/java/`)
2. You ran `docker compose up -d`
3. Docker reused the **cached image** (old JAR) — `docker compose up -d` does **NOT** rebuild by default
4. On the next restart, the new JAR + new migration collide with the existing `postgres-data` volume: either the old JAR responds with a stale JWT secret / token-version → 401, or the new JAR fails Flyway validation → refuses to start

**Solution — always run together when migration or Java code changes:**

```bash
docker compose down -v          # stops + removes containers AND the postgres-data volume
docker compose build --no-cache # forces a clean image rebuild (downloads deps, recompiles, repackages)
docker compose up -d            # starts fresh: empty Postgres → Flyway applies new migration → app starts
docker compose logs -f app      # verify: "Started ProyectoJuegosMonolitoApplication" + no Flyway errors
```

**Three flags are mandatory** — missing any one recreates the bug:
| Flag | Purpose |
|------|---------|
| `-v` | Removes the `postgres-data` volume (Flyway history + old schema) |
| `--no-cache` | Rebuilds the Docker image from scratch (new JAR with your latest code) |
| `-d` | Runs in background (so you can `logs -f app` separately) |

**For Java-only changes (no migration edit)**: drop `-v` (the volume is fine), keep `--no-cache` (new JAR).

**For fast dev iteration (no Docker image rebuild at all)**: use `mvnw spring-boot:run` — only Postgres runs in Docker, the app runs locally with hot reload. This is the fastest feedback loop:

```bash
docker compose up -d postgres
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

## Testing
- All `@SpringBootTest` classes: Testcontainers (`@Import(TestcontainersConfiguration.class)` → `postgres:17-alpine`) + `@ActiveProfiles("test")`
- Docker Desktop down ⇒ every context load fails ("Could not find a valid Docker environment")
- Controller ITs must mock JWT: `SecurityMockMvcRequestPostProcessors.jwt()` with `.subject(userId)`; admin endpoints need `.jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))`
- Controller ITs are `@Transactional`, which hides lazy-loading bugs — the real code runs with `spring.jpa.open-in-view: false`, so **never access lazy associations in a mapper/controller after the service returns** without `@EntityGraph`/fetch join

## Deployment (Render/Railway-ready)
- `Dockerfile`: multi-stage — build `maven:3.9-eclipse-temurin-25`, runtime `eclipse-temurin:25-jre`, `ENTRYPOINT ["java", "-jar", "app.jar"]`
- `.dockerignore` excludes `target/`, `.idea`, `.git`, `.env*`
- `server.port: ${PORT:9090}` and Render injects `PORT`, so the Dockerfile's `EXPOSE 8080` is cosmetic — never hardcode a port in the image
- `.dockerignore` excludes `target/`, `.idea`, `.git`, `.env*`
- Cloud env vars: 13 in the Render dashboard, full table in README > Variables de entorno. `SPRING_PROFILES_ACTIVE=prod`, `APP_JWT_SECRET`, `APP_JWT_EXPIRATION`, `APP_CORS_ALLOWED_ORIGINS`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `CLOUDINARY_URL`, the 4 `R2_*`, `APP_SEED_ENABLED`
- Use Render's **Internal DB URL** (both services on Render, same region) → plain `jdbc:postgresql://<host>:5432/<db>`, no `?sslmode=require`. Only the *external* URL needs SSL, and using it just to reach your own DB adds latency and a hop through the public LB.
