-- Sesiones terminadas de Custom Breathing y las preguntas que salen de ellas (Sprint 5): BQ16.
-- La app ya guarda los campos de las tres preguntas de respiracion (ciclos, patron y musica):
-- las otras dos preguntas agregan sus vistas al final de este archivo, sobre custom_breathing_sessions.
--
-- Origen: firestore_export.custom_breathing_sessions_raw_latest, la instancia de "Stream Firestore
-- to BigQuery" de users/{uid}/custom_breathing_sessions. La app escribe un documento solo cuando el
-- usuario toca Finish despues de al menos un ciclo, asi que cada fila ya es una sesion terminada.
-- No se llama breathing_sessions porque esa coleccion es de la app de flutter, con otros campos.
--
-- Uso: correr despues de views.sql y views_bq4_and_age.sql (usa to_ts y test_accounts) y antes
-- de armar la pagina de Data Studio. No hace falta volver a correr views_answers.sql.

-- Una fila por sesion terminada de Custom Breathing.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.custom_breathing_sessions` AS
SELECT
  b.document_id AS session_id,
  JSON_VALUE(b.path_params, '$.uid') AS user_id,
  `ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(b.data, '$.endedAt')) AS ended_at,
  SAFE_CAST(JSON_VALUE(b.data, '$.completedCycles') AS INT64) AS completed_cycles,
  SAFE_CAST(JSON_VALUE(b.data, '$.durationSeconds') AS INT64) AS duration_s,
  -- BOX, RELAXING, QUICK_CALM o CUSTOM (patron mas usado)
  JSON_VALUE(b.data, '$.patternType') AS pattern_type,
  -- sesiones con musica de fondo
  SAFE_CAST(JSON_VALUE(b.data, '$.withMusic') AS BOOL) AS with_music,
  SAFE_CAST(JSON_VALUE(b.data, '$.musicSeconds') AS INT64) AS music_seconds,
  -- el emulador de Android Studio no representa a un usuario: se separa con la misma regla de
  -- las demas vistas
  CASE
    WHEN JSON_VALUE(b.data, '$.deviceModel') IS NULL THEN 'Unknown'
    WHEN LOWER(JSON_VALUE(b.data, '$.deviceModel')) LIKE 'sdk_gphone%'
      OR LOWER(JSON_VALUE(b.data, '$.deviceModel')) LIKE '%emulator%'
      OR LOWER(JSON_VALUE(b.data, '$.deviceModel')) LIKE 'generic%' THEN 'Emulator'
    ELSE 'Real phone'
  END AS device_type,
  ta.user_id IS NOT NULL AS is_test_account
FROM `ark-mobiles.firestore_export.custom_breathing_sessions_raw_latest` AS b
LEFT JOIN `ark-mobiles.ark_dashboards.test_accounts` AS ta
  ON ta.user_id = JSON_VALUE(b.path_params, '$.uid')
WHERE SAFE_CAST(JSON_VALUE(b.data, '$.completedCycles') AS INT64) >= 1;

