-- BQ4 (formato de herramienta que mas se completa) y pregunta de edad de los usuarios activos.
--
-- Uso: correr despues de views.sql y views_analytics.sql (usa users, tool_interactions,
-- mood_checkins, tool_load_latency y daily_screen_time).

-- Cuentas de prueba creadas a mano en la app (no por los scripts seed, esas ya salen con
-- is_synthetic). No se borran: las vistas las marcan con is_test_account y el dashboard las
-- filtra con un control. Si aparece otra, se agrega una fila aqui y se vuelve a correr.
CREATE OR REPLACE TABLE `ark-mobiles.ark_dashboards.test_accounts` AS
SELECT * FROM UNNEST([
  STRUCT('aqM1Wg5fUYYFDCQBGzCn9r5FPcE3' AS user_id, 'nombre twst2, edad 67' AS reason),
  ('rNwrX2G2SBZ9MzHwQzg2d4RzGzf1', 'nombre pruebita, edad 67'),
  ('H4MYzKheDRfHt57hoTZIWMATM7k2', 'nombre test3, edad 67'),
  ('silaAf33u4NB6s126LOdVCbyqNr2', 'nombre tecleado al azar, edad 89')
])
UNION ALL
SELECT document_id, 'id qa_ de un script de pruebas'
FROM `ark-mobiles.firestore_export.users_raw_latest`
WHERE STARTS_WITH(document_id, 'qa_');

-- BQ4: sesiones completadas por formato, emulador o celular real y cuenta de prueba.
-- La respuesta se lee con device_type = 'Real phone' y is_test_account = false.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq4_tool_formats` AS
WITH ti AS (
  SELECT
    interaction_id,
    user_id,
    tool_id,
    happened_at,
    CASE
      WHEN interaction_type IN ('PHOTO', 'MULTIMEDIA') THEN 'multimedia'
      ELSE LOWER(interaction_type)
    END AS tool_format
  FROM `ark-mobiles.ark_dashboards.tool_interactions`
  WHERE complete AND interaction_type IS NOT NULL AND NOT COALESCE(is_synthetic, FALSE)
  -- la foto del dia es una por dia: retomarla reemplaza la foto, no es otra sesion completada
  QUALIFY interaction_type NOT IN ('PHOTO', 'MULTIMEDIA')
    OR ROW_NUMBER() OVER (
      PARTITION BY user_id, interaction_type, DATE(happened_at, 'America/Bogota')
      ORDER BY happened_at DESC) = 1
),
-- tool_interactions no guarda el celular: se toma de la apertura de la misma herramienta,
-- por el mismo usuario, justo antes de terminarla (la mide BQ1)
opened AS (
  SELECT
    ti.interaction_id,
    ARRAY_AGG(l.device_type ORDER BY l.happened_at DESC LIMIT 1)[SAFE_OFFSET(0)] AS device_type
  FROM ti
  JOIN `ark-mobiles.ark_dashboards.tool_load_latency` AS l
    ON l.user_id = ti.user_id
    AND l.tool_id = ti.tool_id
    AND l.happened_at BETWEEN TIMESTAMP_SUB(ti.happened_at, INTERVAL 3 HOUR) AND ti.happened_at
  GROUP BY 1
),
-- sin apertura (versiones viejas de la app): el unico tipo de dispositivo que ha usado la cuenta
per_user AS (
  SELECT user_id, IF(COUNT(DISTINCT device_type) = 1, ANY_VALUE(device_type), NULL) AS device_type
  FROM (
    SELECT user_id, device_type FROM `ark-mobiles.ark_dashboards.tool_load_latency`
    UNION ALL
    SELECT user_key, device_type FROM `ark-mobiles.ark_dashboards.daily_screen_time`
  )
  WHERE user_id IS NOT NULL
  GROUP BY 1
),
counted AS (
  SELECT
    ti.tool_format,
    COALESCE(o.device_type, u.device_type, 'Unknown') AS device_type,
    ta.user_id IS NOT NULL AS is_test_account,
    COUNT(*) AS total_completions
  FROM ti
  LEFT JOIN opened AS o USING (interaction_id)
  LEFT JOIN per_user AS u ON u.user_id = ti.user_id
  LEFT JOIN `ark-mobiles.ark_dashboards.test_accounts` AS ta ON ta.user_id = ti.user_id
  GROUP BY 1, 2, 3
)
SELECT
  tool_format,
  total_completions,
  -- porcentaje dentro de su grupo (dispositivo y cuenta de prueba), no del total
  ROUND(100 * total_completions / SUM(total_completions) OVER (PARTITION BY device_type, is_test_account), 1)
    AS completion_percentage,
  device_type,
  is_test_account
FROM counted;

-- Edad de los usuarios activos: al menos un check-in o una herramienta en los ultimos 30 dias,
-- por grupo de curso de vida de MinSalud (Res. 3280 de 2018).
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq_age_distribution` AS
WITH activity AS (
  SELECT user_id, happened_at FROM `ark-mobiles.ark_dashboards.mood_checkins`
  UNION ALL
  SELECT user_id, happened_at FROM `ark-mobiles.ark_dashboards.tool_interactions`
),
active AS (
  SELECT DISTINCT user_id
  FROM activity
  WHERE happened_at >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 30 DAY)
),
valid_users AS (
  SELECT
    u.user_id,
    CASE
      WHEN u.age BETWEEN 12 AND 17 THEN 'Adolescencia (12-17)'
      WHEN u.age BETWEEN 18 AND 28 THEN 'Juventud (18-28)'
      WHEN u.age BETWEEN 29 AND 59 THEN 'Adultez (29-59)'
      ELSE 'Vejez (60+)'
    END AS age_bucket,
    ta.user_id IS NOT NULL AS is_test_account
  FROM `ark-mobiles.ark_dashboards.users` AS u
  LEFT JOIN `ark-mobiles.ark_dashboards.test_accounts` AS ta ON ta.user_id = u.user_id
  -- mismos limites que el registro de la app (UserModel: 13 a 120)
  WHERE NOT COALESCE(u.is_synthetic, FALSE) AND u.age BETWEEN 13 AND 120
),
by_age AS (
  SELECT v.age_bucket, v.is_test_account, COUNT(*) AS active_users_30d
  FROM valid_users AS v
  JOIN active AS a ON a.user_id = v.user_id
  GROUP BY 1, 2
)
SELECT
  age_bucket,
  active_users_30d,
  ROUND(100 * SAFE_DIVIDE(active_users_30d, SUM(active_users_30d) OVER (PARTITION BY is_test_account)), 1)
    AS pct_of_active_users,
  is_test_account
FROM by_age;
