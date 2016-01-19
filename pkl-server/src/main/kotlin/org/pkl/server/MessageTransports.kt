/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.server

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/** Factory methods for creating [MessageTransport]s. */
object MessageTransports {
  /** Creates a message transport that reads from [inputStream] and writes to [outputStream]. */
  fun stream(inputStream: InputStream, outputStream: OutputStream): MessageTransport {
    return EncodingMessageTransport(
      MessageDecoders.from(inputStream),
      MessageEncoders.into(outputStream)
    )
  }

  /** Creates "client" and "server" transports that are directly connected to each other. */
  fun direct(): Pair<MessageTransport, MessageTransport> {
    val transport1 = DirectMessageTransport()
    val transport2 = DirectMessageTransport()
    transport1.other = transport2
    transport2.other = transport1
    return transport1 to transport2
  }

  internal class EncodingMessageTransport(
    private val decoder: MessageDecoder,
    private val encoder: MessageEncoder,
  ) : AbstractMessageTransport() {
    @Volatile private var isClosed: Boolean = false

    override fun doStart() {
      while (!isClosed) {
        val message = decoder.decode() ?: return
        accept(message)
      }
    }

    override fun doClose() {
      isClosed = true
    }

    override fun doSend(message: Message) {
      encoder.encode(message)
    }
  }

  internal class DirectMessageTransport : AbstractMessageTransport() {
    lateinit var other: DirectMessageTransport

    override fun doStart() {}

    override fun doClose() {}

    override fun doSend(message: Message) {
      other.accept(message)
    }
  }

  // TODO: clean up callbacks if evaluation fails for some reason (ThreadInterrupt, timeout, etc)
  internal abstract class AbstractMessageTransport : MessageTransport {
    private lateinit var oneWayHandler: (OneWayMessage) -> Unit
    private lateinit var requestHandler: (RequestMessage) -> Unit
    private val responseHandlers: MutableMap<Long, (ResponseMessage) -> Unit> = ConcurrentHashMap()

    protected abstract fun doStart()

    protected abstract fun doClose()

    protected abstract fun doSend(message: Message)

    protected fun accept(message: Message) {
      log("Received message: $message")
      when (message) {
        is OneWayMessage -> oneWayHandler(message)
        is RequestMessage -> requestHandler(message)
        is ResponseMessage -> {
          val handler =
            responseHandlers.remove(message.requestId)
              ?: throw ProtocolException(
                "Received response ${message.javaClass.simpleName} for unknown request ID `${message.requestId}`."
              )
          handler(message)
        }
      }
    }

    final override fun start(
      oneWayHandler: (OneWayMessage) -> Unit,
      requestHandler: (RequestMessage) -> Unit
    ) {
      log("Starting transport: $this")
      this.oneWayHandler = oneWayHandler
      this.requestHandler = requestHandler
      doStart()
    }

    final override fun close() {
      log("Closing transport: $this")
      doClose()
      responseHandlers.clear()
    }

    override fun send(message: OneWayMessage) {
      log("Sending message: $message")
      doSend(message)
    }

    override fun send(message: RequestMessage, responseHandler: (ResponseMessage) -> Unit) {
      log("Sending message: $message")
      responseHandlers[message.requestId] = responseHandler
      return doSend(message)
    }

    override fun send(message: ResponseMessage) {
      log("Sending message: $message")
      doSend(message)
    }
  }
}