-- BQ16: cuantos ciclos de respiracion completan los usuarios por sesion de Custom Breathing.
-- Una fila por numero de ciclos (1 a 9 y "10+"), tipo de dispositivo y cuenta de prueba, con 0
-- donde no hay sesiones: asi las barras y la curva no se saltan ningun numero.
--   sessions          -> sesiones que terminaron con exactamente ese numero de ciclos (las barras)
--   sessions_at_least -> sesiones que llegaron por lo menos a ese numero de ciclos (la curva)
--   group_sessions    -> todas las sesiones del grupo; en Data Studio el porcentaje de la curva es
--                        SUM(sessions_at_least) / SUM(group_sessions)
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq16_cycles_distribution` AS
WITH
-- cada sesion con su numero de ciclos, y 10 para todas las de 10 ciclos o mas
sessions AS (
  SELECT
    device_type,
    is_test_account,
    LEAST(completed_cycles, 10) AS cycles_order
  FROM `ark-mobiles.ark_dashboards.custom_breathing_sessions`
),
-- los grupos que existen (tipo de dispositivo y cuenta de prueba) con su total de sesiones
groups AS (
  SELECT device_type, is_test_account, COUNT(*) AS group_sessions
  FROM sessions
  GROUP BY device_type, is_test_account
),
-- los 10 numeros de ciclos para cada grupo, aunque no tengan sesiones
all_rows AS (
  SELECT g.device_type, g.is_test_account, g.group_sessions, cycles_order
  FROM groups AS g
  CROSS JOIN UNNEST(GENERATE_ARRAY(1, 10)) AS cycles_order
)
SELECT
  r.device_type,
  r.is_test_account,
  -- texto del eje X: "1" ... "9" y "10+"; para ordenar el eje se usa cycles_order
  IF(r.cycles_order = 10, '10+', CAST(r.cycles_order AS STRING)) AS cycles_label,
  r.cycles_order,
  COUNTIF(s.cycles_order = r.cycles_order) AS sessions,
  COUNTIF(s.cycles_order >= r.cycles_order) AS sessions_at_least,
  r.group_sessions
FROM all_rows AS r
LEFT JOIN sessions AS s
  ON s.device_type = r.device_type AND s.is_test_account = r.is_test_account
GROUP BY r.device_type, r.is_test_account, r.cycles_order, r.group_sessions;

-- BQ16: los numeros de los scorecards, una fila por tipo de dispositivo y cuenta de prueba.
--   total_sessions -> sesiones terminadas
--   median_cycles  -> la sesion tipica: la mitad de las sesiones tiene esos ciclos o menos
--   mode_cycles    -> el numero de ciclos mas comun (si empatan, el menor)
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq16_cycles_summary` AS
WITH
-- cuantas sesiones hay con cada numero exacto de ciclos, numeradas de la mas comun a la menos
cycle_counts AS (
  SELECT
    device_type,
    is_test_account,
    completed_cycles,
    COUNT(*) AS sessions,
    ROW_NUMBER() OVER (
      PARTITION BY device_type, is_test_account
      ORDER BY COUNT(*) DESC, completed_cycles) AS place
  FROM `ark-mobiles.ark_dashboards.custom_breathing_sessions`
  GROUP BY device_type, is_test_account, completed_cycles
),
-- el total y la mediana de cada grupo
totals AS (
  SELECT
    device_type,
    is_test_account,
    COUNT(*) AS total_sessions,
    APPROX_QUANTILES(completed_cycles, 100)[OFFSET(50)] AS median_cycles
  FROM `ark-mobiles.ark_dashboards.custom_breathing_sessions`
  GROUP BY device_type, is_test_account
)
SELECT
  t.device_type,
  t.is_test_account,
  t.total_sessions,
  t.median_cycles,
  c.completed_cycles AS mode_cycles,
  ROUND(100 * c.sessions / t.total_sessions, 1) AS mode_pct_of_sessions
FROM totals AS t
JOIN cycle_counts AS c
  ON c.device_type = t.device_type AND c.is_test_account = t.is_test_account AND c.place = 1;

-- BQ16: la respuesta escrita para la pagina de Data Studio (una tabla de una celda con bq16_answer).
-- Va en su propia vista para no tocar bq_answers de las demas BQ.
-- Solo celulares reales y sin cuentas de prueba.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq16_answer` AS
SELECT FORMAT(
  'Of %d completed Custom Breathing sessions, the typical one (median) reaches %d cycles and the most common is %d cycles (%.1f%% of sessions).',
  total_sessions, median_cycles, mode_cycles, mode_pct_of_sessions) AS bq16_answer
FROM `ark-mobiles.ark_dashboards.bq16_cycles_summary`
WHERE device_type = 'Real phone' AND NOT is_test_account;
