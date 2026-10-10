# Marvel Library for Android (phones and Android TV)

**Just want to install it?** Use the ready-made `MarvelLibrary.apk` (next to this folder). Skip to step 3. The rest of this file is only for people who want to rebuild it.

A small app that opens your Marvel Library website full screen. One app works on phones and on Android TV.

## 1. Put your addresses in (so you never type them on a TV remote)
Open `app/src/main/res/values/strings.xml` and fill in:
- `default_home_url`  - your PC's address on your home Wi-Fi, for example `http://192.168.1.20:3000`
- `default_away_url`  - your internet link, for example `https://something.trycloudflare.com`

The app tries the home address first and uses the internet one when you are away.
Whenever you open the app, the website tells it the PC's newest internet link, so a changed trycloudflare link is picked up automatically (the app must connect at least once after each restart, for example at home on Wi-Fi).
You can also change them later inside the app (footer link "Change server address") or leave both empty and type them on first launch.

## 2. Build the APK (pick one)
**A. No installs - build in the cloud (free GitHub account)**
1. Create a new repository on github.com and upload everything in this folder (drag the folder's contents into the page).
2. Open the repository's **Actions** tab, choose **Build APK**, press **Run workflow**.
3. When it finishes (about 3 minutes) open the run and download **MarvelLibrary-apk**. Unzip it to get `app-debug.apk`.

**B. Android Studio**
1. Install Android Studio, choose **Open** and select this folder, wait for the sync.
2. Menu **Build > Build APK(s)**. The file is `app/build/outputs/apk/debug/app-debug.apk`.

## 3. Install it
- **Phone:** copy the APK to the phone, open it, allow "install unknown apps" when asked.
- **Android TV:** the easiest way is the free **Downloader** app from the TV's store. Put the APK on a file-sharing link, type that link in Downloader, then install. A USB stick with a file-manager app or `adb install app-debug.apk` also work.
  The app appears in the TV home screen's app row as **Marvel Library**.

## Remote control
Arrows move around, OK opens, Back goes back (closes menus, then the player, then leaves the app).
Play/Pause, Fast-forward and Rewind keys control the video. In the player: Left/Right skip 10 s, Up/Down show the controls.
