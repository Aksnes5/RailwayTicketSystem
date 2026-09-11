@echo off
echo 正在构建12306购票系统APK...
echo.

REM 检查是否安装了Android SDK
if not defined ANDROID_HOME (
    echo 错误：未找到ANDROID_HOME环境变量
    echo 请先安装Android SDK并设置ANDROID_HOME环境变量
    echo 下载地址：https://developer.android.com/studio
    pause
    exit /b 1
)

REM 检查Java环境
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误：未找到Java环境
    echo 请先安装Java JDK 11或更高版本
    pause
    exit /b 1
)

echo 开始构建...
echo.

REM 清理之前的构建
if exist app\build rmdir /s /q app\build

REM 构建APK
gradlew.bat assembleDebug

if %errorlevel% equ 0 (
    echo.
    echo ✅ 构建成功！
    echo.
    
    REM 检查APK文件是否存在
    if exist app\build\outputs\apk\debug\app-debug.apk (
        echo 📱 APK文件已生成，正在复制到工作目录...
        
        REM 生成带时间戳的文件名
        for /f "tokens=1-3 delims=/" %%a in ('date /t') do set mydate=%%c%%a%%b
        for /f "tokens=1-2 delims=:" %%a in ('time /t') do set mytime=%%a%%b
        set mytime=%mytime: =0%
        
        REM 复制APK到工作目录
        copy "app\build\outputs\apk\debug\app-debug.apk" "12306购票系统-v1.1-%mydate%-%mytime%.apk" >nul
        
        if %errorlevel% equ 0 (
            echo ✅ APK已复制到工作目录
            echo.
            echo 📁 文件位置：D:\12306\12306购票系统-v1.1-%mydate%-%mytime%.apk
            echo.
            
            REM 显示文件信息
            echo 📊 文件信息：
            dir "12306购票系统-v1.1-%mydate%-%mytime%.apk" | find "12306购票系统"
            echo.
            echo 🚀 可以直接安装到Android设备上使用！
        ) else (
            echo ❌ 复制APK文件失败
        )
    ) else (
        echo ❌ APK文件未找到
    )
) else (
    echo.
    echo ❌ 构建失败，请检查错误信息
)

echo.
pause





