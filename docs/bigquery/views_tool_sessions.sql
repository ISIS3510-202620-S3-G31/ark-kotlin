-- Sesiones completadas de las herramientas (Sprint 4) y las preguntas que salen de ellas: BQ13 y BQ14.
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
WITH
-- las interacciones completadas (sin datos sinteticos), numeradas dentro de cada dia:
-- 1 = la ultima de ese usuario, ese tipo y ese dia
completed_interactions AS (
  SELECT
    interaction_id,
    user_id,
    tool_id,
    interaction_type,
    happened_at,
    duration_s,
    ROW_NUMBER() OVER (
      PARTITION BY user_id, interaction_type, DATE(happened_at, 'America/Bogota')
      ORDER BY happened_at DESC) AS number_in_day
  FROM `ark-mobiles.ark_dashboards.tool_interactions`
  WHERE complete AND tool_id IS NOT NULL AND happened_at IS NOT NULL
    AND NOT COALESCE(is_synthetic, FALSE)
),
-- la foto del dia es una por dia: retomarla reemplaza la foto, no es otra sesion completada.
-- de PHOTO y MULTIMEDIA solo queda la ultima de cada dia; las demas herramientas quedan todas
completed_sessions AS (
  SELECT interaction_id, user_id, tool_id, happened_at, duration_s
  FROM completed_interactions
  WHERE interaction_type NOT IN ('PHOTO', 'MULTIMEDIA') OR number_in_day = 1
),
-- aperturas que guarda la app en Firestore (las mide BQ1), desde el 27 de octubre de 2026: se leen
-- directo de la tabla de la extension porque la vista tool_load_latency tambien recorre todos los
-- eventos de Analytics y vuelve lenta cada consulta (los de antes de esa fecha casi nunca traen
-- el punto de entrada). El emulador se separa con la misma regla de tool_load_latency.
tool_opens AS (
  SELECT
    JSON_VALUE(path_params, '$.uid') AS user_id,
    JSON_VALUE(data, '$.toolId') AS tool_id,
    `ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(data, '$.timestamp')) AS happened_at,
    JSON_VALUE(data, '$.entryPoint') AS entry_point,
    CASE
      WHEN LOWER(JSON_VALUE(data, '$.deviceModel')) LIKE 'sdk_gphone%' THEN 'Emulator'
      WHEN LOWER(JSON_VALUE(data, '$.deviceModel')) LIKE '%emulator%' THEN 'Emulator'
      WHEN LOWER(JSON_VALUE(data, '$.deviceModel')) LIKE 'generic%' THEN 'Emulator'
      ELSE 'Real phone'
    END AS device_type
  FROM `ark-mobiles.firestore_export.tool_load_latency_raw_latest`
),
-- las aperturas de la misma herramienta, por el mismo usuario, en las 3 horas antes de terminarla,
-- numeradas de la mas reciente a la mas vieja
opens_before_finishing AS (
  SELECT
    s.interaction_id,
    o.entry_point,
    o.device_type,
    ROW_NUMBER() OVER (PARTITION BY s.interaction_id ORDER BY o.happened_at DESC) AS open_number
  FROM completed_sessions AS s
  JOIN tool_opens AS o
    ON o.user_id = s.user_id
    AND o.tool_id = s.tool_id
    AND o.happened_at BETWEEN TIMESTAMP_SUB(s.happened_at, INTERVAL 3 HOUR) AND s.happened_at
),
-- la apertura justo antes de terminarla: la mas reciente
last_open AS (
  SELECT interaction_id, entry_point, device_type
  FROM opens_before_finishing
  WHERE open_number = 1
),
-- para las sesiones sin apertura: el tipo de dispositivo de la cuenta, solo si siempre ha usado
-- el mismo (si uso los dos queda null)
device_per_user AS (
  SELECT
    user_id,
    IF(COUNT(DISTINCT device_type) = 1, MAX(device_type), NULL) AS device_type
  FROM tool_opens
  WHERE user_id IS NOT NULL
  GROUP BY user_id
),
-- el periodo: las ultimas 4 semanas completas, de lunes a domingo en hora de Bogota
last_4_weeks AS (
  SELECT
    DATE_SUB(DATE_TRUNC(CURRENT_DATE('America/Bogota'), ISOWEEK), INTERVAL 28 DAY) AS first_monday,
    DATE_TRUNC(CURRENT_DATE('America/Bogota'), ISOWEEK) AS this_monday
)
SELECT
  s.interaction_id,
  s.user_id,
  s.tool_id,
  `ark-mobiles.ark_dashboards.tool_name`(s.tool_id) AS tool,
  s.happened_at,
  DATE(s.happened_at, 'America/Bogota') AS day,
  DATE_TRUNC(DATE(s.happened_at, 'America/Bogota'), ISOWEEK) AS week_start,
  s.duration_s,
  -- recommendation, surprise_me, weather o catalog; null si la app no registro la apertura
  NULLIF(o.entry_point, 'unknown') AS entry_point,
  COALESCE(o.device_type, u.device_type, 'Unknown') AS device_type,
  ta.user_id IS NOT NULL AS is_test_account,
  -- true si cae en las ultimas 4 semanas completas (el periodo de estas preguntas)
  DATE(s.happened_at, 'America/Bogota') >= w.first_monday
    AND DATE(s.happened_at, 'America/Bogota') < w.this_monday AS in_last_4_weeks
FROM completed_sessions AS s
CROSS JOIN last_4_weeks AS w
LEFT JOIN last_open AS o ON o.interaction_id = s.interaction_id
LEFT JOIN device_per_user AS u ON u.user_id = s.user_id
LEFT JOIN `ark-mobiles.ark_dashboards.test_accounts` AS ta ON ta.user_id = s.user_id;

-- BQ13: cuantas veces por semana se completa cada herramienta en las ultimas 4 semanas.
-- Una fila por herramienta, semana, tipo de dispositivo y cuenta de prueba, con 0 en las semanas
-- sin sesiones: asi la grafica distingue una herramienta habitual (todas las semanas) de una
-- esporadica (un pico en una sola semana), cosa que un promedio esconde.
-- Lee completed_tool_sessions una sola vez (BigQuery recalcula una vista cada vez que se nombra):
-- primero cuenta las 4 semanas de cada herramienta en 4 columnas y despues las pasa a 4 filas.
-- La respuesta se lee con device_type = 'Real phone' y is_test_account = false.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq13_weekly_tool_completions` AS
WITH
-- el lunes de la primera semana (W1); las otras 3 empiezan 7, 14 y 21 dias despues
period AS (
  SELECT DATE_SUB(DATE_TRUNC(CURRENT_DATE('America/Bogota'), ISOWEEK), INTERVAL 28 DAY) AS first_monday
),
-- unica lectura de las sesiones: cuenta las de cada herramienta en cada una de las 4 semanas.
-- no se filtra por periodo para que toda herramienta que se completo alguna vez aparezca, aunque
-- tenga 0 en las 4 semanas
completions_per_tool AS (
  SELECT
    s.tool,
    s.device_type,
    s.is_test_account,
    COUNTIF(s.week_start = p.first_monday) AS week_1,
    COUNTIF(s.week_start = DATE_ADD(p.first_monday, INTERVAL 7 DAY)) AS week_2,
    COUNTIF(s.week_start = DATE_ADD(p.first_monday, INTERVAL 14 DAY)) AS week_3,
    COUNTIF(s.week_start = DATE_ADD(p.first_monday, INTERVAL 21 DAY)) AS week_4,
    -- la ultima sesion de esta herramienta dentro del periodo
    MAX(IF(s.in_last_4_weeks, s.happened_at, NULL)) AS last_session_of_tool
  FROM `ark-mobiles.ark_dashboards.completed_tool_sessions` AS s
  CROSS JOIN period AS p
  GROUP BY s.tool, s.device_type, s.is_test_account
),
-- la lista fija de las 4 semanas, para pasar las columnas week_1 ... week_4 a filas
the_4_weeks AS (
  SELECT 1 AS week_number, first_monday AS week_start FROM period
  UNION ALL SELECT 2, DATE_ADD(first_monday, INTERVAL 7 DAY) FROM period
  UNION ALL SELECT 3, DATE_ADD(first_monday, INTERVAL 14 DAY) FROM period
  UNION ALL SELECT 4, DATE_ADD(first_monday, INTERVAL 21 DAY) FROM period
)
SELECT
  c.tool,
  w.week_number,
  -- "W1 Oct 26 - Nov 01": el numero deja las semanas en orden en Data Studio
  FORMAT('W%d %s - %s', w.week_number, FORMAT_DATE('%b %d', w.week_start),
    FORMAT_DATE('%b %d', DATE_ADD(w.week_start, INTERVAL 6 DAY))) AS week,
  w.week_start,
  -- las sesiones de esta semana salen de la columna que le toca
  CASE w.week_number
    WHEN 1 THEN c.week_1
    WHEN 2 THEN c.week_2
    WHEN 3 THEN c.week_3
    WHEN 4 THEN c.week_4
  END AS completions,
  c.week_1 + c.week_2 + c.week_3 + c.week_4 AS completions_4_weeks,
  -- en cuantas de las 4 semanas se completo al menos una vez: 4 = habitual, 1 = esporadica
  IF(c.week_1 > 0, 1, 0) + IF(c.week_2 > 0, 1, 0) + IF(c.week_3 > 0, 1, 0) + IF(c.week_4 > 0, 1, 0)
    AS active_weeks,
  -- las 4 semanas en una sola linea, "28 / 0 / 8 / 1", para la tabla y la respuesta
  CONCAT(CAST(c.week_1 AS STRING), ' / ', CAST(c.week_2 AS STRING), ' / ',
    CAST(c.week_3 AS STRING), ' / ', CAST(c.week_4 AS STRING)) AS weekly_completions,
  -- la ultima sesion del periodo de todas las herramientas de ese tipo de dispositivo y cuenta de
  -- prueba (no solo de esta herramienta)
  MAX(c.last_session_of_tool) OVER (PARTITION BY c.device_type, c.is_test_account) AS last_session_at,
  c.device_type,
  c.is_test_account
FROM completions_per_tool AS c
CROSS JOIN the_4_weeks AS w;

-- BQ14: cuanto se demoran los usuarios completando cada herramienta, en 3 rangos fijos:
-- menos de 1 minuto (1 a 59 s), de 1 a 3 minutos (60 a 180 s) y mas de 3 minutos (mas de 180 s).
-- Una fila por herramienta, rango, tipo de dispositivo y cuenta de prueba, con 0 en los rangos sin
-- sesiones (asi la barra al 100 % siempre tiene los 3 pedazos en el mismo orden).
-- Una duracion de 0 segundos, negativa o vacia es un error del reloj: no se puede clasificar, asi
-- que no entra en los rangos y se cuenta aparte en invalid_duration_sessions.
-- La respuesta se lee con device_type = 'Real phone' y is_test_account = false.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq14_tool_duration_brackets` AS
WITH grouped AS (
  SELECT
    tool,
    device_type,
    is_test_account,
    -- sesiones de cada rango, en el orden de los rangos
    [COUNTIF(duration_s BETWEEN 1 AND 59),
     COUNTIF(duration_s BETWEEN 60 AND 180),
     COUNTIF(duration_s > 180)] AS by_bracket,
    COUNTIF(duration_s IS NULL OR duration_s <= 0) AS invalid_duration_sessions,
    MAX(happened_at) AS last_session_at
  FROM `ark-mobiles.ark_dashboards.completed_tool_sessions`
  WHERE in_last_4_weeks
  GROUP BY 1, 2, 3
)
SELECT
  g.tool,
  b.duration_bracket,
  b.bracket_order,
  g.by_bracket[OFFSET(b.bracket_order - 1)] AS sessions,
  -- porcentaje dentro de su herramienta (dispositivo y cuenta de prueba), no del total
  ROUND(100 * SAFE_DIVIDE(g.by_bracket[OFFSET(b.bracket_order - 1)],
    (SELECT SUM(n) FROM UNNEST(g.by_bracket) AS n)), 1) AS pct_of_tool_sessions,
  g.invalid_duration_sessions,
  g.last_session_at,
  g.device_type,
  g.is_test_account
FROM grouped AS g
CROSS JOIN UNNEST([
  STRUCT(1 AS bracket_order, '1. Under 1 min' AS duration_bracket),
  (2, '2. 1 to 3 min'),
  (3, '3. Over 3 min')
]) AS b;
