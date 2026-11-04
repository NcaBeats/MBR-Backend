# Proyecto Juegos Monolito

Backend Spring Boot de una tienda de videojuegos con arquitectura modular (Spring Modulith).

## Requisitos

- **Docker Desktop** (opcionales Docker y los tests con Testcontainers)
- **Java 25** (solo si NO usás Docker)

## Cómo levantar el proyecto

### Opción 1: Solo Docker (recomendada para evaluación)

```bash
# 1. Clonar el repositorio
git clone <url-del-repositorio>
cd Proyecto-Juegos-Monolito

# 2. Crear el .env (compose.yaml lo usa como env_file y exige la password de la DB)
cp .env.example .env
# editar .env: SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD y APP_JWT_SECRET
# GOOGLE_CLIENT_ID es opcional acá (ver "Google Sign-In")

# 3. Levantar todo (Postgres + App)
docker compose up -d

# 4. Ver logs
docker compose logs -f app
```

La app arranca en `http://localhost:9090`. **No necesita Java instalado.**

## ⚠️ Si modificás el código o la migración, ejecutá este flujo

```bash
docker compose down -v          # detiene los contenedores Y borra el volumen postgres-data
docker compose build --no-cache # rebuildea la imagen Docker desde cero (nuevo JAR)
docker compose up -d            # arranca con DB limpia + nuevo código
docker compose logs -f app      # verificá: "Started ProyectoJuegosMonolitoApplication" + sin errores Flyway
```

**Los 3 flags son obligatorios:**

| Flag | Propósito |
|------|-----------|
| `-v` | Borra el volumen `postgres-data` (historial Flyway + schema viejo) |
| `--no-cache` | Reconstruye la imagen Docker desde cero (nuevo JAR con tu código) |
| `-d` | Corre en background (para poder hacer `logs -f app` aparte) |

**Si te falta cualquiera de los 3, el bug vuelve:** vas a ver 401 en endpoints públicos (`permitAll()`) o `Migration checksum mismatch` de Flyway. Eso pasa porque Docker reusa la imagen vieja y el JAR desactualizado choca con el schema nuevo (o con el schema viejo si solo cambiaste código).

**Si solo cambiaste código Java (sin tocar la migración):** se puede sacar `-v` pero mantener `--no-cache` (necesitás el JAR nuevo).

**Para iterar rápido sin Docker:** usar `mvnw spring-boot:run` con solo Postgres en Docker — hot reload instantáneo:

```bash
docker compose up -d postgres
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### Opción 2: Docker + Java en PC (desarrollo)

```bash
# 1. Clonar el repositorio
git clone <url-del-repositorio>
cd Proyecto-Juegos-Monolito

# 2. Levantar solo PostgreSQL
docker compose up -d postgres

# 3. Compilar y ejecutar la aplicación
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

La app arranca en `http://localhost:9090` (configurable con la env var `PORT`).
**Necesita Java 25 en la PC.**

**Configuración:** todas las variables viven en `.env` (gitignored). Copiar `.env.example`
y completarlo: ahí está documentada cada variable, qué hace y de dónde sale su valor
en producción.

## Variables de entorno

Los nombres son **los mismos** en local y en producción; lo que cambia es el valor. El prefijo
indica quién es el dueño: `SPRING_*` el framework, `APP_*` la aplicación, y sin prefijo un
proveedor externo.

### Desarrollo local — `.env` (12 variables)

