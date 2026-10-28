-- Ark: vistas sobre Firebase Analytics (analytics_556383466.events_*, que crea el enlace
-- Firebase Analytics -> BigQuery). Se corre despues de views.sql. En otro proyecto, cambiar
-- analytics_556383466 por el dataset que aparece en BigQuery despues de enlazar.

-- BQ1: latencia de carga por herramienta, desde Firebase Analytics.
-- events_* incluye las tablas diarias y events_intraday_* (la del streaming, que se llena
-- a los pocos minutos).
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.tool_load_latency` AS
WITH events AS (
  SELECT
    TIMESTAMP_MICROS(event_timestamp) AS happened_at,
    app_info.id AS app_id, -- com.moviles.ark = Kotlin; la app Flutter trae su propio id
    user_pseudo_id,
    user_id, -- uid de Firebase Auth (desde la version que llama setUserId); null en eventos viejos
    (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'tool_id') AS tool_id,
    (SELECT COALESCE(value.int_value, CAST(value.double_value AS INT64))
       FROM UNNEST(event_params) WHERE key = 'latency_ms') AS latency_ms,
    -- recommendation, surprise_me, weather o catalog; 'unknown' en eventos de versiones viejas
    COALESCE((SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'entry_point'), 'unknown') AS entry_point,
    -- 1 = primera herramienta desde que arranco la app (carga en frio)
    (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'first_open') AS first_open,
    -- mobile_model_name casi siempre viene vacio; el modelo de hardware si llega siempre
    COALESCE(device.mobile_marketing_name, device.mobile_model_name, device.mobile_os_hardware_model) AS device_model,
    -- el emulador de Android Studio corre en el Mac y no se parece a un telefono: se separa
    IF(LOWER(device.mobile_os_hardware_model) LIKE 'sdk_gphone%'
         OR LOWER(device.mobile_os_hardware_model) LIKE '%emulator%'
         OR LOWER(device.mobile_os_hardware_model) LIKE 'generic%',
       'Emulator', 'Real phone') AS device_type,
    device.operating_system_version AS os_version,
    app_info.version AS app_version
  FROM `ark-mobiles.analytics_556383466.events_*`
  WHERE event_name = 'tool_load_latency'
)
SELECT
  *,
  CASE
    WHEN first_open = 1 THEN 'cold (first tool)'
    WHEN first_open = 0 THEN 'warm'
    ELSE 'unknown'
  END AS open_type,
  -- rangos para el histograma; el prefijo numerico deja las barras en orden en Looker Studio
  CASE
    WHEN latency_ms < 100 THEN '1. <100 ms'
    WHEN latency_ms < 200 THEN '2. 100-199 ms'
    WHEN latency_ms < 300 THEN '3. 200-299 ms'
    WHEN latency_ms < 500 THEN '4. 300-499 ms'
    WHEN latency_ms < 1000 THEN '5. 500-999 ms'
    ELSE '6. >=1000 ms'
  END AS latency_bucket
FROM events
WHERE latency_ms IS NOT NULL;

-- BQ1: resumen por herramienta y tipo de dispositivo.
-- p50 = lo que espera un usuario tipico; p90 = el 10 % mas lento (el peor caso frecuente);
-- opens = cuantas mediciones hay, para saber cuanto confiar en lo demas.
-- p95 no se incluye: con menos de ~40 aperturas por herramienta es casi la mas lenta de todas.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.tool_load_latency_summary` AS
SELECT
  tool_id,
  device_type,
  COUNT(*) AS opens,
  APPROX_QUANTILES(latency_ms, 100)[OFFSET(50)] AS p50_ms,
  APPROX_QUANTILES(latency_ms, 100)[OFFSET(90)] AS p90_ms
FROM `ark-mobiles.ark_dashboards.tool_load_latency`
WHERE app_id = 'com.moviles.ark'
GROUP BY tool_id, device_type;

