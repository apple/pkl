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

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import org.assertj.core.api.Assertions.assertThat

class TestTransport(private val delegate: MessageTransport) : AutoCloseable {
  val incomingMessages: BlockingQueue<Message> = ArrayBlockingQueue(10)

  fun start() {
    delegate.start({ incomingMessages.put(it) }, { incomingMessages.put(it) })
  }

  override fun close() {
    delegate.close()
  }

  fun send(message: ClientOneWayMessage) {
    delegate.send(message)
  }

  fun send(message: ClientRequestMessage) {
    delegate.send(message) { incomingMessages.put(it) }
  }

  fun send(message: ClientResponseMessage) {
    delegate.send(message)
  }

  inline fun <reified T : Message> receive(): T {
    val message = incomingMessages.take()
    assertThat(message).isInstanceOf(T::class.java)
    return message as T
  }
}
