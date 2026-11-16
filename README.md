<p align="center">
  <img src="assets/logo.png" width="120" alt="Mash Deck Logo" />
</p>

<h1 align="center">Mash Deck</h1>

<hr />

<p align="center">
  <b>Android Virtual Controller Client (VSD2 Protocol)</b><br />
  A high-performance, open-source macro keypad client for Android devices communicating directly over local Wi-Fi with encrypted WebSocket protocol, bi-directional sync, and multi-device profile slots.
</p>

<p align="center">
  <a href="https://github.com/maulana-mash/Mash-Deck/releases/latest/download/MashDeck.apk">
    <img src="https://img.shields.io/badge/DOWNLOAD%20APK-LATEST%20RELEASE-111111?style=for-the-badge&logo=android&logoColor=white&labelColor=333333" alt="Download APK" />
  </a>
  <img src="https://img.shields.io/badge/PLATFORM-ANDROID%20API%2024%2B-111111?style=for-the-badge&labelColor=333333" alt="Platform" />
  <img src="https://img.shields.io/badge/PROTOCOL-VSD2%20NATIVE-111111?style=for-the-badge&labelColor=333333" alt="Protocol" />
  <img src="https://img.shields.io/badge/SECURITY-AES--256--GCM-111111?style=for-the-badge&labelColor=333333" alt="Security" />
  <img src="https://img.shields.io/badge/LICENSE-MIT-111111?style=for-the-badge&labelColor=333333" alt="License" />
</p>

<br />

---

## Features

- **Encrypted Local Networking**: Native WebSocket communication with end-to-end ECDH P-256 key exchange and AES-256-GCM symmetric encryption.
- **Bi-directional Page Sync**: Real-time layout and button state synchronization between mobile keypad and host application.
- **Virtual Device Slots**: Dynamic multi-profile management allowing independent layouts across different workflows (e.g., Productivity, Streaming, Gaming).
- **Zero Configuration Discovery**: Instant local network discovery using mDNS, with manual IP address and QR code pairing fallbacks.
- **Modern Jetpack Compose UI**: Clean dark slate visual styling optimized for OLED displays with tactile haptic feedback.
- **Completely Local & Subscription-Free**: Runs entirely within your local Wi-Fi network without third-party cloud servers or recurring fees.

---

## Download & Installation

Pre-compiled APK releases are available directly under the [Releases](https://github.com/maulana-mash/Mash-Deck/releases) tab.

1. Download **[MashDeck.apk](https://github.com/maulana-mash/Mash-Deck/releases/latest/download/MashDeck.apk)** from the latest release.
2. Install the package on your Android device (enable *Install unknown apps* if prompted).
3. Connect your Android device to the same Wi-Fi network as your host computer.
4. Launch the application to auto-discover and link with your desktop host.

---

## Architecture & Tech Stack

- **Target Platform**: Android (Minimum SDK 24 / Nougat, Target SDK 35)
- **Programming Language**: Kotlin
- **User Interface**: Jetpack Compose & Material Design 3
- **Network Engine**: OkHttp 4 (WebSocket), JmDNS (mDNS Service Discovery)
- **Data Serialization**: Protocol Buffers (Google Protobuf)
- **Cryptography**: SpongyCastle / BouncyCastle (ECDH P-256, AES-256-GCM)
- **Image Decompression**: Native LibLZF decompressor and Android Bitmap rendering

---

## Building from Source

### Prerequisites
- JDK 17 or higher
- Android SDK (API 34/35)

### Build Steps
1. Clone this repository:
   ```bash
   git clone https://github.com/maulana-mash/Mash-Deck.git
   cd Mash-Deck
   ```
2. Compile the release APK package:
   ```bash
   ./gradlew assembleRelease
   ```
3. The compiled binary will be placed at:
   `app/build/outputs/apk/release/app-release.apk`

---

## Automated CI/CD (GitHub Actions)

An automated release workflow is configured in `.github/workflows/release.yml`. When pushing a new version tag (e.g. `v1.0.1`), GitHub Actions will automatically:
1. Initialize the Android build environment in the cloud runner.
2. Compile the signed release APK (`MashDeck.apk`).
3. Publish a new GitHub Release with the APK artifact attached.

---

## License

This project is licensed under the terms of the MIT License.
