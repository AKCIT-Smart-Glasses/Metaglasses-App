package br.ufg.akcit.smartglasses.elo.grpc

import com.metaglass.proto.AgentGrpcKt
import com.metaglass.proto.MediaPayload
import com.metaglass.proto.interactRequest
import java.util.concurrent.TimeUnit

data class InteractResult(
    val response: String,
    val query: String,
    val agent: String,
    val memoryId: String,
    val audio: ByteArray,
    val audioMimeType: String,
)

class AgentApi(private val channelProvider: GrpcChannelProvider) {
  companion object {
    private const val INTERACT_DEADLINE_SECONDS = 60L
  }

  suspend fun interact(sessionId: String, payloads: List<MediaPayload>): InteractResult {
    val stub =
        AgentGrpcKt.AgentCoroutineStub(channelProvider.currentChannel())
            .withDeadlineAfter(INTERACT_DEADLINE_SECONDS, TimeUnit.SECONDS)
    val response =
        stub.interact(
            interactRequest {
              this.sessionId = sessionId
              this.payloads.addAll(payloads)
            }
        )
    response.status.requireOk("Interact")
    return InteractResult(
        response = response.response,
        query = response.query,
        agent = response.agent,
        memoryId = response.memoryId,
        audio = response.audio.toByteArray(),
        audioMimeType = response.audioMimeType,
    )
  }
}
