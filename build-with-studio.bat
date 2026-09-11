@echo off
echo ========================================
echo    12306购票系统 - 使用Android Studio构建
echo ========================================
echo.

echo 由于Gradle构建需要下载大量依赖，建议使用Android Studio构建：
echo.
echo 步骤1：打开Android Studio
echo 步骤2：选择 "Open an existing project"
echo 步骤3：选择 D:\12306 文件夹
echo 步骤4：等待Gradle同步完成（可能需要5-10分钟）
echo 步骤5：点击菜单 Build → Build Bundle(s) / APK(s) → Build APK(s)
echo.

echo 如果遇到问题，请检查：
echo - Android Studio版本（建议2023.1+）
echo - 网络连接（需要下载依赖）
echo - Java环境配置
echo.

echo 项目已修复的问题：
echo ✅ 应用图标问题已解决
echo ✅ Gradle版本已更新
echo ✅ Java路径已配置
echo.

echo 按任意键打开Android Studio...
pause >nul

REM 尝试打开Android Studio
if exist "C:\Program Files\Android\Android Studio\bin\studio64.exe" (
    start "" "C:\Program Files\Android\Android Studio\bin\studio64.exe" "D:\12306"
) else if exist "C:\Program Files (x86)\Android\Android Studio\bin\studio64.exe" (
    start "" "C:\Program Files (x86)\Android\Android Studio\bin\studio64.exe" "D:\12306"
) else (
    echo 未找到Android Studio，请手动打开
)




