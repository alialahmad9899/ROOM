#!/usr/bin/env python3
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[2]
APP=ROOT/"ali-car-system"/"app"; SRC=APP/"src"/"main"
GRADLE=(APP/"build.gradle").read_text(encoding="utf-8")
MAN=(SRC/"AndroidManifest.xml").read_text(encoding="utf-8")
MAIN=(SRC/"java/com/ali/carsystem/MainActivity.java").read_text(encoding="utf-8")
DASH=(SRC/"java/com/ali/carsystem/DashboardWidgets.java").read_text(encoding="utf-8")
VIDEO=(SRC/"java/com/ali/carsystem/VideoApi.java").read_text(encoding="utf-8")
NAV=(SRC/"java/com/ali/carsystem/NavigationActivity.java").read_text(encoding="utf-8")
TRACKING=(SRC/"java/com/ali/carsystem/LocationTrackingService.java").read_text(encoding="utf-8")
TLS=(SRC/"java/com/ali/carsystem/LegacyTls.java").read_text(encoding="utf-8")
SETTINGS=(SRC/"java/com/ali/carsystem/SettingsActivity.java").read_text(encoding="utf-8")
def require(ok,msg):
 if not ok: raise SystemExit("FAIL: "+msg)
require("applicationId 'com.ali.carsystem'" in GRADLE,"application package")
require("minSdk 21" in GRADLE and "targetSdk 22" in GRADLE,"API 21/22 target")
require("versionCode 3111" in GRADLE and "versionName '3.11.1'" in GRADLE,"ALI CAR version 3.11.1")
require("versionNameSuffix" not in GRADLE,"debug APK version name matches release metadata")
require("com.google.android.exoplayer:exoplayer:2.19.1" in GRADLE,"API21 ExoPlayer dependency")
require('android:largeHeap="true"' in MAN and 'android:hardwareAccelerated="true"' in MAN,"heap/hardware config")
require('android:name=".CarApplication"' in MAN and "onTrimMemory" in (SRC/"java/com/ali/carsystem/CarApplication.java").read_text(),"memory manager")
require('android:name=".CarAdminReceiver"' in MAN and "extends DeviceAdminReceiver" in (SRC/"java/com/ali/carsystem/CarAdminReceiver.java").read_text(),"device admin receiver")
require("setApplicationHidden" in SETTINGS and "isDeviceOwnerApp" in SETTINGS,"Device Owner guard")
require("DeviceDiagnosticsActivity.class" in SETTINGS
        and "DeviceDiagnosticsActivity" in MAN
        and (SRC/"java/com/ali/carsystem/DeviceDiagnosticsActivity.java").is_file()
        and (APP/"src/androidTest/java/com/ali/carsystem/DeviceDiagnosticsActivitySmokeTest.java").is_file(),
        "device diagnostics screen is registered, linked from settings and instrumented")

require("setLayoutDirection(View.LAYOUT_DIRECTION_RTL)" in MAIN,"RTL dashboard")
require("new NeonIconView(this,types[i],colors[i])" in MAIN,"custom dashboard icons and typed dock functions")
require('getIdentifier("bg_dashboard","drawable"' in MAIN,"embedded drawable wallpaper")
require("Bitmap.Config.RGB_565" in MAIN,"RGB565 decode")
require("new AnalogClockView(this)" in MAIN and "new SpeedometerView(this)" in MAIN,"clock and speed gauge")
require("dashboard_speed_gauge" in MAIN and "dashboard_analog_clock" in MAIN
        and "dashboard_media_bar" in MAIN and "dp(194)" in MAIN and "dp(224)" in MAIN,
        "left media panel, right reference clock and secondary speedometer are installed")
