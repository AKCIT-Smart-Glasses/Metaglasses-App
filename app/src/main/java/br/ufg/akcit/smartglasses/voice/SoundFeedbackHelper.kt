/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.voice

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * Low-latency audio feedback helper for glasses and device speakers.
 * Uses hardware ToneGenerator for instantaneous acoustic feedback (beeps).
 */
class SoundFeedbackHelper {

    private val tag = "SoundFeedbackHelper"
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize ToneGenerator for STREAM_VOICE_CALL", e)
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            } catch (e2: Exception) {
                Log.e(tag, "Failed to initialize ToneGenerator for STREAM_MUSIC", e2)
            }
        }
    }

    /**
     * Plays an immediate wake-up beep (confirming wake word was recognized).
     */
    fun playWakeBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
        } catch (e: Exception) {
            Log.e(tag, "Error playing wake beep", e)
        }
    }

    /**
     * Plays a confirmation tone (e.g. command captured successfully).
     */
    fun playConfirmTone() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        } catch (e: Exception) {
            Log.e(tag, "Error playing confirm tone", e)
        }
    }

    /**
     * Plays an error tone (e.g. timeout / no command recognized).
     */
    fun playErrorTone() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 250)
        } catch (e: Exception) {
            Log.e(tag, "Error playing error tone", e)
        }
    }

    /**
     * Plays a cancellation tone (e.g. user cancelled listening or interrupted response).
     */
    fun playCancelTone() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (e: Exception) {
            Log.e(tag, "Error playing cancel tone", e)
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
