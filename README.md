# 镜导 · Lens Coach

Android 相机：打开预览后叠加建议取景框，单击对焦，双击对焦并拍照。内置日系 / 电影 / 纪实三套风格，拍后套色并保存到相册 `Pictures/LensCoach`。

## 运行

1. 用 Android Studio 打开本目录，或命令行：

```bat
gradlew.bat installDebug
```

2. 需要一台 Android 8.0+ 真机（开 USB 调试）。首次会请求相机权限。

小米 / HyperOS 若提示 `INSTALL_FAILED_USER_RESTRICTED`：打开 **设置 → 开发者选项**，打开 **USB 调试（安全设置）** 和 **通过 USB 安装**，安装弹窗出现时点允许，再执行一次 `gradlew.bat installDebug`。

APK 路径：`app/build/outputs/apk/debug/app-debug.apk`。

## 手势

- **单击**：对焦 + 测光
- **双击**：对焦并拍照
- 底部快门：备用拍照
- 风格芯片：切换取景比例和成片调色