require(DASH.count("InstrumentDialStyle.drawFace(c,p,cx,cy,r)") == 2,"shared speedometer and analog clock face")
require("import android.graphics.Path;" in NAV,"navigation dot heading Path import")
require('digitalClock=label("00:00"' not in MAIN,"digital dashboard clock removed")
require("dashboard_sleep_toggle" in MAIN and "ambient_sleep_overlay" in MAIN,"manual and automatic sleep screen")
require("ambient_wake_button" in MAIN and "REST  •  RESET  •  DRIVE SAFE" in MAIN,"sleep clock wake control and English motto")
require("screenBrightness=0.045f" in MAIN and "restoreWindowBrightness" in MAIN,"sleep dim brightness restore")
require("90000" in MAIN and "ACTION_SCREEN_ON" not in MAIN,
        "ambient clock remains without replaying welcome on every screen wake")
require("final class NeonSplashView" in DASH and "أهلاً بك، رحلتك أصبحت جاهزة" in DASH
        and "WELCOME, ALI" in DASH and "YOUR DRIVE  •  YOUR WORLD" in DASH,
        "professional personalized welcome screen")
require("setLayerType(View.LAYER_TYPE_SOFTWARE,null)" in DASH
        and "welcome_splash_overlay" in DASH and "drawArc" in DASH
        and "postInvalidateDelayed(33)" not in DASH,
        "first-boot welcome is static software-rendered without continuous GPU redraw")
require("handler.postDelayed(this::dismissWelcome,4600L)" in MAIN
        and "handler.postDelayed(this::initializeTts,350L)" in MAIN
        and "v.animate()" not in MAIN,
        "welcome holds long enough for the spoken greeting without animated layer transitions")
MAIN_CREATE = MAIN.split("@Override protected void onCreate(Bundle state) {",1)[1].split("private android.content.SharedPreferences prefs()",1)[0]
require("new TextToSpeech(this,this)" not in MAIN_CREATE
        and "startGps()" not in MAIN_CREATE and "handler.post(clockTick)" not in MAIN_CREATE
        and "ACTION_SCREEN_ON" not in MAIN,
        "GPS, TTS and clock initialization are deferred until after welcome")
require("initializeTts();" in MAIN and "if(!splashPending&&welcomeOverlay==null)" in MAIN,
        "deferred TTS and GPS startup resumes after welcome closes")
TRACK=(SRC/"java/com/ali/carsystem/LocationTrackingService.java").read_text(encoding="utf-8")
require("trip_meters" in TRACK and "odometer_meters" in TRACK and "LocationManager.GPS_PROVIDER" in TRACK,"persistent local GPS distance and odometer service")
require("startForeground" in TRACK and "ACTION_UPDATE" in TRACK,"foreground GPS tracking service broadcasts state")
require("LocationTrackingService.ACTION_RESET_TRIP" in MAIN and "setPositiveButton" in MAIN
         and 'putFloat("max_speed",0f)' in MAIN
         and "TripResetPolicy.afterReset" in TRACK
         and 'putFloat("max_speed", maxSpeedKmh)' in TRACK
         and "previousAccepted = null" in TRACK,
         "trip reset clears distance and max speed while preserving odometer and resetting GPS anchor")
require("sleepBlocked()" in MAIN and "Intent.ACTION_POWER_CONNECTED" in MAIN and "MusicPlaybackService.isAudioPlaying" in MAIN,"sleep blocked while charging or music playback")
require("static boolean shouldBlockSleep" in MAIN and "showAmbientClockForTest" in MAIN,"testable sleep power interlock and isolated visual test")
require("FLAG_KEEP_SCREEN_ON" in MAIN,"main dashboard sleep policy")
APP_DIAGNOSTICS=(SRC/"java/com/ali/carsystem/AppDiagnostics.java").read_text(encoding="utf-8")
require("setDefaultUncaughtExceptionHandler" in APP_DIAGNOSTICS
        and "previous.uncaughtException" in APP_DIAGNOSTICS
        and "app-errors.log" in APP_DIAGNOSTICS,
        "internal crash logging preserves Android's default crash handler")
require((APP/"src/androidTest/java/com/ali/carsystem/AppDiagnosticsSmokeTest.java").is_file(),
        "internal diagnostics smoke test exists")

POWER=(SRC/"java/com/ali/carsystem/PowerReceiver.java").read_text(encoding="utf-8")
require("ACTION_POWER_CONNECTED" not in POWER and "startActivity" not in POWER,
        "plugging in charger must not relaunch MainActivity or interrupt foreground app")
