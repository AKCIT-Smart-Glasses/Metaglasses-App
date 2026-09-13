package br.ufg.akcit.smartglasses.elo.grpc

import android.util.Log
import br.ufg.akcit.smartglasses.elo.settings.EloSettingsStore
import br.ufg.akcit.smartglasses.elo.settings.OrchestratorTarget
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class GrpcChannelProvider(private val settingsStore: EloSettingsStore) {
  companion object {
    private const val TAG = "Elo:GrpcChannel"
    private const val MAX_MESSAGE_BYTES = 16 * 1024 * 1024
    private const val KEEPALIVE_TIME_SECONDS = 30L
    private const val KEEPALIVE_TIMEOUT_SECONDS = 10L
  }

  private val lock = Any()
  private var channel: ManagedChannel? = null
  private var channelTarget: OrchestratorTarget? = null

  suspend fun currentChannel(): ManagedChannel {
    val target = settingsStore.targetFlow.first()
    synchronized(lock) {
      val existing = channel
      if (existing != null && channelTarget == target && !existing.isShutdown && !existing.isTerminated) {
        return existing
      }
      existing?.shutdownNow()
      return buildChannel(target).also {
        channel = it
        channelTarget = target
      }
    }
  }

  fun shutdown() {
    synchronized(lock) {
      channel?.shutdownNow()
      channel = null
      channelTarget = null
    }
  }

  private fun buildChannel(target: OrchestratorTarget): ManagedChannel {
    Log.i(TAG, "Connecting to orchestrator at ${target.host}:${target.port}")
    return OkHttpChannelBuilder.forAddress(target.host, target.port)
        .usePlaintext()
        .maxInboundMessageSize(MAX_MESSAGE_BYTES)
        .keepAliveTime(KEEPALIVE_TIME_SECONDS, TimeUnit.SECONDS)
        .keepAliveTimeout(KEEPALIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
  }
}
