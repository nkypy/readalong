# Read Along Web APK

一个基础 Kotlin Android WebView 壳应用，默认全屏打开：

https://readalong.google.com/

## 功能

- 全屏 WebView，无额外标头。
- Manifest 和运行时请求麦克风、存储/媒体读取权限。
- WebRTC 麦克风授权会转交给网页。
- 支持文件选择和下载到系统 Downloads。
- 支持多个网址：点击左侧中部窄把手可切换网址，长按把手可管理自定义网址。
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

应用内点击左侧中部窄把手即可切换网址，选择结果会保存在设备本地。

也可以直接在应用内点“添加网址”临时添加站点；这些自定义网址只保存在当前设备上。

## GitHub CI 打包

把这个目录推到 GitHub 后，进入 `Actions`，运行 `Android APK` workflow。

产物会在 workflow 的 artifact 里，名称为 `readalong-arm64-apk`。
