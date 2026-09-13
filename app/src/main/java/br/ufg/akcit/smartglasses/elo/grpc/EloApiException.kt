package br.ufg.akcit.smartglasses.elo.grpc

import com.metaglass.proto.Status
import com.metaglass.proto.StatusCode

class EloApiException(val operation: String, val serverMessage: String) :
    Exception("$operation: ${serverMessage.ifBlank { "no message" }}")

internal fun Status.requireOk(operation: String) {
  if (code != StatusCode.STATUS_CODE_OK) {
    throw EloApiException(operation, message)
  }
}
