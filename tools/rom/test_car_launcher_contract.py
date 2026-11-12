#!/usr/bin/env python3
"""Static acceptance checks for the clean native Android 5 car launcher."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "ali-car-system"
SRC = APP / "app" / "src" / "main"
JAVA = SRC / "java" / "com" / "ali" / "carsystem"
GRADLE = (APP / "app" / "build.gradle").read_text(encoding="utf-8")
MANIFEST = ET.parse(SRC / "AndroidManifest.xml").getroot()
NS = {"a": "http://schemas.android.com/apk/res/android"}


def require(ok: bool, message: str) -> None:
    if not ok:
        raise SystemExit("FAIL: " + message)


require(re.search(r"applicationId\s+['\"]com\.ali\.carsystem['\"]", GRADLE), "applicationId mismatch")
require(re.search(r"minSdk\s+21\b", GRADLE), "minSdk must be API 21")
require(re.search(r"targetSdk\s+22\b", GRADLE), "targetSdk must be API 22")
require("androidx.recyclerview:recyclerview:1.3.2" in GRADLE, "Native RecyclerView dependency missing")
require("org.osmdroid:osmdroid-android:6.1.20" in GRADLE, "OSMDroid dependency missing")

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
require(admin is not None, "DeviceAdminReceiver missing")
require(admin.find("meta-data") is not None, "DeviceAdminReceiver metadata missing")
boot = MANIFEST.find("./application/receiver[@android:name='.BootReceiver']", {"android": NS["a"]})
require(boot is not None, "BootReceiver missing")

def read(name: str) -> str:
    path = JAVA / name
    require(path.is_file(), "source file missing: " + name)
    return path.read_text(encoding="utf-8")

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

for token in ("ALI CAR", "تم تطوير الواجهة بواسطة: علي أحمد", "Bitmap.Config.RGB_565",
              "SYSTEM_UI_FLAG_IMMERSIVE_STICKY", "أهلاً وسهلاً بك سيد علي، بسم الله نبدأ",
              "setWifiEnabled", "BluetoothAdapter", "90000", "LocationManager.GPS_PROVIDER"):
    require(token in main, "dashboard contract missing: " + token)

require((SRC / "assets" / "dashboard.webp").is_file(), "compressed dashboard WebP asset missing")
require("RecyclerView" in video_search and "HttpURLConnection" in video_api, "Native search stack missing")
require('URLEncoder.encode(query, "UTF-8")' in video_api, "Arabic video query must be URL-encoded using UTF-8")
require("NetworkCompat.open(context, address)" in video_api, "Video API bypasses the TLS compatibility layer")
require("downloadThumbnail" in video_adapter and "ImageView" in video_adapter, "Video thumbnails are not rendered")
require('URLEncoder.encode(searchText, "UTF-8")' in navigation, "Map search must be URL-encoded using UTF-8")
require("NetworkCompat.open(this, address)" in navigation, "Navigation bypasses the TLS compatibility layer")
require('AliCarLauncher/2.0 (Android 5.0)' in network, "Required Nominatim User-Agent missing")
require("setSSLSocketFactory(LegacyTls.socketFactory(context))" in network, "HTTPS does not use the API-21 TLS factory")
require("getDefaultHostnameVerifier()" in network, "HTTPS hostname verification must remain enabled")
require('"TLSv1.2"' in tls and "TrustManagerFactory" in tls, "TLS 1.2 trust-manager setup missing")
require("system.checkServerTrusted" in tls and "extra.checkServerTrusted" in tls,
        "TLS trust must combine system roots and the bundled ISRG root")
require("trustAll" not in tls and "ALLOW_ALL" not in tls, "Trust-all certificate acceptance is prohibited")
require((SRC / "res" / "raw" / "isrg_root_x1.pem").is_file(), "Bundled ISRG Root X1 trust anchor missing")
for token in ("pipedapi.adminforge.de", "instances.json", "video/mp4", "360p", "480p"):
    require(token in video_api, "Piped/Invidious fallback contract missing: " + token)
for token in ("SurfaceView", "MediaPlayer", "setDisplay", "prepareAsync", "FLAG_KEEP_SCREEN_ON"):
    require(token in video_player, "hardware video playback contract missing: " + token)
require("WebView" not in "".join(x.read_text(encoding="utf-8") for x in JAVA.glob("*.java")),
        "WebView is prohibited in the clean native app")

for token in ("MediaStore.Audio.Media", "prepareAsync", "SeekBar", "seekTo"):
    require(token in music, "native music player contract missing: " + token)
for token in ("MapView", "nominatim.openstreetmap.org", "router.project-osrm.org",
              "accept-language=ar,en", "map.onDetach", "map.setMapOrientation(-bearing)", "createCarArrow"):
    require(token in navigation, "navigation contract missing: " + token)
for token in ("isDeviceOwnerApp", "setApplicationHidden", "setLockTaskPackages", "startLockTask"):
    require(token in admin_src, "Device Owner guard missing: " + token)
require("if(!dpm.isDeviceOwnerApp(getPackageName()))" in admin_src,
        "hiding apps must be blocked unless the package is Device Owner")

print("PASS: API21 native launcher, secure TLS 1.2, UTF-8 Arabic requests, thumbnail results, car-oriented OSM, MediaStore and DPM guards.")
