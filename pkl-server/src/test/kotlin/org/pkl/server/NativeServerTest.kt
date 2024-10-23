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

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.pkl.commons.test.PklExecutablePaths

class NativeServerTest : AbstractServerTest() {
  private lateinit var server: Process
  override lateinit var client: TestTransport

  @BeforeEach
  fun beforeEach() {
    val executable = PklExecutablePaths.firstExisting.toString()
    server = ProcessBuilder(executable, "server").start()
    client = TestTransport(MessageTransports.stream(server.inputStream, server.outputStream))
    executor.execute { client.start() }
  }

  @AfterEach
  fun afterEach() {
    client.close()
    server.destroy()
  }
}
