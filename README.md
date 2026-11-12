# Tally

Two parts in one repo.

- `container/` is the engine (BlackBox, Apache 2.0, see `NOTICE`). It is a separate Gradle project with its own wrapper.
  - Default build = the test container "Box", package `com.killswitch.box` (workflow `container.yml`, release `box-latest`).
  - Custom build = the same project built with YOUR package id, app name and icon (workflow `custom.yml`, started by dispatch, output on release `out-<build id>` as `app.apk`).
- `tally/` (coming in the next slice) is the compiler app, package `com.killswitch.tally`. It collects name, icon and package, uploads the icon to branch `build-inputs`, starts `custom.yml` through the GitHub API, waits in the background, then saves the APK to Downloads.

Custom build inputs are validated in `tools/apply_custom.py` (package format and reserved prefixes, name characters, icon file) before anything is written or compiled. The built APK is checked with `aapt dump badging` and the run fails if package or label differ from what was asked.

Signing: committed public debug key (`container/app/debug.keystore`), so a rebuilt app with the same package installs over the old one. versionCode = 100 + the GitHub run number.
Logs: branch `build-logs` (default build) and `build-logs-custom` (custom builds).
Known: guest apps start with fresh data, the container asks for many permissions including INTERNET, speed cost untested.
