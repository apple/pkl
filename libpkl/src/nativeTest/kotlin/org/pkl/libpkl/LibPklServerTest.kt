/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.pkl.commons.test.server.AbstractServerTest
import org.pkl.commons.test.server.TestTransport

/**
 * Tests libpkl bindings by using JNA (see [LibPklJNA] and [LibPklMessageTransport]).
 *
 * Extends [AbstractServerTest] directly rather than
 * [org.pkl.commons.test.server.AbstractHttpServerTest], since a real outbound HTTP request causes
 * `doClose` to hang for about a minute here (suspected to be an unclosed `HttpClient`'s default
 * thread pool blocking native isolate teardown).
 *
 * To run these tests in IntelliJ, add
 * `-Djna.library.path=$ProjectFileDir$/libpkl/build/native-libs/<os>-<arch>/lib` to the run
 * configuration.
 *
 * You can modify the IntelliJ JUnit configuration template so that this flag gets added
 * automatically. See https://www.jetbrains.com/help/idea/run-debug-configuration.html#templates for
 * more details.
 */
class LibPklServerTest : AbstractServerTest() {
  override lateinit var client: TestTransport

  val delegate = LibPklMessageTransport()

  @BeforeEach
  fun beforeEach() {
    client = TestTransport(delegate).also { it.start() }
  }

  @AfterEach
  fun afterEach() {
    client.close()
  }
}
