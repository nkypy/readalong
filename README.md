# Read Along Web APK

一个基础 Java Android Custom Tabs 启动器，默认打开：

https://readalong.google.com/

## 功能

- 使用 Chrome Custom Tabs，优先调用 Chrome，避免 Read Along 拒绝内嵌 WebView/GeckoView。
- APK 本身只声明网络权限；麦克风、文件选择和下载由浏览器处理。
- 支持多个内置网址：如果配置多个网址，启动时会弹出选择框。
- GitHub Actions 直接打包 APK。

## 修改网址

编辑 `app/src/main/java/com/jchshi/readalong/AppConfig.java`：

```java
public final class AppConfig {
    public static final List<WebEntry> SITES = Arrays.asList(
        new WebEntry("Read Along", "https://readalong.google.com/"),
        new WebEntry("Example", "https://example.com/")
    );
}
```

Custom Tabs 会显示浏览器自己的顶部栏；这个栏不能由第三方 APK 完全隐藏。

## GitHub CI 打包

把这个目录推到 GitHub 后，进入 `Actions`，运行 `Android APK` workflow。

产物会在 workflow 的 artifact 里，名称为 `readalong-arm64-apk`。

CI 使用仓库里的固定 release keystore 签名，因此同包名 APK 后续可以覆盖升级。若设备上安装过早期 debug 签名版本，需要先卸载一次再安装新版。
