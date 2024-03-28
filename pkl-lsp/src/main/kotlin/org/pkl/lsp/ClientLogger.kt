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
package org.pkl.lsp

import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient

class ClientLogger(private val client: LanguageClient, private val verbose: Boolean) {

  fun log(msg: String) {
    if (!verbose) return
    val params = MessageParams(MessageType.Log, msg)
    client.logMessage(params)
  }

  fun warn(msg: String) {
    if (!verbose) return
    val params = MessageParams(MessageType.Warning, msg)
    client.logMessage(params)
  }

  fun info(msg: String) {
    if (!verbose) return
    val params = MessageParams(MessageType.Info, msg)
    client.logMessage(params)
  }

  fun error(msg: String) {
    if (!verbose) return
    val params = MessageParams(MessageType.Error, msg)
    client.logMessage(params)
  }
}
