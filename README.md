# Tally

Two parts in one repo, each its own Gradle project.

- `tally/` is **Tally, the compiler app** (`com.killswitch.tally`, Kotlin and Compose, Aura look). You give it an app name, a package id and an icon.
  It sends them to this repository's GitHub Actions, waits in the background (foreground service with a notification), then saves the finished APK in your Downloads folder.
  Release `latest` holds `Tally.apk`. Install a newer one over the old one, the key and package never change.
- `container/` is the engine every built app is made from (BlackBox, Apache 2.0, see `NOTICE`).
  - `container.yml`: default test build "Box" (`com.killswitch.box`), release `box-latest`.
  - `custom.yml`: started by Tally through the GitHub API with your package id, name and icon. Output: release `out-<build id>` as `app.apk`.

How a build goes: Tally uploads the icon to branch `build-inputs`, dispatches `custom.yml`, `tools/apply_custom.py` checks every input and rewrites the name and icon, Gradle builds with `-PcontainerId=<package>`, `aapt` confirms package and label match what was asked, the APK is published, Tally downloads it.

Tally needs a fine-grained GitHub token for this one repository (Actions and Contents, Read and write), pasted in Setup. It is stored encrypted on the phone and only ever sent to api.github.com.

Signing: the committed public debug key (`app/debug.keystore` in each project), so a rebuilt app with the same package installs over the old one. versionCode = 100 + the GitHub run number.
Logs: branches `build-logs` (Tally and cycle script), `build-logs-custom` (custom builds). Tests for the logic without Android: `tally/tools/run-tests.sh`.
Known: containers start guest apps with fresh data, the container asks for many permissions including INTERNET, speed cost untested.
