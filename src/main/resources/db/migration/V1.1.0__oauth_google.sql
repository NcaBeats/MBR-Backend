-- =============================================
-- OAUTH IDENTITY (Google Sign-In)
-- =============================================
-- Una fila por cuenta externa vinculada. Se busca por (provider, provider_user_id)
-- = el "sub" del ID token, NUNCA por email: Google permite cambiarlo y dos cuentas
-- podrian quedar apuntando al mismo correo.
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

-- Una cuenta creada con Google no tiene password local. El login por password
-- debe rechazarla (UserDetailsServiceImpl), no reventar con un 500.
ALTER TABLE "public"."user" ALTER COLUMN "password" DROP NOT NULL;
