# ALI CAR 3.8.4 — First-boot stability

- Replaced the animated full-screen welcome renderer with a single static software-rendered frame for the Intel SoFIA 3GR / Android 5 device.
- Removed the cosmetic progress beam and continuous 30 FPS redraw loop; the welcome screen no longer runs shader animation or repeated GPU work.
- Deferred GPS foreground-service startup, Text-to-Speech initialization and the live dashboard clock until the welcome screen has closed.
- Removed the screen-wake receiver that replayed the welcome overlay every time the display turned on.
- Added an API 21 instrumentation test verifying the static software layer and deferred startup sequence.
- Version: 3.8.4 (versionCode 384), minimum Android 5.0 / API 21, x86 32-bit.
