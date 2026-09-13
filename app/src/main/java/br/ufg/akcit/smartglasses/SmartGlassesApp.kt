/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses

import android.app.Application
import br.ufg.akcit.smartglasses.elo.EloContainer

class SmartGlassesApp : Application() {
  lateinit var container: EloContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = EloContainer(this)
  }
}
