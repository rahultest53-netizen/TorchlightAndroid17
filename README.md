# Torchlight — Android 17 project

A small flashlight app with a dark, frosted-glass interface, soft blurred background glows, a central torch button, and a 0–100 brightness slider.

## Android configuration

- **Compile SDK:** Android 17 / API 37
- **Target SDK:** Android 17 / API 37
- **Minimum SDK:** Android 8.0 / API 26
- **Language:** Java
- **External libraries:** None

## Important brightness behaviour

The slider uses Android's torch-strength API on devices that expose variable flash strength (Android 13+ and compatible flash hardware). A slider value of `0` switches the torch off. Values `1–100` map to the flash's available hardware strength range.

Some phones only expose a basic on/off flash. On those devices the slider still runs from 0 to 100, but the phone cannot produce true intermediate hardware brightness levels; the app labels this limitation in the UI.

## Build the APK

This workspace does not include the Android SDK or Gradle installation, so the APK cannot be compiled in this environment. To build it locally:

1. Install Android Studio **Meerkat 2024.3.1 or newer**, with JDK 17+.
2. In SDK Manager, install **Android SDK Platform 37** and **Android SDK Build-Tools 37.x**.
3. Open this folder (`TorchlightAndroid17`) as a project in Android Studio and allow Gradle sync to finish.
4. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. Android Studio will create the debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

For a command-line build after installing Gradle and the SDK, run `gradle assembleDebug` from this folder.

## Build online for free with GitHub Actions

This project includes `.github/workflows/build-apk.yml` for a cloud build.

1. Create a **new GitHub repository** and upload the contents of this project (the `app`, `gradle` configuration files, and `.github` folder). Do not upload the parent folder as an extra nested folder.
2. Open the repository's **Actions** tab and enable Actions if prompted.
3. Select **Build Torchlight APK** → **Run workflow**.
4. When the run finishes successfully, open that run and download the `Torchlight-debug-apk` artifact. Extract it to get `app-debug.apk`.

GitHub Actions usage is subject to GitHub's current free-tier limits. This workflow requires the Android SDK 37 package to be available from Google's SDK repository; if that package is not available on the runner yet, the build will fail until it is published. This is a debug APK, not a Play Store release-signed APK.
