/*
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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import org.msgpack.core.MessageBufferPacker
import org.msgpack.core.MessagePack
import org.pkl.core.messaging.Message

internal fun log(msg: String) {
  if (System.getenv("PKL_DEBUG") == "1") {
    System.err.println("[pkl-server] $msg")
  }
}

internal fun AutoCloseable.closeQuietly() {
  try {
    close()
  } catch (e: Exception) {
    log(e.message.orEmpty())
  }
}

internal val threadLocalBufferPacker: ThreadLocal<MessageBufferPacker> =
  ThreadLocal.withInitial { MessagePack.newDefaultBufferPacker() }

private val threadLocalEncoder: ThreadLocal<(Message) -> ByteArray> =
  ThreadLocal.withInitial {
    val packer = threadLocalBufferPacker.get()
    val encoder = ServerMessagePackEncoder(packer);
    { message: Message ->
      packer.clear()
      encoder.encode(message)
      packer.toByteArray()
    }
  }

internal fun encode(message: Message): ByteArray {
  return threadLocalEncoder.get()(message)
}

/**
 * This is like [Future.get], except it throws the actual exception given to
 * [CompletableFuture.completeExceptionally].
 *
 * [Future.get] will wrap any exception in [ExecutionException], which is kind of silly.
 */
fun <T> Future<T>.getUnderlying(): T =
  try {
    get()
  } catch (e: ExecutionException) {
    throw e.cause!!
  }
