# AirShare - Offline Local File Sharing App

AirShare is a high-speed, 100% offline peer-to-peer file transfer Android app (similar to Xender/ShareMe) that lets users discover nearby devices, pair instantly via QR code or local Wi-Fi hotspots, browse categorized device storage (Images, Videos, Documents, and APKs), and stream files securely over direct TCP sockets at maximum local Wi-Fi speeds without consuming any cellular data or internet.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and user experience decisions were confirmed during clarification:
> - **Connection Protocol**: Local Wi-Fi TCP Socket paired with QR code sharing and local network discovery (with fallback manual IP/Port entry). The receiver starts an embedded TCP server and displays a pairing QR code containing its local IP address and port; the sender connects directly with zero internet required.
> - **File Explorer Experience**: Tabbed categorized browser with distinct sections for Photos, Videos, Documents, and Installed Apps (APKs), featuring multi-selection, file size previews, and real-time selected payload summaries.
> - **Visual Identity**: Modern vibrant utility aesthetic using a deep electric-indigo and teal accent palette, high-contrast status cards, animated transfer progress meters, and dynamic speed calculations (MB/s).
> - **Storage & Permissions Handling**: Modern Scoped Storage querying via Android `MediaStore` with backward-compatible runtime permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` on Android 13+ / API 33+, and `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` on Android 12 and below).

---

### 1. Overview & Core Concept

- **What It Does**: Enables lightning-fast, zero-cloud offline file sharing between Android devices on the same Wi-Fi network or mobile hotspot. Users can inspect local media/files, select multiple items, and transmit them via raw streaming TCP sockets with live byte-level progress reporting.
- **Target Audience & Context**: Users in bandwidth-constrained environments, field teams sharing large video or document assets, and everyday users who need instant device-to-device transfers without cloud uploads or cables.
- **Key Value**: True offline capability, privacy (no external telemetry or servers), high throughput (up to 40-80 MB/s on 5GHz Wi-Fi), and no file size limitations.

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **Home Screen**:
   - Prominent hero header displaying local Wi-Fi connection status and device IP address.
   - Two large, high-impact action cards: **"Send Files"** (vibrant electric indigo) and **"Receive Files"** (vibrant emerald teal).
   - Quick Access stats: Total files shared, recent transfer history with timestamps, status, and one-tap re-open.
2. **File Explorer Screen (Send Mode)**:
   - Sticky top bar with search query filter and selected payload counter (e.g., "3 files selected • 48.2 MB").
   - Filter tabs: **Images**, **Videos**, **Documents**, **Apps**.
   - Responsive multi-column grid for images and videos with media thumbnails, and clean list layouts for documents and APKs.
   - Persistent floating bottom sheet with "Send (N)" action button triggering peer connection.
3. **Connection & Pairing Flow**:
   - **Receiver**: Launches TCP server on port `8988`, generates an offline pairing QR code containing `airshare://<IP>:<PORT>`, and listens for incoming payloads.
   - **Sender**: Scans the QR code or types the receiver's IP, performs TCP handshake, and begins streaming files sequentially.
4. **Active Transfer Dashboard**:
   - Real-time progress bar with percentage, transmitted/total bytes, transfer speed (MB/s), estimated time remaining (ETA), and individual file completion badges.
   - Audio/haptic feedback and completion dialog with options to open received files or share more.

#### Visual Identity & Theme
- **Theme Palette**: Deep slate background (`#0B0F19`), surface card containers (`#161E2E`), primary accent vibrant Indigo (`#4F46E5`), secondary receiver accent Teal (`#0D9488`), and alert/status tokens.
- **Typography**: Clean, robust hierarchy with bold numeric displays for transfer speeds and file sizes.
- **Micro-Interactions**: Pulse animations on the radar/receiver beacon, animated progress bars with gradient fills, and springy multi-select checkboxes.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Direct TCP Socket Protocol vs. Wi-Fi Direct (P2P)**
  - *Chosen Approach*: Raw non-blocking TCP socket server with custom binary packet framing (Header: 4-byte magic, filename UTF-8 length, filename, 8-byte file length; followed by raw chunked payload stream).
  - *Why*: Works seamlessly across existing Wi-Fi networks and portable Hotspot connections without the notoriously brittle OEM-specific Wi-Fi Direct pairing timeouts.
  - *Alternatives Considered*: Wi-Fi Direct P2P Group formation was considered, but requires complex negotiation that frequently fails across different vendor devices (Samsung, Xiaomi, Pixel) without manual intervention.
