/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.elo.session

import android.app.Notification as SystemNotification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import br.ufg.akcit.smartglasses.MainActivity
import br.ufg.akcit.smartglasses.R
import br.ufg.akcit.smartglasses.SmartGlassesApp
import com.metaglass.proto.Notification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EloSessionService : Service() {

  companion object {
    private const val TAG = "Elo:SessionService"
    private const val CHANNEL_ID = "elo_session_channel"
    private const val NOTIFICATION_ID = 2001
    private const val WAKELOCK_TAG = "SmartGlasses::EloSessionWakeLock"
    private const val WAKELOCK_TIMEOUT_MS = 60L * 60L * 1000L
    private const val ACTION_STOP = "br.ufg.akcit.smartglasses.elo.STOP"

    fun start(context: Context) {
      val intent =
          Intent(context, EloSessionService::class.java).apply { `package` = context.packageName }
      context.startForegroundService(intent)
    }

    fun stop(context: Context) {
      val intent =
          Intent(context, EloSessionService::class.java).apply {
            `package` = context.packageName
            action = ACTION_STOP
          }
      context.startForegroundService(intent)
    }
  }

  private val binder = LocalBinder()

  inner class LocalBinder : Binder() {
    fun getService(): EloSessionService = this@EloSessionService
  }

  override fun onBind(intent: Intent?): IBinder = binder

  private lateinit var scope: CoroutineScope
  private var wakeLock: PowerManager.WakeLock? = null
  private var driveJob: Job? = null

  override fun onCreate() {
    super.onCreate()
    scope = CoroutineScope(SupervisorJob())
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    try {
      startForeground(
          NOTIFICATION_ID,
          createNotification(),
          ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
      )
    } catch (e: Exception) {
      Log.e(TAG, "Failed to enter foreground; stopping service", e)
      stopSelf()
      return START_NOT_STICKY
    }

    if (intent?.action == ACTION_STOP) {
      Log.d(TAG, "Service stopping")
      teardown()
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    acquireWakeLock()
    driveElo()
    return START_STICKY
  }

  override fun onDestroy() {
    teardown()
    super.onDestroy()
  }

  private fun driveElo() {
    if (driveJob != null) return
    val container = (application as SmartGlassesApp).container

    driveJob =
        scope.launch {
          container.connectionManager.connect()
          launch {
            container.connectionManager.state.collect { state ->
              if (state is ConnectionState.Connected) {
                container.notificationSubscriber.start(state.sessionId)
              } else {
                container.notificationSubscriber.stop()
              }
            }
          }
          launch {
            container.notificationSubscriber.notifications.collect { notification ->
              handleNotification(notification)
            }
          }
        }
  }

  private fun handleNotification(notification: Notification) {
    Log.i(TAG, "Notification received: type=${notification.type}, text=\"${notification.text}\"")
    val container = (application as SmartGlassesApp).container
    val played = container.ttsPlayer.play(notification.audio.toByteArray(), notification.audioMimeType)
    if (!played) {
      Log.i(TAG, "No audio for this notification; text-only: ${notification.text}")
    }
  }

  private fun teardown() {
    releaseWakeLock()
    driveJob?.cancel()
    driveJob = null
    val container = (application as SmartGlassesApp).container
    container.notificationSubscriber.stop()
    container.ttsPlayer.stop()
    scope.cancel()
  }

  private fun createNotificationChannel() {
    val channel =
        NotificationChannel(
                CHANNEL_ID,
                getString(R.string.elo_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            .apply {
              description = getString(R.string.elo_notification_channel_description)
              setShowBadge(false)
            }
    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(channel)
  }

  private fun createNotification(): SystemNotification {
    val pendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
              flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.elo_notification_title))
        .setContentText(getString(R.string.elo_notification_body))
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()
  }

  private fun acquireWakeLock() {
    if (wakeLock == null) {
      val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
      wakeLock =
          powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            acquire(WAKELOCK_TIMEOUT_MS)
          }
      Log.d(TAG, "WakeLock acquired")
    }
  }

  private fun releaseWakeLock() {
    wakeLock?.let {
      if (it.isHeld) {
        it.release()
        Log.d(TAG, "WakeLock released")
      }
    }
    wakeLock = null
  }
}
