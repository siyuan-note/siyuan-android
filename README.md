[中文](https://github.com/siyuan-note/siyuan-android/blob/master/README_zh_CN.md)

## Overview

* Please go to [SiYuan issues](https://github.com/siyuan-note/siyuan/issues) to report issues/consult discussions
* Code contributions are welcome

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/org.b3log.siyuan/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=org.b3log.siyuan)

## Construction guide

1. Refer to [SiYuan Development Guide](https://github.com/siyuan-note/siyuan/blob/master/.github/CONTRIBUTING.md) to compile the kernel
2. Copy the resource files and package it in app/src/main/assets/app.zip
   * appearance
   * guide
   * stage
   * changelogs

Directory structure reference:

![project-tree](project-tree.png)

![app.zip](app-zip.png)

## About Multi-Channel Software Distribution

If you are using Android Studio's **Build** -> **Generate Signed Bundle APK...** method to build your program, please ignore the following content.

**The following content is only necessary when executing in the command line console**.

### Steps

1. Copy `signings.templates.gradle` and rename it as `signings.gradle`.
2. Configure information in `signings.gradle`.
3. Navigate to the project root directory using the console and execute the following commands

   ```shell
   # For Windows
   .\gradlew clean assembleXiaomiRelease assembleVoRelease bundleGoogleplayRelease bundleHuaweiRelease assembleOfficialRelease
   
   # For Linux
   gradle clean assembleXiaomiRelease assembleVoRelease bundleGoogleplayRelease bundleHuaweiRelease assembleOfficialRelease
   ```

   The naming convention here is:

   ```txt
   assemble/bundle Xiaomi Debug/Release
   ```
   
   `assemble` generates APK.
   `bundle` generates AAB.
   `Xiaomi` is the channel package name; refer to the location specified in `flavors.gradle productFlavors {}` configuration.
   `Debug/Release`: Testing version/Official version.

4. After execution, you can find the generated program at these locations:

   ```txt
   siyuan-android\app\build\outputs\apk\*     // APK location
   siyuan-android\app\build\outputs\bundle\*  // AAB location
   ```
