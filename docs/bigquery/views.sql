-- Ark: vistas limpias sobre lo que llega a BigQuery en tiempo real.
--
-- Origen:
--   * firestore_export.*_raw_latest  -> lo crea la extension "Stream Firestore to BigQuery"
--     (una instancia por coleccion). Cada fila trae el documento como JSON en `data`
--     y el uid del usuario en `path_params`.
--
-- Estas vistas convierten ese JSON en columnas con tipo, para que Looker Studio las use
-- directo. Son vistas (no tablas): siempre muestran lo ultimo que llego, sin copiar datos.
--
-- Uso: correr todo el archivo en la consola de BigQuery
-- (o `bq query --use_legacy_sql=false < views.sql`). Las vistas de Analytics estan en
-- views_analytics.sql porque ese dataset aparece despues.
--
-- `is_synthetic` = TRUE para los datos que generaron los scripts seed (demo: true,
-- is_synthetic_seed: true o uid demo_*). No se borran: se filtran en el dashboard.

CREATE SCHEMA IF NOT EXISTS `ark-mobiles.ark_dashboards`
OPTIONS (location = 'US');

-- La app guarda fechas de dos formas: Long en milisegundos (check-ins, feedback) y
-- Date de Kotlin, que Firestore guarda como Timestamp (tool_interactions). Esta funcion
-- recibe el campo como JSON (JSON_QUERY) y entiende las dos, y tambien texto ISO (lo que
-- escriben algunos seeds). BigQuery no deja armar la ruta JSON dentro de la funcion, por
-- eso se le pasa el campo ya extraido.
CREATE OR REPLACE FUNCTION `ark-mobiles.ark_dashboards.to_ts`(field_json STRING)
RETURNS TIMESTAMP AS (
  COALESCE(
    TIMESTAMP_SECONDS(SAFE_CAST(JSON_VALUE(field_json, '$._seconds') AS INT64)),
    TIMESTAMP_MILLIS(SAFE_CAST(JSON_VALUE(field_json, '$') AS INT64)),
    SAFE_CAST(JSON_VALUE(field_json, '$') AS TIMESTAMP)
  )
);

-- Usuarios: solo lo que sirve para segmentar. Nombre y correo se quedan fuera a proposito.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.users` AS
SELECT
  document_id AS user_id,
  SAFE_CAST(JSON_VALUE(data, '$.age') AS INT64) AS age,
  CASE
    WHEN SAFE_CAST(JSON_VALUE(data, '$.age') AS INT64) IS NULL
      OR SAFE_CAST(JSON_VALUE(data, '$.age') AS INT64) NOT BETWEEN 15 AND 90 THEN 'Unknown'
    WHEN SAFE_CAST(JSON_VALUE(data, '$.age') AS INT64) <= 19 THEN '17-19'
    WHEN SAFE_CAST(JSON_VALUE(data, '$.age') AS INT64) <= 22 THEN '20-22'
    WHEN SAFE_CAST(JSON_VALUE(data, '$.age') AS INT64) <= 25 THEN '23-25'
    ELSE '26+'
  END AS age_bucket,
  (JSON_VALUE(data, '$.demo') = 'true'
    OR JSON_VALUE(data, '$.is_synthetic_seed') = 'true'
    OR STARTS_WITH(document_id, 'demo')) AS is_synthetic
FROM `ark-mobiles.firestore_export.users_raw_latest`;

-- Una fila por uso de herramienta (BQ4 y las de uso de herramientas).
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.tool_interactions` AS
SELECT
  t.document_id AS interaction_id,
  JSON_VALUE(t.path_params, '$.uid') AS user_id,
  JSON_VALUE(t.data, '$.toolId') AS tool_id,
  UPPER(JSON_VALUE(t.data, '$.interactionType')) AS interaction_type,
  SAFE_CAST(JSON_VALUE(t.data, '$.duration') AS INT64) AS duration_s,
  SAFE_CAST(JSON_VALUE(t.data, '$.intensity') AS INT64) AS intensity,
  SAFE_CAST(JSON_VALUE(t.data, '$.complete') AS BOOL) AS complete,
  SAFE_CAST(JSON_VALUE(t.data, '$.value') AS INT64) AS value,
  `ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(t.data, '$.timestamp')) AS happened_at,
  u.age_bucket,
  COALESCE(u.is_synthetic, FALSE)
    OR JSON_VALUE(t.data, '$.is_synthetic_seed') = 'true'
    OR JSON_VALUE(t.data, '$.demo') = 'true' AS is_synthetic
FROM `ark-mobiles.firestore_export.tool_interactions_raw_latest` AS t
LEFT JOIN `ark-mobiles.ark_dashboards.users` AS u
  ON u.user_id = JSON_VALUE(t.path_params, '$.uid');

-- Un check-in por fila.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.mood_checkins` AS
SELECT
  c.document_id AS checkin_id,
  JSON_VALUE(c.path_params, '$.uid') AS user_id,
  JSON_VALUE(c.data, '$.moodName') AS mood_name,
  `ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(c.data, '$.timestamp')) AS happened_at,
  SAFE_CAST(JSON_VALUE(c.data, '$.latitude') AS FLOAT64) AS latitude,
  SAFE_CAST(JSON_VALUE(c.data, '$.longitude') AS FLOAT64) AS longitude,
  ARRAY_LENGTH(JSON_QUERY_ARRAY(c.data, '$.emotions')) AS emotions_count,
  u.age_bucket,
  COALESCE(u.is_synthetic, FALSE) OR JSON_VALUE(c.data, '$.demo') = 'true' AS is_synthetic
FROM `ark-mobiles.firestore_export.mood_checkins_raw_latest` AS c
LEFT JOIN `ark-mobiles.ark_dashboards.users` AS u
  ON u.user_id = JSON_VALUE(c.path_params, '$.uid');

-- Una fila por emocion: un check-in con dos emociones da dos filas (como export.js).
-- La semana va en hora de Bogota.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.mood_emotions` AS
SELECT
  c.document_id AS checkin_id,
  JSON_VALUE(c.path_params, '$.uid') AS user_id,
  JSON_VALUE(e, '$.name') AS emotion,
  SAFE_CAST(JSON_VALUE(e, '$.intensity') AS INT64) AS intensity,
  `ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(c.data, '$.timestamp')) AS happened_at,
  DATE_TRUNC(
    DATE(`ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(c.data, '$.timestamp')), 'America/Bogota'),
    ISOWEEK) AS week_start,
  u.age_bucket,
  COALESCE(u.is_synthetic, FALSE) OR JSON_VALUE(c.data, '$.demo') = 'true' AS is_synthetic
