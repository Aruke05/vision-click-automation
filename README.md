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

如果“窗口句柄截图（严格后台）”结果是全黑，通常不是缺少第三方库，而是 `PrintWindow` 对目标窗口渲染方式不兼容（常见于 DirectX/硬件加速/受保护渲染）。

优先按以下顺序排查：

- 以管理员权限启动本程序（和目标窗口保持同等权限）
- 目标程序改为窗口化/无边框窗口化（避免独占全屏）
- 关闭目标程序硬件加速（如浏览器、Electron 应用）
- 如仍失败，查看日志中的句柄抓图尝试明细（`PrintWindow` / `BitBlt(GetDC)`）

当前版本默认策略：在绑定窗口时先探测句柄抓图是否可用，再固定本次绑定的截图模式：

- 可用：后续监控直接使用后台句柄截图（不再每轮做黑帧模式切换判断）。
- 不可用：后续监控直接使用屏幕截图。

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

