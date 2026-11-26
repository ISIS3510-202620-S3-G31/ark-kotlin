-- BQ21 (issue #274).
-- Uso: correr en BigQuery despues de views.sql, views_bq4_and_age.sql y views_tool_sessions.sql
-- (usa completed_tool_sessions).

-- BQ21: que dias de la semana se completan mas sesiones de herramientas en el ultimo mes.
-- Periodo: los ultimos 28 dias en hora de Bogota (hoy cuenta), el mismo de BQ13. 28 dias traen
-- exactamente 4 lunes, 4 martes ... 4 domingos, asi que sumar por dia es justo: ningun dia tiene mas
-- fechas que otro. Una fila por dia de la semana con la suma de sesiones (0 si no hubo ninguna).
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq21_weekday_completions` AS
WITH
weekdays AS (
  SELECT n AS weekday_number,
    ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][OFFSET(n - 1)] AS weekday
  FROM UNNEST(GENERATE_ARRAY(1, 7)) AS n
),
filters AS (
  SELECT device_type, is_test_account
  FROM UNNEST(['Real phone', 'Emulator', 'Unknown']) AS device_type,
  UNNEST([FALSE, TRUE]) AS is_test_account
),
sessions AS (
  SELECT
    -- 1 = lunes ... 7 = domingo
    MOD(EXTRACT(DAYOFWEEK FROM day) + 5, 7) + 1 AS weekday_number,
    device_type,
    is_test_account,
    COUNT(*) AS completed_sessions
  FROM `ark-mobiles.ark_dashboards.completed_tool_sessions`
  WHERE in_last_4_weeks
  GROUP BY 1, 2, 3
),
-- usuarios distintos que completaron al menos una sesion en el periodo. Se repite en las 7 filas de
-- cada filtro: en Data Studio se usa con agregacion Max, nunca sumado
users AS (
  SELECT device_type, is_test_account, COUNT(DISTINCT user_id) AS users_with_sessions
  FROM `ark-mobiles.ark_dashboards.completed_tool_sessions`
  WHERE in_last_4_weeks
  GROUP BY 1, 2
)
SELECT
  w.weekday_number,
  w.weekday,
  f.device_type,
  f.is_test_account,
  COALESCE(s.completed_sessions, 0) AS completed_sessions,
  COALESCE(u.users_with_sessions, 0) AS users_with_sessions
FROM weekdays AS w
CROSS JOIN filters AS f
LEFT JOIN sessions AS s
  ON s.weekday_number = w.weekday_number AND s.device_type = f.device_type
  AND s.is_test_account = f.is_test_account
LEFT JOIN users AS u
  ON u.device_type = f.device_type AND u.is_test_account = f.is_test_account;