require('android:name=".PowerReceiver"' in MAN and 'ACTION_POWER_CONNECTED' not in MAN.split('android:name=".PowerReceiver"')[1],
        "PowerReceiver must restore navigation only at boot, never on charger connection")
NAV_GUIDANCE=(SRC/"java/com/ali/carsystem/NavigationGuidanceService.java").read_text(encoding="utf-8")
require("NETWORK_PROVIDER" in NAV_GUIDANCE and "GPS_PROVIDER" in NAV_GUIDANCE,
        "background guidance requests GPS and network location providers")
require("RouteProgressEngine" in NAV_GUIDANCE and "remainingToTurnMeters" in NAV_GUIDANCE,
        "background guidance uses bounded route-progress matching")
require("EXTRA_ROUTE_FILE" in NAV_GUIDANCE and "FileInputStream" in NAV_GUIDANCE
        and "startForeground" in NAV_GUIDANCE,
        "background route is restored from a file after foreground service start")
HUD=(SRC/"java/com/ali/carsystem/NavigationBannerView.java").read_text(encoding="utf-8")
require("NavigationBannerView" in MAIN and
        (SRC/"java/com/ali/carsystem/NavigationBannerView.java").is_file()
        and "dashboard_navigation_hud" in HUD and "navigation_hud_distance" in HUD
        and "all.addView(navigationHud,hudLp)" in MAIN
        and "all.addView(mediaBar,mediaLp)" in MAIN
        and "setCompactMode(true)" in MAIN and "setCompactMode(boolean compact)" in HUD,
        "compact collapsible navigation HUD is directly below the left media panel")
require((APP/"src/androidTest/java/com/ali/carsystem/PowerReceiverSmokeTest.java").is_file(),
        "charger focus regression test exists")
require((APP/"src/androidTest/java/com/ali/carsystem/BackgroundNavigationServiceSmokeTest.java").is_file(),
        "background navigation service smoke test exists")
require((APP/"src/test/java/com/ali/carsystem/RouteProgressEngineTest.java").is_file(),
        "route progress unit tests exist")
