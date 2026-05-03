# Language Fluency Riser

Native Android MVP for a local-first B2 to C1 English fluency coach chain.

The app talks to a local Ollama server through `/api/chat` and defaults to:

- Server URL: `http://10.0.2.2:11434`
- Model: `gemma3:12b`

`10.0.2.2` is the Android emulator address for the host computer. For a physical Android phone, use your computer LAN IP, run Ollama on `0.0.0.0`, and allow the Windows firewall prompt for port `11434`.

## Build

This project is dependency-light and uses the Android Gradle Plugin already cached on this machine.

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
& "C:\Users\antoi\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat" :app:assembleDebug --offline
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
