@echo off
set JAVA_HOME=D:\Game Mash_\Android Studio\jbr
set ANDROID_HOME=D:\Game Mash_\Android Studio 2
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "d:\Game Mash_\Antigravity Project\Mash Deck"
call "C:\Users\Mash_Maulana\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat" assembleRelease
if %ERRORLEVEL% EQU 0 (
    if not exist "C:\Users\Mash_Maulana\Documents\Mash - Deck" mkdir "C:\Users\Mash_Maulana\Documents\Mash - Deck"
    copy /Y "app\build\outputs\apk\release\app-release.apk" "C:\Users\Mash_Maulana\Documents\Mash - Deck\MashDeck.apk"
    copy /Y "app\build\outputs\apk\release\app-release.apk" "MashDeck.apk"
    echo Build and copy successful!
)
