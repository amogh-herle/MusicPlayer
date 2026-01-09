# 🎵 MusicPlayer

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose">
  <img src="https://img.shields.io/badge/Android-15%20(API%2035)-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Min%20SDK-26-orange" alt="Min SDK">
  <img src="https://img.shields.io/github/license/amogh-herle/MusicPlayer" alt="License">
</p>

<p align="center">
  A modern, offline-first music player for Android built with <strong>Jetpack Compose</strong> and <strong>Material 3</strong>.<br>
  Features a <strong>Smart Shuffle</strong> algorithm, Spotify-style UI, and full Android 15 background playback support.
</p>

---

## 📱 Screenshots

<p align="center">
  <img src="screenshots/home.png" width="30%" alt="Home Screen">
  <img src="screenshots/player.png" width="30%" alt="Player Screen">
  <img src="screenshots/notification.png" width="30%" alt="Notification">
</p>

---

## ✨ Features

### 🎧 Core Playback Engine
| Feature | Description |
|---------|-------------|
| **Offline-First** | Plays `.mp3` and `.m4a` files directly from local storage |
| **Targeted Scanning** | Scans `/Music/MySpotifyBackup` by default (avoids WhatsApp/ringtones clutter) |
| **Background Playback** | Full Android 15 `MediaSessionService` with `foregroundServiceType="mediaPlayback"` |
| **Audio Focus** | Automatically pauses when other apps take audio (calls, YouTube, etc.) |
| **Smart Shuffle** | Custom algorithm ensuring no two songs from the same artist play back-to-back |

### 🎨 Modern UI/UX (Jetpack Compose)
| Feature | Description |
|---------|-------------|
| **Mini Player** | Floating bar at the bottom when browsing |
| **Expandable Full Player** | Smooth animation from Mini → Full Screen |
| **Material 3 Dark Theme** | Modern design with vibrant Cyan/Purple accents |
| **Album Art Everywhere** | List items, full player, and system notification |
| **Animated Glow Effects** | Pulsing glow on album art when playing |

### 📋 Queue & Library Management
| Feature | Description |
|---------|-------------|
| **Drag-and-Drop Reorder** | Long-press to reorder songs in the queue |
| **Optimistic UI Updates** | Instant visual feedback while dragging |
| **Auto-Scroll** | List scrolls when dragging to edges |
| **Fuzzy Search** | Find tracks by title, artist, or album |
| **Directory Picker** | Change music folder via Navigation Drawer |

### 🤖 System Integrations
| Feature | Description |
|---------|-------------|
| **Notification Controls** | Play/Pause, Next, Previous with seekbar |
| **Lock Screen Support** | Full media controls on lock screen |
| **MediaSession** | Works with Bluetooth, Android Auto, etc. |

---

## 🏗️ Architecture

```
app/src/main/java/com/example/musicplayer/
├── MainActivity.kt              # Entry point, permissions, Compose setup
├── MusicPlayerService.kt        # Foreground service, MediaPlayer, notifications
├── MusicPlayerViewModel.kt      # UI state, playback control
├── MusicPlaybackState.kt        # Shared state (Service ↔ UI via StateFlow)
├── PlaylistManager.kt           # Queue logic, Smart Shuffle algorithm
├── LocalScanner.kt              # MediaStore scanning for mp3/m4a files
├── data/
│   └── Song.kt                  # Song data class
└── ui/
    ├── components/
    │   ├── MiniPlayer.kt        # Collapsible mini player
    │   ├── FullScreenPlayer.kt  # Expanded player view
    │   ├── DraggableSongList.kt # Drag-and-drop list with auto-scroll
    │   └── SongList.kt          # Main song list container
    └── theme/
        ├── Color.kt             # Color palette
        ├── Theme.kt             # Material 3 theme
        ├── Type.kt              # Typography
        └── Shape.kt             # Shape definitions
```

### Key Design Decisions
- **StateFlow over Broadcasts**: Uses Kotlin `StateFlow` for Service↔UI communication instead of deprecated `LocalBroadcastManager`
- **Optimistic UI**: Drag-and-drop updates UI instantly, syncs backend afterward
- **Smart Shuffle**: Three-pass algorithm to break artist clusters after initial shuffle

---

## 📥 Installation

### Option 1: Download APK (Recommended)
1. **[Download the latest APK](https://github.com/amogh-herle/MusicPlayer/releases/latest)**
2. Install on your Android phone
   - Enable "Install from unknown sources" if prompted
3. Place music files in `/Music/MySpotifyBackup` on your phone
4. Grant audio permissions when prompted

### Option 2: Build from Source
```bash
# Clone the repository
git clone https://github.com/amogh-herle/MusicPlayer.git
cd MusicPlayer

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

---

## 🛠️ Requirements

| Requirement | Version |
|-------------|---------|
| **Android** | 8.0+ (API 26) |
| **Target SDK** | 35 (Android 15) |
| **Kotlin** | 2.0 |
| **Gradle** | 8.x |

### Required Permissions
```xml
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## 🔧 Configuration

### Changing Default Music Directory
By default, the app scans `/Music/MySpotifyBackup`. To change:
1. Open the Navigation Drawer (hamburger menu)
2. Tap "Change Music Directory"
3. Select your music folder using the system picker

Or modify the default in [LocalScanner.kt](app/src/main/java/com/example/musicplayer/LocalScanner.kt):
```kotlin
private const val DEFAULT_DIRECTORY = "/Music/MySongs"  // Change this
```

---

## 📦 Dependencies

| Library | Purpose |
|---------|---------|
| **Jetpack Compose BOM** | Modern declarative UI |
| **Material 3** | Design system |
| **Coil** | Image loading (album art) |
| **AndroidX Media** | MediaSession compatibility |
| **DocumentFile** | SAF directory picker |

---

## 🤝 Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is open source. See the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI toolkit
- [Material Design 3](https://m3.material.io/) - Design system
- [Coil](https://coil-kt.github.io/coil/) - Image loading library

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/amogh-herle">Amogh</a>
</p>