FROM `ark-mobiles.firestore_export.mood_checkins_raw_latest` AS c
CROSS JOIN UNNEST(JSON_QUERY_ARRAY(c.data, '$.emotions')) AS e
LEFT JOIN `ark-mobiles.ark_dashboards.users` AS u
  ON u.user_id = JSON_VALUE(c.path_params, '$.uid');

-- Emotion By Location: una fila por emocion de cada check-in, con su localidad.
-- Lee mood_checkins, donde escriben Kotlin y Flutter. Reemplaza el CSV de
-- process_checkins_locality.js.
-- La localidad sale del poligono oficial de las 20 localidades (tabla bogota_localities, se
-- crea con bogota_localities.sql). Sin coordenadas: 'Ubicación no especificada'; con
-- coordenadas fuera de Bogota (otro municipio, el emulador): 'Otra localidad'.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.mood_emotions_by_locality` AS
WITH checkins AS (
  SELECT
    c.*,
    SAFE_CAST(JSON_VALUE(c.data, '$.latitude') AS FLOAT64) AS latitude,
    SAFE_CAST(JSON_VALUE(c.data, '$.longitude') AS FLOAT64) AS longitude
  FROM `ark-mobiles.firestore_export.mood_checkins_raw_latest` AS c
),
-- cruce espacial aparte: BigQuery no acepta un LEFT JOIN cuya unica condicion es geografica.
-- Las localidades no se cruzan, asi que cada punto cae en una sola (o en ninguna).
located AS (
  SELECT c.document_name, l.name
  FROM checkins AS c
  JOIN `ark-mobiles.ark_dashboards.bogota_localities` AS l
    -- SAFE: una coordenada fuera de rango da NULL (sin localidad) en vez de romper la vista
    ON ST_CONTAINS(l.geom, SAFE.ST_GEOGPOINT(c.longitude, c.latitude))
)
SELECT
  CONCAT(JSON_VALUE(c.path_params, '$.uid'), '/', c.document_id) AS checkin_key,
  JSON_VALUE(c.path_params, '$.uid') AS user_id,
  CASE
    WHEN c.latitude IS NULL OR c.longitude IS NULL THEN 'Ubicación no especificada'
    ELSE COALESCE(l.name, 'Otra localidad')
  END AS locality,
  c.latitude,
  c.longitude,
  -- Kotlin guarda el nombre del enum (HAPPINESS); en minuscula queda igual venga de la app que venga
  LOWER(JSON_VALUE(e, '$.name')) AS emotion,
  SAFE_CAST(JSON_VALUE(e, '$.intensity') AS INT64) AS intensity,
  `ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(c.data, '$.timestamp')) AS happened_at,
  u.age_bucket,
  -- COALESCE en los dos lados: FALSE OR NULL da NULL, y el resumen agrupa y une por esta columna
  COALESCE(u.is_synthetic, FALSE)
    OR COALESCE(JSON_VALUE(c.data, '$.demo') = 'true', FALSE) AS is_synthetic
