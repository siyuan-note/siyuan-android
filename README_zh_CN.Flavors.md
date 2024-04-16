
## 多渠道软件分发

如果你使用的是 Android Studio 的【Build】【Generate Signed Bundle APK...】的方式构建程序，请忽略以下内容。

**以下内容仅仅是在控制台命令行执行时才需要配置**：

### 步骤

1. 将 signings.templates.gradle 复制一份，并且重命名为 signings.gradle
2. 配置 signings.gradle 相关信息
3. 使用控制台进入项目根目录并执行以下内容：
```shell
# windows
.\gradlew clean assembleXiaomiRelease bundleGoogleplayRelease bundleHuaweiRelease assembleOfficialRelease

# linux
gradle clean assembleXiaomiRelease bundleGoogleplayRelease bundleHuaweiRelease assembleOfficialRelease
```
这里的命名规则是：
```txt
assemble/bundle  Xiaomi  Debug/Release
```
assemble 生成 APK
bundle 生成 AAB
Xiaomi 为渠道包名称，指定位置请看 flavors.gradle productFlavors {} 配置
Debug/Release 测试版/正式版

4. 执行完成之后，你可以在以下位置找到生成好的程序：
```txt
siyuan-android\app\build\outputs\apk\*     // apk 位置
siyuan-android\app\build\outputs\bundle\*  // aab 位置
```