require("MusicPlaybackService.dispatch" in MAIN,"dashboard music buttons share playback service")
require("ExecutorCompletionService" in VIDEO,"parallel video search")
require("instances.json" in VIDEO and "TeamPiped/Piped/wiki/Instances" in VIDEO,"dynamic API discovery")
require("scheduleDiscovery(c)" in VIDEO and "ALI-CAR-Video-Discovery" in VIDEO and "ensureDefaultEndpoints()" in VIDEO,"non-blocking video instance discovery")
require('root.optJSONArray("items")' in VIDEO,"Piped response parser")
require("formatStreams" in VIDEO and "adaptiveFormats" in VIDEO and "dashUrl" in VIDEO and "hlsUrl" in VIDEO,"MP4/adaptive/DASH/HLS resolver")
require("audioStreams" in VIDEO and "videoOnly" in VIDEO,"Piped separate audio and video streams")
require("encodeQuery" in VIDEO,"UTF-8 query contract")
require("osmdroid-android:6.1.20" in GRADLE,"pinned OSMDroid")
require("play-services-location:21.0.1" in GRADLE,"Google Play fused location API compatible with API 21")
require("OfflineRoutingEngine.requestRoutes" in NAV and "RouteNetworkClient" not in NAV and "offline-brouter" in (SRC/"java/com/ali/carsystem/OfflineRoutingEngine.java").read_text(encoding="utf-8"),"offline BRouter is sole routing engine")
require("chooseDestination" in NAV and "setItems(labels" in NAV,"multiple place search results selectable")
require("MapsForgeTileSource.createFromFiles" in NAV and "MapsForgeTileProvider" in NAV,"offline Syria vector map renderer")
require("OfflinePlacesDb.search" in NAV and "GeocodingClient" not in NAV,"local POI search only")
require("setUseDataConnection(false)" in NAV and "TileSourceFactory.MAPNIK" not in NAV,"online map tile access disabled")
require("startOfflineMapImport" in NAV and "ACTION_OPEN_DOCUMENT" in NAV and "OfflineMapsManager.installArchive" in NAV,"offline map ZIP importer")
require((SRC/"java/com/ali/carsystem/OfflineMapsManager.java").is_file(),"offline map package manager")
require((SRC/"java/com/ali/carsystem/OfflinePlacesDb.java").is_file(),"offline POI database")
require((SRC/"java/com/ali/carsystem/OfflineRoutingEngine.java").is_file(),"local BRouter engine adapter")
require("osmdroid-mapsforge:6.1.20" in GRADLE and "brouter-1.7.10-ro.jar" in GRADLE,"offline map and BRouter app dependencies")
require("noCompress += ['map', 'rd5', 'sqlite']" in GRADLE,"avoid recompressing offline map and routing payloads")
require("offlinePreparationRunning" in NAV,"prevent searching before bundled offline data is ready")
require("voicehints" in (SRC/"java/com/ali/carsystem/OfflineRoutingEngine.java").read_text(encoding="utf-8"),"offline turn-by-turn guidance")
require('android:name=".LocationTrackingService"' in MAN and "FOREGROUND_SERVICE" in MAN,"foreground location service manifest")
require("createLocationDot" in NAV and "Small red pointer rotates" in NAV,"blue GPS dot and heading pointer")
require("navigation_retry_route" in NAV,"visible route retry control")
require("navigation_free_mode" in NAV and "navigation_guided_mode" in NAV,"free and guided navigation modes")
require("routeAttemptedForDestination" in NAV and "requestId!=routeRequestId" in NAV,"route failure latch and stale request protection")
require("RouteChoiceLogic.shortestIndex" in NAV and "RouteChoiceLogic.fewestTurnsIndex" in NAV,"selectable route ranking")
require("الازدحام المباشر غير متاح" in NAV,"no fabricated live traffic data")
require((APP/"src/main/java/com/ali/carsystem/RouteChoiceLogic.java").is_file(),"route ranking helper")
require((APP/"src/test/java/com/ali/carsystem/RouteChoiceLogicTest.java").is_file(),"route ranking unit tests")
require("OpenStreetMap contributors" in NAV,"map attribution")
music=(SRC/"java/com/ali/carsystem/MusicActivity.java").read_text(encoding="utf-8")
require("RecyclerView" in music and "Visualizer" in music and "1000L/duration" in music,"redesigned music, actual waveform and normalized progress")
require("VinylDiscView" in music and "setSpinning" in music and "new float[36]" in music,"rotating record and redesigned 36-bar live equalizer")
require(not (SRC/"assets/music").exists(),
        "no historical or unrelated audio archive is bundled in the APK")
audio_extensions={".mp3",".ogg",".wav",".m4a",".aac",".flac",".opus",".wma",".aiff",".ape",".mid",".midi"}
require(not any(p.is_file() and p.suffix.lower() in audio_extensions
                for p in (SRC/"assets").rglob("*")),
        "no bundled audio in any supported format can enter the APK")
require("for(MusicPlaybackService.Song song:allSongs)" in music
        and "if(!isSupportedArtist(song))continue" not in music
        and 'case "كل الأغاني": include=true;' in music,
        "all local tracks remain visible; artist names are optional playlist filters")
require("MusicFilePolicy.MAX_TRACK_BYTES" in music
        and "MusicFilePolicy.isAllowed" in (SRC/"java/com/ali/carsystem/MusicPlaybackService.java").read_text(encoding="utf-8"),
        "music imports and MediaStore scans allow full-length tracks up to 256 MiB")
require("256L * 1024L * 1024L" in (SRC/"java/com/ali/carsystem/MusicFilePolicy.java").read_text(encoding="utf-8"),"music import size limit is 256 MiB")
require("OpenMusicInstaller.install" in (SRC/"java/com/ali/carsystem/MusicPlaybackService.java").read_text(encoding="utf-8")
        and "removeLegacyAudioTree" in (SRC/"java/com/ali/carsystem/OpenMusicInstaller.java").read_text(encoding="utf-8")
        and "Open Arabic Archive" in (SRC/"java/com/ali/carsystem/OpenMusicInstaller.java").read_text(encoding="utf-8"),
        "upgrade recursively removes all old archive audio only from legacy archive paths")
