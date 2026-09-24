/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.turn

import android.graphics.Bitmap

/** Captures one still photo from the glasses at the moment a question finishes. */
fun interface PhotoCapture {
  suspend fun capturePhoto(): Bitmap?
}
