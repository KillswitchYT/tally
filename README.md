# Tally

Slice 1: a bare virtual container (BlackBox engine) under `com.killswitch.tally`. Goal of this slice is one question only:
do TikTok and Geometry Dash actually run inside it on a Galaxy A52 with Android 14? No compiler, no custom look yet.

- Build: GitHub Actions on every push to `main`. The APK is published as release `latest` (file `Tally.apk`).
- Signed with the committed public debug key (`app/debug.keystore`), so each build installs over the last one.
- versionCode = 100 + the GitHub run number.
- Logs of every build: branch `build-logs` (`errors.txt`, `build.log`, `sdk.log`, `run.txt`).
- Known: guest apps start with fresh data (no root), the manifest declares many permissions for guests (including INTERNET), speed cost is untested.
- Upstream readme: `UPSTREAM_README.md`. Licence: Apache 2.0, see `LICENSE` and `NOTICE`.
