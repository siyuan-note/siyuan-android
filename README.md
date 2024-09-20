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

If you are building your program using the Android Studio method of going to 【Build】,【Generate Signed Bundle APK...】, you only need to modify the `siyuanVersionName` and `siyuanVersionCode` within the build.gradle file at the project level. After making the changes, you can directly package the app and ignore the following content.

### Steps

**The following content is only necessary when building via the command line console**:

When building using the command line console, you not only need to modify the `siyuanVersionName` and `siyuanVersionCode` within the build.gradle file at the project level, but you also need to perform the following steps:

1. Copy the `signings.templates.gradle` file and rename it to `signings.gradle`.
2. Configure the related information in `signings.gradle`.
3. Use the command line to navigate to the root directory of the project and execute the following
   ```shell
   # windows
   .\gradlew clean buildReleaseTask
   # linux
   gradle clean buildReleaseTask
   ```
   
   The naming convention is as follows:

   ```txt
   assemble/bundle  Googleplay  Debug/Release
   ```
   
   `assemble` generates APKs
   `bundle` generates AABs
   `Googleplay` is the name of the channel package; refer to the `productFlavors {}` configuration in flavors.gradle for the specified location
   `Debug/Release` stands for Test version/Official version
4. After the execution is complete, you can find the generated program at the following location
   ```txt
   siyuan-android\app\build-release\siyuan-${versionName}-all
   ```