FROM checkins AS c
CROSS JOIN UNNEST(JSON_QUERY_ARRAY(c.data, '$.emotions')) AS e
LEFT JOIN located AS l
  ON l.document_name = c.document_name
LEFT JOIN `ark-mobiles.ark_dashboards.users` AS u
  ON u.user_id = JSON_VALUE(c.path_params, '$.uid');

-- Emotion By Location: una fila por localidad, con las mismas columnas que tenia el CSV
-- (asi las graficas de Data Studio no se rompen al cambiar la fuente), separada por
-- is_synthetic para filtrar en el dashboard. Diferencia con el CSV: total_checkins ahora
-- cuenta check-ins; lo que antes contaba (emociones) queda en emotion_entries.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.locality_emotion_summary` AS
WITH per_emotion AS (
  SELECT
    is_synthetic, locality, emotion,
    COUNT(*) AS emotion_count,
    ROUND(AVG(intensity), 2) AS avg_intensity
  FROM `ark-mobiles.ark_dashboards.mood_emotions_by_locality`
  GROUP BY is_synthetic, locality, emotion
),
per_locality AS (
  SELECT
    is_synthetic, locality,
    COUNT(DISTINCT checkin_key) AS total_checkins,
    COUNT(*) AS emotion_entries,
    MAX(happened_at) AS last_checkin_at
  FROM `ark-mobiles.ark_dashboards.mood_emotions_by_locality`
  GROUP BY is_synthetic, locality
),
top_emotions AS (
  SELECT
    is_synthetic, locality,
    ARRAY_AGG(emotion IGNORE NULLS ORDER BY emotion_count DESC, emotion LIMIT 1)[SAFE_OFFSET(0)] AS most_frequent_emotion,
    MAX(emotion_count) AS most_frequent_count,
    ARRAY_AGG(emotion IGNORE NULLS ORDER BY avg_intensity DESC, emotion LIMIT 1)[SAFE_OFFSET(0)] AS highest_intensity_emotion,
    MAX(avg_intensity) AS highest_avg_intensity
  FROM per_emotion
  GROUP BY is_synthetic, locality
)
SELECT
  l.locality,
  l.total_checkins,
  t.most_frequent_emotion,
  t.most_frequent_count,
  t.highest_intensity_emotion,
  t.highest_avg_intensity,
  l.emotion_entries,
  l.last_checkin_at,
  l.is_synthetic
FROM per_locality AS l
JOIN top_emotions AS t USING (is_synthetic, locality);

-- Calificaciones que deja el usuario al terminar una herramienta.
-- La app Kotlin guarda la fecha en createdAt; seed.js la guarda en startedAt (texto ISO).
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.tool_feedback` AS
SELECT
  f.document_id AS feedback_id,
  JSON_VALUE(f.path_params, '$.uid') AS user_id,
  JSON_VALUE(f.data, '$.toolId') AS tool_id,
  SAFE_CAST(JSON_VALUE(f.data, '$.rating') AS INT64) AS rating,
  JSON_VALUE(f.data, '$.interactionId') AS interaction_id,
  COALESCE(
    `ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(f.data, '$.createdAt')),
    `ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(f.data, '$.startedAt')),
    `ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(f.data, '$.timestamp'))
  ) AS happened_at,
  u.age_bucket,
  COALESCE(u.is_synthetic, FALSE) OR JSON_VALUE(f.data, '$.demo') = 'true' AS is_synthetic
FROM `ark-mobiles.firestore_export.tool_feedback_raw_latest` AS f
LEFT JOIN `ark-mobiles.ark_dashboards.users` AS u
  ON u.user_id = JSON_VALUE(f.path_params, '$.uid');
