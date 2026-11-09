/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.cli

import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliCommand
import org.pkl.commons.cli.CliException
import org.pkl.core.messaging.ProtocolException
import org.pkl.server.Server

class CliServer(options: CliBaseOptions) : CliCommand(options) {
  override fun doRun(): Unit =
    try {
      val server = Server.stream(System.`in`, System.out)
      server.use { it.start() }
    } catch (e: ProtocolException) {
      throw CliException(e.message!!)
    }
}
