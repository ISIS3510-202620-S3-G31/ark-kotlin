-- Sesiones completadas de las herramientas (Sprint 4) y las preguntas que salen de ellas: BQ13.
--
-- Uso: correr despues de views.sql y views_bq4_and_age.sql (usa tool_interactions, tool_name,
-- test_accounts y la tabla tool_load_latency_raw_latest de la extension). Despues correr
-- views_answers.sql, que escribe las respuestas en vivo.
--
-- Periodo de estas preguntas: las ultimas 4 semanas completas, de lunes a domingo en hora de
-- Bogota (la semana en curso no cuenta porque esta a medias). Unidad: una sesion completada
-- (complete = true en tool_interactions). Sin datos sinteticos de los scripts seed.

-- Una fila por sesion completada, con lo que la app no guarda en tool_interactions: el celular y el
-- punto de entrada salen de la apertura de la misma herramienta justo antes de terminarla, como en
-- bq4_tool_formats.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.completed_tool_sessions` AS
WITH ti AS (
  SELECT interaction_id, user_id, tool_id, interaction_type, happened_at, duration_s
  FROM `ark-mobiles.ark_dashboards.tool_interactions`
  WHERE complete AND tool_id IS NOT NULL AND happened_at IS NOT NULL
    AND NOT COALESCE(is_synthetic, FALSE)
  -- la foto del dia es una por dia: retomarla reemplaza la foto, no es otra sesion completada
  QUALIFY interaction_type NOT IN ('PHOTO', 'MULTIMEDIA')
    OR ROW_NUMBER() OVER (
      PARTITION BY user_id, interaction_type, DATE(happened_at, 'America/Bogota')
      ORDER BY happened_at DESC) = 1
),
-- aperturas que guarda la app en Firestore (las mide BQ1), desde el 27 de octubre de 2026: se leen
-- directo de la tabla de la extension porque la vista tool_load_latency tambien recorre todos los
-- eventos de Analytics y vuelve lenta cada consulta (los de antes de esa fecha casi nunca traen
-- el punto de entrada). El emulador se separa con la misma regla de tool_load_latency.
opens AS (
  SELECT
    JSON_VALUE(path_params, '$.uid') AS user_id,
    JSON_VALUE(data, '$.toolId') AS tool_id,
    `ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(data, '$.timestamp')) AS happened_at,
    JSON_VALUE(data, '$.entryPoint') AS entry_point,
    IF(LOWER(JSON_VALUE(data, '$.deviceModel')) LIKE 'sdk_gphone%'
         OR LOWER(JSON_VALUE(data, '$.deviceModel')) LIKE '%emulator%'
         OR LOWER(JSON_VALUE(data, '$.deviceModel')) LIKE 'generic%',
       'Emulator', 'Real phone') AS device_type
  FROM `ark-mobiles.firestore_export.tool_load_latency_raw_latest`
),
-- la apertura de la misma herramienta, por el mismo usuario, justo antes de terminarla
opened AS (
  SELECT
    ti.interaction_id,
    ARRAY_AGG(STRUCT(o.entry_point, o.device_type) ORDER BY o.happened_at DESC LIMIT 1)[SAFE_OFFSET(0)] AS open
  FROM ti
  JOIN opens AS o
    ON o.user_id = ti.user_id
    AND o.tool_id = ti.tool_id
    AND o.happened_at BETWEEN TIMESTAMP_SUB(ti.happened_at, INTERVAL 3 HOUR) AND ti.happened_at
  GROUP BY 1
),
-- sin apertura: el unico tipo de dispositivo que ha usado la cuenta
per_user AS (
  SELECT user_id, IF(COUNT(DISTINCT device_type) = 1, ANY_VALUE(device_type), NULL) AS device_type
  FROM opens
  WHERE user_id IS NOT NULL
  GROUP BY 1
),
-- semanas de lunes a domingo en hora de Bogota
weeks AS (
  SELECT
    DATE_SUB(DATE_TRUNC(CURRENT_DATE('America/Bogota'), ISOWEEK), INTERVAL 28 DAY) AS window_start,
    DATE_TRUNC(CURRENT_DATE('America/Bogota'), ISOWEEK) AS window_end
)
SELECT
  ti.interaction_id,
  ti.user_id,
  ti.tool_id,
  `ark-mobiles.ark_dashboards.tool_name`(ti.tool_id) AS tool,
  ti.happened_at,
  DATE(ti.happened_at, 'America/Bogota') AS day,
  DATE_TRUNC(DATE(ti.happened_at, 'America/Bogota'), ISOWEEK) AS week_start,
  ti.duration_s,
  -- recommendation, surprise_me, weather o catalog; null si la app no registro la apertura
  NULLIF(o.open.entry_point, 'unknown') AS entry_point,
  COALESCE(o.open.device_type, u.device_type, 'Unknown') AS device_type,
  ta.user_id IS NOT NULL AS is_test_account,
  -- true si cae en las ultimas 4 semanas completas (el periodo de estas preguntas)
  DATE(ti.happened_at, 'America/Bogota') >= w.window_start
    AND DATE(ti.happened_at, 'America/Bogota') < w.window_end AS in_last_4_weeks
