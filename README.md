# Read Along Web APK

一个基础 Kotlin Android WebView 壳应用，默认全屏打开：

https://readalong.google.com/

## 功能

- 全屏 WebView，无额外标头。
- 只声明网络和麦克风权限；麦克风权限会在网页真正请求录音时再弹出。
- WebRTC 麦克风授权会转交给网页。
- 支持文件选择和下载；文件选择通过系统选择器完成，不申请照片/视频/音频读取权限。
- 支持多个网址：从屏幕左边缘向右滑动可打开网址菜单；长按网页区域也可打开。
- 支持在应用内添加/删除自定义网址，后续切换不需要重新打包。
- GitHub Actions 直接打包 APK。

## 修改网址

编辑 `app/src/main/java/com/jchshi/readalong/AppConfig.kt`：

```kotlin
object AppConfig {
    val sites = listOf(
        WebEntry("Read Along", "https://readalong.google.com/"),
        WebEntry("Example", "https://example.com/"),
    )
}
```

应用内从屏幕左边缘向右滑动即可切换网址，选择结果会保存在设备本地。

也可以直接在应用内点“添加网址”临时添加站点；这些自定义网址只保存在当前设备上。

## GitHub CI 打包

把这个目录推到 GitHub 后，进入 `Actions`，运行 `Android APK` workflow。

产物会在 workflow 的 artifact 里，名称为 `readalong-arm64-apk`。

CI 使用仓库里的固定 release keystore 签名，因此同包名 APK 后续可以覆盖升级。若设备上安装过早期 debug 签名版本，需要先卸载一次再安装新版。