-- BQ4 desde Analytics (complementa tool_interactions de Firestore).
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.tool_entry_completed` AS
SELECT
  TIMESTAMP_MICROS(event_timestamp) AS happened_at,
  app_info.id AS app_id, -- com.moviles.ark = Kotlin; la app Flutter trae su propio id
  user_pseudo_id,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'tool_id') AS tool_id,
  UPPER((SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'tool_format')) AS tool_format
FROM `ark-mobiles.analytics_556383466.events_*`
WHERE event_name = 'tool_entry_completed';

-- Tiempo de pantalla diario por usuario (tipo 4).
-- La app manda app_screen_time cada vez que pasa a segundo plano, con day (dia local
-- yyyy-MM-dd), duration_ms y start_hour; una sesion que cruza la medianoche llega partida.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.app_screen_time` AS
SELECT
  TIMESTAMP_MICROS(event_timestamp) AS happened_at,
  -- la cuenta si ya habia sesion; si no, la instalacion (user_pseudo_id)
  COALESCE(user_id, CONCAT('device:', user_pseudo_id)) AS user_key,
  user_id,
  user_pseudo_id,
  PARSE_DATE('%Y-%m-%d', (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'day')) AS day,
  (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'duration_ms') AS duration_ms,
  (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'start_hour') AS start_hour,
  device.mobile_model_name AS device_model,
  app_info.version AS app_version
FROM `ark-mobiles.analytics_556383466.events_*`
WHERE event_name = 'app_screen_time'
  AND app_info.id = 'com.moviles.ark';

-- Una fila por usuario y dia: minutos totales y cuantas veces abrio la app ese dia.
-- Es la tabla base de la pregunta: el "screentime diario por usuario".
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.daily_screen_time` AS
SELECT
  s.user_key,
  s.day,
  -- emulador o celular real: el emulador son pruebas del equipo, no usuarios
  s.device_type,
  FORMAT_DATE('%A', s.day) AS weekday,
  EXTRACT(DAYOFWEEK FROM s.day) AS weekday_number, -- 1 = domingo, para ordenar
  COUNT(*) AS sessions,
  ROUND(SUM(s.duration_ms) / 60000, 1) AS minutes,
  CASE
    WHEN SUM(s.duration_ms) < 2 * 60000 THEN '1. <2 min'
    WHEN SUM(s.duration_ms) < 5 * 60000 THEN '2. 2-5 min'
    WHEN SUM(s.duration_ms) < 10 * 60000 THEN '3. 5-10 min'
    WHEN SUM(s.duration_ms) < 20 * 60000 THEN '4. 10-20 min'
    WHEN SUM(s.duration_ms) < 40 * 60000 THEN '5. 20-40 min'
    ELSE '6. >=40 min'
  END AS minutes_bucket,
  -- rango de edad desde Firestore (solo si el evento trae user_id)
  COALESCE(ANY_VALUE(u.age_bucket), 'Unknown') AS age_bucket
FROM `ark-mobiles.ark_dashboards.app_screen_time` s
LEFT JOIN `ark-mobiles.ark_dashboards.users` u ON u.user_id = s.user_id
WHERE s.day IS NOT NULL AND s.duration_ms > 0
GROUP BY s.user_key, s.day, s.device_type;

-- Resumen por dia de la distribucion entre usuarios: cuantos usaron la app, la mediana y
-- los cuartiles de minutos. Sirve para una serie de tiempo con p25/p50/p75 (la "banda").
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.daily_screen_time_summary` AS
SELECT
  day,
  device_type,
  COUNT(*) AS active_users,
  ROUND(AVG(minutes), 1) AS avg_minutes,
  APPROX_QUANTILES(minutes, 100)[OFFSET(25)] AS p25_minutes,
  APPROX_QUANTILES(minutes, 100)[OFFSET(50)] AS p50_minutes,
  APPROX_QUANTILES(minutes, 100)[OFFSET(75)] AS p75_minutes,
  APPROX_QUANTILES(minutes, 100)[OFFSET(90)] AS p90_minutes,
  ROUND(SUM(minutes), 1) AS total_minutes
