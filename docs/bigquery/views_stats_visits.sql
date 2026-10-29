-- Pregunta de negocio de Stats: cuantas veces en promedio entran los usuarios a estadisticas por semana.
--
-- Origen: firestore_export.stats_daily_visits_raw_latest, la instancia de "Stream Firestore to
-- BigQuery" para users/{uid}/stats_daily_visits (ver README, seccion 2). La app escribe un documento
-- por usuario y dia (id = yyyyMMdd, el dia del celular) con:
--   userId, dateKey (yyyyMMdd), count (veces que entro a Stats ese dia), lastUpdatedAt (ms), deviceModel
-- Cada vez que el usuario entra a Stats, la app sube el documento del dia con count + 1 (en segundos
-- si hay red; si no, apenas vuelva). La extension lo pasa a BigQuery en segundos y Data Studio relee
-- cada minuto: el promedio se mueve en tiempo real.
--
-- Uso: correr despues de views.sql (usa users, tool_interactions, mood_checkins y to_ts).

-- Una fila por usuario y dia con al menos una visita.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.stats_daily_visits` AS
SELECT
  JSON_VALUE(v.path_params, '$.uid') AS user_id,
  SAFE.PARSE_DATE('%Y%m%d', JSON_VALUE(v.data, '$.dateKey')) AS visit_date,
  SAFE_CAST(JSON_VALUE(v.data, '$.count') AS INT64) AS visits,
  `ark-mobiles.ark_dashboards.to_ts`(JSON_QUERY(v.data, '$.lastUpdatedAt')) AS last_visit_at,
  -- el emulador de Android Studio corre en el Mac y no se parece a un telefono: se separa
  -- (los documentos de antes de deviceModel quedan como 'Unknown')
  CASE
    WHEN JSON_VALUE(v.data, '$.deviceModel') IS NULL THEN 'Unknown'
    WHEN LOWER(JSON_VALUE(v.data, '$.deviceModel')) LIKE 'sdk_gphone%'
      OR LOWER(JSON_VALUE(v.data, '$.deviceModel')) LIKE '%emulator%'
      OR LOWER(JSON_VALUE(v.data, '$.deviceModel')) LIKE 'generic%' THEN 'Emulator'
    ELSE 'Real phone'
  END AS device_type,
  u.age_bucket,
  COALESCE(u.is_synthetic, FALSE) OR JSON_VALUE(v.data, '$.demo') = 'true' AS is_synthetic
FROM `ark-mobiles.firestore_export.stats_daily_visits_raw_latest` AS v
LEFT JOIN `ark-mobiles.ark_dashboards.users` AS u
  ON u.user_id = JSON_VALUE(v.path_params, '$.uid')
WHERE SAFE_CAST(JSON_VALUE(v.data, '$.count') AS INT64) > 0;

-- Una fila por usuario y semana (lunes a domingo, como mood_emotions).
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.stats_weekly_visits` AS
SELECT
  user_id,
  DATE_TRUNC(visit_date, ISOWEEK) AS week_start,
  SUM(visits) AS visits,
  COUNT(*) AS days_with_visits,
  -- si en la semana entro desde el emulador y desde un celular, cuenta como celular real
  IF(LOGICAL_OR(device_type = 'Real phone'), 'Real phone', MAX(device_type)) AS device_type,
  ANY_VALUE(age_bucket) AS age_bucket,
  LOGICAL_OR(is_synthetic) AS is_synthetic
FROM `ark-mobiles.ark_dashboards.stats_daily_visits`
WHERE visit_date IS NOT NULL
GROUP BY user_id, week_start;

-- La respuesta: una fila por semana, sin datos sinteticos.
--   avg_visits_per_active_user: entre quienes usaron Ark esa semana (check-in, herramienta o Stats),
--     contando 0 a quien no abrio Stats. Es el promedio que responde la pregunta.
--   avg_visits_per_stats_user: solo entre quienes abrieron Stats al menos una vez esa semana.
--   pct_active_users_opening_stats: que parte de los usuarios activos abrio Stats.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.stats_weekly_visits_summary` AS
WITH active_users AS (
  SELECT user_id, DATE_TRUNC(DATE(happened_at, 'America/Bogota'), ISOWEEK) AS week_start
  FROM `ark-mobiles.ark_dashboards.tool_interactions`
  WHERE happened_at IS NOT NULL AND NOT is_synthetic
  UNION DISTINCT
  SELECT user_id, DATE_TRUNC(DATE(happened_at, 'America/Bogota'), ISOWEEK) AS week_start
  FROM `ark-mobiles.ark_dashboards.mood_checkins`
  WHERE happened_at IS NOT NULL AND NOT is_synthetic
  UNION DISTINCT
  SELECT user_id, week_start
  FROM `ark-mobiles.ark_dashboards.stats_weekly_visits`
  WHERE NOT is_synthetic
)
SELECT
  a.week_start,
  COUNT(DISTINCT a.user_id) AS active_users,
  COUNT(DISTINCT w.user_id) AS users_opening_stats,
  COALESCE(SUM(w.visits), 0) AS total_visits,
  ROUND(SAFE_DIVIDE(COALESCE(SUM(w.visits), 0), COUNT(DISTINCT a.user_id)), 2) AS avg_visits_per_active_user,
  ROUND(SAFE_DIVIDE(SUM(w.visits), COUNT(DISTINCT w.user_id)), 2) AS avg_visits_per_stats_user,
  ROUND(100 * SAFE_DIVIDE(COUNT(DISTINCT w.user_id), COUNT(DISTINCT a.user_id)), 1) AS pct_active_users_opening_stats
FROM active_users AS a
LEFT JOIN `ark-mobiles.ark_dashboards.stats_weekly_visits` AS w
  ON w.user_id = a.user_id AND w.week_start = a.week_start AND NOT w.is_synthetic
GROUP BY a.week_start;
