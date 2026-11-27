-- Sesiones terminadas de Custom Breathing y las preguntas que salen de ellas (Sprint 5): BQ16, BQ17 y BQ18.
-- La app guarda los campos de las tres preguntas de respiracion (ciclos, patron y musica) y cada
-- pregunta tiene aqui su bloque de vistas, todas sobre custom_breathing_sessions.
--
-- Origen: firestore_export.custom_breathing_sessions_raw_latest, la instancia de "Stream Firestore
-- to BigQuery" de users/{uid}/custom_breathing_sessions. La app escribe un documento solo cuando el
-- usuario toca Finish despues de al menos un ciclo, asi que cada fila ya es una sesion terminada.
-- No se llama breathing_sessions porque esa coleccion es de la app de flutter, con otros campos.
--
-- Uso: correr despues de views.sql y views_bq4_and_age.sql (usa to_ts y test_accounts).
-- Cada BQ tiene aqui su propia vista de respuesta (bq16_answer, bq17_answer): no hace falta
-- correr views_answers.sql.

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

-- BQ17: que patrones de respiracion se usan mas en las sesiones terminadas de Custom Breathing.
-- Una fila por patron, tipo de dispositivo y cuenta de prueba. Los 4 patrones salen siempre, con 0
-- si nadie los uso: que un patron no se use tambien es una respuesta.
--   sessions -> sesiones terminadas con ese patron (barras y anillo; el anillo saca el % solo)
--   users    -> usuarios distintos que terminaron al menos una sesion con ese patron
-- Las sesiones sin patternType (pruebas de antes de que la app lo guardara) no se cuentan.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq17_pattern_usage` AS
WITH
-- los 4 patrones con el nombre que se muestra en Data Studio y un orden fijo
patterns AS (
  SELECT * FROM UNNEST([
    STRUCT('BOX' AS pattern_type, 'Box Breathing (4-4-4-4)' AS pattern_label, 1 AS pattern_order),
    ('RELAXING', 'Relaxing (4-7-8)', 2),
    ('QUICK_CALM', 'Quick Calm (4-6)', 3),
    ('CUSTOM', 'Custom', 4)
  ])
),
-- los grupos que existen (tipo de dispositivo y cuenta de prueba)
groups AS (
  SELECT DISTINCT device_type, is_test_account
  FROM `ark-mobiles.ark_dashboards.custom_breathing_sessions`
),
-- los 4 patrones para cada grupo, aunque no tengan sesiones
all_rows AS (
  SELECT g.device_type, g.is_test_account, p.pattern_type, p.pattern_label, p.pattern_order
  FROM groups AS g
  CROSS JOIN patterns AS p
)
SELECT
  r.device_type,
  r.is_test_account,
  r.pattern_type,
  r.pattern_label,
  r.pattern_order,
  COUNT(s.session_id) AS sessions,
  COUNT(DISTINCT s.user_id) AS users
FROM all_rows AS r
LEFT JOIN `ark-mobiles.ark_dashboards.custom_breathing_sessions` AS s
  ON s.device_type = r.device_type
  AND s.is_test_account = r.is_test_account
  AND s.pattern_type = r.pattern_type
GROUP BY r.device_type, r.is_test_account, r.pattern_type, r.pattern_label, r.pattern_order;

-- BQ17: la respuesta escrita para la pagina de Data Studio (una tabla de una celda con bq17_answer).
-- Va en su propia vista para no tocar bq_answers de las demas BQ.
-- Solo celulares reales y sin cuentas de prueba.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq17_answer` AS
WITH
-- cada patron con el total de sesiones y su puesto (1 = el mas usado; si empatan, el orden fijo)
usage AS (
  SELECT
    pattern_label,
    sessions,
    users,
    SUM(sessions) OVER () AS total_sessions,
    ROW_NUMBER() OVER (ORDER BY sessions DESC, pattern_order) AS place
  FROM `ark-mobiles.ark_dashboards.bq17_pattern_usage`
  WHERE device_type = 'Real phone' AND NOT is_test_account
)
SELECT CONCAT(
  FORMAT('%s is the most used pattern: %d of %d completed sessions (%.1f%%) by %d users. Then ',
    MAX(IF(place = 1, pattern_label, NULL)),
    MAX(IF(place = 1, sessions, NULL)),
    MAX(total_sessions),
    100 * SAFE_DIVIDE(MAX(IF(place = 1, sessions, NULL)), MAX(total_sessions)),
    MAX(IF(place = 1, users, NULL))),
  STRING_AGG(IF(place > 1, FORMAT('%s %.1f%%', pattern_label, 100 * SAFE_DIVIDE(sessions, total_sessions)), NULL),
    ', ' ORDER BY place),
  '.') AS bq17_answer
