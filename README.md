# Mash Deck

Mash Deck is an open-source alternative to Elgato Stream Deck Mobile for Android. A subscription-free drop-in replacement communicating natively with Stream Deck PC via reverse-engineered VSD2 encrypted WebSocket (AES-256-GCM, ECDH, Protobuf) with local mDNS discovery and zero cloud dependency.

---

## Features

- **Native VSD2 Engine**: End-to-end encrypted WebSocket connection (AES-256-GCM / ECDH) matching the official Stream Deck mobile protocol.
- **Bi-directional Page Sync**: Dynamic layout and page number synchronization directly matched to Stream Deck PC.
- **Multi-Device Profile Slots**: Switch between distinct virtual device profiles (e.g., Work, Streaming, Gaming) on the fly, allowing separate layouts and profiles on the PC.
- **Ultra-Fast mDNS Discovery**: Instant discovery of Stream Deck hosts running on the local network, with fallback manual and QR pairing.
- **Modern Jetpack Compose Interface**: Optimized dark slate theme with OLED-friendly styling, fluid animations, and customizable grid densities.
- **Hardware Feedback**: Native haptic feedback on key presses.

---

## Installation & Download

Pre-compiled APK packages are available directly in the [Releases](https://github.com/maulana-mash/Mash-Deck/releases) section.

1. Download `MashDeck.apk` from the latest release.
2. Install the APK on your Android device (ensure *Install from unknown sources* is permitted).
3. Ensure your phone and PC are connected to the same Wi-Fi / local network.
4. Launch Stream Deck on your PC, then open Mash Deck to auto-discover and connect.

---

## Tech Stack

- **Platform**: Android (Min SDK 24, Target SDK 34)
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose & Material 3
- **Network**: OkHttp 4 (WebSocket), JmDNS (mDNS Service Discovery)
- **Protocol**: Protocol Buffers (Google Protobuf)
- **Security**: SpongyCastle / BouncyCastle (ECDH + AES-256-GCM)
- **Image Processing**: LibLZF native decompression & Android Bitmap rendering

---

## Building from Source

### Prerequisites
- JDK 17 or higher
- Android SDK (API 34)

### Build Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/maulana-mash/Mash-Deck.git
   cd Mash-Deck
   ```
2. Build the release APK:
   ```bash
   ./gradlew assembleRelease
   ```
3. The generated APK will be located at:
   `app/build/outputs/apk/release/app-release.apk`

---

## License

This project is licensed under the MIT License.
