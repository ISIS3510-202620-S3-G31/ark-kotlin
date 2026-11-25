-- Respuestas de las BQ de Kotlin escritas por BigQuery, para que el texto del dashboard se
-- actualice solo (un cuadro de texto de Data Studio no puede mostrar datos).
-- Una sola fila con una columna por pregunta. En cada pagina se muestra con una tabla de una
-- celda (sin encabezado ni paginacion). Cada respuesta empieza directo con el dato.
-- Solo celulares reales y sin cuentas de prueba.
--
-- Uso: correr despues de views.sql, views_analytics.sql, views_bq4_and_age.sql,
-- views_stats_visits.sql y views_tool_sessions.sql (lee sus vistas).

CREATE OR REPLACE FUNCTION `ark-mobiles.ark_dashboards.tool_name`(tool_id STRING) AS (
  CASE tool_id
    WHEN 'scream_tank' THEN 'Scream Tank'
    WHEN 'custom_breathing' THEN 'Custom Breathing'
    WHEN 'photo_of_the_day' THEN 'Photo of the Day'
    ELSE INITCAP(REPLACE(tool_id, '_', ' '))
  END
);

CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq_answers` AS
WITH
-- BQ13: sesiones completadas por semana de cada herramienta (ultimos 28 dias, en 4 bloques de 7).
-- la respuesta nombra su vista una sola vez (BigQuery recalcula una vista cada vez que se nombra,
-- y esta sale de completed_tool_sessions): todo se calcula en un solo SELECT sobre la vista.
-- las partes del texto, cada una en su columna. una fila por herramienta: la de la primera
-- semana ya trae el total y las 4 semanas
bq13_parts AS (
  SELECT
    -- el primer dia del periodo: la W1 empieza ahi
    MIN(week_start) AS first_day,
    STRING_AGG(FORMAT('%s %s (%d in total, completed in %d of 4 weeks)',
      tool, weekly_completions, completions_4_weeks, active_weeks),
      '; ' ORDER BY completions_4_weeks DESC, tool) AS per_tool,
    -- habituales: completadas las 4 semanas; esporadicas: una sola semana (null si no hay)
    STRING_AGG(IF(active_weeks = 4, tool, NULL), ', ' ORDER BY tool) AS habitual_tools,
    STRING_AGG(IF(active_weeks = 1, tool, NULL), ', ' ORDER BY tool) AS sporadic_tools
  FROM `ark-mobiles.ark_dashboards.bq13_weekly_tool_completions`
  WHERE device_type = 'Real phone' AND NOT is_test_account AND week_number = 1
),
-- arma el texto con las partes
bq13 AS (
  SELECT CONCAT(
    FORMAT('Completed sessions per week, %s to %s (W1 / W2 / W3 / W4): ',
      FORMAT_DATE('%b %d', first_day), FORMAT_DATE('%b %d', DATE_ADD(first_day, INTERVAL 27 DAY))),
    per_tool,
    '. ',
    IF(habitual_tools IS NULL,
      'No tool was completed in all 4 weeks.',
      CONCAT('Habitual (completed every week): ', habitual_tools, '.')),
    IF(sporadic_tools IS NULL,
      '',
      CONCAT(' Sporadic (a single week): ', sporadic_tools, '.'))) AS answer
  FROM bq13_parts
),
-- BQ14: cuanto se demoran completando cada herramienta, en 3 rangos (ultimos 28 dias).
-- lee la vista una sola vez; las ROW_NUMBER marcan el rango mas comun de cada herramienta y la
-- herramienta con mas porcentaje en cada rango, para no tener que volver a leer la vista
bq14_rows AS (
  SELECT
    tool,
    bracket_order,
    -- "under 1 min", "1 to 3 min" u "over 3 min"
    LOWER(SUBSTR(duration_bracket, 4)) AS bracket_name,
    sessions,
    pct_of_tool_sessions,
    invalid_duration_sessions,
    -- 1 = el rango con mas sesiones de la herramienta (si empatan, el mas corto)
    ROW_NUMBER() OVER (PARTITION BY tool ORDER BY sessions DESC, bracket_order) AS place_in_tool,
    -- 1 = la herramienta con mas porcentaje de sus sesiones en ese rango (si empatan, por nombre)
    ROW_NUMBER() OVER (PARTITION BY bracket_order ORDER BY pct_of_tool_sessions DESC, sessions DESC, tool)
      AS place_in_bracket
  FROM `ark-mobiles.ark_dashboards.bq14_tool_duration_brackets`
  WHERE device_type = 'Real phone' AND NOT is_test_account
),
-- las partes del texto, cada una en su columna
bq14_parts AS (
  SELECT
    SUM(sessions) AS total,
    SUM(IF(bracket_order = 1, sessions, 0)) AS under_1_min,
    SUM(IF(bracket_order = 2, sessions, 0)) AS from_1_to_3_min,
    SUM(IF(bracket_order = 3, sessions, 0)) AS over_3_min,
    -- el rango mas comun de cada herramienta, "Journaling under 1 min (60%)"
    STRING_AGG(IF(place_in_tool = 1 AND sessions > 0,
      CONCAT(tool, ' ', bracket_name, ' (', CAST(CAST(ROUND(pct_of_tool_sessions) AS INT64) AS STRING), '%)'),
      NULL), ', ' ORDER BY tool) AS most_common_per_tool,
    -- la que mas sesiones de mas de 3 minutos tiene: la que mas le cuesta terminar al usuario.
    -- solo una fila cumple, MAX la saca (null si ninguna)
    MAX(IF(bracket_order = 3 AND place_in_bracket = 1 AND sessions > 0,
      CONCAT(tool, ' (', CAST(CAST(ROUND(pct_of_tool_sessions) AS INT64) AS STRING), '% of its sessions over 3 min)'),
      NULL)) AS takes_longest,
    -- invalid_duration_sessions se repite en los 3 rangos de cada herramienta: se cuenta una vez
    SUM(IF(bracket_order = 1, invalid_duration_sessions, 0)) AS invalid
  FROM bq14_rows
),
-- arma el texto con las partes
bq14 AS (
  SELECT CONCAT(
    FORMAT('Of %d completed sessions in the last 28 days: ', total),
    'under 1 min ', CAST(CAST(ROUND(100 * SAFE_DIVIDE(under_1_min, total)) AS INT64) AS STRING), '%, ',
    '1 to 3 min ', CAST(CAST(ROUND(100 * SAFE_DIVIDE(from_1_to_3_min, total)) AS INT64) AS STRING), '%, ',
    'over 3 min ', CAST(CAST(ROUND(100 * SAFE_DIVIDE(over_3_min, total)) AS INT64) AS STRING), '%',
    '. Most common per tool: ',
    most_common_per_tool,
    '. ',
    IF(takes_longest IS NULL, 'No tool took over 3 min.', CONCAT('Takes longest: ', takes_longest, '.')),
    IF(invalid > 0, FORMAT(' %d sessions with a duration of 0 s or less were left out.', invalid), '')) AS answer
  FROM bq14_parts
),
-- BQ15: desde que entrada se abren las herramientas que se completan (ultimas 4 semanas completas).
-- lee la vista una sola vez y suma las sesiones de cada entrada (todas las herramientas juntas)
bq15_entries AS (
  SELECT
    entry_order,
    entry_point_label,
    entry_group,
    has_entry_point,
    SUM(sessions) AS entry_sessions
  FROM `ark-mobiles.ark_dashboards.bq15_entry_points`
  WHERE device_type = 'Real phone' AND NOT is_test_account
  GROUP BY entry_order, entry_point_label, entry_group, has_entry_point
),
-- a cada entrada le pega el total con entrada registrada (la base de los porcentajes); va con
-- OVER () y no con otro SELECT sobre bq15_entries para no leer la vista dos veces
bq15_entries_with_total AS (
  SELECT
    entry_order,
    entry_point_label,
    entry_group,
    has_entry_point,
    entry_sessions,
    SUM(IF(has_entry_point, entry_sessions, 0)) OVER () AS recorded
  FROM bq15_entries
),
-- las partes del texto, cada una en su columna
bq15_parts AS (
  SELECT
    MAX(recorded) AS recorded,
    -- "Tools catalog 60% (12)", de la entrada mas usada a la menos usada
    STRING_AGG(IF(has_entry_point,
      CONCAT(entry_point_label, ' ',
        CAST(CAST(ROUND(100 * SAFE_DIVIDE(entry_sessions, recorded)) AS INT64) AS STRING), '% (',
        CAST(entry_sessions AS STRING), ')'),
      NULL), ', ' ORDER BY entry_sessions DESC, entry_order) AS per_entry,
    SUM(IF(entry_group = 'Home card', entry_sessions, 0)) AS home,
    SUM(IF(has_entry_point, 0, entry_sessions)) AS not_recorded
  FROM bq15_entries_with_total
),
-- arma el texto con las partes
bq15 AS (
  SELECT CONCAT(
    FORMAT('Of %d completed sessions with a recorded entry point (last 4 complete weeks): ', recorded),
    per_entry,
    '. The three Home cards together: ',
    CAST(CAST(ROUND(100 * SAFE_DIVIDE(home, recorded)) AS INT64) AS STRING),
    '%.',
    IF(not_recorded > 0,
      FORMAT(' %d more sessions have no recorded entry point (older app versions).', not_recorded),
      '')) AS answer
  FROM bq15_parts
),
-- BQ1: herramienta mas lenta en abrir (mediana) y las demas
bq1_rows AS (
  SELECT
    `ark-mobiles.ark_dashboards.tool_name`(tool_id) AS tool,
    CAST(ROUND(p50_ms) AS INT64) AS p50,
    CAST(ROUND(p90_ms) AS INT64) AS p90,
    opens,
    ROW_NUMBER() OVER (ORDER BY p50_ms DESC) AS rank
  FROM `ark-mobiles.ark_dashboards.tool_load_latency_summary`
  WHERE device_type = 'Real phone'
),
bq1 AS (
  SELECT CONCAT(
    FORMAT('%s is the slowest tool to open (median %d ms, p90 %d ms, %d opens). ',
      MAX(IF(rank = 1, tool, NULL)), MAX(IF(rank = 1, p50, NULL)),
      MAX(IF(rank = 1, p90, NULL)), MAX(IF(rank = 1, opens, NULL))),
    'Others: ',
    STRING_AGG(IF(rank > 1, FORMAT('%s %d ms', tool, p50), NULL), ', ' ORDER BY p50 DESC),
    '. p50 = what a typical user waits; p90 = the slowest 10% of opens.') AS answer
  FROM bq1_rows
),
-- BQ9: minutos al dia por usuario y hora con mas uso
bq9_days AS (
  SELECT * FROM `ark-mobiles.ark_dashboards.daily_screen_time` WHERE device_type = 'Real phone'
),
bq9_hour AS (
  SELECT start_hour
  FROM `ark-mobiles.ark_dashboards.app_screen_time`
  WHERE device_type = 'Real phone' AND start_hour IS NOT NULL
  GROUP BY start_hour
  ORDER BY SUM(minutes) DESC
  LIMIT 1
),
bq9 AS (
  SELECT CONCAT(
    FORMAT('A typical user spends %.0f minutes a day in Ark (median of %d user-days from %d users, %s to %s), ',
      APPROX_QUANTILES(minutes, 100)[OFFSET(50)], COUNT(*), COUNT(DISTINCT user_key),
      FORMAT_DATE('%b %d', MIN(day)), FORMAT_DATE('%b %d', MAX(day))),
    FORMAT('and the heaviest 10%% spend %.0f minutes or more. ', APPROX_QUANTILES(minutes, 100)[OFFSET(90)]),
    FORMAT('The busiest hour starts at %s.',
      (SELECT LTRIM(FORMAT_TIME('%I %p', TIME(start_hour, 0, 0)), '0') FROM bq9_hour)),
    IF(COUNT(DISTINCT day) < 7,
      FORMAT(' With %d days of data, the day-of-week comparison is not reliable yet.', COUNT(DISTINCT day)),
      '')) AS answer
  FROM bq9_days
),
-- BQ7: visitas a Stats por usuario en la semana mas reciente
bq7_week AS (
  SELECT *
  FROM `ark-mobiles.firestore_export.vw_stats_weekly_kpis`
  WHERE device_type = 'Real phone'
  ORDER BY week_start DESC
  LIMIT 1
),
bq7 AS (
  SELECT CONCAT(
    FORMAT('Users open Stats %.2f times per week on average (week of %s: %d visits from %d users). ',
      avg_visits_per_user, FORMAT_DATE('%b %d', week_start), total_visits, active_users),
    'A visit is opening the Stats tab; refreshing or rotating the phone does not count.') AS answer
  FROM bq7_week
),
-- BQ5: localidades con mas check-ins dentro de Bogota y cuanto pesa lo que no tiene ubicacion
bq5_rows AS (
  SELECT * FROM `ark-mobiles.ark_dashboards.locality_emotion_summary` WHERE NOT is_synthetic
),
bq5 AS (
  SELECT CONCAT(
    'Inside Bogota: ',
    (SELECT STRING_AGG(
        FORMAT('%s %d check-ins (most frequent: %s; highest intensity: %s %.1f)',
          locality, total_checkins, most_frequent_emotion, highest_intensity_emotion, highest_avg_intensity),
        '; ' ORDER BY total_checkins DESC, locality LIMIT 3)
     FROM bq5_rows WHERE locality NOT IN ('Ubicación no especificada', 'Otra localidad')),
    FORMAT('. Careful: of %d check-ins, %d have no location and %d are outside Bogota, so this rests on %d inside the city.',
      SUM(total_checkins),
      SUM(IF(locality = 'Ubicación no especificada', total_checkins, 0)),
      SUM(IF(locality = 'Otra localidad', total_checkins, 0)),
      SUM(IF(locality NOT IN ('Ubicación no especificada', 'Otra localidad'), total_checkins, 0)))) AS answer
  FROM bq5_rows
),
-- BQ3: formato de herramienta que mas se completa
bq3_rows AS (
  SELECT *, ROW_NUMBER() OVER (ORDER BY total_completions DESC) AS rank
  FROM `ark-mobiles.ark_dashboards.bq4_tool_formats`
  WHERE device_type = 'Real phone' AND NOT is_test_account
),
bq3 AS (
  SELECT CONCAT(
    FORMAT('Users complete %s tools the most (%.1f%% of %d sessions), followed by ',
      INITCAP(MAX(IF(rank = 1, tool_format, NULL))), MAX(IF(rank = 1, completion_percentage, NULL)),
      SUM(total_completions)),
    STRING_AGG(IF(rank > 1, FORMAT('%s (%.1f%%)', INITCAP(tool_format), completion_percentage), NULL),
      ' and ' ORDER BY total_completions DESC),
    '.') AS answer
  FROM bq3_rows
),
-- BQ8: grupos de edad de los usuarios activos en 30 dias
bq8 AS (
  SELECT CONCAT(
    FORMAT('%d users were active in the last 30 days: ', SUM(active_users_30d)),
    STRING_AGG(FORMAT('%s %.1f%%', age_bucket, pct_of_active_users), ', ' ORDER BY active_users_30d DESC),
    '.') AS answer
  FROM `ark-mobiles.ark_dashboards.bq_age_distribution`
  WHERE NOT is_test_account
)
SELECT
  (SELECT answer FROM bq1) AS bq1_answer,
  (SELECT answer FROM bq3) AS bq3_answer,
  (SELECT answer FROM bq5) AS bq5_answer,
  (SELECT answer FROM bq7) AS bq7_answer,
  (SELECT answer FROM bq8) AS bq8_answer,
  (SELECT answer FROM bq9) AS bq9_answer,
  (SELECT answer FROM bq13) AS bq13_answer,
  (SELECT answer FROM bq14) AS bq14_answer,
  (SELECT answer FROM bq15) AS bq15_answer;