| Variable | Para qué |
|----------|----------|
| `SPRING_DATASOURCE_URL` | Conexión a Postgres. Local: tu máquina. Docker: `compose.yaml` la sobrescribe con el host `postgres` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de Postgres |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de Postgres |
| `APP_JWT_SECRET` | Clave de firma de los JWT (HS256, mínimo 256 bits) |
| `APP_JWT_EXPIRATION` | Vigencia del token en ms (default `3600000`) |
| `GOOGLE_CLIENT_ID` | Client ID público del OAuth Client de Google (validación del claim `aud`). **Opcional en local**: sin él la app arranca y `/auth/google` responde 503 |
| `R2_ACCOUNT_ID` | Cuenta de Cloudflare R2 (trailers) |
| `R2_ACCESS_KEY_ID` | Access Key de R2 |
| `R2_SECRET_ACCESS_KEY` | Secret Key de R2 |
| `R2_BUCKET` | Bucket de R2 (default `trailers`) |
| `APP_MEDIA_NORMALIZE_ENABLED` | Runner one-shot que reescribe URLs legacy a absolutas (default `false`) |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes del navegador permitidos: separados por comas y **sin barra final** |

Tres variables **no** van en `.env` porque no son configuración sino banderas de arranque:
`SPRING_PROFILES_ACTIVE` (la pone `compose.yaml` o el plugin de Maven), `APP_SEED_ENABLED`
(el perfil `dev` lo fuerza a `true`) y `R2_PUBLIC_BASE_URL` (vive como default en
`application.yaml`, porque no es secreta ni cambia entre entornos).

`APP_CORS_ALLOWED_ORIGINS` acepta una lista explícita, nunca `*`: `allowCredentials` está
activo y el navegador rechaza esa combinación.

**Ejemplo recomendado para `.env`:**
```properties
APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000,http://192.168.100.64:3000,https://steam-clone-*.vercel.app
```

- `http://localhost:3000` / `http://127.0.0.1:3000` → front en tu PC
- `http://192.168.100.64:3000` → front desde otro dispositivo en tu LAN (IP de tu Wi-Fi, cámbiala por la tuya)
- `https://steam-clone-*.vercel.app` → previews de Vercel (`*` matchea `mi-rama-steam-clone-abc123.vercel.app`); si tu dominio de prod no calza, agrégalo explícito

**Importante:** cambia `APP_CORS_ALLOWED_ORIGINS` requiere **restart** del backend (se lee al arrancar). Si ves error `CORS` / `Access-Control-Allow-Origin` en consola del navegador, falta el origin exacto que muestra el error — cópialo tal cual a la lista.

### Producción — panel de Render (14 variables)

El panel de Render **es** el almacén de secretos: los valores de producción no van en ningún
archivo del repo. De las 12 de `.env`, solo 2 conservan el mismo valor
(`APP_JWT_EXPIRATION` y `R2_BUCKET`); el resto cambia o depende del entorno
(`GOOGLE_CLIENT_ID` es público y puede ser el mismo client si registrás el dominio de prod
como origen autorizado, y `R2_ACCOUNT_ID` solo cambia si la rotación de keys fue a otra cuenta).

| Variable | De dónde sale el valor |
|----------|----------------------|
| `SPRING_PROFILES_ACTIVE` | Literal `prod` |
| `SPRING_DATASOURCE_URL` | Panel de la DB → *Internal DB URL*, en formato JDBC |
| `SPRING_DATASOURCE_USERNAME` | Panel de la DB |
| `SPRING_DATASOURCE_PASSWORD` | Panel de la DB. **No reutilizar la de local** |
| `APP_JWT_SECRET` | Generado por vos. **No reutilizar la de local** |
| `APP_JWT_EXPIRATION` | `3600000` |
| `GOOGLE_CLIENT_ID` | Google Cloud Console. Público; puede ser el mismo client con el dominio de prod como origen autorizado |
| `APP_CORS_ALLOWED_ORIGINS` | Literal: el dominio de Vercel |
| `APP_SEED_ENABLED` | `true` siembra catálogo y admin de arranque; `false` deja la DB vacía |
| `R2_ACCOUNT_ID` | Dashboard de Cloudflare. Igual salvo que la rotación fuera a otra cuenta |
| `R2_ACCESS_KEY_ID` | Dashboard de Cloudflare. Nuevo junto con el secret |
| `R2_SECRET_ACCESS_KEY` | Dashboard de Cloudflare. **No reutilizar la de local** |
| `R2_BUCKET` | `trailers` (tiene default en el código) |
| `CLOUDINARY_URL` | Obsoleta: se puede borrar (toda la media vive en R2) |

