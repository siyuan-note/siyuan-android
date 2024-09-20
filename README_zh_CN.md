[English](https://github.com/siyuan-note/siyuan-android/blob/master/README.md)

## 概述

* 报告问题/咨询讨论请到 [SiYuan issues](https://github.com/siyuan-note/siyuan/issues)
* 欢迎参与代码贡献

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/org.b3log.siyuan/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=org.b3log.siyuan)

## 搭建步骤

1. 参考[思源笔记开发指南](https://github.com/siyuan-note/siyuan/blob/master/.github/CONTRIBUTING_zh_CN.md)编译内核
2. 拷贝资源文件并打包 app/src/main/assets/app.zip
   * appearance
   * guide
   * stage
   * changelogs

目录结构参考：

![project-tree](project-tree.png)

![app.zip](app-zip.png)

## 关于多渠道软件分发

如果你使用的是 Android Studio 的【Build】【Generate Signed Bundle APK...】的方式构建程序，只需要修改项目级的 build.gradle 文件内的
siyuanVersionName 和 siyuanVersionCode 两个版本号即可，修改完毕后直接打包，可忽略以下内容。

### 步骤

**以下内容仅仅是在控制台命令行执行时才需要配置**：

需要使用控制台命令行构建，不仅仅需要修改项目级的 build.gradle 文件内的 siyuanVersionName 和 siyuanVersionCode 版本号，还需要进行以下操作：

1. 将 signings.templates.gradle 复制一份，并且重命名为 signings.gradle
2. 配置 signings.gradle 相关信息
3. 使用控制台进入项目根目录并执行以下内容
   ```shell
   # windows
   .\gradlew clean buildReleaseTask
   
   # linux
   gradle clean buildReleaseTask
   ```

   这里的命名规则是：
   
   ```txt
   assemble/bundle  Googleplay  Debug/Release
   ```
   
   `assemble` 生成 APK
   `bundle` 生成 AAB
   `Googleplay` 为渠道包名称，指定位置请看 flavors.gradle productFlavors {} 配置
   `Debug/Release` 测试版/正式版
4. 执行完成之后，你可以在以下位置找到生成好的程序
   ```txt
   siyuan-android\app\build-release\siyuan-${versionName}-all
   ```
