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

import java.io.PipedInputStream
import java.io.PipedOutputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.pkl.core.messaging.MessageTransport
import org.pkl.core.messaging.MessageTransports
import org.pkl.core.util.Pair

class JvmServerTest : AbstractServerTest() {
  private val transports: Pair<MessageTransport, MessageTransport> = run {
    if (USE_DIRECT_TRANSPORT) {
      MessageTransports.direct(::log)
    } else {
      val in1 =
        PipedInputStream(10240) // use larger pipe size since large messages can be >1024 bytes
      val out1 = PipedOutputStream(in1)
      val in2 = PipedInputStream()
      val out2 = PipedOutputStream(in2)
      Pair.of(
        MessageTransports.stream(
          ServerMessagePackDecoder(in1),
          ServerMessagePackEncoder(out2),
          ::log
        ),
        MessageTransports.stream(
          ServerMessagePackDecoder(in2),
          ServerMessagePackEncoder(out1),
          ::log
        )
      )
    }
  }

  override val client: TestTransport = TestTransport(transports.first)
  private val server: Server = Server(transports.second)

  @BeforeEach
  fun beforeEach() {
    executor.execute { server.start() }
    executor.execute { client.start() }
  }

  @AfterEach
  fun after() {
    client.close()
    server.close()
  }
}
