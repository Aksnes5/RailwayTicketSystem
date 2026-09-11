@echo off
echo ========================================
echo    12306购票系统 - 快速构建APK
echo ========================================
echo.

REM 检查Android Studio是否安装
if exist "C:\Program Files\Android\Android Studio\bin\studio64.exe" (
    echo ✅ 检测到Android Studio已安装
    set "STUDIO_PATH=C:\Program Files\Android\Android Studio\bin\studio64.exe"
) else if exist "C:\Program Files (x86)\Android\Android Studio\bin\studio64.exe" (
    echo ✅ 检测到Android Studio已安装
    set "STUDIO_PATH=C:\Program Files (x86)\Android\Android Studio\bin\studio64.exe"
) else (
    echo ❌ 未检测到Android Studio，请先安装Android Studio
    echo 下载地址：https://developer.android.com/studio
    pause
    exit /b 1
)

REM 检查Java环境
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ 未检测到Java环境，请先安装Java JDK 11+
    pause
    exit /b 1
) else (
    echo ✅ Java环境检测通过
)

echo.
echo 正在尝试构建APK...
echo.

REM 尝试使用Android Studio的Gradle
set "GRADLE_PATH=C:\Program Files\Android\Android Studio\gradle\gradle-8.0\bin\gradle.bat"
if not exist "%GRADLE_PATH%" (
    set "GRADLE_PATH=C:\Program Files (x86)\Android\Android Studio\gradle\gradle-8.0\bin\gradle.bat"
)

if exist "%GRADLE_PATH%" (
    echo 使用Android Studio内置Gradle构建...
    "%GRADLE_PATH%" assembleDebug
    if %errorlevel% equ 0 (
        echo.
        echo ✅ 构建成功！
        echo APK文件位置：app\build\outputs\apk\debug\app-debug.apk
        echo.
        if exist "app\build\outputs\apk\debug\app-debug.apk" (
            echo 📱 APK文件信息：
            dir "app\build\outputs\apk\debug\app-debug.apk"
        )
    ) else (
        echo ❌ 构建失败，请使用Android Studio手动构建
    )
) else (
    echo ⚠️  未找到Gradle，建议使用Android Studio手动构建
    echo.
    echo 请按照以下步骤操作：
    echo 1. 打开Android Studio
    echo 2. 选择 "Open an existing project"
    echo 3. 选择 D:\12306 文件夹
    echo 4. 等待Gradle同步完成
    echo 5. 点击菜单 Build → Build Bundle(s) / APK(s) → Build APK(s)
)

echo.
echo 按任意键退出...
pause >nul




