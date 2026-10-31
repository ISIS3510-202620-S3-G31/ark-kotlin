"""Genera bogota_localities.sql: la tabla con el poligono oficial de cada localidad de Bogota.

Fuente: "Localidad. Bogota D.C." de la Secretaria Distrital de Planeacion, en Datos Abiertos
Bogota (licencia CC BY 4.0). Descargar el GeoJSON (loca.json, formato Esri con `rings`) de
https://datosabiertos.bogota.gov.co/dataset/localidad-bogota-d-c y correr:

    uv run --with shapely python build_localities_sql.py loca.json

El sistema de coordenadas es MAGNA-SIRGAS (EPSG:4686); frente al GPS (WGS84) la diferencia es
de centimetros, asi que se usa tal cual. Los poligonos se simplifican a ~5 m para que el SQL
quepa en el editor de BigQuery.
"""

import json
import sys
from pathlib import Path

from shapely.geometry import LinearRing, MultiPolygon, Polygon

# LocCodigo -> nombre con tildes (el archivo los trae en mayuscula)
NAMES = {
    '01': 'Usaquén', '02': 'Chapinero', '03': 'Santa Fe', '04': 'San Cristóbal',
    '05': 'Usme', '06': 'Tunjuelito', '07': 'Bosa', '08': 'Kennedy', '09': 'Fontibón',
    '10': 'Engativá', '11': 'Suba', '12': 'Barrios Unidos', '13': 'Teusaquillo',
    '14': 'Los Mártires', '15': 'Antonio Nariño', '16': 'Puente Aranda',
    '17': 'La Candelaria', '18': 'Rafael Uribe Uribe', '19': 'Ciudad Bolívar',
    '20': 'Sumapaz',
}
TOLERANCE_DEGREES = 0.00005  # ~5 m


def to_geometry(rings):
    """En Esri los anillos exteriores van en sentido horario y los huecos al reves."""
    shells, holes = [], []
    for ring in rings:
        (holes if LinearRing(ring).is_ccw else shells).append(ring)
    polygons = [Polygon(shell) for shell in shells]
    for hole in holes:
        owner = next(i for i, p in enumerate(polygons) if p.contains(Polygon(hole).representative_point()))
        p = polygons[owner]
        polygons[owner] = Polygon(p.exterior.coords, [*map(lambda h: h.coords, p.interiors), hole])
    geometry = MultiPolygon(polygons) if len(polygons) > 1 else polygons[0]
    return geometry.simplify(TOLERANCE_DEGREES, preserve_topology=True)


def main(source):
    features = json.loads(Path(source).read_text(encoding='utf-8'))['features']
    rows = []
    for feature in sorted(features, key=lambda f: f['attributes']['LocCodigo']):
        code = feature['attributes']['LocCodigo']
        wkt = to_geometry(feature['geometry']['rings']).wkt
        rows.append(f"  STRUCT('{code}' AS code, '{NAMES[code]}' AS name, '{wkt}' AS wkt)")
    if len(rows) != 20:
        raise SystemExit(f'Se esperaban 20 localidades y llegaron {len(rows)}')

    sql = (
        '-- Generado por build_localities_sql.py desde "Localidad. Bogotá D.C." (Secretaría\n'
        '-- Distrital de Planeación, Datos Abiertos Bogotá, CC BY 4.0). No editar a mano.\n'
        'CREATE OR REPLACE TABLE `ark-mobiles.ark_dashboards.bogota_localities` AS\n'
        'SELECT code, name, ST_GEOGFROMTEXT(wkt, make_valid => TRUE) AS geom\n'
        'FROM UNNEST([\n' + ',\n'.join(rows) + '\n]);\n'
    )
    out = Path(__file__).with_name('bogota_localities.sql')
    out.write_text(sql, encoding='utf-8')
    print(f'{out.name}: {len(rows)} localidades, {len(sql) / 1024:.0f} KB')


if __name__ == '__main__':
    main(sys.argv[1])