FROM `ark-mobiles.ark_dashboards.daily_screen_time`
GROUP BY day, device_type;

-- ============================================================================
-- PASO 2 (BQ1 casi en tiempo real). Correr SOLO despues de:
--   1. publicar firestore.rules del repo ark-kotlin (permite users/{uid}/tool_load_latency);
--   2. instalar otra instancia de "Stream Firestore to BigQuery" con
--      Collection path = users/{uid}/tool_load_latency, Table ID = tool_load_latency,
--      Dataset ID = firestore_export, wildcard = Yes (mismos valores que las otras 4).
-- Desde la version de la app que guarda la latencia en Firestore, cada apertura llega a
-- BigQuery en segundos. Analytics la sigue recibiendo (en tandas de hasta una hora): para no
-- contarla dos veces, de Analytics solo se toma lo anterior a la primera fila de Firestore.
-- Reemplaza la vista tool_load_latency de arriba con las mismas columnas (+ source), asi el
-- dashboard no se rompe; tool_load_latency_summary se recalcula sola porque lee de esta vista.
-- ============================================================================
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.tool_load_latency` AS
WITH firestore_rows AS (
  SELECT
    `ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(data, '$.timestamp')) AS happened_at,
    'com.moviles.ark' AS app_id,
    CAST(NULL AS STRING) AS user_pseudo_id,
    JSON_VALUE(path_params, '$.uid') AS user_id,
    JSON_VALUE(data, '$.toolId') AS tool_id,
    SAFE_CAST(JSON_VALUE(data, '$.latencyMs') AS INT64) AS latency_ms,
    COALESCE(JSON_VALUE(data, '$.entryPoint'), 'unknown') AS entry_point,
    IF(JSON_VALUE(data, '$.firstOpen') = 'true', 1, 0) AS first_open,
    JSON_VALUE(data, '$.deviceModel') AS device_model,
    CONCAT('Android ', JSON_VALUE(data, '$.osVersion')) AS os_version,
    CAST(NULL AS STRING) AS app_version,
    'firestore' AS source
  FROM `ark-mobiles.firestore_export.tool_load_latency_raw_latest`
),
cutover AS (
  -- primera medicion valida que llego por Firestore; si todavia no hay ninguna, todo sale de Analytics
  SELECT COALESCE(MIN(happened_at), TIMESTAMP '9999-12-31') AS since
  FROM firestore_rows
  WHERE latency_ms IS NOT NULL AND happened_at IS NOT NULL
),
analytics_rows AS (
  SELECT
    TIMESTAMP_MICROS(event_timestamp) AS happened_at,
    app_info.id AS app_id,
    user_pseudo_id,
    user_id,
    (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'tool_id') AS tool_id,
    (SELECT COALESCE(value.int_value, CAST(value.double_value AS INT64))
       FROM UNNEST(event_params) WHERE key = 'latency_ms') AS latency_ms,
    COALESCE((SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'entry_point'), 'unknown') AS entry_point,
    (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'first_open') AS first_open,
    COALESCE(device.mobile_marketing_name, device.mobile_model_name, device.mobile_os_hardware_model) AS device_model,
    device.operating_system_version AS os_version,
    app_info.version AS app_version,
    'analytics' AS source
  FROM `ark-mobiles.analytics_556383466.events_*`
  WHERE event_name = 'tool_load_latency'
    AND TIMESTAMP_MICROS(event_timestamp) < (SELECT since FROM cutover)
),
events AS (
  SELECT * FROM firestore_rows
  UNION ALL
  SELECT * FROM analytics_rows
)
SELECT
  happened_at, app_id, user_pseudo_id, user_id, tool_id, latency_ms, entry_point, first_open,
  device_model,
  IF(LOWER(device_model) LIKE 'sdk_gphone%'
       OR LOWER(device_model) LIKE '%emulator%'
       OR LOWER(device_model) LIKE 'generic%',
     'Emulator', 'Real phone') AS device_type,
  os_version, app_version,
  CASE
    WHEN first_open = 1 THEN 'cold (first tool)'
    WHEN first_open = 0 THEN 'warm'
    ELSE 'unknown'
  END AS open_type,
  CASE
    WHEN latency_ms < 100 THEN '1. <100 ms'
    WHEN latency_ms < 200 THEN '2. 100-199 ms'
    WHEN latency_ms < 300 THEN '3. 200-299 ms'
    WHEN latency_ms < 500 THEN '4. 300-499 ms'
    WHEN latency_ms < 1000 THEN '5. 500-999 ms'
    ELSE '6. >=1000 ms'
  END AS latency_bucket,
  source
