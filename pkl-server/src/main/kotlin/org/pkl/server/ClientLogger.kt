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

import org.pkl.core.Logger
import org.pkl.core.StackFrame

internal class ClientLogger(
  private val evaluatorId: Long,
  private val transport: MessageTransport
) : Logger {
  override fun trace(message: String, frame: StackFrame) {
    transport.send(LogMessage(evaluatorId, level = 0, message, frame.moduleUri))
  }

  override fun warn(message: String, frame: StackFrame) {
    transport.send(LogMessage(evaluatorId, level = 1, message, frame.moduleUri))
  }
}
