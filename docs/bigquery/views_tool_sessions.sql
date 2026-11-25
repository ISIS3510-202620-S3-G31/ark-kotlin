-- Sesiones completadas de las herramientas (Sprint 4) y las preguntas que salen de ellas: BQ13, BQ14 y BQ15.
--
-- Uso: correr despues de views.sql y views_bq4_and_age.sql (usa tool_interactions, tool_name,
-- test_accounts y la tabla tool_load_latency_raw_latest de la extension). Despues correr
-- views_answers.sql, que escribe las respuestas en vivo.
--
-- Periodo de estas preguntas: los ultimos 28 dias, contando hoy, en hora de Bogota (desde hoy - 27
-- hasta hoy). Es una ventana que se mueve cada dia: asi el dashboard muestra cada sesion nueva en uno
-- o dos minutos, en vez de quedarse quieto hasta que cierre la semana. Unidad: una sesion completada
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
-- el periodo: los ultimos 28 dias en hora de Bogota, del dia hoy - 27 hasta hoy (hoy cuenta)
last_28_days AS (
  SELECT
    DATE_SUB(CURRENT_DATE('America/Bogota'), INTERVAL 27 DAY) AS first_day,
    CURRENT_DATE('America/Bogota') AS last_day
)
SELECT
  s.interaction_id,
  s.user_id,
  s.tool_id,
  `ark-mobiles.ark_dashboards.tool_name`(s.tool_id) AS tool,
  s.happened_at,
  DATE(s.happened_at, 'America/Bogota') AS day,
  -- semana de calendario (lunes a domingo); BQ13 no la usa, arma sus bloques de 7 dias con day
  DATE_TRUNC(DATE(s.happened_at, 'America/Bogota'), ISOWEEK) AS week_start,
  s.duration_s,
  -- recommendation, surprise_me, weather o catalog; null si la app no registro la apertura
  NULLIF(o.entry_point, 'unknown') AS entry_point,
  COALESCE(o.device_type, u.device_type, 'Unknown') AS device_type,
  ta.user_id IS NOT NULL AS is_test_account,
  -- true si cae en los ultimos 28 dias (el periodo de estas preguntas). se sigue llamando
  -- in_last_4_weeks porque Data Studio ya esta conectado con ese nombre: 28 dias = 4 bloques de 7
  DATE(s.happened_at, 'America/Bogota') BETWEEN w.first_day AND w.last_day AS in_last_4_weeks
FROM completed_sessions AS s
CROSS JOIN last_28_days AS w
LEFT JOIN last_open AS o ON o.interaction_id = s.interaction_id
LEFT JOIN device_per_user AS u ON u.user_id = s.user_id
LEFT JOIN `ark-mobiles.ark_dashboards.test_accounts` AS ta ON ta.user_id = s.user_id;

