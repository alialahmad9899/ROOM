#!/usr/bin/env python3
"""Build a compact, indexed Arabic/English POI database from Syria OSM PBF.

The database contains only places that are actually tagged in the source data.
It is not an authoritative business directory and does not invent missing shops.
"""
from __future__ import annotations

import argparse
import sqlite3
from pathlib import Path
from typing import Iterable

import osmium


CATEGORY_KEYS = (
    "place", "shop", "amenity", "tourism", "healthcare", "leisure",
    "office", "craft", "historic", "natural", "railway", "public_transport",
    "highway", "building"
)


def first_category(tags) -> str:
    for key in CATEGORY_KEYS:
        value = tags.get(key)
        if value and value not in ("yes", "no", "true", "false"):
            return f"{key}:{value}"
    return "place"


def address_text(tags) -> str:
    parts = []
    for key in ("addr:street", "addr:housenumber", "addr:city", "addr:suburb", "addr:postcode"):
        value = tags.get(key)
        if value:
            parts.append(str(value))
    return "، ".join(parts)


def is_interesting(tags) -> bool:
    if tags.get("name") or tags.get("name:ar"):
        return any(tags.get(key) for key in CATEGORY_KEYS) or bool(tags.get("addr:street"))
    return False


class PlaceHandler(osmium.SimpleHandler):
    def __init__(self, db: sqlite3.Connection):
        super().__init__()
        self.db = db
        self.inserted = 0

    def _store(self, osm_id: int, kind: str, tags, lat: float, lon: float) -> None:
        if not is_interesting(tags):
            return
        name = (tags.get("name") or tags.get("name:ar") or "").strip()
        name_ar = (tags.get("name:ar") or name).strip()
        if not name:
            return
        category = first_category(tags)
        self.db.execute(
            """INSERT OR REPLACE INTO places
               (id, name, name_ar, category, lat, lon, address, phone, website, opening_hours)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                f"{kind}/{osm_id}", name, name_ar, category, float(lat), float(lon),
                address_text(tags), tags.get("phone") or tags.get("contact:phone") or "",
                tags.get("website") or tags.get("contact:website") or "",
                tags.get("opening_hours") or "",
            ),
        )
        self.inserted += 1
        if self.inserted % 5000 == 0:
            self.db.commit()

    def node(self, node) -> None:
        if node.location.valid():
            self._store(node.id, "n", node.tags, node.location.lat, node.location.lon)

    def way(self, way) -> None:
        if not is_interesting(way.tags):
            return
        points = []
        for node in way.nodes:
            if node.location.valid():
                points.append((node.location.lat, node.location.lon))
        if not points:
            return
        # A centre point is sufficient for name search and map pin navigation.
        lat = sum(p[0] for p in points) / len(points)
        lon = sum(p[1] for p in points) / len(points)
        self._store(way.id, "w", way.tags, lat, lon)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pbf", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not args.pbf.is_file():
        raise SystemExit(f"Missing PBF: {args.pbf}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.output.exists():
        args.output.unlink()

    db = sqlite3.connect(args.output)
    try:
        db.execute("PRAGMA journal_mode=DELETE")
        db.execute("PRAGMA synchronous=NORMAL")
        db.execute("PRAGMA temp_store=MEMORY")
        db.execute(
            """CREATE TABLE places (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                name_ar TEXT NOT NULL,
                category TEXT NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                address TEXT NOT NULL DEFAULT '',
                phone TEXT NOT NULL DEFAULT '',
                website TEXT NOT NULL DEFAULT '',
                opening_hours TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.execute("CREATE INDEX idx_places_name ON places(name COLLATE NOCASE)")
        db.execute("CREATE INDEX idx_places_name_ar ON places(name_ar COLLATE NOCASE)")
        db.execute("CREATE INDEX idx_places_category ON places(category)")
        db.execute("CREATE INDEX idx_places_lat_lon ON places(lat, lon)")
        db.commit()

        handler = PlaceHandler(db)
        handler.apply_file(str(args.pbf), locations=True, idx="flex_mem")
        db.commit()
        count = db.execute("SELECT COUNT(*) FROM places").fetchone()[0]
        if count == 0:
            raise SystemExit("The PBF produced no named places/POIs.")
        db.execute("ANALYZE")
        db.commit()
        print(f"POI rows: {count}")
        print(f"SQLite bytes: {args.output.stat().st_size}")
    finally:
        db.close()


if __name__ == "__main__":
    main()