FROM events
WHERE latency_ms IS NOT NULL;

-- ============================================================================
-- PASO 2 (tiempo de pantalla casi en tiempo real). Correr SOLO despues de publicar las reglas
-- (permiten users/{uid}/screen_time) e instalar otra instancia de la extension con
-- Collection path = users/{uid}/screen_time, Table ID = screen_time (lo demas igual).
-- La app manda un pedazo cada minuto mientras esta abierta y otro al salir; sumando los pedazos
-- de un dia sale el tiempo de pantalla de ese dia. Reemplaza la vista app_screen_time de arriba
-- (mismas columnas + source); daily_screen_time y daily_screen_time_summary leen de esta vista.
-- ============================================================================
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.app_screen_time` AS
WITH firestore_rows AS (
  SELECT
    `ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(data, '$.timestamp')) AS happened_at,
    JSON_VALUE(path_params, '$.uid') AS user_id,
    CAST(NULL AS STRING) AS user_pseudo_id,
    SAFE.PARSE_DATE('%Y-%m-%d', JSON_VALUE(data, '$.day')) AS day,
    SAFE_CAST(JSON_VALUE(data, '$.durationMs') AS INT64) AS duration_ms,
    SAFE_CAST(JSON_VALUE(data, '$.startHour') AS INT64) AS start_hour,
    JSON_VALUE(data, '$.deviceModel') AS device_model,
    CAST(NULL AS STRING) AS app_version,
    'firestore' AS source
  FROM `ark-mobiles.firestore_export.screen_time_raw_latest`
),
cutover AS (
  -- primer pedazo valido que llego por Firestore; si todavia no hay ninguno, todo sale de Analytics
  SELECT COALESCE(MIN(happened_at), TIMESTAMP '9999-12-31') AS since
  FROM firestore_rows
  WHERE duration_ms IS NOT NULL AND happened_at IS NOT NULL
),
analytics_rows AS (
  SELECT
    TIMESTAMP_MICROS(event_timestamp) AS happened_at,
    user_id,
    user_pseudo_id,
    SAFE.PARSE_DATE('%Y-%m-%d', (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'day')) AS day,
    (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'duration_ms') AS duration_ms,
    (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'start_hour') AS start_hour,
    COALESCE(device.mobile_marketing_name, device.mobile_model_name, device.mobile_os_hardware_model) AS device_model,
    app_info.version AS app_version,
    'analytics' AS source
  FROM `ark-mobiles.analytics_556383466.events_*`
  WHERE event_name = 'app_screen_time'
    AND app_info.id = 'com.moviles.ark'
    AND TIMESTAMP_MICROS(event_timestamp) < (SELECT since FROM cutover)
),
events AS (
  SELECT * FROM firestore_rows
  UNION ALL
  SELECT * FROM analytics_rows
)
SELECT
  happened_at,
  COALESCE(user_id, CONCAT('device:', user_pseudo_id)) AS user_key,
  user_id, user_pseudo_id, day, duration_ms, start_hour, device_model,
  IF(LOWER(device_model) LIKE 'sdk_gphone%'
       OR LOWER(device_model) LIKE '%emulator%'
       OR LOWER(device_model) LIKE 'generic%',
     'Emulator', 'Real phone') AS device_type,
  app_version, source,
  ROUND(duration_ms / 60000, 2) AS minutes
FROM events
WHERE day IS NOT NULL AND duration_ms IS NOT NULL;