-- BQ13: cuantas veces por semana se completa cada herramienta en los ultimos 28 dias.
-- Las "semanas" son 4 bloques de 7 dias contados desde el primer dia del periodo: W1 = dias 1 a 7,
-- W2 = dias 8 a 14, W3 = dias 15 a 21 y W4 = los ultimos 7 dias (con hoy). No son de lunes a
-- domingo: asi la W4 ya trae las sesiones de hoy y no hay que esperar a que cierre la semana.
-- Una fila por herramienta, semana, tipo de dispositivo y cuenta de prueba, con 0 en las semanas
-- sin sesiones: asi la grafica distingue una herramienta habitual (todas las semanas) de una
-- esporadica (un pico en una sola semana), cosa que un promedio esconde.
-- Lee completed_tool_sessions una sola vez (BigQuery recalcula una vista cada vez que se nombra):
-- primero cuenta las 4 semanas de cada herramienta en 4 columnas y despues las pasa a 4 filas.
-- La respuesta se lee con device_type = 'Real phone' y is_test_account = false.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq13_weekly_tool_completions` AS
WITH
-- el primer dia del periodo (hoy - 27): ahi empieza la W1; las otras 3 empiezan 7, 14 y 21 dias despues
period AS (
  SELECT DATE_SUB(CURRENT_DATE('America/Bogota'), INTERVAL 27 DAY) AS first_day
),
-- unica lectura de las sesiones: a cada una le pone el numero de su bloque de 7 dias (1 a 4).
-- los dias desde el primer dia, divididos entre 7 (division entera), dan 0, 1, 2 o 3; +1 da la semana.
-- las de fuera del periodo quedan con semana 0: no se botan para que toda herramienta que se
-- completo alguna vez aparezca, aunque tenga 0 en las 4 semanas
sessions_with_week AS (
  SELECT
    s.tool,
    s.device_type,
    s.is_test_account,
    s.happened_at,
    s.in_last_4_weeks,
    IF(s.in_last_4_weeks, DIV(DATE_DIFF(s.day, p.first_day, DAY), 7) + 1, 0) AS week_number
  FROM `ark-mobiles.ark_dashboards.completed_tool_sessions` AS s
  CROSS JOIN period AS p
),
-- cuenta las sesiones de cada herramienta en cada una de las 4 semanas
completions_per_tool AS (
  SELECT
    tool,
    device_type,
    is_test_account,
    COUNTIF(week_number = 1) AS week_1,
    COUNTIF(week_number = 2) AS week_2,
    COUNTIF(week_number = 3) AS week_3,
    COUNTIF(week_number = 4) AS week_4,
    -- la ultima sesion de esta herramienta dentro del periodo
    MAX(IF(in_last_4_weeks, happened_at, NULL)) AS last_session_of_tool
  FROM sessions_with_week
  GROUP BY tool, device_type, is_test_account
),
-- la lista fija de las 4 semanas, para pasar las columnas week_1 ... week_4 a filas
the_4_weeks AS (
  SELECT 1 AS week_number, first_day AS week_start FROM period
  UNION ALL SELECT 2, DATE_ADD(first_day, INTERVAL 7 DAY) FROM period
  UNION ALL SELECT 3, DATE_ADD(first_day, INTERVAL 14 DAY) FROM period
  UNION ALL SELECT 4, DATE_ADD(first_day, INTERVAL 21 DAY) FROM period
)
SELECT
  c.tool,
  w.week_number,
  -- "W4 Nov 19 - Nov 25": el numero deja las semanas en orden en Data Studio
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

-- BQ14: cuanto se demoran los usuarios completando cada herramienta en los ultimos 28 dias
-- (in_last_4_weeks), en 3 rangos fijos:
-- menos de 1 minuto (1 a 59 s), de 1 a 3 minutos (60 a 180 s) y mas de 3 minutos (mas de 180 s).
-- Una fila por herramienta, rango, tipo de dispositivo y cuenta de prueba, con 0 en los rangos sin
-- sesiones (asi la barra al 100 % siempre tiene los 3 pedazos en el mismo orden).
-- Una duracion de 0 segundos, negativa o vacia es un error del reloj: no se puede clasificar, asi
-- que no entra en los rangos y se cuenta aparte en invalid_duration_sessions.
-- Lee completed_tool_sessions una sola vez: cuenta los 3 rangos en columnas y despues los pasa a filas.
-- La respuesta se lee con device_type = 'Real phone' y is_test_account = false.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq14_tool_duration_brackets` AS
WITH
-- unica lectura de las sesiones del periodo: cuenta las de cada rango por herramienta
sessions_per_bracket AS (
  SELECT
    tool,
    device_type,
    is_test_account,
    COUNTIF(duration_s BETWEEN 1 AND 59) AS under_1_min,
    COUNTIF(duration_s BETWEEN 60 AND 180) AS from_1_to_3_min,
    COUNTIF(duration_s > 180) AS over_3_min,
    COUNTIF(duration_s IS NULL OR duration_s <= 0) AS invalid_duration_sessions,
    MAX(happened_at) AS last_session_at
  FROM `ark-mobiles.ark_dashboards.completed_tool_sessions`
  WHERE in_last_4_weeks
  GROUP BY tool, device_type, is_test_account
),
-- la lista fija de los 3 rangos, en orden
brackets AS (
  SELECT 1 AS bracket_order, '1. Under 1 min' AS duration_bracket
  UNION ALL SELECT 2, '2. 1 to 3 min'
  UNION ALL SELECT 3, '3. Over 3 min'
),
-- una fila por herramienta y rango: las sesiones salen de la columna que le toca
tool_brackets AS (
  SELECT
    c.tool,
    b.duration_bracket,
    b.bracket_order,
    CASE b.bracket_order
      WHEN 1 THEN c.under_1_min
      WHEN 2 THEN c.from_1_to_3_min
      WHEN 3 THEN c.over_3_min
    END AS sessions,
    -- las sesiones que si caen en un rango: la base del porcentaje
    c.under_1_min + c.from_1_to_3_min + c.over_3_min AS sessions_with_bracket,
    c.invalid_duration_sessions,
    c.last_session_at,
    c.device_type,
    c.is_test_account
  FROM sessions_per_bracket AS c
  CROSS JOIN brackets AS b
)
SELECT
  tool,
  duration_bracket,
  bracket_order,
  sessions,
  -- porcentaje dentro de su herramienta (dispositivo y cuenta de prueba), no del total
  ROUND(100 * SAFE_DIVIDE(sessions, sessions_with_bracket), 1) AS pct_of_tool_sessions,
  invalid_duration_sessions,
  last_session_at,
  device_type,
  is_test_account
