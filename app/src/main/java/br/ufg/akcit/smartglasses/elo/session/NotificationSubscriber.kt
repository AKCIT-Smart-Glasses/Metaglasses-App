/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.session

import android.util.Log
import br.ufg.akcit.smartglasses.elo.grpc.NotificationsApi
import com.metaglass.proto.Notification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NotificationSubscriber(
    private val notificationsApi: NotificationsApi,
    private val scope: CoroutineScope,
) {
  companion object {
    private const val TAG = "Elo:Notifications"
    private const val INITIAL_BACKOFF_MS = 1_000L
    private const val MAX_BACKOFF_MS = 30_000L
    private const val NOTIFICATION_BUFFER = 20
  }

  private val _notifications =
      MutableSharedFlow<Notification>(
          extraBufferCapacity = NOTIFICATION_BUFFER,
          onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )
  val notifications: SharedFlow<Notification> = _notifications.asSharedFlow()

  private var job: Job? = null

  /** (Re)starts the subscription for [sessionId]. */
  fun start(sessionId: String) {
    stop()
    job =
        scope.launch {
          var backoff = INITIAL_BACKOFF_MS
          while (isActive) {
            try {
              notificationsApi.subscribe(sessionId).collect { notification ->
                _notifications.emit(notification)
                backoff = INITIAL_BACKOFF_MS
              }
              Log.i(TAG, "Subscription closed by the server — resubscribing")
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              Log.w(TAG, "Subscription dropped ($e) — reconnecting in ${backoff}ms")
            }
            if (!isActive) return@launch
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
          }
        }
  }

  fun stop() {
    job?.cancel()
    job = null
  }
}