Las cuatro marcadas son secretos: **no pueden reutilizar el valor de local**. Si se filtran
las claves de esta máquina, quien las tenga puede firmar tokens de ADMIN, entrar a la base o
borrar la media de producción.

`R2_PUBLIC_BASE_URL` **no hace falta declararla**: el default de `application.yaml` ya apunta al
bucket de producción, así que las URLs públicas se resuelven solas.

Dos variables del panel quedaron obsoletas y se pueden borrar: `APP_MIGRATE_IMAGES` (su runner ya
no existe; lo reemplaza `APP_MEDIA_NORMALIZE_ENABLED`) y `CLOUDINARY_URL` (toda la media vive en R2).

`application-prod.yaml` declara el datasource, el JWT y el CORS **sin default**, así que una
variable faltante hace fallar el arranque con un mensaje que la nombra. En cambio
las de R2 tienen default vacío: si faltan, la app **arranca igual** y
falla recién al subir el primer archivo. Revisar los logs del primer deploy, no solo el health check.

Con `APP_SEED_ENABLED=true` el seed corre **una sola vez**: `DataInitializer` está guardado
con `if (gameService.count() == 0)`, así que reiniciar el contenedor no lo vuelve a ejecutar
ni choca con el `UNIQUE` del email. Es seguro dejarlo activo en Render.

El admin de arranque (`player1@gmail.com` / `pass123` hardcodeados en `DataInitializer.java`)
es público en el repo. **Cambiar esa contraseña apenas el primer deploy esté arriba**,
antes de compartir la URL. El endpoint `PUT /api/v1/users/password`
también invalida los tokens ya emitidos de ese usuario.

### Seed remoto a producción (cuando la DB ya tiene datos)

El seed automático solo corre en bases **vacías** (`count() == 0`). Para poblar prod con GTA VI y demases sin migrar la DB:

1. En tu PC, en la carpeta del proyecto, ejecutar la app local apuntando a la DB de prod:
```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://dpg-db61etid0e5s7391srk0-a.oregon-postgres.render.com:5432/postgres_steam_clone_veyu?sslmode=require"
$env:SPRING_DATASOURCE_USERNAME="postgres_steam_clone_veyu_user"
$env:SPRING_DATASOURCE_PASSWORD="ZdRTfMu0RDkVXVkEL74yNwiXdcy5epnM"
$env:APP_SEED_ENABLED="true"
./mvnw spring-boot:run
```
(Limpiar antes `R2_ACCESS_KEY_ID`/`R2_SECRET_ACCESS_KEY` del shell si existen).

2. Esperar `Started` + fin de inserts (varios minutos). Ctrl+C para apagar. **Una sola vez** (el seed tiene guard `count() == 0`; vendors son idempotentes).

3. Verificar en prod: `GET /games?name=grand theft auto vi` → 1 juego, `GET /regions` → 16, login `player1@gmail.com` / `pass123` → **cambiar la password**.

4. En Render: `APP_SEED_ENABLED=false` + restart (higiene: admin de arranque con password conocido = riesgo).

La app de prod (`APP_SEED_ENABLED=false`) permanece inalterada. Solo tu PC hace el trabajo pesado; prod sigue leyendo.

## Media: Cloudflare R2 (todas las imágenes y trailers)