require("WRITE_EXTERNAL_STORAGE" in MAN,
        "API21 shared music archive has legacy write-storage permission")
require("context.getFilesDir()" in (SRC/"java/com/ali/carsystem/OfflineMapsManager.java").read_text(encoding="utf-8"),
        "offline map installer falls back to app-private storage when external storage is unavailable")
require("File root = new File(privateFiles, \"offline\")" in (SRC/"java/com/ali/carsystem/OfflineMapsManager.java").read_text(encoding="utf-8")
        and "getExternalFilesDir" not in (SRC/"java/com/ali/carsystem/OfflineMapsManager.java").read_text(encoding="utf-8"),
        "offline map installation always uses writable app-private internal storage")
require("-sdcard /tmp/ali-car-sdcard.img" in (ROOT/".github/workflows/build-car-app-3.yml").read_text(encoding="utf-8"),
        "API21 emulator is provisioned with writable SD storage for music and map installation")

require((SRC/"java/com/ali/carsystem/OpenMusicInstaller.java").is_file(),
        "legacy music archive cleanup exists")
require("install_open_music.py" in (ROOT/".github/workflows/build-car-app-3.yml").read_text(encoding="utf-8")
        and "MUSIC_ASSETS_OK" in (ROOT/"tools/rom/install_open_music.py").read_text(encoding="utf-8"),
        "CI verifies that no unrequested historical tracks are bundled")

require("NavigationBannerView" in music and "music_playlist_filters" in music
        and "navigation_hud_distance" in HUD,
        "music screen has compact navigation HUD and playlist filters")
require("music_add_local_files" in music and "ACTION_OPEN_DOCUMENT" in music
        and "MediaScannerConnection.scanFile" in music,
        "licensed local audio import is supported")
require("final MusicPlaybackService connectedService=" in music
        and "service!=connectedService" in music and "isDestroyed()" in music
        and "service.setListener(null)" in music,
        "music service callbacks are guarded against stale delivery after activity unbind")
require("addSongToPlaylist" in music and "service.isFavorite" in music,
        "custom playlists and favorites are managed from music UI")

require("showMusicSleep" in music and "sleepClockTick" in music and "sleepBlocked()" in music,"music screen ambient clock respects charger and active playback")
require("categoryPattern" in (SRC/"java/com/ali/carsystem/OfflinePlacesDb.java").read_text(encoding="utf-8"),"Arabic offline POI category search")
service=(SRC/"java/com/ali/carsystem/MusicPlaybackService.java").read_text(encoding="utf-8")
require("START_STICKY" in service and "MediaPlayer" in service and "isAudioPlaying" in service and "ali_car_audio" in service and "libraryExecutor" in service and "startForeground" in service and "toggleFavorite" in service and "rememberRecent" in service,"foreground music service with background indexing, favorites and recent tracks")
player=(SRC/"java/com/ali/carsystem/VideoPlayerActivity.java").read_text(encoding="utf-8")
require("ExoPlayer" in player and "DashMediaSource" in player and "HlsMediaSource" in player and "MergingMediaSource" in player,"MP4/DASH/HLS video player")
require("GpsStatus.Listener" in NAV and "addGpsStatusListener" in NAV,"GPS satellite diagnostics")
require("requestLocationUpdates(1000,0,criteria,this,Looper.getMainLooper())" in NAV,"Android fused Criteria location path")
require("getElapsedRealtimeNanos" in NAV and "isNavigationFixUsable" in NAV,"monotonic GNSS fix age and navigation accuracy")
require("showGpsDiagnostics" in NAV and "Google Play services" in NAV,"in-app GPS diagnostic report")
require("FusedLocationProviderClient" in NAV and "LocationServices.getFusedLocationProviderClient" in NAV,"Google Fused Location client")
require("LocationRequest.PRIORITY_HIGH_ACCURACY" in NAV and "requestLocationUpdates(request,fusedCallback" in NAV,"high-accuracy Google location updates")
require("gmsAvailabilityCode" in NAV and "gmsLocationStatus" in NAV,"Google Play services runtime status reporting")
require("windowSoftInputMode=" in MAN and "adjustResize" in MAN,"navigation keyboard resize")
require("sendExtraCommand" in NAV and "force_xtra_injection" in NAV and "force_time_injection" in NAV,"optional A-GPS assistance refresh")
require("ACCESS_LOCATION_EXTRA_COMMANDS" in MAN,"GPS assistance permission")
for name in ("GeoMathTest.java","VideoApiTest.java","ArtistMatcherTest.java","MusicFilePolicyTest.java","TripResetPolicyTest.java"):
 require((APP/"src/test/java/com/ali/carsystem"/name).is_file(),"unit test "+name)
