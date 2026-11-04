# Plan — OAuth con Google (Google Identity Services + onboarding)

> Estado: aprobado e implementado en la rama `feature/OAuth`.
> Última actualización: 2026-11-03.

## Decisiones

| Tema | Decisión |
|---|---|
| Flujo | **ID token** de Google Identity Services validado en el backend contra el JWKS de Google. Sin sesiones, sin cookies, sin `redirect_uri`, sin client secret. |
| Alcance | Login **y** registro. Una cuenta nueva completa RUN/región/comuna/dirección en un segundo paso antes de recibir el JWT definitivo. |
| Dominios | Solo `duoc.cl`, `profesor.duoc.cl` y `gmail.com`, igual que `EmailDomainValidator`. La restricción aplica **solo al crear** cuentas nuevas. |
| Migración | Archivo nuevo `V1.1.0__oauth_google.sql` (no se toca `V1.0.0`, que ya está aplicado en producción). |
| Identidad | Tabla `oauth_identity`; se busca por `(provider, provider_user_id)` = `sub` de Google, nunca por email. Si no hay match, se vincula por email verificado. |

## Contrato de API

### `POST /api/v1/auth/google`

```json
{ "idToken": "<credential de Google Identity Services>" }
```

Cuenta existente → `200`

```json
{ "status": "AUTHENTICATED", "token": "<jwt>", "expiresIn": 3600000 }
```

Cuenta nueva → `200`

```json
{
  "status": "ONBOARDING_REQUIRED",
  "onboardingToken": "<jwt 10 min>",
  "suggestedProfile": {
    "email": "nico@gmail.com",
    "firstName": "Nicolás",
    "lastName": "Beats",
    "picture": "https://lh3.googleusercontent.com/..."
  }
}
```

### `POST /api/v1/auth/google/complete`

```json
{
  "onboardingToken": "<jwt 10 min>",
  "run": "19011022K",
  "nickname": "nicobeats",
  "birthDate": "1999-04-12",
  "comunaId": 13101,
  "address": "Av. Siempre Viva 742"
}
```

→ `201` `{ "token": "<jwt>", "expiresIn": 3600000 }`

### Errores

| Código | Caso |
|---|---|
| `401` | ID token inválido/expirado, `email_verified != true`, cuenta soft-deleted, onboarding token inválido/expirado |
| `403` | `EMAIL_DOMAIN_NOT_ALLOWED` (solo en altas) |
| `409` | RUN o nickname duplicado al completar el onboarding |
| `429` | Rate limit por IP |

## Esquema (`V1.1.0__oauth_google.sql`)

```sql
ALTER TABLE "public"."user" ALTER COLUMN "password" DROP NOT NULL;

CREATE TABLE "public"."oauth_identity" (
    "id"                bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    "user_id"           bigint NOT NULL,
    "provider"          varchar(20) NOT NULL,
    "provider_user_id"  varchar(255) NOT NULL,
    "email_at_provider" varchar(100),
    "linked_at"         timestamp NOT NULL,
    PRIMARY KEY ("id"),
    UNIQUE ("provider", "provider_user_id"),
    UNIQUE ("user_id", "provider")
);

ALTER TABLE "public"."oauth_identity" ADD CONSTRAINT "fk_oauth_identity_user_id_user_id"
    FOREIGN KEY ("user_id") REFERENCES "public"."user"("id");
```

## Archivos

### Nuevos

- `security/oauth/GoogleIdTokenVerifier.java` — valida firma (JWKS cacheado), `iss`, `aud`, `exp` y `email_verified`.
- `security/oauth/InvalidGoogleTokenException.java`
- `security/service/OnboardingTokenService.java`
- `security/dto/GoogleAuthRequest.java`
- `security/dto/GoogleAuthResponse.java`
- `security/dto/GoogleCompleteRequest.java`
- `account/user/model/OAuthIdentity.java`
- `account/user/repository/OAuthIdentityRepository.java`
- `account/user/model/GoogleIdentity.java`
- `account/user/model/GoogleSignup.java`
- `src/main/resources/db/migration/V1.1.0__oauth_google.sql`

### Modificados

- `security/controller/AuthController.java`
- `security/service/AuthService.java`
- `security/service/JwtService.java`
- `security/config/SecurityConfig.java`
- `security/config/TokenVersionValidatingJwtAuthenticationConverter.java`
- `security/service/LoginAttemptLimiter.java`
- `account/user/service/UserService.java`
- `account/user/service/UserDetailsServiceImpl.java`
- `exception/GlobalExceptionHandler.java`
- `application*.yaml`
- `README.md`, `AGENTS.md`

