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
@file:JvmName("Main")

package org.pkl.core.externalreaderfixture

import java.net.URI
import org.pkl.core.externalreader.ExternalModuleReader
import org.pkl.core.externalreader.ExternalReaderClient
import org.pkl.core.externalreader.ExternalReaderMessagePackDecoder
import org.pkl.core.externalreader.ExternalReaderMessagePackEncoder
import org.pkl.core.externalreader.ExternalResourceReader
import org.pkl.core.messaging.MessageTransports
import org.pkl.core.module.PathElement

object ModuleReader : ExternalModuleReader {
  override val isLocal: Boolean = true

  override fun read(uri: URI): String = "hello"

  override val scheme: String = "foo"

  override val hasHierarchicalUris: Boolean = false

  override val isGlobbable: Boolean = false

  override fun listElements(uri: URI): List<PathElement> {
    throw NotImplementedError()
  }
}

object ResourceReader : ExternalResourceReader {
  override fun read(uri: URI): ByteArray = "hello".toByteArray()

  override val scheme: String = "foo"

  override val hasHierarchicalUris: Boolean = false

  override val isGlobbable: Boolean = false

  override fun listElements(uri: URI): List<PathElement> {
    throw NotImplementedError()
  }
}

fun main() {
  val transport =
    MessageTransports.stream(
      ExternalReaderMessagePackDecoder(System.`in`),
      ExternalReaderMessagePackEncoder(System.out),
    ) {}
  val client = ExternalReaderClient(listOf(ModuleReader), listOf(ResourceReader), transport)
  client.run()
}
