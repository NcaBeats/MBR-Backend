-- Datos mínimos que los tests de integración comparten con UserFixtures.profile(user):
-- ese fixture referencia Region(1) y Comuna(1) como entidades detached, así que el
-- seed tiene que dejar exactamente esos ids. En una base recién migrada la identidad
-- arranca en 1, y el WHERE NOT EXISTS evita duplicados cuando se reutiliza el
-- contenedor entre contextos de Spring.
INSERT INTO region (nombre)
SELECT 'Metropolitana de Santiago'
WHERE NOT EXISTS (SELECT 1 FROM region WHERE nombre = 'Metropolitana de Santiago');

INSERT INTO comuna (nombre, region_id)
SELECT 'Santiago', r.id
FROM region r
WHERE r.nombre = 'Metropolitana de Santiago'
  AND NOT EXISTS (SELECT 1 FROM comuna c WHERE c.nombre = 'Santiago');