FROM usage;

-- BQ18: que porcentaje de las sesiones terminadas de Custom Breathing se hacen con musica de fondo
-- y cuantas en silencio. Una fila por "With music" / "In silence", tipo de dispositivo y cuenta de
-- prueba, con 0 si no hay sesiones (para el anillo; el anillo saca el % solo).
-- Con musica = la musica sono en algun momento mientras el ejercicio corria (withMusic de la app).
-- Las sesiones sin withMusic (pruebas de antes de que la app lo guardara) no se cuentan.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq18_music_usage` AS
WITH
-- las dos opciones con el nombre que se muestra en Data Studio y un orden fijo
music_options AS (
  SELECT * FROM UNNEST([
    STRUCT(TRUE AS with_music, 'With music' AS music_label, 1 AS music_order),
    (FALSE, 'In silence', 2)
  ])
),
-- los grupos que existen (tipo de dispositivo y cuenta de prueba)
session_groups AS (
  SELECT DISTINCT device_type, is_test_account
  FROM `ark-mobiles.ark_dashboards.custom_breathing_sessions`
),
-- las dos opciones para cada grupo, aunque no tengan sesiones
all_rows AS (
  SELECT g.device_type, g.is_test_account, m.with_music, m.music_label, m.music_order
  FROM session_groups AS g
  CROSS JOIN music_options AS m
)
SELECT
  r.device_type,
  r.is_test_account,
  r.music_label,
  r.music_order,
  COUNT(s.session_id) AS sessions
FROM all_rows AS r
LEFT JOIN `ark-mobiles.ark_dashboards.custom_breathing_sessions` AS s
  ON s.device_type = r.device_type
  AND s.is_test_account = r.is_test_account
  AND s.with_music = r.with_music
GROUP BY r.device_type, r.is_test_account, r.music_label, r.music_order;

-- BQ18: en las sesiones con musica, que parte del ejercicio sono la musica, en 4 rangos.
-- Parte con musica = music_seconds / duration_s (la app guarda music_seconds sin pasar de duration_s).
-- Una fila por rango, tipo de dispositivo y cuenta de prueba, con 0 si no hay sesiones (para las barras).
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq18_music_share` AS
WITH
-- los 4 rangos con el nombre que se muestra en Data Studio y un orden fijo
share_ranges AS (
  SELECT * FROM UNNEST([
    STRUCT(1 AS range_order, 'Under 25%' AS range_label),
    (2, '25% to 50%'),
    (3, '50% to 75%'),
    (4, '75% to 100%')
  ])
),
-- cada sesion con musica y el numero de su rango
music_sessions AS (
  SELECT
    device_type,
    is_test_account,
    CASE
      WHEN SAFE_DIVIDE(music_seconds, duration_s) < 0.25 THEN 1
      WHEN SAFE_DIVIDE(music_seconds, duration_s) < 0.50 THEN 2
      WHEN SAFE_DIVIDE(music_seconds, duration_s) < 0.75 THEN 3
      ELSE 4
    END AS range_order
  FROM `ark-mobiles.ark_dashboards.custom_breathing_sessions`
  WHERE with_music AND duration_s > 0
),
-- los grupos que existen (tipo de dispositivo y cuenta de prueba)
session_groups AS (
  SELECT DISTINCT device_type, is_test_account
  FROM `ark-mobiles.ark_dashboards.custom_breathing_sessions`
),
-- los 4 rangos para cada grupo, aunque no tengan sesiones
all_rows AS (
  SELECT g.device_type, g.is_test_account, r.range_order, r.range_label
  FROM session_groups AS g
  CROSS JOIN share_ranges AS r
)
SELECT
  a.device_type,
  a.is_test_account,
  a.range_label,
  a.range_order,
  COUNTIF(m.range_order = a.range_order) AS sessions
FROM all_rows AS a
LEFT JOIN music_sessions AS m
  ON m.device_type = a.device_type AND m.is_test_account = a.is_test_account
GROUP BY a.device_type, a.is_test_account, a.range_label, a.range_order;
