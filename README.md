# NX Keyboard

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![Android](https://img.shields.io/badge/Android-21%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Build Debug](https://github.com/dilekarkun6/NX-keyboard/actions/workflows/build-debug.yml/badge.svg)](https://github.com/dilekarkun6/NX-keyboard/actions/workflows/build-debug.yml)

An open-source, privacy-focused, AI-powered Android keyboard.

## Features

- **9 keyboard layouts** out of the box: English (QWERTY), Turkish (Q), Turkish (F), German (QWERTZ), French (AZERTY), Spanish, Russian (ЙЦУКЕН), Arabic (RTL), Japanese (Romaji), plus a numbers/symbols layout
- **AI assistance** powered by OpenRouter — grammar correction that understands typos and phonetic spellings, plus instant translation to any of 8 target languages
- **Built-in API key** with multi-layer obfuscation (XOR mask + Base64 + 4-fragment splitting) so users get AI features without setup; a personal key can override it from Settings
- **Voice input** via Android's `SpeechRecognizer` with locale-aware recognition
- **Clipboard manager** with persistent history — recently copied/cut items appear as tappable chips; tap to paste
- **1,800+ emojis** across 9 categories including 96 country flags
- **Light / Dark / System** themes
- **Fully localized**: app name and UI adapt to device language (English / Turkish included)
- **Privacy first**: API keys stored in `EncryptedSharedPreferences` (AES-256-GCM); nothing is sent anywhere except OpenRouter when you explicitly invoke AI features
- **No ads, no tracking, no telemetry**

## Screenshots

> Screenshots coming soon.

## Architecture

```
app/src/main/java/com/nxkeyboard/
├── service/
│   └── NXInputMethodService.kt      Main IME service — orchestrates keyboard, emoji panel, clipboard toolbar, voice, AI
├── keyboard/
│   ├── NXKeyboardView.kt            Custom Canvas-rendered key view (handles touch, shift, repeat-delete, layout switching)
│   ├── KeyboardLayoutManager.kt     XML layout parser + key-code constants
│   ├── EmojiKeyboardView.kt         RecyclerView-based emoji grid with category tabs
│   ├── EmojiData.kt                 Curated emoji set across 9 categories
│   ├── ClipboardToolbar.kt          Clipboard history chips + action buttons (cut/copy/paste/AI/translate)
│   └── VoiceInputManager.kt         SpeechRecognizer wrapper
├── ai/
│   ├── AIManager.kt                 OpenRouter HTTP client (5 free models supported)
│   └── ApiKeyVault.kt               Built-in key obfuscation + runtime decoding
├── language/
│   └── LanguageManager.kt           Locale management, layout resource resolution, RTL detection
├── theme/
│   └── ThemeManager.kt              Light / Dark / System theme resolver
├── settings/
│   └── SettingsActivity.kt          Preference screen (categories: Setup, General, Languages, Emoji, Clipboard, Voice, AI, About)
└── utils/
    ├── PrefsHelper.kt               Default + EncryptedSharedPreferences accessors
    ├── HapticHelper.kt              Vibration feedback (VibratorManager-aware for Android 12+)
    ├── ClipboardHelper.kt           Cut / copy / paste / select-all / cursor movement / history persistence
    └── RecentEmojiManager.kt        Tracks last 30 used emojis
```

## Built-in API key

The app ships with a working OpenRouter API key embedded in a way that resists simple extraction. The key is split into four byte fragments inside `app/src/main/java/com/nxkeyboard/ai/ApiKeyVault.kt`. At runtime the fragments are concatenated, Base64-decoded, then XOR-decrypted using the formula:

```
maskByte(i) = ((i * 17 + 91) ^ (i % 7 + 23) ^ 0x5A) & 0xFF
```

The plaintext key never appears in the APK directly. Users can replace it with their own OpenRouter key from **Settings → AI → Override API key** — when set, the user key takes priority over the built-in one.

## Build

### Option 1 — GitHub Actions (no local setup)

Push to `main` or `develop` and a debug APK builds automatically. Download it from the **Actions** tab → run → **Artifacts**.

For a signed release APK, push a tag matching `v*.*.*` (e.g. `v1.0.0`). Configure these repository secrets first:

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Your keystore file, base64-encoded |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

The release workflow decodes the keystore, builds, signs, and creates a GitHub Release with the APK attached.

### Option 2 — Local build

```bash
git clone https://github.com/dilekarkun6/NX-keyboard.git
cd NX-keyboard
chmod +x gradlew
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

**Requirements:** JDK 17, Android SDK with `compileSdk 34`. Gradle 8.9 is provided via the wrapper.

## Install & enable

1. Install the APK
2. **Settings → System → Languages & input → On-screen keyboard → Manage keyboards**
3. Enable **NX Keyboard**
4. Tap any text field, then the keyboard switcher icon → select **NX Keyboard**

## Tech stack

- **Kotlin** 2.0.21
- **Android Gradle Plugin** 8.5.2
- **AndroidX**: AppCompat, Material, RecyclerView, ConstraintLayout, Preference, Security-Crypto
- **OkHttp** 4.12.0 (OpenRouter API client)
- **Kotlin Coroutines** 1.8.1
- **Min SDK**: 21 (Android 5.0 Lollipop)
- **Target SDK**: 34 (Android 14)

## Supported AI models

All models are routed through OpenRouter's free tier:

- `meta-llama/llama-3.3-70b-instruct:free` *(default)*
- `deepseek/deepseek-r1:free`
- `qwen/qwen3-235b-a22b:free`
- `mistralai/mistral-7b-instruct:free`
- `meta-llama/llama-3.1-8b-instruct:free`

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | OpenRouter API calls (only when you invoke AI features) |
| `RECORD_AUDIO` | Voice input |
| `VIBRATE` | Haptic key-press feedback |

`BIND_INPUT_METHOD` is granted automatically by the system when you enable the keyboard.

## Roadmap

- [ ] Long-press popup characters (the layouts already declare them)
- [ ] Swipe typing / gesture input
- [ ] Custom themes and keyboard backgrounds
- [ ] More layouts (the framework already supports 28 locales; only 9 have full layouts shipped)
- [ ] Word prediction and next-word suggestion
- [ ] On-device autocorrect dictionary

## Contributing

Pull requests welcome. Please open an issue first for substantial changes.

## License

[GNU Affero General Public License v3.0](LICENSE) — if you fork or modify this software and serve it over a network, you must release your modifications under the same license.

## Credits

- Logo and design: [@dilekarkun6](https://github.com/dilekarkun6)
- AI inference: [OpenRouter](https://openrouter.ai)
