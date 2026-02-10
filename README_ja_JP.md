[中文](https://github.com/siyuan-note/siyuan-android/blob/master/README_zh_CN.md) | [English](https://github.com/siyuan-note/siyuan-android/blob/master/README.md)

## 概要

* 問題報告や質問は [SiYuan issues](https://github.com/siyuan-note/siyuan/issues) にお願いします
* コードの貢献を歓迎します

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="F-Droidで入手" height="80">](https://f-droid.org/packages/org.b3log.siyuan/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Google Playで入手" height="80">](https://play.google.com/store/apps/details?id=org.b3log.siyuan)

## ビルドガイド

1. [SiYuan Development Guide](https://github.com/siyuan-note/siyuan/blob/master/.github/CONTRIBUTING.md) を参照してカーネルをコンパイルします
2. リソースファイルをコピーして `app/src/main/assets/app.zip` にパッケージ化します
   * appearance
   * guide
   * stage
   * changelogs

ディレクトリ構造参考：

![project-tree](project-tree.png)

![app.zip](app-zip.png)

## マルチチャネルソフトウェア配布について

Android Studio を使用して 【Build】→【Generate Signed Bundle APK...】 の方法でプログラムをビルドする場合は、プロジェクトレベルの `build.gradle` ファイル内の `siyuanVersionName` と `siyuanVersionCode` のみを変更する必要があります。変更後、アプリを直接パッケージ化でき、以下の内容は無視してください。

### 手順

**以下の内容はコマンドラインコンソール経由でビルドする場合にのみ必要です**：

コマンドラインコンソール経由でビルドする場合は、プロジェクトレベルの `build.gradle` ファイル内の `siyuanVersionName` と `siyuanVersionCode` を変更するだけでなく、以下の手順も実行する必要があります：

1. `signings.templates.gradle` ファイルをコピーして `signings.gradle` にリネームします。
2. `signings.gradle` 内の関連情報を設定します。
3. コマンドラインを使用してプロジェクトのルートディレクトリに移動し、以下を実行します
   ```shell
   # windows
   .\gradlew clean buildReleaseTask
   # linux
   gradle clean buildReleaseTask
   ```

   命名規則は以下の通りです：

   ```txt
   assemble/bundle  Googleplay  Debug/Release
   ```

   `assemble` は APK を生成します
   `bundle` は AAB を生成します
   `Googleplay` はチャネルパッケージの名前です；`flavors.gradle` の `productFlavors {}` 設定を参照して指定箇所を確認してください
   `Debug/Release` はテスト版/正式版を表します
4. 実行完了後、生成されたプログラムは以下の場所で確認できます
   ```txt
   siyuan-android\app\build-release\siyuan-${versionName}-all
   ```
