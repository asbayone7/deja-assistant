package com.asbaygarcia.deja

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import android.os.Bundle
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.Executor
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var web: WebView
    private var speech: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val aiExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private lateinit var biometricExecutor: Executor
    @Volatile private var ownerVerified = false
    @Volatile private var activeProfile: String? = null
    @Volatile private var activeProfileExpiresAt: Long = 0L
    private val ownerSessionDurationMs = 5 * 60_000L
    private val profileSessionDurationMs = 15 * 60_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biometricExecutor = ContextCompat.getMainExecutor(this)
        tts = TextToSpeech(this, this)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        // The bundled HTML needs file access to load, but local files must not
        // be able to read other file/content URLs or cross-origin resources.
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = false
        web.settings.allowFileAccessFromFileURLs = false
        web.settings.allowUniversalAccessFromFileURLs = false
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.setSupportMultipleWindows(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            web.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString().orEmpty()
                // DEJA's only WebView document is its packaged local interface.
                // All external navigation is blocked; native actions use Android intents.
                return !target.startsWith("file:///android_asset/")
            }
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return !(url?.startsWith("file:///android_asset/") ?: false)
            }
        }
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(DejaBridge(), "DejaNative")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
        }
    }

    inner class DejaBridge {
        @JavascriptInterface fun startListening(language: String, offlineOnly: Boolean) {
            runOnUiThread {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41); notifyStatus("Allow microphone access, then tap Speak again."); return@runOnUiThread
                }
                if (offlineOnly) {
                    if (Build.VERSION.SDK_INT < 33 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this@MainActivity)) {
                        notifyStatus("Offline voice recognition is not available on this phone yet. Install an offline speech language pack in Android settings, or use typed commands. DEJA will not switch online."); return@runOnUiThread
                    }
                } else if (!SpeechRecognizer.isRecognitionAvailable(this@MainActivity)) { notifyStatus("Speech recognition is unavailable. Type your command instead."); return@runOnUiThread }
                try {
                    speech?.destroy()
                    speech = if (offlineOnly && Build.VERSION.SDK_INT >= 33) SpeechRecognizer.createOnDeviceSpeechRecognizer(this@MainActivity) else SpeechRecognizer.createSpeechRecognizer(this@MainActivity)
                    speech?.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = notifyStatus("Listening…")
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() = notifyStatus("Processing…")
                        override fun onError(error: Int) = notifyStatus("Couldn't recognise that. Try again or type.")
                        override fun onResults(results: Bundle?) { val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(); web.evaluateJavascript("window.onNativeSpeechResult && window.onNativeSpeechResult(${org.json.JSONObject.quote(text)})", null) }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, language); putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language); putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1); putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offlineOnly) }
                    speech?.startListening(intent)
                } catch (_: Exception) { notifyStatus("Voice recognition could not start. Type your command.") }
            }
        }
        @JavascriptInterface fun speak(text: String, language: String) { runOnUiThread {
            val locale = when(language) { "pt-BR" -> Locale("pt", "BR"); "ng-NA" -> Locale("ng", "NA"); else -> Locale.US }
            val engine = tts ?: return@runOnUiThread
            if (engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) {
                notifyStatus(if (language == "ng-NA") "Oshiwambo speech is not supported by the installed voice engine. The written response remains available." else "The selected voice is unavailable. The written response remains available.")
                return@runOnUiThread
            }
            engine.language = locale
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "DEJA_UTTERANCE")
        } }
        @JavascriptInterface fun askAi(message: String, language: String) {
            val prompt = message.trim()
            if (prompt.isEmpty() || prompt.length > 4000) { notifyStatus("Please enter a message under 4,000 characters."); return }
            val baseUrl = BuildConfig.DEJA_API_BASE_URL.trim().trimEnd('/')
            if (baseUrl.isEmpty()) { runOnUiThread { notifyStatus("OpenAI brain is not connected yet. Configure DEJA_API_BASE_URL and deploy the secure backend.") }; return }
            aiExecutor.execute {
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL("$baseUrl/v1/chat").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"; connectTimeout = 15000; readTimeout = 45000
                        doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    val body = org.json.JSONObject().put("message", prompt).put("language", language).toString()
                    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) throw IllegalStateException("AI service returned HTTP $code")
                    val answer = org.json.JSONObject(responseText).optString("reply").trim()
                    if (answer.isEmpty()) throw IllegalStateException("AI service returned an empty response")
                    runOnUiThread { web.evaluateJavascript("window.onDejaAiResponse && window.onDejaAiResponse(${org.json.JSONObject.quote(answer)})", null) }
                } catch (_: Exception) {
                    runOnUiThread { web.evaluateJavascript("window.onDejaAiResponse && window.onDejaAiResponse('I could not reach the AI service. Check the internet connection and backend settings, then try again.')", null) }
                } finally { connection?.disconnect() }
            }
        }
        @JavascriptInterface fun stopSpeaking() { runOnUiThread { tts?.stop(); notifyStatus("Voice stopped") } }
        @JavascriptInterface fun openWifiSettings() { runOnUiThread { if (!hasAccess("basic")) return@runOnUiThread; try { startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) { notifyStatus("Wi-Fi settings could not be opened.") } } }
        @JavascriptInterface fun openBluetoothSettings() { runOnUiThread { if (!hasAccess("basic")) return@runOnUiThread; try { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Exception) { notifyStatus("Bluetooth settings could not be opened.") } } }
        @JavascriptInterface fun openGeneralSettings() { runOnUiThread { if (!hasAccess("basic")) return@runOnUiThread; try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_: Exception) { notifyStatus("Settings could not be opened.") } } }
        @JavascriptInterface fun openCamera() { runOnUiThread { if (!hasAccess("basic")) return@runOnUiThread; try { startActivity(Intent("android.media.action.IMAGE_CAPTURE")) } catch (_: Exception) { notifyStatus("Camera app could not be opened.") } } }
        @JavascriptInterface fun openContacts() { runOnUiThread { if (!hasAccess("basic")) return@runOnUiThread; try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/"))) } catch (_: Exception) { notifyStatus("Contacts app could not be opened.") } } }
        @JavascriptInterface fun setAlarm(hour: Int, minute: Int, label: String) { runOnUiThread { if (!hasAccess("basic")) return@runOnUiThread; try { val i = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour).putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute).putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label).putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false); startActivity(i) } catch (_: Exception) { notifyStatus("Clock app could not open the alarm screen.") } } }
        @JavascriptInterface fun openCalendar() { runOnUiThread { if (!hasAccess("basic")) return@runOnUiThread; try { startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse("content://com.android.calendar/time/"))) } catch (_: Exception) { notifyStatus("Calendar app could not be opened.") } } }
        @JavascriptInterface fun verifyOwner() { runOnUiThread { verifyOwnerBiometric() } }
        // Individual profile PINs are enrolled on-device; only salted hashes are stored.
        @JavascriptInterface fun registerProfile(profile: String, pin: String): Boolean {
            if (!isOwnerAdmin() || (profile != "AILI" && profile != "MAGGY")) return false
            if (pin.length !in 6..12 || !pin.all { it.isDigit() }) return false
            return try {
                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val hash = profilePinHash(pin, salt)
                getSharedPreferences("deja_profile_identity", MODE_PRIVATE).edit()
                    .putString("${profile}_salt", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
                    .putString("${profile}_hash", android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP))
                    .putBoolean("${profile}_registered", true)
                    .putInt("${profile}_failed_attempts", 0)
                    .putLong("${profile}_lock_until", 0L).apply()
                true
            } catch (_: Exception) { false }
        }
        @JavascriptInterface fun loginProfile(profile: String, pin: String): Boolean {
            if (profile != "AILI" && profile != "MAGGY") return false
            if (pin.length !in 6..12 || !pin.all { it.isDigit() }) return false
            return try {
                val prefs = getSharedPreferences("deja_profile_identity", MODE_PRIVATE)
                if (!prefs.getBoolean("${profile}_registered", false)) return false
                val now = System.currentTimeMillis()
                val lockUntil = prefs.getLong("${profile}_lock_until", 0L)
                if (now < lockUntil) return false
                val salt = android.util.Base64.decode(prefs.getString("${profile}_salt", "") ?: "", android.util.Base64.NO_WRAP)
                val expected = android.util.Base64.decode(prefs.getString("${profile}_hash", "") ?: "", android.util.Base64.NO_WRAP)
                val actual = profilePinHash(pin, salt)
                val valid = java.security.MessageDigest.isEqual(expected, actual)
                if (valid) {
                    prefs.edit().putInt("${profile}_failed_attempts", 0).putLong("${profile}_lock_until", 0L).apply()
                    ownerVerified = false
                    activeProfile = profile
                    activeProfileExpiresAt = now + profileSessionDurationMs
                } else {
                    val failures = (prefs.getInt("${profile}_failed_attempts", 0) + 1).coerceAtMost(15)
                    val cooldown = when { failures >= 15 -> 30 * 60_000L; failures >= 10 -> 5 * 60_000L; failures >= 5 -> 60_000L; else -> 0L }
                    prefs.edit().putInt("${profile}_failed_attempts", failures)
                        .putLong("${profile}_lock_until", if (cooldown > 0) now + cooldown else 0L).apply()
                }
                valid
            } catch (_: Exception) { false }
        }
        @JavascriptInterface fun getRegisteredProfiles(): String {
            val prefs = getSharedPreferences("deja_profile_identity", MODE_PRIVATE)
            return org.json.JSONObject().put("AILI", prefs.getBoolean("AILI_registered", false))
                .put("MAGGY", prefs.getBoolean("MAGGY_registered", false)).toString()
        }
        @JavascriptInterface fun getProfileLoginStatus(profile: String): String {
            if (profile != "AILI" && profile != "MAGGY") return "Invalid profile."
            val prefs = getSharedPreferences("deja_profile_identity", MODE_PRIVATE)
            if (!prefs.getBoolean("${profile}_registered", false)) return "Not registered. Ask Asbay to register this profile."
            val remaining = prefs.getLong("${profile}_lock_until", 0L) - System.currentTimeMillis()
            if (remaining > 0) return "Temporarily locked. Try again in ${((remaining + 999) / 1000)} seconds, or ask Asbay to reset the profile."
            val failures = prefs.getInt("${profile}_failed_attempts", 0)
            return if (failures >= 15) "Repeated failed attempts: a 30-minute pause applies after each failed attempt. Ask Asbay to reset the profile if needed."
                else "Profile ready for PIN verification. Failed attempts: $failures. A temporary pause starts after five failed attempts."
        }
        @JavascriptInterface fun resetProfile(profile: String): Boolean {
            if (!isOwnerAdmin() || (profile != "AILI" && profile != "MAGGY")) return false
            return try {
                getSharedPreferences("deja_profile_identity", MODE_PRIVATE).edit()
                    .remove("${profile}_salt").remove("${profile}_hash")
                    .remove("${profile}_registered").remove("${profile}_failed_attempts")
                    .remove("${profile}_lock_until").commit()
                getSharedPreferences("deja_access_policy", MODE_PRIVATE).edit()
                    .remove("${profile}_basic").remove("${profile}_phone")
                    .remove("${profile}_messages").remove("${profile}_security").commit()
                if (activeProfile == profile) { activeProfile = null; ownerVerified = false; activeProfileExpiresAt = 0L }
                true
            } catch (_: Exception) { false }
        }
        @JavascriptInterface fun isOwnerVerified(): Boolean = isOwnerAdmin()
        @JavascriptInterface fun getSessionStatus(): String {
            val role = when {
                isOwnerAdmin() -> "OWNER"
                activeProfile == "AILI" && System.currentTimeMillis() < activeProfileExpiresAt -> "AILI"
                activeProfile == "MAGGY" && System.currentTimeMillis() < activeProfileExpiresAt -> "MAGGY"
                else -> {
                    activeProfile = null
                    activeProfileExpiresAt = 0L
                    ownerVerified = false
                    "LOCKED"
                }
            }
            return role
        }
        @JavascriptInterface fun signOut() {
            ownerVerified = false
            activeProfile = null
            activeProfileExpiresAt = 0L
            runOnUiThread {
                web.evaluateJavascript("window.onSessionChanged && window.onSessionChanged('LOCKED')", null)
                notifyStatus("Signed out. Owner and profile permissions are locked.")
            }
        }
        @JavascriptInterface fun dial(number: String) { runOnUiThread { if (!hasAccess("phone")) return@runOnUiThread; try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))) } catch (_: Exception) { notifyStatus("Phone dialler is unavailable.") } } }
        @JavascriptInterface fun composeSms(number: String, message: String) { runOnUiThread { if (!hasAccess("messages")) return@runOnUiThread; try { val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")); i.putExtra("sms_body", message); startActivity(i) } catch (_: Exception) { notifyStatus("Messaging app is unavailable.") } } }
        @JavascriptInterface fun shareWhatsApp(message: String) { runOnUiThread { if (!hasAccess("messages")) return@runOnUiThread; try { val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message); setPackage("com.whatsapp") }; startActivity(i) } catch (_: Exception) { notifyStatus("WhatsApp is not installed. You can use the SMS option instead.") } } }
        @JavascriptInterface fun openDeviceFinder() { runOnUiThread { if (!hasAccess("security")) return@runOnUiThread; try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://android.com/find"))) } catch (_: Exception) { notifyStatus("Could not open device finder.") } } }
        // Backup data can contain private notes and settings, so the native layer
        // independently enforces owner verification instead of trusting the Web UI.
        @JavascriptInterface fun encryptBackup(json: String): String {
            if (!isOwnerAdmin()) return "ERROR: Verify the owner before creating a backup."
            return try { DejaCrypto.encrypt(json) } catch (_: Exception) { "ERROR: Could not encrypt backup on this device." }
        }
        @JavascriptInterface fun decryptBackup(payload: String): String {
            if (!isOwnerAdmin()) return "ERROR: Verify the owner before restoring a backup."
            return try { DejaCrypto.decrypt(payload) } catch (_: Exception) { "ERROR: Backup cannot be decrypted on this device. Backups are tied to the phone that created them." }
        }
        @JavascriptInterface fun saveProfilePermissions(profile: String, basic: Boolean, phone: Boolean, messages: Boolean, security: Boolean): Boolean {
            if (!isOwnerAdmin() || (profile != "AILI" && profile != "MAGGY")) return false
            return try {
                getSharedPreferences("deja_access_policy", MODE_PRIVATE).edit()
                    .putBoolean("${profile}_basic", basic)
                    .putBoolean("${profile}_phone", phone)
                    .putBoolean("${profile}_messages", messages)
                    .putBoolean("${profile}_security", false)
                    .apply()
                true
            } catch (_: Exception) { false }
        }
        @JavascriptInterface fun getProfilePermissions(profile: String): String {
            if (profile != "AILI" && profile != "MAGGY") return "{}"
            val prefs = getSharedPreferences("deja_access_policy", MODE_PRIVATE)
            val result = org.json.JSONObject()
                .put("basic", prefs.getBoolean("${profile}_basic", true))
                .put("phone", prefs.getBoolean("${profile}_phone", false))
                .put("messages", prefs.getBoolean("${profile}_messages", false))
                .put("security", false)
            return result.toString()
        }
        @JavascriptInterface fun openCallSettings() { runOnUiThread { if (!isOwnerAdmin()) { notifyStatus("Only Asbay can change default phone-app settings. Verify biometrically first."); return@runOnUiThread }; try { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } catch (_: Exception) { notifyStatus("Android call settings could not be opened.") } } }
        @JavascriptInterface fun notifyWeb(message: String) { runOnUiThread { notifyStatus(message) } }
    }

    /** Native-side authorization. The Web page is not trusted to enforce permissions. */
    private fun isOwnerAdmin(): Boolean {
        if (ownerVerified && activeProfile == "ASBAY_OWNER" && System.currentTimeMillis() < activeProfileExpiresAt) return true
        if (activeProfile == "ASBAY_OWNER") {
            ownerVerified = false
            activeProfile = null
            activeProfileExpiresAt = 0L
        }
        return false
    }

    private fun hasAccess(permission: String): Boolean {
        if (isOwnerAdmin()) return true
        if ((activeProfile == "AILI" || activeProfile == "MAGGY") && System.currentTimeMillis() >= activeProfileExpiresAt) {
            activeProfile = null
            activeProfileExpiresAt = 0L
            ownerVerified = false
            notifyStatus("Your DEJA profile session expired. Verify your profile again.")
        }
        if (permission == "security") {
            notifyStatus("Security and anti-theft controls are owner-only. Verify Asbay biometrically.")
            return false
        }
        val profile = activeProfile
        if (profile != "AILI" && profile != "MAGGY") {
            notifyStatus("Access denied. Verify Asbay or sign in with an authorized profile.")
            return false
        }
        val prefs = getSharedPreferences("deja_access_policy", MODE_PRIVATE)
        val allowed = prefs.getBoolean("${profile}_${permission}", permission == "basic")
        if (!allowed) notifyStatus("Access denied: Asbay has not granted $permission permission to $profile.")
        return allowed
    }

    private fun profilePinHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 160_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun verifyOwnerBiometric() {
        val manager = BiometricManager.from(this)
        val can = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) { notifyStatus("Set up a strong fingerprint or face biometric in Android Settings first."); return }
        val prompt = BiometricPrompt(this, biometricExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { super.onAuthenticationSucceeded(result); ownerVerified = true; activeProfile = "ASBAY_OWNER"; activeProfileExpiresAt = System.currentTimeMillis() + ownerSessionDurationMs; notifyStatus("Owner verified. Owner access expires after 5 minutes."); web.evaluateJavascript("window.onOwnerVerified && window.onOwnerVerified(true); window.onSessionChanged && window.onSessionChanged('OWNER')", null) }
            override fun onAuthenticationFailed() { super.onAuthenticationFailed(); notifyStatus("Biometric not recognised. Try again.") }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { super.onAuthenticationError(errorCode, errString); notifyStatus("Verification cancelled or unavailable: $errString") }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Verify DEJA owner").setSubtitle("Asbay Garcia — owner access").setNegativeButtonText("Cancel").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build()
        prompt.authenticate(info)
    }
    private fun notifyStatus(message: String) { if (::web.isInitialized) web.evaluateJavascript("document.getElementById('status').textContent=${org.json.JSONObject.quote(message)}", null) }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == 41 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) Toast.makeText(this, "DEJA microphone access enabled", Toast.LENGTH_SHORT).show() }
    override fun onStop() { ownerVerified = false; activeProfile = null; activeProfileExpiresAt = 0L; super.onStop() }
    override fun onDestroy() { speech?.destroy(); tts?.stop(); tts?.shutdown(); aiExecutor.shutdownNow(); web.destroy(); super.onDestroy() }
}
