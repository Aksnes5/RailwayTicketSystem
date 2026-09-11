@echo off
echo 正在构建12306购票系统APK...
echo.

echo 请按照以下步骤操作：
echo.
echo 1. 打开Android Studio
echo 2. 选择 "Open an existing project"
echo 3. 选择 D:\12306 文件夹
echo 4. 等待Gradle同步完成
echo 5. 点击菜单 Build → Build Bundle(s) / APK(s) → Build APK(s)
echo 6. APK文件将生成在 app\build\outputs\apk\debug\ 目录下
echo.

echo 或者，如果您已经配置了Android SDK环境变量，可以尝试：
echo gradlew assembleDebug
echo.

pause