Todo el media del catálogo vive en el bucket R2 **`trailers`**, con claves agrupadas por el **slug
canonical** del juego (derivado del nombre, p. ej. *Assassin's Creed Shadows* → `assassin-s-creed-shadows`):

| Asset | Key |
|-------|-----|
| Card de juego | `{slug}/card/{archivo}` |
| Banner | `{slug}/banner/{archivo}` |
| Galería | `{slug}/gallery/{archivo}` |
| Trailer | `{slug}/trailer.mp4` |
| Portada de blog | `blogs/{slug}/cover.{ext}` |

La URL pública se resuelve contra `R2_PUBLIC_BASE_URL` (default en `application.yaml`, no va en `.env`).
Los uploads **no pasan por el servidor**: el backend **presignea** la URL y el navegador sube directo a R2
(presign de imagen `POST /api/v1/games/media/image/presign` y de video `POST /api/v1/games/media/video/presign`).
El frontend guarda la `publicPath` resultante —que es la URL pública absoluta, pese al nombre histórico
del campo—.

### Forma de las URLs de media

**Todas las columnas de media son URLs absolutas**: `game.video_url`, `game.image_url`,
`game.banner_url` y `game_image.url`. No hay rutas relativas.

El contrato vive en `game/model/MediaUrl.java` y `GameService` lo valida en los diez caminos de
escritura (alta por JSON y por multipart, edición, banner, galería). Un valor null o vacío sigue
siendo válido y significa "no cambiar". Una ruta relativa se rechaza con 400: el origen del
problema no eran los datos, sino que la forma nunca se validaba y dos caminos de escritura
producían convenciones distintas.

### Runner de normalización (`MediaUrlNormalizer`)

Componente en `config/` — se activa con `app.media.normalize.enabled` (por defecto `false`) y
reescribe las filas que guardan la ruta legacy del trailer (`/uploads/games/{slug}/trailer.mp4`)
a su URL pública. Es **idempotente** y **no toca R2**: solo reescribe el string, porque lo que se
guarda es el prefijo seguido de la clave exacta dentro del bucket.

Es un seguro, no una migración pendiente: el servicio ya no emite rutas, así que en una base creada
con el código actual no hay nada que hacer. Su valor real es twofold: dejar los datos antiguos en
la forma nueva, y **reportar** cuántas filas encontró y reescribió.

#### Guía: correrlo en producción (una sola vez)

1. En el panel de Render agregá `APP_MEDIA_NORMALIZE_ENABLED=true`.
2. Deploy (Manual Deploy → *Deploy latest image*, o un push). El runner corre al arrancar.
3. Leé la línea de log:
   ```text
   MediaUrlNormalizer: found=0 rewritten=0 (video=0 image=0 banner=0 gallery=0)
   ```
   Si `found` y `rewritten` son 0, la base ya estaba consistente (lo esperable: los 45 juegos del
   seed guardan URLs absolutas). Un número mayor indica datos viejos que se acaba de corregir.
4. **Quitá la variable** del panel y re-deployá para que no vuelva a correr en cada reinicio.
5. Para confirmar de forma independiente:
   ```sql
   -- Debe devolver 0
   SELECT count(*) FROM game
   WHERE video_url LIKE '/uploads/games/%'
      OR image_url LIKE '/uploads/games/%'
      OR banner_url LIKE '/uploads/games/%';
   SELECT count(*) FROM game_image WHERE url LIKE '/uploads/games/%';
   ```

Cuando `R2_PUBLIC_BASE_URL` está vacío el runner no hace nada y lo reporta en `ERROR`: antes
que escribir una URL que no resuelve, prefiere fallar.

<details>
<summary>Runner anterior (<code>ImageMigrationRunner</code>) — retirado</summary>

Eliminado del código. Migraba imágenes residuales de Cloudinary al bucket de R2 y
consolidaba trailers en slugs antiguos (ejecutado el 2026-09-30). Su bandera `APP_MIGRATE_IMAGES`
queda sin uso en el panel de Render.
</details>

**Si un arranque local devuelve `401 Unauthorized` contra R2**, una variable de entorno del SO
(`R2_ACCESS_KEY_ID`/`R2_SECRET_ACCESS_KEY`) está pisando a `.env`: Spring resuelve las env vars con
prioridad sobre el import de `.env`. Limpiarla antes de levantar:
```powershell
Remove-Item Env:R2_ACCESS_KEY_ID, Env:R2_SECRET_ACCESS_KEY
> ./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
> ```

## Tests

Los tests de integración usan **Testcontainers** (levantan un Postgres en Docker sobre la marcha).
Requisito: **Docker Desktop corriendo**. No hace falta definir ninguna variable: el perfil `test`
trae valores ficticios para JWT y R2, y el datasource lo construye el contenedor.

```bash
# PowerShell
.\mvnw.cmd clean test

# Bash / Linux / macOS
./mvnw clean test
```

**Nota:** el contexto Spring se comparte entre clases de test (mismo Postgres de Testcontainers).
Los tests no transaccionales persisten filas (p. ej. usuarios con `user@test.com`); por eso usan
emails únicos. Si un caso falla por "email ya existente", es un resto de una corrida anterior:
reiniciar el daemon de Docker o borrar el volumen del contenedor de test.

**Lección: `@EntityGraph` evita `LazyInitializationException` en tests `@Transactional`**

`ProfileService.findByUserId` usa `@EntityGraph(attributePaths = {"region", "comuna"})`
para traer las relaciones eager. Los tests `@Transactional` ocultan el bug (la sesión sigue viva
tras el service), pero en runtime real (`spring.jpa.open-in-view: false`) el mapper
accede a `region.getNombre()` sin sesión → `LazyInitializationException`. Solución:
`Repository` con `@EntityGraph(attributePaths = {"region", "comuna"})` en el método `findById`
y servicio **no transaccional** para lecturas. Los tests de integración ahora usan
`OpenInViewDisabledIntegrationTest` para atrapar esto.

## Soft delete de usuarios

Los usuarios **no se borran físicamente**: `DELETE /api/v1/users/{id}` y `DELETE /api/v1/users`
hacen un *soft delete* (marcan `deleted_at` y revocan los tokens del usuario).

- El **email queda bloqueado**: como el constraint `UNIQUE` sigue vigente, no se puede
  registrar ni reasignar el email de un usuario eliminado.
- Los usuarios borrados **desaparecen** de listados, búsquedas y login (filtro `deleted_at IS NULL`).
- **No se puede eliminar una cuenta `ADMIN`** (devuelve error).
- Los juegos sí se eliminan físicamente (`DELETE /api/v1/games/{id}`), sujeto a integridad
  referencial (no se puede borrar un juego con compras/biblioteca asociadas).

## Datos de prueba

El sistema carga automáticamente **45 juegos, 9 categorías, 3 usuarios base y 24 estudios (`VENDEDOR`)**
al primer arranque con la base vacía.

### Usuarios

| Email | Contraseña | Rol | Wallet |
|-------|-----------|-----|--------|
| `player1@gmail.com` | `pass123` | ADMIN | $200 |
| `player2@gmail.com` | `pass123` | CLIENTE | $50 |
| `broke@gmail.com` | `pass123` | CLIENTE | $0 |

Además se crean 24 estudios con rol `VENDEDOR` (p. ej. `ubisoft@gmail.com`, `microsoft@gmail.com`,
`valve@gmail.com`, …) asociados a los juegos como "seller".

### Roles

| Rol | Descripción |
|-----|-------------|
| `ADMIN` | Acceso total al sistema |
| `VENDEDOR` | Visualiza productos y órdenes (solo lectura) |
| `CLIENTE` | Solo accede a la tienda |

### Categorías

Action, Adventure, RPG, Shooter, Fighting, Open World, Horror, Simulation, Racing.

### Juegos de ejemplo (con precio y descuento)

| Juego | Precio original | Descuento | Precio final |
|-------|----------------|-----------|--------------|
| Minecraft | $29.99 | 0% | $29.99 |
| Stardew Valley | $14.99 | 0% | $14.99 |
| Elden Ring | $59.99 | 0% | $59.99 |
| Rocket League | $0.00 | 0% | $0.00 |
| Spider-Man Remastered | $59.99 | 40% | $35.99 |
| Grand Theft Auto V | $29.99 | 50% | $15.00 |
| **Grand Theft Auto VI** | **$79.99** | **0%** | **$79.99** |
| Battlefield 1 | $59.99 | 75% | $15.00 |
| Assassin's Creed Shadows | $69.99 | 20% | $55.99 |
| Cyberpunk 2077 | $59.99 | 60% | $24.00 |
| Dragon Ball FighterZ | $59.99 | 70% | $18.00 |
| Mortal Kombat 11 | $49.99 | 80% | $10.00 |
| Red Dead Redemption 2 | $59.99 | 67% | $19.80 |
| The Witcher 3 | $39.99 | 85% | $6.00 |
| Halo Infinite | $59.99 | 50% | $30.00 |
| Forza Horizon 4 | $59.99 | 60% | $24.00 |
| Civilization VI | $59.99 | 75% | $15.00 |

Cada juego incluye una **descripción detallada** y **especificaciones de PC** (mínimas y recomendadas) en formato JSON con campos como `os`, `processor`, `memory`, `graphics` y `storage`.

## Endpoints principales

### Auth
- `POST /api/v1/auth/login` — Iniciar sesión
- `POST /api/v1/auth/register` — Registrar usuario
- `POST /api/v1/auth/google` — Login/registro con Google. Recibe `{ "idToken": "<credential>" }`; responde `AUTHENTICATED` (cuenta existente) u `ONBOARDING_REQUIRED` (cuenta nueva + `onboardingToken` de 10 min)
- `POST /api/v1/auth/google/complete` — Completa RUN/región/comuna del alta con Google y recién ahí crea la cuenta
- `POST /api/v1/auth/logout` — Cerrar sesión

#### Google Sign-In (ID token)

El frontend usa Google Identity Services y manda el **ID token** a `POST /api/v1/auth/google`; el
backend valida firma (JWKS de Google), `iss`, `aud`, `exp` y `email_verified`. No hay sesiones,
cookies, `redirect_uri` ni client secret.

1. Cuenta existente (por `sub` de Google o por email verificado) → `200 { status: "AUTHENTICATED", token, expiresIn }`.
2. Cuenta nueva → `200 { status: "ONBOARDING_REQUIRED", onboardingToken, suggestedProfile }`. **No se crea nada en la BD todavía**.
3. El front muestra el formulario (RUN, nickname, región/comuna, dirección) y llama
   `POST /api/v1/auth/google/complete` con el `onboardingToken` → `201 { token, expiresIn }`.

Configuración en Google Cloud Console: crear un **OAuth Client ID** de tipo *Web application* y
agregar a *Authorized JavaScript origins* `http://localhost:3000`, `http://127.0.0.1:3000` y el
dominio de producción (sin barra final). No hace falta redirect URI. El Client ID va en
`GOOGLE_CLIENT_ID` (backend) y en la variable pública del frontend, y **ambos deben tener el
mismo valor por entorno**.

Si `GOOGLE_CLIENT_ID` está vacío (el caso por defecto en local), la app arranca normalmente y
solo `POST /api/v1/auth/google` responde `503` con `code: GOOGLE_SIGN_IN_NOT_CONFIGURED`.
En producción la variable es obligatoria: el arranque falla si falta.

### Games
- `GET /api/v1/games` — Listar juegos (paginado). Filtros opcionales y combinables: `?category=<nombre>`, `?minPrice=<número>`, `?maxPrice=<número>`, `?state=AVAILABLE|COMING_SOON|DISCONTINUED`, `?name=<texto>`. El filtrado por precio usa el **precio vigente** (post-descuento), no el original. Orden con `?sort=<prop>,<asc|desc>` (`price` = precio efectivo post-descuento, `name`, `originalPrice`, `discountPercent`, `createdAt`, `launchDate`, `state`, `id`); sin `sort`, `createdAt DESC`
- `GET /api/v1/games/{id}` — Detalle de un juego
- `GET /api/v1/games/discounted` — Juegos en descuento
- `GET /api/v1/games/banners` — Juegos con banner
- `GET /api/v1/games/stats` — Estadísticas (total, activos, valor del catálogo)
- `POST /api/v1/games` — Crear juego (ADMIN)
- `PUT /api/v1/games/{id}` — Actualizar juego (ADMIN)
- `PUT /api/v1/games/{id}/video` — Subir/actualizar trailer (ADMIN, se guarda en R2)
- `POST /api/v1/games/media/video/presign` — URL firmada para subir el trailer directo a R2 (ADMIN)
- `POST /api/v1/games/media/image/presign` — URL firmada para subir una imagen (`{slug}/card|banner|gallery/{uuid}.{ext}`) directo a R2 (ADMIN)
- `DELETE /api/v1/games/{id}` — Eliminar juego (ADMIN)
- `POST /api/v1/games/{id}/image` — Subir imagen de portada (ADMIN)
- `POST /api/v1/games/{id}/banner` — Subir imagen de banner (ADMIN)

**Ejemplo de filtro combinado:**
```bash
curl "http://localhost:9090/api/v1/games?category=Action&minPrice=10&maxPrice=50" \
  -H "Authorization: Bearer <token>"
```

### Categories
- `GET /api/v1/categories` — Listar categorías (default `name ASC`)
- `GET /api/v1/categories/{id}` — Detalle de una categoría
- `POST /api/v1/categories` — Crear categoría (ADMIN)
- `PUT /api/v1/categories/{id}` — Actualizar categoría (ADMIN)
- `DELETE /api/v1/categories/{id}` — Eliminar categoría (ADMIN)

### Manage (ADMIN / VENDEDOR)
- `GET /api/v1/manage/games` — Listar juegos gestionables. Filtros opcionales y combinables `?name=<texto>`, `?state=AVAILABLE|COMING_SOON|DISCONTINUED`, y orden `?sort=price,asc|desc` (precio efectivo)
- `GET /api/v1/manage/games/{id}` — Detalle gestionable de un juego
- `GET /api/v1/manage/orders` — Listar órdenes
- `GET /api/v1/manage/orders/{id}` — Detalle de una orden

### Blogs
- `GET /api/v1/blogs` — Listar publicaciones del blog. Filtro opcional `?q=<texto>` (busca en título, resumen y contenido)
- `GET /api/v1/blogs/{id}` — Detalle de una publicación
- `POST /api/v1/blogs` — Crear publicación (ADMIN)
- `PUT /api/v1/blogs/{id}` — Actualizar publicación (ADMIN)
- `DELETE /api/v1/blogs/{id}` — Eliminar publicación (ADMIN)
- `POST /api/v1/blogs/media/cover/presign` — URL firmada para subir la portada directo a R2 (ADMIN)
- `POST /api/v1/blogs/{id}/cover` — Subir portada vía multipart (ADMIN)

**Campos del blog:** `title` (máx 200), `excerpt` (máx 500), `content` (texto libre), `coverImage` (URL absoluta opcional), `category` (enum: `EVENTS`, `INDUSTRY`, `NEWS`, `REVIEWS`, `GUIDES`, `INTERVIEWS` — **solo mayúsculas**, case-sensitive). `coverImage` nulo/vacío en `PUT` = no cambiar. Subida R2: `POST /media/cover/presign` + `PUT` a la URL firmada con el `contentType` exacto, o `POST /{id}/cover` multipart.

### Users
- `GET /api/v1/users` — Listar usuarios (ADMIN)
- `GET /api/v1/users/{id}` — Detalle de un usuario (ADMIN)
- `PUT /api/v1/users/{id}` — Actualizar email/rol/contraseña (ADMIN)
- `DELETE /api/v1/users/{id}` — Eliminar usuario (ADMIN, **soft delete**)
- `POST /api/v1/users` — Crear usuario (ADMIN)
- `GET /api/v1/users/me` — Mi perfil
- `PUT /api/v1/users` — Actualizar mi usuario
- `PUT /api/v1/users/password` — Cambiar contraseña
- `DELETE /api/v1/users` — Eliminar mi cuenta (**soft delete**)

### Wallet
- `GET /api/v1/wallet` — Ver mi saldo
- `PUT /api/v1/wallet` — Establecer saldo (ADMIN)
- `POST /api/v1/wallet/deposit` — Depositar fondos en mi wallet

### Purchases
- `POST /api/v1/purchases` — Comprar juegos (requiere `Idempotency-Key` header)
- `GET /api/v1/purchases` — Ver mi historial de compras
- `GET /api/v1/purchases/{id}` — Detalle de una compra (ítems ordenados por `id`)

### Library
- `GET /api/v1/library` — Ver mi biblioteca de juegos comprados (default `acquiredAt DESC`: lo último adquirido primero)
- `POST /api/v1/library` — Agregar un juego a la biblioteca
- `DELETE /api/v1/library/game/{gameId}` — Quitar un juego de la biblioteca
- `DELETE /api/v1/library` — Vaciar la biblioteca

### Profiles
- `GET /api/v1/profile` — Ver mi perfil
- `GET /api/v1/profile/{userId}` — Ver perfil público de otro usuario
- `PATCH /api/v1/profile` — Actualizar mi perfil
- `PATCH /api/v1/profile/{userId}` — Actualizar perfil de otro usuario (ADMIN)

### Regions & Comunas (catálogo geográfico)
- `GET /api/v1/regions` — Listar todas las regiones (16, ordenadas por nombre). Público
- `GET /api/v1/regions/{regionId}/comunas` — Listar comunas de una región. Público

Ambos endpoints son públicos y no requieren autenticación. El front usa `GET /regions` para llenar el select de regiones, y al seleccionar una, `GET /regions/{id}/comunas` para el select de comunas. En escritura (registro y edición de perfil) **solo se envía `comunaId`**; la región se deriva automáticamente de la comuna (imposible guardar combinaciones inválidas).

### Contacts (formulario de contacto público)
- `POST /api/v1/contacts` — Enviar mensaje de contacto (público, sin auth)
- `GET /api/v1/contacts` — Listar mensajes de contacto (ADMIN)

**Ejemplo de contacto:**
```bash
curl -X POST http://localhost:9090/api/v1/contacts \
  -H "Content-Type: application/json" \
  -d '{"name": "Juan", "email": "juan@duoc.cl", "comment": "Hola, me interesa..."}'
```

## Autenticación

Los endpoints protegidos requieren un token JWT en el header:

```
Authorization: Bearer <token>
```

Para obtener un token, hacer login:

```bash
curl -X POST http://localhost:9090/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "player1@gmail.com", "password": "pass123"}'
```

### Permisos por rol

| Rol | Acceso |
|-----|--------|
| `ADMIN` | CRUD completo en juegos, usuarios, wallet, ver contactos, **blogs (CRUD + presign + cover)**, asignar sellers, seed |
| `VENDEDOR` | Solo lectura de juegos, categorías y compras |
| `CLIENTE` | Tienda, compras, perfil propio, enviar contacto |
| (Sin auth) | Login (incluido Google), registro, enviar contacto, ver juegos, ver categorías, **ver blog (`GET /blogs`, `GET /blogs/{id}`, `?q=`), ver regiones/comunas (`GET /regions`, `GET /regions/{id}/comunas`), filtrar juegos por `?state=`** |

## Health check

```bash
curl http://localhost:9090/actuator/health
```
