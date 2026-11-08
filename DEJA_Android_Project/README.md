# DEJA Android Assistant — consolidated project v0.7.0

This is the single maintained Android Studio project for DEJA. It is a source project, not an APK. It can be built locally with Android Studio or remotely using the included GitHub Actions workflow.

## Included foundations
- Futuristic DEJA interface packaged in a native Android app.
- Local typed command handling for greetings, time/date, simple arithmetic, notes, and selected Android settings/app shortcuts.
- Speech recognition request with an offline-only option, where supported by the device's on-device recognition service and installed language packs.
- Android text-to-speech, subject to installed voices.
- Tap-to-speak and opt-in repeated "Hey DEJA" recognition while the app is open. This is not an always-on background wake-word service.
- Strong-biometric owner verification using AndroidX BiometricPrompt.
- AILI and MAGGY permission preference profiles. These are not yet independent identity-enrolment/authentication flows.
- Dialler, SMS compose, WhatsApp share, camera, contacts, calendar, alarm and settings intents. User reviews and confirms actions in Android's destination app.
- Device-finder shortcut to Google's Android device-finding site.
- AES-GCM backup export using Android Keystore. Current encryption is device-bound and not a portable recovery solution.

## Explicitly excluded
- A dedicated Oshiwambo offline speech recognition/synthesis engine, as requested.

## Not yet fully implemented
- General-purpose conversational AI model. The current local brain handles a limited set of commands; open-ended AI needs a separately chosen online provider or a properly packaged local model.
- Background/always-on wake-word service.
- Automatic call answering or screening. Android requires a dedicated call-screening implementation and system role, and device/OEM testing.
- Automatic SMS replies. The current app stores reply preferences/templates but does not silently send messages. Full SMS automation requires default-SMS role and careful permissions. WhatsApp does not allow unrestricted third-party automation.
- Independent secure credential enrolment for AILI and MAGGY.
- Full remote anti-theft controls. The app provides a device-finder shortcut; it cannot guarantee locating or controlling a powered-off/offline phone.
- Portable encrypted backup restore across devices. Current Keystore key is device-bound.

## Build

### Cloud build (without installing Android SDK on your PC)
See `BUILD_WITH_CLOUD.txt`. The `.github/workflows/android.yml` workflow builds a debug APK on GitHub Actions and uploads it as an artifact. This requires a GitHub account and uploading the project contents to a repository. Keep the repository private.

### Local build
Open this folder in Android Studio with Android SDK Platform 35 installed, sync Gradle, then choose Build > Build Bundle(s) / APK(s) > Build APK(s).

## Test requirements
- Test microphone permission and speech recognition.
- Test TTS language/voice availability.
- Test biometric prompt with a strong biometric enrolled on the device.
- Test each Android intent on the target Samsung firmware.
- Test offline recognition only with a compatible on-device speech service and downloaded language pack.

This source has not been compiled or tested on a physical Samsung phone in this environment. A successful build and real-device test are still required.
