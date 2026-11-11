#!/usr/bin/env python3
"""Static contract checks for the Android 5 ALI Car System app."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "ali-car-system"
GRADLE = (APP / "app" / "build.gradle").read_text(encoding="utf-8")
MANIFEST_PATH = APP / "app" / "src" / "main" / "AndroidManifest.xml"
MANIFEST = ET.parse(MANIFEST_PATH).getroot()
NS = {"a": "http://schemas.android.com/apk/res/android"}


def fail(message: str) -> None:
    print("FAIL:", message)
    raise SystemExit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


require(re.search(r"applicationId\s+['\"]com\.ali\.carsystem['\"]", GRADLE) is not None,
        "applicationId must be com.ali.carsystem")
require(re.search(r"minSdk\s+21\b", GRADLE) is not None,
        "minSdk must remain 21")
require(re.search(r"targetSdk\s+22\b", GRADLE) is not None,
        "targetSdk must remain 22")
require("org.osmdroid:osmdroid-android:6.1.20" in GRADLE,
        "API-21-compatible pinned osmdroid dependency is missing")

permissions = {item.get(f"{{{NS['a']}}}name") for item in MANIFEST.findall("uses-permission")}
for permission in (
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.INTERNET",
    "android.permission.WAKE_LOCK",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_SETTINGS",
    "android.permission.CHANGE_WIFI_STATE",
    "android.permission.BLUETOOTH_ADMIN",
):
    require(permission in permissions, f"required permission missing: {permission}")

activities = {
    item.get(f"{{{NS['a']}}}name")
    for item in MANIFEST.findall("./application/activity")
}
require(".MainActivity" in activities, "MainActivity missing from manifest")
require(".NavigationActivity" in activities, "NavigationActivity missing from manifest")
require(".MusicActivity" in activities, "MusicActivity missing from manifest")
require(".YouTubeLiteActivity" in activities, "YouTubeLiteActivity missing from manifest")

main_activity = next(
    item for item in MANIFEST.findall("./application/activity")
    if item.get(f"{{{NS['a']}}}name") == ".MainActivity"
)
home_actions = {
    node.get(f"{{{NS['a']}}}name")
    for intent in main_activity.findall("intent-filter/action")
}
home_categories = {
    node.get(f"{{{NS['a']}}}name")
    for intent in main_activity.findall("intent-filter/category")
}
require("android.intent.action.MAIN" in home_actions, "HOME activity lacks MAIN action")
require("android.intent.category.HOME" in home_categories, "MainActivity is not a HOME launcher")
require("android.intent.category.DEFAULT" in home_categories, "HOME launcher lacks DEFAULT category")

receiver = MANIFEST.find("./application/receiver")
require(receiver is not None, "BootReceiver is missing")
receiver_actions = {
    node.get(f"{{{NS['a']}}}name")
    for node in receiver.findall("intent-filter/action")
}
require("android.intent.action.BOOT_COMPLETED" in receiver_actions,
        "boot receiver action missing")
require("android.intent.action.ACTION_POWER_CONNECTED" in receiver_actions,
        "charger-connected action missing")

source_root = APP / "app" / "src" / "main" / "java" / "com" / "ali" / "carsystem"
main = (source_root / "MainActivity.java").read_text(encoding="utf-8")
navigation = (source_root / "NavigationActivity.java").read_text(encoding="utf-8")
music = (source_root / "MusicActivity.java").read_text(encoding="utf-8")
boot = (source_root / "BootReceiver.java").read_text(encoding="utf-8")

for expected in (
    "ALI CAR",
    "تم تطوير الواجهة بواسطة : ali ahmad",
    "SCREEN_BRIGHTNESS",
    "setWifiEnabled",
    "BluetoothAdapter",
    "LocationManager.GPS_PROVIDER",
    "SimpleDateFormat",
):
    require(expected in main, f"dashboard feature missing: {expected}")

for expected in (
    "MapView",
    "TileSourceFactory.MAPNIK",
    "nominatim.openstreetmap.org",
    "router.project-osrm.org",
    "decodePolyline",
    "maneuverText",
    "OpenStreetMap contributors",
):
    require(expected in navigation, f"navigation feature missing: {expected}")

require("MediaStore.Audio.Albums.ALBUM_ART" in music,
        "album-art lookup missing from local music player")
require("prepareAsync()" in music, "music player must prepare audio asynchronously")
require("ACTION_POWER_CONNECTED" in boot, "charger receiver implementation missing")
require("ACQUIRE_CAUSES_WAKEUP" in boot, "charger wake-up attempt missing")

print("PASS: API 21 / HOME launcher / charger event / dashboard / map-route / local music contracts.")
