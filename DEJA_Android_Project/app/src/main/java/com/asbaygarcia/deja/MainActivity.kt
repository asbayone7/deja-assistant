package com.asbaygarcia.deja

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.Executor

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var web: WebView
    private var speech: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var biometricExecutor: Executor
    private var ownerVerified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biometricExecutor = ContextCompat.getMainExecutor(this)
        tts = TextToSpeech(this, this)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.webViewClient = WebViewClient()
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
        @JavascriptInterface fun speak(text: String, language: String) { runOnUiThread { val locale = when(language) { "pt-BR" -> Locale("pt", "BR"); "ng-NA" -> Locale("en", "NA"); else -> Locale.US }; tts?.language = locale; tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "DEJA_UTTERANCE") } }
        @JavascriptInterface fun stopSpeaking() { runOnUiThread { tts?.stop(); notifyStatus("Voice stopped") } }
        @JavascriptInterface fun openWifiSettings() { runOnUiThread { try { startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) { notifyStatus("Wi-Fi settings could not be opened.") } } }
        @JavascriptInterface fun openBluetoothSettings() { runOnUiThread { try { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Exception) { notifyStatus("Bluetooth settings could not be opened.") } } }
        @JavascriptInterface fun openGeneralSettings() { runOnUiThread { try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_: Exception) { notifyStatus("Settings could not be opened.") } } }
        @JavascriptInterface fun openCamera() { runOnUiThread { try { startActivity(Intent("android.media.action.IMAGE_CAPTURE")) } catch (_: Exception) { notifyStatus("Camera app could not be opened.") } } }
        @JavascriptInterface fun openContacts() { runOnUiThread { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/"))) } catch (_: Exception) { notifyStatus("Contacts app could not be opened.") } } }
        @JavascriptInterface fun setAlarm(hour: Int, minute: Int, label: String) { runOnUiThread { try { val i = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour).putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute).putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label).putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false); startActivity(i) } catch (_: Exception) { notifyStatus("Clock app could not open the alarm screen.") } } }
        @JavascriptInterface fun openCalendar() { runOnUiThread { try { startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse("content://com.android.calendar/time/"))) } catch (_: Exception) { notifyStatus("Calendar app could not be opened.") } } }
        @JavascriptInterface fun verifyOwner() { runOnUiThread { verifyOwnerBiometric() } }
        @JavascriptInterface fun isOwnerVerified(): Boolean = ownerVerified
        @JavascriptInterface fun dial(number: String) { runOnUiThread { if (!ownerVerified) { notifyStatus("Verify Asbay first to use phone actions."); return@runOnUiThread }; try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))) } catch (_: Exception) { notifyStatus("Phone dialler is unavailable.") } } }
        @JavascriptInterface fun composeSms(number: String, message: String) { runOnUiThread { if (!ownerVerified) { notifyStatus("Verify Asbay first to use messaging actions."); return@runOnUiThread }; try { val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")); i.putExtra("sms_body", message); startActivity(i) } catch (_: Exception) { notifyStatus("Messaging app is unavailable.") } } }
        @JavascriptInterface fun shareWhatsApp(message: String) { runOnUiThread { if (!ownerVerified) { notifyStatus("Verify Asbay first to share a message."); return@runOnUiThread }; try { val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message); setPackage("com.whatsapp") }; startActivity(i) } catch (_: Exception) { notifyStatus("WhatsApp is not installed. You can use the SMS option instead.") } } }
        @JavascriptInterface fun openDeviceFinder() { runOnUiThread { if (!ownerVerified) { notifyStatus("Verify Asbay first to open device security."); return@runOnUiThread }; try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://android.com/find"))) } catch (_: Exception) { notifyStatus("Could not open device finder.") } } }
        @JavascriptInterface fun encryptBackup(json: String): String {
            return try { DejaCrypto.encrypt(json) } catch (_: Exception) { "ERROR: Could not encrypt backup on this device." }
        }
        @JavascriptInterface fun decryptBackup(payload: String): String {
            return try { DejaCrypto.decrypt(payload) } catch (_: Exception) { "ERROR: Backup cannot be decrypted on this device. Backups are tied to the phone that created them." }
        }
        @JavascriptInterface fun openCallSettings() { runOnUiThread { try { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } catch (_: Exception) { notifyStatus("Android call settings could not be opened.") } } }
        @JavascriptInterface fun notifyWeb(message: String) { runOnUiThread { notifyStatus(message) } }
    }

    private fun verifyOwnerBiometric() {
        val manager = BiometricManager.from(this)
        val can = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) { notifyStatus("Set up a strong fingerprint or face biometric in Android Settings first."); return }
        val prompt = BiometricPrompt(this, biometricExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { super.onAuthenticationSucceeded(result); ownerVerified = true; notifyStatus("Owner verified for this session."); web.evaluateJavascript("window.onOwnerVerified && window.onOwnerVerified(true)", null) }
            override fun onAuthenticationFailed() { super.onAuthenticationFailed(); notifyStatus("Biometric not recognised. Try again.") }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { super.onAuthenticationError(errorCode, errString); notifyStatus("Verification cancelled or unavailable: $errString") }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Verify DEJA owner").setSubtitle("Asbay Garcia — owner access").setNegativeButtonText("Cancel").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build()
        prompt.authenticate(info)
    }
    private fun notifyStatus(message: String) { if (::web.isInitialized) web.evaluateJavascript("document.getElementById('status').textContent=${org.json.JSONObject.quote(message)}", null) }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == 41 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) Toast.makeText(this, "DEJA microphone access enabled", Toast.LENGTH_SHORT).show() }
    override fun onStop() { ownerVerified = false; super.onStop() }
    override fun onDestroy() { speech?.destroy(); tts?.stop(); tts?.shutdown(); web.destroy(); super.onDestroy() }
}
