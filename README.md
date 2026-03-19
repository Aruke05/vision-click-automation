# desktop-auto-script

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

## 配置文件

- 默认配置文件：`config.json`
- 运行时会自动读写该文件

## 隐私与缓存

`config.json` 可能包含以下敏感信息：

- 最近绑定窗口信息（PID、标题、类名）
- 模板图片路径
- 点击坐标与监控区域

如果要分享仓库或打包给他人，建议先清空配置缓存。当前仓库已将 `config.json` 重置为安全空配置（`{}`）。

