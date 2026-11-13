#!/usr/bin/env bash
set -euo pipefail

ROOT="${GITHUB_WORKSPACE:-$(pwd)}"
WORK="$ROOT/offline-map-build"
OUT="$ROOT/offline-map-output"
PBF="$WORK/syria-latest.osm.pbf"
MAP="$OUT/syria.map"
PACKAGE="$OUT/package"
mkdir -p "$WORK" "$OUT" "$PACKAGE"

echo "Downloading current Syria OpenStreetMap extract..."
curl --fail --location --retry 4 --retry-delay 3 \
  "https://download.geofabrik.de/asia/syria-latest.osm.pbf" -o "$PBF"
test -s "$PBF"
sha256sum "$PBF" > "$OUT/syria-pbf.sha256"

echo "Building offline Mapsforge vector map..."
mkdir -p "$HOME/.openstreetmap/osmosis/plugins"
cp "$WORK/mapsforge-map-writer.jar" "$HOME/.openstreetmap/osmosis/plugins/mapsforge-map-writer.jar"
export JAVACMD_OPTIONS="${JAVACMD_OPTIONS:--Xmx4G}"
osmosis --rb file="$PBF" --mapfile-writer file="$MAP" type=hd bbox=32.0,35.3,37.5,42.8
test -s "$MAP"

echo "Building local Arabic/English POI and settlement database..."
python3 "$ROOT/tools/maps/build_syria_places.py" --pbf "$PBF" --output "$PACKAGE/syria-places.sqlite"
test -s "$PACKAGE/syria-places.sqlite"

echo "Downloading BRouter road-routing segments for Syria..."
mkdir -p "$PACKAGE/brouter/segments4" "$PACKAGE/brouter/profiles2" "$PACKAGE/brouter/modes"
for segment in E35_N30 E35_N35 E40_N30 E40_N35; do
  curl --fail --location --retry 4 --retry-delay 3 \
    "https://brouter.de/brouter/segments4/${segment}.rd5" \
    -o "$PACKAGE/brouter/segments4/${segment}.rd5"
  test "$(stat -c%s "$PACKAGE/brouter/segments4/${segment}.rd5")" -gt 4096
done

echo "Preparing the offline car routing profile..."
git clone --depth 1 --branch v1.7.10 https://github.com/abrensch/brouter.git "$WORK/brouter"
cp "$WORK/brouter/misc/profiles2/lookups.dat" "$PACKAGE/brouter/profiles2/lookups.dat"
python3 - "$WORK/brouter/misc/profiles2/car-vario.brf" "$PACKAGE/brouter/profiles2/car-fast.brf" <<'PY'
import pathlib, re, sys
source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
updated, count = re.subn(r"(?m)^assign\\s+vmax\\s*=.*$", "assign vmax = 160  # ALI CAR offline road routing", source)
if count != 1:
    raise SystemExit(f"Expected one vmax setting, found {count}")
pathlib.Path(sys.argv[2]).write_text(updated, encoding="utf-8")
PY

cp "$MAP" "$PACKAGE/syria.map"
cat > "$PACKAGE/ATTRIBUTION.txt" <<'EOF'
Map and place data
- © OpenStreetMap contributors
- Source: Geofabrik Syria extract
- Data license: Open Database License (ODbL) 1.0
- The map file and SQLite place database are derived from OpenStreetMap data.

Offline road routing
- BRouter routing engine: https://github.com/abrensch/brouter
- Routing segments derived from OpenStreetMap data.
- The BRouter engine is distributed separately in the ALI CAR application.
- BRouter source license and copyright notices are available in its upstream source repository.

Map rendering
- Mapsforge map format and writer: https://github.com/mapsforge/mapsforge
- Mapsforge license and copyright notices are available in its upstream source repository.
EOF
cat > "$PACKAGE/BUILD-INFO.txt" <<EOF
ALI CAR Syria Offline Maps Package
Created: $(date -u +%Y-%m-%dT%H:%M:%SZ)
OSM source: https://download.geofabrik.de/asia/syria-latest.osm.pbf
Coverage: Syria country extract
Offline map: Mapsforge vector map, Arabic labels requested
POI database: Syria named places, roads and tagged amenities/shops/services
Offline routing: BRouter car-fast profile and four required 5x5-degree road segments
Segments: E35_N30, E35_N35, E40_N30, E40_N35
Coordinate coverage includes Damascus, Homs, Tartus, Safita and surrounding villages where mapped in OpenStreetMap.
Important: local shops, services and address completeness depend on OpenStreetMap contributor coverage; this package cannot guarantee every business exists in the data.
EOF

mkdir -p "$OUT"
(
  cd "$PACKAGE"
  find . -type f -print0 | sort -z | xargs -0 sha256sum > "$OUT/SHA256SUMS.txt"
)
cp "$OUT/SHA256SUMS.txt" "$PACKAGE/SHA256SUMS.txt"
cd "$PACKAGE"
zip -r -0 "$OUT/ALI-CAR-SYRIA-OFFLINE-MAPS.zip" *
test -s "$OUT/ALI-CAR-SYRIA-OFFLINE-MAPS.zip"
unzip -t "$OUT/ALI-CAR-SYRIA-OFFLINE-MAPS.zip"
sha256sum "$OUT/ALI-CAR-SYRIA-OFFLINE-MAPS.zip"
echo "Offline Syria package ready: $OUT/ALI-CAR-SYRIA-OFFLINE-MAPS.zip"
