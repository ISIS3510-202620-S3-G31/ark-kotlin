# BigQuery en tiempo real (Ark Kotlin)

Reemplaza el camino batch (Firestore -> script -> CSV -> Google Sheets -> Looker Studio) por uno
en tiempo real, sin scripts que correr a mano:

```
App Ark ──> Firestore ──(extension "Stream Firestore to BigQuery")──> firestore_export.*  ┐
   │                                                                                       ├─> ark_dashboards.* (views.sql) ──> Data Studio
   └──> Firebase Analytics ──(enlace BigQuery, streaming)──────────> analytics_<ID>.events_* ┘
```

- Firestore -> BigQuery: cada escritura llega en segundos.
- Analytics -> BigQuery: con streaming, el evento llega en minutos (tabla `events_intraday_*`).
- Data Studio relee BigQuery cada 1 minuto (configurado así en las fuentes de BQ1 y screen time).

Proyecto Firebase/GCP: `ark-mobiles`. Todo en la ubicación **US** (BigQuery no deja juntar
datasets de ubicaciones distintas en una misma vista).

## 0. Requisito: plan Blaze

La extensión y el streaming de Analytics solo funcionan con facturación activa.

1. Firebase console > `ark-mobiles` > engranaje > *Usage and billing* > *Details & settings* >
   *Modify plan* > **Blaze**. Lo hace alguien con rol Owner del proyecto y una tarjeta.
2. Google Cloud console > *Billing* > *Budgets & alerts* > crear presupuesto de US$5 con alertas
   al 50/90/100 %. (Avisa por correo; no corta el gasto.)

Con el volumen de la clase el costo esperado es de centavos: BigQuery da gratis 10 GB de
almacenamiento y 1 TB de consultas al mes, y Cloud Functions 2 millones de invocaciones.

## 1. Enlazar Firebase Analytics con BigQuery

Firebase console > engranaje > *Project settings* > *Integrations* > **BigQuery** > *Link*:

- Ubicación del dataset: **United States (US)**.
- Activar **Google Analytics**, con exportación **Daily** y **Streaming**.
- Crashlytics, Performance, etc.: opcional.

Crea el dataset `analytics_<PROPERTY_ID>`. Solo trae datos desde el momento del enlace (no hay
históricos).

## 2. Instalar la extensión (una vez por colección)

Firebase console > *Extensions* > **Stream Firestore to BigQuery** > *Install*. Una instalación
por colección, cambiando solo la colección y la tabla. Hoy hay 6:

| Instancia (ID) | Collection path | Table ID |
|---|---|---|
| `firestore-bigquery-export` | `users/{uid}/tool_interactions` | `tool_interactions` |
| `firestore-bq-mood-checkins` | `users/{uid}/mood_checkins` | `mood_checkins` |
| `firestore-bq-tool-feedback` | `users/{uid}/tool_feedback` | `tool_feedback` |
| `firestore-bigquery-export-ctci` | `users` | `users` |
| `firestore-bq-tool-load-latency` | `users/{uid}/tool_load_latency` | `tool_load_latency` |
| `firestore-bigquery-export-h3lz` | `users/{uid}/screen_time` | `screen_time` |
| `firestore-bq-stats-daily-visits` | `users/{uid}/stats_daily_visits` | `stats_daily_visits` |

Las dos últimas necesitan que `firestore.rules` permita `tool_load_latency` y `screen_time`
(ya publicadas).

