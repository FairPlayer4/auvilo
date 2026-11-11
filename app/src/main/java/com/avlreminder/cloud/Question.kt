package com.avlreminder.cloud

import kotlinx.coroutines.CompletableDeferred

/**
 * One question to the person while a step of [CloudBackup] waits: the step [ask]s and waits, the
 * screen [answer]s. Without an open question an answer goes nowhere (a late tap does nothing).
 */
internal class Question<T> {
    @Volatile private var open: CompletableDeferred<T>? = null

    /**
     * Opens the question, lets [show] put it on screen and waits for the answer. An answer that
     * comes while [show] still runs counts.
     */
    suspend fun ask(show: suspend () -> Unit): T {
        val answer = CompletableDeferred<T>().also { open = it }
        show()
        return answer.await()
    }

    fun answer(value: T) {
        open?.complete(value)
    }

    /**
     * Answers with what [produce] returns (null = no answer).
     *
     * KRITISCH: It answers the question that was open when it started, never one opened in the
     * meantime: the password service may take long, and its key belongs to the old question.
     */
    suspend fun answerWith(produce: suspend () -> T?) {
        val answer = open ?: return
        produce()?.let { answer.complete(it) }
    }

    /** The step ended; the question is gone. */
    fun close() {
        open = null
    }
}