require((APP/"src/androidTest/java/com/ali/carsystem/LaunchSmokeTest.java").is_file(),"dashboard UI smoke test")
require((APP/"src/androidTest/java/com/ali/carsystem/WelcomeStartupSmokeTest.java").is_file(),
        "first-boot welcome stability and deferred-startup instrumentation test")
require((APP/"src/androidTest/java/com/ali/carsystem/DashboardSleepSmokeTest.java").is_file(),"dashboard sleep and gauge UI smoke test")
require((APP/"src/androidTest/java/com/ali/carsystem/MusicActivitySmokeTest.java").is_file(),"music screen UI smoke test")
require((APP/"src/androidTest/java/com/ali/carsystem/MusicPlaybackLifecycleTest.java").is_file(),"local audio playback lifecycle instrumentation test")
require((APP/"src/androidTest/java/com/ali/carsystem/PhoneActivitySmokeTest.java").is_file(),"phone and Bluetooth UI instrumentation test")
require((SRC/"java/com/ali/carsystem/PhoneActivity.java").is_file()
        and 'android:name=".PhoneActivity"' in MAN and "PhoneActivity.class" in MAIN
        and "dashboard_secondary_shortcuts" in MAIN,
        "secondary cockpit shortcuts retain phone, contacts and Bluetooth access")
require((APP/"src/androidTest/java/com/ali/carsystem/OpenMusicInstallerSmokeTest.java").is_file(),"legacy bundled music removal instrumentation test")
require((APP/"src/androidTest/java/com/ali/carsystem/NavigationActivitySmokeTest.java").is_file(),"navigation screen UI smoke test")
java="\n".join(p.read_text(encoding="utf-8") for p in (SRC/"java").rglob("*.java"))
require("new WebView" not in java,"WebView prohibited")
wallpaper=(ROOT/"tools"/"rom"/"materialize_dashboard_asset.py").read_text(encoding="utf-8")
require("1280x720" in wallpaper and "111.jfif" in wallpaper and "ImageOps.fit" in wallpaper,"user 111 wallpaper optimization")
require(not (SRC/"java/com/ali/carsystem/RouteNetworkClient.java").exists(),"obsolete online routing client removed")
require(not (SRC/"java/com/ali/carsystem/GeocodingClient.java").exists(),"obsolete online geocoder removed")
require(not (APP/"src/test/java/com/ali/carsystem/RouteNetworkClientTest.java").exists(),"obsolete online routing tests removed")
require("showImportAlbumPicker(selected)" in music and "addImportedSongsToAlbum" in music and "متنوعة" in music,"multi-select import chooses and persists an album")
require("music_variety_album" in music and "music_archive_album" in music and "الأرشيف" in music and 'TextView label=text("",16,Color.WHITE,true)' in music,"variety album and larger track font")
require("dashboard_media_bar" in MAIN and "all.addView(navigationHud,hudLp)" in MAIN
        and "dashboard_analog_clock" in MAIN and "dashboard_bottom_dock" in MAIN
        and "dashboard_trip_top" in MAIN and "dashboard_speed_gauge" in MAIN
        and "setCompactMode(true)" in MAIN,
        "top route and music strips, frameless main speedometer and clock, and bottom dock")