FROM tool_brackets;

-- BQ15: desde que entrada se abren las herramientas que se completan: las tres tarjetas del Home
-- (recomendacion, "Leave it to chance" y clima) o el catalogo (pestana Tools o "Browse full
-- catalog"). La app la guarda en cada apertura (entryPoint de tool_load_latency).
-- Una fila por entrada, herramienta, tipo de dispositivo y cuenta de prueba, con 0 en las entradas
-- sin sesiones. Las sesiones sin apertura registrada (versiones de la app anteriores al 27 de
-- octubre, o abiertas hace mas de 3 horas) van como 'Not recorded' y no se mezclan con las demas.
-- Lee completed_tool_sessions una sola vez: cuenta las 5 entradas en columnas y despues las pasa a filas.
-- La respuesta se lee con device_type = 'Real phone' y is_test_account = false.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq15_entry_points` AS
WITH
-- unica lectura de las sesiones del periodo: cuenta las de cada entrada por herramienta
sessions_per_entry AS (
  SELECT
    tool,
    device_type,
    is_test_account,
    COUNTIF(entry_point = 'recommendation') AS from_recommendation,
    COUNTIF(entry_point = 'surprise_me') AS from_surprise_me,
    COUNTIF(entry_point = 'weather') AS from_weather,
    COUNTIF(entry_point = 'catalog') AS from_catalog,
    -- sin apertura registrada; una entrada que la app no conoce todavia tambien cuenta aqui
    COUNTIF(entry_point IS NULL
      OR entry_point NOT IN ('recommendation', 'surprise_me', 'weather', 'catalog')) AS not_recorded,
    MAX(happened_at) AS last_session_at
  FROM `ark-mobiles.ark_dashboards.completed_tool_sessions`
  WHERE in_last_4_weeks
  GROUP BY tool, device_type, is_test_account
),
-- la lista fija de las 5 entradas, en orden
entry_points AS (
  SELECT 1 AS entry_order, 'Home: recommendation card' AS entry_point_label,
    'Home card' AS entry_group, TRUE AS has_entry_point
  UNION ALL SELECT 2, 'Home: Leave it to chance', 'Home card', TRUE
  UNION ALL SELECT 3, 'Home: weather card', 'Home card', TRUE
  UNION ALL SELECT 4, 'Tools catalog', 'Tools catalog', TRUE
  UNION ALL SELECT 5, 'Not recorded', 'Not recorded', FALSE
)
SELECT
  e.entry_point_label,
  e.entry_group,
  e.entry_order,
  e.has_entry_point,
  c.tool,
  -- las sesiones salen de la columna que le toca a la entrada
  CASE e.entry_order
    WHEN 1 THEN c.from_recommendation
    WHEN 2 THEN c.from_surprise_me
    WHEN 3 THEN c.from_weather
    WHEN 4 THEN c.from_catalog
    WHEN 5 THEN c.not_recorded
  END AS sessions,
  c.last_session_at,
  c.device_type,
  c.is_test_account
FROM sessions_per_entry AS c
CROSS JOIN entry_points AS e;
