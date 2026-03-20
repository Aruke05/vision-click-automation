@echo off

:: 检查是否是管理员权限
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo 请求管理员权限...
    powershell -Command "Start-Process '%~f0' -Verb runAs"
    exit
)

:: 切换到当前目录
cd /d %~dp0

:: 启动 jar（GUI 程序）
echo 启动 desktop-auto-script...
java -jar desktop-auto-script-1.0.0.jar

pause
