# TESTING (on your phone, plain words)

Nothing here has run on a real phone yet. Tell me what happens, screenshots help.

## Tally, the compiler (release "latest", file Tally.apk)
1. Install Tally.apk. Does it open? Is the look right, light and dark (Setup, Appearance)?
2. Setup, Token: paste your GitHub token, Save. Setup, Check connection: Repository found, Push access Yes, Build workflow Found.
3. Build tab: type a name. The package id fills itself in. Pick an image. Try Fill and Fit. Build app.
4. Leave Tally right away and use another app. Does a notification show the progress? About 5 minutes.
5. Builds tab: when it says Saved in Downloads, tap Install. Does your new app show your name and icon on the home screen?
6. Build again with the SAME package id and a changed name. Does it install over the first one without uninstalling?
7. Bad input: a package id like com.android.x or Com.Test, a name with quotes. Does the form refuse it before building?
8. Turn on airplane mode in the middle of a build, then off. Does it carry on by itself?
9. Swipe Tally away from recents during a build, then open it again. Does the build still show up and finish?

## Test container Box (release "box-latest", file Box.apk)
10. Install Box.apk. Add a simple app, then TikTok, then Geometry Dash. Do they run inside it? Which package does Digital Wellbeing show?