require("connectionStatus" in MAIN and "dashboard_night_overlay" in MAIN and "smart_night_mode" in MAIN,"live Wi-Fi/Bluetooth/GPS indicators and smart dashboard night mode")
require("TripHistoryStore.record" in TRACK and "trip_started_at" in TRACK and "showTripHistory" in MAIN,"completed trip history is stored and visible")
require("96L*1024*1024" in NAV and "64L*1024*1024" in NAV and "setTilesScaledToDpi(false)" in NAV and "sharedOfflineTileSource" in NAV,"bounded warm offline vector map cache and native-resolution rendering")
require("restoreMapCamera" in NAV and "saved_destinations" in NAV and "recent_destinations" in NAV and "parking_lat" in NAV and "map_night_mode" in NAV
        and "if(followCar&&guidedMode)centerOnCar(current)" in NAV
        and "if(!isRouteStartUsable(current))current=null" in NAV
        and "loadLastKnown();" in NAV,
        "persistent map camera, saved places and recentering on current live location")
require("music_search" in music and "music_duplicates" in music and "knownHashes.add(fingerprint)" in music and "حذف نهائياً من الجهاز" in music,"music search, exact duplicate prevention, review workflow and confirmed permanent delete")
service=(SRC/"java/com/ali/carsystem/MusicPlaybackService.java").read_text(encoding="utf-8")
require("dataPath" in service and "deleteSong(long songId)" in service and "removeSongReferences" in service and "isHiddenFromPlaylist" in service,"safe MediaStore deletion and complete playlist reference cleanup")
require("CarBackupManager.exportJson" in SETTINGS and "CarBackupManager.restoreJson" in SETTINGS and "إنشاء نسخة احتياطية" in SETTINGS and "استعادة نسخة احتياطية" in SETTINGS,"validated local backup and restore UI")
for name in ("TripHistoryStore.java","CarBackupManager.java"):
 require((SRC/"java/com/ali/carsystem"/name).is_file(),"new storage helper "+name)
for name in ("TripHistoryStoreSmokeTest.java","CarBackupManagerSmokeTest.java"):
 require((APP/"src/androidTest/java/com/ali/carsystem"/name).is_file(),"new instrumentation test "+name)
MAP_THEME_PATH=SRC/"assets"/"mapsforge"/"ali-car-dark.xml"
require(MAP_THEME_PATH.is_file(),"flat dark MapsForge cockpit theme is bundled")
MAP_THEME=MAP_THEME_PATH.read_text(encoding="utf-8")
require('map-background="#14212D"' in MAP_THEME and 'k="highway"' in MAP_THEME
        and "hillshading" not in MAP_THEME.lower(),
        "map style is flat dark street rendering without hill shading")
require("AssetsRenderTheme" in NAV and "ali-car-dark.xml" in NAV
        and "ALI-CAR-COCKPIT-FLAT" in NAV,
        "offline navigation activates custom flat cockpit theme")
require("navigationHud=new NavigationBannerView(this)" in MAIN,
        "dashboard keeps its HUD reference without local variable shadowing")
require("dashboard_secondary_page" in MAIN and "dashboard_secondary_shortcuts" in MAIN
        and "dashboard_bottom_dock" in MAIN,
        "main cockpit and secondary swipe page are present")
require("welcome_voice_rate" in MAIN and "setSpeechRate" in MAIN and "setPitch" in MAIN
        and "getVoices()" in MAIN and "WELCOME, ALI" in DASH,
        "slower welcome voice and best-available installed female voice")
require("Math.min(Visualizer.getMaxCaptureRate(),10000)" in music
        and "postInvalidateDelayed(66)" in music,
        "music visualizer and disc animation are throttled for low-memory devices")
require("openYouTubeLite" in MAIN and "https://m.youtube.com" in MAIN
        and "YouTube Lite" in MAIN and "openRadioApp" not in MAIN,
        "radio is removed and the dock opens the lightweight YouTube mobile site")
