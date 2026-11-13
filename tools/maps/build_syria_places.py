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


CATEGORY_AR = {
    ("place", "city"): "مدينة", ("place", "town"): "بلدة",
    ("place", "village"): "قرية", ("place", "hamlet"): "ضيعة",
    ("amenity", "restaurant"): "مطعم", ("amenity", "fast_food"): "وجبات سريعة",
    ("amenity", "cafe"): "مقهى", ("amenity", "pharmacy"): "صيدلية",
    ("amenity", "hospital"): "مشفى", ("amenity", "clinic"): "عيادة",
    ("amenity", "doctors"): "طبيب", ("amenity", "bank"): "مصرف",
    ("amenity", "atm"): "صراف آلي", ("amenity", "fuel"): "محطة وقود",
    ("amenity", "school"): "مدرسة", ("amenity", "university"): "جامعة",
    ("amenity", "kindergarten"): "روضة أطفال", ("amenity", "police"): "مركز شرطة",
    ("amenity", "post_office"): "مكتب بريد", ("amenity", "car_wash"): "غسيل سيارات",
    ("amenity", "parking"): "موقف سيارات", ("tourism", "hotel"): "فندق",
    ("tourism", "guest_house"): "بيت ضيافة", ("tourism", "museum"): "متحف",
    ("shop", "supermarket"): "سوبرماركت", ("shop", "convenience"): "بقالة",
    ("shop", "bakery"): "مخبز", ("shop", "butcher"): "ملحمة",
    ("shop", "clothes"): "متجر ألبسة", ("shop", "electronics"): "متجر إلكترونيات",
    ("shop", "mobile_phone"): "متجر هواتف", ("shop", "car_repair"): "صيانة سيارات",
    ("shop", "hairdresser"): "حلاق", ("shop", "beauty"): "تجميل",
    ("leisure", "park"): "حديقة", ("leisure", "sports_centre"): "مركز رياضي",
    ("healthcare", "pharmacy"): "صيدلية", ("healthcare", "hospital"): "مشفى",
}
SEARCHABLE_UNNAMED = {
    "restaurant", "fast_food", "cafe", "pharmacy", "hospital", "clinic", "doctors",
    "bank", "atm", "fuel", "school", "university", "kindergarten", "police",
    "post_office", "car_wash", "parking"
}


def arabic_category(tags) -> str:
    for key in CATEGORY_KEYS:
        value = tags.get(key)
        if not value or value in ("yes", "no", "true", "false"):
            continue
        translated = CATEGORY_AR.get((key, value))
        if translated:
            return translated
        if key == "shop":
            return "متجر"
        if key == "place":
            return {"city": "مدينة", "town": "بلدة", "village": "قرية", "hamlet": "ضيعة"}.get(value, "مكان")
        if key == "amenity":
            return "خدمة"
    return "مكان"


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
    if tags.get("shop"):
        return True
    if tags.get("amenity") in SEARCHABLE_UNNAMED:
        return True
    if tags.get("healthcare") in ("pharmacy", "hospital", "clinic", "doctor"):
        return True
    if tags.get("tourism") in ("hotel", "guest_house", "hostel", "museum", "attraction"):
        return True
    return False


class PlaceHandler(osmium.SimpleHandler):
    def __init__(self, db: sqlite3.Connection):
        super().__init__()
        self.db = db
        self.inserted = 0

    def _store(self, osm_id: int, kind: str, tags, lat: float, lon: float) -> None:
        if not is_interesting(tags):
            return
        category = first_category(tags)
        fallback_name = arabic_category(tags)
        name = (tags.get("name") or tags.get("name:ar") or fallback_name).strip()
        name_ar = (tags.get("name:ar") or (name if name else fallback_name)).strip()
        if not name:
            return
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