- **Decision 2: MediaStore & Scoped Storage Querying**
  - *Chosen Approach*: Query `MediaStore.Images`, `MediaStore.Video`, `MediaStore.Files`, and query installed non-system application packages via `PackageManager` to extract APK files.
  - *Why*: Strict adherence to Google Play policies without requiring dangerous `MANAGE_EXTERNAL_STORAGE` permissions unless targeting Android 10 and below.
- **Decision 3: Offline QR Code Generation**
  - *Chosen Approach*: Lightweight ZXing core matrix generation rendered onto Jetpack Compose `Canvas` or `Bitmap`.
  - *Why*: Zero dependencies on Google Play Services, operates completely air-gapped without network connectivity.

---

### 4. Technical Architecture & Data Strategy

#### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                          User Interface                          │
│  ┌───────────────────────┐  ┌──────────────────────────────────┐ │
│  │      HomeScreen       │  │       FileExplorerScreen         │ │
│  │ [Send]      [Receive] │  │ [Photos] [Videos] [Docs] [Apps]  │ │
│  └──────────┬────────────┘  └────────────────┬─────────────────┘ │
│             │                                │                   │
│             ▼                                ▼                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    TransferDashboardScreen                  │ │
│  │   [QR Code / Pairing]   [Speed Meter]   [Progress Bar]      │ │
│  └──────────────────────────────┬──────────────────────────────┘ │
└─────────────────────────────────┼────────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────────┐
│                    ViewModel & State Layer                       │
│  ┌───────────────────────┐  ┌──────────────────────────────────┐ │
│  │   TransferViewModel   │  │       FileExplorerViewModel      │ │
│  │  - serverStateFlow    │  │  - categorizedFilesFlow          │ │
│  │  - transferProgress   │  │  - selectedFilesSet              │ │
│  └──────────┬────────────┘  └────────────────┬─────────────────┘ │
└─────────────┼────────────────────────────────┼───────────────────┘
              │                                │
              ▼                                ▼
┌──────────────────────────────┐ ┌─────────────────────────────────┐
│     Network Socket Engine    │ │     Storage & MediaStore        │
│ ┌──────────────────────────┐ │ │ ┌─────────────────────────────┐ │
│ │ SocketServer (Receive)   │ │ │ │ MediaStoreRepository        │ │
│ │ - ServerSocket(8988)     │ │ │ │ - queryImages()             │ │
│ │ - Buffered input stream  │ │ │ │ - queryVideos()             │ │
│ └──────────────────────────┘ │ │ │ - queryDocuments()          │ │
│ ┌──────────────────────────┐ │ │ │ - queryInstalledApps()      │ │
│ │ SocketClient (Send)      │ │ │ └─────────────────────────────┘ │
│ │ - Socket(ip, 8988)       │ │ └─────────────────────────────────┘
│ │ - Chunked file streaming │ │
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

#### Data Entities & Transfer Protocol Framing
1. **TransferItem**: `id`, `name`, `size`, `uri`, `mimeType`, `category` (IMAGE, VIDEO, DOCUMENT, APP), `progress` (0f..1f), `status` (PENDING, TRANSFERRING, COMPLETED, FAILED).
2. **TransferSession**: `peerAddress`, `mode` (SENDER/RECEIVER), `totalBytes`, `transferredBytes`, `currentSpeedBytesPerSec`, `activeFileIndex`, `filesCount`.
3. **Stream Wire Protocol**:
   - `[Magic: 4 bytes (0x53, 0x48, 0x41, 0x52)]`
   - `[File Count: 4 bytes int]`
   - For each file:
     - `[Name Length: 4 bytes int]`
     - `[File Name: UTF-8 bytes]`
     - `[File Size: 8 bytes long]`
     - `[File Payload: raw bytes with periodic progress emission]`
     - `[File Ack: 1 byte confirmation (0x06 ACK)]`