**Reglas:** `firestore.rules` y `storage.rules` de este repo son iguales a lo publicado en la
consola y a las del repo `backend` (PR backend#2, que actualizó su `firestore.rules`). Si se
cambian, cambiarlas en los dos repos. Desde `backend`, para subir solo las funciones:
`firebase deploy --only functions`. Valores iguales en todas (en el formulario, llenar primero las ubicaciones):

- BigQuery Dataset location: **us**
- BigQuery Project ID: `ark-mobiles`
- Firestore Instance ID: `(default)`
- Firestore Instance Location: la de la base (Firestore > *Database* > ubicación; se creó
  pensando en `us-east1`, confirmarlo)
- Enable Wildcard Column field: **Yes** (crea `path_params` con el `uid`; el nombre `{uid}`
  importa porque `views.sql` lo lee así)
- Dataset ID: `firestore_export`
- View Type: **View**
- El resto con los valores por defecto.

Cada instancia crea `firestore_export.<tabla>_raw_changelog` (historial) y
`firestore_export.<tabla>_raw_latest` (estado actual de cada documento).

## 3. Traer lo que ya existe en Firestore

La extensión solo copia escrituras nuevas. Para lo anterior, en **Cloud Shell** (botón `>_`
arriba a la derecha en console.cloud.google.com; ya trae `node` y la sesión iniciada):

```bash
npx @firebaseextensions/fs-bq-import-collection
```

Responder con los mismos valores del paso 2 (proyecto, colección, dataset `firestore_export`,
tabla, ubicación `us`). Para las subcolecciones responder **sí** a importar con *Collection Group
query*. Una corrida por colección.

## 4. Crear las vistas limpias

1. Correr `bogota_localities.sql` (tabla con el polígono oficial de las 20 localidades; la
   necesita `mood_emotions_by_locality`). Se regenera con `build_localities_sql.py`, que
   explica de dónde bajar el GeoJSON de Planeación.
2. Pegar todo `views.sql` en el editor de consultas de BigQuery y correrlo (vistas de Firestore).
3. Correr `views_analytics.sql` (vistas de Analytics). El dataset de Ark es
   `analytics_556383466` y ya está puesto en el archivo; en otro proyecto hay que cambiarlo por
   el que aparece en BigQuery después de enlazar. Los bloques "PASO 2" del final van después de
   instalar las instancias de `tool_load_latency` y `screen_time`.
4. Correr `views_bq4_and_age.sql` (BQ4 y edad de los usuarios activos; usa las vistas de los
   dos pasos anteriores).

Quedan en el dataset `ark_dashboards`:

| Vista | Para |
|---|---|
| `tool_interactions` | uso de herramientas por tipo, duración, intensidad (BQ4 y similares) |
| `mood_checkins`, `mood_emotions` | check-ins y emociones por semana y edad |
| `bogota_localities` (tabla) | polígono oficial de las 20 localidades (Planeación, CC BY 4.0) |
| `mood_emotions_by_locality` | Emotion By Location, una fila por emoción de cada check-in con su localidad de Bogotá, latitud y longitud |
| `locality_emotion_summary` | Emotion By Location, una fila por localidad: check-ins, emoción más frecuente y de mayor intensidad |
| `tool_feedback` | calificaciones por herramienta |
| `users` | edad y rango de edad (sin nombre ni correo) |
| `tool_load_latency` | BQ1, una fila por apertura: latencia, herramienta, entrada, frío/caliente, rango |
| `tool_load_latency_summary` | BQ1, por herramienta y tipo de dispositivo (emulador o celular real): aperturas, p50, p90 |
| `tool_entry_completed` | BQ4, desde Analytics |
| `app_screen_time` | tiempo de pantalla, un pedazo por fila (cada minuto con la app abierta y el resto al salir) |
| `daily_screen_time` | tiempo de pantalla, una fila por usuario, día y tipo de dispositivo: minutos, sesiones, rango, edad |
| `daily_screen_time_summary` | tiempo de pantalla, por día y tipo de dispositivo: usuarios activos, p25/p50/p75/p90 |
| `test_accounts` (tabla) | cuentas de prueba creadas a mano en la app; se marcan con `is_test_account`, no se borran |
| `bq4_tool_formats` | BQ4, sesiones completadas por formato, tipo de dispositivo y cuenta de prueba |
| `bq_age_distribution` | edad de los usuarios activos en 30 días, por grupo de MinSalud y cuenta de prueba |

Los dos bloques "PASO 2" del final de `views_analytics.sql` ya se corrieron: `tool_load_latency`
y `app_screen_time` leen de Firestore (llega en segundos) y de Analytics solo lo anterior a la
primera fila de Firestore, para no contar dos veces. La columna `source` dice de dónde vino cada fila.

Todas traen `is_synthetic`: `TRUE` en lo que crearon los scripts seed. No se borra nada; en el
dashboard se agrega el filtro `is_synthetic = false` (o un control para elegir).

## 5. Data Studio (antes Looker Studio)

Reporte "Ark Analytics and Business Questions - Group 31". Para agregar una vista:
*Add data* > **BigQuery** > `ark-mobiles` > `ark_dashboards` > la vista. Luego, en *Resource* >
*Manage added data sources* > *Edit*:

- *Data freshness*: **Custom > Every 1 minute** (por defecto viene en 12 horas).
- Revisar los tipos: Data Studio toma `*_minutes` como "Minute" y `start_hour` como "Hour"
  (fechas). Cambiarlos a **Numeric > Number**, si no, no sirven como métrica.
- Si la vista cambia de columnas en BigQuery: *Refresh fields*.

Colores fijos para todo el reporte (*Resource* > *Manage dimension value colors*):
`Real phone` azul, `Emulator` naranja.

Siempre separar `device_type`: el emulador corre en el Mac y no representa a un usuario; la
respuesta se da con celulares reales.

### Página BQ1 (latencia de render de una herramienta)

Fuentes `tool_load_latency` y `tool_load_latency_summary`:

1. Barras `tool_id` × `MEDIAN(latency_ms)` con desglose `device_type`: la respuesta directa.
2. Histograma: `latency_bucket` (orden ascendente) × *Record Count*, desglose `device_type`,
   barras sin apilar. Muestra la forma de la distribución.
3. Tabla con el resumen: `device_type`, `tool_id`, aperturas, p50 y p90.
   p50 = lo que espera un usuario típico; p90 = el 10 % más lento; aperturas = cuánto confiar.
   Sin p95 ni "% sobre un umbral" hasta tener ~40 aperturas por herramienta y una meta acordada.
4. Texto con cómo leerlo y la respuesta con fecha (se actualiza a mano).

Pendiente cuando haya datos de la versión nueva: comparar `open_type` (primera apertura vs
siguientes) y `entry_point`.

### Página Emotion By Location

Antes leía el CSV de `bq-location/process_checkins_locality.js` (a mano, y de `check_ins`).
Ahora la fuente es `locality_emotion_summary`, que tiene las mismas columnas del CSV, así que
las gráficas se conservan al cambiar la fuente. Lee `mood_checkins`, donde escriben Kotlin y
Flutter.

1. Tabla: `locality`, `most_frequent_emotion`, `highest_intensity_emotion`,
   `highest_avg_intensity`, `total_checkins`, `most_frequent_count`. `highest_avg_intensity`
   va con agregación **Max** (con *Count* la tabla muestra 1 en todas las localidades).
2. Control de filtro `is_synthetic` (en `false` por defecto) y control `locality`.
3. Para gráficas por emoción o por fecha, usar `mood_emotions_by_locality`.

`total_checkins` cuenta check-ins; el CSV contaba emociones (eso queda en `emotion_entries`).
La localidad sale de cruzar el punto con el polígono oficial de cada una de las 20 localidades
(tabla `bogota_localities`). Un punto fuera de Bogotá (otro municipio, el emulador) sale como
`Otra localidad`, y un check-in sin permiso de ubicación como `Ubicación no especificada`.
La función `bogota_locality` que quedó en BigQuery es la versión vieja con rectángulos (cubría
10 localidades); ya no la usa ninguna vista.

### Página BQ Screen Time (tiempo de pantalla diario por usuario, tipo 4)

Fuentes `daily_screen_time`, `daily_screen_time_summary` y `app_screen_time`:

1. Barras `day` × mediana diaria (`p50_minutes`, agregación Max) con desglose `device_type`.
2. Histograma: `minutes_bucket` (orden ascendente) × *Record Count* (= usuario-días), desglose
   `device_type`.
3. Tabla del resumen: `device_type`, `day`, usuarios activos, p50 y p90 de minutos.
4. Texto con cómo leerlo y la respuesta con fecha.
5. `weekday` × `MEDIAN(minutes)` (orden por `weekday_number`) y `start_hour` ×
   `SUM(minutes)` (orden por `start_hour`).

Para un tercero (tipo 4) solo se muestran agregados, nunca filas por usuario.

### Página BQ4 Tool Interaction Formats

Fuente `bq4_tool_formats`:

1. Controles *Drop-down list* `device_type` (por defecto `Real phone`) e `is_test_account` (por
   defecto `false`). La respuesta es con esos dos; cambiándolos se ve el emulador o las pruebas.
2. Anillo `tool_format` × `total_completions` (no `completion_percentage`: con varias filas por
   formato, sumar porcentajes da números falsos; el anillo calcula el suyo).
3. Barras `tool_format` × `total_completions` y scorecard con el total.
4. Texto con la respuesta y la fecha.

`tool_interactions` no guarda el celular, así que la vista lo toma de la apertura de la misma
herramienta justo antes (BQ1). La foto del día cuenta una vez por usuario y día, aunque se
retome. En Kotlin ninguna herramienta registra el formato `text`, por eso no aparece.

### Página BQ Age Distribution

Fuente `bq_age_distribution`: control `is_test_account` (por defecto `false`), anillo y barras
`age_bucket` × `active_users_30d`, scorecard con el total y texto con la respuesta y la fecha.
Activo = al menos un check-in o una herramienta en los últimos 30 días.

Los campos nuevos (`entry_point`, `first_open`, `app_screen_time`, `user_id`, las colecciones
`tool_load_latency` y `screen_time`) solo llegan desde la versión de la app que los trae; antes
de eso `entry_point` sale como `unknown`.

## Ojo: cuándo sube la app los datos

- `mood_checkins` se escribe en Firestore apenas se hace el check-in.
- `tool_load_latency` se escribe al dibujarse la herramienta y `screen_time` cada minuto con la
  app abierta y al salir (sin esperar la respuesta; Firestore usa caché en memoria, así que lo
  escrito sin red se pierde si la app se cierra antes de reconectar).
- `tool_interactions`, `tool_feedback`, `photos` y `stats_daily_visits` se guardan primero en
  Room y, al guardarse, le piden a `SyncManager` que suba: con internet llegan a BigQuery en
  segundos; sin internet, apenas el teléfono vuelve a tener conexión. Al subirse,
  `tool_interactions` y `tool_feedback` se borran del teléfono (Firestore queda con la copia).

## BQ de Stats: visitas por semana a estadísticas

Pregunta: ¿cuántas veces en promedio entran los usuarios a Stats por semana?

La app cuenta una visita cada vez que el usuario abre la pestaña Stats (no al refrescar ni al
rotar dentro de la misma entrada) y guarda un documento por usuario y día en
`users/{uid}/stats_daily_visits/{yyyyMMdd}` con el total del día. El mismo dato se ve en vivo en
la app: la tarjeta *Your stats visits* de Stats sube con cada entrada (esta semana y promedio
semanal del usuario, desde Room, también sin internet).

Para el dashboard:

1. `firestore.rules` permite `stats_daily_visits`: publicarlo en la consola (y en el repo
   `backend`), si no, la subida falla con `PERMISSION_DENIED`.
2. Instalar la instancia `firestore-bq-stats-daily-visits` de la tabla de la sección 2.
3. Traer lo que ya exista (sección 3) con la colección `stats_daily_visits`.
4. Correr `views_stats_visits.sql`. Crea `stats_daily_visits`, `stats_weekly_visits` (usuario ×
   semana) y `stats_weekly_visits_summary` (una fila por semana).
5. Página en Data Studio con frescura de 1 minuto:
   - Scorecard `avg_visits_per_active_user` de la semana actual: la respuesta.
   - Serie `week_start` × `avg_visits_per_active_user` y `avg_visits_per_stats_user`.
   - Histograma de `stats_weekly_visits.visits` (cuántos usuarios entran 1, 2, 3… veces), con
     desglose `device_type` y filtro `is_synthetic = false`.

Hoy la página "Stats" lee otras vistas: `firestore_export.vw_stats_daily_parsed`,
`vw_stats_weekly_user` y `vw_stats_weekly_kpis` (al final de `views_stats_visits.sql`). Las
`ark_dashboards.stats_*` de arriba no están creadas en BigQuery. Ojo con `vw_stats_daily_parsed`:
la app no manda `is_synthetic`, así que el filtro tiene que ser `IS NOT TRUE`; con `IS FALSE` se
descartan todas las visitas reales. Las tres traen `device_type` (sale del `deviceModel` que manda la app; los documentos
viejos sin modelo quedan como `Unknown`) y la página tiene un control `device_type` en
`Real phone`: sin él, la tarjeta "Avg per user" suma el promedio del emulador y el de los
celulares.

Para verlo en tiempo real: con el dashboard abierto, entrar a Stats en un celular con internet;
la visita llega a Firestore en segundos y el dashboard la muestra en la siguiente relectura
(máximo 1 minuto).