require("dashboard_four_way_swipe_surface" in MAIN
        and "DashboardGesturePolicy.LEFT" in MAIN and "DashboardGesturePolicy.RIGHT" in MAIN
        and "DashboardGesturePolicy.UP" in MAIN and "DashboardGesturePolicy.DOWN" in MAIN
        and (SRC/"java/com/ali/carsystem/DashboardGesturePolicy.java").is_file()
        and (APP/"src/test/java/com/ali/carsystem/DashboardGesturePolicyTest.java").is_file(),
        "left/right dashboard paging and up/down navigation/music gestures have four-direction unit tests")
require("LocationFixPolicy.isUsable" in TRACKING and "MAX_FIX_AGE_MS" in TRACKING
        and "SPEED_STALE_TIMEOUT_MS" in TRACKING
        and (APP/"src/main/java/com/ali/carsystem/LocationFixPolicy.java").is_file()
        and (APP/"src/test/java/com/ali/carsystem/LocationFixPolicyTest.java").is_file(),
        "tracking service rejects stale, out-of-order, inaccurate and invalid GPS fixes and times speed out")
require("AudioFocusPolicy.shouldResumeAfterTransientLoss" in (SRC/"java/com/ali/carsystem/MusicPlaybackService.java").read_text(encoding="utf-8")
        and (APP/"src/test/java/com/ali/carsystem/AudioFocusPolicyTest.java").is_file(),
        "music resumes after transient focus loss only when playback was previously active and still wanted")
require("BrouterManeuverPolicy" in (SRC/"java/com/ali/carsystem/OfflineRoutingEngine.java").read_text(encoding="utf-8")
        and (APP/"src/test/java/com/ali/carsystem/BrouterManeuverPolicyTest.java").is_file(),
        "BRouter maneuver IDs are mapped from authoritative 1.7.10 command codes")
require("manifest.sha256" in (SRC/"java/com/ali/carsystem/OfflineMapsManager.java").read_text(encoding="utf-8")
        and "verifyInstalledBundle" in (SRC/"java/com/ali/carsystem/OfflineMapsManager.java").read_text(encoding="utf-8")
        and "requireStorage" in (SRC/"java/com/ali/carsystem/OfflineMapsManager.java").read_text(encoding="utf-8"),
        "offline maps use storage preflight and SHA-256 manifest verification")
require("isApplicationHidden" in SETTINGS,"hidden-app dialog reflects Device Owner application state")
require("GpsStatus" in (SRC/"java/com/ali/carsystem/DeviceDiagnosticsActivity.java").read_text(encoding="utf-8")
        and "BluetoothProfile.A2DP" in (SRC/"java/com/ali/carsystem/DeviceDiagnosticsActivity.java").read_text(encoding="utf-8"),
        "diagnostics report satellite fix and actual Bluetooth A2DP connection state")
require("dashboard_settings_button" in MAIN and "dashboard_media_previous" in MAIN
        and "dashboard_media_next" in MAIN and "R.drawable.ic_cockpit_settings" in MAIN,
        "dashboard control symbols use real vector resources")
require("shouldAcceptIncomingFix" in NAV and "isNewerFix" in NAV
        and "isFreshLocation(candidate,180000L)" in NAV
        and "onProviderEnabled(String p)" in NAV
        and "requestLocationSources();}" not in NAV
        and 'getSharedPreferences("ali_car_v3"' not in NAV
        and "ali-car-saved-location" not in NAV
        and "return isFreshLocation(l,180000L)&&l.hasAccuracy()" in NAV,
        "stale/out-of-order GPS fixes are rejected and saved preferences are never shown as live location")
require((APP/"src/androidTest/java/com/ali/carsystem/NavigationLocationRegressionTest.java").is_file(),
        "instrumented live/stale GPS regression test exists")
require("offRouteReroutePolicy.shouldReroute" in NAV
        and "requestRoute(location,destination,destinationName)" in NAV
        and (SRC/"java/com/ali/carsystem/OffRouteReroutePolicy.java").is_file()
        and (APP/"src/test/java/com/ali/carsystem/OffRouteReroutePolicyTest.java").is_file(),
        "automatic offline rerouting is debounced, accuracy-gated and unit-tested")

print("PASS: ALI CAR 3.10.0 cockpit, YouTube Lite shortcut, four-way gestures, GPS fix policy, offline maps and music contracts")
