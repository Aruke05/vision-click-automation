# vision-click-automation


Windows 桌面自动化工具（Java 17 + Swing + JNA + OpenCV）。

## 环境要求

- JDK 17
- Maven 3.9+
- Windows 系统（依赖 `user32` 等 Win32 API）

## 快速开始

```bash
mvn clean package
java -jar target/desktop-auto-script-1.0.0.jar
```

## 句柄截图全黑排查

如果“窗口句柄截图（后台防遮挡）”结果是全黑，通常不是缺少第三方库，而是 `PrintWindow` 对目标窗口渲染方式不兼容（常见于 DirectX/硬件加速/受保护渲染）。

优先按以下顺序排查：

- 以管理员权限启动本程序（和目标窗口保持同等权限）
- 目标程序改为窗口化/无边框窗口化（避免独占全屏）
- 关闭目标程序硬件加速（如浏览器、Electron 应用）
- 切换截图模式为“屏幕截图（原方式）”

当前版本在“窗口句柄截图（后台防遮挡）”模式下，如果检测到 `PrintWindow` 返回黑帧，会自动回退到“屏幕截图（原方式）”，并在日志中提示回退原因。

## 打包给其他设备直接运行

可以，不需要对方单独安装 JDK。建议使用 `jpackage` 把 Java 运行时一起打包（JDK 17 自带）：

```bash
mvn clean package
jpackage \
  --name vision-click-automation \
  --type app-image \
  --input target \
  --main-jar desktop-auto-script-1.0.0.jar \
  --main-class com.example.autoscript.DesktopAutomationApp
```

生成目录可直接拷贝到其他 Windows 机器运行。
如果你要操作高权限窗口，仍建议以管理员权限启动应用。

## 配置文件

- 默认配置文件：`config.json`
- 运行时会自动读写该文件

## 隐私与缓存

`config.json` 可能包含以下敏感信息：

- 最近绑定窗口信息（PID、标题、类名）
- 模板图片路径
- 点击坐标与监控区域

如果要分享仓库或打包给他人，建议先清空配置缓存。当前仓库已将 `config.json` 重置为安全空配置（`{}`）。

