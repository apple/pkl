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

import java.net.URI
import java.util.Optional
import org.pkl.core.messaging.*
import org.pkl.core.module.ExternalModuleResolver
import org.pkl.core.messaging.Messages.*
import org.pkl.core.module.*

internal class ClientModuleKeyFactory(
  private val readerSpecs: Collection<ModuleReaderSpec>,
  transport: MessageTransport,
  evaluatorId: Long
) : ModuleKeyFactory {
  private val schemes = readerSpecs.map { it.scheme }

  private val resolver: ExternalModuleResolver =
    ExternalModuleResolver(transport, evaluatorId)

  override fun create(uri: URI): Optional<ModuleKey> =
    when (uri.scheme) {
      in schemes -> {
        val readerSpec = readerSpecs.find { it.scheme == uri.scheme }!!
        val moduleKey = ModuleKeys.externalResolver(uri, readerSpec, resolver)
        Optional.of(moduleKey)
      }
      else -> Optional.empty()
    }
}