FROM ti
CROSS JOIN weeks AS w
LEFT JOIN opened AS o USING (interaction_id)
LEFT JOIN per_user AS u ON u.user_id = ti.user_id
LEFT JOIN `ark-mobiles.ark_dashboards.test_accounts` AS ta ON ta.user_id = ti.user_id;

-- BQ13: cuantas veces por semana se completa cada herramienta en las ultimas 4 semanas.
-- Una fila por herramienta, semana, tipo de dispositivo y cuenta de prueba, con 0 en las semanas
-- sin sesiones: asi la grafica distingue una herramienta habitual (todas las semanas) de una
-- esporadica (un pico en una sola semana), cosa que un promedio esconde.
-- Lee completed_tool_sessions una sola vez (BigQuery recalcula una vista cada vez que se nombra):
-- agrupa las sesiones de cada tipo de dispositivo en un arreglo y cuenta sobre el arreglo.
-- La respuesta se lee con device_type = 'Real phone' y is_test_account = false.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq13_weekly_tool_completions` AS
WITH grouped AS (
  SELECT
    device_type,
    is_test_account,
    DATE_SUB(DATE_TRUNC(CURRENT_DATE('America/Bogota'), ISOWEEK), INTERVAL 28 DAY) AS window_start,
    MAX(IF(in_last_4_weeks, happened_at, NULL)) AS last_session_at,
    ARRAY_AGG(STRUCT(tool, week_start, in_last_4_weeks)) AS sessions
  FROM `ark-mobiles.ark_dashboards.completed_tool_sessions`
  GROUP BY 1, 2
),
filled AS (
  SELECT
    tool_name AS tool,
    week_number,
    DATE_ADD(g.window_start, INTERVAL 7 * (week_number - 1) DAY) AS week_start,
    (SELECT COUNT(*) FROM UNNEST(g.sessions) AS s
     WHERE s.in_last_4_weeks AND s.tool = tool_name
       AND s.week_start = DATE_ADD(g.window_start, INTERVAL 7 * (week_number - 1) DAY)) AS completions,
    g.last_session_at,
    g.device_type,
    g.is_test_account
  FROM grouped AS g
  -- toda herramienta que se completo alguna vez, para que aparezca aunque tenga 0 en el periodo
  CROSS JOIN UNNEST(ARRAY(SELECT DISTINCT s.tool FROM UNNEST(g.sessions) AS s)) AS tool_name
  CROSS JOIN UNNEST([1, 2, 3, 4]) AS week_number
)
SELECT
  tool,
  week_number,
  -- "W1 Oct 26 - Nov 01": el numero deja las semanas en orden en Data Studio
  FORMAT('W%d %s - %s', week_number, FORMAT_DATE('%b %d', week_start),
    FORMAT_DATE('%b %d', DATE_ADD(week_start, INTERVAL 6 DAY))) AS week,
  week_start,
  completions,
  SUM(completions) OVER per_tool AS completions_4_weeks,
  -- en cuantas de las 4 semanas se completo al menos una vez: 4 = habitual, 1 = esporadica
  COUNTIF(completions > 0) OVER per_tool AS active_weeks,
  -- las 4 semanas en una sola linea, "28 / 0 / 8 / 1", para la tabla y la respuesta
  ARRAY_TO_STRING(ARRAY_AGG(CAST(completions AS STRING)) OVER (per_tool ORDER BY week_number
    ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING), ' / ') AS weekly_completions,
  last_session_at,
  device_type,
  is_test_account
FROM filled
WINDOW per_tool AS (PARTITION BY tool, device_type, is_test_account);
