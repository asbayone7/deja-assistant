# DEJA Android Assistant — consolidated project v0.8.6

This is the single maintained Android Studio project for DEJA. It is a source project, not an APK. It can be built locally with Android Studio or remotely using the included GitHub Actions workflow.

## Included foundations
- Futuristic DEJA interface packaged in a native Android app.
- Local typed command handling for greetings, time/date, simple arithmetic, notes, and selected Android settings/app shortcuts.
- Speech recognition request with an offline-only option, where supported by the device's on-device recognition service and installed language packs.
- Android text-to-speech, subject to installed voices.
- Tap-to-speak and opt-in repeated "Hey DEJA" recognition while the app is open. This is not an always-on background wake-word service.
- Strong-biometric owner verification using AndroidX BiometricPrompt.
- Asbay can register AILI and MAGGY on-device with separate 6–12 digit PINs. Only salted PBKDF2 hashes are stored; PINs are not included in chat or project files.
- AILI/MAGGY profile sign-in creates a session-scoped identity. Native Android methods enforce the selected profile's saved `basic`, `phone`, and `messages` permissions; the Web UI is not the security boundary.
- Owner-only controls (profile registration, permission changes, backup encryption/decryption, device finder, and default phone-app settings) require Asbay's strong Android biometric session. A profile sign-in ends the owner session.
- Security/anti-theft permissions cannot be granted to AILI or MAGGY in this build; these controls remain owner-only.
- Dialler, SMS compose, WhatsApp share, camera, contacts, calendar, alarm and settings intents. User reviews and confirms actions in Android's destination app.
- Device-finder shortcut to Google's Android device-finding site.
- AES-GCM backup export using Android Keystore; native backup encryption/decryption now independently requires owner biometric verification. Current encryption is device-bound and not a portable recovery solution.

## Explicitly excluded
- A dedicated Oshiwambo offline speech recognition/synthesis engine, as requested.

## Not yet fully implemented
- General-purpose conversational AI model. The current local brain handles a limited set of commands; open-ended AI needs a separately chosen online provider or a properly packaged local model.
- Background/always-on wake-word service.
- Automatic call answering or screening. Android requires a dedicated call-screening implementation and system role, and device/OEM testing.
- Automatic SMS replies. The current app stores reply preferences/templates but does not silently send messages. Full SMS automation requires default-SMS role and careful permissions. WhatsApp does not allow unrestricted third-party automation.
- PIN lockout: after 5 failed attempts, a 60-second pause; after 10, a 5-minute pause; after 15, a 30-minute pause after each further failed attempt. Successful login clears the counter.
- Owner-only profile reset removes that profile PIN and its saved permissions; Asbay must register a new PIN. Reset requires an active owner biometric session.
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

This source has not been compiled or tested on a physical Samsung phone in this environment. A successful build and real-device test are still required. The 0.8.6 update is source-only until the cloud or local Android build succeeds.


## v0.8.3 security changes
- Native authorization checks are applied to Android bridge actions, including basic app/settings shortcuts, dialler, SMS compose, and WhatsApp share.
- Phone and messaging actions are allowed only for the owner or a signed-in AILI/MAGGY profile with the matching permission enabled by Asbay.
- Device finder, backup, profile registration, permission administration, and default phone-app settings remain owner-only.
- Successful AILI/MAGGY sign-in switches out of the owner session, preventing a previously verified owner session from being reused by a profile.
- Profile permission settings are loaded from Android storage in the permission screen; the UI's local storage is not used as the authorization source.
- Android intents still open the destination app for user review; DEJA does not silently place calls or send messages.


## v0.8.4 security changes
- Added persistent failed-PIN attempt tracking and escalating cooldowns for AILI and MAGGY.
- Added native-side validation of PIN length and digits before hash verification.
- Added owner-only reset for an authorized profile, removing its credential and permission entries.
- Re-registering a profile creates a fresh random salt and clears the old lockout state.
- This source update has not been compiled or tested on a physical Android device.

## Session security and WebView hardening (v0.8.6)

- Owner biometric sessions automatically expire after 5 minutes.
- AILI and MAGGY profile sessions automatically expire after 15 minutes.
- Added a visible Sign out and lock DEJA action.
- Native authorization checks enforce session expiry; the UI also refreshes the displayed session state.
- WebView blocks external navigation, content access, file-to-file and universal file URL access, mixed content, and automatic popup windows. Android actions continue through the native bridge.
- These controls reduce exposure but do not replace a full security review or real-device testing.

## OpenAI brain (v0.8.5)

The Android app now has a native bridge to a separate OpenAI gateway. See `backend/README.md`. The API key must remain on the backend; never paste it into chat, Kotlin, HTML, Gradle files, or a public repository. Configure `DEJA_API_BASE_URL` as a Gradle project property when building. The gateway must be deployed over HTTPS and protected by real user authentication/quotas before public use.

The app's Online mode sends general questions to the configured gateway. Offline local commands continue to work without a network connection. This source package contains the integration, not a deployed service or a working APK.

See `CALLS_AND_MESSAGING_LIMITS.md` and `OSHIWAMBO_VOICE_STATUS.txt` for the platform and speech-engine work that remains.

## OpenAI gateway deployment preparation (v0.8.7)

A Render Blueprint is included as `render.yaml`. Deployment instructions are in `backend/README.md`. The gateway is not yet safe for unrestricted public use because real user authentication, per-user quotas, and monitoring still need to be added. Do not connect a released app until those protections are complete. An authorized adult must manage any API/hosting accounts and billing.


## DEJA v0.8.9 — authenticated OpenAI gateway

The backend now requires a signed 12-hour bearer token for `/v1/chat`. Android provides a native gateway sign-in dialog and encrypts the returned token using an AES-GCM key held in Android Keystore. Gateway credentials and the OpenAI API key remain server-side. See `backend/README.md` and `DEPLOYMENT_STATUS_v0.8.9.txt`.
