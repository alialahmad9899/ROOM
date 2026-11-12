#!/usr/bin/env python3
"""Static acceptance checks for ALI CAR Android 5 native launcher."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "ali-car-system"
SRC = APP / "app" / "src" / "main"
JAVA = SRC / "java" / "com" / "ali" / "carsystem"
WALLPAPER_PARTS = ROOT / "tools" / "rom" / "assets" / "ali-car-wallpaper-v2"
GRADLE = (APP / "app" / "build.gradle").read_text(encoding="utf-8")
MANIFEST = ET.parse(SRC / "AndroidManifest.xml").getroot()
NS = {"a": "http://schemas.android.com/apk/res/android"}


def require(ok: bool, message: str) -> None:
    if not ok:
        raise SystemExit("FAIL: " + message)


def read(name: str) -> str:
    path = JAVA / name
    require(path.is_file(), "source file missing: " + name)
    return path.read_text(encoding="utf-8")


require(re.search(r"applicationId\\s+['\\"]com\\.ali\\.carsystem['\\"]", GRADLE), "applicationId mismatch")
require(re.search(r"minSdk\\s+21\\b", GRADLE), "minSdk must be API 21")
require(re.search(r"targetSdk\\s+22\\b", GRADLE), "targetSdk must be API 22")
require("versionCode 11" in GRADLE and "versionName '2.1.0'" in GRADLE, "app version must be 2.1.0")
require("androidx.recyclerview:recyclerview:1.3.2" in GRADLE, "Native RecyclerView dependency missing")
require("org.osmdroid:osmdroid-android:6.1.20" in GRADLE, "OSMDroid dependency missing")
require("materializeDashboardWallpaper" in GRADLE, "Gradle wallpaper materialization task missing")
require('new String(image, 8, 4, "US-ASCII") != "WEBP"' in GRADLE, "WebP asset validation missing")
for number in range(1, 9):
    require((WALLPAPER_PARTS / f"part-{number:02d}.b64").is_file(),
            f"supplied compressed wallpaper segment {number}/8 missing")

permissions = {x.get(f"{{{NS['a']}}}name") for x in MANIFEST.findall("uses-permission")}
for item in ("android.permission.INTERNET", "android.permission.ACCESS_FINE_LOCATION",
             "android.permission.ACCESS_WIFI_STATE", "android.permission.CHANGE_WIFI_STATE",
             "android.permission.BLUETOOTH_ADMIN", "android.permission.READ_EXTERNAL_STORAGE",
             "android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.WRITE_SETTINGS"):
    require(item in permissions, "permission missing: " + item)
require("android.permission.READ_PHONE_STATE" not in permissions, "unused phone permission must stay removed")

activities = {x.get(f"{{{NS['a']}}}name") for x in MANIFEST.findall("./application/activity")}
for item in (".MainActivity", ".YouTubeSearchActivity", ".VideoPlayerActivity",
             ".NavigationActivity", ".MusicActivity", ".AdminToolsActivity"):
    require(item in activities, "manifest activity missing: " + item)

main_activity = next(x for x in MANIFEST.findall("./application/activity")
                     if x.get(f"{{{NS['a']}}}name") == ".MainActivity")
actions = {x.get(f"{{{NS['a']}}}name") for x in main_activity.findall("intent-filter/action")}
cats = {x.get(f"{{{NS['a']}}}name") for x in main_activity.findall("intent-filter/category")}
require("android.intent.action.MAIN" in actions and "android.intent.category.HOME" in cats,
        "MainActivity must remain the HOME/launcher activity")

admin = MANIFEST.find("./application/receiver[@android:name='.CarAdminReceiver']", {"android": NS["a"]})
require(admin is not None and admin.find("meta-data") is not None, "DeviceAdminReceiver metadata missing")
boot = MANIFEST.find("./application/receiver[@android:name='.BootReceiver']", {"android": NS["a"]})
require(boot is not None, "BootReceiver missing")

main = read("MainActivity.java")
video_api = read("VideoApiClient.java")
video_search = read("YouTubeSearchActivity.java")
video_player = read("VideoPlayerActivity.java")
music = read("MusicActivity.java")
navigation = read("NavigationActivity.java")
admin_src = read("AdminToolsActivity.java")
tls = read("LegacyTls.java")
network = read("NetworkCompat.java")
video_adapter = read("VideoAdapter.java")

# Exact clean dashboard structure and transparent sections.
for token in ("ALI CAR", "تم تطوير الواجهة بواسطة : ali ahmad", "Bitmap.Config.RGB_565",
              "SYSTEM_UI_FLAG_IMMERSIVE_STICKY", "أهلاً وسهلاً بك سيد علي، بسم الله نبدأ",
              "setWifiEnabled", "BluetoothAdapter", "LocationManager.GPS_PROVIDER",
              "brightnessPill", "postDelayed(hideBrightness,3000)", "new AnalogClock(this)",
              "new SpeedView(this)", "setBackgroundColor(Color.TRANSPARENT)"):
    require(token in main, "dashboard contract missing: " + token)
dock_lines = [line for line in main.splitlines() if "dock.addView(dockItem(" in line]
require(len(dock_lines) == 6, "dashboard dock must contain exactly six items")
for label in ('"يوتيوب"', '"الملاحة"', '"الموسيقى"', '"بلوتوث"', '"واي فاي"', '"السطوع"'):
    require(any(label in line for line in dock_lines), "required dock item missing: " + label)
for forbidden in ("راديو", "الهاتف", "التطبيقات", "قائمة التطبيقات", "القراءة"):
    require(forbidden not in "".join(dock_lines), "forbidden dock item found: " + forbidden)

# Search and playback: parallel requests, Arabic UTF-8, quality fallbacks and HLS.
require("ExecutorCompletionService" in video_api and "Executors.newFixedThreadPool(4)" in video_api,
        "video server search must run in parallel")
require('URLEncoder.encode(query,"UTF-8")' in video_api, "video search query must be UTF-8 URL encoded")
require("video/mp4" in video_api and "!item.optBoolean(\"videoOnly\",true)" in video_api,
        "progressive muxed MP4 filtering missing")
for quality in ("360", "480", "720", "240"):
    require(f'quality.contains("{quality}")' in video_api, f"video quality preference {quality} missing")
require(".m3u8" in video_api and "hlsUrl" in video_api, "HLS stream fallback missing")
require("for(String server:piped)" in video_api and "for(String server:invidious)" in video_api,
        "same-video alternate server fallback missing")
require("setConnectTimeout(4000)" in video_api and "setReadTimeout(5000)" in video_api,
        "video API timeout must be capped at 4s/5s")
require("downloadThumbnail" in video_adapter and "ImageView" in video_adapter,
        "video thumbnails are not rendered")
require("0xFF111111" in video_search and "0xFF666666" in video_search,
        "video search input contrast colors missing")
for token in ("SurfaceView", "MediaPlayer", "setDisplay", "prepareAsync", "FLAG_KEEP_SCREEN_ON",
              "player.setDataSource(this,Uri.parse(url),headers)"):
    require(token in video_player, "native video playback contract missing: " + token)
require("WebView" not in "".join(x.read_text(encoding="utf-8") for x in JAVA.glob("*.java")),
        "WebView is prohibited in the clean native app")

# Local player recycles its rows and releases playback.
for token in ("MediaStore.Audio.Media", "RecyclerView", "prepareAsync", "SeekBar", "seekTo"):
    require(token in music, "native music player contract missing: " + token)

# Nominatim + GeoJSON OSRM + immersive, full-screen car-navigation mode.
for token in ("MapView", "nominatim.openstreetmap.org", "router.project-osrm.org",
              'URLEncoder.encode(text,"UTF-8")', "geometries=geojson", "coordinates",
              "AliCarLauncher/2.0 (Android 5.0)", "routeLine.setWidth(dp(14))",
              "map.setMapOrientation(-location.getBearing())", "createCarBitmap",
              "moveCarMarker", "map.getController().setZoom(18.5)", "onWindowFocusChanged",
              "SYSTEM_UI_FLAG_IMMERSIVE_STICKY", "map.onDetach", "0xFF111111", "0xFF666666"):
    require(token in navigation, "car navigation contract missing: " + token)

# Shared secure API-21 transport; no permissive certificate bypass.
require("NetworkCompat.open(context, address)" in video_api, "video API bypasses TLS transport")
require("NetworkCompat.open(this, address)" in navigation, "navigation bypasses TLS transport")
require("setSSLSocketFactory(LegacyTls.socketFactory(context))" in network,
        "HTTPS does not use the API-21 TLS socket factory")
require("getDefaultHostnameVerifier()" in network, "HTTPS hostname verification must remain enabled")
require('"TLSv1.2"' in tls and "TrustManagerFactory" in tls, "TLS 1.2 trust-manager setup missing")
require("system.checkServerTrusted" in tls and "extra.checkServerTrusted" in tls,
        "TLS trust must combine system roots and the bundled ISRG root")
require("trustAll" not in tls and "ALLOW_ALL" not in tls, "trust-all certificate acceptance is prohibited")
require((SRC / "res" / "raw" / "isrg_root_x1.pem").is_file(), "ISRG Root X1 trust anchor missing")

for token in ("isDeviceOwnerApp", "setApplicationHidden", "setLockTaskPackages", "startLockTask"):
    require(token in admin_src, "Device Owner guard missing: " + token)
require("if(!dpm.isDeviceOwnerApp(getPackageName()))" in admin_src,
        "app hiding must be blocked unless the package is Device Owner")

print("PASS: API21 launcher v2.1.0, supplied WebP pipeline, Arabic dashboard, parallel MP4/HLS video, GeoJSON OSRM car navigation, secure TLS and DPM guards.")
