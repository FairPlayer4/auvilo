package com.avlreminder.voice

/** Android's recognizer behind a port, so the flow below runs in plain tests. */
interface Recognizer {
    /** Starts (or restarts) listening; results come back through [VoiceSession]. */
    fun start()
    /** Stops listening; the recognizer still delivers what it heard. */
    fun stop()
    /** Switches from the on-device recognizer to Android's service; false if there is none left. */
    fun fallback(): Boolean
}

/** Why the recognizer ended, mapped from SpeechRecognizer's error codes. */
enum class ListenError { NOTHING_HEARD, BUSY, LANGUAGE_MISSING, PERMISSION, OTHER }

sealed interface VoiceState {
    data class Listening(val text: String) : VoiceState
    data class Heard(val text: String) : VoiceState
    data class Failed(val message: String) : VoiceState
}

/**
 * One recording (Anf. 7.35 SR2): Android's recognizer ends by itself after a pause, so the
 * session restarts it and collects the pieces until Kevin taps "Fertig". Then the recognizer
 * delivers its last piece (or an error) and the session reports the whole text.
 */
class VoiceSession(private val recognizer: Recognizer, private val onState: (VoiceState) -> Unit) {
    companion object {
        /** Restarts without anything heard before the session gives up (each one is a few seconds). */
        const val MAX_EMPTY_RESTARTS = 8
    }

    private val pieces = mutableListOf<String>()
    private var partial = ""
    private var stopping = false
    private var done = false
    private var emptyRestarts = 0

    fun start() {
        pieces.clear()
        partial = ""
        stopping = false
        done = false
        emptyRestarts = 0
        onState(VoiceState.Listening(""))
        recognizer.start()
    }

    /** "Fertig": stop and evaluate what was heard. */
    fun finish() {
        if (done) return
        stopping = true
        recognizer.stop()
    }

    fun onPartial(text: String) {
        if (done) return
        partial = text
        onState(VoiceState.Listening(joined()))
    }

    fun onPiece(text: String) {
        if (done) return
        partial = ""
        if (text.isNotBlank()) {
            pieces += text.trim()
            emptyRestarts = 0
        }
        if (stopping) report() else {
            onState(VoiceState.Listening(joined()))
            recognizer.start()
        }
    }

    fun onError(error: ListenError) {
        if (done) return
        when {
            // After "Fertig" any end counts: evaluate what is there.
            stopping -> report()
            error == ListenError.NOTHING_HEARD || error == ListenError.BUSY -> {
                // A pause ended the piece; the partial text is all there is of it.
                if (partial.isNotBlank()) pieces += partial.trim() else emptyRestarts++
                partial = ""
                if (emptyRestarts > MAX_EMPTY_RESTARTS) report() else recognizer.start()
            }
            error == ListenError.LANGUAGE_MISSING && recognizer.fallback() -> recognizer.start()
            else -> fail(message(error))
        }
    }

    private fun report() {
        val text = joined()
        if (text.isBlank()) fail("Nichts verstanden.") else {
            done = true
            onState(VoiceState.Heard(text))
        }
    }

    private fun fail(message: String) {
        done = true
        onState(VoiceState.Failed(message))
    }

    private fun joined(): String = (pieces + partial).filter { it.isNotBlank() }.joinToString(" ")

    private fun message(error: ListenError): String = when (error) {
        ListenError.LANGUAGE_MISSING -> "Deutsch fehlt der Spracherkennung. In den Android-Einstellungen unter Spracheingabe die Sprache Deutsch (offline) laden."
        ListenError.PERMISSION -> "Das Mikrofon ist nicht erlaubt."
        else -> "Die Spracherkennung ist ausgefallen."
    }
}
