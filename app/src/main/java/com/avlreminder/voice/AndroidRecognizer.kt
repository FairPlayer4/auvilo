package com.avlreminder.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.avlreminder.system.Diag

/**
 * Android's speech recognizer (Anf. 7.35 SR2): on the device if Android has one, otherwise
 * the system's recognition service, asked to stay offline. Must be used on the main thread.
 */
class AndroidRecognizer(private val context: Context) : Recognizer {
    /** Set before [start]; the session the results go to. */
    lateinit var session: VoiceSession
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    /** True while the on-device recognizer is in use. */
    var onDevice = false
        private set

    /** False when the phone has no recognizer at all. */
    val available: Boolean
        get() = SpeechRecognizer.isOnDeviceRecognitionAvailable(context) || SpeechRecognizer.isRecognitionAvailable(context)

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Longer pauses before the recognizer ends a piece; many services ignore these.
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)

    override fun start() {
        // Posted: restarting from inside a callback makes some recognizers report "busy".
        main.post { (recognizer ?: create())?.startListening(intent) ?: session.onError(ListenError.OTHER) }
    }

    override fun stop() {
        main.post { recognizer?.stopListening() }
    }

    override fun fallback(): Boolean {
        if (!onDevice || !SpeechRecognizer.isRecognitionAvailable(context)) return false
        Diag.log("voice: on-device recognizer without German, falling back to the system service")
        destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(listener) }
        onDevice = false
        return true
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun create(): SpeechRecognizer? {
        val r = when {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> {
                onDevice = true
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            }
            SpeechRecognizer.isRecognitionAvailable(context) -> {
                onDevice = false
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            else -> return null
        }
        r.setRecognitionListener(listener)
        recognizer = r
        return r
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) = session.onPiece(first(results))
        override fun onPartialResults(partialResults: Bundle?) = session.onPartial(first(partialResults))
        override fun onError(error: Int) {
            // Only the code goes to the log, never what was said.
            if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Diag.log("voice: recognizer error $error (onDevice=$onDevice)")
            }
            session.onError(map(error))
        }
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun first(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private fun map(error: Int): ListenError = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ListenError.NOTHING_HEARD
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> ListenError.BUSY
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> ListenError.LANGUAGE_MISSING
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ListenError.PERMISSION
        else -> ListenError.OTHER
    }
}
