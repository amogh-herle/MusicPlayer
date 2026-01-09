# MusicPlayer

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Android%2015-brightgreen)
![Android](https://img.shields.io/badge/Android-15-orange)

## Features

- **Smart Shuffle Engine**: Custom algorithm that prevents the same artist from playing back-to-back.
- **Background Playback**: Full Android 15 MediaSession support with notification controls.
- **Targeted Scanning**: Specific logic to scan the `/Music/MySongs` directory by default.
- **Modern UI**: Spotify-style expandable mini-player with shared element transitions.
- **Queue Management**: Drag-and-drop reordering with optimistic UI updates.
- **Fuzzy Search**: Fast local search for tracks and artists.

## Screenshots

![Home Screen](screenshots/home.png)
![Player Screen](screenshots/player.png)

## Download & Install

You don't need to build the code yourself!

1. **[Download the latest APK here](https://github.com/amogh-herle/MusicPlayer/releases/latest)**.
2. Install the APK on your Android phone.
    - *Note: You may need to allow "Install from unknown sources" since this is a custom app.*
3. Place your music files in the `/Music/MySpotifyBackup` folder on your phone storage.

## Building from Source (Optional)

If you want to contribute or modify the code:

1. Clone the repository:
   ```bash
   git clone [https://github.com/amogh-herle/MusicPlayer.git](https://github.com/amogh-herle/MusicPlayer.git)