## Configuración

```yaml
app:
  oauth:
    google:
      client-id: ${GOOGLE_CLIENT_ID:}
      jwk-set-uri: https://www.googleapis.com/oauth2/v3/certs
    onboarding:
      ttl-ms: 600000
```

`application-prod.yaml` declara `${GOOGLE_CLIENT_ID}` sin default: en prod la app no arranca si falta.
En `dev` la variable es opcional: si está vacía la app arranca y `/auth/google` responde **503 `GOOGLE_SIGN_IN_NOT_CONFIGURED`**.
`application-test.yaml` usa un client id ficticio.
**Google Cloud Console:** OAuth Client ID tipo *Web application*, orígenes JS `http://localhost:3000` y el dominio de producción. Sin redirect URI ni client secret.

## Seguridad

- `aud` contra `GOOGLE_CLIENT_ID` (impide replay de tokens emitidos para otra app).
- `iss` acepta `https://accounts.google.com` y `accounts.google.com`.
- `email_verified == true` obligatorio.
- Dominio permitido solo al crear cuenta.
- `sub` como clave; el email puede cambiar.
- El onboarding token no sirve como access token: `type=onboarding`, `sub` no numérico y validación `type=access` en el converter.
- Nunca se loguea el `idToken`.
- Rate limit por IP en ambos endpoints.
- El rol de una cuenta Google es siempre `CLIENTE`.

## Tests

- `GoogleIdTokenVerifierTest` — unitario, sin Docker: par RSA local + JWKS fijo. Casos: válido, `aud` erróneo, `iss` erróneo, expirado, `email_verified=false`, sin email.
- `GoogleAuthControllerIntegrationTest` — Testcontainers: cuenta existente, cuenta nueva, onboarding completo, RUN duplicado, onboarding expirado, onboarding usado como Bearer, dominio no permitido, usuario borrado.
- `GoogleSignInDisabledIntegrationTest` — Testcontainers: con `GOOGLE_CLIENT_ID` vacío la app arranca y `/auth/google` responde 503.
- `ModulithTests` — el flujo no cruza repositorios entre módulos.

## Riesgos

- Cargar `GOOGLE_CLIENT_ID` en Render **antes** de desplegar o el arranque falla por diseño (en dev es opcional y degrada a 503).
- Las cuentas existentes con dominio fuera de la whitelist no se bloquean.
- Un usuario OAuth no puede usar `PUT /users/password` (follow-up: definir contraseña inicial cuando `password IS NULL`).
- Rate limiter y `TokenVersionCache` siguen siendo en memoria (single-instance).

## Estado de implementación

Implementado en la rama `feature/OAuth`:

- `GoogleIdTokenVerifier` + decoder JWKS con `@Qualifier`/`@Primary` (sin dependencias nuevas).
- `POST /api/v1/auth/google` y `POST /api/v1/auth/google/complete`.
- Token de onboarding `type=onboarding` y chequeo `type=access` en el converter.
- `oauth_identity` + `user.password` nullable + guardia en `UserDetailsServiceImpl`.
- Auto-link por email verificado, rechazo de cuentas soft-deleted y de dominios no permitidos.
- Rate limit por IP en ambos endpoints.
- `EmailDomainValidator.isAllowed` como fuente única de la whitelist.
- Tests: `GoogleIdTokenVerifierTest` (10), `AuthServiceGoogleTest` (6), `GoogleAuthControllerIntegrationTest` (9, Testcontainers), `GoogleSignInDisabledIntegrationTest` (1, Testcontainers) + `ModulithTests` verde.
- Se arregló el código de test preexistente que no compilaba (`UserFixtures`, `ProfilePatchRequest`/`ProfileResponse`/`UserRequestCreate`, `ProfileServiceTest`, `LibraryServiceTest`) y se agregó `src/test/resources/test-data.sql` con el par región/comuna que usan los fixtures.
- Se declaró `account :: profile-repository` como named interface para que `security`/`config` no violen Modulith.

Pendiente / no verificable en este entorno:

- Los tests de Testcontainers requieren Docker; no se pudieron ejecutar acá.
- `GOOGLE_CLIENT_ID` es opcional en `.env` (dev): sin ella la app arranca y `/auth/google` responde 503. En Render (prod) es obligatoria **antes** de desplegar.
- El frontend (repo aparte) debe sumar el botón de Google Identity Services y la pantalla de onboarding.

