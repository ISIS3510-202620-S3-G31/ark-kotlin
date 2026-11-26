-- Respuesta en vivo de BQ19, BQ20 y BQ21 (issue #274). Correr despues de
-- views_bq19_tool_completions_by_age.sql, views_bq20_tool_satisfaction_ranking.sql y
-- views_bq21_weekday_completions.sql.

-- Respuesta en vivo de BQ19, BQ20 y BQ21 (una fila, una columna por pregunta), como bq_answers:
-- en cada pagina se muestra con una tabla de una celda. Solo celulares reales y sin cuentas de prueba.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq19_to_bq21_answers` AS
WITH
age AS (
  SELECT age_group, age_group_order, tool, SUM(completed_sessions) AS n
  FROM `ark-mobiles.ark_dashboards.bq19_tool_completions_by_age`
  WHERE device_type = 'Real phone' AND NOT is_test_account
  GROUP BY 1, 2, 3
),
age_top AS (
  SELECT
    age_group,
    age_group_order,
    SUM(n) AS group_total,
    ARRAY_AGG(STRUCT(tool, n) ORDER BY n DESC, tool LIMIT 1)[OFFSET(0)] AS top
  FROM age
  GROUP BY 1, 2
),
bq19 AS (
  SELECT CONCAT(
    STRING_AGG(CONCAT(age_group, ': ', top.tool, ' (', CAST(top.n AS STRING), ' of ',
      CAST(group_total AS STRING), ' sessions, ', CAST(ROUND(100 * top.n / group_total) AS STRING), '%)'),
      '. ' ORDER BY age_group_order),
    '. Groups with no completed sessions do not appear yet.') AS answer
  FROM age_top
),
rating AS (
  SELECT tool, ANY_VALUE(tool_ratings) AS total, ANY_VALUE(avg_rating) AS avg_rating,
    ANY_VALUE(positive_share) AS positive
  FROM `ark-mobiles.ark_dashboards.bq20_tool_satisfaction_ranking`
  WHERE NOT is_test_account
  GROUP BY 1
),
bq20 AS (
  SELECT CONCAT(
    STRING_AGG(CONCAT(tool, ': ', FORMAT('%.1f', avg_rating), ' stars on average (',
      CAST(total AS STRING), ' rating', IF(total = 1, '', 's'), ', ', CAST(positive AS STRING), '% with 4 or 5 stars)'),
      '. ' ORDER BY avg_rating DESC, total DESC),
    '. Tools with fewer than 5 ratings are not conclusive yet.') AS answer
  FROM rating
),
by_day AS (
  SELECT weekday, weekday_number, SUM(completed_sessions) AS n
  FROM `ark-mobiles.ark_dashboards.bq21_weekday_completions`
  WHERE device_type = 'Real phone' AND NOT is_test_account
  GROUP BY 1, 2
),
bq21 AS (
  SELECT CONCAT(
    (SELECT STRING_AGG(CONCAT(weekday, ': ', CAST(n AS STRING), ' session', IF(n = 1, '', 's')), '. ' ORDER BY n DESC, weekday_number) FROM by_day),
    '. Completed tool sessions in the last 28 days (', (SELECT CAST(SUM(n) AS STRING) FROM by_day), ' in total).') AS answer
)
SELECT
  (SELECT answer FROM bq19) AS bq19_answer,
  (SELECT answer FROM bq20) AS bq20_answer,
  (SELECT answer FROM bq21) AS bq21_answer;
