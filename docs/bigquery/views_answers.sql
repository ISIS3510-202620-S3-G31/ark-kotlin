-- Respuestas de las BQ de Kotlin escritas por BigQuery, para que el texto del dashboard se
-- actualice solo (un cuadro de texto de Data Studio no puede mostrar datos).
-- Una sola fila con una columna por pregunta. En cada pagina se muestra con una tabla de una
-- celda (sin encabezado ni paginacion). Cada respuesta empieza con la fecha y hora del ultimo dato
-- que la alimenta, en hora de Bogota. Solo celulares reales y sin cuentas de prueba.
--
-- Uso: correr despues de views.sql, views_analytics.sql, views_bq4_and_age.sql y
-- views_stats_visits.sql (lee sus vistas).

CREATE OR REPLACE FUNCTION `ark-mobiles.ark_dashboards.as_of`(ts TIMESTAMP) AS (
  CONCAT('Updated with data up to ', FORMAT_TIMESTAMP('%b %d, %Y %I:%M %p', ts, 'America/Bogota'),
    ' (Bogota time). ')
);

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
    (SELECT `ark-mobiles.ark_dashboards.as_of`(MAX(happened_at))
     FROM `ark-mobiles.ark_dashboards.tool_load_latency` WHERE device_type = 'Real phone'),
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
    (SELECT `ark-mobiles.ark_dashboards.as_of`(MAX(happened_at))
     FROM `ark-mobiles.ark_dashboards.app_screen_time` WHERE device_type = 'Real phone'),
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
    (SELECT `ark-mobiles.ark_dashboards.as_of`(MAX(updated_at))
     FROM `ark-mobiles.firestore_export.vw_stats_daily_parsed` WHERE device_type = 'Real phone'),
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
    `ark-mobiles.ark_dashboards.as_of`(MAX(last_checkin_at)),
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
    (SELECT `ark-mobiles.ark_dashboards.as_of`(MAX(happened_at))
     FROM `ark-mobiles.ark_dashboards.tool_interactions` WHERE complete),
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
    (SELECT `ark-mobiles.ark_dashboards.as_of`(MAX(happened_at)) FROM (
       SELECT happened_at FROM `ark-mobiles.ark_dashboards.mood_checkins`
       UNION ALL
       SELECT happened_at FROM `ark-mobiles.ark_dashboards.tool_interactions`)),
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
  (SELECT answer FROM bq9) AS bq9_answer;
