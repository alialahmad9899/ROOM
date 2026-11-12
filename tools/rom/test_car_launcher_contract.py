#!/usr/bin/env python3
"""Static acceptance checks for ALI CAR Android 5 v2.2 cockpit."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "ali-car-system"
SRC = APP / "app" / "src" / "main"
JAVA = SRC / "java" / "com" / "ali" / "carsystem"
WALLPAPER_SOURCE = APP / "app" / "src" / "main" / "assets" / "dashboard.webp"
GRADLE = (APP / "app" / "build.gradle").read_text(encoding="utf-8")
MANIFEST = ET.parse(SRC / "AndroidManifest.xml").getroot()
NS = {"a": "http://schemas.android.com/apk/res/android"}


def require(ok, message):
    if not ok:
        raise SystemExit("FAIL: " + message)


def read(name):
    path = JAVA / name
    require(path.is_file(), "source file missing: " + name)
    return path.read_text(encoding="utf-8")


require(re.search(r"applicationId\s+['\"]com\.ali\.carsystem['\"]", GRADLE), "applicationId mismatch")
require(re.search(r"minSdk\s+21\b", GRADLE), "minSdk must be API 21")
require(re.search(r"targetSdk\s+22\b", GRADLE), "targetSdk must be API 22")
require("versionCode 12" in GRADLE and "versionName '2.2.0'" in GRADLE, "app version must be 2.2.0")
require("androidx.viewpager:viewpager:1.0.0" in GRADLE, "ViewPager dependency missing")
require("org.osmdroid:osmdroid-android:6.1.20" in GRADLE, "OSMDroid dependency missing")
require("materializeDashboardWallpaper" in GRADLE, "Gradle wallpaper task missing")
require('src/main/res/drawable-nodpi/bg_car_dashboard.webp' in GRADLE, "wallpaper must be installed into drawable-nodpi")
require('inputs.file(dashboardSource)' in GRADLE and 'dashboardWallpaper.bytes = image' in GRADLE,
        "Gradle must install the clean supplied dashboard photo directly")
require(WALLPAPER_SOURCE.is_file(), "supplied dashboard photograph is missing")
try:
    from PIL import Image
    with Image.open(WALLPAPER_SOURCE) as wallpaper:
        wallpaper.verify()
    with Image.open(WALLPAPER_SOURCE) as wallpaper:
        wallpaper.load()
        require(wallpaper.width >= 300 and wallpaper.height >= 160,
                "wallpaper resolution is unexpectedly small")
except Exception as error:
    raise SystemExit("FAIL: supplied dashboard wallpaper cannot be fully decoded: " + str(error))

permissions = {x.get(f"{{{NS['a']}}}name") for x in MANIFEST.findall("uses-permission")}
for item in ("android.permission.INTERNET", "android.permission.ACCESS_FINE_LOCATION",
             "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_WIFI_STATE",
             "android.permission.CHANGE_WIFI_STATE", "android.permission.BLUETOOTH_ADMIN",
             "android.permission.READ_EXTERNAL_STORAGE", "android.permission.MODIFY_AUDIO_SETTINGS",
             "android.permission.WAKE_LOCK", "android.permission.WRITE_SETTINGS"):
    require(item in permissions, "permission missing: " + item)

activities = {x.get(f"{{{NS['a']}}}name") for x in MANIFEST.findall("./application/activity")}
for item in (".MainActivity", ".YouTubeSearchActivity", ".VideoPlayerActivity",
             ".NavigationActivity", ".MusicActivity", ".AdminToolsActivity"):
    require(item in activities, "manifest activity missing: " + item)

main = read("MainActivity.java")
music = read("MusicActivity.java")
video_api = read("VideoApiClient.java")
video_search = read("YouTubeSearchActivity.java")
video_player = read("VideoPlayerActivity.java")
navigation = read("NavigationActivity.java")
network = read("NetworkCompat.java")
tls = read("LegacyTls.java")

# Bundle wallpaper and transparent icons; do not reintroduce icon cards.
for token in ("bg_car_dashboard", "Bitmap.Config.RGB_565", "ImageView.ScaleType.CENTER_CROP"):
    require(token in main, "bundled wallpaper decode contract missing: " + token)
dock = [line for line in main.splitlines() if "dock.addView(dockItem(" in line]
require(len(dock) == 6, "main dashboard must have exactly six shortcut icons")
for name in ('"يوتيوب"', '"الملاحة"', '"الموسيقى"', '"بلوتوث"', '"واي فاي"', '"السطوع"'):
    require(any(name in line for line in dock), "dock item missing: " + name)
require("iconBubble" not in main and "R.drawable.ic_car_youtube" in main
        and "R.drawable.ic_car_navigation" in main and "R.drawable.ic_car_music" in main
        and "R.drawable.ic_car_bluetooth" in main and "R.drawable.ic_car_wifi" in main
        and "R.drawable.ic_car_brightness" in main,
        "dock must use six native vector icons instead of Unicode glyphs")
for icon in ("ic_car_youtube.xml", "ic_car_navigation.xml", "ic_car_music.xml",
             "ic_car_bluetooth.xml", "ic_car_wifi.xml", "ic_car_brightness.xml"):
    require((SRC / "res" / "drawable" / icon).is_file(), "vector dock icon missing: " + icon)
require("dashboard.webp" in main and "wallpaper!=null" in main,
        "wallpaper decode must include a validated bitmap fallback")

# Animated four-second neon welcome and screen-on broadcast.
for token in ("showWelcomeSplash", "ACTION_SCREEN_ON", "SplashNeonView", "PathMeasure",
              "ALI CAR  •  SMART COCKPIT", 'tts.speak("Welcome Ali"',
              "handler.postDelayed(()->", "4000"):
    require(token in main, "animated welcome/screen wake contract missing: " + token)

# ViewPager page 2 instrument cluster and a real persisted trip/odometer model.
for token in ("ViewPager", "setLayoutDirection(View.LAYOUT_DIRECTION_RTL)", "buildClusterPage",
              "TRIP KM", "ODOMETER", "MAX SPEED", "trip_km", "odometer_km", "max_speed",
              "page.setKeepScreenOn(true)", "drawArc", "drawLine", "FLAG_KEEP_SCREEN_ON",
              "postDelayed(idleTimer,90000)"):
    require(token in main, "cockpit cluster/sleep/speed gauge contract missing: " + token)
require("pager.getCurrentItem()==0" in main, "ambient sleep must be restricted to main page")

# Music screen is native and animated; playback shortcuts resolve to the saved track.
for token in ("MediaStore.Audio.Media", "MediaPlayer", "player.prepareAsync()", "DiscView",
              "VisualizerView", "setAlbumArt", "setPlaying", "last_song_title",
              "FLAG_KEEP_SCREEN_ON", "MediaStore.Audio.Media.ALBUM_ID"):
    require(token in music, "music player contract missing: " + token)
require("private void releasePlayer()" in music and "progressTick" in music, "music progress loop missing")

# YouTube search across services, MP4 progressive formats and HLS fallback.
for token in ("ExecutorCompletionService", "Executors.newFixedThreadPool(4)", "deadline=System.nanoTime()",
              'root.optJSONArray("items")', "video/mp4",
              "formatStreams", ".m3u8", "hlsUrl", "hls", "setConnectTimeout(8000)",
              "setReadTimeout(10000)", "for(String server:piped)", "for(String server:invidious)"):
    require(token in video_api, "video streaming contract missing: " + token)
require("0xFF111111" in video_search, "YouTube search text must be dark")
require("applyCockpitTheme" in video_search, "YouTube glass cockpit theme missing")
require("FLAG_KEEP_SCREEN_ON" in video_search, "YouTube search must prevent auto-sleep")
require("FLAG_KEEP_SCREEN_ON" in video_player, "video playback must prevent auto-sleep")

# Full-screen navigation should immediately use latest GPS or network location and keep tracking both.
for token in ("getLastKnownLocation(provider)", "LocationManager.GPS_PROVIDER",
              "LocationManager.NETWORK_PROVIDER", "requestLocationUpdates(LocationManager.GPS_PROVIDER",
              "requestLocationUpdates(LocationManager.NETWORK_PROVIDER",
              "nominatim.openstreetmap.org", "router.project-osrm.org", "geometries=geojson",
              "routeLine.setWidth(dp(14))", "carMarker.setRotation(location.getBearing())",
              "zoomToBoundingBox", "OpenStreetMap contributors", "lastRouteAttemptMs", ">=10000L",
              "map.setMapOrientation(-location.getBearing())", "FLAG_KEEP_SCREEN_ON"):
    require(token in navigation, "navigation/GPS contract missing: " + token)
require("location.getAccuracy()>45f" not in navigation,
        "navigation must not discard all GPS fixes solely for a 45m accuracy threshold")
require("gpsRunning=registered" in main and "handler.post(()->{stopGps();startGps();})" in main,
        "main GPS provider state must retry after provider enable")

# API21 TLS uses trusted roots and hostname verification, not permissive trust-all.
require("NetworkCompat.open(context" in video_api, "video API bypasses NetworkCompat")
require("NetworkCompat.open(this" in navigation, "navigation bypasses NetworkCompat")
require("TrustManagerFactory" in tls and "TLSv1.2" in tls, "TLS 1.2 trusted transport missing")
require("getDefaultHostnameVerifier()" in network, "hostname verification must remain enabled")
require("trustAll" not in tls and "ALLOW_ALL" not in tls, "trust-all TLS is prohibited")

print("PASS: ALI CAR v2.2 Android 5: bundled WebP/RGB565, transparent 6-icon dock, neon splash, RTL ViewPager, circular gauges, persistent trip/odometer, modern music, MP4/HLS streaming and dual-provider OSRM navigation.")
