/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.libpkl

import com.sun.jna.Pointer
import java.io.ByteArrayOutputStream
import java.lang.AutoCloseable
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import org.assertj.core.api.Assertions.assertThat
import org.msgpack.core.MessagePack
import org.pkl.core.messaging.Message
import org.pkl.core.messaging.Messages.ModuleReaderSpec
import org.pkl.core.messaging.Messages.ResourceReaderSpec
import org.pkl.server.CreateEvaluatorRequest
import org.pkl.server.CreateEvaluatorResponse
import org.pkl.server.Http
import org.pkl.server.Project
import org.pkl.server.ServerMessagePackDecoder
import org.pkl.server.ServerMessagePackEncoder

class JNATestClient : LibPklLibrary.PklMessageResponseHandler, Iterable<Message?>, AutoCloseable {
  val incoming: BlockingQueue<Message> = ArrayBlockingQueue(10)

  override fun invoke(length: Int, message: Pointer, userData: Pointer?) {
    val receivedBytes: ByteArray = message.getByteArray(0, length)
    val message = decode(receivedBytes)
    assertThat(message).isInstanceOf(Message::class.java)
    incoming.add(message!!)
  }

  override fun close() = incoming.clear()

  override fun iterator(): Iterator<Message?> = incoming.iterator()

  fun send(message: Message): Int =
    // TODO: Propagate `handlerContext` through, and validate it.
    encode(message).let { LibPklLibrary.INSTANCE.pkl_send_message(it.size, it) }

  inline fun <reified T : Message> receive(): T {
    val message = incoming.take()
    assertThat(message).isInstanceOf(T::class.java)
    return message as T
  }

  fun sendCreateEvaluatorRequest(
    requestId: Long = 123,
    resourceReaders: List<ResourceReaderSpec> = listOf(),
    moduleReaders: List<ModuleReaderSpec> = listOf(),
    modulePaths: List<Path> = listOf(),
    project: Project? = null,
    cacheDir: Path? = null,
    http: Http? = null,
  ): Long {
    val message =
      CreateEvaluatorRequest(
        123,
        listOf(".*"),
        listOf(".*"),
        moduleReaders,
        resourceReaders,
        modulePaths,
        mapOf(),
        mapOf(),
        null,
        null,
        cacheDir,
        null,
        project,
        http,
        null,
        null,
      )

    send(message)

    val response = receive<CreateEvaluatorResponse>()
    assertThat(response.requestId()).isEqualTo(requestId)
    assertThat(response.evaluatorId).isNotNull
    assertThat(response.error).isNull()

    return response.evaluatorId!!
  }

  private fun encode(message: Message): ByteArray {
    ByteArrayOutputStream().use { os ->
      val packer = MessagePack.newDefaultPacker(os)
      val encoder = ServerMessagePackEncoder(packer)
      encoder.encode(message)
      return os.toByteArray()
    }
  }

  private fun decode(receivedBytes: ByteArray): Message? {
    val unpacker = MessagePack.newDefaultUnpacker(receivedBytes)
    return ServerMessagePackDecoder(unpacker).decode()
  }
}
