# DJI Tello Hand Gesture Control — Android App

A native Android application built with **Kotlin**, **Jetpack Compose**, **CameraX**, **MediaPipe HandLandmarker**, and **TensorFlow Lite** that lets you connect to a **DJI Tello** drone over Wi-Fi and fly it in real time using hand gestures.

## Prebuilt APK

A ready-to-install APK is included directly in this repository:

- **[`releases/tello-gesture-android-v1.0.0.apk`](releases/tello-gesture-android-v1.0.0.apk)**

### Install on an Android Device
- **Direct sideload**: Download `releases/tello-gesture-android-v1.0.0.apk` on your Android phone (Android 8.0 / API 26+) and open it to install.
- **Via ADB**:
  ```bash
  adb install -r releases/tello-gesture-android-v1.0.0.apk
  ```

---

## Features
- **On-Device Hand Gesture Recognition**: MediaPipe `hand_landmarker.task` extracts 21 hand landmarks, normalized relative to the wrist and classified by `keypoint_classifier.tflite` (8 flight gestures) with `GestureBuffer` temporal smoothing.
- **Direct DJI Tello UDP Control**: Native Kotlin UDP client (`192.168.10.1:8889`) for `command`, `takeoff`, `land`, `emergency`, and continuous `rc` velocity control, plus live video receiver (`TelloVideoReceiver`).
- **Android Wi-Fi Network Binding**: `WifiNetworkBinder` binds UDP sockets directly to the Tello Wi-Fi interface even when Android detects no internet on `TELLO-XXXXXX`.
- **Jetpack Compose UI**: Real-time camera preview with 21-point hand skeleton overlay (`HandOverlay`), telemetry readout, mode toggles, and safety controls.

---

## Supported Hand Gestures

| ID | Command | Symbol | Hand Pose |
| :-: | :--- | :-: | :--- |
| `0` | **Forward** | ✋ | Open palm facing camera, fingers up |
| `1` | **Stop** | ✊ | Upright closed fist (hover brake) |
| `2` | **Up** | 👆 | Index finger pointing straight up |
| `3` | **Land** | 👌 | OK sign (thumb + index pinched, 3 fingers up) |
| `4` | **Down** | 👇 | Index finger pointing straight down |
| `5` | **Back** | 👊 | Forward fist (knuckles facing camera) |
| `6` | **Left** | 🤛 | Upright fist with thumb pointing left |
| `7` | **Right** | 🤜 | Upright fist with thumb pointing right |

---

## How to Fly
1. Power on your **DJI Tello** drone and wait for the status LED to blink yellow.
2. Connect your Android phone to the **`TELLO-XXXXXX`** Wi-Fi network (accept "Keep Wi-Fi without internet" if prompted).
3. Open **Tello Gesture Control**, grant Camera permission, and tap **Connect**.
4. Tap **Takeoff**, switch to **Gesture Mode**, and steer using the 8 hand gestures above.
5. Show the **OK Sign (👌)** or tap **Land / Emergency** anytime to land safely.

---

## Project Structure

```text
├── app/
│   └── src/main/
│       ├── assets/
│       │   ├── hand_landmarker.task          # MediaPipe HandLandmarker model
│       │   ├── keypoint_classifier.tflite    # Trained 8-class gesture MLP
│       │   └── keypoint_classifier_label.csv # Class labels
│       └── kotlin/com/example/tellogesturecontrol/
│           ├── MainActivity.kt
│           ├── gesture/
│           │   ├── HandGestureRecognizer.kt  # MediaPipe + TFLite pipeline
│           │   ├── KeypointClassifier.kt     # TFLite interpreter wrapper
│           │   └── GestureBuffer.kt          # Multi-frame gesture smoothing
│           ├── tello/
│           │   ├── TelloClient.kt            # UDP SDK command & telemetry client
│           │   ├── TelloGestureController.kt # Gesture ID -> RC velocity mapping
│           │   ├── TelloVideoReceiver.kt     # Tello UDP H.264 video stream
│           │   └── WifiNetworkBinder.kt      # Binds traffic to Tello Wi-Fi
│           └── ui/
│               ├── main/                     # MainScreen, ViewModel, HandOverlay
│               └── theme/                    # Material 3 Compose theme
└── releases/
    └── tello-gesture-android-v1.0.0.apk      # Prebuilt installable APK
```

---

## Building from Source

### Requirements
- Android Studio Ladybug / Meerkat+ (or Gradle CLI)
- JDK 17
- Android SDK `compileSdk = 36`, `targetSdk = 35`, `minSdk = 26`

### Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumented UI tests on a connected device/emulator
./gradlew connectedDebugAndroidTest
```
The compiled debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
