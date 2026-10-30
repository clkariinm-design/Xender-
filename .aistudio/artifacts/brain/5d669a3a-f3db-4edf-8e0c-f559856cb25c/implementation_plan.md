# Dependency & Service Cleanup for AirShare

This maintenance cycle streamlines the codebase by removing all unused cloud plugins, Firebase dependencies, Google Services, Secrets Gradle plugin, Room/KSP code generation, and HTTP libraries, making the project 100% self-contained, lightweight, and offline-buildable with zero external credentials.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following cleanup scope was confirmed during clarification:
> - **Remove Firebase & Google Services**: Eliminate `com.google.gms.google-services` plugin, `google-services.json` requirements, Firebase BOM, App Check, and AI Logic dependencies.
> - **Remove Room & KSP**: Eliminate `com.google.devtools.ksp` plugin and AndroidX Room compiler/runtime, relying on efficient in-memory transfer state and direct MediaStore Scoped Storage queries.
> - **Remove Secrets Gradle Plugin**: Eliminate `com.google.android.libraries.mapsplatform.secrets-gradle-plugin` and `.env` template requirements so Gradle builds cleanly without any local properties or environment secrets.
> - **Remove Unused HTTP/Networking**: Eliminate Retrofit, OkHttp, Logging Interceptor, and Moshi since AirShare uses native Java Socket channels (`ServerSocket` / `Socket`).
> - **Preserve Core UI & Functionality**: Keep Compose BOM, Material 3, Material Icons Extended, Coil, ZXing, Coroutines, and local unit test frameworks intact.

---

### 1. Overview & Core Concept

- **What It Does**: Strips unnecessary build plugins, pre-installed templates, and redundant transitive dependencies from the project.
- **Why It Matters**: Dramatically reduces Gradle configuration and compilation times, eliminates missing `google-services.json` build warnings, trims APK binary size, and ensures the repository builds completely offline without external network or service dependencies.

---

### 2. User Experience & Visual Design

- **Zero UI Regression**: All existing user interfaces (Home Screen, Categorized File Explorer, Receiver QR view, Sender stream dashboard) remain 100% identical in functionality, styling, and responsiveness.
- **Visual Design**: The custom M3 dark tech theme, animated status indicators, custom adaptive launcher icons, and Coil thumbnail rendering are fully preserved.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Native Java Sockets over Retrofit/OkHttp**
  - *Chosen Approach*: Retain pure `java.net.ServerSocket` and `java.net.Socket` implementation with binary wire framing.
  - *Why*: AirShare transmits files directly over raw TCP sockets on the local LAN/hotspot; HTTP clients (Retrofit/OkHttp) are completely unused and add unnecessary overhead.
- **Decision 2: Direct MediaStore & Memory State over Room**
  - *Chosen Approach*: Query system `MediaStore` directly on background dispatchers and store transfer history in ViewModel state.
  - *Why*: Removes KSP symbol processing task, reducing build time and eliminating Room annotation processing overhead.
- **Decision 3: Zero Configuration / No Secrets Required**
  - *Chosen Approach*: Clean build configuration so `gradle assembleDebug` executes cleanly without requiring `.env`, `google-services.json`, or external tokens.

---

### 4. Technical Architecture & Data Strategy

#### Dependency Hierarchy After Cleanup

```
┌────────────────────────────────────────────────────────────────────────┐
│                        AirShare Core Applet                            │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         ▼                         ▼                         ▼
┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐
│  Jetpack Compose │      │  Local P2P Media │      │ Offline QR Code  │
│  - Compose BOM   │      │  - Coil (Async)  │      │  - ZXing Core    │
│  - Material 3    │      │  - MediaStore    │      │    (BitMatrix)   │
│  - Icons Ext     │      │  - Java Sockets  │      └──────────────────┘
└──────────────────┘      └──────────────────┘
```

#### Files to Clean
1. `build.gradle.kts`: Remove `google.devtools.ksp`, `secrets`, and `google.services` plugins.
2. `app/build.gradle.kts`:
   - Remove unused plugins (`google.devtools.ksp`, `secrets`, `google.services`).
   - Remove `secrets { ... }` block and `googleServices { ... }` configuration.
   - Remove all Firebase, Room, KSP, Retrofit, OkHttp, and Moshi dependencies.
   - Retain only core Compose, Lifecycle, Coroutines, Coil, ZXing, and testing dependencies.
3. `gradle/libs.versions.toml`:
   - Remove unused version declarations (`ksp`, `secrets`, `googleServices`, `firebase`, `retrofit`, `room`, `moshi`, `okhttp`, etc.).
   - Retain only active dependencies.
4. `AndroidManifest.xml`: Ensure clean manifest without unnecessary configurations while preserving required Wi-Fi and storage permissions.
5. Verification: Execute `gradle assembleDebug` and unit tests to ensure clean, green compilation.
