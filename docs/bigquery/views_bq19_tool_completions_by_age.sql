-- BQ19 (issue #272).
-- Uso: correr en BigQuery despues de views.sql, views_bq4_and_age.sql y views_tool_sessions.sql
-- (usa users y completed_tool_sessions).

-- Unidad: una sesion completada (completed_tool_sessions: complete = true, sin datos sinteticos, la
-- foto del dia una vez por usuario y dia). device_type e is_test_account son columnas para que Data
-- Studio muestre por defecto solo celulares reales y usuarios reales, igual que BQ13.

-- BQ19: que herramientas completan mas los usuarios de cada grupo de curso de vida de MinSalud
-- (Res. 3280 de 2018). Edad valida como en el registro de la app (13 a 120). Sin ventana de tiempo:
-- la pregunta es por grupo etario, no por periodo.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq19_tool_completions_by_age` AS
SELECT
  CASE
    WHEN u.age BETWEEN 12 AND 17 THEN 'Adolescence (12-17)'
    WHEN u.age BETWEEN 18 AND 28 THEN 'Youth (18-28)'
    WHEN u.age BETWEEN 29 AND 59 THEN 'Adulthood (29-59)'
    ELSE 'Old Age (60+)'
  END AS age_group,
  CASE
    WHEN u.age BETWEEN 12 AND 17 THEN 1
    WHEN u.age BETWEEN 18 AND 28 THEN 2
    WHEN u.age BETWEEN 29 AND 59 THEN 3
    ELSE 4
  END AS age_group_order,
  s.tool,
  -- una fila por sesion: Data Studio suma las sesiones y cuenta los usuarios distintos sin repetirlos
  s.user_id,
  s.device_type,
  s.is_test_account,
  1 AS completed_sessions
FROM `ark-mobiles.ark_dashboards.completed_tool_sessions` AS s
JOIN `ark-mobiles.ark_dashboards.users` AS u ON u.user_id = s.user_id
WHERE NOT COALESCE(u.is_synthetic, FALSE)
  AND u.age BETWEEN 13 AND 120;
