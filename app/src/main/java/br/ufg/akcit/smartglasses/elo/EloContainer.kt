/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo

import android.app.Application
import br.ufg.akcit.smartglasses.elo.audio.TtsPlayer
import br.ufg.akcit.smartglasses.elo.grpc.AgentApi
import br.ufg.akcit.smartglasses.elo.grpc.ContextApi
import br.ufg.akcit.smartglasses.elo.grpc.GrpcChannelProvider
import br.ufg.akcit.smartglasses.elo.grpc.NotificationsApi
import br.ufg.akcit.smartglasses.elo.grpc.SessionApi
import br.ufg.akcit.smartglasses.elo.identity.IdentityStore
import br.ufg.akcit.smartglasses.elo.session.EloConnectionManager
import br.ufg.akcit.smartglasses.elo.session.NotificationSubscriber
import br.ufg.akcit.smartglasses.elo.settings.EloSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class EloContainer(application: Application) {
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val settingsStore = EloSettingsStore(application)
  val identityStore = IdentityStore(application)
  val channelProvider = GrpcChannelProvider(settingsStore)

  val sessionApi = SessionApi(channelProvider)
  val notificationsApi = NotificationsApi(channelProvider)
  val agentApi = AgentApi(channelProvider)
  val contextApi = ContextApi(channelProvider)

  val connectionManager = EloConnectionManager(sessionApi, identityStore, appScope)
  val notificationSubscriber = NotificationSubscriber(notificationsApi, appScope)
  val ttsPlayer = TtsPlayer(application)
}
