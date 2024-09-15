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

import java.net.URI
import java.util.*
import org.pkl.core.resource.ResourceReader
import org.pkl.core.resource.ResourceReaderFactory

internal class ClientResourceReaderFactory(
  private val readerSpecs: Collection<ResourceReaderSpec>,
  private val transport: MessageTransport,
  private val evaluatorId: Long
) : ResourceReaderFactory {
  private val schemes = readerSpecs.map { it.scheme }

  override fun create(uri: URI): Optional<ResourceReader> =
    when (uri.scheme) {
      in schemes -> {
        val readerSpec = readerSpecs.find { it.scheme == uri.scheme }!!
        val reader = ClientResourceReader(transport, evaluatorId, readerSpec)
        Optional.of(reader)
      }
      else -> Optional.empty()
    }
}
