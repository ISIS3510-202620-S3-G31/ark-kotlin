-- BQ20 (issue #273).
-- Uso: correr en BigQuery despues de views.sql, views_bq4_and_age.sql y views_tool_sessions.sql
-- (usa tool_feedback, test_accounts y tool_name).

-- BQ20: que herramientas califican mejor los usuarios al terminarlas (1 a 5 estrellas, la pantalla
-- de feedback que sale al finalizar). Una fila por herramienta y estrella con su conteo, para mostrar
-- la distribucion completa y no solo un promedio. positive_share = % de 4 y 5 estrellas de esa
-- herramienta y avg_rating = su promedio de estrellas, para ordenar el ranking.
CREATE OR REPLACE VIEW `ark-mobiles.ark_dashboards.bq20_tool_satisfaction_ranking` AS
WITH ratings AS (
  SELECT
    -- algunas calificaciones viejas guardaron el id corto de la herramienta (breathing, photo,
    -- scream, tear): se pasan al mismo nombre que usan las sesiones para no partir una herramienta en dos
    `ark-mobiles.ark_dashboards.tool_name`(CASE LOWER(f.tool_id)
      WHEN 'breathing' THEN 'custom_breathing'
      WHEN 'photo' THEN 'photo_of_the_day'
      WHEN 'scream' THEN 'scream_tank'
      WHEN 'tear' THEN 'tear_collection'
      ELSE f.tool_id
    END) AS tool,
    f.rating,
    ta.user_id IS NOT NULL AS is_test_account
  FROM `ark-mobiles.ark_dashboards.tool_feedback` AS f
  LEFT JOIN `ark-mobiles.ark_dashboards.test_accounts` AS ta ON ta.user_id = f.user_id
  WHERE f.rating BETWEEN 1 AND 5
    AND f.tool_id IS NOT NULL
    AND NOT COALESCE(f.is_synthetic, FALSE)
),
counted AS (
  SELECT tool, rating, is_test_account, COUNT(*) AS ratings
  FROM ratings
  GROUP BY 1, 2, 3
)
SELECT
  tool,
  rating,
  CONCAT(CAST(rating AS STRING), ' star', IF(rating = 1, '', 's')) AS stars,
  is_test_account,
  ratings,
  SUM(ratings) OVER (PARTITION BY tool, is_test_account) AS tool_ratings,
  ROUND(100 * SAFE_DIVIDE(
    SUM(IF(rating >= 4, ratings, 0)) OVER (PARTITION BY tool, is_test_account),
    SUM(ratings) OVER (PARTITION BY tool, is_test_account)), 1) AS positive_share,
  -- promedio de estrellas de la herramienta (1 a 5). Va junto a la distribucion completa, nunca solo
  ROUND(SAFE_DIVIDE(
    SUM(rating * ratings) OVER (PARTITION BY tool, is_test_account),
    SUM(ratings) OVER (PARTITION BY tool, is_test_account)), 2) AS avg_rating
FROM counted;
